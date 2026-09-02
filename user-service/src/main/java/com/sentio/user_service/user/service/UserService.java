package com.sentio.user_service.user.service;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtBlacklistService;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtService;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.user_service.organization.dto.organization_invite.OrganizationInviteResponse;
import com.sentio.user_service.organization.dto.organization_member.OrganizationMemberResponse;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.organization.mapper.OrganizationInviteMapper;
import com.sentio.user_service.organization.mapper.OrganizationMemberMapper;
import com.sentio.user_service.organization.repository.OrganizationMemberRepository;
import com.sentio.user_service.organization.finder.OrganizationFinder;
import com.sentio.user_service.organization.finder.OrganizationInviteFinder;
import com.sentio.user_service.organization.finder.OrganizationMemberFinder;
import com.sentio.user_service.refresh_token.finder.RefreshTokenFinder;
import com.sentio.user_service.user.dto.UserContextResponse;
import com.sentio.user_service.user.dto.UserUpdateRequest;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.mapper.UserMapper;
import com.sentio.user_service.user.repository.UserRepository;
import com.sentio.user_service.user.finder.UserFinder;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
/** UserService class. */
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationFinder organizationFinder;
    private final OrganizationMemberFinder organizationMemberFinder;
    private final OrganizationInviteFinder organizationInviteFinder;
    private final RefreshTokenFinder refreshTokenFinder;

    private final UserMapper userMapper;
    private final OrganizationMemberMapper organizationMemberMapper;
    private final OrganizationInviteMapper organizationInviteMapper;

    private final JwtBlacklistService jwtBlacklistService;
    private final JwtService jwtService;

    private final UserFinder userFinder;

    @Transactional(readOnly = true)
    public UserContextResponse findUserById(long userId) {
        log.debug("Fetching user context for userId: {}", userId);
        User user = userFinder.findById(userId);

        OrganizationMember membership = organizationMemberFinder
                .findByUserIdAndIsDefaultTrue(userId)
                .orElse(null);

        return userMapper.toUserContextResponse(user, membership);
    }

    @Transactional(readOnly = true)
    public List<OrganizationMemberResponse> getOrganizations(long userId) {
        log.debug("Fetching organizations for userId: {}", userId);
        if (!userFinder.existsById(userId)) {
            log.warn("Failed to fetch organizations: userId {} not found", userId);
            throw new ResourceNotFoundException("User", "id", userId);
        }

        return organizationMemberFinder.findAllByUserId(userId).stream()
                .map(organizationMemberMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrganizationInviteResponse> getInvites(String email) {
        log.debug("Fetching invites for email: {}", email);
        if (!userFinder.existsByEmail(email)) {
            log.warn("Failed to fetch invites: user with email {} not found", email);
            throw new ResourceNotFoundException("User", "email", email);
        }

        return organizationInviteFinder.findAllByEmail(email).stream()
                .map(organizationInviteMapper::toResponse)
                .toList();
    }

    @Transactional
    public UserContextResponse updateUser(UserUpdateRequest request, long userId) {
        log.debug("Attempting to update profile for userId: {}", userId);
        User user = userFinder.findById(userId);

        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.middleName() != null) {
            user.setMiddleName(request.middleName());
        }

        User savedUser = userRepository.save(user);
        log.info("Successfully updated profile for userId: {}", userId);

        OrganizationMember member = organizationMemberFinder
                .findByUserIdAndIsDefaultTrue(userId)
                .orElse(null);

        return userMapper.toUserContextResponse(user, member);
    }

    @Transactional
    public void deleteUser(long userId, String accessToken) {
        log.debug("Attempting to delete userId: {}", userId);
        User user = userFinder.findById(userId);

        organizationMemberFinder.findAllByUserId(userId).stream()
                .filter(m -> m.getRole() == OrgRole.OWNER)
                .forEach(m -> {
                    long orgId = m.getOrganization().getId();

                    organizationFinder.findByIdLocked(orgId);

                    OrganizationMember freshMember = organizationMemberFinder
                            .findByUserIdAndOrganizationId(userId, orgId)
                            .orElse(null);

                    if (freshMember != null && freshMember.getRole() == OrgRole.OWNER) {
                        if (organizationMemberFinder.countByOrganizationIdAndRole(orgId, OrgRole.OWNER) <= 1) {
                            log.warn("Cannot delete userId {}: they are the last OWNER of orgId {}", userId, orgId);
                            throw new IllegalArgumentException("Cannot delete account: you are the last owner of \""
                                    + m.getOrganization().getName()
                                    + "\". Promote someone else to OWNER or delete the organization first.");
                        }
                    }
                });

        user.setDeletedAt(Instant.now());
        userRepository.save(user);

        if (accessToken != null) {
            try {
                jwtBlacklistService.addToBlacklist(
                        accessToken, jwtService.extractExpiration(accessToken).toEpochMilli());
            } catch (Exception e) {
                log.debug("Could not blacklist access token on logout, skipping", e);
            }
        }

        organizationMemberRepository.deleteAllByUserId(userId);

        refreshTokenFinder.findAllByUserIdAndRevokedAtIsNull(userId).forEach(rt -> rt.setRevokedAt(Instant.now()));
        log.info("Successfully deleted userId: {}", userId);
    }
}
