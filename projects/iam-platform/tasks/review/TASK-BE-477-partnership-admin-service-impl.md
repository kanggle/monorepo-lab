# TASK-BE-477 — cross-org partnership: admin-service implementation (aggregate + invite/accept/terminate + evaluator cross-org branch + cascade-revoke + cross-org-leak IT)

**Status:** review
**Area:** iam-platform / admin-service · **Scope:** `apps/admin-service/**` (code + Flyway + IT)
**Type:** cross-org privilege-origination implementation — isolation/escalation-critical (ADR-MONO-045 §3.4 step 2)
**Implemented:** branch `be-477-partnership-impl` (backend-engineer Opus dispatch, dispatcher-reverified). ~20 new + 9 extended files. **crux 독립 재검증**: (1) `AdminGrantScopeEvaluator.effectiveAdminScope` **byte-unchanged**(변경셋 미포함) → cross-org actor admin scope 공집합 → `/api/admin/**` 403 (IT AC-4b); (2) cross-org 분기는 `OperatorAssignmentCheckUseCase` 에만 — `delegated ∩ participant ∩ host-holds` 삼중교집합·ACTIVE-only·fail-closed·additive `delegatedScope`(NON_NULL, 정상 assignment 경로 byte-unchanged); (3) `ScopeSet.containsAdminRole()` invite-time cap 강제(422); (4) `topicFor` 7 `partnership.*` 케이스 전부; (5) V0039/V0040 net-zero·idempotent, JSON 컬럼 shape 매치. **검증 실행**: `:admin-service:compileJava/compileTestJava` clean + Docker-free 파트너십 unit/slice `:test` green(dispatcher 재실행) + 에이전트 full `:test` BUILD SUCCESSFUL + keystone IT `PartnershipCrossOrgLeakIntegrationTest` 7 AC green(로컬 non-flaked 1회; **CI Linux Testcontainers 가 durable 권위** — Windows npipe flaky). **host-holds seam**: `HostEntitledScopeResolver` 기본=unbounded(admin-service 는 host-entitlement 로컬 미러 없음·hot-path cross-service 금지, ADR-020 §3.1) → 요청시 `∩ host-holds` 는 no-op, **step 2b(auth-service) 로 이관**(보안핵심 no-admin-role cap 은 invite-time 무조건 강제).
**Depends on:** TASK-BE-476 (specs/contracts — the SoT this implements; merged `cbdf91e3a`), ADR-MONO-045 (ACCEPTED), ADR-MONO-024 impl (BE-345/346/347 — `AdminGrantScopeEvaluator`/`TenantScopeGuard`/grant-menu the cross-org branch relates to), ADR-MONO-020 impl (BE-327/338 — `/internal/operator-assignments/check` effective-scope the partnership-derived reach feeds)
**Analysis model:** Opus 4.8 · **Impl model:** Opus (cross-org isolation/escalation; the highest-scrutiny IAM change since ADR-024)

## Goal

Implement the cross-org partnership model exactly as TASK-BE-476 specced it: the `tenant_partnership` aggregate + `tenant_partnership_participant`, the two-sided invite/accept lifecycle, the `delegated_scope` attenuation cap, the cross-org confinement (derived domain-operating authority only — **admin scope never widened**), and D6 relationship-scoped cascade-revoke. Prove end-to-end with a Testcontainers cross-org-leak IT: A invites → B accepts → a B-operator assumes into A within the slice → B offboards them / A terminates → access gone; a B-operator can NEVER exceed `delegated_scope`, reach A's admin plane, or reach a third tenant.

## 핵심 구현 불변식 (must not drift from BE-476 spec)

1. **Partnership widens domain-operating reach, NEVER admin scope.** `effectiveAdminScope(operator, permission)` (the `AdminGrantScopeEvaluator` D2 path) stays **byte-unchanged** — it reads only `admin_operator_roles`, never partnerships. A cross-org actor (B-operator) attempting `/api/admin/**` in A has an EMPTY admin scope → 403. The cross-org branch lives in the **assume-tenant effective-scope** path (`OperatorAssignmentCheckUseCase` / `TenantScopeResolver`), NOT in the admin-grant evaluator.
2. **Triple-intersection cap.** A B-operator's derived reach into host A = `delegated_scope ∩ participant_scope ∩ host-entitled-scope`, computed request-time from the DB. Never carries an admin role (structurally excluded from `delegated_scope` by the invite-time cap + data-model invariant).
3. **M1 single-tenant token preserved.** The assumed token remains a single-`tenant_id` (host) token; only `entitled_domains`/roles are capped by the intersection. No cross-org multi-tenant token.
4. **Cascade-revoke = derivation loss.** SUSPENDED/TERMINATED partnership or participant removal → the `findActive`/participant lookup returns null → next assume-tenant issuance derives 0. No per-operator sweep; bounded by the perm-cache TTL. One-shot `partnership.terminated` event.
5. **No transitive re-delegation.** A participant cannot re-originate; default deny (structurally — participant is a B-owned operator, not an origination point).
6. **Two-sided consent gate.** Partnership-management endpoints gated by `partnership.manage` + D2 `TenantScopeGuard` with target = acting-side tenant (invite/host-terminate → host; accept/participant/partner-terminate → partner).

