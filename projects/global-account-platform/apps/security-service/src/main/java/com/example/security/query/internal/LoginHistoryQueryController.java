package com.example.security.query.internal;

import com.example.security.query.SecurityQueryService;
import com.example.security.query.dto.LoginHistoryView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/internal/security")
@RequiredArgsConstructor
public class LoginHistoryQueryController {

    private final SecurityQueryService queryService;

    @GetMapping("/login-history")
    public ResponseEntity<?> getLoginHistory(
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
        Page<LoginHistoryView> result = queryService.findLoginHistory(accountId, from, to, outcome, pageable);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", result.getContent());
        response.put("page", result.getNumber());
        response.put("size", result.getSize());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());

        return ResponseEntity.ok(response);
    }
}
