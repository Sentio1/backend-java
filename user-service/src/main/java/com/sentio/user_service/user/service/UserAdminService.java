package com.sentio.user_service.user.service;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.sentio.shared.dto.PageResponse;
import com.sentio.user_service.organization.dto.organization_member.OrganizationMemberResponse;
import com.sentio.user_service.organization.mapper.OrganizationMemberMapper;
import com.sentio.user_service.organization.repository.OrganizationMemberRepository;
import com.sentio.user_service.user.dto.UserAdminDetailResponse;
import com.sentio.user_service.user.dto.UserAdminSummaryResponse;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.enums.PlatformRole;
import com.sentio.user_service.user.mapper.UserMapper;
import com.sentio.user_service.user.repository.UserRepository;
import com.sentio.user_service.user.service.finder.UserFinder;
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
/** UserAdminService class. */
public class UserAdminService {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    private final UserMapper userMapper;
    private final OrganizationMemberMapper organizationMemberMapper;

    private final UserFinder userFinder;

    @Transactional(readOnly = true)
    public PageResponse<UserAdminSummaryResponse> getAllUsers(
            String email, Boolean includeDeleted, final Pageable pageable) {
        log.debug("Admin fetching all users with email: {}, includeDeleted: {}", email, includeDeleted);
        return PageResponse.of(userFinder
                .search(email, includeDeleted, pageable)
                .map(user -> userMapper.toUserAdminSummaryResponse(user, getOrganizationCount(user))));
    }

    @Transactional(readOnly = true)
    public UserAdminDetailResponse getUser(long userId) {
        log.debug("Admin fetching details for userId: {}", userId);
        User user = userFinder.findByIdIncludingDeleted(userId);

        List<OrganizationMemberResponse> organizations = organizationMemberRepository.findAllByUserId(userId).stream()
                .map(organizationMemberMapper::toResponse)
                .toList();

        return userMapper.toUserAdminDetailResponse(user, organizations);
    }

    @Transactional
    public void promoteToAdmin(long userId) {
        log.debug("Attempting to promote userId: {} to ADMIN", userId);
        User user = userFinder.findById(userId);
        user.setPlatformRole(PlatformRole.ADMIN);
        userRepository.save(user);
        log.info("Successfully promoted userId: {} to ADMIN", userId);
    }

    @Transactional
    public void demoteFromAdmin(long userId) {
        log.debug("Attempting to demote userId: {} from ADMIN", userId);
        User user = userFinder.findById(userId);

        if (user.getPlatformRole() == PlatformRole.ADMIN
                && userRepository.countByPlatformRole(PlatformRole.ADMIN) <= 1) {
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
        User user = userFinder.findByIdIncludingDeleted(userId);

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
        return organizationMemberRepository.countByUserId(user.getId());
    }
}
