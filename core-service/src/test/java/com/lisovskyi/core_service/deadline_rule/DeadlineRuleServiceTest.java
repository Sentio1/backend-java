package com.lisovskyi.core_service.deadline_rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.court.CourtInstance;
import com.lisovskyi.core_service.deadline.DeadlineRepository;
import com.lisovskyi.core_service.deadline_rule.dto.request.DeadlineRuleCreateRequest;
import com.lisovskyi.core_service.deadline_rule.dto.request.DeadlineRuleUpdateRequest;
import com.lisovskyi.core_service.deadline_rule.dto.response.DeadlineRuleResponse;
import com.lisovskyi.core_service.deadline_rule.enums.CountFrom;
import com.lisovskyi.core_service.deadline_rule.enums.DayKind;
import com.lisovskyi.core_service.deadline_rule.enums.DurationUnit;
import com.lisovskyi.core_service.deadline_rule.finder.DeadlineRuleFinder;
import com.lisovskyi.core_service.deadline_rule.mapper.DeadlineRuleMapper;
import com.lisovskyi.web.error.autoconfigure.standard.ForbiddenOperationException;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.entity.id.deadline_rule.DeadlineRuleId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * SEN-24: pins down the versioning branches in {@link DeadlineRuleService#createDeadlineRule}
 * (new code vs. new version of an existing one, and the validFrom-ordering guard) and the
 * delete-guard in {@link DeadlineRuleService#deleteDeadlineRule} - the two places that went
 * through several genuinely wrong implementations during review before landing on the current
 * one. Pure Mockito, no Spring context - the DB-constraint side of versioning (unique index,
 * exclusion constraint) is covered separately by {@code DeadlineRuleVersioningIT}.
 */
@ExtendWith(MockitoExtension.class)
class DeadlineRuleServiceTest {

    @Mock
    private DeadlineRuleFinder deadlineRuleFinder;

    @Mock
    private DeadlineRuleRepository deadlineRuleRepository;

    @Mock
    private DeadlineRepository deadlineRepository;

    @Mock
    private DeadlineRuleMapper deadlineRuleMapper;

    @InjectMocks
    private DeadlineRuleService deadlineRuleService;

    private DeadlineRule rule(long id, String code, short version, LocalDate validFrom, LocalDate validTo) {
        return DeadlineRule.builder()
                .id(id)
                .code(code)
                .procedure(ProcedureType.CIVIL)
                .courtInstance(CourtInstance.FIRST)
                .triggerEventCode(EventCode.RULING_RECEIVED)
                .title("Подання відзиву на позовну заяву")
                .legalBasis("ст. 178 ЦПК України")
                .durationValue((short) 15)
                .durationUnit(DurationUnit.DAY)
                .dayKind(DayKind.CALENDAR)
                .countFrom(CountFrom.NEXT_DAY)
                .isExtendable(false)
                .validFrom(validFrom)
                .validTo(validTo)
                .version(version)
                .build();
    }

    // DeadlineRuleResponse - record, тож замість мокати його (final, немає сенсу перевіряти
    // поведінку "заглушки"), будуємо реальний мінімальний екземпляр - той самий підхід, що й
    // SearchServiceTest.caseResponse/clientResponse.
    private DeadlineRuleResponse response(long id) {
        return new DeadlineRuleResponse(
                id, "CODE", ProcedureType.CIVIL, CourtInstance.FIRST, EventCode.RULING_RECEIVED, "title", "basis",
                (short) 15, DurationUnit.DAY, DayKind.CALENDAR, CountFrom.NEXT_DAY, false, LocalDate.of(2017, 12, 15),
                null, (short) 1);
    }

    private DeadlineRuleCreateRequest createRequest(String code, LocalDate validFrom) {
        return new DeadlineRuleCreateRequest(
                code,
                ProcedureType.CIVIL,
                CourtInstance.FIRST,
                EventCode.RULING_RECEIVED,
                "Подання відзиву на позовну заяву",
                "ст. 178 ЦПК України",
                (short) 15,
                DurationUnit.DAY,
                DayKind.CALENDAR,
                CountFrom.NEXT_DAY,
                false,
                validFrom,
                JsonNullable.undefined());
    }

    // ─── createDeadlineRule ───────────────────────────────────────────────

    @Test
    void createDeadlineRule_withNewCode_setsVersionOne_andDoesNotTouchAnyExistingRule() {
        DeadlineRuleCreateRequest request = createRequest("NEW_CODE", LocalDate.of(2024, 1, 1));
        DeadlineRule mapped = rule(0L, "NEW_CODE", (short) 0, LocalDate.of(2024, 1, 1), null);
        when(deadlineRuleFinder.findAllByCodeOrderByVersionDesc("NEW_CODE")).thenReturn(List.of());
        when(deadlineRuleMapper.toEntity(request)).thenReturn(mapped);
        when(deadlineRuleRepository.save(mapped)).thenReturn(mapped);
        when(deadlineRuleMapper.toResponse(mapped)).thenReturn(response(1L));

        deadlineRuleService.createDeadlineRule(request);

        assertThat(mapped.getVersion()).isEqualTo((short) 1);
        verify(deadlineRuleRepository, times(1)).save(any());
    }

    @Test
    void createDeadlineRule_withExistingCode_andLaterValidFrom_incrementsVersion_andClosesPreviousExactlyAtNewValidFrom() {
        DeadlineRule previous = rule(1L, "CPC_STATEMENT_OF_DEFENCE", (short) 1, LocalDate.of(2017, 12, 15), null);
        LocalDate newValidFrom = LocalDate.of(2024, 1, 1);
        DeadlineRuleCreateRequest request = createRequest("CPC_STATEMENT_OF_DEFENCE", newValidFrom);
        DeadlineRule mapped = rule(0L, "CPC_STATEMENT_OF_DEFENCE", (short) 0, newValidFrom, null);
        when(deadlineRuleFinder.findAllByCodeOrderByVersionDesc("CPC_STATEMENT_OF_DEFENCE"))
                .thenReturn(List.of(previous));
        when(deadlineRuleMapper.toEntity(request)).thenReturn(mapped);
        when(deadlineRuleRepository.save(mapped)).thenReturn(mapped);
        when(deadlineRuleMapper.toResponse(mapped)).thenReturn(response(1L));

        deadlineRuleService.createDeadlineRule(request);

        // Версія - попередня + 1, не "завжди 1" і не "залишити як є" - саме ці дві помилки
        // траплялись у попередніх реалізаціях цього методу.
        assertThat(mapped.getVersion()).isEqualTo((short) 2);
        // Без розриву й без перетину: previous.validTo == нового правила validFrom рівно
        // (V29 exclusion constraint - напіввідкритий '[)').
        assertThat(previous.getValidTo()).isEqualTo(newValidFrom);
    }

    @Test
    void createDeadlineRule_withValidFromNotAfterPrevious_throwsIllegalArgumentException_andNeverSaves() {
        DeadlineRule previous = rule(1L, "CPC_STATEMENT_OF_DEFENCE", (short) 1, LocalDate.of(2024, 1, 1), null);
        DeadlineRuleCreateRequest sameDateRequest =
                createRequest("CPC_STATEMENT_OF_DEFENCE", LocalDate.of(2024, 1, 1));
        when(deadlineRuleFinder.findAllByCodeOrderByVersionDesc("CPC_STATEMENT_OF_DEFENCE"))
                .thenReturn(List.of(previous));
        when(deadlineRuleMapper.toEntity(sameDateRequest)).thenReturn(rule(0L, "x", (short) 0, null, null));

        assertThatThrownBy(() -> deadlineRuleService.createDeadlineRule(sameDateRequest))
                .isInstanceOf(IllegalArgumentException.class);

        verify(deadlineRuleRepository, never()).save(any());
    }

    @Test
    void createDeadlineRule_withValidFromBeforePrevious_throwsIllegalArgumentException() {
        DeadlineRule previous = rule(1L, "CPC_STATEMENT_OF_DEFENCE", (short) 1, LocalDate.of(2024, 1, 1), null);
        DeadlineRuleCreateRequest earlierRequest =
                createRequest("CPC_STATEMENT_OF_DEFENCE", LocalDate.of(2020, 1, 1));
        when(deadlineRuleFinder.findAllByCodeOrderByVersionDesc("CPC_STATEMENT_OF_DEFENCE"))
                .thenReturn(List.of(previous));
        when(deadlineRuleMapper.toEntity(earlierRequest)).thenReturn(rule(0L, "x", (short) 0, null, null));

        assertThatThrownBy(() -> deadlineRuleService.createDeadlineRule(earlierRequest))
                .isInstanceOf(IllegalArgumentException.class);

        verify(deadlineRuleRepository, never()).save(any());
    }

    // ─── updateDeadlineRule ───────────────────────────────────────────────

    @Test
    void updateDeadlineRule_appliesPatchViaMapper_andSaves() {
        DeadlineRule existing = rule(1L, "CPC_STATEMENT_OF_DEFENCE", (short) 1, LocalDate.of(2017, 12, 15), null);
        DeadlineRuleUpdateRequest request = new DeadlineRuleUpdateRequest(
                1L,
                JsonNullable.of("Нова назва"),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());
        when(deadlineRuleFinder.findById(1L)).thenReturn(existing);
        when(deadlineRuleRepository.save(existing)).thenReturn(existing);
        when(deadlineRuleMapper.toResponse(existing)).thenReturn(response(1L));

        deadlineRuleService.updateDeadlineRule(request);

        verify(deadlineRuleMapper).updateEntityFromRequest(request, existing);
        verify(deadlineRuleRepository).save(existing);
    }

    // ─── deleteDeadlineRule ───────────────────────────────────────────────

    @Test
    void deleteDeadlineRule_notReferencedByAnyDeadline_deletesIt() {
        DeadlineRuleId id = DeadlineRuleId.of(1L);
        DeadlineRule existing = rule(1L, "CPC_STATEMENT_OF_DEFENCE", (short) 1, LocalDate.of(2017, 12, 15), null);
        when(deadlineRuleFinder.findById(1L)).thenReturn(existing);
        when(deadlineRepository.existsByRuleId(1L)).thenReturn(false);

        deadlineRuleService.deleteDeadlineRule(id);

        verify(deadlineRuleRepository).delete(existing);
    }

    @Test
    void deleteDeadlineRule_referencedByADeadline_throwsForbiddenOperationException_andNeverDeletes() {
        // Регресія: ця гілка раніше кидала ResourceNotFoundException (404) для рядка, який щойно
        // знайшли - тобто клієнт бачив "не знайдено" замість "заборонено, бо використовується".
        DeadlineRuleId id = DeadlineRuleId.of(1L);
        DeadlineRule existing = rule(1L, "CPC_STATEMENT_OF_DEFENCE", (short) 1, LocalDate.of(2017, 12, 15), null);
        when(deadlineRuleFinder.findById(1L)).thenReturn(existing);
        when(deadlineRepository.existsByRuleId(1L)).thenReturn(true);

        assertThatThrownBy(() -> deadlineRuleService.deleteDeadlineRule(id))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(deadlineRuleRepository, never()).delete(any());
    }

    // ─── getActiveRule ──────────────────────────────────────────────────

    @Test
    void getActiveRule_whenFound_mapsToResponse() {
        DeadlineRule found = rule(1L, "CPC_STATEMENT_OF_DEFENCE", (short) 1, LocalDate.of(2017, 12, 15), null);
        DeadlineRuleResponse expected = response(1L);
        when(deadlineRuleFinder.findByActiveRule(
                        ProcedureType.CIVIL, EventCode.RULING_RECEIVED, CourtInstance.FIRST, LocalDate.of(2024, 1, 1)))
                .thenReturn(Optional.of(found));
        when(deadlineRuleMapper.toResponse(found)).thenReturn(expected);

        DeadlineRuleResponse result = deadlineRuleService.getActiveRule(
                ProcedureType.CIVIL, EventCode.RULING_RECEIVED, CourtInstance.FIRST, LocalDate.of(2024, 1, 1));

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getActiveRule_whenNotFound_throwsResourceNotFoundException() {
        when(deadlineRuleFinder.findByActiveRule(any(), any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deadlineRuleService.getActiveRule(
                        ProcedureType.CIVIL, EventCode.RULING_RECEIVED, CourtInstance.FIRST, LocalDate.of(2024, 1, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── getByCodeLatest ────────────────────────────────────────────────

    @Test
    void getByCodeLatest_whenFound_mapsToResponse() {
        DeadlineRule found = rule(1L, "CPC_STATEMENT_OF_DEFENCE", (short) 2, LocalDate.of(2024, 1, 1), null);
        DeadlineRuleResponse expected = response(1L);
        when(deadlineRuleRepository.findByCodeOrderByVersionDesc("CPC_STATEMENT_OF_DEFENCE"))
                .thenReturn(Optional.of(found));
        when(deadlineRuleMapper.toResponse(found)).thenReturn(expected);

        assertThat(deadlineRuleService.getByCodeLatest("CPC_STATEMENT_OF_DEFENCE")).isSameAs(expected);
    }

    @Test
    void getByCodeLatest_whenNotFound_throwsResourceNotFoundException() {
        when(deadlineRuleRepository.findByCodeOrderByVersionDesc("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deadlineRuleService.getByCodeLatest("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── getAllDeadlineRules ──────────────────────────────────────────────

    @Test
    void getAllDeadlineRules_withProcedure_filtersByProcedure() {
        Pageable pageable = PageRequest.of(0, 20);
        DeadlineRule found = rule(1L, "CPC_STATEMENT_OF_DEFENCE", (short) 1, LocalDate.of(2017, 12, 15), null);
        when(deadlineRuleRepository.findAllByProcedure(ProcedureType.CIVIL, pageable))
                .thenReturn(new PageImpl<>(List.of(found)));
        when(deadlineRuleMapper.toResponse(found)).thenReturn(response(1L));

        deadlineRuleService.getAllDeadlineRules(ProcedureType.CIVIL, pageable);

        verify(deadlineRuleFinder, never()).findAll(any(Pageable.class));
    }

    @Test
    void getAllDeadlineRules_withoutProcedure_returnsUnfilteredPage() {
        Pageable pageable = PageRequest.of(0, 20);
        when(deadlineRuleFinder.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        deadlineRuleService.getAllDeadlineRules(null, pageable);

        verify(deadlineRuleRepository, never()).findAllByProcedure(any(), any());
    }
}
