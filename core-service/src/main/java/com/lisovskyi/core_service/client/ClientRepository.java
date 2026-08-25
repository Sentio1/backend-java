package com.lisovskyi.core_service.client;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;import org.springframework.data.repository.query.Param;import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Page<Client> findAllByOrganizationId(Long organizationId, Pageable pageable);

    @Query("""
        SELECT c FROM Client c 
        WHERE c.organizationId = :organizationId 
        AND (
            LOWER(c.lastName) LIKE LOWER(CONCAT('%', :query, '%')) 
            OR LOWER(c.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(c.companyName) LIKE LOWER(CONCAT('%', :query, '%'))
        )
    """)
    Page<Client> searchClient(@Param("organizationId") Long organizationId, @Param("query") String query, Pageable pageable);

    Optional<Client> findByIdAndOrganizationId(Long id, Long organizationId);

}
