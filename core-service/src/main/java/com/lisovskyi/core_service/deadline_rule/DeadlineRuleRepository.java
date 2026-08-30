package com.lisovskyi.core_service.deadline_rule;

import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DeadlineRuleRepository extends JpaRepository<DeadlineRule, Long> {

    @Query(
        """
        SELECT r From DeadlineRule r
        WHERE r.procedure = :procedure
                AND r.triggerEventCode = :triggerEventCode
                AND r.validFrom <= :date
                AND (r.validTo IS NULL or r.validTo > :date)
        """)
    Optional<DeadlineRule> findByActiveRule(
            @Param("procedure") ProcedureType procedure,
            @Param("triggerEventCode")EventCode triggerEventCode,
            @Param("date") LocalDate date
    );
}
