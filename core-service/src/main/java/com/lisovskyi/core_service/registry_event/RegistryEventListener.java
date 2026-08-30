package com.lisovskyi.core_service.registry_event;

import static com.lisovskyi.core_service.registry_event.RegistryEventConstants.*;

import com.lisovskyi.core_service.case_event.CaseEventService;
import com.lisovskyi.core_service.case_event.dto.request.CaseEventAutoRegisterRequest;
import com.lisovskyi.core_service.registry_event.dto.RegistryDocumentFoundEvent;
import com.lisovskyi.core_service.registry_event.mapper.RegistryEventMapper;
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
            caseEventService.registerRegistryCaseEvent(
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
