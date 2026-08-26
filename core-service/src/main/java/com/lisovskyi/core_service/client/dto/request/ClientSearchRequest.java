package com.lisovskyi.core_service.client.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Bound from query params (?query=...) the same way {@code Pageable} is - as an implicit
 * {@code @ModelAttribute}. Validated through the normal {@code @Valid} +
 * {@code MethodArgumentNotValidException} path, already handled by
 * {@code GlobalExceptionHandler}, rather than a bare {@code @RequestParam} constraint (which
 * needs class-level {@code @Validated} and throws {@code ConstraintViolationException} instead).
 */
public record ClientSearchRequest(
        @NotBlank @Size(min = 2, max = 100) String query) {}
