package com.sentio.user_service.identity.organization.internal.service;

import com.lisovskyi.security.autoconfigure.security.jwt.OpaqueTokenService;
import com.lisovskyi.web.error.autoconfigure.standard.*;
import com.sentio.shared.dto.PageResponse;
import com.sentio.user_service.identity.organization.api.dto.OrganizationInviteAcceptResponse;
import com.sentio.user_service.identity.organization.api.service.OrganizationInviteService;
import com.sentio.user_service.identity.organization.internal.controller.dto.organization_invite.OrganizationInviteCreatedResponse;
import com.sentio.user_service.identity.organization.internal.controller.dto.organization_invite.OrganizationInviteRequest;
import com.sentio.user_service.identity.organization.api.dto.OrganizationInviteResponse;
import com.sentio.user_service.identity.organization.internal.entity.Organization;
import com.sentio.user_service.identity.organization.internal.entity.OrganizationInvite;
import com.sentio.user_service.identity.organization.internal.entity.OrganizationMember;
import com.sentio.user_service.identity.organization.api.enums.OrgRole;
import com.sentio.user_service.identity.organization.internal.exception.OrganizationNotFoundException;
import com.sentio.user_service.identity.organization.internal.mapper.OrganizationInviteMapper;
import com.sentio.user_service.identity.organization.internal.repository.OrganizationInviteRepository;
import com.sentio.user_service.identity.organization.internal.repository.OrganizationMemberRepository;
import com.sentio.user_service.identity.organization.internal.repository.OrganizationRepository;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.service.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrganizationInviteServiceImpl implements OrganizationInviteService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationInviteRepository organizationInviteRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    private final OrganizationServiceImpl organizationService;
    private final OpaqueTokenService opaqueTokenService;
    private final UserService userService;

    private final OrganizationInviteMapper organizationInviteMapper;

    @Transactional(readOnly = true)
    public PageResponse<OrganizationInviteResponse> getAllInvites(long orgId, Pageable pageable) {
        log.debug("Fetching invites for orgId: {}", orgId);
        return PageResponse.of(organizationInviteRepository
                .findAllByOrganizationId(orgId, pageable)
                .map(organizationInviteMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationInviteResponse> findAllByEmail(String email) {
        return organizationInviteRepository.findAllByEmail(email).stream()
                .map(organizationInviteMapper::toResponse)
                .toList();
    }

    @Transactional
    public OrganizationInviteCreatedResponse inviteUserToOrganization(
            OrganizationInviteRequest inviteRequest, long orgId, long userId) {
        log.debug(
                "Attempting to invite user with email: {} to orgId: {} by userId: {}",
                inviteRequest.email(),
                orgId,
                userId);
        if (organizationInviteRepository.existsByOrganizationIdAndEmailAndAcceptedAtIsNullAndRevokedAtIsNull(
                orgId, inviteRequest.email())) {
            log.warn("Invite failed: Active invite for {} already exists in orgId: {}", inviteRequest.email(), orgId);
            throw new ResourceAlreadyExistsException("Active invite for " + inviteRequest.email() + " already exists");
        }

        UserDto owner = userService.findUserById(userId);

        OrganizationMember organizationMember = organizationMemberRepository
                .findByUserIdAndOrganizationId(userId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationMember", "userId", userId));

        if (organizationMember.getRole() != OrgRole.OWNER) {
            log.warn("Invite failed: userId: {} is not an OWNER in orgId: {}", userId, orgId);
            throw new ForbiddenOperationException("Only owners can invite users to an organization");
        }

        Organization organization = organizationRepository
                .findById(orgId)
                .orElseThrow(() -> new OrganizationNotFoundException("id", orgId));

        String token = opaqueTokenService.generate();
        String tokenHash = opaqueTokenService.hash(token);

        OrganizationInvite organizationInvite = OrganizationInvite.builder()
                .organization(organization)
                .email(inviteRequest.email())
                .role(inviteRequest.role())
                .tokenHash(tokenHash)
                .invitedById(owner.id())
                .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                .build();

        OrganizationInvite savedInvite = organizationInviteRepository.save(organizationInvite);
        log.info(
                "Successfully invited email: {} to orgId: {} (inviteId: {})",
                inviteRequest.email(),
                orgId,
                savedInvite.getId());

        return organizationInviteMapper.toCreatedResponse(savedInvite, token);
    }

    @Override
    @Transactional
    public OrganizationInviteAcceptResponse acceptInvite(String token, long userId) {
        log.debug("Attempting to accept invite for userId: {}", userId);
        String hashedToken = opaqueTokenService.hash(token);

        OrganizationInvite organizationInvite = organizationInviteRepository
                .findByTokenHash(hashedToken)
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationInvite", "tokenHash", hashedToken));

        if (organizationInvite.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Accept invite failed: Invite {} has expired", organizationInvite.getId());
            throw new BadRequestException("Invite has expired");
        }

        if (organizationInvite.getAcceptedAt() != null) {
            log.warn("Accept invite failed: Invite {} already accepted", organizationInvite.getId());
            throw new BadRequestException("Invite has already been accepted");
        }

        if (organizationInvite.getRevokedAt() != null) {
            log.warn("Accept invite failed: Invite {} has been revoked", organizationInvite.getId());
            throw new BadRequestException("Invite has been revoked");
        }

        UserDto user = userService.findUserById(userId);

        if (!organizationInvite.getEmail().equalsIgnoreCase(user.email())) {
            log.warn(
                    "Accept invite failed: Email mismatch for invite {}. Expected: {}, Actual: {}",
                    organizationInvite.getId(),
                    organizationInvite.getEmail(),
                    user.email());
            throw new UnauthorizedException("This invite was issued to a different email address");
        }

        Organization organization = organizationInvite.getOrganization();

        if (organizationMemberRepository.existsByUserIdAndOrganizationId(userId, organization.getId())) {
            log.warn("Accept invite failed: User {} is already a member of orgId: {}", userId, organization.getId());
            throw new ResourceAlreadyExistsException("User is already a member of this organization");
        }

        boolean isFirstOrganization =
                organizationService.findDefaultMembership(userId).isEmpty();

        OrganizationMember organizationMember = OrganizationMember.builder()
                .organization(organization)
                .userId(user.id())
                .role(organizationInvite.getRole())
                .isDefault(isFirstOrganization)
                .joinedAt(Instant.now())
                .build();

        organizationInviteRepository.save(organizationInvite);

        organizationInvite.setAcceptedAt(Instant.now());

        OrganizationMember savedMember = organizationMemberRepository.save(organizationMember);
        log.info(
                "Successfully accepted invite {} - userId: {} joined orgId: {} as {}",
                organizationInvite.getId(),
                userId,
                organization.getId(),
                savedMember.getRole());

        return organizationInviteMapper.toAcceptResponse(savedMember);
    }

    @Transactional
    public OrganizationInviteResponse revokeInvite(long orgId, long inviteId) {
        log.debug("Attempting to revoke inviteId: {} in orgId: {}", inviteId, orgId);
        OrganizationInvite organizationInvite = organizationInviteRepository
                .findByIdLocked(inviteId)
                .filter(invite -> invite.getOrganization().getId() == orgId)
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationInvite", "id", inviteId));

        organizationInvite.setRevokedAt(Instant.now());
        OrganizationInvite savedInvite = organizationInviteRepository.save(organizationInvite);
        log.info("Successfully revoked inviteId: {} in orgId: {}", inviteId, orgId);

        return organizationInviteMapper.toResponse(savedInvite);
    }
}
