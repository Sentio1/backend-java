package com.sentio.user_service.organization.service;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.user_service.auth.dto.AuthResult;
import com.sentio.user_service.auth.token.TokenIssuer;
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
import dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    private final TokenIssuer tokenIssuer;

    private final UserMapper userMapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationMemberMapper organizationMemberMapper;

    @Transactional(readOnly = true)
    public PageResponse<OrganizationMemberResponse> getAllOrganizationMembers(long orgId, Pageable pageable) {
        return PageResponse.of(organizationMemberRepository
                .findAllByOrganizationId(orgId, pageable)
                .map(organizationMemberMapper::toResponse)
        );
    }

    @Transactional
    public OrganizationResponse updateOrganization(long id, UpdateOrganizationRequest updateRequest) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", id));

        organization.setName(updateRequest.name());

        return organizationMapper.toResponse(organizationRepository.save(organization));
    }

    @Transactional
    public AuthResult switchDefaultOrganization(long userId, long targetOrgId) {
        OrganizationMember target = organizationMemberRepository
                .findByUserIdAndOrganizationId(userId, targetOrgId)
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationMember", "userId", userId));

        findDefaultMembership(userId)
                .ifPresent(current -> {
                    current.setDefault(false);
                    organizationMemberRepository.save(current);
                });

        target.setDefault(true);
        organizationMemberRepository.save(target);

        User user = target.getUser();
        return new AuthResult(tokenIssuer.issue(user, target), userMapper.toResponse(user, target));
    }


    @Transactional
    public OrganizationMemberResponse patchRoleForMember(long orgId, long userId, OrgRole newRole) {
        organizationRepository.findByIdLocked(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", orgId));

        OrganizationMember organizationMember = organizationMemberRepository
                .findByUserIdAndOrganizationId(userId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationMember", "userId", userId));

        organizationMember.setRole(newRole);

        return organizationMemberMapper.toResponse(organizationMemberRepository.save(organizationMember));
    }

    @Transactional
    public void deleteOrganizationMember(long orgId, long userId) {
        organizationRepository.findByIdLocked(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", orgId));

        OrganizationMember organizationMember = organizationMemberRepository
                .findByUserIdAndOrganizationId(userId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationMember", "userId", userId));

        if (organizationMember.getRole() == OrgRole.OWNER) {
            long ownerCount = organizationMemberRepository.countByOrganizationIdAndRole(orgId, OrgRole.OWNER);
            if (ownerCount <= 1) {
                throw new IllegalArgumentException("Cannot delete the last owner of the organization. Promote someone else to OWNER first.");
            }
        }

        organizationMemberRepository.delete(organizationMember);
    }


    // Login/refresh need the default membership and must fail loudly if it's somehow
    // missing (that would mean a user with no organization at all, which the register/
    // Google-signup flows never allow to happen).
    public OrganizationMember getDefaultMembership(long userId) {
        return findDefaultMembership(userId)
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationMember", "userId", userId));
    }

    // Google sign-up needs the non-throwing form: no default membership yet is the
    // expected state for a brand new user, and it falls back to creating one.
    public Optional<OrganizationMember> findDefaultMembership(long userId) {
        return organizationMemberRepository.findByUserIdAndIsDefaultTrue(userId);
    }
}
