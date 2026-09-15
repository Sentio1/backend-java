package com.sentio.user_service.refresh_token.internal.service;

import com.sentio.user_service.refresh_token.api.dto.RefreshTokenDto;
import com.sentio.user_service.refresh_token.api.dto.SessionResponse;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenMapper refreshTokenMapper;

    @Override
    @Transactional
    public RefreshTokenDto issue(long userId, String tokenHash, String userAgent, InetAddress ip, Instant expiresAt) {
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .userAgent(userAgent)
                .ip(ip)
                .expiresAt(expiresAt)
                .build();

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        return refreshTokenMapper.toDto(saved);
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
        token.setRevokedAt(Instant.now());
    }

    @Override
    @Transactional
    public void revokeSessions(List<Long> refreshTokenIds) {
        List<RefreshToken> refreshTokens = refreshTokenRepository.findAllById(refreshTokenIds);
        refreshTokens.forEach(refreshToken -> refreshToken.setRevokedAt(Instant.now()));
        refreshTokenRepository.saveAll(refreshTokens);
    }

    @Override
    @Transactional
    public void revokeAllActiveForUser(long userId) {
        List<RefreshToken> refreshTokensForUser = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        refreshTokensForUser.forEach(refreshToken -> refreshToken.setRevokedAt(Instant.now()));
        refreshTokenRepository.saveAll(refreshTokensForUser);
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
        oldest.forEach(session -> session.setRevokedAt(now));
        refreshTokenRepository.saveAll(oldest);
    }
}
