package com.lisovskyi.core_service.client.finder;

import com.lisovskyi.core_service.client.Client;
import com.sentio.shared.entity.finder.EntityFinder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ClientFinder extends EntityFinder<Client, Long> {

    Client findByIdAndOrganizationId(Long id, Long organizationId);

    Page<Client> findAllByOrganizationId(Long organizationId, Pageable pageable);

    Page<Client> findAllDeletedByOrganizationId(Long organizationId, Pageable pageable);

    Page<Client> searchClient(Long organizationId, String query, Pageable pageable);

    Client findDeletedByIdAndOrganizationId(Long id, Long organizationId);

    Client findByOrganizationIdAndRnokpp(Long organizationId, String rnokpp);

    Client findByOrganizationIdAndEdrpou(Long organizationId, String edrpou);

    boolean existsActiveByOrganizationIdAndRnokpp(Long organizationId, String rnokpp, Long excludeId);

    boolean existsActiveByOrganizationIdAndEdrpou(Long organizationId, String edrpou, Long excludeId);
}
