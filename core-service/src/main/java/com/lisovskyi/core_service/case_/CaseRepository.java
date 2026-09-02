package com.lisovskyi.core_service.case_;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseRepository extends JpaRepository<Case, Long> {

    Page<Case> findAllByOrganizationId(Long organizationId, Pageable pageable);

    Page<Case> findByOrganizationIdAndCaseNumberContainingIgnoreCase(Long organizationId, String caseNumber, Pageable pageable);

    @Query(
        "SELECT c FROM Case c " +
        "LEFT JOIN CaseParty cp ON cp.case_.id = c.id " +
        "WHERE cp.client.id = :clientId " +
        "AND cp.organizationId = :organizationId " +
        "AND c.organizationId = :organizationId"
    )
    Page<Case> findAllByClientIdAndOrganizationId(Long clientId, Long organizationId, Pageable pageable);

    Optional<Case> findByIdAndOrganizationId(Long id, Long organizationId);

    boolean existsByIdAndOrganizationId(Long id, Long organizationId);

    @Query(
            value = "SELECT * FROM core.cases c WHERE c.id = :id AND c.organization_id = :organizationId "
                    + "AND c.deleted_at IS NOT NULL",
            nativeQuery = true)
    Optional<Case> findDeletedByIdAndOrganizationId(@Param("id") Long id, @Param("organizationId") Long organizationId);
}
