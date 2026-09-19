package com.sentio.user_service.identity.user.api.service;

public interface UserDeletionService {

    /**
     * Soft-deletes the account: removes its memberships and revokes all its sessions (refresh
     * tokens). Refuses (LastOwnerException, 409) while the user is the last OWNER of any
     * organization. The current access token is the caller's (auth's) business - this module
     * never sees raw tokens.
     */
    void deleteUser(long userId);
}
