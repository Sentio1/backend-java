package com.sentio.user_service.organization.repository;

import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
/** OrganizationMemberRepository interface. */
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {

    // AndUserDeletedAtIsNull is explicit on purpose, not relying on User's own
    // @SQLRestriction propagating through this EntityGraph join - that's exactly
    // the kind of thing that silently breaks across Hibernate versions.
    @EntityGraph(attributePaths = {"user", "organization"})
    Page<OrganizationMember> findAllByOrganizationIdAndUserDeletedAtIsNull(long orgId, Pageable pageable);

    List<OrganizationMember> findAllByUserId(long userId);

    Optional<OrganizationMember> findByUserIdAndIsDefaultTrue(long userId);

    Optional<OrganizationMember> findByUserIdAndOrganizationId(long userId, long organizationId);

    boolean existsByUserIdAndOrganizationId(long userId, long organizationId);

    long countByOrganizationIdAndRole(long orgId, OrgRole role);

    int countByUserId(long userId);

    void deleteAllByUserId(long userId);
}
