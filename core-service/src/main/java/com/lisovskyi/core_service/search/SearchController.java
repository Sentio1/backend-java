package com.lisovskyi.core_service.search;

import com.lisovskyi.core_service.search.dto.response.SearchResponse;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.security.CurrentOrganizationId;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Validated
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public ResponseEntity<SearchResponse> search(
            @CurrentOrganizationId OrganizationId organizationId,
            @RequestParam @NotBlank String query
    ) {
        return ResponseEntity.ok(searchService.search(organizationId, query));
    }
}
