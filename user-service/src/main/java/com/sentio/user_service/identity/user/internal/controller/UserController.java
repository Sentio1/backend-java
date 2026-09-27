package com.sentio.user_service.identity.user.internal.controller;

import com.lisovskyi.security.autoconfigure.security.annotation.CurrentUser;
import com.sentio.user_service.identity.organization.api.dto.OrganizationInviteResponse;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberResponse;
import com.sentio.user_service.identity.user.api.SecurityUser;
import com.sentio.user_service.identity.user.api.dto.UserContextResponse;
import com.sentio.user_service.identity.user.internal.controller.dto.request.UserUpdateRequest;
import com.sentio.user_service.identity.user.internal.service.UserServiceImpl;
import com.sentio.user_service.refresh_token.api.dto.SessionResponse;
import com.sentio.user_service.refresh_token.api.service.RefreshTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserServiceImpl userService;
    private final RefreshTokenService refreshTokenService;

    @GetMapping("/me")
    public ResponseEntity<UserContextResponse> me(
            @CurrentUser SecurityUser user
    ) {
        return ResponseEntity.ok(userService.findUserByIdContext(user.getId()));
    }

    @GetMapping("/me/organizations")
    public ResponseEntity<List<OrganizationMemberResponse>> getOrganizations(
            @CurrentUser SecurityUser user
    ) {
        return ResponseEntity.ok(userService.getOrganizations(user.getId()));
    }

    @GetMapping("/me/invites")
    public ResponseEntity<List<OrganizationInviteResponse>> getInvites(
            @CurrentUser SecurityUser user
    ) {
        return ResponseEntity.ok(userService.getInvites(user.email()));
    }

    @GetMapping("/me/sessions")
    public ResponseEntity<List<SessionResponse>> getSessions(
            @CurrentUser SecurityUser user
    ) {
        return ResponseEntity.ok(refreshTokenService.findActiveSessions(user.getId()));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserContextResponse> updateUser(
            @RequestBody @Valid UserUpdateRequest request,
            @CurrentUser SecurityUser user
    ) {
        return ResponseEntity.ok(userService.updateUser(request, user.getId()));
    }
}
