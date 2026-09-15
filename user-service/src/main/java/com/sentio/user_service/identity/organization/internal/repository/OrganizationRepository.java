package com.sentio.user_service.identity.organization.internal.repository;

import com.sentio.user_service.identity.organization.internal.entity.Organization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Organization o WHERE o.id = :id")
    Optional<Organization> findByIdLocked(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    boolean existsById(@Param("id") long id);
}
