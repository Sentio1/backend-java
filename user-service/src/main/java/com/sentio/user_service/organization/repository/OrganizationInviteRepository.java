package com.sentio.user_service.organization.repository;

import com.sentio.user_service.organization.entity.OrganizationInvite;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationInviteRepository extends JpaRepository<OrganizationInvite, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OrganizationInvite> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM OrganizationInvite i WHERE i.id = :id")
    Optional<OrganizationInvite> findByIdLocked(long id);

    List<OrganizationInvite> findAllByEmail(String email);

    Page<OrganizationInvite> findAllByOrganizationId(long orgId, Pageable pageable);

    boolean existsByOrganizationIdAndEmailAndAcceptedAtIsNullAndRevokedAtIsNull(long orgId, String email);
}
