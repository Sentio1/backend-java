package com.lisovskyi.core_service.deadline_rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lisovskyi.core_service.TestcontainersConfiguration;
import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.CaseRepository;
import com.lisovskyi.core_service.case_.enums.CaseInstance;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.court.Court;
import com.lisovskyi.core_service.court.CourtInstance;
import com.lisovskyi.core_service.deadline.Deadline;
import com.lisovskyi.core_service.deadline.DeadlineRepository;
import com.lisovskyi.core_service.deadline_rule.dto.request.DeadlineRuleCreateRequest;
import com.lisovskyi.core_service.deadline_rule.dto.response.DeadlineRuleResponse;
import com.lisovskyi.core_service.deadline_rule.enums.CountFrom;
import com.lisovskyi.core_service.deadline_rule.enums.DayKind;
import com.lisovskyi.core_service.deadline_rule.enums.DurationUnit;
import com.lisovskyi.core_service.deadline_rule.finder.DeadlineRuleFinder;
import com.lisovskyi.web.error.autoconfigure.standard.ForbiddenOperationException;
import com.sentio.shared.entity.id.deadline_rule.DeadlineRuleId;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * SEN-24: the parts of the versioning story that only a real Postgres can verify - the DTO/mapper
 * unit tests and DeadlineRuleServiceTest already cover the branching logic against mocks, but the
 * whole point of the back-and-forth on this ticket was a chain of DB-constraint bugs (missing
 * NOT NULL columns, an exclusion constraint with the wrong daterange bound, an off-by-one gap
 * between two versions' validity windows) that no mock would ever have caught.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DeadlineRuleVersioningIT {

    @Autowired
    private DeadlineRuleService deadlineRuleService;

    @Autowired
    private DeadlineRuleRepository deadlineRuleRepository;

    @Autowired
    private DeadlineRuleFinder deadlineRuleFinder;

    @Autowired
    private DeadlineRepository deadlineRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private EntityManager entityManager;

    private void flushAndDetach() {
        entityManager.flush();
        entityManager.clear();
    }

    private DeadlineRuleCreateRequest request(String code, CourtInstance instance, EventCode trigger, LocalDate validFrom) {
        return new DeadlineRuleCreateRequest(
                code,
                ProcedureType.CIVIL,
                instance,
                trigger,
                "Тестове правило",
                "ст. 178 ЦПК України",
                (short) 15,
                DurationUnit.DAY,
                DayKind.CALENDAR,
                CountFrom.NEXT_DAY,
                true,
                validFrom,
                JsonNullable.undefined());
    }

    private Court persistCourt(String code) {
        Court court = Court.builder()
                .name("Печерський районний суд м. Києва")
                .code(code)
                .courtInstance(CourtInstance.FIRST)
                .timeZone("Europe/Kyiv")
                .isActive(true)
                .build();
        entityManager.persist(court);
        return court;
    }

    private Case persistCase(Court court) {
        Case case_ = Case.builder()
                .organizationId(9_001L)
                .responsibleUserId(1L)
                .title("Позов про стягнення заборгованості")
                .procedure(ProcedureType.CIVIL)
                .instance(CaseInstance.FIRST)
                .court(court)
                .build();
        return caseRepository.save(case_);
    }

    // ─── версіонування: create v1 -> create v2, round-trip через реальну БД ────────────────

    @Test
    void createDeadlineRule_secondVersion_closesFirstExactlyAtNewValidFrom_inRealDb() {
        String code = "IT_VERSIONING_1";
        deadlineRuleService.createDeadlineRule(request(code, CourtInstance.FIRST, EventCode.RULING_RECEIVED, LocalDate.of(2017, 12, 15)));
        flushAndDetach();

        deadlineRuleService.createDeadlineRule(request(code, CourtInstance.FIRST, EventCode.RULING_RECEIVED, LocalDate.of(2024, 1, 1)));
        flushAndDetach();

        List<DeadlineRule> versions = deadlineRuleRepository.findAllByCodeOrderByVersionDesc(code);
        assertThat(versions).hasSize(2);

        DeadlineRule latest = versions.get(0);
        DeadlineRule first = versions.get(1);
        assertThat(latest.getVersion()).isEqualTo((short) 2);
        assertThat(latest.getValidFrom()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(latest.getValidTo()).isNull();
        assertThat(first.getVersion()).isEqualTo((short) 1);
        // Без розриву й без перетину: перша версія закрита РІВНО там, де починається друга.
        assertThat(first.getValidTo()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    @Test
    void createDeadlineRule_thirdVersion_doesNotThrow_onceThereAreAlreadyTwoRows() {
        // Регресія: findByCodeLatestVersion (попередня реалізація) повертала Optional<...> без
        // LIMIT і падала з IncorrectResultSizeDataAccessException, щойно для code існувало більше
        // одного рядка - тобто рівно на третій версії. findAllByCodeOrderByVersionDesc не має цієї
        // проблеми.
        String code = "IT_VERSIONING_2";
        deadlineRuleService.createDeadlineRule(request(code, CourtInstance.FIRST, EventCode.RULING_RECEIVED, LocalDate.of(2017, 12, 15)));
        flushAndDetach();
        deadlineRuleService.createDeadlineRule(request(code, CourtInstance.FIRST, EventCode.RULING_RECEIVED, LocalDate.of(2020, 1, 1)));
        flushAndDetach();

        deadlineRuleService.createDeadlineRule(request(code, CourtInstance.FIRST, EventCode.RULING_RECEIVED, LocalDate.of(2024, 1, 1)));
        flushAndDetach();

        List<DeadlineRule> versions = deadlineRuleRepository.findAllByCodeOrderByVersionDesc(code);
        assertThat(versions).extracting(DeadlineRule::getVersion).containsExactly((short) 3, (short) 2, (short) 1);
    }

    @Test
    void createDeadlineRule_withValidFromNotAfterPrevious_rejectsBeforeTouchingTheDatabase() {
        String code = "IT_VERSIONING_3";
        deadlineRuleService.createDeadlineRule(request(code, CourtInstance.FIRST, EventCode.RULING_RECEIVED, LocalDate.of(2024, 1, 1)));
        flushAndDetach();

        assertThatThrownBy(() -> deadlineRuleService.createDeadlineRule(
                        request(code, CourtInstance.FIRST, EventCode.RULING_RECEIVED, LocalDate.of(2024, 1, 1))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(deadlineRuleRepository.findAllByCodeOrderByVersionDesc(code)).hasSize(1);
    }

    // ─── V29 exclusion constraint: захист від перекриття на рівні САМОЇ БД ─────────────────

    @Test
    void exclusionConstraint_rejectsOverlappingValidityRanges_forTheSameCode_evenBypassingTheService() {
        // Сервісна перевірка (validFrom має бути після попередньої) не дає дійти до цього
        // сценарію звичайним шляхом - тут навмисно зберігаємо два перекривні рядки напряму через
        // репозиторій, щоб перевірити САМ constraint із V29 (exclude_overlapping_rule_versions,
        // daterange(..., '[)')), незалежно від прикладного коду.
        String code = "IT_VERSIONING_OVERLAP";
        DeadlineRule first = DeadlineRule.builder()
                .code(code)
                .procedure(ProcedureType.CIVIL)
                .courtInstance(CourtInstance.FIRST)
                .triggerEventCode(EventCode.RULING_RECEIVED)
                .title("Перша версія")
                .legalBasis("ст. 178 ЦПК України")
                .durationValue((short) 15)
                .durationUnit(DurationUnit.DAY)
                .dayKind(DayKind.CALENDAR)
                .countFrom(CountFrom.NEXT_DAY)
                .isExtendable(false)
                .validFrom(LocalDate.of(2017, 12, 15))
                .validTo(LocalDate.of(2025, 1, 1))
                .version((short) 1)
                .build();
        deadlineRuleRepository.save(first);
        flushAndDetach();

        DeadlineRule overlapping = DeadlineRule.builder()
                .code(code)
                .procedure(ProcedureType.CIVIL)
                .courtInstance(CourtInstance.FIRST)
                .triggerEventCode(EventCode.RULING_RECEIVED)
                .title("Друга версія з перекриттям")
                .legalBasis("ст. 178 ЦПК України")
                .durationValue((short) 15)
                .durationUnit(DurationUnit.DAY)
                .dayKind(DayKind.CALENDAR)
                .countFrom(CountFrom.NEXT_DAY)
                .isExtendable(false)
                // Перекривається з першою версією на 2024-12-31 (перша ще не закрита) -
                // '[)' на обох діапазонах: [2024-12-31, ∞) і [..., 2025-01-01) справді перетинаються.
                .validFrom(LocalDate.of(2024, 12, 31))
                .version((short) 2)
                .build();
        deadlineRuleRepository.save(overlapping);
        entityManager.flush();

        // Конструкт EXCLUDE тепер DEFERRABLE INITIALLY DEFERRED (щоб не заважати
        // createDeadlineRule - див. коментар у V29): звичайний flush() у межах транзакції
        // більше НЕ перевіряє його одразу. SET CONSTRAINTS ALL IMMEDIATE примусово ганяє
        // відкладені перевірки негайно, не чекаючи COMMIT (якого в @Transactional-тесті й не
        // буде - лишень rollback).
        assertThatThrownBy(() -> entityManager.createNativeQuery("SET CONSTRAINTS ALL IMMEDIATE").executeUpdate())
                .hasMessageContaining("exclude_overlapping_rule_versions");
    }

    // ─── instance розрізняє правила з однаковим (procedure, triggerEventCode) ──────────────

    @Test
    void findByActiveRule_withSameProcedureAndTrigger_differentInstance_returnsTheMatchingRuleForEachInstance() {
        // Той самий сценарій, що й у сідингу V32: CPC_APPEAL_ON_DECISION (FIRST) і
        // CPC_CASSATION_APPEAL (APPEAL) ділять trigger_event_code=DECISION - без колонки instance
        // (V30) findByActiveRule не міг би розрізнити, яке з двох правил чинне. Тут навмисно
        // CLAIM_FILED, а не DECISION: V32 уже сіє CIVIL/DECISION для FIRST і APPEAL, і власний
        // (procedure, trigger, instance) тест зіткнувся б із тими рядками - findByActiveRule не
        // фільтрує за code, тож отримав би 2 результати замість одного.
        DeadlineRuleResponse firstInstanceRule = deadlineRuleService.createDeadlineRule(
                request("IT_INSTANCE_FIRST", CourtInstance.FIRST, EventCode.CLAIM_FILED, LocalDate.of(2017, 12, 15)));
        DeadlineRuleResponse appealInstanceRule = deadlineRuleService.createDeadlineRule(
                request("IT_INSTANCE_APPEAL", CourtInstance.APPEAL, EventCode.CLAIM_FILED, LocalDate.of(2017, 12, 15)));
        flushAndDetach();

        Optional<DeadlineRule> foundForFirst = deadlineRuleFinder.findByActiveRule(
                ProcedureType.CIVIL, EventCode.CLAIM_FILED, CourtInstance.FIRST, LocalDate.of(2024, 1, 1));
        Optional<DeadlineRule> foundForAppeal = deadlineRuleFinder.findByActiveRule(
                ProcedureType.CIVIL, EventCode.CLAIM_FILED, CourtInstance.APPEAL, LocalDate.of(2024, 1, 1));

        assertThat(foundForFirst).isPresent();
        assertThat(foundForFirst.get().getId()).isEqualTo(firstInstanceRule.id());
        assertThat(foundForAppeal).isPresent();
        assertThat(foundForAppeal.get().getId()).isEqualTo(appealInstanceRule.id());
    }

    // ─── delete-guard проти реального Deadline, що посилається на правило ──────────────────

    @Test
    void deleteDeadlineRule_referencedByARealDeadline_throwsForbiddenOperationException_andKeepsTheRow() {
        DeadlineRuleResponse createdRule = deadlineRuleService.createDeadlineRule(
                request("IT_DELETE_GUARD_USED", CourtInstance.FIRST, EventCode.RULING_RECEIVED, LocalDate.of(2024, 1, 1)));
        Court court = persistCourt("IT_DELETE_GUARD");
        Case case_ = persistCase(court);
        DeadlineRule ruleEntity = deadlineRuleRepository.findById(createdRule.id()).orElseThrow();
        Deadline deadline = Deadline.builder()
                .organizationId(case_.getOrganizationId())
                .case_(case_)
                .rule(ruleEntity)
                .title(ruleEntity.getTitle())
                .startsOn(LocalDate.of(2024, 1, 2))
                .dueOn(LocalDate.of(2024, 1, 17))
                .build();
        deadlineRepository.save(deadline);
        flushAndDetach();

        assertThatThrownBy(() -> deadlineRuleService.deleteDeadlineRule(DeadlineRuleId.of(createdRule.id())))
                .isInstanceOf(ForbiddenOperationException.class);

        assertThat(deadlineRuleRepository.findById(createdRule.id())).isPresent();
    }

    @Test
    void deleteDeadlineRule_notReferencedByAnyDeadline_removesIt() {
        DeadlineRuleResponse createdRule = deadlineRuleService.createDeadlineRule(
                request("IT_DELETE_GUARD_UNUSED", CourtInstance.FIRST, EventCode.RULING_RECEIVED, LocalDate.of(2024, 1, 1)));
        flushAndDetach();

        deadlineRuleService.deleteDeadlineRule(DeadlineRuleId.of(createdRule.id()));
        flushAndDetach();

        assertThat(deadlineRuleRepository.findById(createdRule.id())).isEmpty();
    }
}
