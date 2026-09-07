package com.lisovskyi.core_service.deadline_rule.finder;

import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.court.CourtInstance;
import com.lisovskyi.core_service.deadline_rule.DeadlineRule;
import com.sentio.shared.entity.finder.EntityFinder;
import com.sentio.shared.entity.id.deadline_rule.DeadlineRuleId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DeadlineRuleFinder extends EntityFinder<DeadlineRule, Long> {

    List<DeadlineRule> findAllByCodeOrderByVersionDesc(String code);

    DeadlineRule findById(DeadlineRuleId deadlineRuleId);

    Optional<DeadlineRule> findByActiveRule(ProcedureType procedure, EventCode triggerEventCode, CourtInstance instance, LocalDate date);
}
