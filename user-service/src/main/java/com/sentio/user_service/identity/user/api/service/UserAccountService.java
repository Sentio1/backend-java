package com.sentio.user_service.identity.user.api.service;

import com.sentio.user_service.identity.user.api.dto.NewExternalUser;
import com.sentio.user_service.identity.user.api.dto.NewLocalUser;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.enums.AuthProvider;
import java.util.Optional;

/**
 * Account-level operations the auth module needs (lookup by credentials/identity, creation,
 * identity linking) - kept separate from {@link UserService} so auth never has to reach into
 * user's repositories or entities.
 */
public interface UserAccountService {

    Optional<UserDto> findActiveById(long userId);

    Optional<UserDto> findActiveByEmail(String email);

    // Unlike the find*Active* lookups this can return a soft-deleted user: the
    // identity row outlives the soft delete, and callers need to tell "deleted"
    // apart from "never seen" (see UserDto.deleted()).
    Optional<UserDto> findByExternalIdentity(AuthProvider provider, String providerUserId);

    // Throws ResourceAlreadyExistsException on an email/phone conflict, including
    // one that only shows up as a unique violation under a concurrent registration.
    UserDto registerLocal(NewLocalUser newUser);

    // Runs in its own transaction (REQUIRES_NEW) so that a unique violation from a
    // concurrent first sign-in only rolls back this attempt, not the caller's
    // transaction. Surfaces that violation as DataIntegrityViolationException.
    UserDto createFromExternalIdentity(NewExternalUser newUser);

    void linkExternalIdentity(long userId, AuthProvider provider, String providerUserId);

    void updatePasswordHash(long userId, String passwordHash);
}
