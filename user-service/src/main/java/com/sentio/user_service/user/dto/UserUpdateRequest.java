package com.sentio.user_service.user.dto;

/** UserUpdateRequest record. */
public record UserUpdateRequest(String phoneNumber, String lastName, String firstName, String middleName) {}
