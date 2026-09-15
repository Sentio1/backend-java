package com.sentio.user_service.identity.user.internal.service;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.sentio.shared.dto.PageResponse;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberResponse;
import com.sentio.user_service.identity.organization.api.service.OrganizationMemberService;
import com.sentio.user_service.identity.user.internal.controller.dto.response.UserAdminDetailResponse;
import com.sentio.user_service.identity.user.internal.controller.dto.response.UserAdminSummaryResponse;
import com.sentio.user_service.identity.user.internal.entity.User;
import com.sentio.user_service.identity.user.api.enums.PlatformRole;
import com.sentio.user_service.identity.user.internal.exception.UserNotFoundException;
import com.sentio.user_service.identity.user.internal.mapper.UserMapper;
import com.sentio.user_service.identity.user.internal.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminServiceImpl {

    private final UserRepository userRepository;
    private final OrganizationMemberService organizationMemberService;

    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public PageResponse<UserAdminSummaryResponse> getAllUsers(
            String email,
            Boolean includeDeleted,
            final Pageable pageable
    ) {
        log.debug("Admin fetching all users with email: {}, includeDeleted: {}", email, includeDeleted);
        return PageResponse.of(userRepository
                .search(email, includeDeleted, pageable)
                .map(user -> userMapper.toUserAdminSummaryResponse(user, getOrganizationCount(user))));
    }

    @Transactional(readOnly = true)
    public UserAdminDetailResponse getUser(long userId) {
        log.debug("Admin fetching details for userId: {}", userId);
        User user = userRepository
                .findByIdIncludingDeleted(userId)
                .orElseThrow(() -> new UserNotFoundException("id", userId));

        List<OrganizationMemberResponse> organizations = organizationMemberService.findAllByUserId(userId);

        return userMapper.toUserAdminDetailResponse(user, organizations);
    }

    @Transactional
    public void promoteToAdmin(long userId) {
        log.debug("Attempting to promote userId: {} to ADMIN", userId);
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("id", userId));
        user.setPlatformRole(PlatformRole.ADMIN);
        userRepository.save(user);
        log.info("Successfully promoted userId: {} to ADMIN", userId);
    }

    @Transactional
    public void demoteFromAdmin(long userId) {
        log.debug("Attempting to demote userId: {} from ADMIN", userId);
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("id", userId));

        if (user.getPlatformRole() == PlatformRole.ADMIN
                && userRepository.findActiveByPlatformRoleWithLock(PlatformRole.ADMIN).size() <= 1) {
            log.warn("Cannot demote userId {}: they are the last platform admin", userId);
            throw new IllegalStateException("Cannot demote: " + user.getEmail() + " is the last platform admin.");
        }

        user.setPlatformRole(PlatformRole.USER);
        userRepository.save(user);
        log.info("Successfully demoted userId: {} to USER", userId);
    }

    @Transactional
    public void restoreUser(long userId) {
        log.debug("Attempting to restore deleted userId: {}", userId);
        User user = userRepository
                .findByIdIncludingDeleted(userId)
                .orElseThrow(() -> new UserNotFoundException("id", userId));

        if (!user.isDeleted()) {
            log.warn("Restore failed: userId {} is not deleted", userId);
            throw new IllegalArgumentException("User " + userId + " is not deleted");
        }

        // цей запит поверне лише true, якщо в користувача буде активна пошта
        if (userRepository.existsByEmail(user.getEmail())) {
            log.warn("Restore failed: active user with email {} already exists", user.getEmail());
            throw new ResourceAlreadyExistsException("User with email " + user.getEmail() + " is already existed");
        }

        user.setDeletedAt(null);
        userRepository.save(user);

        log.info("Restored user {}", userId);
    }

    private int getOrganizationCount(User user) {
        return organizationMemberService.countByUserId(user.getId());
    }
}
