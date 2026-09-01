package com.lisovskyi.core_service.case_.mapper;

import static com.sentio.shared.util.JsonNullableSupport.setIfPresent;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.dto.request.CaseCreateRequest;
import com.lisovskyi.core_service.case_.dto.request.CaseUpdateRequest;
import com.lisovskyi.core_service.case_.dto.response.CaseResponse;
import com.lisovskyi.core_service.court.mapper.CourtMapper;
import com.sentio.shared.util.JsonNullableSupport;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {JsonNullableSupport.class, CourtMapper.class})
public interface CaseMapper {

    // createdById - окремий параметр, як і в ClientMapper.toEntity: не з тіла запиту, а з
    // @CurrentUserId. status/court/... навмисно лишаються поза цим мапінгом - їх виставляє
    // CaseService (status/court/openedAt/closedAt/registryWatchEnabled - або мають дефолт на
    // entity (@Builder.Default), або заповнюються пізніше через CaseUpdateRequest, не при
    // створенні).
    @Mapping(target = "createdBy", source = "createdById")
    @Mapping(target = "organizationId", source = "organizationId")
    Case toEntity(CaseCreateRequest request, Long organizationId, Long createdById);

    CaseResponse toResponse(Case case_);

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
