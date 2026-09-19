package com.sentio.user_service.identity.auth.service;

import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.identity.auth.dto.request.ServiceTokenRequest;
import com.sentio.user_service.identity.auth.dto.response.ServiceTokenResult;
import com.sentio.user_service.identity.auth.token.TokenIssuer;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.enums.PlatformRole;
import com.sentio.user_service.identity.user.api.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.sentio.user_service.identity.auth.AuthConstants.INVALID_CREDENTIALS_MSG;

@Service
@RequiredArgsConstructor
public class ServiceToken {

    // RFC 6749 §5.1 token_type for a client_credentials-style response - tells the
    // caller to send it as "Authorization: Bearer <token>".
    private static final String TOKEN_TYPE = "Bearer";

    private final UserAccountService userAccountService;
    private final TokenIssuer tokenIssuer;
    private final PasswordVerifier passwordVerifier;

    @Transactional(readOnly = true)
    public ServiceTokenResult serviceToken(ServiceTokenRequest request) {
        Optional<UserDto> found = userAccountService.findActiveByEmail(request.clientId());
        // A NULL hash ("secret not provisioned in this environment") never matches.
        boolean secretMatches = passwordVerifier.matches(
                request.secret(), found.map(UserDto::password).orElse(null));

        if (found.isEmpty() || !secretMatches || found.get().platformRole() != PlatformRole.SERVICE) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MSG);
        }

        return new ServiceTokenResult(
                tokenIssuer.issueServiceAccessToken(found.get()), TOKEN_TYPE, tokenIssuer.accessTokenTtlSeconds());
    }
}
