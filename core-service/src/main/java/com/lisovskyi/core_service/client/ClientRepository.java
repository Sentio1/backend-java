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

    boolean existsByOrganizationIdAndRnokpp(Long organizationId, String rnokpp);

    boolean existsByOrganizationIdAndEdrpou(Long organizationId, String edrpou);
}
