package com.sentio.user_service.user;

import com.lisovskyi.security.autoconfigure.cookie.CookieService;
import com.lisovskyi.security.autoconfigure.security.annotation.CurrentUser;
import com.sentio.user_service.organization.dto.organization_invite.OrganizationInviteAcceptResponse;
import com.sentio.user_service.organization.dto.organization_invite.OrganizationInviteResponse;
import com.sentio.user_service.organization.dto.organization_member.OrganizationMemberResponse;
import com.sentio.user_service.organization.service.OrganizationInviteService;
import com.sentio.user_service.user.dto.UserContextResponse;
import com.sentio.user_service.security.SecurityUser;
import com.sentio.user_service.user.dto.UserUpdateRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final OrganizationInviteService organizationInviteService;
    private final CookieService cookieService;


    @GetMapping("/me")
    public ResponseEntity<UserContextResponse> me(
            @CurrentUser SecurityUser user
    ) {
        return ResponseEntity.ok(userService.findUserById(user.getId()));
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
        return ResponseEntity.ok(userService.getInvites(user.getUser().getEmail()));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserContextResponse> updateUser(
            @RequestBody @Valid UserUpdateRequest request,
            @CurrentUser SecurityUser user
    ) {
        return ResponseEntity.ok(userService.updateUser(request, user.getId()));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteUser(
            @CurrentUser SecurityUser user,
            final HttpServletRequest request
            ) {
        String accessToken = cookieService.getAccessTokenCookie(request)
                .orElse(null);

        userService.deleteUser(user.getId(), accessToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/invites/{token}/accept")
    public ResponseEntity<OrganizationInviteAcceptResponse> acceptOrganizationInvite(
        @PathVariable String token,
        @CurrentUser SecurityUser user
    ) {
        return ResponseEntity.ok(organizationInviteService.acceptInvite(token, user.getId()));
    }
}
