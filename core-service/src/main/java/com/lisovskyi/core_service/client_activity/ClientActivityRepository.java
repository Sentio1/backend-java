package com.lisovskyi.core_service.client_activity;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientActivityRepository extends JpaRepository<ClientActivity, Long> {

    Page<ClientActivity> findAllByOrganizationId(Long organizationId, Pageable pageable);

    Optional<ClientActivity> findByIdAndOrganizationId(Long id, Long organizationId);
}
