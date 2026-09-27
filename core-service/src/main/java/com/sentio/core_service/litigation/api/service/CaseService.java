package com.sentio.core_service.litigation.api.service;

import com.sentio.core_service.litigation.api.dto.CaseResponse;
import java.util.List;

public interface CaseService {

    /** Throws CaseNotFoundException (404) when the case doesn't exist in this organization. */
    void assertCaseExists(long caseId, long organizationId);

    /** Cases whose case number contains the query (case-insensitive), at most {@code limit}. */
    List<CaseResponse> searchByCaseNumber(long organizationId, String query, int limit);

    /** Cases where the client is a party, at most {@code limit}. */
    List<CaseResponse> findAllByClient(long clientId, long organizationId, int limit);
}
