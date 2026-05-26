# Tasks Index — scm-platform

This document defines task lifecycle, naming, and move rules for the **scm-platform** project. Repo-root [tasks/INDEX.md](../../../tasks/INDEX.md) covers monorepo-level (cross-project) tasks; this file covers scm-platform-internal tasks only.

---

# Lifecycle

backlog → ready → in-progress → review → done → archive

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-SCM-BE-XXX`: backend (Spring Boot service implementations)
- `TASK-SCM-INT-XXX`: cross-service integration / E2E (Testcontainers · Docker compose)
- `TASK-SCM-FE-XXX`: frontend — declared for future use, scm v1 is backend-only

---

# Move Rules

## backlog → ready
Allowed only when:
- related specs exist (`specs/services/<service>/architecture.md`, `specs/contracts/...`)
- related contracts are identified
- acceptance criteria are clear
- task template is complete

## ready → in-progress
Allowed only when implementation starts.

## in-progress → review
Allowed only when:
- implementation is complete
- tests are added
- contract / spec updates are completed if required

## review → done
Allowed only after review approval.

### Review Rules
- Tasks in `review/` must not be re-implemented directly.
- If a review reveals a bug or missing requirement, create a new fix task in `ready/` referencing the original task.
- Fix tasks must include the original task ID in their Goal section (e.g. "Fix issue found in TASK-SCM-BE-001").
- Do not modify a task file after it moves to `review/` or `done/`.

### PR Separation Rule (lifecycle ↔ PR boundary)

Each lifecycle transition lands in its own PR. **Never bundle task spec authoring with implementation in the same PR.**

| Stage | Recommended PR shape |
|---|---|
| `(writing) → ready` | **spec PR** — adds the task file to `ready/` + updates this `INDEX.md` ready list. No implementation code. |
| `ready → in-progress → review` | **impl PR** — moves the task file through `in-progress/` to `review/` and lands the implementation. Lifecycle moves and impl commits should be separate commits but live in one PR. |
| `review → done` | **chore PR** — moves merged task file(s) from `review/` to `done/` + updates the `INDEX.md` done list. May batch multiple merged tasks. |

The repo-root [tasks/INDEX.md](../../../tasks/INDEX.md) is the authoritative definition. This summary applies the same rule at the project level.

## done → archive
Allowed when no further active change is expected.

---

# Rule

Tasks must not be implemented from `backlog/`, `in-progress/`, `review/`, `done/`, or `archive/`.

---

# Task List

## backlog

(empty)

## ready

- `TASK-SCM-BE-018-procurement-dead-port-removal.md` — `/refactor-code procurement` (2026-05-26) dry-run verify 된 TRUE DEAD port 3 파일 제거 (`IdempotencyStore` + `ClockPort` + `ClockConfig`, 사용처/inject site 0). architecture.md L149/L150 tree 2줄 제거. BE-017 § Out of Scope L44 closure. Option A scope (좁음, refactor-code 정신 부합): Exception + table + § Idempotency narrative 보존 (forward-compatible per BE-316 패턴 재사용). behavior-neutral, low risk. 분석=Opus 4.7 / 구현 권장=Sonnet 4.6.

## in-progress

(empty)

## review

(empty)

## done

- `TASK-SCM-BE-017-refactor-sweep-tail.md` — **DONE** (spec PR #843 `e1d4c213` + impl PR #844 squash `e473fa6a`; 2026-05-26 single-day spec→impl→review→done cycle, /review-task approved fix-0). post-BE-016 sweep tail 4 unit (L0 systemic 1 + L6 잔여 중복 3, behavior-neutral, 10 files / +264 / -34): **(A1)** L0 inventory-visibility `InventoryVisibilityController` → `adapter.outbound.cache.SnapshotAggregationCache` 직접 import (Hexagonal boundary 위반) → `application/port/outbound/SkuBreakdownCachePort` 신설 + `SnapshotAggregationCache` implements + X-Cache 헤더 (HIT/MISS/UNAVAILABLE) byte-identical 보존 (3 WebMvcTest case fence). **(A2)** L6 procurement `PurchaseOrderController` 의 `ActorContextResolver.currentOrThrow()` 정적 호출 6 site (presentation→infrastructure 정적 의존) → `application/security/ActorContextResolver` 로 layer 이동 (mechanical rename, static API 동일 보존, throw type=`IllegalStateException` + message byte-identical). **(A3)** L6 inventory-visibility 두 controller (`InventoryVisibilityController.java:154` + `NodeStalenessController.java:40`) 의 `private String extractTenantId(Jwt)` byte-identical 중복 → `adapter/inbound/web/TenantClaimExtractor` static util + 4 unit test (claim present/wildcard/absent/null jwt). **(A4)** L6 procurement `PurchaseOrderController.java:63` `Collectors.toList()` → `.toList()` (downstream `lines()` read-only 검증). **Independent verify**: AC grep 4/4 PASS / Local Gradle `:check --rerun-tasks` BUILD SUCCESSFUL 1m9s 28/28 (Mockito `@InjectMocks` ctor 의존성 변경 stale cache 우회) / CI ALL GREEN 19 pass + 1 skip (Observability) 0 fail incl. **Integration (scm-platform, Testcontainers) 1m54s authoritative** (실 PostgreSQL + Kafka X-Cache 헤더 + cache port wiring 검증) + **E2E (scm-platform v1 cross-service smoke) 48s** + sibling project IT/E2E pass (cross-project 회귀 0). **BE-016 회귀 0건** (8 sampling: `*RepositoryImpl` rename 9 / `ALREADY_PAST_SUBMITTED` / `recordTransition` / `pageQuery.toPageable()` / `WebhookSignatureVerifier` / `WmsEnvelopeParser` / `applySnapshotDelta` / gateway `String.join`). **`/review-task` 6/6 PASS approved**: Spec Compliance WARN (1 비차단 spec-drift) / Architecture·Code Quality·Security·Performance·Testing 모두 PASS / 4 unit byte-identical 보존 모두 PASS / Blocking 0. **비차단 관찰**: #1 `procurement-service/architecture.md:165` Layer Structure tree 가 ActorContextResolver 를 `infrastructure/security/` 에 표기 → task in-scope: out-of-scope 명시, BE-018 후보 (1줄 spec 정정) / #2 `application/security/ActorContextResolver` Spring Security `SecurityContextHolder` static import (spec-compliant: Boundary rules 가 domain/ 만 framework 금지, application/ 허용. v1 acceptable) / #3 `{@link filter.TenantClaimEnforcer}` sub-package-relative javadoc (cosmetic) / #4 `SnapshotAggregationCache.isAvailable()` Redis GET health-check (기존 동작, 본 PR 회귀 아님). **메모리 갱신**: `project_refactor_sweep_status` 의 SCM cohort = TRUE 0 도달 (GAP, PC console-bff 에 이은 3번째 service-cluster TRUE 0). `project_2026_05_25_cross_project_sweep_and_recovery` 의 17 PR 누적 → 18번째 SCM L0+L6 tail-end 종결. 분석=Opus 4.7 / 구현=Sonnet 4.6 / 리뷰=Opus 4.7.

- `TASK-SCM-BE-016-refactor-sweep.md` — **DONE** (2026-05-25, impl PR #817 squash `f62dd8b7` + close chore PR #822 batch `86702238`). 2026-05-25 cross-project 8-PR sweep cohort 일부. L1 naming (procurement 5 + inventory 4 `*RepositoryAdapter` → `*RepositoryImpl` + gateway `String.join`) + L5 long-method (`PurchaseOrderApplicationService.acknowledge()` if-chain → `ALREADY_PAST_SUBMITTED` 상수) + L6 duplication (procurement 6 method audit+history → `recordTransition` helper) + L3 webhook signature 분리 (`WebhookSignatureVerifier` infrastructure component) + L6 inventory-visibility consumer duplication (`WmsEnvelopeParser` util + `InvalidEnvelopeException` top-level) + L5+L6 `applySnapshotDelta` helper + bug fix (`search()` `PageRequest.of(...)` → `pageQuery.toPageable()` sort 보존). 분석=Opus 4.7 / 구현=Opus 4.7.

- `TASK-SCM-BE-015-platform-console-operator-read-consumer-reconciliation.md` — **DONE** (spec PR #635 `385be18c` + impl PR #636 `bfb9ef8a`; scm PR Separation Rule = spec/impl PR 분리 준수). ADR-MONO-013 § D6 Phase 4 scm-side prerequisite for `TASK-PC-FE-008`. **(B) document/accept — no scm ADR** (ADR-MONO-013 governs; scm gateway `AllowedIssuersValidator`+`TenantClaimValidator`(`tenant_id ∈ {scm,*}`)+`JwtHeaderEnrichmentFilter`(`X-Token-Type=user`) 가 **이미** GAP RS256 운영자 토큰을 계약상 수용 — "v1 backend-only / human-PKCE v2-deferred" 는 scm 자체 UI/user-flow client scope 일 뿐 API 소비자 배제 아님; competing convention 無 · reality-alignment). Records `platform-console` (Model B) as a sanctioned external **read** consumer of scm's existing read surface (procurement PO read + inventory-visibility). spec-only additive: `gateway-public-routes.md` (신규 subsection + `last_updated` 2026-05-11→2026-05-19) / `gap-integration.md` (신규 section + 3 참조) / `PROJECT.md` § GAP IdP (1 bullet). 객관검증: scm `PROJECT.md` frontmatter byte-unchanged (single-org / `multi-tenant`-미선언 보존) · `gateway-service/architecture.md` untouched · 실삭제행 = `last_updated` 1개뿐. closed via batch chore (this). 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

- `TASK-SCM-BE-014-inventory-visibility-architecture-missing-sections.md` — **REVIEWED → approved** (2026-05-16, `/review-task` single-task, review-checklist Spec/Arch/Quality/Security PASS · Perf N/A · Testing PASS via task Verification). impl `task/spec-drift-cohort-2026-05-16` (spec-only, no `apps/`). S20: `inventory-visibility-service/architecture.md` 에 누락 10 standard section 을 `## Dependencies` 뒤에 append (procurement-service 구조 parity, IVS-accurate — copy-paste 아님). **substantive**: Saga/Long-running (ADR-MONO-005 **Cat C** consumer + **Cat D** sweep, A/B 없음 명시) · Idempotency (T8 `event_dedupe`, mutating REST 없음 → no Idempotency-Key) · Trait mapping (**batch-heavy B1/B3/B5/B6 ✅ = scm 첫 구현**, T8 ✅) · Mandatory mapping (**S5 positive-primary** — IVS 가 S5 reference impl; procurement 의 S5-negative 와 대비) · Observability/Failure Modes/Testing 전부 IVS-specific. **justified N/A**: Outbox (ADR-MONO-005 Cat C best-effort 의도적 deviation, self-healing 근거) · audit_log/S7 (state machine 없음, event_dedupe provenance) · Multi-tenancy (scm = single-org, multi-tenant trait 미선언 — 단 tenant_id=scm fail-closed gate 유지). ADR-MONO-012 canonical form(Identity 표 + `### Service Type Composition` H3) 무손상 (append-only). 리뷰 재검증: 10/10 sections, canonical 보존, dead-ref 0, apps/ 0. **비차단 finding**: Trait mapping T7 cell 이 `data-model.md:58` 의 명시적 `version` optimistic-lock 컬럼을 "ordering-based / no concurrent multi-writer" 로 과소진술 — verdict ✅ 자체는 정확(오히려 강화)이라 비차단; 향후 refactor-spec 정밀화 후보 (fix task 미생성). 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

