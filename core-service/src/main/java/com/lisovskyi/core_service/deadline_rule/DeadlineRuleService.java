package com.lisovskyi.core_service.deadline_rule;

import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.court.CourtInstance;
import com.lisovskyi.core_service.deadline.DeadlineRepository;
import com.lisovskyi.core_service.deadline_rule.dto.request.DeadlineRuleCreateRequest;
import com.lisovskyi.core_service.deadline_rule.dto.request.DeadlineRuleUpdateRequest;
import com.lisovskyi.core_service.deadline_rule.dto.response.DeadlineRuleResponse;
import com.lisovskyi.core_service.deadline_rule.finder.DeadlineRuleFinder;
import com.lisovskyi.core_service.deadline_rule.mapper.DeadlineRuleMapper;
import com.lisovskyi.web.error.autoconfigure.standard.ForbiddenOperationException;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.deadline_rule.DeadlineRuleId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeadlineRuleService {

    private final DeadlineRuleFinder deadlineRuleFinder;
    private final DeadlineRuleRepository deadlineRuleRepository;
    private final DeadlineRepository deadlineRepository;
    private final DeadlineRuleMapper deadlineRuleMapper;

    @Transactional(readOnly = true)
    public PageResponse<DeadlineRuleResponse> getAllDeadlineRules(ProcedureType procedure, Pageable pageable) {
        return PageResponse.of(
                procedure != null
                ? deadlineRuleRepository.findAllByProcedure(procedure, pageable)
                        .map(deadlineRuleMapper::toResponse)
                : deadlineRuleFinder.findAll(pageable)
                    .map(deadlineRuleMapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public DeadlineRuleResponse getDeadlineRuleById(DeadlineRuleId deadlineRuleId) {
        return deadlineRuleMapper.toResponse(
                deadlineRuleFinder.findById(deadlineRuleId)
        );
    }

    @Transactional(readOnly = true)
    public List<DeadlineRuleResponse> getAllByCodeAndOrderByVersionDesc(String code) {
        return deadlineRuleFinder.findAllByCodeOrderByVersionDesc(code)
                .stream()
                .map(deadlineRuleMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeadlineRuleResponse getByCodeLatest(String code) {
        return deadlineRuleRepository.findByCodeOrderByVersionDesc(code)
                .map(deadlineRuleMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Deadline", "code", code));
    }

    @Transactional(readOnly = true)
    public DeadlineRuleResponse getActiveRule(ProcedureType procedure, EventCode eventCode, CourtInstance instance, LocalDate date) {
        return deadlineRuleFinder.findByActiveRule(procedure, eventCode, instance, date)
                .map(deadlineRuleMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Deadline rule not found"));
    }

    @Transactional
    public DeadlineRuleResponse createDeadlineRule(DeadlineRuleCreateRequest request) {
        List<DeadlineRule> rules = deadlineRuleFinder.findAllByCodeOrderByVersionDesc(request.code());

        DeadlineRule deadlineRuleNew = deadlineRuleMapper.toEntity(request);
        if (!rules.isEmpty()) {
            DeadlineRule previousRule = rules.getFirst();
            if (!previousRule.getValidFrom().isBefore(request.validFrom())) {
                throw new IllegalArgumentException("New rule valid from date must be after previous rule valid from date");
            }

            previousRule.setValidTo(request.validFrom());
            deadlineRuleNew.setVersion((short) (previousRule.getVersion() + 1));
        } else {
            deadlineRuleNew.setVersion((short) 1);
        }

        return deadlineRuleMapper.toResponse(deadlineRuleRepository.save(deadlineRuleNew));
    }

    @Transactional
    public DeadlineRuleResponse updateDeadlineRule(DeadlineRuleUpdateRequest request) {
        DeadlineRule deadlineRule = deadlineRuleFinder.findById(request.id());
        deadlineRuleMapper.updateEntityFromRequest(request, deadlineRule);
        return deadlineRuleMapper.toResponse(deadlineRuleRepository.save(deadlineRule));
    }

    @Transactional
    public void deleteDeadlineRule(DeadlineRuleId deadlineRuleId) {
        DeadlineRule deadlineRule = deadlineRuleFinder.findById(deadlineRuleId.id());
        if (deadlineRepository.existsByRuleId(deadlineRuleId.id())) {
            throw new ForbiddenOperationException("Deadline rule is used in deadlines");
        }

        deadlineRuleRepository.delete(deadlineRule);
    }
}