## Scope

**IN:**

- **Flyway** — `V0039__create_partnership_tables.sql` (`tenant_partnership` + `tenant_partnership_participant`, JSON `delegated_scope`/`participant_scope`, `uk_tenant_partnership_pair`, FK CASCADE, idempotent `INFORMATION_SCHEMA` guard per V0027/V0029, forward-only, no `@var`); `V0040__seed_partnership_manage_permission.sql` (`partnership.manage` key + `TENANT_ADMIN` mapping, `INSERT … ON CONFLICT/IGNORE`, inert/net-zero). Verify no `db/migration-dev` version collision.
- **JPA** — `TenantPartnershipJpaEntity` + `TenantPartnershipParticipantJpaEntity` (mirror `operator_tenant_assignment` `@JdbcTypeCode(SqlTypes.JSON)` for scope columns) + repositories (`findActive(host, partner)`, `findByOperatorParticipations(operatorId)`, pair-lookup, list by host/partner).
- **Domain** — a `DelegatedScope`/`ScopeSet` value object (`{domains, roles}`) with an `intersect(...)` op + `containsNoAdminRole()` cap validation; partnership status state-machine guard (PENDING→ACTIVE→SUSPENDED/TERMINATED transitions per the spec matrix).
- **Use-cases** (`@Service`, `@Transactional`, audited) — `InvitePartnershipUseCase`, `AcceptPartnershipUseCase`, `TransitionPartnershipUseCase` (suspend/reactivate/terminate), `AddParticipantUseCase`, `RemoveParticipantUseCase`, `ListPartnershipsUseCase`. Each: `partnership.manage` gate + D2 `TenantScopeGuard`(acting-side target) + reason-gated + audit row + outbox event. Invite validates `delegated_scope` cap (≤-own across org, no admin role) → `422 PARTNERSHIP_SCOPE_INVALID`. Participant validates home-tenant == partner + `participant_scope ⊆ delegated_scope`.
- **Controller + DTOs** — `PartnershipController` (`POST /api/admin/partnerships`, `POST .../{id}:accept|:suspend|:reactivate|:terminate`, `GET /api/admin/partnerships`, `POST|DELETE .../{id}/participants/{operatorId}`) + request/response records, per the admin-api.md contract.
- **Permission + action codes** — add `partnership.manage` to `Permission` constants; add `PARTNERSHIP_INVITE/ACCEPT/SUSPEND/REACTIVATE/TERMINATE/PARTICIPANT_ADD/PARTICIPANT_REMOVE` to the action-code enum.
- **Cross-org confinement branch (admin-service read-side)** — extend `OperatorAssignmentCheckUseCase` (and/or `TenantScopeResolver`) so an operator's effective reachable-tenant set additively includes host tenants of ACTIVE partnerships where the operator is a participant, and the returned scope for such a host is the triple-intersection `delegated_scope ∩ participant_scope ∩ host-holds`. `effectiveAdminScope` (D2, `AdminGrantScopeEvaluator`) is **NOT touched** — a cross-org actor has EMPTY admin scope. `/internal/operator-assignments/check` returns the partnership-derived assignment **plus a new additive `delegatedScope` block** (domains + roles) for partnership-derived host tenants (the authoritative confinement output auth-service will consume in step 2b). Contract extension = additive (existing `{assigned, orgScope}` unchanged for non-partnership assignments).
- **Outbox** — `PartnershipEventPublisher` (self-built 7-field envelope, mirror `TenantEventPublisher`) + v2 adapter → `admin_outbox`; add `partnership.*` to `topicFor` (bare iam topics, reject-unmapped). `partnership.terminated` = one-shot.
- **Tests** — Testcontainers **cross-org-leak IT** (the D7 proof): full lifecycle + the three leak assertions (exceed-scope / reach-admin / reach-third-tenant all denied) + termination/participant-removal deny-at-next-request + SUPER_ADMIN/existing assume-tenant net-zero. Plus use-case/slice unit tests (state-machine guard, cap validation, intersection math, two-sided consent gate).

**OUT (deferred):**

