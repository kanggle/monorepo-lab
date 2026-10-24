package com.example.security.service.query.internal;

import com.example.security.service.query.SecurityQueryService;
import com.example.security.service.query.dto.LoginHistoryView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/internal/security")
@RequiredArgsConstructor
public class LoginHistoryQueryController {

    private final SecurityQueryService queryService;

    /**
     * TASK-BE-656 — {@code tenantId} is required (no default): the tenant admin-service already gated,
     * {@code *} = every tenant ({@code security-query-api.md} § login-history «테넌트»). Missing → 400
     * VALIDATION_ERROR (MissingServletRequestParameterException, inherited handler).
     */
    @GetMapping("/login-history")
    public ResponseEntity<?> getLoginHistory(
            @RequestParam String tenantId,
            @RequestParam String accountId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String outcome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > 100) {
            size = 100;
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<LoginHistoryView> result = queryService.findLoginHistory(tenantId, accountId, from, to, outcome, pageable);

        return ResponseEntity.ok(PageResponse.of(result));
    }
}
