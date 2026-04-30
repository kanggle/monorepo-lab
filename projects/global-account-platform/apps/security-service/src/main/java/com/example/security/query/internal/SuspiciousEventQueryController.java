package com.example.security.query.internal;

import com.example.security.query.SecurityQueryService;
import com.example.security.query.dto.SuspiciousEventView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only suspicious events query endpoint. admin-service only.
 * Response PII-safe: evidence is rule-supplied (no raw IP/email) and we do not
 * echo the trigger IP here.
 */
@RestController
@RequestMapping("/internal/security")
@RequiredArgsConstructor
public class SuspiciousEventQueryController {

    private final SecurityQueryService queryService;

    @GetMapping("/suspicious-events")
    public ResponseEntity<?> getSuspiciousEvents(
            @RequestParam String accountId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > 100) {
            size = 100;
        }
        if (size < 1) {
            size = 1;
        }

        Instant fromInstant = from == null ? Instant.EPOCH : from;
        Instant toInstant = to == null ? Instant.now().plusSeconds(60) : to;

        Pageable pageable = PageRequest.of(page, size);
        Page<SuspiciousEventView> result = queryService.findSuspiciousEvents(
                accountId, fromInstant, toInstant, ruleCode, pageable);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", result.getContent());
        response.put("page", result.getNumber());
        response.put("size", result.getSize());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());

        return ResponseEntity.ok(response);
    }
}