- `TASK-SCM-BE-013-procurement-events-adr-path-fix.md` — spec commit `80de0325` + impl PR #513 (squash `ff8d0b3a`) + close chore (2026-05-14, BE-283 직속 후속). **APPROVED (inline self-review)** — `/refactor-spec all --dry-run` **Tier 3 #2 closure** (SCM 의 마지막 dead-ref). 1-line mechanical fix: `projects/scm-platform/specs/contracts/events/scm-procurement-events.md:44` `[ADR-MONO-004](../../../docs/adr/)` → `[ADR-MONO-004](../../../../../docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md)` (3 `../` lands at project root → 5 `../` reaches repo root + filename for deep-link navigation per BE-283 pattern). Origin = TASK-SCM-BE-009 (2026-05-11) authoring 시점의 depth miscount. **검증**: `[ -e ... ]` RESOLVED. **CI**: 2 pass (`changes` 6s + `Frontend E2E smoke` 2m57s — contracts/events 트리거됐지만 markdown only 통과) / 15 SKIPPED / 0 fail. branch = `task/scm-be-013-procurement-events-adr-path-fix` (main 분기, BE-283 chore 머지 commit `c4a21777` 직후). lifecycle = ready → review → done. D4 OVERRIDE: ADR-MONO-003a § D1.1 (project-internal spec polish). **refactor-spec cycle Tier 3 closure**: 5 (BE-165 WMS) → 47 (BE-283 GAP) → 1 (본). **잔존**: BE-284 PiiMaskingUtils Tier 2 (GAP, judgment required — rename or drop). 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7 (inline self-review, single-line mechanical fix + post-merge re-verification).

