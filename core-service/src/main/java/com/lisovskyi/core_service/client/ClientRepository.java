package com.lisovskyi.core_service.client;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Page<Client> findAllByOrganizationId(Long organizationId, Pageable pageable);

    @Query(
            value =
                    "SELECT * FROM core.clients c WHERE c.deleted_at IS NOT NULL AND c.organization_id = :organizationId",
            nativeQuery = true)
    Page<Client> findAllDeletedByOrganizationId(Long organizationId, Pageable pageable);

    @Query(value = """
    SELECT * FROM core.clients c
    WHERE c.organization_id = :organizationId
    AND (COALESCE(c.last_name, '') || ' ' || COALESCE(c.first_name, '') || ' ' || COALESCE(c.company_name, ''))
        ILIKE CONCAT('%', :query, '%')
    AND c.deleted_at IS NULL
    """, nativeQuery = true)
    Page<Client> searchClient(
            @Param("organizationId") Long organizationId, @Param("query") String query, Pageable pageable);

    Optional<Client> findByIdAndOrganizationId(Long id, Long organizationId);

    @Query(
            value =
                    "SELECT * FROM core.clients c WHERE c.id = :id AND c.organization_id = :organizationId AND c.deleted_at IS NOT NULL",
            nativeQuery = true)
    Optional<Client> findDeletedByIdAndOrganizationId(Long id, Long organizationId);

    Optional<Client> findByOrganizationIdAndRnokpp(Long organizationId, String rnokpp);

    Optional<Client> findByOrganizationIdAndEdrpou(Long organizationId, String edrpou);

    @Query("""
    SELECT COUNT(c) > 0 FROM Client c
    WHERE c.organizationId = :organizationId
      AND c.rnokpp = :rnokpp
      AND (:excludeId IS NULL OR c.id != :excludeId)
      AND c.deletedAt IS NULL
""")
    boolean existsActiveByOrganizationIdAndRnokpp(
            @Param("organizationId") Long organizationId,
            @Param("rnokpp") String rnokpp,
            @Param("excludeId") Long excludeId);

    @Query("""
    SELECT COUNT(c) > 0 FROM Client c
    WHERE c.organizationId = :organizationId
      AND c.edrpou = :edrpou
      AND (:excludeId IS NULL OR c.id != :excludeId)
      AND c.deletedAt IS NULL
""")
    boolean existsActiveByOrganizationIdAndEdrpou(
            @Param("organizationId") Long organizationId,
            @Param("edrpou") String edrpou,
            @Param("excludeId") Long excludeId);
}
