package com.sentio.user_service.organization.service.finder;

import com.sentio.shared.entity.finder.AbstractEntityFinder;
import com.sentio.user_service.organization.entity.Organization;
import com.sentio.user_service.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrganizationFinderImpl extends AbstractEntityFinder<Organization, Long> implements OrganizationFinder {

    private final OrganizationRepository organizationRepository;

    @Override
    protected JpaRepository<Organization, Long> getRepository() {
        return organizationRepository;
    }

    @Override
    protected String getEntityName() {
        return "Organization";
    }

    @Override
    public Optional<Organization> findByIdLocked(long id) {
        return organizationRepository.findByIdLocked(id);
    }
}
