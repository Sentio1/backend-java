package com.sentio.user_service.identity.organization.internal.repository;

import com.sentio.user_service.identity.organization.api.enums.OrgRole;
import com.sentio.user_service.identity.organization.internal.entity.OrganizationMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {

    // OrganizationMember only holds a userId now (no User relation - the user module
    // owns identity), so a deleted-user filter can't be a join here. It isn't needed:
    // UserServiceImpl.deleteUser removes all of a user's memberships along with the
    // soft delete, so a membership of a deleted user doesn't exist.
    @EntityGraph(attributePaths = {"organization"})
    Page<OrganizationMember> findAllByOrganizationId(long orgId, Pageable pageable);

    List<OrganizationMember> findAllByUserId(long userId);

    Optional<OrganizationMember> findByUserIdAndIsDefaultTrue(long userId);

    Optional<OrganizationMember> findByUserIdAndOrganizationId(long userId, long organizationId);

    boolean existsByUserIdAndOrganizationId(long userId, long organizationId);

    long countByOrganizationIdAndRole(long orgId, OrgRole role);

    int countByUserId(long userId);

    void deleteAllByUserId(long userId);
}
