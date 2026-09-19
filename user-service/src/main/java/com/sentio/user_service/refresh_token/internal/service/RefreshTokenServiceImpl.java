package com.sentio.user_service.refresh_token.internal.service;

import com.sentio.user_service.refresh_token.api.dto.RefreshTokenDto;
import com.sentio.user_service.refresh_token.api.dto.SessionResponse;
import com.sentio.user_service.refresh_token.api.enums.RevokeReason;
import com.sentio.user_service.refresh_token.api.service.RefreshTokenService;
import com.sentio.user_service.refresh_token.internal.RefreshTokenConstants;
import com.sentio.user_service.refresh_token.internal.exception.RefreshTokenNotFoundException;
import com.sentio.user_service.refresh_token.internal.mapper.RefreshTokenMapper;
import com.sentio.user_service.refresh_token.internal.model.RefreshToken;
import com.sentio.user_service.refresh_token.internal.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenMapper refreshTokenMapper;

    @Override
    @Transactional
    public RefreshTokenDto startSession(
            long userId, String tokenHash, String userAgent, InetAddress ip,
            Instant expiresAt, Instant familyExpiresAt
    ) {
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .familyId(UUID.randomUUID())
                .tokenHash(tokenHash)
                .userAgent(userAgent)
                .ip(ip)
                .expiresAt(earliest(expiresAt, familyExpiresAt))
                .familyExpiresAt(familyExpiresAt)
                .build();

        return refreshTokenMapper.toDto(refreshTokenRepository.save(refreshToken));
    }

    @Override
    @Transactional
    public RefreshTokenDto rotate(
            RefreshTokenDto current, String newTokenHash, String userAgent, InetAddress ip, Instant expiresAt
    ) {
        RefreshToken currentToken = refreshTokenRepository.findById(current.id())
                .orElseThrow(() -> new RefreshTokenNotFoundException("id", current.id()));

        currentToken.revoke(RevokeReason.ROTATED, Instant.now());

        RefreshToken successor = RefreshToken.builder()
                .userId(currentToken.getUserId())
                .familyId(currentToken.getFamilyId())
                .tokenHash(newTokenHash)
                .userAgent(userAgent)
                .ip(ip)
                .expiresAt(earliest(expiresAt, currentToken.getFamilyExpiresAt()))
                .familyExpiresAt(currentToken.getFamilyExpiresAt())
                .build();

        return refreshTokenMapper.toDto(refreshTokenRepository.save(successor));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshTokenDto> findByTokenHash(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash)
                .map(refreshTokenMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> findActiveSessions(long userId) {
        return refreshTokenRepository.findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(userId).stream()
                .map(refreshTokenMapper::toSessionResponse)
                .toList();
    }

    @Override
    @Transactional
    public void revokeSession(long userId, long refreshTokenId) {
        RefreshToken token = refreshTokenRepository.findByIdAndUserId(refreshTokenId, userId)
                .orElseThrow(() -> new RefreshTokenNotFoundException("id", refreshTokenId));
        token.revoke(RevokeReason.LOGOUT, Instant.now());
    }

    @Override
    @Transactional
    public void revokeSessions(List<Long> refreshTokenIds) {
        List<RefreshToken> refreshTokens = refreshTokenRepository.findAllById(refreshTokenIds);
        Instant now = Instant.now();
        refreshTokens.forEach(refreshToken -> refreshToken.revoke(RevokeReason.LOGOUT, now));
        refreshTokenRepository.saveAll(refreshTokens);
    }

    @Override
    @Transactional
    public void revokeAllActiveForUser(long userId) {
        List<RefreshToken> refreshTokensForUser = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        Instant now = Instant.now();
        refreshTokensForUser.forEach(refreshToken -> refreshToken.revoke(RevokeReason.ALL_SESSIONS_REVOKED, now));
        refreshTokenRepository.saveAll(refreshTokensForUser);
    }

    @Override
    @Transactional
    public void revokeFamily(UUID familyId, RevokeReason reason) {
        int revoked = refreshTokenRepository.revokeActiveByFamilyId(familyId, reason, Instant.now());
        log.debug("Revoked {} active refresh token(s) of family {} ({})", revoked, familyId, reason);
    }

    @Override
    @Transactional
    public void enforceActiveSessionLimit(long userId) {
        List<RefreshToken> activeSessions =
                refreshTokenRepository.findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(userId);

        int overLimitBy = activeSessions.size() - RefreshTokenConstants.MAX_ACTIVE_SESSIONS + 1;
        if (overLimitBy <= 0) {
            return;
        }

        Instant now = Instant.now();
        List<RefreshToken> oldest = activeSessions.subList(0, overLimitBy);
        oldest.forEach(session -> session.revoke(RevokeReason.SESSION_LIMIT, now));
        refreshTokenRepository.saveAll(oldest);
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
