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

    @Query("""
        SELECT dr FROM DeadlineRule dr
        WHERE dr.code = :code
        ORDER BY dr.version DESC
        LIMIT 1
    """)
    Optional<DeadlineRule> findByCodeOrderByVersionDesc(@Param("code") String code);
}
