package com.example.admin.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * TASK-MONO-771 S5 — the two reads behind the entry-policy pre-check («이 테넌트 운영자 중 2단계 미등록 N명»,
 * admin-api.md § Tenant Entry Policy › enrolment-summary). Off the hot path: never called by the token exchange
 * or the assume gate.
 */
public interface SecondFactorEnrolmentPort {

    /**
     * The {@code oidc_subject} (= IAM account id) of every ACTIVE operator of {@code tenantId} — home tenant OR an
     * assignment row (the {@code GET /api/admin/operators?tenantId=} membership). An element is {@code null} for
     * an operator with no account link. Platform ({@code '*'}) operators and partnership participants are not
     * members of the tenant and are not listed.
     */
    List<String> activeOperatorAccountIdsOf(String tenantId);

    /**
     * Of {@code accountIds}, the ones with a CONFIRMED account second factor at auth-service
     * ({@code POST /internal/auth/second-factor/enrolment-status}, admin-to-auth.md). The adapter chunks to the
     * provider's per-call cap. Fail-closed: any failure propagates (→ 503) — never «nobody enrolled».
     */
    Set<String> enrolledAmong(Collection<String> accountIds);
}
