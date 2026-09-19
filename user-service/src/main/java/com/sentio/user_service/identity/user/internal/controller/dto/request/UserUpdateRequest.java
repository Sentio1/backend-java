package com.sentio.user_service.identity.user.internal.controller.dto.request;

public record UserUpdateRequest(
        String phoneNumber,
        String lastName,
        String firstName,
        String middleName
) {}
