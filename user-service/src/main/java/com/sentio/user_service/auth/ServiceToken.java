package com.sentio.user_service.auth;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtProperties;
import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.auth.dto.request.ServiceTokenRequest;
import com.sentio.user_service.auth.dto.response.ServiceTokenResult;
import com.sentio.user_service.auth.token.TokenIssuer;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.enums.PlatformRole;
import com.sentio.user_service.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.sentio.user_service.auth.AuthService.INVALID_ERROR_MSG;

@Service
@RequiredArgsConstructor
public class ServiceToken {

    private final UserRepository userRepository;
    private final TokenIssuer tokenIssuer;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ServiceTokenResult serviceToken(ServiceTokenRequest request) {
        User user = userRepository.findByEmail(request.clientId())
                .orElseThrow(() -> new UnauthorizedException(INVALID_ERROR_MSG));

        if (user.getPassword() == null || !passwordEncoder.matches(request.secret(), user.getPassword())) {
            throw new UnauthorizedException(INVALID_ERROR_MSG);
        }

        if (user.getPlatformRole() != PlatformRole.SERVICE) {
            throw new UnauthorizedException(INVALID_ERROR_MSG);
        }

        return new ServiceTokenResult(
                tokenIssuer.issueServiceAccessToken(user),
                "Bearer",
                jwtProperties.getAccessTokenExpiration()
        );
    }
}
