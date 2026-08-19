package com.sentio.user_service.auth.oauth;

import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.auth.AuthGuards;
import com.sentio.user_service.auth.oauth.dto.GoogleIdentity;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.entity.UserIdentity;
import com.sentio.user_service.user.enums.AuthProvider;
import com.sentio.user_service.user.repository.UserIdentityRepository;
import com.sentio.user_service.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves a Google sign-in to a {@link User}: reuses the account if this Google identity was seen
 * before, links the identity onto an existing local account with the same email ("зліплюємо" -
 * SEN-15), or creates a brand new user. Organization/membership/token concerns live elsewhere -
 * this class only answers "who is this".
 */
@Component
@RequiredArgsConstructor
public class GoogleAccountResolver {

    private final AuthGuards authGuards;

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;

    public User resolveOrCreate(GoogleIdentity identity) {
        Optional<UserIdentity> existingIdentity =
                userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, identity.sub());

        if (existingIdentity.isPresent()) {
            User activeUser = existingIdentity.get().getUser();
            authGuards.assertNotDeleted(activeUser);
            return activeUser;
        }

        // Google can, in rare edge cases (e.g. unverified Workspace domains), hand out
        // a token for an email it hasn't verified. Auto-linking that to an existing
        // local account would let an attacker take it over - refuse instead.
        if (!identity.emailVerified()) {
            throw new UnauthorizedException("Google account email is not verified");
        }

        Optional<User> existingUserByEmail = userRepository.findByEmail(identity.email());
        if (existingUserByEmail.isPresent()) {
            User user = existingUserByEmail.get();
            authGuards.assertNotDeleted(user);
            linkGoogleIdentity(user, identity.sub());
            return user;
        }

        User user = User.builder()
                .email(identity.email())
                .firstName(identity.firstName())
                .lastName(identity.lastName())
                .build();
        userRepository.save(user);

        linkGoogleIdentity(user, identity.sub());
        return user;
    }

    private void linkGoogleIdentity(User user, String googleSub) {
        UserIdentity identity = UserIdentity.builder()
                .user(user)
                .provider(AuthProvider.GOOGLE)
                .providerUserId(googleSub)
                .build();

        userIdentityRepository.save(identity);
    }
}
