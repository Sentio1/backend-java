package com.sentio.core_service.litigation.api.spi;

import com.sentio.core_service.litigation.api.dto.TriggeringEvent;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Implemented by the deadline module. Case events need their deadlines synchronously (a
 * registered event is returned together with the deadlines it produced), but the deadline module
 * itself depends on litigation (it reads cases and events) - so instead of litigation calling into
 * deadline, litigation declares here what it needs and deadline plugs the implementation in. All
 * methods join the caller's transaction.
 */
public interface CaseEventDeadlines {

    /**
     * (Re)computes the deadlines triggered by this event against the currently active rules and
     * returns all of them. changedBy null = no human author (registry import, calendar change) -
     * audited as a SYSTEM change.
     */
    List<CaseEventDeadline> regenerate(TriggeringEvent event, @Nullable Long changedBy);

    /** Deadlines per triggering case event id; events without deadlines are absent from the map. */
    Map<Long, List<CaseEventDeadline>> findByCaseEventIds(Collection<Long> caseEventIds);

    /** Soft-deletes every deadline triggered by this event. */
    void deleteForCaseEvent(long caseEventId, long deletedBy, @Nullable String deleteReason);

    /** Ids of all (not deleted) deadlines of the case - for the case's audit history. */
    List<Long> findDeadlineIdsByCase(long caseId, long organizationId);
}
