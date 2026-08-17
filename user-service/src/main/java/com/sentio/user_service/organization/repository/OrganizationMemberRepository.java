package com.sentio.user_service.organization.repository;

import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {

    Page<OrganizationMember> findAllByOrganizationId(long orgId, Pageable pageable);

    List<OrganizationMember> findAllByUserId(long userId);

    Optional<OrganizationMember> findByUserIdAndIsDefaultTrue(long userId);

    Optional<OrganizationMember> findByUserIdAndOrganizationId(long userId, long organizationId);

    boolean existsByUserIdAndOrganizationId(long userId, long organizationId);

    long countByOrganizationIdAndRole(long orgId, OrgRole role);

    void deleteAllByUserId(long userId);
}
