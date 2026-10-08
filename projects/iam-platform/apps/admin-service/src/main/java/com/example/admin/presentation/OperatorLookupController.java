package com.example.admin.presentation;

import com.example.admin.application.OperatorEmailLookupUseCase;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * TASK-MONO-777 — {@code GET /api/admin/operators/lookup?email=&tenantId=}
 * (admin-api.md § GET /api/admin/operators/lookup · rbac.md § 권한 키 없이 테넌트 게이트만 거는 읽기).
 *
 * <p>🔴 Deliberately carries NO {@code @RequiresPermission} (owner decision: no permission key,
 * no matrix change). It is a GET, so the deny-default guardrail — which covers mutations only —
 * does not apply ({@code RequiresPermissionAspect#denyUnannotatedMutation}). Its own controller
 * rather than a branch of {@code GET /api/admin/operators}: that list is declared
 * {@code @RequiresPermission(operator.manage)}, and a permission-free branch there would mean
 * replacing the declaration with a manual check — a permission-mapping change.
 */
@RestController
@RequestMapping("/api/admin/operators")
@RequiredArgsConstructor
public class OperatorLookupController {

    private final OperatorEmailLookupUseCase lookupUseCase;

    @GetMapping("/lookup")
    public ResponseEntity<OperatorLookupResponse> lookup(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String tenantId) {
        List<AdminOperatorPort.OperatorLookupView> rows =
                lookupUseCase.lookup(OperatorContextHolder.require(), tenantId, email);
        List<OperatorLookupResponse.Item> items = new ArrayList<>(rows.size());
        for (AdminOperatorPort.OperatorLookupView r : rows) {
            items.add(new OperatorLookupResponse.Item(r.oidcSubject(), r.displayName(), r.tenantId()));
        }
        return ResponseEntity.ok(new OperatorLookupResponse(items));
    }

    /** Wire shape — {@code {"content":[{"accountId","displayName","tenantId"}]}}. */
    public record OperatorLookupResponse(List<Item> content) {
        public record Item(String accountId, String displayName, String tenantId) {}
    }
}
