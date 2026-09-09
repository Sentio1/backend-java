package com.sentio.user_service.refresh_token.finder;

import com.sentio.shared.entity.finder.EntityFinder;
import com.sentio.user_service.refresh_token.RefreshToken;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenFinder extends EntityFinder<RefreshToken, Long> {

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(long userId);

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(long userId);

    Optional<RefreshToken> findByTokenHashOptional(String tokenHash);
}
