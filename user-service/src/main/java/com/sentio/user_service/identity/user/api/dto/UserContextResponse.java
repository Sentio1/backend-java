package com.sentio.user_service.identity.user.api.dto;

import lombok.Builder;

@Builder
public record UserContextResponse(
        long id,
        String email,
        String lastName,
        String firstName,
        String orgName,
        String orgRole
) {}
