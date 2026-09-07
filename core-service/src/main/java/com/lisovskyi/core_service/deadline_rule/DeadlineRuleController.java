package com.lisovskyi.core_service.deadline_rule;

import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.court.CourtInstance;
import com.lisovskyi.core_service.deadline_rule.dto.request.DeadlineRuleCreateRequest;
import com.lisovskyi.core_service.deadline_rule.dto.request.DeadlineRuleUpdateRequest;
import com.lisovskyi.core_service.deadline_rule.dto.response.DeadlineRuleResponse;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.deadline_rule.DeadlineRuleId;
import com.sentio.shared.web.LocationUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/deadline-rules")
@RequiredArgsConstructor
@Validated
public class DeadlineRuleController {

    private final DeadlineRuleService deadlineRuleService;

    @GetMapping
    public ResponseEntity<PageResponse<DeadlineRuleResponse>> getAllDeadlineRules(
            @RequestParam(required = false) ProcedureType procedure,
            final Pageable pageable
    ) {
        return ResponseEntity.ok(deadlineRuleService.getAllDeadlineRules(procedure, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeadlineRuleResponse> getDeadlineRuleById(
            @PathVariable DeadlineRuleId id
    ) {
        return ResponseEntity.ok(deadlineRuleService.getDeadlineRuleById(id));
    }

    @GetMapping("/codes/{code}")
    public ResponseEntity<List<DeadlineRuleResponse>> getAllByCodeAndOrderByVersionDesc(
            @PathVariable String code
    ) {
        return ResponseEntity.ok(deadlineRuleService.getAllByCodeAndOrderByVersionDesc(code));
    }

    @GetMapping("/codes/{code}/current")
    public ResponseEntity<DeadlineRuleResponse> getCurrentByCode(
            @PathVariable String code
    ) {
        return ResponseEntity.ok(deadlineRuleService.getByCodeLatest(code));
    }

    // Усі чотири - обов'язкові (не required=false, як в getAllDeadlineRules.procedure):
    // findByActiveRule() однозначно шукає ОДНЕ правило за конкретною подією/датою, а не
    // фільтрує список, тож "часткового" виклику з якимось параметром відсутнім не існує -
    // deadlineRuleFinder.requireNonNull(...) все одно відхилить такий запит, просто з
    // менш зрозумілим повідомленням, ніж стандартне Spring "required parameter missing".
    @GetMapping("/active-rule")
    public ResponseEntity<DeadlineRuleResponse> getActiveRule(
            @RequestParam ProcedureType procedure,
            @RequestParam EventCode triggerEventCode,
            @RequestParam CourtInstance courtInstance,
            @RequestParam LocalDate date
    ) {
        return ResponseEntity.ok(deadlineRuleService.getActiveRule(procedure, triggerEventCode, courtInstance, date));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeadlineRuleResponse> createDeadlineRule(
            @RequestBody @Valid DeadlineRuleCreateRequest request
    ) {
        DeadlineRuleResponse response = deadlineRuleService.createDeadlineRule(request);
        return LocationUtility.createdWithLocation(response.id(), response);
    }

    @PatchMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeadlineRuleResponse> updateDeadlineRule(
            @RequestBody @Valid DeadlineRuleUpdateRequest request
    ) {
        return ResponseEntity.ok(deadlineRuleService.updateDeadlineRule(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDeadlineRule(
            @PathVariable DeadlineRuleId id
    ) {
        deadlineRuleService.deleteDeadlineRule(id);
        return ResponseEntity.noContent().build();
    }
}
