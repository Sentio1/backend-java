package com.sentio.core_service.deadline.internal.service;

import com.sentio.core_service.deadline.internal.exception.DeadlineRuleValidFromConflictException;
import com.sentio.core_service.deadline.internal.model.DeadlineRule;
import com.sentio.core_service.deadline.internal.repository.DeadlineRuleRepository;

import com.sentio.core_service.litigation.api.enums.ProcedureType;
import com.sentio.core_service.litigation.api.enums.EventCode;
import com.sentio.core_service.court.api.enums.CourtInstance;
import com.sentio.core_service.deadline.internal.repository.DeadlineRepository;
import com.sentio.core_service.deadline.internal.controller.dto.DeadlineRuleCreateRequest;
import com.sentio.core_service.deadline.internal.controller.dto.DeadlineRuleUpdateRequest;
import com.sentio.core_service.deadline.internal.controller.dto.DeadlineRuleResponse;
import com.sentio.core_service.deadline.internal.exception.DeadlineRuleNotFoundException;
import com.sentio.core_service.deadline.internal.mapper.DeadlineRuleMapper;
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

    private final DeadlineRuleRepository deadlineRuleRepository;
    private final DeadlineRepository deadlineRepository;
    private final DeadlineRuleMapper deadlineRuleMapper;

    @Transactional(readOnly = true)
    public PageResponse<DeadlineRuleResponse> getAllDeadlineRules(ProcedureType procedure, Pageable pageable) {
        return PageResponse.of(
                procedure != null
                ? deadlineRuleRepository.findAllByProcedure(procedure, pageable)
                        .map(deadlineRuleMapper::toResponse)
                : deadlineRuleRepository.findAll(pageable)
                    .map(deadlineRuleMapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public DeadlineRuleResponse getDeadlineRuleById(DeadlineRuleId deadlineRuleId) {
        return deadlineRuleMapper.toResponse(
                findRule(deadlineRuleId.id())
        );
    }

    @Transactional(readOnly = true)
    public List<DeadlineRuleResponse> getAllByCodeAndOrderByVersionDesc(String code) {
        return deadlineRuleRepository.findAllByCodeOrderByVersionDesc(code)
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
        return deadlineRuleRepository.findByActiveRule(procedure, eventCode, instance, date)
                .map(deadlineRuleMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Deadline rule not found"));
    }

    @Transactional
    public DeadlineRuleResponse createDeadlineRule(DeadlineRuleCreateRequest request) {
        List<DeadlineRule> rules = deadlineRuleRepository.findAllByCodeOrderByVersionDesc(request.code());

        DeadlineRule deadlineRuleNew = deadlineRuleMapper.toEntity(request);
        if (!rules.isEmpty()) {
            DeadlineRule previousRule = rules.getFirst();
            if (!previousRule.getValidFrom().isBefore(request.validFrom())) {
                throw new DeadlineRuleValidFromConflictException(
                        request.code(), previousRule.getValidFrom(), request.validFrom());
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
        DeadlineRule deadlineRule = findRule(request.id());
        deadlineRuleMapper.updateEntityFromRequest(request, deadlineRule);
        return deadlineRuleMapper.toResponse(deadlineRuleRepository.save(deadlineRule));
    }

    @Transactional
    public void deleteDeadlineRule(DeadlineRuleId deadlineRuleId) {
        DeadlineRule deadlineRule = findRule(deadlineRuleId.id());
        if (deadlineRepository.existsByRuleId(deadlineRuleId.id())) {
            throw new ForbiddenOperationException("Deadline rule is used in deadlines");
        }

        deadlineRuleRepository.delete(deadlineRule);
    }

    private DeadlineRule findRule(long deadlineRuleId) {
        return deadlineRuleRepository.findById(deadlineRuleId)
                .orElseThrow(() -> new DeadlineRuleNotFoundException(deadlineRuleId));
    }
}
