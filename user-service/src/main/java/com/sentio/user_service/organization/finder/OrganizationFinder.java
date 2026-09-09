package com.sentio.user_service.organization.finder;

import com.sentio.shared.entity.finder.EntityFinder;
import com.sentio.user_service.organization.entity.Organization;

import java.util.Optional;

public interface OrganizationFinder extends EntityFinder<Organization, Long> {

    Optional<Organization> findByIdLocked(long id);
}
