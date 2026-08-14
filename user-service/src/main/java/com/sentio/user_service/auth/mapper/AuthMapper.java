package com.sentio.user_service.auth.mapper;

import com.sentio.user_service.auth.dto.AuthResponse;
import com.sentio.user_service.auth.dto.LoginRequest;
import com.sentio.user_service.auth.dto.RegisterRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuthMapper {
    AuthResponse toResponse(RegisterRequest registerRequest);
    AuthResponse toResponse(LoginRequest loginRequest);
}
