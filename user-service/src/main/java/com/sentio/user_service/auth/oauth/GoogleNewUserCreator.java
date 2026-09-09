package com.sentio.user_service.auth.oauth;

import com.sentio.user_service.auth.oauth.dto.GoogleIdentity;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.entity.UserIdentity;
import com.sentio.user_service.user.enums.AuthProvider;
import com.sentio.user_service.user.repository.UserIdentityRepository;
import com.sentio.user_service.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Split out of {@link GoogleAccountResolver} for one reason: {@code Propagation.REQUIRES_NEW}.
 * When two concurrent first-time Google sign-ins race, the loser's INSERT fails on the {@code
 * users.email} unique index - and in Postgres, a failed statement aborts the *whole* transaction,
 * not just that statement. If the insert attempt ran in the caller's own transaction, catching the
 * {@code DataIntegrityViolationException} in Java wouldn't help: every later query on that same
 * connection (e.g. {@code resolveExistingAfterConflict}'s lookup of the winner) would still blow up
 * with "current transaction is aborted". Running the attempt in its own transaction means only that
 * one rolls back on conflict - the caller's transaction is never touched, so it can still look up
 * the winner afterward.
 */
@Component
@RequiredArgsConstructor
class GoogleNewUserCreator {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User createAndLink(GoogleIdentity identity) {
        User user = User.builder()
                .email(identity.email())
                .firstName(identity.firstName())
                .lastName(identity.lastName())
                .build();

        User savedUser = userRepository.saveAndFlush(user);
        linkGoogleIdentity(savedUser, identity.sub());
        return savedUser;
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
