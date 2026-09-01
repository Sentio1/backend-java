package com.sentio.user_service.organization.service.finder;

import com.sentio.shared.entity.finder.EntityFinder;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface OrganizationMemberFinder extends EntityFinder<OrganizationMember, Long> {

    Page<OrganizationMember> findAllByOrganizationIdAndUserDeletedAtIsNull(long orgId, Pageable pageable);

    List<OrganizationMember> findAllByUserId(long userId);

    Optional<OrganizationMember> findByUserIdAndIsDefaultTrue(long userId);

    Optional<OrganizationMember> findByUserIdAndOrganizationId(long userId, long organizationId);

    boolean existsByUserIdAndOrganizationId(long userId, long organizationId);

    long countByOrganizationIdAndRole(long orgId, OrgRole role);

    int countByUserId(long userId);
}
