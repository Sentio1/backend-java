package com.sentio.shared.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** PageResponse record. */
public record PageResponse<T>(
        List<T> content, int pageNumber, int pageSize, long totalElements, int totalPages, boolean isLast) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<T>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