- `TASK-SCM-BE-012-3-service-overview-skeleton.md` — spec PR #481 (squash `db749bf0`) + impl PR #482 (squash `36f53bae`) + this chore. **post-/refactor-spec portfolio-wide structural finding 의 마지막 piece** — SCM 3/3 service overview.md MISSING 상태 종결. 3 신규 file (~70-85 line each, +227 / -11): `gateway-service` (Layered intentional exception, edge gateway, tenant_id=scm fail-closed + IdentityHeaderStripFilter HIGHEST precedence + project-prefixed rate-limit keys + JWKS startup probe) / `procurement-service` (Hexagonal, PO lifecycle DRAFT→...→CLOSED/CANCELED, S2 Idempotency-Key dedup + S5 cross-service decision boundary + S6 AES-GCM encrypted credentials + S8 vendor SDK infrastructure-only + PoStatusMachine state guard + PurchaseOrderApplicationService TX boundary 유일성) / `inventory-visibility-service` (Hexagonal, read-only REST + cross-project wms Kafka consumer, S5 warning meta in all 4 endpoints + event dedupe on UUID v7 eventId + ShedLock-guarded StalenessDetectionScheduler + fail-open Redis cache + manual ACK 3-retry DLT). 7-section template 답습 (TASK-FAN-BE-006 / TASK-BE-141/142/146 sibling pattern). **본 PR 머지로 portfolio 5 운영 프로젝트 overview.md 일관성 100% 완성**: ecommerce 13/14 + fan-platform 4/4 + GAP 8/8 + WMS 7/7 + SCM 3/3 = ADR-MONO-003b § Phase 5 (Template 추출) unlock 직전의 마지막 polish. CI markdown-only path-filter 15 SKIP + 1 PASS. production code = 0. lifecycle = ready → review 직접 (in-progress 우회, BE-146/141/142 / FAN-BE-006 / MONO-084 same-day single-PR closure precedent — 본 task 가 10번째 entry). 분석=Opus 4.7 / 구현=Opus 4.7. 2026-05-14.

