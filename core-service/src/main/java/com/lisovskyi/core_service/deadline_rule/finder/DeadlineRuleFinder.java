package com.lisovskyi.core_service.deadline_rule.finder;

import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.deadline_rule.DeadlineRule;
import com.sentio.shared.entity.finder.EntityFinder;

import java.time.LocalDate;
import java.util.Optional;

public interface DeadlineRuleFinder extends EntityFinder<DeadlineRule, Long> {

    Optional<DeadlineRule> findByActiveRule(ProcedureType procedure, EventCode triggerEventCode, LocalDate date);
}
