package com.lisovskyi.core_service.deadline_rule;

import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.lisovskyi.core_service.court.CourtInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeadlineRuleRepository extends JpaRepository<DeadlineRule, Long> {

    Page<DeadlineRule> findAllByProcedure(ProcedureType procedure, Pageable pageable);

    List<DeadlineRule> findAllByCodeOrderByVersionDesc(String code);

    @Query("""
        SELECT dr From DeadlineRule dr
        WHERE dr.procedure = :procedure
                AND dr.triggerEventCode = :triggerEventCode
                AND dr.courtInstance = :instance
                AND dr.validFrom <= :date
                AND (dr.validTo IS NULL or dr.validTo > :date)
        """)
    Optional<DeadlineRule> findByActiveRule(
            @Param("procedure") ProcedureType procedure,
            @Param("triggerEventCode") EventCode triggerEventCode,
            @Param("instance") CourtInstance instance,
            @Param("date") LocalDate date);

    // SEN-29: та сама умова, що й findByActiveRule вище, але без припущення "щонайбільше один
    // рядок" - різні DeadlineRule.code (різні строки, різна стаття) можуть законно мати
    // однакові (procedure, triggerEventCode, instance) й одночасно чинні періоди, коли та сама
    // подія відкриває кілька паралельних строків (напр. право на апеляцію й окремо право на
    // клопотання про роз'яснення того самого рішення). findByActiveRule лишається як є для
    // DeadlineRuleController.getActiveRule (адмінський перегляд "якЕ ОДНЕ правило" - там
    // множинний збіг і далі має падати як помилка конфігурації довідника, а не мовчки брати
    // перше). ORDER BY dr.id - лише для детермінованого порядку створюваних Deadline, не для
    // семантики (усі знайдені рядки застосовуються однаково).
    @Query("""
        SELECT dr From DeadlineRule dr
        WHERE dr.procedure = :procedure
                AND dr.triggerEventCode = :triggerEventCode
                AND dr.courtInstance = :instance
                AND dr.validFrom <= :date
                AND (dr.validTo IS NULL or dr.validTo > :date)
        ORDER BY dr.id
        """)
    List<DeadlineRule> findAllActiveRules(
            @Param("procedure") ProcedureType procedure,
            @Param("triggerEventCode") EventCode triggerEventCode,
            @Param("instance") CourtInstance instance,
            @Param("date") LocalDate date);

    @Query("""
        SELECT dr FROM DeadlineRule dr
        WHERE dr.code = :code
        ORDER BY dr.version DESC
        LIMIT 1
    """)
    Optional<DeadlineRule> findByCodeOrderByVersionDesc(@Param("code") String code);
}
