package com.sentio.user_service.refresh_token.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sentio.user_service.refresh_token.api.dto.RefreshTokenDto;
import com.sentio.user_service.refresh_token.api.enums.RevokeReason;
import com.sentio.user_service.refresh_token.internal.RefreshTokenConstants;
import com.sentio.user_service.refresh_token.internal.exception.RefreshTokenNotFoundException;
import com.sentio.user_service.refresh_token.internal.mapper.RefreshTokenMapper;
import com.sentio.user_service.refresh_token.internal.mapper.RefreshTokenMapperImpl;
import com.sentio.user_service.refresh_token.internal.model.RefreshToken;
import com.sentio.user_service.refresh_token.internal.repository.RefreshTokenRepository;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    private RefreshToken persisted(long id, UUID familyId, Instant familyExpiresAt) {
        RefreshToken token = RefreshToken.builder()
                .userId(7L)
                .familyId(familyId)
                .tokenHash("old-hash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .familyExpiresAt(familyExpiresAt)
                .build();
        token.setId(id);
        return token;
    }

    private void stubSaveAssignsId(long id) {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken rt = inv.getArgument(0);
            rt.setId(id);
            return rt;
        });
    }

    @Test
    void startSession_createsNewFamily() {
        stubSaveAssignsId(1L);
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        Instant familyExpiresAt = Instant.now().plus(30, ChronoUnit.DAYS);

        RefreshTokenDto result = refreshTokenService.startSession(
                7L, "hashed-token", "JUnit-Agent/1.0", ip(), expiresAt, familyExpiresAt);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.familyId()).isNotNull();
        assertThat(result.tokenHash()).isEqualTo("hashed-token");
        assertThat(result.ip()).isEqualTo(ip());
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
        assertThat(result.familyExpiresAt()).isEqualTo(familyExpiresAt);
        assertThat(result.revokedAt()).isNull();
    }

    @Test
    void startSession_neverExpiresAfterTheFamilyLimit() {
        stubSaveAssignsId(1L);
        Instant familyExpiresAt = Instant.now().plus(1, ChronoUnit.DAYS);

        RefreshTokenDto result = refreshTokenService.startSession(
                7L, "hash", "agent", null, Instant.now().plus(7, ChronoUnit.DAYS), familyExpiresAt);

        assertThat(result.expiresAt()).isEqualTo(familyExpiresAt);
    }

    @Test
    void rotate_revokesCurrentAsRotatedAndIssuesSuccessorInSameFamily() {
        UUID familyId = UUID.randomUUID();
        Instant familyExpiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
        RefreshToken current = persisted(1L, familyId, familyExpiresAt);
        when(refreshTokenRepository.findById(1L)).thenReturn(Optional.of(current));
        stubSaveAssignsId(2L);
        Instant newExpiry = Instant.now().plus(7, ChronoUnit.DAYS);

        RefreshTokenDto successor = refreshTokenService.rotate(
                refreshTokenMapper.toDto(current), "new-hash", "agent", ip(), newExpiry);

        assertThat(current.getRevokedAt()).isNotNull();
        assertThat(current.getRevokeReason()).isEqualTo(RevokeReason.ROTATED);
        assertThat(successor.id()).isEqualTo(2L);
        assertThat(successor.familyId()).isEqualTo(familyId);
        assertThat(successor.familyExpiresAt()).isEqualTo(familyExpiresAt);
        assertThat(successor.expiresAt()).isEqualTo(newExpiry);
        assertThat(successor.revokedAt()).isNull();
    }

    // Sliding window, but capped: rotation can't push a session past its absolute limit.
    @Test
    void rotate_clampsSuccessorExpiryToFamilyLimit() {
        Instant familyExpiresAt = Instant.now().plus(2, ChronoUnit.DAYS);
        RefreshToken current = persisted(1L, UUID.randomUUID(), familyExpiresAt);
        when(refreshTokenRepository.findById(1L)).thenReturn(Optional.of(current));
        stubSaveAssignsId(2L);

        RefreshTokenDto successor = refreshTokenService.rotate(
                refreshTokenMapper.toDto(current), "new-hash", "agent", ip(), Instant.now().plus(7, ChronoUnit.DAYS));

        assertThat(successor.expiresAt()).isEqualTo(familyExpiresAt);
    }

    @Test
    void revokeFamily_revokesOnlyActiveTokensOfThatFamily() {
        UUID familyId = UUID.randomUUID();

        refreshTokenService.revokeFamily(familyId, RevokeReason.REUSE_DETECTED);

        verify(refreshTokenRepository).revokeActiveByFamilyId(eq(familyId), eq(RevokeReason.REUSE_DETECTED), any());
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
        assertThat(target.getRevokeReason()).isEqualTo(RevokeReason.LOGOUT);
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
        assertThat(activeSessions.get(0).getRevokeReason()).isEqualTo(RevokeReason.SESSION_LIMIT);
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
