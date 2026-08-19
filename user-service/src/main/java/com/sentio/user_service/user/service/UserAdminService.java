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
        return PageResponse.of(userFinder
                .search(email, includeDeleted, pageable)
                .map(user -> userMapper.toUserAdminSummaryResponse(user, getOrganizationCount(user))));
    }

    @Transactional(readOnly = true)
    public UserAdminDetailResponse getUser(long userId) {
        User user = userFinder.findByIdIncludingDeleted(userId);

        List<OrganizationMemberResponse> organizations = organizationMemberRepository.findAllByUserId(userId).stream()
                .map(organizationMemberMapper::toResponse)
                .toList();

        return userMapper.toUserAdminDetailResponse(user, organizations);
    }

    @Transactional
    public void promoteToAdmin(long userId) {
        User user = userFinder.findById(userId);
        user.setPlatformRole(PlatformRole.ADMIN);
        userRepository.save(user);
    }

    @Transactional
    public void demoteFromAdmin(long userId) {
        User user = userFinder.findById(userId);

        if (user.getPlatformRole() == PlatformRole.ADMIN
                && userRepository.countByPlatformRole(PlatformRole.ADMIN) <= 1) {
            throw new IllegalStateException("Cannot demote: " + user.getEmail() + " is the last platform admin.");
        }

        user.setPlatformRole(PlatformRole.USER);
        userRepository.save(user);
    }

    @Transactional
    public void restoreUser(long userId) {
        User user = userFinder.findByIdIncludingDeleted(userId);

        if (!user.isDeleted()) {
            throw new IllegalArgumentException("User " + userId + " is not deleted");
        }

        // цей запит поверне лише true, якщо в користувача буде активна пошта
        if (userRepository.existsByEmail(user.getEmail())) {
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
