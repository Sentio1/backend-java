package com.sentio.user_service.organization.service;

import com.lisovskyi.security.autoconfigure.security.jwt.OpaqueTokenService;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.user_service.organization.dto.organization_invite.OrganizationInviteAcceptResponse;
import com.sentio.user_service.organization.dto.organization_invite.OrganizationInviteCreatedResponse;
import com.sentio.user_service.organization.dto.organization_invite.OrganizationInviteRequest;
import com.sentio.user_service.organization.dto.organization_invite.OrganizationInviteResponse;
import com.sentio.user_service.organization.entity.Organization;
import com.sentio.user_service.organization.entity.OrganizationInvite;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.organization.mapper.OrganizationInviteMapper;
import com.sentio.user_service.organization.repository.OrganizationInviteRepository;
import com.sentio.user_service.organization.repository.OrganizationMemberRepository;
import com.sentio.user_service.organization.repository.OrganizationRepository;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.repository.UserRepository;
import dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrganizationInviteService {

    private final OrganizationInviteRepository organizationInviteRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserRepository userRepository;

    private final OrganizationService organizationService;
    private final OpaqueTokenService opaqueTokenService;

    private final OrganizationInviteMapper organizationInviteMapper;

    @Transactional(readOnly = true)
    public PageResponse<OrganizationInviteResponse> getAllInvites(long orgId, Pageable pageable) {
        return PageResponse.of(
                organizationInviteRepository.findAllByOrganizationId(orgId, pageable)
                        .map(organizationInviteMapper::toResponse)
        );
    }

    @Transactional
    public OrganizationInviteCreatedResponse inviteUserToOrganization(OrganizationInviteRequest inviteRequest, long orgId, long userId) {
        if (organizationInviteRepository.existsByOrganizationIdAndEmailAndAcceptedAtIsNullAndRevokedAtIsNull(orgId, inviteRequest.email())) {
            throw new ResourceAlreadyExistsException("Active invite for " + inviteRequest.email() + " already exists");
        }

        User owner = userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        OrganizationMember organizationMember = organizationMemberRepository
                .findByUserIdAndOrganizationId(userId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationMember", "userId", userId));

        if (organizationMember.getRole() != OrgRole.OWNER) {
            throw new IllegalArgumentException("Only owners can invite users to an organization");
        }

        Organization organization = organizationRepository
                .findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", orgId));

        String token = opaqueTokenService.generate();
        String tokenHash = opaqueTokenService.hash(token);

        OrganizationInvite organizationInvite = OrganizationInvite.builder()
                .organization(organization)
                .email(inviteRequest.email())
                .role(inviteRequest.role())
                .tokenHash(tokenHash)
                .invitedBy(owner)
                .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                .build();



        return organizationInviteMapper.toCreatedResponse(organizationInviteRepository.save(organizationInvite), token);
    }

    @Transactional
    public OrganizationInviteAcceptResponse acceptInvite(String token, long userId) {
        String hashedToken = opaqueTokenService.hash(token);

        OrganizationInvite organizationInvite = organizationInviteRepository
                .findByTokenHash(hashedToken)
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationInvite", "tokenHash", hashedToken));

        if (organizationInvite.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Invite has been expired");
        }

        if (organizationInvite.getAcceptedAt() != null) {
            throw new IllegalArgumentException("Invite has already been accepted");
        }

        if (organizationInvite.getRevokedAt() != null) {
            throw new IllegalArgumentException("Invite has been revoked");
        }

        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Organization organization = organizationInvite.getOrganization();

        if (organizationMemberRepository.existsByUserIdAndOrganizationId(userId, organization.getId())) {
            throw new ResourceAlreadyExistsException("User is already a member of this organization");
        }

        boolean isFirstOrganization = organizationService.findDefaultMembership(userId).isEmpty();

        OrganizationMember organizationMember = OrganizationMember.builder()
                .organization(organization)
                .user(user)
                .role(organizationInvite.getRole())
                .isDefault(isFirstOrganization)
                .joinedAt(Instant.now())
                .build();

        organizationInviteRepository.save(organizationInvite);

        organizationInvite.setAcceptedAt(Instant.now());

        return organizationInviteMapper.toAcceptResponse(organizationMemberRepository.save(organizationMember));
    }

    @Transactional
    public OrganizationInviteResponse revokeInvite(long orgId, long inviteId) {
        OrganizationInvite organizationInvite = organizationInviteRepository
                .findByIdLocked(inviteId)
                .filter(invite -> invite.getOrganization().getId() == orgId)
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationInvite", "id", inviteId));

        organizationInvite.setRevokedAt(Instant.now());

        return organizationInviteMapper.toResponse(organizationInviteRepository.save(organizationInvite));
    }
}
