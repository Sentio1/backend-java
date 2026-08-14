package com.sentio.user_service.auth.dto;

import lombok.Builder;

@Builder
public record UserContextResponse(
        Long id,
        String email,
        String lastName,
        String firstName,
        String orgName,
        String orgRole
) {}
