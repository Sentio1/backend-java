package com.sentio.core_service.litigation.internal.service;

import com.sentio.core_service.client.api.spi.ClientActiveCasesChecker;
import com.sentio.core_service.litigation.api.enums.CaseStatus;
import com.sentio.core_service.litigation.internal.repository.CasePartyRepository;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Lets the client module refuse to delete a client who's still a party to an active case. */
@Component
@RequiredArgsConstructor
public class ClientActiveCasesCheckerImpl implements ClientActiveCasesChecker {

    private static final Set<CaseStatus> TERMINAL_CASE_STATUSES =
            Arrays.stream(CaseStatus.values()).filter(CaseStatus::isTerminal).collect(Collectors.toUnmodifiableSet());

    private final CasePartyRepository casePartyRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveCases(long clientId, long organizationId) {
        return casePartyRepository.existsActiveCaseForClient(clientId, organizationId, TERMINAL_CASE_STATUSES);
    }
}
