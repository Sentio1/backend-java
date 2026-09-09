package com.lisovskyi.core_service.case_.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.dto.request.CaseCreateRequest;
import com.lisovskyi.core_service.case_.dto.request.CaseUpdateRequest;
import com.lisovskyi.core_service.case_.enums.CaseInstance;
import com.lisovskyi.core_service.case_.enums.CaseStatus;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

// Регресія на SEN-21 review fix: updateEntityFromRequest раніше мапився без ignore=true на
// JsonNullable-полях, тож автозгенерований мапінг через unwrap() перезаписував КОЖНЕ поле
// (включно з відсутніми в тілі PATCH) - title/procedure/instance/status (усі NOT NULL) ставали
// null на будь-якому частковому оновленні, а responsibleUserId/registryWatchEnabled
// (примітивні long/boolean) падали NPE на автоанбоксингу. toResponse/CourtMapper тут навмисно
// не тестуються - CaseMapperImpl.courtMapper - @Autowired-поле, не піднімається без Spring-контексту.
class CaseMapperTest {

    private final CaseMapper caseMapper = new CaseMapperImpl();

    private Case existingCase() {
        return Case.builder()
                .organizationId(100L)
                .responsibleUserId(5L)
                .title("Позов про стягнення заборгованості")
                .procedure(ProcedureType.CIVIL)
                .instance(CaseInstance.FIRST)
                .status(CaseStatus.ACTIVE)
                .build();
    }

    private CaseUpdateRequest allUndefinedRequest() {
        return new CaseUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());
    }

    // ─── toEntity (create) ────────────────────────────────────────────

    @Test
    void toEntity_setsOrganizationIdAndCreatedByFromSeparateParameters_notFromRequestBody() {
        CaseCreateRequest request = new CaseCreateRequest(
                JsonNullable.undefined(),
                "Позов",
                ProcedureType.CIVIL,
                CaseInstance.FIRST,
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                7L);

        Case case_ = caseMapper.toEntity(request, 100L, 5L);

        assertThat(case_.getOrganizationId()).isEqualTo(100L);
        assertThat(case_.getCreatedBy()).isEqualTo(5L);
        assertThat(case_.getResponsibleUserId()).isEqualTo(7L);
        assertThat(case_.getTitle()).isEqualTo("Позов");
    }

    // ─── updateEntityFromRequest (PATCH semantics) ──────────────────────

    @Test
    void updateEntityFromRequest_withAllFieldsAbsent_doesNotTouchExistingValues() {
        Case case_ = existingCase();

        caseMapper.updateEntityFromRequest(allUndefinedRequest(), case_);

        assertThat(case_.getTitle()).isEqualTo("Позов про стягнення заборгованості");
        assertThat(case_.getProcedure()).isEqualTo(ProcedureType.CIVIL);
        assertThat(case_.getInstance()).isEqualTo(CaseInstance.FIRST);
        assertThat(case_.getStatus()).isEqualTo(CaseStatus.ACTIVE);
    }

    @Test
    void updateEntityFromRequest_withAllFieldsAbsent_leavesPrimitiveResponsibleUserId_withoutThrowing() {
        // Регресія: responsibleUserId - примітивний long на Case. Автозгенерований мапінг
        // JsonNullable<Long> -> long через unwrap() раніше падав NPE на автоанбоксингу щоразу,
        // коли responsibleUserId просто не передавали в тілі PATCH.
        Case case_ = existingCase();

        caseMapper.updateEntityFromRequest(allUndefinedRequest(), case_);

        assertThat(case_.getResponsibleUserId()).isEqualTo(5L);
    }

    @Test
    void updateEntityFromRequest_withAllFieldsAbsent_leavesPrimitiveRegistryWatchEnabled_withoutThrowing() {
        Case case_ = existingCase();
        case_.setRegistryWatchEnabled(true);

        caseMapper.updateEntityFromRequest(allUndefinedRequest(), case_);

        assertThat(case_.isRegistryWatchEnabled()).isTrue();
    }

    @Test
    void updateEntityFromRequest_withTitlePresent_replacesExistingValue() {
        Case case_ = existingCase();
        CaseUpdateRequest request = new CaseUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.of("Новий позов"),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        caseMapper.updateEntityFromRequest(request, case_);

        assertThat(case_.getTitle()).isEqualTo("Новий позов");
    }

    @Test
    void updateEntityFromRequest_withProcedureAndInstancePresent_replacesExistingValues() {
        // AC SEN-21: зміна виду судочинства/інстанції має бути можлива через PATCH - саме ці
        // поля CaseService.updateCase перевіряє на зміну, щоб тригернути перерахунок дедлайнів.
        Case case_ = existingCase();
        CaseUpdateRequest request = new CaseUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(ProcedureType.COMMERCIAL),
                JsonNullable.of(CaseInstance.APPEAL),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        caseMapper.updateEntityFromRequest(request, case_);

        assertThat(case_.getProcedure()).isEqualTo(ProcedureType.COMMERCIAL);
        assertThat(case_.getInstance()).isEqualTo(CaseInstance.APPEAL);
    }

    @Test
    void updateEntityFromRequest_withResponsibleUserIdPresent_replacesExistingValue() {
        Case case_ = existingCase();
        CaseUpdateRequest request = new CaseUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(99L),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        caseMapper.updateEntityFromRequest(request, case_);

        assertThat(case_.getResponsibleUserId()).isEqualTo(99L);
    }
}
