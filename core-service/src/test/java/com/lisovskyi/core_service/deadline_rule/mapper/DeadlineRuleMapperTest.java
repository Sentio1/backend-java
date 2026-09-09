package com.lisovskyi.core_service.deadline_rule.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.court.CourtInstance;
import com.lisovskyi.core_service.deadline_rule.DeadlineRule;
import com.lisovskyi.core_service.deadline_rule.dto.request.DeadlineRuleCreateRequest;
import com.lisovskyi.core_service.deadline_rule.dto.request.DeadlineRuleUpdateRequest;
import com.lisovskyi.core_service.deadline_rule.dto.response.DeadlineRuleResponse;
import com.lisovskyi.core_service.deadline_rule.enums.CountFrom;
import com.lisovskyi.core_service.deadline_rule.enums.DayKind;
import com.lisovskyi.core_service.deadline_rule.enums.DurationUnit;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

// Регресія: toEntity(DeadlineRuleCreateRequest) генерується через DeadlineRule.builder()
// (SuperBuilder), а метод білдера для `private boolean isExtendable` називається буквально
// isExtendable(boolean) - як саме поле, без get/set-нормалізації. Автозіставлення за іменем
// бачило цю ціль як "isExtendable", а джерело (DeadlineRuleCreateRequest.extendable()) - як
// "extendable", тому мовчки лишало поле false незалежно від того, що передав клієнт (компілятор
// попереджав лише "Unmapped target property"). Явний @Mapping(target = "isExtendable", ...) на
// DeadlineRuleMapper.toEntity - фікс; тест нижче ловить регресію, якщо його прибрати.
class DeadlineRuleMapperTest {

    private final DeadlineRuleMapper deadlineRuleMapper = new DeadlineRuleMapperImpl();

    private DeadlineRule existingRule() {
        return DeadlineRule.builder()
                .code("CPC_STATEMENT_OF_DEFENCE")
                .procedure(ProcedureType.CIVIL)
                .courtInstance(CourtInstance.FIRST)
                .triggerEventCode(EventCode.RULING_RECEIVED)
                .title("Подання відзиву на позовну заяву")
                .legalBasis("ст. 178 ЦПК України")
                .durationValue((short) 15)
                .durationUnit(DurationUnit.DAY)
                .dayKind(DayKind.CALENDAR)
                .countFrom(CountFrom.NEXT_DAY)
                .isExtendable(true)
                .validFrom(LocalDate.of(2017, 12, 15))
                .version((short) 1)
                .build();
    }

    private DeadlineRuleCreateRequest createRequest(boolean extendable) {
        return new DeadlineRuleCreateRequest(
                "CPC_STATEMENT_OF_DEFENCE",
                ProcedureType.CIVIL,
                CourtInstance.FIRST,
                EventCode.RULING_RECEIVED,
                "Подання відзиву на позовну заяву",
                "ст. 178 ЦПК України",
                (short) 15,
                DurationUnit.DAY,
                DayKind.CALENDAR,
                CountFrom.NEXT_DAY,
                extendable,
                LocalDate.of(2017, 12, 15),
                JsonNullable.undefined());
    }

    // ─── toResponse ───────────────────────────────────────────────────────

    @Test
    void toResponse_mapsCourtInstanceToInstance_andExtendableFromGetter() {
        DeadlineRuleResponse response = deadlineRuleMapper.toResponse(existingRule());

        assertThat(response.instance()).isEqualTo(CourtInstance.FIRST);
        assertThat(response.extendable()).isTrue();
        assertThat(response.code()).isEqualTo("CPC_STATEMENT_OF_DEFENCE");
        assertThat(response.version()).isEqualTo((short) 1);
    }

    // ─── toEntity (create) ────────────────────────────────────────────────

    @Test
    void toEntity_mapsInstanceToCourtInstance() {
        DeadlineRule rule = deadlineRuleMapper.toEntity(createRequest(true));

        assertThat(rule.getCourtInstance()).isEqualTo(CourtInstance.FIRST);
    }

    @Test
    void toEntity_withExtendableTrue_setsIsExtendableTrue() {
        DeadlineRule rule = deadlineRuleMapper.toEntity(createRequest(true));

        assertThat(rule.isExtendable()).isTrue();
    }

    @Test
    void toEntity_withExtendableFalse_setsIsExtendableFalse() {
        // Не просто "поле лишилось false за замовчуванням" - без фіксу false тут теж "випадково
        // правильний" результат. Пара з попереднім тестом (true) - саме те, що ловить регресію:
        // якщо @Mapping прибрати, обидва значення схлопнуться в false.
        DeadlineRule rule = deadlineRuleMapper.toEntity(createRequest(false));

        assertThat(rule.isExtendable()).isFalse();
    }

