package com.sentio.core_service.litigation.api.service;

import com.sentio.core_service.litigation.api.dto.CaseEventAutoRegisterRequest;
import com.sentio.core_service.litigation.api.dto.TriggeringEvent;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CaseEventService {

    /** The event if it exists and belongs to this case of this organization. */
    Optional<TriggeringEvent> findTriggeringEvent(long caseEventId, long caseId, long organizationId);

    /** Snapshots of these (not deleted) events, regardless of organization - for system recalculations. */
    List<TriggeringEvent> findTriggeringEvents(Collection<Long> caseEventIds);

    /**
     * Registers an event found by the Registry Monitor. Throws ResourceAlreadyExistsException if
     * this registry document is already registered for the case.
     */
    void registerRegistryDocument(CaseId caseId, OrganizationId organizationId, CaseEventAutoRegisterRequest request);
}
