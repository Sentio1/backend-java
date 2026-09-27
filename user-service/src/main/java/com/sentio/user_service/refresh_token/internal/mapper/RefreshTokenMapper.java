package com.sentio.user_service.refresh_token.internal.mapper;

import com.sentio.user_service.refresh_token.api.dto.RefreshTokenDto;
import com.sentio.user_service.refresh_token.api.dto.SessionResponse;
import com.sentio.user_service.refresh_token.internal.model.RefreshToken;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface RefreshTokenMapper {

    RefreshTokenDto toDto(RefreshToken refreshToken);

    // isCurrent is relative to the request making the call (is this the session's
    // own token?), not anything RefreshToken itself knows - the caller has to fill
    // it in after mapping, same as OrganizationMemberResponse.user.
    @Mapping(target = "isCurrent", ignore = true)
    SessionResponse toSessionResponse(RefreshToken refreshToken);
}
