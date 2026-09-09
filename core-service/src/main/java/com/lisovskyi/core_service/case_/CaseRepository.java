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

    // Явний DISTINCT: унікальний індекс на case_parties - (case_id, client_id, role), не
    // (case_id, client_id) - той самий клієнт легально буває стороною однієї справи під двома
    // ролями одразу (напр. VICTIM + APPLICANT після SEN-21), і на SQL-рівні такий JOIN реально
    // повертає два рядки на одну справу (перевірено нативним запитом). Hibernate тут наразі сам
    // дедуплікує root-entity результати за identity навіть без DISTINCT, тому видимого дубля в
    // Case-списку немає й без цього ключового слова - але покладатись на цю деталь реалізації
    // ORM небезпечно (інша версія/налаштування Hibernate, або перехід на проєкцію/DTO, можуть її
    // не мати), тож DISTINCT тут явний намір, а не косметика. JOIN, а не LEFT JOIN - WHERE
    // cp.client.id = :clientId все одно відкидає NULL-сторону LEFT JOIN, тож LEFT був фіктивний.
    @Query(
        "SELECT DISTINCT c FROM Case c " +
        "JOIN CaseParty cp ON cp.case_.id = c.id " +
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
