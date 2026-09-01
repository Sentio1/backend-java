package com.lisovskyi.core_service.client.finder;

import com.lisovskyi.core_service.client.Client;
import com.lisovskyi.core_service.client.ClientRepository;
import com.sentio.shared.entity.finder.AbstractEntityFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ClientFinderImpl extends AbstractEntityFinder<Client, Long> implements ClientFinder {

    private final ClientRepository clientRepository;

    @Override
    protected JpaRepository<Client, Long> getRepository() {
        return clientRepository;
    }

    @Override
    protected String getEntityName() {
        return "Client";
    }

    @Override
    public Client findByIdAndOrganizationId(Long id, Long organizationId) {
        return findBy(id, organizationId, clientRepository::findByIdAndOrganizationId);
    }

    @Override
    public Page<Client> findAllByOrganizationId(Long organizationId, Pageable pageable) {
        return findAll(organizationId, pageable, clientRepository::findAllByOrganizationId);
    }

    @Override
    public Page<Client> findAllDeletedByOrganizationId(Long organizationId, Pageable pageable) {
        return findAll(organizationId, pageable, clientRepository::findAllDeletedByOrganizationId);
    }

    @Override
    public Page<Client> searchClient(Long organizationId, String query, Pageable pageable) {
        return findAll(organizationId, query, pageable, clientRepository::searchClient);
    }

    @Override
    public Optional<Client> findDeletedByIdAndOrganizationId(Long id, Long organizationId) {
        requireNonNull(id, organizationId);
        return clientRepository.findDeletedByIdAndOrganizationId(id, organizationId);
    }

    @Override
    public boolean existsActiveByOrganizationIdAndRnokpp(Long organizationId, String rnokpp, Long excludeId) {
        return clientRepository.existsActiveByOrganizationIdAndRnokpp(organizationId, rnokpp, excludeId);
    }

    @Override
    public boolean existsActiveByOrganizationIdAndEdrpou(Long organizationId, String edrpou, Long excludeId) {
        return clientRepository.existsActiveByOrganizationIdAndEdrpou(organizationId, edrpou, excludeId);
    }
}