- `TASK-SCM-BE-011-inventory-visibility-alert-envelope-align.md` — spec PR #457 (squash `b5664a74`) + impl PR #458 (squash `4d932a3f`) 머지 (2026-05-13~14). **`/refactor-spec all --dry-run` (2026-05-13) SCM audit critical #1 finding closure**. `inventory-visibility-subscriptions.md` 의 `scm.inventory.alert.v1` publish envelope 가 sibling `scm-procurement-events.md` standard envelope shape (libs/java-messaging `BaseEventPublisher.writeEvent`) 와 불일치 → 5 field rename + drop 으로 align: (a) `eventVersion` → `schemaVersion` / (b) `producer` ("inventory-visibility-service") → `source` ("scm-platform-inventory-visibility-service") / (c) `aggregateType` drop / (d) `aggregateId` → `partitionKey` (value = nodeId) + sibling envelope field 표 (7 필드 description, procurement-events.md L58-66 답습) 추가. **Production code align** = `KafkaAlertPublisherAdapter.java` L41-48 의 publisher emit 도 같은 5 field rename + 1 drop 적용 (publisher 가 spec deviant 와 동일 emit 이었음). 4 file / +32 / -21 (spec + production 7 line + lifecycle + INDEX). **Wire-level breaking impact = 0** (현 spec 상 alert event consumer 미존재). **Impl PR CI = 15 PASS + 1 SKIP** (Observability 정상 skip; Integration (scm-platform) 2m8s PASS = publish-side envelope rename 의 IT 회귀 0 검증, scm E2E 36s PASS = cross-service 회귀 0, 전 sibling project IT/E2E + 4 boot jars + Frontend smoke 6m36s 모두 PASS — production code path-filter scm-platform 활성화 full pipeline 가드 통과). **Sibling 답습 패턴** = TASK-BE-144 (WMS notification eventVersion int 1, PR #451) + TASK-SCM-BE-010 (SCM HTTP error code, PR #453) + TASK-MONO-083 (platform jwt-standard-claims, PR #455) — 모두 same-day single-PR closure 검증된 패턴. 분석=Opus 4.7 / 구현=Opus 4.7.

