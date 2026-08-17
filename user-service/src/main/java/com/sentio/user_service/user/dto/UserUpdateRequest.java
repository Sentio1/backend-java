package com.sentio.user_service.user.dto;

public record UserUpdateRequest(
        String phoneNumber,
        String lastName,
        String firstName,
        String middleName
) { }
