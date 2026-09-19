package com.sentio.core_service.client.internal.repository;

import com.sentio.core_service.client.internal.model.ClientActivity;

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
