package com.sentio.user_service.refresh_token.finder;

import com.sentio.shared.entity.finder.AbstractEntityFinder;
import com.sentio.user_service.refresh_token.RefreshToken;
import com.sentio.user_service.refresh_token.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RefreshTokenFinderImpl extends AbstractEntityFinder<RefreshToken, Long> implements RefreshTokenFinder {

    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    protected JpaRepository<RefreshToken, Long> getRepository() {
        return refreshTokenRepository;
    }

    @Override
    protected String getEntityName() {
        return "RefreshToken";
    }

    @Override
    public List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(long userId) {
        return refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
    }

    @Override
    public List<RefreshToken> findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(long userId) {
        return refreshTokenRepository.findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(userId);
    }

    @Override
    public Optional<RefreshToken> findByTokenHashOptional(String tokenHash) {
        requireNonNull(tokenHash);
        return refreshTokenRepository.findByTokenHash(tokenHash);
    }
}
