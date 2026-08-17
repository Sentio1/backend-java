package com.sentio.user_service.user;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtBlacklistService;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtService;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.user_service.organization.dto.organization_invite.OrganizationInviteResponse;
import com.sentio.user_service.organization.dto.organization_member.OrganizationMemberResponse;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.organization.mapper.OrganizationInviteMapper;
import com.sentio.user_service.organization.mapper.OrganizationMemberMapper;
import com.sentio.user_service.organization.repository.OrganizationInviteRepository;
import com.sentio.user_service.organization.repository.OrganizationMemberRepository;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.repository.OrganizationRepository;
import com.sentio.user_service.refresh_token.RefreshTokenRepository;
import com.sentio.user_service.user.dto.UserContextResponse;
import com.sentio.user_service.user.dto.UserUpdateRequest;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.mapper.UserMapper;
import com.sentio.user_service.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationInviteRepository organizationInviteRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    private final UserMapper userMapper;
    private final OrganizationMemberMapper organizationMemberMapper;
    private final OrganizationInviteMapper organizationInviteMapper;

    private final JwtBlacklistService jwtBlacklistService;
    private final JwtService jwtService;


    @Transactional(readOnly = true)
    public UserContextResponse findUserById(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        OrganizationMember membership = organizationMemberRepository.findByUserIdAndIsDefaultTrue(userId)
                .orElse(null);

        return userMapper.toResponse(user, membership);
    }

    @Transactional(readOnly = true)
    public List<OrganizationMemberResponse> getOrganizations(long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", "id", userId);
        }

        return organizationMemberRepository
                .findAllByUserId(userId)
                .stream()
                .map(organizationMemberMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrganizationInviteResponse> getInvites(String email) {
        if (!userRepository.existsByEmail(email)) {
            throw new ResourceNotFoundException("User", "email", email);
        }

        return organizationInviteRepository
                .findAllByEmail(email)
                .stream()
                .map(organizationInviteMapper::toResponse)
                .toList();
    }

    @Transactional
    public UserContextResponse updateUser(UserUpdateRequest request, long userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        user.setPhoneNumber(request.phoneNumber());
        user.setLastName(request.lastName());
        user.setFirstName(request.firstName());

        if (request.middleName() != null) {
            user.setMiddleName(request.middleName());
        }

        userRepository.save(user);

        OrganizationMember member = organizationMemberRepository.findByUserIdAndIsDefaultTrue(userId)
                .orElse(null);

        return userMapper.toResponse(user, member);
    }

    @Transactional
    public void deleteUser(long userId, String accessToken) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        organizationMemberRepository.findAllByUserId(userId).stream()
                .filter(m -> m.getRole() == OrgRole.OWNER)
                .forEach(m -> {
                    long orgId = m.getOrganization().getId();

                    organizationRepository.findByIdLocked(orgId);

                    if (organizationMemberRepository.countByOrganizationIdAndRole(orgId, OrgRole.OWNER) <= 1) {
                        throw new IllegalArgumentException(
                                "Cannot delete account: you are the last owner of \"" + m.getOrganization().getName()
                                        + "\". Promote someone else to OWNER or delete the organization first.");
                    }
                });

        user.setDeletedAt(Instant.now());
        userRepository.save(user);

        if (accessToken != null) {
            try {
                jwtBlacklistService.addToBlacklist(accessToken, jwtService.extractExpiration(accessToken).toEpochMilli());
            } catch (Exception e) {
                log.debug("Could not blacklist access token on logout, skipping", e);
            }
        }

        organizationMemberRepository.deleteAllByUserId(userId);

        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId)
                .forEach(rt -> rt.setRevokedAt(Instant.now()));
    }
}
