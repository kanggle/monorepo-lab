# Task ID

TASK-ERP-BE-017

# Title

**approval-service per-request delegation scoping — `DelegationGrant.scope` (GLOBAL | REQUEST), v2.2-deferred sub-part #1.** Today a `DelegationGrant` is **blanket A→D**: the delegate may act for the delegator at ANY stage where A is approver. This increment adds a `scope` dimension so a grant can be **narrowed to ONE specific approval request** (`scope = REQUEST`, `scopeRequestId = <approvalRequestId>`) — the delegate then authorizes ONLY that one request, not A's whole queue. `scope = GLOBAL` (default) preserves today's blanket behavior **byte-for-byte**. The complex piece is the **transition-time resolution**: `DelegationResolver.resolve` gains the `approvalRequestId` and a request-scoped grant authorizes a transition only when its `scopeRequestId` matches the request being acted on. Producer leg only — the `delegated` event payload carries `scope`/`scopeRequestId` (additive, NON_NULL) for a later **read-model projection (BE-018)** + **console card (PC-FE-056)** to project; those are out of scope here. **V4 migration extends the DB CHECK allow-list** (§16 — `@Enumerated(STRING)` length fitting ≠ migration-free; the new value set + the scope↔scopeRequestId coherence are pinned by `ck_*` CHECKs).

# Status

done

# Owner

backend-engineer (dispatched, model=opus — delegation resolution change + cross-field invariant; dispatcher independently re-verifies)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api
- event
- test

---

# Dependency Markers

- **builds on**: TASK-ERP-BE-013 (대결/위임 — `DelegationGrant` aggregate, `DelegationResolver`, `erp.approval.delegated.v1`, `actingForApproverId` on transition payloads). This task **refines** that aggregate (adds scope) — the blanket A→D path is the `scope = GLOBAL` special case (byte-unchanged behavior).
- **realises**: architecture.md § v2 deferred "**v2.2-deferred sub-parts**: per-request/per-route delegation" (approval-service/architecture.md L972) + approval-api.md/erp-approval-events.md v2.1/v2.2 "v2.2-deferred: per-request/per-route delegation". This is the **per-request** half; **per-route** (delegation scoped to a route template) stays deferred (needs a first-class route-template identity — § Out of Scope).
- **forward interface (NOT consumed here)**: the `scope`/`scopeRequestId` fields added to the `erp.approval.delegated.v1` payload are a **producer-only forward interface** (mirrors how BE-013 added the `delegated` topic before BE-015 consumed it). **TASK-ERP-BE-018** (read-model `delegation_fact_proj` scope projection) + **TASK-PC-FE-056** (console card scope display) consume them later. read-model/notification ignore the new fields until then (unknown-field tolerant) → no regression.
- **decision (user, 2026-06-06)**: 다음 작업 = per-request 위임 scoping (producer leg first; read-model + console follow).
- [[feedback_spring_boot_diagnostic_patterns]] §16 (DB CHECK on STRING enum) + §14 (Testcontainers IT authoritative vs Docker-free `:check` slice).

# Goal

Let a delegator (or operator) issue a delegation that authorizes the delegate for **exactly one approval request** rather than their entire approver queue, while keeping the existing blanket grant as the default. The authorization decision at approve/reject time must honor the scope: a `REQUEST`-scoped grant is inert for every request except its `scopeRequestId`.

# Scope

## In Scope (approval-service only)

- **`DelegationScope` enum** (new, `domain/delegation`): `GLOBAL`, `REQUEST`.
- **`DelegationGrant`** aggregate:
  - New fields `scope` (`@Enumerated(STRING)`, `length = 16`, NOT NULL) + `scopeRequestId` (`length = 64`, nullable).
  - `create(...)` gains `scope` + `scopeRequestId` params. **Cross-field invariant** (→ `DelegationInvalidException`, 422): `scope = REQUEST` ⟺ `scopeRequestId` non-blank; `scope = GLOBAL` ⟺ `scopeRequestId` null/blank. Existing self-delegation + inverted-window invariants unchanged. A `null` scope arg defaults to `GLOBAL` (back-compat for any internal caller) — but the public create path always supplies a resolved scope.
  - New domain method `coversRequest(String approvalRequestId)`: `scope == GLOBAL || (scope == REQUEST && scopeRequestId.equals(approvalRequestId))`. Pure; the single place the scope semantics live.
  - `isActiveAt` / `revoke` unchanged.
