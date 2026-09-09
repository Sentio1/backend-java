package com.sentio.user_service.user.dto;

import lombok.Builder;

@Builder
/** UserContextResponse record. */
public record UserContextResponse(
        Long id, String email, String lastName, String firstName, String orgName, String orgRole) {}