    @Test
    void toEntity_ignoresVersionFromMapping_fallsBackToBuilderDefault() {
        // @Mapping(target = "version", ignore = true) - мапер узагалі не чіпає version, тож
        // toEntity() будується через DeadlineRule.builder() (SuperBuilder) з його
        // @Builder.Default = 1. DeadlineRuleService.createDeadlineRule одразу перезаписує
        // це своїм обчисленим значенням (1 для нового code, попередня + 1 для нової версії) -
        // цей тест лише фіксує, що мапер сам НІЧОГО свідомо не вираховує, а не яке саме число
        // тут з'являється.
        DeadlineRule rule = deadlineRuleMapper.toEntity(createRequest(true));

        assertThat(rule.getVersion()).isEqualTo((short) 1);
    }

    @Test
    void toEntity_withValidToPresent_unwrapsJsonNullable() {
        DeadlineRuleCreateRequest request = new DeadlineRuleCreateRequest(
                "CPC_STATEMENT_OF_DEFENCE",
                ProcedureType.CIVIL,
                CourtInstance.FIRST,
                EventCode.RULING_RECEIVED,
                "Подання відзиву на позовну заяву",
                "ст. 178 ЦПК України",
                (short) 15,
                DurationUnit.DAY,
                DayKind.CALENDAR,
                CountFrom.NEXT_DAY,
                true,
                LocalDate.of(2017, 12, 15),
                JsonNullable.of(LocalDate.of(2020, 1, 1)));

        DeadlineRule rule = deadlineRuleMapper.toEntity(request);

        assertThat(rule.getValidTo()).isEqualTo(LocalDate.of(2020, 1, 1));
    }

    @Test
    void toEntity_withValidToAbsent_leavesValidToNull() {
        DeadlineRule rule = deadlineRuleMapper.toEntity(createRequest(true));

        assertThat(rule.getValidTo()).isNull();
    }

    // ─── updateEntityFromRequest (PATCH semantics) ─────────────────────────

    private DeadlineRuleUpdateRequest allUndefinedUpdateRequest(long id) {
        return new DeadlineRuleUpdateRequest(
                id, JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());
    }

    @Test
    void updateEntityFromRequest_withAllFieldsAbsent_doesNotTouchExistingValues() {
        DeadlineRule rule = existingRule();

        deadlineRuleMapper.updateEntityFromRequest(allUndefinedUpdateRequest(1L), rule);

        assertThat(rule.getTitle()).isEqualTo("Подання відзиву на позовну заяву");
        assertThat(rule.getLegalBasis()).isEqualTo("ст. 178 ЦПК України");
        assertThat(rule.isExtendable()).isTrue();
        assertThat(rule.getValidTo()).isNull();
    }

    @Test
    void updateEntityFromRequest_doesNotTouchIdentityOrLegalSubstanceFields() {
        // AC SEN-24 "правило ніколи не редагується на місці": code/procedure/instance/
        // triggerEventCode/duration*/validFrom не мають полів у DeadlineRuleUpdateRequest
        // взагалі, тож ця перевірка - на випадок, якщо хтось "спростить" мапер і почне
        // мапити їх автоматично.
        DeadlineRule rule = existingRule();

        deadlineRuleMapper.updateEntityFromRequest(allUndefinedUpdateRequest(1L), rule);

        assertThat(rule.getCode()).isEqualTo("CPC_STATEMENT_OF_DEFENCE");
        assertThat(rule.getProcedure()).isEqualTo(ProcedureType.CIVIL);
        assertThat(rule.getCourtInstance()).isEqualTo(CourtInstance.FIRST);
        assertThat(rule.getTriggerEventCode()).isEqualTo(EventCode.RULING_RECEIVED);
        assertThat(rule.getDurationValue()).isEqualTo((short) 15);
        assertThat(rule.getValidFrom()).isEqualTo(LocalDate.of(2017, 12, 15));
    }

    @Test
    void updateEntityFromRequest_withTitlePresent_replacesExistingValue() {
        DeadlineRule rule = existingRule();
        DeadlineRuleUpdateRequest request = new DeadlineRuleUpdateRequest(
                1L, JsonNullable.of("Уточнена назва"), JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());

        deadlineRuleMapper.updateEntityFromRequest(request, rule);

        assertThat(rule.getTitle()).isEqualTo("Уточнена назва");
    }

    @Test
    void updateEntityFromRequest_withExtendablePresent_replacesExistingValue() {
        DeadlineRule rule = existingRule();
        DeadlineRuleUpdateRequest request = new DeadlineRuleUpdateRequest(
                1L, JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.of(false), JsonNullable.undefined());

        deadlineRuleMapper.updateEntityFromRequest(request, rule);

        assertThat(rule.isExtendable()).isFalse();
    }

    @Test
    void updateEntityFromRequest_withValidToPresent_closesTheRule() {
        DeadlineRule rule = existingRule();
        DeadlineRuleUpdateRequest request = new DeadlineRuleUpdateRequest(
                1L,
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(LocalDate.of(2026, 1, 1)));

        deadlineRuleMapper.updateEntityFromRequest(request, rule);

        assertThat(rule.getValidTo()).isEqualTo(LocalDate.of(2026, 1, 1));
    }
}