- **step 2b (auth-service, dependent follow-up task)** — `TenantClaimTokenCustomizer.customizeForAssumeTenant` applies the delegated **domains/roles cap** (intersect `entitled_domains` with `delegatedScope.domains`; cap effective roles to `delegatedScope.roles`) when the `/internal/operator-assignments/check` response carries the new `delegatedScope` block; federation assume-tenant IT. Split out per the ADR-020 precedent (admin check = BE-327, auth consumer = BE-338 were separate tasks). **Safe to split**: net-zero (no partnerships exist until created; the feature is dormant), and the admin-service `check` output is the authoritative confinement — this task's IT proves it at that boundary. No live partnership is usable until 2b lands, so no latent leak ships.
- The partner-console UI (step 3, thin surface).
- N-way consortia, `SUPER_ADMIN` broker gate, partner billing, ABAC per-resource cross-org data scoping, invite rate-limiting (step 4).
- Any change to `SUPER_ADMIN`, ADR-024 within-tenant confinement, or the M1-M7 isolation model (all reused verbatim).

## Acceptance Criteria

- [ ] **AC-1** Flyway V0039/V0040 apply on a real MySQL (Testcontainers) under `ddl-auto=validate`; entities validate; net-zero seed (no operator newly granted).
- [ ] **AC-2** Invite (host `TENANT_ADMIN`) creates a PENDING partnership; accept (partner `TENANT_ADMIN`) → ACTIVE; suspend/reactivate/terminate transitions honor the spec state matrix; wrong-side or wrong-state → `403 PARTNERSHIP_SCOPE_DENIED` / `409 PARTNERSHIP_TRANSITION_INVALID`.
- [ ] **AC-3** `delegated_scope` cap enforced at invite: admin role or ≤-own violation → `422 PARTNERSHIP_SCOPE_INVALID`. Participant home-tenant ≠ partner → `422 PARTICIPANT_NOT_OWN_OPERATOR`; `participant_scope ⊄ delegated_scope` → `422 PARTICIPANT_SCOPE_EXCEEDS_DELEGATION`.
- [ ] **AC-4** **Cross-org-leak IT (the keystone, at the admin-service confinement boundary)**: A invites → B accepts → `GET /internal/operator-assignments/check(B-operator, hostA)` returns `assigned=true` + `delegatedScope` = exactly `delegated_scope ∩ participant_scope ∩ host-holds` (NOT the host's full scope); the SAME B-operator (a) never gets a domain/role outside that intersection, (b) has EMPTY `effectiveAdminScope` in A → 403 `TENANT_SCOPE_DENIED` on an `/api/admin/**` admin mutation targeting A, (c) `check(B-operator, thirdTenantC)` → `assigned=false`. All fail-closed, audited where applicable.
- [ ] **AC-5** **Cascade-revoke IT**: after `:terminate` (or `:suspend`, or participant `DELETE`), `check(B-operator, hostA)` → `assigned=false` at the next request (derivation gone, no per-operator sweep); one-shot `partnership.terminated` event emitted to `admin_outbox`.
- [ ] **AC-6** **Net-zero IT**: with no partnerships, `/internal/operator-assignments/check` + assume-tenant issuance are byte-identical to pre-change; `effectiveAdminScope`/`SUPER_ADMIN` unaffected.
- [ ] **AC-7** `partnership.*` events emitted via outbox (partitionKey=partnershipId), each audited with the matching `PARTNERSHIP_*` action code.
- [ ] **AC-8** `./gradlew :admin-service:check` green locally (Docker-free slice/unit); **Integration (iam, Testcontainers)** green in CI (the wiring authority — Docker-free `:check` misses cross-org effective-scope wiring). 0 failing required at merge.

## Related Specs / Contracts

- `specs/services/admin-service/data-model.md` (tenant_partnership + participant), `rbac.md` (partnership.manage + Cross-Org Partner Delegation Confinement — the branch this implements), `specs/contracts/http/admin-api.md` § Partnership Management, `specs/contracts/events/partnership-events.md`, `specs/contracts/http/internal/auth-to-admin.md` (`/internal/operator-assignments/check` — the extension point).
- ADR-MONO-045 §3.4 step 2; §3.1 hard invariants.

## Edge Cases / Failure Scenarios

- **half-state**: PENDING partnership derives 0 (participant lookup gated on ACTIVE).
- **transitive re-delegation**: structurally impossible (participant ≠ origination); assert default-deny in IT.
- **host delegates role it lacks**: invite-time ≤-own cap rejects (422) + request-time `∩ host-entitled` double-defense.
- **duplicate (host,partner)**: `409 PARTNERSHIP_ALREADY_EXISTS` (unique index).
- **partner offboards own employee**: participant DELETE → derivation gone at next request (no A-side action) — the offboarding-defect fix, asserted in IT.
- **wiring gotcha**: Docker-free `:check` (slice only) will NOT exercise the assume-tenant effective-scope cross-org wiring — the Testcontainers IT (real MySQL + MockMvc/real customizer) is the authority (per repo memory). AC-8 requires the CI IT lane green.
