package com.sentio.user_service.organization.service.finder;

import com.sentio.shared.entity.finder.EntityFinder;
import com.sentio.user_service.organization.entity.OrganizationInvite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface OrganizationInviteFinder extends EntityFinder<OrganizationInvite, Long> {

    Page<OrganizationInvite> findAllByOrganizationId(long orgId, Pageable pageable);

    boolean existsByOrganizationIdAndEmailAndAcceptedAtIsNullAndRevokedAtIsNull(long orgId, String email);

    List<OrganizationInvite> findAllByEmail(String email);

    Optional<OrganizationInvite> findByTokenHash(String tokenHash);

    Optional<OrganizationInvite> findByIdLocked(long id);
}