- **`DelegationResolver.resolve`** gains `approvalRequestId`: an active grant authorizes the transition only when **both** `isActiveAt(now)` **and** `coversRequest(approvalRequestId)`. A `REQUEST`-scoped grant for a different request → `notAuthorized()` (fail-closed). Direct (actor == stageApprover) path unchanged.
- **`DelegationGrantRepository.findActiveGrant`** gains `approvalRequestId`: the query matches an ACTIVE grant A→D whose window contains `now` **and** (`scope='GLOBAL'` OR (`scope='REQUEST'` AND `scope_request_id = :approvalRequestId`)). When both a GLOBAL and a matching REQUEST grant exist, either authorizes equally — return one (ORDER BY scope so `GLOBAL` is deterministic; `setMaxResults(1)`). The in-domain `coversRequest` filter in the resolver is the authoritative re-check (defense-in-depth; the query is an optimization).
- **`ApprovalApplicationService.resolveActingApprover`** passes `request.getId()` into `delegationResolver.resolve(...)`. SoD (delegate ≠ submitter) + finalized/DRAFT guards unchanged.
- **`Commands.CreateDelegationCommand`** + **`DelegationRequests.CreateDelegationRequest`** + **`DelegationController`**: add `scope` (nullable string; null/blank → `GLOBAL`) + `scopeRequestId` (nullable, `@Size(max=64)`). Unknown scope string → 400 `VALIDATION_ERROR` (parse in controller/command; an unparseable enum is a client error, not 422). The domain factory raises 422 `DELEGATION_INVALID` for the coherence violation.
- **`DelegationGrantView`**: add `scope` (always present) + `scopeRequestId` (NON_NULL — ABSENT when GLOBAL).
- **`ApprovalEventPublisher.publishDelegated`**: payload gains `scope` (always) + `scopeRequestId` (only when `REQUEST`, NON_NULL absent otherwise). **`publishRevoked` is byte-unchanged** (revoke does not restate scope — the read model keeps what `delegated` projected; sticky like the validity window).
- **V4 migration** `V4__delegation_scope.sql` (approval-service):
  - `ALTER TABLE delegation_grant ADD COLUMN scope VARCHAR(16) NOT NULL DEFAULT 'GLOBAL'` (existing rows backfill to GLOBAL = today's behavior) `ADD COLUMN scope_request_id VARCHAR(64) NULL`.
  - `ADD CONSTRAINT ck_delegation_grant_scope CHECK (scope IN ('GLOBAL','REQUEST'))`.
  - `ADD CONSTRAINT ck_delegation_grant_scope_req CHECK ((scope = 'GLOBAL' AND scope_request_id IS NULL) OR (scope = 'REQUEST' AND scope_request_id IS NOT NULL))`.
  - **§16**: the enum VARCHAR fits, but the value set + scope↔scopeRequestId coherence are DB-enforced. Docker-free `:check` slice does not exercise CHECK → Testcontainers IT is the authoritative gate (§14).
- **contracts/specs**: approval-api.md (v2.3 amendment — create `scope?`/`scopeRequestId?` + view fields + resolution honors scope) + erp-approval-events.md (v2.3 amendment — `scope`/`scopeRequestId` on `delegated` payload, producer-only forward) + architecture.md (§ v2.3 amendment + § v2 deferred: per-request REALISED, per-route still deferred).

## Out of Scope

- **read-model projection** of scope (`delegation_fact_proj.scope`) → **TASK-ERP-BE-018** (consumes the new payload fields).
- **console card** scope display → **TASK-PC-FE-056**.
- **per-route delegation** (grant scoped to a route template, not a single request) — needs a first-class route-template identity that does not yet exist; stays v2.2-deferred (record the gap, do not invent a route id).
- auto-absence detection / transitive chaining (other v2.2 sub-parts).
- the four transition consumers + notification-service + read-model behavior (all unknown-field tolerant → byte-unchanged).
- revoke payload changes (scope not restated on revoke).

# Acceptance Criteria

- [ ] **AC-1** A `REQUEST`-scoped grant A→D (`scopeRequestId = R1`) authorizes the delegate to approve/reject **R1** but NOT a different request **R2** of the same approver A (→ 403 `APPROVAL_NOT_AUTHORIZED_APPROVER`).
- [ ] **AC-2** A `GLOBAL` grant A→D (default) authorizes the delegate on **every** request where A is the current stage approver — **byte-identical** to BE-013 behavior (existing delegation tests pass unchanged).
- [ ] **AC-3** `POST /delegations` accepts `scope` (`GLOBAL`|`REQUEST`, default GLOBAL) + `scopeRequestId`. `scope=REQUEST` with blank/absent `scopeRequestId`, or `scope=GLOBAL` with a non-blank `scopeRequestId` → 422 `DELEGATION_INVALID`. Unknown `scope` string → 400 `VALIDATION_ERROR`.
- [ ] **AC-4** `DelegationGrantView` (create + list responses) carries `scope`; `scopeRequestId` is ABSENT when GLOBAL, present when REQUEST.
- [ ] **AC-5** `erp.approval.delegated.v1` payload carries `scope` (always) + `scopeRequestId` (REQUEST only, NON_NULL). The four transition topics + `erp.approval.delegation.revoked.v1` are byte-unchanged.
- [ ] **AC-6** SoD preserved: a request-scoped delegate who is the request's submitter is still refused (self-approval-via-delegation). `withdraw` stays submitter-only.
- [ ] **AC-7** V4 migration extends `delegation_grant` with `scope`/`scope_request_id` + the two CHECK constraints; existing rows are GLOBAL. `:approval-service:check` GREEN (unit/slice). Testcontainers IT (CI Linux) proves the request-scoped authorization + the CHECK coherence (insert with scope=REQUEST & null request_id rejected). H2 forbidden.

# Related Specs

- `specs/services/approval-service/architecture.md` (§ v2.1 amendment 대결/위임 + § v2 deferred — this realises the per-request half) (v2.3 amendment).
- ADR-MONO-016 § D3 (approval forward-declaration). erp.md E3/E4/E6 (authorized approver + no-self-approval + idempotent transition + fail-closed authz) + transactional T5 (optimistic lock) + audit-heavy A7/A10.

# Related Contracts

- `specs/contracts/http/approval-api.md` (v2.3 — create `scope?`/`scopeRequestId?`, view fields, resolution honors scope).
- `specs/contracts/events/erp-approval-events.md` (v2.3 — `scope`/`scopeRequestId` on `delegated` payload; producer-only forward; transition + revoke topics byte-unchanged).

# Edge Cases

- `scope=REQUEST`, `scopeRequestId` blank → 422 `DELEGATION_INVALID` (coherence). `scope=GLOBAL`, `scopeRequestId` present → 422.
- Both a GLOBAL grant and a matching REQUEST grant active for the same A→D → authorized (either covers); resolver returns delegated(A) once.
- REQUEST grant for R1, delegate acts on R2 (same approver A) → fail-closed `APPROVAL_NOT_AUTHORIZED_APPROVER`.
- REQUEST grant's `scopeRequestId` references a request that does not exist / belongs to a different approver → the grant is simply inert (no cross-aggregate validation at create time; the resolver only ever matches it against the actual request being acted on). Recorded: create does NOT validate that scopeRequestId is a real request of A (avoids a cross-aggregate read on the create path; a stale id just never authorizes).
- Existing rows after migration: scope=GLOBAL → unchanged authorization.
- `scope` enum value not in CHECK allow-list (e.g. a future value inserted without migration) → DB rejects (§16; IT-caught).
- Unknown `scope` string in request body → 400 (client error), not 422.

# Failure Scenarios

- Resolver omits the `coversRequest` re-check and trusts only the query → a REQUEST grant could leak to other requests. Guard: resolver applies `coversRequest(approvalRequestId)` as the authoritative in-domain filter (defense-in-depth over the SQL predicate). Unit test asserts R2 is refused.
- Migration ships the column without the coherence CHECK → a REQUEST row with null request_id (or GLOBAL with a request_id) becomes possible → resolver `coversRequest` NPE / mis-auth. Guard: `ck_delegation_grant_scope_req` + IT insert-rejection test (§16).
- Back-compat break: changing the `create` signature breaks existing GLOBAL callers/tests. Guard: AC-2 — GLOBAL is the default and existing delegation unit/IT pass unchanged (null/GLOBAL scope ⇒ today's behavior).

# Test Requirements

- **unit**: `DelegationGrantTest` (scope invariants: REQUEST needs requestId, GLOBAL forbids it; `coversRequest` GLOBAL-any / REQUEST-match / REQUEST-mismatch). `DelegationResolverTest` (REQUEST grant authorizes matching request only; GLOBAL authorizes any; mismatch → notAuthorized). `DelegationApplicationServiceTest` (create REQUEST grant happy + coherence-violation 422). controller/DTO mapping (scope parse, unknown → 400).
- **IT** (`DelegationIntegrationTest`, Testcontainers, CI Linux authoritative — §14): (a) REQUEST-scoped grant lets delegate approve R1 but R2 → 403; (b) GLOBAL grant unchanged; (c) CHECK coherence — direct insert (or repo save bypassing the factory) of scope=REQUEST/null request_id rejected by the DB. H2 forbidden.
- **event**: assert `delegated` payload carries `scope` (+ `scopeRequestId` for REQUEST, absent for GLOBAL) — extend the existing delegated-event assertion.
- existing approval/delegation unit + IT pass unchanged (AC-2/AC-6 regression).
- `:approval-service:check` GREEN (unit/slice). `:approval-service:integrationTest` GREEN on CI Linux.

# Definition of Done

- [ ] `DelegationScope` + `DelegationGrant.scope`/`scopeRequestId`/`coversRequest` + create invariant.
- [ ] `DelegationResolver.resolve(approvalRequestId)` + `findActiveGrant(approvalRequestId)` + call-site threading.
- [ ] Command/DTO/controller/view scope fields + `publishDelegated` payload scope.
- [ ] V4 migration (2 CHECK constraints; backfill GLOBAL).
- [ ] GLOBAL byte-unchanged (existing tests pass); REQUEST authorizes one request only.
- [ ] spec/contract v2.3 amendments.
- [ ] `:check` GREEN; Testcontainers IT GREEN (request-scoped authz + CHECK coherence).
- [ ] Task md + INDEX updated.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Opus (delegation 도메인 resolution 변경 + cross-field 불변식 + transition authz 경로 — 단순 mirror 아님; 디스패처 독립 재검증). 사용자 "per-request 위임 scoping" 선택. 메타: ① **blanket A→D 의 정제** — `scope=GLOBAL` 이 기존 동작의 special case (byte-unchanged), `scope=REQUEST` 가 한 건 한정. ② resolution 이 핵심 리스크 — `coversRequest` in-domain 재검증이 SQL predicate 위 defense-in-depth (R2 누수 방지). ③ **V4 CHECK 선반영** (§16: enum STRING 길이 OK ≠ 마이그레이션 불필요 — 값 집합 + scope↔scopeRequestId coherence 를 `ck_*` 로 고정; ERP-BE-014 CI-RED 재발 방지). ④ producer-only forward — read-model(BE-018) + console(PC-FE-056) 후속, per-route 는 route-template identity 부재로 계속 deferred. [[project_monorepo_template_strategy]] [[feedback_spring_boot_diagnostic_patterns]] [[project_platform_console_adr_013]]
