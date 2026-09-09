package com.lisovskyi.core_service.deadline_rule.finder;

import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.court.CourtInstance;
import com.lisovskyi.core_service.deadline_rule.DeadlineRule;
import com.lisovskyi.core_service.deadline_rule.DeadlineRuleRepository;
import com.sentio.shared.entity.finder.AbstractEntityFinder;
import com.sentio.shared.entity.id.deadline_rule.DeadlineRuleId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
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
    public List<DeadlineRule> findAllByCodeOrderByVersionDesc(String code) {
        requireNotBlank(code);
        return findAll(code, deadlineRuleRepository::findAllByCodeOrderByVersionDesc);
    }

    @Override
    public DeadlineRule findById(DeadlineRuleId deadlineRuleId) {
        return findById(deadlineRuleId.id());
    }

    @Override
    public Optional<DeadlineRule> findByActiveRule(ProcedureType procedure, EventCode triggerEventCode, CourtInstance instance, LocalDate date) {
        requireNonNull(procedure, triggerEventCode, date);
        return deadlineRuleRepository.findByActiveRule(procedure, triggerEventCode, instance, date);
    }

    @Override
    public List<DeadlineRule> findAllActiveRules(ProcedureType procedure, EventCode triggerEventCode, CourtInstance instance, LocalDate date) {
        requireNonNull(procedure, triggerEventCode, date);
        return deadlineRuleRepository.findAllActiveRules(procedure, triggerEventCode, instance, date);
    }
}
