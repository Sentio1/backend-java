package com.sentio.user_service.auth;

import com.sentio.user_service.auth.dto.AuthResponse;
import com.sentio.user_service.auth.dto.RegisterRequest;
import com.sentio.user_service.auth.mapper.AuthMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthMapper authMapper;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        return null;
    }

    @Transactional
    public void login() {

    }

    @Transactional
    public void refresh() {

    }

    @Transactional
    public void logout() {

    }
}
