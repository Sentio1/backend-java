package com.lisovskyi.core_service.case_party;

import com.lisovskyi.core_service.case_.enums.CaseStatus;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CasePartyRepository extends JpaRepository<CaseParty, Long> {

    @Query("SELECT cp FROM CaseParty cp WHERE cp.id = :id AND cp.case_.id = :caseId AND cp.organizationId = :organizationId")
    Optional<CaseParty> findByIdAndCaseIdAndOrganizationId(
            @Param("id") Long id,
            @Param("caseId") Long caseId,
            @Param("organizationId") Long organizationId
    );

    @Query("SELECT cp FROM CaseParty cp WHERE cp.case_.id = :caseId AND cp.organizationId = :organizationId")
    Page<CaseParty> findAllByCaseIdAndOrganizationId(
            @Param("caseId") Long caseId,
            @Param("organizationId") Long organizationId,
            Pageable pageable
    );

    // @SQLRestriction("deleted_at IS NULL") на CoreEntity фільтрує будь-який JPQL-запит до цієї
    // сутності, тому restoreCaseParty не може знайти вже видалений рядок через звичайний
    // findByIdAndCaseIdAndOrganizationId - потрібен нативний запит в обхід рестрикції (той самий
    // підхід, що й ClientRepository.findDeletedByIdAndOrganizationId).
    @Query(
            value = "SELECT * FROM core.case_parties cp WHERE cp.id = :id AND cp.case_id = :caseId "
                    + "AND cp.organization_id = :organizationId AND cp.deleted_at IS NOT NULL",
            nativeQuery = true)
    Optional<CaseParty> findDeletedByIdAndCaseIdAndOrganizationId(
            @Param("id") Long id, @Param("caseId") Long caseId, @Param("organizationId") Long organizationId);

    @Query("SELECT COUNT(cp) > 0 FROM CaseParty cp " + "WHERE cp.client.id = :clientId "
            + "AND cp.organizationId = :organizationId "
            + "AND cp.case_.status NOT IN :terminalStatuses")
    boolean existsActiveCaseForClient(
            @Param("clientId") Long clientId,
            @Param("organizationId") Long organizationId,
            @Param("terminalStatuses") Set<CaseStatus> terminalStatuses);
}
