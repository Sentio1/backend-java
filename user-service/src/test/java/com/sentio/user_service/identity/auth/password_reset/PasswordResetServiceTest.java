package com.sentio.user_service.identity.auth.password_reset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sentio.user_service.identity.event.PasswordResetRequestedEvent;
import com.sentio.user_service.identity.user.api.dto.IssuedUserActionToken;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.enums.PlatformRole;
import com.sentio.user_service.identity.user.api.enums.UserActionTokenType;
import com.sentio.user_service.identity.user.api.service.UserAccountService;
import com.sentio.user_service.identity.user.api.service.UserActionTokenService;
import com.sentio.user_service.refresh_token.api.service.RefreshTokenService;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final long USER_ID = 7L;
    private static final String EMAIL = "user@sentio.dev";
    private static final String RESET_URL = "http://localhost:5173/reset-password";

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserActionTokenService userActionTokenService;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        PasswordResetProperties properties = new PasswordResetProperties(Duration.ofMinutes(30), RESET_URL);
        passwordResetService = new PasswordResetService(
                eventPublisher, userActionTokenService, userAccountService, properties, passwordEncoder, refreshTokenService);
    }

    private static UserDto user() {
        return new UserDto(USER_ID, EMAIL, "old-hash", "Jane", PlatformRole.USER, Instant.now(), false);
    }

    @Test
    void requestReset_existingActiveUser_issuesTokenAndPublishesEvent() {
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(30));
        when(userAccountService.findActiveByEmail(EMAIL)).thenReturn(Optional.of(user()));
        when(userActionTokenService.issueToken(USER_ID, UserActionTokenType.PASSWORD_RESET, Duration.ofMinutes(30)))
                .thenReturn(new IssuedUserActionToken(1L, "raw-token", expiresAt));

        ZoneId userZone = ZoneId.of("Europe/Warsaw");
        passwordResetService.requestReset(EMAIL, userZone);

        ArgumentCaptor<PasswordResetRequestedEvent> captor = ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        PasswordResetRequestedEvent event = captor.getValue();
        assertThat(event.userId()).isEqualTo(USER_ID);
        assertThat(event.email()).isEqualTo(EMAIL);
        assertThat(event.firstName()).isEqualTo("Jane");
        assertThat(event.rawToken()).isEqualTo("raw-token");
        assertThat(event.expiresAt()).isEqualTo(expiresAt);
        assertThat(event.resetUrl()).isEqualTo(RESET_URL);
        assertThat(event.userZone()).isEqualTo(userZone);
    }

    // The whole point: a caller probing for which emails are registered must see the same
    // (silent, no-op) outcome as a real user resetting their password - otherwise this endpoint
    // becomes a user-enumeration oracle.
    @Test
    void requestReset_unknownEmail_doesNothingObservable() {
        when(userAccountService.findActiveByEmail(EMAIL)).thenReturn(Optional.empty());

        passwordResetService.requestReset(EMAIL, ZoneId.of("Europe/Kyiv"));

        verifyNoInteractions(userActionTokenService, eventPublisher);
    }

    @Test
    void resetPassword_validToken_updatesPasswordHashAndRevokesAllSessions() {
        when(userActionTokenService.consumeToken("raw-token", UserActionTokenType.PASSWORD_RESET))
                .thenReturn(USER_ID);

        passwordResetService.resetPassword("raw-token", "NewPassword123!");

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(userAccountService).updatePasswordHash(eq(USER_ID), hashCaptor.capture());
        assertThat(hashCaptor.getValue()).isNotEqualTo("NewPassword123!");
        assertThat(passwordEncoder.matches("NewPassword123!", hashCaptor.getValue())).isTrue();

        // Anyone else already logged in (e.g. with the old, compromised password) must be
        // kicked out the moment the password actually changes.
        verify(refreshTokenService).revokeAllActiveForUser(USER_ID);
    }

    @Test
    void resetPassword_updatesPasswordBeforeRevokingSessions_notTheOtherWayAround() {
        // If revocation happened first and the hash update then failed, the account would be
        // left both logged-out everywhere AND still on the old password - the worst of both.
        when(userActionTokenService.consumeToken(anyString(), any())).thenReturn(USER_ID);

        passwordResetService.resetPassword("raw-token", "NewPassword123!");

        var inOrder = org.mockito.Mockito.inOrder(userAccountService, refreshTokenService);
        inOrder.verify(userAccountService).updatePasswordHash(eq(USER_ID), anyString());
        inOrder.verify(refreshTokenService).revokeAllActiveForUser(USER_ID);
    }
}
