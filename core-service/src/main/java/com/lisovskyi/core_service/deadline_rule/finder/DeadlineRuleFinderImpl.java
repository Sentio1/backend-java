package com.lisovskyi.core_service.deadline_rule.finder;

import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.deadline_rule.DeadlineRule;
import com.lisovskyi.core_service.deadline_rule.DeadlineRuleRepository;
import com.sentio.shared.entity.finder.AbstractEntityFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DeadlineRuleFinderImpl extends AbstractEntityFinder<DeadlineRule, Long> implements DeadlineRuleFinder {

    private final DeadlineRuleRepository deadlineRuleRepository;

    @Override
    protected JpaRepository<DeadlineRule, Long> getRepository() {
        return deadlineRuleRepository;
    }

    @Override
    protected String getEntityName() {
        return "DeadlineRule";
    }

    @Override
    public Optional<DeadlineRule> findByActiveRule(ProcedureType procedure, EventCode triggerEventCode, LocalDate date) {
        requireNonNull(procedure, triggerEventCode, date);
        return deadlineRuleRepository.findByActiveRule(procedure, triggerEventCode, date);
    }
}
