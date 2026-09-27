package com.sentio.user_service.identity.user.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sentio.user_service.identity.user.api.dto.IssuedUserActionToken;
import com.sentio.user_service.identity.user.api.enums.UserActionTokenType;
import com.sentio.user_service.identity.user.internal.exception.InvalidUserActionTokenException;
import com.sentio.user_service.identity.user.internal.model.UserActionToken;
import com.sentio.user_service.identity.user.internal.repository.UserActionTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserActionTokenServiceImplTest {

    private static final long USER_ID = 1L;
    private static final Duration TTL = Duration.ofHours(24);

    @Mock
    private UserActionTokenRepository userActionTokenRepository;

    private UserActionTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserActionTokenServiceImpl(userActionTokenRepository);
    }

    private UserActionToken tokenAt(Instant expiresAt) {
        UserActionToken token = UserActionToken.create(USER_ID, "hash", UserActionTokenType.EMAIL_VERIFICATION, TTL);
        token.setExpiresAt(expiresAt);
        return token;
    }

    @Test
    void issueToken_invalidatesAnyPriorActiveTokenBeforeCreatingTheNewOne() {
        when(userActionTokenRepository.invalidateAllActiveTokens(eq(USER_ID), eq(UserActionTokenType.EMAIL_VERIFICATION), any()))
                .thenReturn(1);

        IssuedUserActionToken issued = service.issueToken(USER_ID, UserActionTokenType.EMAIL_VERIFICATION, TTL);

        verify(userActionTokenRepository).invalidateAllActiveTokens(eq(USER_ID), eq(UserActionTokenType.EMAIL_VERIFICATION), any());

        ArgumentCaptor<UserActionToken> saved = ArgumentCaptor.forClass(UserActionToken.class);
        verify(userActionTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getTokenType()).isEqualTo(UserActionTokenType.EMAIL_VERIFICATION);

        // The raw token handed back to the caller must never be the same string as
        // what got persisted (that's the hash) - only its SHA-256 digest is stored.
        assertThat(issued.rawToken()).isNotBlank().isNotEqualTo(saved.getValue().getHash());
    }

    @Test
    void consumeToken_validToken_marksItUsedAndReturnsTheOwningUserId() {
        UserActionToken token = tokenAt(Instant.now().plus(TTL));
        when(userActionTokenRepository.findByHashAndTokenTypeForUpdate(anyString(), eq(UserActionTokenType.EMAIL_VERIFICATION)))
                .thenReturn(Optional.of(token));

        Long resolvedUserId = service.consumeToken("raw-token", UserActionTokenType.EMAIL_VERIFICATION);

        assertThat(resolvedUserId).isEqualTo(USER_ID);
        assertThat(token.isUsed()).isTrue();
        verify(userActionTokenRepository).save(token);
    }

    @Test
    void consumeToken_expiredToken_isRejectedAndNotConsumed() {
        UserActionToken token = tokenAt(Instant.now().minus(TTL));
        when(userActionTokenRepository.findByHashAndTokenTypeForUpdate(anyString(), eq(UserActionTokenType.EMAIL_VERIFICATION)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.consumeToken("raw-token", UserActionTokenType.EMAIL_VERIFICATION))
                .isInstanceOf(InvalidUserActionTokenException.class);

        assertThat(token.isUsed()).isFalse();
        verify(userActionTokenRepository, never()).save(any());
    }

    @Test
    void consumeToken_alreadyUsedToken_isRejectedOnSecondAttempt() {
        UserActionToken token = tokenAt(Instant.now().plus(TTL));
        token.markAsUsed();
        when(userActionTokenRepository.findByHashAndTokenTypeForUpdate(anyString(), eq(UserActionTokenType.EMAIL_VERIFICATION)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.consumeToken("raw-token", UserActionTokenType.EMAIL_VERIFICATION))
                .isInstanceOf(InvalidUserActionTokenException.class);

        verify(userActionTokenRepository, never()).save(any());
    }

    @Test
    void consumeToken_invalidatedToken_isRejectedEvenThoughItWasNeverUsed() {
        // Superseded by a resend (issueToken's invalidateAllActiveTokens) - must not
        // still be consumable, or invalidation-on-resend achieves nothing.
        UserActionToken token = tokenAt(Instant.now().plus(TTL));
        token.setInvalidatedAt(Instant.now());
        when(userActionTokenRepository.findByHashAndTokenTypeForUpdate(anyString(), eq(UserActionTokenType.EMAIL_VERIFICATION)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.consumeToken("raw-token", UserActionTokenType.EMAIL_VERIFICATION))
                .isInstanceOf(InvalidUserActionTokenException.class);

        verify(userActionTokenRepository, never()).save(any());
    }

    @Test
    void consumeToken_noMatchForHashAndType_isRejected() {
        // Also covers "right hash, wrong type": the lookup is scoped to tokenType, so a
        // PASSWORD_RESET token can never be consumed as an EMAIL_VERIFICATION one - it
        // simply won't be found by this query.
        when(userActionTokenRepository.findByHashAndTokenTypeForUpdate(anyString(), eq(UserActionTokenType.EMAIL_VERIFICATION)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consumeToken("raw-token", UserActionTokenType.EMAIL_VERIFICATION))
                .isInstanceOf(InvalidUserActionTokenException.class);

        verify(userActionTokenRepository, never()).save(any());
    }

    @Test
    void invalidateActiveTokens_delegatesToTheRepositoryAndReturnsItsCount() {
        when(userActionTokenRepository.invalidateAllActiveTokens(eq(USER_ID), eq(UserActionTokenType.EMAIL_VERIFICATION), any()))
                .thenReturn(3);

        int invalidated = service.invalidateActiveTokens(USER_ID, UserActionTokenType.EMAIL_VERIFICATION);

        assertThat(invalidated).isEqualTo(3);
        verify(userActionTokenRepository, times(1))
                .invalidateAllActiveTokens(eq(USER_ID), eq(UserActionTokenType.EMAIL_VERIFICATION), any());
    }
}