- `TASK-SCM-BE-010-procurement-http-error-code-drift-fix.md` — impl PR #453 머지 (2026-05-13, squash commit `db5e7e00`). `/refactor-spec all --dry-run` (2026-05-13) SCM audit Top 1+2 critical findings closure. **Fix**: architecture.md Failure Mode #2 `409 IDEMPOTENCY_KEY_MISMATCH` → `422` (contracts canonical) + canonical `## Service Type` standalone header 추가 (HARDSTOP-10 PASS sibling alignment) + api.md Errors 목록 + Error Codes table 의 single `CONFLICT (409)` row 를 distinct 2 row 로 split (`CONCURRENT_MODIFICATION` optimistic-lock retry-OK + `CONFLICT` DB integrity must-change-state) + `GlobalExceptionHandler.java:117` emission `"CONFLICT"` → `"CONCURRENT_MODIFICATION"` (OptimisticLock handler only; DataIntegrityViolation handler 의 `"CONFLICT"` 유지) + `GlobalExceptionHandlerTest.java` 2 method assertion + DisplayName 갱신. 4 file / +18 / -10. API consumer breaking change (외부 consumer 거의 없는 SCM v1 portfolio scope). **CI 15 PASS + 1 SKIP** (Observability 정상 skip): Integration (scm-platform) 1m38s PASS = production code IT regression 0. E2E smoke 3 service + 4 boot jars 모두 PASS. **HARDSTOP-10 pre-condition fix** 가 hook bug (CRLF/LF mismatch in Edit tool simulation) 회피 + sibling order-service canonical pattern 답습. gateway / inventory-visibility 도 동일 HARDSTOP-10 drift 보유 → 별도 audit cleanup task 후보. 분석=Opus 4.7 / 구현=Opus 4.7.

- `TASK-SCM-BE-009-procurement-events-contract-authoring.md` — PR #341 머지 (2026-05-11). 7 published topics 정형화 (scm.procurement.po.submitted/acknowledged/confirmed/canceled/received/closed + scm.procurement.asn.received) — envelope shape + payload schema + idempotency key + consumer dedupe + retention 명시. spec-only. fan-platform/scm event contract 패턴 mirror.

