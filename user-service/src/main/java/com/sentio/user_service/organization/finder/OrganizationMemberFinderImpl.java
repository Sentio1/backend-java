package com.sentio.user_service.organization.finder;

import com.sentio.shared.entity.finder.AbstractEntityFinder;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.organization.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrganizationMemberFinderImpl extends AbstractEntityFinder<OrganizationMember, Long> implements OrganizationMemberFinder {

    private final OrganizationMemberRepository organizationMemberRepository;

    @Override
    protected JpaRepository<OrganizationMember, Long> getRepository() {
        return organizationMemberRepository;
    }

    @Override
    protected String getEntityName() {
        return "OrganizationMember";
    }

    @Override
    public Page<OrganizationMember> findAllByOrganizationIdAndUserDeletedAtIsNull(long orgId, Pageable pageable) {
        return organizationMemberRepository.findAllByOrganizationIdAndUserDeletedAtIsNull(orgId, pageable);
    }

    @Override
    public List<OrganizationMember> findAllByUserId(long userId) {
        return organizationMemberRepository.findAllByUserId(userId);
    }

    @Override
    public Optional<OrganizationMember> findByUserIdAndIsDefaultTrue(long userId) {
        return organizationMemberRepository.findByUserIdAndIsDefaultTrue(userId);
    }

    @Override
    public Optional<OrganizationMember> findByUserIdAndOrganizationId(long userId, long organizationId) {
        return organizationMemberRepository.findByUserIdAndOrganizationId(userId, organizationId);
    }

    @Override
    public boolean existsByUserIdAndOrganizationId(long userId, long organizationId) {
        return organizationMemberRepository.existsByUserIdAndOrganizationId(userId, organizationId);
    }

    @Override
    public long countByOrganizationIdAndRole(long orgId, OrgRole role) {
        return organizationMemberRepository.countByOrganizationIdAndRole(orgId, role);
    }

    @Override
    public int countByUserId(long userId) {
        return organizationMemberRepository.countByUserId(userId);
    }
}
