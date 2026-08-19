package com.sentio.user_service.user.controller;

import com.sentio.shared.dto.PageResponse;
import com.sentio.user_service.user.dto.UserAdminDetailResponse;
import com.sentio.user_service.user.dto.UserAdminSummaryResponse;
import com.sentio.user_service.user.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
/** UserAdminController class. */
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    public ResponseEntity<PageResponse<UserAdminSummaryResponse>> getAllUsers(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Boolean includeDeleted,
            final Pageable pageable) {
        return ResponseEntity.ok(userAdminService.getAllUsers(email, includeDeleted, pageable));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserAdminDetailResponse> getUserById(@PathVariable long userId) {
        return ResponseEntity.ok(userAdminService.getUser(userId));
    }

    @PostMapping("/{userId}/promote")
    public ResponseEntity<Void> promoteToAdmin(@PathVariable long userId) {
        userAdminService.promoteToAdmin(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/demote")
    public ResponseEntity<Void> demoteFromAdmin(@PathVariable long userId) {
        userAdminService.demoteFromAdmin(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/restore")
    public ResponseEntity<Void> restoreUser(@PathVariable long userId) {
        userAdminService.restoreUser(userId);
        return ResponseEntity.noContent().build();
    }
}
