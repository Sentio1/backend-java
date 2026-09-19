package com.sentio.core_service.litigation.internal.mapper;

import static com.sentio.shared.util.JsonNullableSupport.setIfPresent;

import com.sentio.core_service.litigation.internal.model.Case;
import com.sentio.core_service.litigation.internal.controller.dto.CaseCreateRequest;
import com.sentio.core_service.litigation.internal.controller.dto.CaseUpdateRequest;
import com.sentio.core_service.litigation.api.dto.CaseResponse;
import com.sentio.core_service.court.api.dto.CourtResponse;
import com.sentio.shared.util.JsonNullableSupport;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(uses = JsonNullableSupport.class)
public interface CaseMapper {

    // createdById - окремий параметр, як і в ClientMapper.toEntity: не з тіла запиту, а з
    // @CurrentUserId. status/court/... навмисно лишаються поза цим мапінгом - їх виставляє
    // CaseService (status/court/openedAt/closedAt/registryWatchEnabled - або мають дефолт на
    // entity (@Builder.Default), або заповнюються пізніше через CaseUpdateRequest, не при
    // створенні).
    @Mapping(target = "createdBy", source = "createdById")
    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deleteReason", ignore = true)
    @Mapping(target = "restoredAt", ignore = true)
    @Mapping(target = "restoredBy", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "openedAt", ignore = true)
    @Mapping(target = "closedAt", ignore = true)
    @Mapping(target = "registryWatchEnabled", ignore = true)
    @Mapping(target = "registryLastCheckedAt", ignore = true)
    Case toEntity(CaseCreateRequest request, Long organizationId, Long createdById);

    // court - resolved by the caller through court's CourtService (Case only stores courtId).
    @Mapping(target = "id", source = "case_.id")
    @Mapping(target = "court", source = "court")
    CaseResponse toResponse(Case case_, CourtResponse court);

    // Усі JsonNullable-поля ignore=true й проставляються вручну в applyPresentFields(...): це
    // PATCH, JsonNullable розрізняє "поле відсутнє в тілі" (не чіпати) від "поле є і дорівнює
    // null" (очистити) - автозгенерований мапінг через unwrap() зводить обидва випадки до null і
    // тому завжди перезаписував би поле, навіть коли воно просто не передане. Для
    // title/procedure/instance/status (усі NOT NULL на entity) це ламало б кожен частковий PATCH;
    // для responsibleUserId/registryWatchEnabled (примітивні long/boolean) unwrap(відсутнє)
    // повертав би null і падав NPE на автоанбоксингу в сеттер. Той самий підхід, що й
    // ClientMapper.applyPresentFields.
    @Mapping(target = "caseNumber", ignore = true)
    @Mapping(target = "internalNumber", ignore = true)
    @Mapping(target = "title", ignore = true)
    @Mapping(target = "procedure", ignore = true)
    @Mapping(target = "instance", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "judgeName", ignore = true)
    @Mapping(target = "responsibleUserId", ignore = true)
    @Mapping(target = "openedAt", ignore = true)
    @Mapping(target = "closedAt", ignore = true)
    @Mapping(target = "registryWatchEnabled", ignore = true)
    // courtId - set by CaseServiceImpl via setIfPresent (validated against the court module);
    // auto-mapping it here would null it on every PATCH that simply doesn't mention it.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deleteReason", ignore = true)
    @Mapping(target = "restoredAt", ignore = true)
    @Mapping(target = "restoredBy", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "registryLastCheckedAt", ignore = true)
    @Mapping(target = "courtId", ignore = true)
    void updateEntityFromRequest(CaseUpdateRequest request, @MappingTarget Case case_);

    @AfterMapping
    default void applyPresentFields(CaseUpdateRequest request, @MappingTarget Case case_) {
        setIfPresent(request.caseNumber(), case_::setCaseNumber);
        setIfPresent(request.internalNumber(), case_::setInternalNumber);
        setIfPresent(request.title(), case_::setTitle);
        setIfPresent(request.procedure(), case_::setProcedure);
        setIfPresent(request.instance(), case_::setInstance);
        setIfPresent(request.status(), case_::setStatus);
        setIfPresent(request.judgeName(), case_::setJudgeName);
        setIfPresent(request.responsibleUserId(), case_::setResponsibleUserId);
        setIfPresent(request.openedAt(), case_::setOpenedAt);
        setIfPresent(request.closedAt(), case_::setClosedAt);
        setIfPresent(request.registryWatchEnabled(), case_::setRegistryWatchEnabled);
    }
}
