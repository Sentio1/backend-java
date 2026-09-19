package com.sentio.user_service.identity.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.identity.auth.oauth.dto.GoogleIdentity;
import com.sentio.user_service.identity.auth.oauth.exception.AccountLinkingConflictException;
import com.sentio.user_service.identity.auth.service.AuthGuards;
import com.sentio.user_service.identity.user.api.dto.NewExternalUser;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.enums.AuthProvider;
import com.sentio.user_service.identity.user.api.enums.PlatformRole;
import com.sentio.user_service.identity.user.api.service.UserAccountService;
import com.sentio.user_service.refresh_token.api.service.RefreshTokenService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class GoogleAccountResolverTest {

    private static final String SUB = "google-sub-1";
    private static final String EMAIL = "user@sentio.dev";

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private RefreshTokenService refreshTokenService;

    // Real guards - they're pure checks on the DTO, nothing worth mocking.
    private GoogleAccountResolver googleAccountResolver;

    @BeforeEach
    void setUp() {
        googleAccountResolver = new GoogleAccountResolver(new AuthGuards(), userAccountService, refreshTokenService);
    }

    private GoogleIdentity identity(boolean emailVerified) {
        return new GoogleIdentity(SUB, EMAIL, "Jane", "Doe", emailVerified);
    }

    private static UserDto user(long id, boolean emailVerified, boolean deleted) {
        return new UserDto(id, EMAIL, null, PlatformRole.USER, emailVerified ? Instant.now() : null, deleted);
    }

    @Test
    void knownGoogleIdentity_returnsItsUserWithoutLookingUpByEmail() {
        UserDto existing = user(1L, true, false);
        when(userAccountService.findByExternalIdentity(AuthProvider.GOOGLE, SUB)).thenReturn(Optional.of(existing));

        assertThat(googleAccountResolver.resolveOrCreate(identity(true))).isEqualTo(existing);

        verify(userAccountService, never()).findActiveByEmail(any());
        verify(userAccountService, never()).linkExternalIdentity(anyLong(), any(), anyString());
    }

    @Test
    void knownGoogleIdentityOfDeletedUser_isRejected() {
        when(userAccountService.findByExternalIdentity(AuthProvider.GOOGLE, SUB))
                .thenReturn(Optional.of(user(1L, true, true)));

        assertThatThrownBy(() -> googleAccountResolver.resolveOrCreate(identity(true)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void verifiedLocalAccountWithSameEmail_linksGoogleIdentityAndRevokesItsSessions() {
        UserDto local = user(1L, true, false);
        when(userAccountService.findByExternalIdentity(AuthProvider.GOOGLE, SUB)).thenReturn(Optional.empty());
        when(userAccountService.findActiveByEmail(EMAIL)).thenReturn(Optional.of(local));

        assertThat(googleAccountResolver.resolveOrCreate(identity(true))).isEqualTo(local);

        verify(userAccountService).linkExternalIdentity(1L, AuthProvider.GOOGLE, SUB);
        verify(refreshTokenService).revokeAllActiveForUser(1L);
    }

    // Pre-account-takeover: an attacker could have registered this email locally
    // (no verification yet) - linking the real owner's Google to it would let the
    // attacker, who knows the local password, into the victim's account.
    @Test
    void unverifiedLocalAccountWithSameEmail_isNotLinked() {
        when(userAccountService.findByExternalIdentity(AuthProvider.GOOGLE, SUB)).thenReturn(Optional.empty());
        when(userAccountService.findActiveByEmail(EMAIL)).thenReturn(Optional.of(user(1L, false, false)));

        assertThatThrownBy(() -> googleAccountResolver.resolveOrCreate(identity(true)))
                .isInstanceOf(AccountLinkingConflictException.class);

        verify(userAccountService, never()).linkExternalIdentity(anyLong(), any(), anyString());
        verify(refreshTokenService, never()).revokeAllActiveForUser(anyLong());
    }

    @Test
    void unverifiedGoogleEmail_isRejectedBeforeAnyLookup() {
        assertThatThrownBy(() -> googleAccountResolver.resolveOrCreate(identity(false)))
                .isInstanceOf(UnauthorizedException.class);

        verifyNoInteractions(userAccountService, refreshTokenService);
    }

    @Test
    void noMatchAtAll_createsUserFromGoogleIdentity() {
        UserDto created = user(5L, true, false);
        when(userAccountService.findByExternalIdentity(AuthProvider.GOOGLE, SUB)).thenReturn(Optional.empty());
        when(userAccountService.findActiveByEmail(EMAIL)).thenReturn(Optional.empty());
        when(userAccountService.createFromExternalIdentity(
                new NewExternalUser(AuthProvider.GOOGLE, SUB, EMAIL, "Jane", "Doe")))
                .thenReturn(created);

        assertThat(googleAccountResolver.resolveOrCreate(identity(true))).isEqualTo(created);
    }

    // createFromExternalIdentity runs in its own REQUIRES_NEW transaction specifically
    // so this fallback is reachable - on Postgres a failed statement aborts the whole
    // transaction, which would otherwise poison the lookup below too.
    @Test
    void conflictOnCreate_fallsBackToTheWinnerResolvedByGoogleIdentity() {
        UserDto winner = user(7L, true, false);
        when(userAccountService.findByExternalIdentity(AuthProvider.GOOGLE, SUB))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(userAccountService.findActiveByEmail(EMAIL)).thenReturn(Optional.empty());
        when(userAccountService.createFromExternalIdentity(any()))
                .thenThrow(new DataIntegrityViolationException("conflict"));

        assertThat(googleAccountResolver.resolveOrCreate(identity(true))).isEqualTo(winner);
    }

    @Test
    void conflictOnCreate_fallsBackToVerifiedAccountWithSameEmail_andLinksIt() {
        UserDto winner = user(7L, true, false);
        when(userAccountService.findByExternalIdentity(AuthProvider.GOOGLE, SUB)).thenReturn(Optional.empty());
        when(userAccountService.findActiveByEmail(EMAIL)).thenReturn(Optional.empty(), Optional.of(winner));
        when(userAccountService.createFromExternalIdentity(any()))
                .thenThrow(new DataIntegrityViolationException("conflict"));

        assertThat(googleAccountResolver.resolveOrCreate(identity(true))).isEqualTo(winner);
        verify(userAccountService).linkExternalIdentity(7L, AuthProvider.GOOGLE, SUB);
    }
}
