package com.lisovskyi.core_service.deadline_processor;

import static com.lisovskyi.core_service.deadline_processor.DeadlineCalculator.calculateStartsOn;
import static com.sentio.shared.persistence.ConstraintViolations.isUniqueConstraintViolation;

import com.lisovskyi.core_service.audit_log.AuditLogService;
import com.lisovskyi.core_service.audit_log.enums.EntityType;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.court.Court;
import com.lisovskyi.core_service.court.EventDateResolver;
import com.lisovskyi.core_service.deadline.Deadline;
import com.lisovskyi.core_service.deadline.DeadlineRepository;
import com.lisovskyi.core_service.deadline.enums.DeadlineSource;
import com.lisovskyi.core_service.deadline.enums.DeadlineStatus;
import com.lisovskyi.core_service.deadline.finder.DeadlineFinder;
import com.lisovskyi.core_service.deadline_processor.dto.DeadlineDatesResponse;
import com.lisovskyi.core_service.deadline_rule.DeadlineRule;
import com.lisovskyi.core_service.deadline_rule.finder.DeadlineRuleFinder;
import com.lisovskyi.core_service.holiday.finder.HolidayFinder;
import com.sentio.shared.entity.id.deadline.DeadlineId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeadlineEngine {

    private final DeadlineRepository deadlineRepository;
    private final DeadlineFinder deadlineFinder;
    private final DeadlineRuleFinder deadlineRuleFinder;
    private final HolidayFinder holidayFinder;
    private final AuditLogService auditLogService;


    // changedBy: null означає "немає людини-автора" (напр. автоматична реєстрація події з
    // Registry Monitor через registerRegistryCaseEvent, чи перерахунок від DeadlineListener при
    // зміні виробничого календаря - там ще нема SERVICE-токена з ідентичністю виклику, див.
    // README/SEN-33). Це більше не означає "не аудитувати": AuditLogService.logSystemChange
    // пише рядок з changedByType = SYSTEM і changedBy = null замість вигаданого "системного"
    // користувача, якого AuditLog.changedBy (коли не null - soft-ref на реального
    // auth.users.id) не мав би сенсу представляти.
    //
    // SEN-29: повертає СПИСОК сутностей, а не Optional<Long> - та сама подія (та сама пара
    // procedure + eventCode + instance + дата) може одночасно підпадати під кілька рядків
    // DeadlineRule (різний DeadlineRule.code), і кожне застосовне правило породжує власний
    // Deadline. Порожній список - "нема жодного застосовного правила" (не помилка, подія
    // все одно збережена викликачем до цього виклику - див. CaseEventService). Повні сутності,
    // а не самі id - викликачу (CaseEventService/CaseEventMapper) потрібен ще й status кожного
    // дедлайна для DeadlineResolution (AC2: REJECTED != NO_RULE_MATCHED), а зайвий SELECT після
    // save() того самого рядка був би зайвим.
    @Transactional
    public List<Deadline> generateDeadline(CaseEvent caseEvent, Long changedBy) {
        Court court = caseEvent.getCase_().getCourt();
        if (court == null) {
            log.info("Skipping deadline calculation for event id={}: case has no court", caseEvent.getId());
            return List.of();
        }

        LocalDate occurredAt = EventDateResolver.toLocalDate(caseEvent.getOccurredAt(), court);
        ProcedureType procedure = caseEvent.getCase_().getProcedure();
        EventCode triggerEventCode = caseEvent.getEventCode();

        List<DeadlineRule> rules = deadlineRuleFinder.findAllActiveRules(
                procedure, triggerEventCode, court.getCourtInstance(), occurredAt);
        if (rules.isEmpty()) {
            // не помилка, просто нема жодного застосовного правила
            return List.of();
        }

        ReconciliationResult reconciliation = reconcile(caseEvent, rules);

        List<Deadline> deadlines = new ArrayList<>();
        for (RulePairing pairing : reconciliation.pairings()) {
            deadlines.add(generateDeadlineForRule(
                    caseEvent, pairing.rule(), occurredAt, court, Optional.ofNullable(pairing.existing()), changedBy));
        }
        for (Deadline orphaned : reconciliation.orphaned()) {
            deadlines.add(autoRejectOrphaned(caseEvent, orphaned, changedBy));
        }
        return deadlines;
    }

    private record RulePairing(DeadlineRule rule, Deadline existing) {}

    // orphaned: рядки, що лишились PENDING/RULE, але жодне активне правило з rules більше їх
    // не покриває - обробляються окремо від pairings у generateDeadline (autoRejectOrphaned),
    // а не тут, бо це вже дія над сутністю (save + аудит), а reconcile() лише розподіляє.
    private record ReconciliationResult(List<RulePairing> pairings, List<Deadline> orphaned) {}

    // Зіставляє щойно знайдені активні правила з уже наявними Deadline-ами цієї події, щоб
    // "оновити в місці" правильний рядок, а не завжди створювати новий чи (гірше) дублювати.
    //
    // Прохід 1 - точний збіг по DeadlineRule.code: покриває звичайний перерахунок (та сама
    // подія, той самий набір застосовних правил, змінилась хіба дата чи виробничий календар).
    //
    // Прохід 2 - позиційний fallback для того, що лишилось незіставленим: коли зміна
    // procedure/instance справи (SEN-21) підбирає ІНШЕ правило (інший DeadlineRule.code) на
    // місце старого, код-збігу вже не буде, а другий рядок Deadline замість оновлення старого
    // на місці - саме той дубль, якого SEN-21-регресія (CaseUpdateRecalculatesDeadlinesIT)
    // навмисно пильнує. Позиційна відповідність (i-й лишившийся старий ↔ i-е лишившееся нове
    // правило) - евристика без кращого сигналу про "яке нове правило замінює яке старе", але
    // для типового 1-старе/1-нове вона детермінована й коректна.
    //
    // Якщо після обох проходів старих рядків лишилось більше, ніж нових правил - надлишок
    // повертається як orphaned і generateDeadline автоматично відхиляє кожен (не лишає
    // "мовчки PENDING зі строком, що вже нічому не відповідає").
    private ReconciliationResult reconcile(CaseEvent caseEvent, List<DeadlineRule> rules) {
        // SEN-29 AC3: юрист може прив'язати ручний (source = MANUAL) дедлайн до тієї самої
        // події - без цього фільтра позиційний fallback-прохід нижче міг би підхопити такий
        // рядок як "вільний слот" і переписати його rule/title/dueOn під розраховане правило,
        // тихо перетворивши ручний дедлайн на автоматичний. MANUAL ніколи не бере участі в
        // реконсиляції - лишається чіпаним лише через DeadlineService (AC3/AC4).
        //
        // status == PENDING - той самий фільтр, що вже є у findTriggeringEventsByStatusAndWindowCovering
        // (SEN-26): DONE/MISSED/REJECTED/SUSPENDED/EXTENDED - вже зафіксовані факти чи рішення
        // юриста, перерахунок їх не чіпає й не бере участі в підборі "вільного слота".
        List<Deadline> unmatchedExisting = deadlineFinder.findAllByTriggeringEvent(caseEvent).stream()
                .filter(existing -> existing.getSource() == DeadlineSource.RULE)
                .filter(existing -> existing.getStatus() == DeadlineStatus.PENDING)
                .sorted(Comparator.comparing(Deadline::getId))
                .collect(Collectors.toCollection(ArrayList::new));
        List<DeadlineRule> unmatchedRules = new ArrayList<>(rules);

        List<RulePairing> pairings = new ArrayList<>();
        Iterator<DeadlineRule> ruleIt = unmatchedRules.iterator();
        while (ruleIt.hasNext()) {
            DeadlineRule rule = ruleIt.next();
            Iterator<Deadline> existingIt = unmatchedExisting.iterator();
            while (existingIt.hasNext()) {
                Deadline existing = existingIt.next();
                if (existing.getRule() != null && existing.getRule().getCode().equals(rule.getCode())) {
                    pairings.add(new RulePairing(rule, existing));
                    ruleIt.remove();
                    existingIt.remove();
                    break;
                }
            }
        }

        int fallbackPairs = Math.min(unmatchedRules.size(), unmatchedExisting.size());
        for (int i = 0; i < fallbackPairs; i++) {
            pairings.add(new RulePairing(unmatchedRules.get(i), unmatchedExisting.get(i)));
        }
        for (int i = fallbackPairs; i < unmatchedRules.size(); i++) {
            pairings.add(new RulePairing(unmatchedRules.get(i), null));
        }

        List<Deadline> orphaned = unmatchedExisting.subList(fallbackPairs, unmatchedExisting.size());
        return new ReconciliationResult(pairings, orphaned);
    }

    // Раніше такі рядки просто лишались PENDING з попередженням у лог - юрист бачив у картці
    // справи строк, що вже нічому не відповідає, і не мав жодного сигналу про це. REJECTED -
    // той самий термінальний статус, що й для ручного відхилення (AC4), лише з rejectedBy=null
    // (немає людини-автора, як і для будь-якої SYSTEM-зміни в цьому класі) і системною причиною.
    // Аудит - тим самим двома записами (status, rejectionReason), що й DeadlineService.rejectDeadline,
    // але через logSystemChange, бо тут немає ні лаунчера-юриста, ні його UserId.
    private Deadline autoRejectOrphaned(CaseEvent caseEvent, Deadline orphaned, Long changedBy) {
        log.info(
                "Auto-rejecting deadline id={} for event id={}: no longer matches any active rule "
                        + "(case procedure/instance likely changed)",
                orphaned.getId(),
                caseEvent.getId());

        orphaned.setStatus(DeadlineStatus.REJECTED);
        orphaned.setRejectedAt(Instant.now());
        orphaned.setRejectedBy(changedBy);
        orphaned.setRejectionReason(
                "Автоматично відхилено: жодне активне правило більше не відповідає поточним "
                        + "параметрам справи (вид судочинства чи інстанція, ймовірно, змінились)");

        Deadline saved = deadlineRepository.save(orphaned);

        OrganizationId organizationId = OrganizationId.of(caseEvent.getOrganizationId());
        DeadlineId deadlineId = DeadlineId.of(saved.getId());
        if (changedBy != null) {
            auditLogService.log(
                    organizationId, EntityType.DEADLINE, deadlineId, UserId.of(changedBy),
                    "status", "PENDING", "REJECTED");
            auditLogService.log(
                    organizationId, EntityType.DEADLINE, deadlineId, UserId.of(changedBy),
                    "rejectionReason", null, saved.getRejectionReason());
        } else {
            auditLogService.logSystemChange(
                    organizationId, EntityType.DEADLINE, deadlineId, "status", "PENDING", "REJECTED");
            auditLogService.logSystemChange(
                    organizationId, EntityType.DEADLINE, deadlineId, "rejectionReason", null, saved.getRejectionReason());
        }

        return saved;
    }

    private Deadline generateDeadlineForRule(
            CaseEvent caseEvent,
            DeadlineRule rule,
            LocalDate occurredAt,
            Court court,
            Optional<Deadline> existingOpt,
            Long changedBy) {
        // Уся арифметика (з наступного дня чи з того самого, календарні/робочі дні,
        // перенесення закінчення з вихідного на робочий) - у DeadlineCalculator (SEN-27),
        // чистій функції без Spring і без репозиторіїв. DeadlineEngine лише дістає з БД те,
        // чого сама функція дістати не може (свята), і передає аргументами.
        LocalDate startsOn = calculateStartsOn(occurredAt, rule.getCountFrom());
        DeadlineDatesResponse dates = calculateDueOn(startsOn, rule, court);

        LocalDate dueOn = dates.dueOn();
        LocalDate naiveDueOn = dates.naiveDueOn();

        LocalDate oldDueOn = existingOpt.map(Deadline::getDueOn).orElse(null);

        Deadline deadline = existingOpt
                .map(existing -> {
                    log.info(
                            "Recalculating deadline id={} for event id={}, rule code={}: dueOn {} -> {}",
                            existing.getId(),
                            caseEvent.getId(),
                            rule.getCode(),
                            existing.getDueOn(),
                            dueOn);

                    existing.setRule(rule);
                    existing.setTitle(rule.getTitle());
                    existing.setLegalBasis(rule.getLegalBasis());
                    existing.setStartsOn(startsOn);
                    existing.setDueOn(dueOn);
                    existing.setRuleVersion(rule.getVersion());
                    existing.setBaseDate(occurredAt);
                    existing.setNaiveDueOn(naiveDueOn);
                    existing.setDurationValue(rule.getDurationValue());
                    existing.setDurationUnit(rule.getDurationUnit());
                    existing.setDayKind(rule.getDayKind());
                    return existing;
                })
                .orElseGet(() -> Deadline.builder()
                        .title(rule.getTitle())
                        .legalBasis(rule.getLegalBasis())
                        .organizationId(caseEvent.getOrganizationId())
                        .case_(caseEvent.getCase_())
                        .rule(rule)
                        .triggeringEvent(caseEvent)
                        .startsOn(startsOn)
                        .dueOn(dueOn)
                        .ruleVersion(rule.getVersion())
                        .baseDate(occurredAt)
                        .naiveDueOn(naiveDueOn)
                        .durationValue(rule.getDurationValue())
                        .durationUnit(rule.getDurationUnit())
                        .dayKind(rule.getDayKind())
                        .build());

        Deadline savedDeadline = saveWithRaceRecovery(deadline, caseEvent, rule);

        // Лише перерахунок УЖЕ наявного дедлайна (не первинне створення - там нема з чим
        // порівнювати "стару" дату, як і CaseService.updateCase не пише аудит на createCase),
        // і лише коли dueOn реально змінився. changedBy != null -> звичайний запис з людиною-
        // автором; changedBy == null -> той самий факт зміни, але з ChangedByType.SYSTEM
        // (див. коментар над generateDeadline) - в обох випадках рядок в історії лишається.
        if (oldDueOn != null && !Objects.equals(oldDueOn, dueOn)) {
            OrganizationId organizationId = OrganizationId.of(caseEvent.getOrganizationId());
            DeadlineId deadlineId = DeadlineId.of(savedDeadline.getId());
            if (changedBy != null) {
                auditLogService.log(
                        organizationId, EntityType.DEADLINE, deadlineId, UserId.of(changedBy),
                        "dueOn", oldDueOn.toString(), dueOn.toString());
            } else {
                auditLogService.logSystemChange(
                        organizationId, EntityType.DEADLINE, deadlineId,
                        "dueOn", oldDueOn.toString(), dueOn.toString());
            }
        }

        return savedDeadline;
    }

    // uq_deadlines_triggering_event_rule (V39) - остання лінія оборони проти дубліката для
    // тієї самої пари (подія, правило), якщо два виклики generateDeadline для одного й того ж
    // ще-не-наявного дедлайна перекрились у часі (обидва пройшли existingByRuleCode-перевірку
    // до того, як інший встиг закомітити INSERT). SELECT-перед-INSERT вище цю гонку не ловить -
    // лише індекс у БД. Замість падати назовні - тихо повертаємо рядок, який щойно вставив
    // конкурент: для читача результату (deadlineId) байдуже, яка саме транзакція фізично
    // вставила рядок, аби застосована пара (подія, правило) не подвоїлась.
    private Deadline saveWithRaceRecovery(Deadline deadline, CaseEvent caseEvent, DeadlineRule rule) {
        try {
            return deadlineRepository.save(deadline);
        } catch (DataIntegrityViolationException e) {
            if (deadline.getId() == null && isUniqueConstraintViolation(e, "uq_deadlines_triggering_event_rule")) {
                log.warn(
                        "Concurrent generateDeadline detected for event id={}, rule code={}: "
                                + "recovering the row inserted by the other transaction",
                        caseEvent.getId(),
                        rule.getCode());
                return deadlineFinder
                        .findByTriggeringEventAndRule(caseEvent, rule)
                        .orElseThrow(() -> e);
            }
            throw e;
        }
    }

    private DeadlineDatesResponse calculateDueOn(LocalDate startsOn, DeadlineRule rule, Court court) {
        // asOf = "сьогодні" в часовому поясі суду - календар таким, яким він відомий зараз;
        // рядки з effectiveFrom у майбутньому (оголошене, але ще не чинне перенесення) до
        // вибірки не потраплять. Матеріалізацію Map<дата, чи робочий> з core.holidays робить
        // HolidayFinder (SEN-26) - DeadlineEngine лише каже, за яке вікно і на яку дату.
        LocalDate asOf = LocalDate.now(EventDateResolver.toZoneId(court));
        Map<LocalDate, Boolean> holidayOverrides =
                holidayFinder.findWorkingDayOverrides(startsOn, holidayWindowEnd(startsOn, rule), asOf);

        // Періоди зупинення строку (SEN-27 AC) ще нізвідки брати - для їх зберігання й
        // редагування нема ні сутності, ні таблиці (окрема задача); тут завжди порожній
        // список, DeadlineCalculator у цьому випадку поводиться, так само як і без нього.
        return DeadlineCalculator.calculateDueOn(
                startsOn, rule.getDurationUnit(), rule.getDayKind(), rule.getDurationValue(), holidayOverrides);
    }

    // Запас під вибірку свят з БД: щедрий, бо це лише межа SELECT-у, а не результат
    // розрахунку - помилка тут означала б або зайвий рядок з БД (нешкідливо), або
    // IllegalStateException з DeadlineCalculator (видно одразу, не тиха хиба).
    private LocalDate holidayWindowEnd(LocalDate startsOn, DeadlineRule rule) {
        return switch (rule.getDurationUnit()) {
            case MONTH -> startsOn.plusMonths(rule.getDurationValue()).plusDays(10);
            case DAY -> startsOn.plusDays(rule.getDurationValue() * 7L + 60);
        };
    }
}