- `TASK-SCM-BE-008-inventory-visibility-nodes-endpoint-cross-ref.md` — PR #333 머지 (2026-05-11). inventory-visibility 4 REST endpoint 중 `/nodes` 가 public 으로 결정 + architecture.md/gateway-public-routes.md 양쪽 동기. /refactor-spec all (PR #326) Finding [SCM 7] closure.

- `TASK-SCM-BE-007-int-001b-schema-doc-backfill.md` — PR #332 머지 (2026-05-11). INT-001b cycle 2 production fix (poNumber UUID v7 rand_b tail + InventoryNode.contactInfo @JdbcTypeCode(JSON)) data-model.md 반영. spec-only. /refactor-spec all Finding [SCM 6] closure.

- `TASK-SCM-BE-006-procurement-service-architecture-spec.md` — PR #331 머지 (2026-05-11). retroactive `procurement-service/architecture.md` + `procurement-api.md` 저작 (TASK-SCM-BE-002 PR #239 production code 후속). Hexagonal + PO 8단계 + Resilience4j + outbox + audit_log + AES-GCM + JWT. inventory-visibility-service/architecture.md 패턴 mirror. /refactor-spec all Finding [SCM 2] closure.

- `TASK-SCM-BE-005-inventory-visibility-consumer-it.md` — spec PR #266 + impl PR #267. inventory-visibility-service Testcontainers IT 8 메서드 / 6 클래스 (AbstractInventoryVisibilityIntegrationTest base + WmsInventoryAdjustedConsumer 2 + WmsInventoryReceivedConsumer 2 + EventDedupe 1 + InventoryNodeAutoCreate 1 + CrossTenantIsolation 1 + StalenessScheduler 1). cycle 1 PASS 1m14s on Rancher Desktop dockerd 29.1.3 + DOCKER_API_VERSION=1.45. **JSONB 회귀 가드 검증 완료** — `@JdbcTypeCode(SqlTypes.JSON)` 일시 제거 시 IT-1 + IT-4 둘 다 fail with `PSQLException: column "contact_info" is of type jsonb but expression is of type character varying` (정확히 INT-001b root cause #2 패턴). e2e 5m13s 에서만 catch 되던 회귀를 IT 1.5min 에서 catch. INT-001b → BE-002d 패턴 답습. 2026-05-09.

- `TASK-SCM-INT-001b-deeper-investigation-2-scenarios.md` — PR #262. TASK-SCM-INT-001a 잔존 2 fail 의 deeper investigation 종결. Cycle 1 (diagnostic): `@Disabled` 제거 + `[INT-001b][cb]` draft response print + `[INT-001b][wms]` Kafka AdminClient dump (topics / consumer-group state + members + assignment / endOffset / committedOffsets). Cycle 1 evidence 로 두 root cause 결정적 분리: (a) **23505 unique_violation** — `PurchaseOrderApplicationService.draft` 의 `poNumber = "PO-" + poId.substring(0, 8)` 에서 UUID v7 첫 32 bits 가 ms timestamp 라 tight loop 충돌. (b) **42804 datatype_mismatch** — `InventoryNodeJpaEntity.contactInfo` JSONB 컬럼에 `@JdbcTypeCode(SqlTypes.JSON)` 누락, Hibernate 6 default String→BYTEA 디스크립터로 jsonb 거부. Cycle 2 (production fix): poId.substring(28) (rand_b tail) + `@JdbcTypeCode(SqlTypes.JSON)`. CI 6/6 PASS (E2E scm-platform 5m13s). Path-filter 로 비-scm 잡 10개 SKIP 으로 cycle 당 ~9 분. 2 cycle 종결. 2026-05-07.

