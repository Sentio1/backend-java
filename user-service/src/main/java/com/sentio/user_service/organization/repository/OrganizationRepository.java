package com.sentio.user_service.organization.repository;

import com.sentio.user_service.organization.entity.Organization;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
/** OrganizationRepository interface. */
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Organization o WHERE o.id = :id")
    Optional<Organization> findByIdLocked(long id);
}
