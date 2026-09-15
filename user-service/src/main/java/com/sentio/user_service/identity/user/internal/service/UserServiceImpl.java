package com.sentio.user_service.identity.user.internal.service;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtBlacklistService;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtService;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.user_service.identity.organization.api.dto.OrganizationDto;
import com.sentio.user_service.identity.organization.api.dto.OrganizationInviteResponse;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberResponse;
import com.sentio.user_service.identity.organization.api.enums.OrgRole;
import com.sentio.user_service.identity.organization.api.service.OrganizationInviteService;
import com.sentio.user_service.identity.organization.api.service.OrganizationMemberService;
import com.sentio.user_service.identity.organization.api.service.OrganizationService;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.service.UserService;
import com.sentio.user_service.identity.user.api.dto.UserContextResponse;
import com.sentio.user_service.identity.user.internal.controller.dto.request.UserUpdateRequest;
import com.sentio.user_service.identity.user.internal.entity.User;
import com.sentio.user_service.identity.user.internal.exception.UserNotFoundException;
import com.sentio.user_service.identity.user.internal.mapper.UserMapper;
import com.sentio.user_service.identity.user.internal.repository.UserRepository;
import com.sentio.user_service.refresh_token.api.service.RefreshTokenService;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    private final OrganizationService organizationService;
    private final OrganizationMemberService organizationMemberService;
    private final OrganizationInviteService organizationInviteService;
    private final RefreshTokenService refreshTokenService;

    private final JwtBlacklistService jwtBlacklistService;
    private final JwtService jwtService;

    @Override
    @Transactional(readOnly = true)
    public UserDto findUserById(long userId) {
        log.debug("Fetching user dto for userId: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("id", userId));

        OrganizationMemberDto membership = organizationMemberService.findDefaultMembership(userId)
                .orElse(null);

        return userMapper.toDto(user, membership);
    }

    @Transactional(readOnly = true)
    public UserContextResponse findUserByIdContext(long userId) {
        return buildUserContext(userId, organizationMemberService.findDefaultMembership(userId).orElse(null));
    }

    @Override
    public UserContextResponse buildUserContext(long userId, OrganizationMemberDto membership) {
        log.debug("Fetching user context for userId: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("id", userId));

        return userMapper.toUserContextResponse(user, membership);
    }


    @Transactional(readOnly = true)
    public List<OrganizationMemberResponse> getOrganizations(long userId) {
        log.debug("Fetching organizations for userId: {}", userId);
        if (!userRepository.existsById(userId)) {
            log.warn("Failed to fetch organizations: userId {} not found", userId);
            throw new ResourceNotFoundException("User", "id", userId);
        }

        return organizationMemberService.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<OrganizationInviteResponse> getInvites(String email) {
        log.debug("Fetching invites for email: {}", email);
        if (!userRepository.existsByEmail(email)) {
            log.warn("Failed to fetch invites: user with email {} not found", email);
            throw new ResourceNotFoundException("User", "email", email);
        }

        return organizationInviteService.findAllByEmail(email);
    }

    @Transactional
    public UserContextResponse updateUser(UserUpdateRequest request, long userId) {
        log.debug("Attempting to update profile for userId: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("id", userId));

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

        userRepository.save(user);
        log.info("Successfully updated profile for userId: {}", userId);

        OrganizationMemberDto member = organizationMemberService.findDefaultMembership(userId)
                .orElse(null);

        return userMapper.toUserContextResponse(user, member);
    }

    @Transactional
    public void deleteUser(long userId, String accessToken) {
        log.debug("Attempting to delete userId: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("id", userId));

        organizationMemberService.findAllByUserId(userId).stream()
                .filter(m -> m.orgRole() == OrgRole.OWNER)
                .forEach(m -> {
                    long orgId = m.orgId();

                    organizationMemberService.lockOrganizationOrThrow(orgId);

                    OrganizationMemberDto freshMember = organizationMemberService.findByUserIdAndOrganizationId(userId, orgId)
                            .orElse(null);

                    if (freshMember != null && freshMember.orgRole() == OrgRole.OWNER) {
                        if (organizationMemberService.countByOrganizationIdAndRole(orgId, OrgRole.OWNER) <= 1) {
                            log.warn("Cannot delete userId {}: they are the last OWNER of orgId {}", userId, orgId);

                            OrganizationDto organization = organizationService.getOrganizationById(orgId);

                            throw new IllegalArgumentException("Cannot delete account: you are the last owner of \""
                                    + organization.name()
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

        organizationMemberService.deleteAllByUserId(userId);

        refreshTokenService.revokeAllActiveForUser(userId);
        log.info("Successfully deleted userId: {}", userId);
    }
}