- `TASK-SCM-INT-001a-e2e-environment-fixup.md` — PR #260 의 6 cascade fix. (1) Slf4jLogConsumer attach 진단 인프라, (2) inventory-visibility V1 outbox + processed_events tables, (3) inventory-visibility JpaConfig (libs:java-messaging suppress 우회), (4) gateway @Primary on accountKeyResolver (TASK-MONO-044d 패턴), (5) e2e classpath PostgreSQL JDBC driver, (6) ProcurementDbFixtures URL host:port 명시 + Kafka topic 사전 생성. 5 fix 중 3 production / 2 test. 4/6 PASS, 2 잔존 → INT-001b 가 cycle 2 cycle 로 해소. 2026-05-07.

- `TASK-SCM-INT-001-procurement-inventory-visibility-e2e.md` — PR #260. scm-platform 첫 cross-service E2E. procurement → outbox → Kafka → inventory-visibility 흐름 + GAP IdP `tenant_id=scm` fail-closed + supplier circuit breaker E2E + cross-tenant isolation + cross-project event consumption. INT-001a 의 6 fix + INT-001b 의 2 production fix (poNumber 무작위 suffix + `@JdbcTypeCode(JSON)` on contactInfo) 후 6/6 PASS 종결. **Phase 5 trigger 의 마지막 outstanding 해소** — TASK-MONO-047 verify-template-readiness exit 0 후보. 2026-05-07.

- `TASK-SCM-BE-002d-procurement-testcontainers-it.md` — PR #257. TASK-SCM-BE-002b Phase 5 종결 — procurement-service Testcontainers IT 7 클래스 (multi-tenant isolation / outbox relay / supplier circuit breaker / supplier idempotency / state machine atomicity / asn overreceipt / audit log) + AbstractProcurementIntegrationTest base. Production small fix 3 (V1__init.sql `currency CHAR(3)` → `VARCHAR(3)` for PostgreSQL bpchar↔Hibernate VARCHAR mismatch / `@JdbcTypeCode(SqlTypes.JSON)` on `Supplier.contactInfoJson` + `AuditLog.beforeState`/`afterState` for Hibernate 6 JSONB binding) 별 commit 분리. Test infra fix: PO number suffix UUID v7 timestamp → pure random (collision in 65s window), AuditLog `@AfterEach` → `@AfterAll` for MockWebServer cross-test, IT-4 supplier-first call order alignment. local + CI 검증 — `Integration (scm-platform, Testcontainers)` Job pass 1m57s (PR #258 의 CI job 위에서). 9 tests PASS. **Phase 5 trigger 의 첫 outstanding 해소** — 남은 1건 = TASK-SCM-INT-001. 2026-05-07.

- `TASK-SCM-BE-002c-procurement-slice-tests.md` — PR #247. Phase 4 slice tests 추가. `@WebMvcTest` 3 controllers: PurchaseOrderControllerSliceTest 11 + AsnWebhookControllerSliceTest 4 + SupplierAckWebhookControllerSliceTest 4 = **20 slice tests**. + GlobalExceptionHandler small fix (`ResponseStatusException` 핸들러 부재로 webhook 401 이 catch-all 의 500 INTERNAL_ERROR 로 잘못 매핑되던 버그 — handler + test 1건 추가, 별 commit). H2 Flyway 호환 결과 **FAIL** — V1__init.sql 가 PostgreSQL syntax (JSONB/TIMESTAMPTZ/BYTEA/BIGSERIAL) → JPA slice 는 002d (Testcontainers IT) 로 통합. Security context 패턴: `TestingAuthenticationToken` + SecurityContextHolder 직접 + `@AutoConfigureMockMvc(addFilters=false)` (MockedStatic 불필요). 누적 procurement-service tests = 121 (002b 101 + 002c 20). 2026-05-06.

