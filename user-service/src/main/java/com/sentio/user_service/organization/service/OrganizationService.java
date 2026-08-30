package com.sentio.user_service.organization.service;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.dto.PageResponse;
import com.sentio.user_service.auth.dto.response.AuthResult;
import com.sentio.user_service.auth.token.TokenIssuer;
import com.sentio.user_service.organization.dto.CreateOrganizationRequest;
import com.sentio.user_service.organization.dto.organization.OrganizationResponse;
import com.sentio.user_service.organization.dto.organization.UpdateOrganizationRequest;
import com.sentio.user_service.organization.dto.organization_member.OrganizationMemberResponse;
import com.sentio.user_service.organization.entity.Organization;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.organization.mapper.OrganizationMapper;
import com.sentio.user_service.organization.mapper.OrganizationMemberMapper;
import com.sentio.user_service.organization.repository.OrganizationMemberRepository;
import com.sentio.user_service.organization.repository.OrganizationRepository;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.mapper.UserMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
/** OrganizationService class. */
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationCreationService organizationProvisioning;

    private final TokenIssuer tokenIssuer;

    private final UserMapper userMapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationMemberMapper organizationMemberMapper;

    @Transactional(readOnly = true)
    public PageResponse<OrganizationMemberResponse> getAllOrganizationMembers(long orgId, Pageable pageable) {
        log.debug("Fetching organization members for orgId: {}", orgId);
        return PageResponse.of(organizationMemberRepository
                .findAllByOrganizationIdAndUserDeletedAtIsNull(orgId, pageable)
                .map(organizationMemberMapper::toResponse));
    }

    @Transactional
    public OrganizationResponse updateOrganization(long id, UpdateOrganizationRequest updateRequest) {
        log.debug("Attempting to update organization id: {}", id);
        Organization organization = organizationRepository
                .findByIdLocked(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", id));

        organization.setName(updateRequest.name());

        Organization updatedOrg = organizationRepository.save(organization);
        log.info("Successfully updated organization id: {}", id);
        return organizationMapper.toResponse(updatedOrg);
    }

    // For an org-less user this is the second half of "join or create" - the same
    // deal as switchDefaultOrganization below, except the org doesn't exist yet.
    // createOwnerMembership always sets isDefault=true unconditionally (safe when
    // it's only ever called for a brand new user with zero memberships), so here -
    // where the caller may already have a default org - the old default has to be
    // cleared first or the partial unique index on organization_members rejects it.
    @Transactional
    public AuthResult createOrganization(User user, CreateOrganizationRequest request, String ip, String userAgent) {
        log.debug("Attempting to create organization: {} for user: {}", request.orgName(), user.getId());
        findDefaultMembership(user.getId()).ifPresent(current -> {
            current.setDefault(false);
            organizationMemberRepository.save(current);
            log.debug("Unset previous default organization for user: {}", user.getId());
        });

        OrganizationMember membership = organizationProvisioning.createOwnerMembership(
                user, request.orgName(), request.edrpou(), request.plan());

        log.info("Successfully created organization and set as default for user: {}", user.getId());
        return new AuthResult(
                tokenIssuer.issue(user, membership, ip, userAgent), userMapper.toUserContextResponse(user, membership));
    }

    @Transactional
    public AuthResult switchDefaultOrganization(long userId, long targetOrgId, String ip, String userAgent) {
        log.debug("User: {} attempting to switch default organization to targetOrgId: {}", userId, targetOrgId);
        OrganizationMember target = organizationMemberRepository
                .findByUserIdAndOrganizationId(userId, targetOrgId)
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationMember", "userId", userId));

        findDefaultMembership(userId).ifPresent(current -> {
            current.setDefault(false);
            organizationMemberRepository.save(current);
            log.debug("Unset previous default organization for user: {}", userId);
        });

        target.setDefault(true);
        organizationMemberRepository.save(target);

        User user = target.getUser();
        log.info("User: {} successfully switched default organization to: {}", userId, targetOrgId);
        return new AuthResult(
                tokenIssuer.issue(user, target, ip, userAgent), userMapper.toUserContextResponse(user, target));
    }

    @Transactional
    public OrganizationMemberResponse patchRoleForMember(long orgId, long userId, OrgRole newRole) {
        log.debug("Attempting to patch role to {} for userId: {} in orgId: {}", newRole, userId, orgId);
        organizationRepository
                .findByIdLocked(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", orgId));

        OrganizationMember organizationMember = organizationMemberRepository
                .findByUserIdAndOrganizationId(userId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationMember", "userId", userId));

        if (organizationMember.getRole() == OrgRole.OWNER
                && newRole != OrgRole.OWNER
                && organizationMemberRepository.countByOrganizationIdAndRole(orgId, OrgRole.OWNER) <= 1) {
            log.warn("Cannot change role for userId: {} in orgId: {}: they are the last OWNER", userId, orgId);
            throw new IllegalArgumentException(
                    "Cannot change the last owner's role. Promote someone else to OWNER first.");
        }

        organizationMember.setRole(newRole);
        OrganizationMember savedMember = organizationMemberRepository.save(organizationMember);
        log.info("Successfully patched role to {} for userId: {} in orgId: {}", newRole, userId, orgId);

        return organizationMemberMapper.toResponse(savedMember);
    }

    @Transactional
    public void deleteOrganizationMember(long orgId, long userId) {
        log.debug("Attempting to delete userId: {} from orgId: {}", userId, orgId);
        organizationRepository
                .findByIdLocked(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", orgId));

        OrganizationMember organizationMember = organizationMemberRepository
                .findByUserIdAndOrganizationId(userId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationMember", "userId", userId));

        if (organizationMember.getRole() == OrgRole.OWNER) {
            long ownerCount = organizationMemberRepository.countByOrganizationIdAndRole(orgId, OrgRole.OWNER);
            if (ownerCount <= 1) {
                log.warn("Cannot delete userId: {} from orgId: {}: they are the last OWNER", userId, orgId);
                throw new IllegalArgumentException(
                        "Cannot delete the last owner of the organization. Promote someone else to OWNER first.");
            }
        }

        organizationMemberRepository.delete(organizationMember);
        log.info("Successfully deleted userId: {} from orgId: {}", userId, orgId);
    }

    // No default membership is a legitimate state now, not just transiently during
    // Google sign-up - registration is always org-less (see AuthService.register),
    // so login/refresh have to tolerate it too, not just Google's fallback path.
    public Optional<OrganizationMember> findDefaultMembership(long userId) {
        return organizationMemberRepository.findByUserIdAndIsDefaultTrue(userId);
    }
}
