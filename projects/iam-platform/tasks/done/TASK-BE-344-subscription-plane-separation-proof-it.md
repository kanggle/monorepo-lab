# Task ID

TASK-BE-344

# Title

ADR-MONO-023 § 3.3 step 3 — plane-separation proof IT (entitlement plane). An account-service `@SpringBootTest` Testcontainers IT proving, end-to-end through the real mutation + read use-cases against real MySQL, that an entitlement status change is reflected in BOTH downstream read paths (catalog + `entitled_domains`) and is fully reversible — the entitlement-plane half of ADR-023 D2 (GCP billing↔IAM parity). IAM-plane preservation is architecturally guaranteed (account-service has no admin_db access), documented in the IT; the cross-service operator-token re-issuance fidelity check is deferred to the federation-e2e stack.

# Status

done

> **완료 (2026-06-10)**: impl PR #1246 (squash `c5c202bf9ad3f3e57c7fea14c8318e1c2809ae85`). ADR-MONO-023 § 3.3 step 3 — 평면분리 증명 IT(entitlement plane). account-service `@SpringBootTest` Testcontainers IT: subscribe(ACTIVE)→카탈로그+entitled_domains+이벤트(prev=null) / suspend→양 읽기경로 제거 but **row 보존(SUSPENDED, reversible)**+이벤트(ACTIVE→SUSPENDED) / resume→동일 row 복원(재부여 없음) / cancel→terminal 제외 / 시드 finance+wms 불영향. **IAM-plane 보존은 service/DB 경계로 구조적 보장**(account-service admin_db 미접근)—IT에 문서화; cross-service 토큰 재발급 fidelity는 federation-e2e로 deferred. test-only(메커니즘=BE-341/342/343). `integrationTest` 태스크(@Tag("integration")). 검증: 3 tests/0 fail 실MySQL8 로컬(3m23s) + CI Integration(iam) GREEN. 3차원 ✓(20 pass/0 fail, MERGED `c5c202bf`/origin tip 일치). **ADR-MONO-023 § 3.3 실행 로드맵 D1~D6 전 단계 완료(step1 BE-341 / step2a BE-342 / step2b BE-343 / step3 BE-344)**. 분석=Opus 4.8 / 구현=Opus 4.8.

# Owner

backend

# Task Tags

- backend
- account-service
- adr
- multi-tenant
- integration-test

---

# Dependency Markers

- **depends on**: TASK-BE-341 (state machine, #1240) + TASK-BE-342 (mutation + event, #1242) — the IT exercises both.
- **proves**: ADR-MONO-023 D2 plane-separation invariant (entitlement-plane half) + D1 reversibility + D4 event-per-mutation.
- **deferred (federation-e2e)**: the cross-service half — operator token issuance → suspend → token re-issuance drops the domain from `entitled_domains` while the `operator_tenant_assignment` row + RBAC stay byte-unchanged — belongs to the federation-hardening-e2e stack (both services + DBs running). The IAM-plane preservation is meanwhile guaranteed by the service/DB boundary (D2: account-service cannot write admin_db).

# Goal

Make the ADR-023 D2 invariant executable: suspend removes the domain from the catalog source AND the `entitled_domains` source while PRESERVING the subscription row (reversible); resume restores access with no re-creation; cancel is terminal. This is the verification capstone of the ADR-023 step-2 mechanism.

# Scope

- NEW `account-service/src/test/java/.../integration/SubscriptionPlaneSeparationIntegrationTest.java` (`@SpringBootTest @Testcontainers extends AbstractIntegrationTest`, runs under the `integrationTest` task):
  - `planeSeparationCycle` — subscribe (ACTIVE) → in catalog + entitled_domains + event(prev=null); suspend → dropped from both, row PRESERVED as SUSPENDED + event(ACTIVE→SUSPENDED); resume → restored (same row); cancel → excluded, terminal row retained.
  - `seededActiveDomainsUnaffected` — suspending `scm` leaves the tenant's seeded `finance`+`wms` entitlements intact.
  - `catalogCarriesCustomerRows` — the catalog source carries the customer-id row (ADR-019 D4 shape).
- Test-only. No production change (the mechanism is BE-341/342/343; this is the proof).

# Acceptance Criteria

- **AC-1** The cycle IT proves both read paths (catalog `listActive()` + `entitled_domains` reverse-lookup `listActive(null, tenantId)`) follow the subscription status.
- **AC-2** After suspend, the subscription ROW is preserved (status SUSPENDED, not deleted) — reversibility; resume restores via the same row (no re-create).
- **AC-3** Each mutation writes a `tenant.subscription.changed` outbox event with correct previous/current status (aggregate `TenantDomainSubscription`).
- **AC-4** The IT documents that IAM-plane preservation is guaranteed by the service/DB boundary (D2), not asserted in-process.
- **AC-5** GREEN under `:account-service:integrationTest` against real MySQL (local + CI "Integration (iam, Testcontainers)").

# Related Specs

- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` § D2 / D6 step 3
- `specs/contracts/http/internal/account-tenant-domain-subscriptions.md`
- `specs/contracts/events/account-events.md` (`tenant.subscription.changed`)

# Related Contracts

- `specs/contracts/events/account-events.md`

# Edge Cases

- `@Tag("integration")` (via `AbstractIntegrationTest`) — runs under the dedicated `integrationTest` task, NOT `:test`; skipped when Docker is unavailable.
- Uses `acme-corp` + the free `scm` domain (acme-corp is V0020-seeded ACTIVE for finance+wms only) so the cycle does not collide with the seed; `@BeforeEach` cleans outbox + the `(acme-corp, scm)` row for re-runnability.

# Failure Scenarios

- If a suspend DELETED the row instead of flipping status → reversibility lost; AC-2 fails.
- If `listActive` returned non-ACTIVE rows → catalog/entitled_domains would leak suspended entitlements; AC-1 fails.