- `TASK-SCM-BE-002b-procurement-test-pyramid.md` — PR #243 + #244 + #245. procurement-service test pyramid 1차 완료 (TASK-SCM-BE-002 production code 후속). **101 tests 누적**: Phase 1 domain unit 64 (PoStatusMachine 49 — full transition matrix per actor + terminal/self-transition guards + linear lifecycle, Money VO 15 — factory normalization + null/negative/length validation + add() currency match) + Phase 2 application unit 16 (PurchaseOrderApplicationServiceTest — 6 commands × happy + edge, Mockito strict-safe with `lenient()`) + Phase 3 GlobalExceptionHandler 21 (모든 exception → ApiErrorBody status code 매핑 검증, direct unit test no Spring context). Phase 4 (slice WebMvcTest) → TASK-SCM-BE-002c 분리. Phase 5 (Testcontainers IT) → TASK-SCM-BE-002d 분리 (Docker fix 후). production code 무변경. CI Build & Test all PASS. 2026-05-06.

- `TASK-SCM-BE-003-inventory-visibility-service-bootstrap.md` — PR #241. scm-platform 두 번째 도메인 service `inventory-visibility-service` 부트스트랩. **Hexagonal architecture** + Service Type=`rest-api`+`event-consumer` + cross-node 재고 read-model (자사 wms / supplier / 3PL / in-transit). **cross-project event consumption 첫 사례** — wms-platform 의 `wms.inventory.{received,adjusted,transferred}.v1` 3 topic 구독 + EventDedupe (eventId 기반 멱등). **batch-heavy trait 첫 코드** — `@Scheduled` 5분 주기 staleness 감지 + ShedLock 분산 lock + alert event publish (`scm.inventory.alert.v1`). 5 read-only API endpoint + 4 entity (InventoryNode / InventorySnapshot / NodeStaleness / EventDedupe) + Flyway V1 schema + OAuth2 RS (RS256 / GAP JWKS / `tenant_id=scm` fail-closed). 81 files / 4409 insertions. Spec 3개 (architecture / data-model / staleness-monitoring) + contracts 2개 (inventory-visibility-api / inventory-visibility-subscriptions). gateway-service `/api/v1/inventory-visibility/**` 라우트 활성화. CI 12/12 PASS — Build & Test 1m42s, GAP/master/gateway/fan-platform IT all pass, frontend e2e pass, 모든 boot jars pass. 2026-05-06.

- `TASK-SCM-BE-002-procurement-service-bootstrap.md` — PR #239. scm-platform 핵심 도메인 service `procurement-service` 부트스트랩. **Hexagonal architecture** (domain/application/infrastructure/presentation) + PO 상태기계 8 단계 (DRAFT→SUBMITTED→ACKNOWLEDGED→CONFIRMED→PARTIALLY_RECEIVED→RECEIVED→SETTLED→CLOSED) + supplier adapter port + Resilience4j circuit breaker + idempotency + outbox polling scheduler + OAuth2 RS (RS256 / GAP JWKS / `tenant_id=scm` fail-closed) + AES credential encryptor + tenant claim enforcer filter + 9 domain exceptions + Money VO + audit history. JPA adapters 6개 + Flyway V1__init.sql 209 lines. REST controllers 3 (PurchaseOrder + AsnWebhook + SupplierAckWebhook). 89 files / 4231 insertions. CI 12/12 PASS — Build & Test 1m38s, GAP/master/gateway IT pass, Frontend e2e pass, 모든 boot jars pass. **Tests deferred** (agent FailedToOpenSocket mid-session) → TASK-SCM-BE-002b 분리. 2026-05-06.

- `TASK-SCM-BE-001-gateway-service-bootstrap.md` — scm-platform 의 첫 service `gateway-service` Spring Boot 부트스트랩. Spring Cloud Gateway 3.4 + OAuth2 Resource Server (GAP RS256 JWT 검증) + TenantClaimValidator (`tenant_id=scm` only) + Redis rate limit + Traefik label 활성화 (`scm.local`). procurement / inventory-visibility 라우트는 placeholder (BE-002/003 활성화). spec 3개 (gateway architecture / public-routes / gap-integration). 단위·슬라이스·통합 테스트 + 루트 CI Build & Test 에 모듈 추가. PR #194 머지. 2026-05-05.

## archive

(empty)
