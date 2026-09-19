package com.sentio.user_service.identity.organization.internal.service;

import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.user.UserId;
import com.sentio.user_service.identity.organization.api.dto.OrganizationDto;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberResponse;
import com.sentio.user_service.identity.organization.api.enums.OrgRole;
import com.sentio.user_service.identity.organization.api.service.OrganizationMemberService;
import com.sentio.user_service.identity.organization.api.service.OrganizationService;
import com.sentio.user_service.identity.organization.api.dto.CreateOrganizationRequest;
import com.sentio.user_service.identity.organization.internal.controller.dto.organization.OrganizationResponse;
import com.sentio.user_service.identity.organization.internal.controller.dto.organization.UpdateOrganizationRequest;
import com.sentio.user_service.identity.organization.internal.entity.Organization;
import com.sentio.user_service.identity.organization.internal.entity.OrganizationMember;
import com.sentio.user_service.identity.organization.internal.exception.OrganizationMemberNotFoundException;
import com.sentio.user_service.identity.organization.internal.exception.OrganizationNotFoundException;
import com.sentio.user_service.identity.organization.internal.mapper.OrganizationMapper;
import com.sentio.user_service.identity.organization.internal.mapper.OrganizationMemberMapper;
import com.sentio.user_service.identity.organization.internal.repository.OrganizationMemberRepository;
import com.sentio.user_service.identity.organization.internal.repository.OrganizationRepository;
import com.sentio.user_service.identity.user.api.dto.UserContextResponse;
import com.sentio.user_service.identity.user.api.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationMemberService, OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    private final OrganizationCreationService organizationProvisioning;
    private final UserService userService;

    private final OrganizationMapper organizationMapper;
    private final OrganizationMemberMapper organizationMemberMapper;

    @Transactional(readOnly = true)
    public PageResponse<OrganizationMemberResponse> getAllOrganizationMembers(long orgId, Pageable pageable) {
        log.debug("Fetching organization members for orgId: {}", orgId);

        return PageResponse.of(organizationMemberRepository
                .findAllByOrganizationId(orgId, pageable)
                .map(this::toMemberResponse));
    }

    @Transactional
    public OrganizationResponse updateOrganization(long id, UpdateOrganizationRequest updateRequest) {
        log.debug("Attempting to update organization id: {}", id);
        Organization organization = organizationRepository
                .findByIdLocked(id)
                .orElseThrow(() -> new OrganizationNotFoundException("id", id));

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
    // cleared first, or the partial unique index on organization_members rejects it.
    @Override
    @Transactional
    public OrganizationMemberDto createOrganization(long userId, CreateOrganizationRequest request) {
        log.debug("Attempting to create organization: {} for user: {}", request.orgName(), userId);

        organizationMemberRepository.findByUserIdAndIsDefaultTrue(userId)
            .ifPresent(current -> {
                current.setDefault(false);
                organizationMemberRepository.save(current);
                log.debug("Unset previous default organization for user: {}", userId);
            });

        // createOwnerMembership sets isDefault=true on the new membership internally
        OrganizationMemberDto membership = organizationProvisioning.createOwnerMembership(
                UserId.of(userId), request.orgName(), request.edrpou(), request.plan());

        log.info("Successfully created organization and set as default for user: {}", userId);
        return membership;
    }

    @Override
    @Transactional
    public OrganizationMemberDto switchDefaultOrganization(long userId, long targetOrgId) {
        log.debug("User: {} attempting to switch default organization to targetOrgId: {}", userId, targetOrgId);
        OrganizationMember target = organizationMemberRepository
                .findByUserIdAndOrganizationId(userId, targetOrgId)
                .orElseThrow(() -> new OrganizationMemberNotFoundException("userId", userId));

        organizationMemberRepository.findByUserIdAndIsDefaultTrue(userId)
            .filter(current -> !current.getId().equals(target.getId()))
            .ifPresent(current -> {
                current.setDefault(false);
                organizationMemberRepository.save(current);
                log.debug("Unset previous default organization for user: {}", userId);
            });

        target.setDefault(true);
        organizationMemberRepository.save(target);

        log.info("User: {} successfully switched default organization to: {}", userId, targetOrgId);
        return organizationMemberMapper.toDto(target);
    }

    @Transactional
    public OrganizationMemberResponse patchRoleForMember(long orgId, long userId, OrgRole newRole) {
        log.debug("Attempting to patch platformRole to {} for userId: {} in orgId: {}", newRole, userId, orgId);

        // Row lock on the org serializes every "is this the last OWNER?" check-then-act
        // (role change, member removal, account deletion) - without it two of them can
        // each see two owners and together leave the organization with none.
        lockOrganization(orgId);

        OrganizationMember organizationMember = organizationMemberRepository
                .findByUserIdAndOrganizationId(userId, orgId)
                .orElseThrow(() -> new OrganizationMemberNotFoundException("userId", userId));

        if (organizationMember.getRole() == OrgRole.OWNER
                && newRole != OrgRole.OWNER
                && organizationMemberRepository.countByOrganizationIdAndRole(orgId, OrgRole.OWNER) <= 1) {
            log.warn("Cannot change platformRole for userId: {} in orgId: {}: they are the last OWNER", userId, orgId);
            throw new IllegalArgumentException(
                    "Cannot change the last owner's role. Promote someone else to OWNER first.");
        }

        organizationMember.setRole(newRole);
        OrganizationMember savedMember = organizationMemberRepository.save(organizationMember);
        log.info("Successfully patched platformRole to {} for userId: {} in orgId: {}", newRole, userId, orgId);

        return toMemberResponse(savedMember);
    }

    @Transactional
    public void deleteOrganizationMember(long orgId, long userId) {
        log.debug("Attempting to delete userId: {} from orgId: {}", userId, orgId);

        // Row lock on the org serializes every "is this the last OWNER?" check-then-act
        // (role change, member removal, account deletion) - without it two of them can
        // each see two owners and together leave the organization with none.
        lockOrganization(orgId);

        OrganizationMember organizationMember = organizationMemberRepository
                .findByUserIdAndOrganizationId(userId, orgId)
                .orElseThrow(() -> new OrganizationMemberNotFoundException("userId", userId));

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

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationMemberResponse> findAllByUserId(long userId) {
        return organizationMemberRepository.findAllByUserId(userId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationMemberDto> findByUserIdAndOrganizationId(long userId, long orgId) {
        return organizationMemberRepository.findByUserIdAndOrganizationId(userId, orgId)
                .map(organizationMemberMapper::toDto);
    }

    // No default membership is a legitimate state now, not just transiently during
    // Google sign-up - registration is always org-less (see AuthService.register),
    // so login/refresh have to tolerate it too, not just Google's fallback path.
    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationMemberDto> findDefaultMembership(long userId) {
        return organizationMemberRepository.findByUserIdAndIsDefaultTrue(userId)
                .map(organizationMemberMapper::toDto);
    }

    // Not readOnly: it takes a SELECT ... FOR UPDATE lock held until the caller's
    // transaction ends (see UserServiceImpl.deleteUser).
    @Override
    @Transactional
    public void lockOrganizationOrThrow(long orgId) {
        lockOrganization(orgId);
    }

    private void lockOrganization(long orgId) {
        organizationRepository.findByIdLocked(orgId)
                .orElseThrow(() -> new OrganizationNotFoundException("id", orgId));
    }

    @Override
    @Transactional(readOnly = true)
    public long countByOrganizationIdAndRole(long orgId, OrgRole role) {
        return organizationMemberRepository.countByOrganizationIdAndRole(orgId, role);
    }

    @Override
    @Transactional
    public void deleteAllByUserId(long userId) {
        organizationMemberRepository.deleteAllByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public int countByUserId(long userId) {
        return organizationMemberRepository.countByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationDto getOrganizationById(long orgId) {
        return organizationRepository.findById(orgId)
                .map(organizationMapper::toDto)
                .orElseThrow(() -> new OrganizationNotFoundException("id", orgId));
    }

    // OrganizationMember only carries a userId, not a User relation (organization
    // owns membership, not identity) - so the "user" part of the response can only be
    // built by asking the user module for it, via its public UserService contract.
    private OrganizationMemberResponse toMemberResponse(OrganizationMember member) {
        OrganizationMemberDto dto = organizationMemberMapper.toDto(member);
        UserContextResponse user = userService.buildUserContext(member.getUserId(), dto);
        return new OrganizationMemberResponse(dto.organizationId(), dto.orgRole(), member.isDefault(), user);
    }
}
