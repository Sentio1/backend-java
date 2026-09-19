package com.sentio.core_service.litigation.api.dto;

import static com.sentio.core_service.litigation.internal.CaseConstants.TITLE_LENGTH;
import static com.sentio.core_service.litigation.internal.CaseEventConstants.REGISTRY_DOCUMENT_TEXT_REF_LENGTH;

import com.sentio.core_service.litigation.api.enums.EventCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Builder;

@Builder
public record CaseEventAutoRegisterRequest(
        @NotNull EventCode eventCode,

        @NotBlank @Size(max = TITLE_LENGTH) String title,

        String description,

        @NotNull Instant occurredAt,

        @NotNull Long registryDocumentId,

        @Size(max = REGISTRY_DOCUMENT_TEXT_REF_LENGTH) String registryDocumentTextRef) {}
