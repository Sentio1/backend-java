package com.sentio.user_service.auth;

import com.sentio.user_service.auth.dto.AuthResponse;
import com.sentio.user_service.auth.dto.RegisterRequest;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Transactional
    public AuthResponse register(RegisterRequest request) {

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
