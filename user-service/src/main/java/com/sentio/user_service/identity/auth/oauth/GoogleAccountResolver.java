package com.sentio.user_service.identity.auth.oauth;

import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.identity.auth.oauth.dto.GoogleIdentity;
import com.sentio.user_service.identity.auth.oauth.exception.AccountLinkingConflictException;
import com.sentio.user_service.identity.auth.service.AuthGuards;
import com.sentio.user_service.identity.user.api.dto.NewExternalUser;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.enums.AuthProvider;
import com.sentio.user_service.identity.user.api.service.UserAccountService;
import com.sentio.user_service.refresh_token.api.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves a Google sign-in to a user: reuses the account if this Google identity was seen before,
 * links the identity onto an existing local account with the same (verified) email ("зліплюємо" -
 * SEN-15), or creates a brand new user. Organization/membership/token concerns live elsewhere -
 * this class only answers "who is this".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleAccountResolver {

    private final AuthGuards authGuards;
    private final UserAccountService userAccountService;
    private final RefreshTokenService refreshTokenService;

    public UserDto resolveOrCreate(GoogleIdentity identity) {
        // Google can, in rare edge cases (e.g. unverified Workspace domains), hand out
        // a token for an email it hasn't verified - never trust that email for anything.
        if (!identity.emailVerified()) {
            log.warn("Pre-ATO prevention: Google email {} is not verified by Google", identity.email());
            throw new UnauthorizedException("Google email is not verified");
        }

        Optional<UserDto> existingByIdentity =
                userAccountService.findByExternalIdentity(AuthProvider.GOOGLE, identity.sub());
        if (existingByIdentity.isPresent()) {
            UserDto user = existingByIdentity.get();
            authGuards.assertNotDeleted(user);
            return user;
        }

        Optional<UserDto> existingByEmail = userAccountService.findActiveByEmail(identity.email());
        if (existingByEmail.isPresent()) {
            return linkGoogleIdentity(existingByEmail.get(), identity.sub());
        }

        try {
            return userAccountService.createFromExternalIdentity(new NewExternalUser(
                    AuthProvider.GOOGLE, identity.sub(), identity.email(), identity.firstName(), identity.lastName()));
        } catch (DataIntegrityViolationException _) {
            // createFromExternalIdentity runs in its own transaction, so this one is
            // still usable - a concurrent first sign-in won the race, look it up.
            return resolveExistingAfterConflict(identity);
        }
    }

    private UserDto resolveExistingAfterConflict(GoogleIdentity identity) {
        Optional<UserDto> byGoogle = userAccountService.findByExternalIdentity(AuthProvider.GOOGLE, identity.sub());
        if (byGoogle.isPresent()) {
            UserDto user = byGoogle.get();
            authGuards.assertNotDeleted(user);
            return user;
        }

        return userAccountService.findActiveByEmail(identity.email())
                .map(user -> linkGoogleIdentity(user, identity.sub()))
                .orElseThrow(() -> new UnauthorizedException("Unable to resolve account after conflict"));
    }

    private UserDto linkGoogleIdentity(UserDto user, String googleSub) {
        // Pre-account-takeover: anyone can register a local account on someone else's
        // email (no verification yet). Auto-linking the real owner's Google identity to
        // it would hand the attacker - who still knows the local password - the account.
        if (!user.emailVerified()) {
            log.warn("Pre-ATO blocked: attempt to link Google identity to unverified local account: {}", user.email());
            throw new AccountLinkingConflictException(
                    "An unverified account with this email already exists. Please verify your email or reset your password first.");
        }

        userAccountService.linkExternalIdentity(user.id(), AuthProvider.GOOGLE, googleSub);
        refreshTokenService.revokeAllActiveForUser(user.id());
        log.info("Successfully linked Google identity to user id: {}", user.id());
        return user;
    }
}
