package com.sentio.user_service.refresh_token.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sentio.user_service.refresh_token.api.dto.RefreshTokenDto;
import com.sentio.user_service.refresh_token.internal.RefreshTokenConstants;
import com.sentio.user_service.refresh_token.internal.exception.RefreshTokenNotFoundException;
import com.sentio.user_service.refresh_token.internal.mapper.RefreshTokenMapper;
import com.sentio.user_service.refresh_token.internal.mapper.RefreshTokenMapperImpl;
import com.sentio.user_service.refresh_token.internal.model.RefreshToken;
import com.sentio.user_service.refresh_token.internal.repository.RefreshTokenRepository;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RefreshTokenMapper stays real (pure MapStruct mapping, no I/O) so issue()'s DTO actually reflects
 * what got built, instead of a mocked passthrough.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private final RefreshTokenMapper refreshTokenMapper = new RefreshTokenMapperImpl();

    private RefreshTokenServiceImpl refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenServiceImpl(refreshTokenRepository, refreshTokenMapper);
    }

    private InetAddress ip() {
        try {
            return InetAddress.getByName("203.0.113.5");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void issue_savesAndReturnsMappedDto() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken rt = inv.getArgument(0);
            rt.setId(1L);
            return rt;
        });

        Instant expiresAt = Instant.now().plusSeconds(3600);
        RefreshTokenDto result = refreshTokenService.issue(7L, "hashed-token", "JUnit-Agent/1.0", ip(), expiresAt);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.tokenHash()).isEqualTo("hashed-token");
        assertThat(result.userAgent()).isEqualTo("JUnit-Agent/1.0");
        assertThat(result.ip()).isEqualTo(ip());
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
        assertThat(result.revokedAt()).isNull();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getRevokedAt()).isNull();
    }

    @Test
    void findByTokenHash_mapsPresentToken() {
        RefreshToken existing = RefreshToken.builder()
                .userId(7L)
                .tokenHash("hashed-token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        existing.setId(1L);
        when(refreshTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(existing));

        Optional<RefreshTokenDto> result = refreshTokenService.findByTokenHash("hashed-token");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(1L);
        assertThat(result.get().userId()).isEqualTo(7L);
    }

    @Test
    void findByTokenHash_unknownHash_returnsEmpty() {
        when(refreshTokenRepository.findByTokenHash("nope")).thenReturn(Optional.empty());

        assertThat(refreshTokenService.findByTokenHash("nope")).isEmpty();
    }

    @Test
    void revokeSession_revokesOnlyThatOneToken() {
        RefreshToken target = RefreshToken.builder()
                .userId(7L)
                .tokenHash("hash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(refreshTokenRepository.findByIdAndUserId(99L, 7L)).thenReturn(Optional.of(target));

        refreshTokenService.revokeSession(7L, 99L);

        assertThat(target.getRevokedAt()).isNotNull();
    }

    // Scoping the lookup to (id, userId) together - not just id - means a session
    // that exists but belongs to someone else 404s exactly like one that doesn't
    // exist at all, instead of leaking that another user's session id is valid.
    @Test
    void revokeSession_unknownOrNotOwnedByCaller_throwsRefreshTokenNotFoundException() {
        when(refreshTokenRepository.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.revokeSession(7L, 99L))
                .isInstanceOf(RefreshTokenNotFoundException.class);
    }

    @Test
    void revokeSessions_revokesExactlyTheGivenIds() {
        RefreshToken a = RefreshToken.builder().userId(7L).tokenHash("a").expiresAt(Instant.now()).build();
        RefreshToken b = RefreshToken.builder().userId(7L).tokenHash("b").expiresAt(Instant.now()).build();
        when(refreshTokenRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(a, b));

        refreshTokenService.revokeSessions(List.of(1L, 2L));

        assertThat(a.getRevokedAt()).isNotNull();
        assertThat(b.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).saveAll(List.of(a, b));
    }

    @Test
    void revokeAllActiveForUser_revokesEveryActiveSessionForThatUser() {
        RefreshToken a = RefreshToken.builder().userId(7L).tokenHash("a").expiresAt(Instant.now()).build();
        RefreshToken b = RefreshToken.builder().userId(7L).tokenHash("b").expiresAt(Instant.now()).build();
        when(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(7L)).thenReturn(List.of(a, b));

        refreshTokenService.revokeAllActiveForUser(7L);

        assertThat(a.getRevokedAt()).isNotNull();
        assertThat(b.getRevokedAt()).isNotNull();
    }

    @Test
    void enforceActiveSessionLimit_beyondLimit_revokesOnlyTheOldestOverflow() {
        List<RefreshToken> activeSessions = new ArrayList<>();
        for (int i = 0; i < RefreshTokenConstants.MAX_ACTIVE_SESSIONS; i++) {
            activeSessions.add(RefreshToken.builder()
                    .userId(7L)
                    .tokenHash("hash-" + i)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build());
        }
        when(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(7L))
                .thenReturn(activeSessions);

        // Already at the limit - the caller is about to issue one more, so exactly
        // the oldest existing session must be revoked to make room for it.
        refreshTokenService.enforceActiveSessionLimit(7L);

        ArgumentCaptor<List<RefreshToken>> captor = ArgumentCaptor.forClass(List.class);
        verify(refreshTokenRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(activeSessions.get(0));
        assertThat(activeSessions.get(0).getRevokedAt()).isNotNull();
        activeSessions.subList(1, activeSessions.size())
                .forEach(session -> assertThat(session.getRevokedAt()).isNull());
    }

    @Test
    void enforceActiveSessionLimit_underLimit_revokesNothing() {
        when(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(7L))
                .thenReturn(List.of());

        refreshTokenService.enforceActiveSessionLimit(7L);

        verify(refreshTokenRepository, never()).saveAll(any());
    }
}
