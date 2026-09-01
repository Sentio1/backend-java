package com.sentio.user_service.organization.service.finder;

import com.sentio.shared.entity.finder.AbstractEntityFinder;
import com.sentio.user_service.organization.entity.OrganizationInvite;
import com.sentio.user_service.organization.repository.OrganizationInviteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrganizationInviteFinderImpl extends AbstractEntityFinder<OrganizationInvite, Long> implements OrganizationInviteFinder {

    private final OrganizationInviteRepository organizationInviteRepository;

    @Override
    protected JpaRepository<OrganizationInvite, Long> getRepository() {
        return organizationInviteRepository;
    }

    @Override
    protected String getEntityName() {
        return "OrganizationInvite";
    }

    @Override
    public Page<OrganizationInvite> findAllByOrganizationId(long orgId, Pageable pageable) {
        return organizationInviteRepository.findAllByOrganizationId(orgId, pageable);
    }

    @Override
    public boolean existsByOrganizationIdAndEmailAndAcceptedAtIsNullAndRevokedAtIsNull(long orgId, String email) {
        return organizationInviteRepository.existsByOrganizationIdAndEmailAndAcceptedAtIsNullAndRevokedAtIsNull(orgId, email);
    }

    @Override
    public List<OrganizationInvite> findAllByEmail(String email) {
        requireNonNull(email);
        return organizationInviteRepository.findAllByEmail(email);
    }

    @Override
    public Optional<OrganizationInvite> findByTokenHash(String tokenHash) {
        requireNonNull(tokenHash);
        return organizationInviteRepository.findByTokenHash(tokenHash);
    }

    @Override
    public Optional<OrganizationInvite> findByIdLocked(long id) {
        return organizationInviteRepository.findByIdLocked(id);
    }
}
