package com.sentio.core_service.registry.internal;

import static com.sentio.core_service.registry.internal.RegistryEventConstants.*;

import com.sentio.core_service.litigation.api.service.CaseEventService;
import com.sentio.core_service.litigation.api.dto.CaseEventAutoRegisterRequest;
import com.sentio.core_service.registry.internal.dto.RegistryDocumentFoundEvent;
import com.sentio.core_service.registry.internal.mapper.RegistryEventMapper;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RegistryEventListener {

    private final CaseEventService caseEventService;
    private final RegistryEventMapper registryEventMapper;

    @KafkaListener(topics = DOCUMENT_FOUND_TOPIC)
    public void listen(RegistryDocumentFoundEvent event, Acknowledgment acknowledgment) {
        CaseEventAutoRegisterRequest caseEventAutoRegisterRequest = registryEventMapper.toRegisterRequest(event);

        try {
            caseEventService.registerRegistryDocument(
                    CaseId.of(event.caseId()), OrganizationId.of(event.orgId()), caseEventAutoRegisterRequest);
        } catch (ResourceAlreadyExistsException _) {
            log.info(
                    "Registry event already registered, skipping duplicate: caseId={}, registryDocumentId={}",
                    event.caseId(),
                    event.registryDocId());
        }

        acknowledgment.acknowledge();
    }
}
