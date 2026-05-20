# Task ID

TASK-PC-FE-013

# Title

Phase 7 두 번째 cross-domain dashboard — Domain Health Overview (console-bff 의 `§ 2.4.9.2` composition route + console-web `(console)/dashboards/health` 5-card screen)

# Status

done

# Owner

backend + frontend (joint, TASK-PC-FE-011 동형 명명 — joint scope 라도 FE 번호로 author)

# Task Tags

- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **part of**: ADR-MONO-017 § 3.3 #4 — *"Subsequent Phase 7 dashboards (domain health, throughput) — separate tasks."* 본 task = 명시된 *domain health* 후속.
- **depends on**: TASK-PC-BE-001 (`console-bff` skeleton, done) + TASK-PC-FE-011 (MVP Operator Overview, done) — 두 task 의 패턴 verbatim reuse.
- **builds on**: TASK-PC-BE-002 (virtual thread × `@RequestScope` pre-resolve fix, done) — 본 use case 의 fan-out 도 동일 패턴 (그러나 *credential-less* legs 이므로 pre-resolve 단계 자체가 부재, ScopeNotActiveException 회귀 sound by construction).
- **ADR-017 § D4 scope clarification (doc-level only)**: D4 sealed switch 의 scope 는 *§ 2.4.5/6/7/8 으로 정의된 data API legs only*. Public no-auth `/actuator/health` legs (Spring Boot actuator 표준, 모든 5 producer SecurityConfig 에서 verified public) 는 D4 scope **외** — credential 없이 호출. ADR 텍스트 변경 부재 (`§ 3.3 / § D4 / D1-D8` byte-unchanged), § 2.4.9.2 안에서 explicit clarify. HARDSTOP-04 안전.

# Goal

ADR-MONO-017 § 3.3 #4 가 사전 인가한 두 후보 (*domain health*, *throughput*) 중 첫 번째 = **Domain Health Overview** 구현. TASK-PC-FE-011 의 MVP Operator Overview pattern 을 verbatim reuse 하되:

- **Surface**: `GET /api/console/dashboards/domain-health` (additive `§ 2.4.9.2`)
- **Composition**: 5 도메인의 public `/actuator/health` fan-out (server-side, parallel virtual threads).
- **Credential**: D4 scope **외** — actuator endpoint 가 모든 5 producer 에서 public no-auth (SecurityConfig `permitAll`). BFF leg 는 Authorization header 절대 부재. ADR-017 § D4 *"per-domain credential rule"* 가 *§ 2.4.5/6/7/8 data API* 만 govern, actuator metadata legs 는 별 dispatch (sealed switch 호출 부재).
- **Response shape**: 5-card envelope, fixed order `[gap, wms, scm, finance, erp]`, status ∈ `{ ok, degraded }` (forbidden 부재 — 403 절대 발생 안함, public endpoint). `data.status` ∈ Spring Boot health 표준 `{ UP, DOWN, OUT_OF_SERVICE, UNKNOWN }`.
- **Hard invariants**: § 3.3 zero-retrofit 7번째 confirmation (5 producer spec/impl byte-unchanged), § 3 parity matrix attestation count = 16 byte-unchanged.

console-web 측 = 1 server-component route (`(console)/dashboards/health`) + 1 server-side proxy + 5-card screen + 1 nav entry. server-component-first (only RetryButton = client). 새 navigation entry "도메인 상태".

# Scope

## In Scope

### Spec-first (lands before/with impl in same spec PR commit 1)

- `projects/platform-console/specs/contracts/console-integration-contract.md` **§ 2.4.9.2** 신규 (additive only; § 2.4.9 / § 2.4.9.1 / § 2.5 / § 3 byte-unchanged):
  - Surface (path / auth / producer = `console-bff`).
  - 5-row producer table — 각 도메인의 actuator-host + `/actuator/health` path + **"Auth: none (public actuator; § D4 scope 외 — D4 governs § 2.4.5/6/7/8 data legs only)"** column + 인용 SecurityConfig (each producer permitAll).
  - Response schema (5-card envelope, status ∈ `{ok, degraded}`, `data.status` = Spring Boot 표준).
  - Error envelope (composition-level — 400 NO_ACTIVE_TENANT / 401 TOKEN_INVALID / 503 reserved-never-emitted; per-leg degrade).
  - Auth flow (Inbound = MVP 동일; Outbound = no Authorization header per leg).
  - Resilience (per-leg circuit-breaker keyed by `(domain, "domain-health")` + per-leg 2s timeout + composition 5s budget).
  - Observability (`bff_fanout_latency_seconds{domain,route="domain-health"}` + `bff_fanout_errors_total{...,route="domain-health"}` + `bff_aggregation_degrade_count_total{dashboard="domain-health",degraded_domain}` — 3 mandatory metric families with new `route` / `dashboard` label values).
  - **Hard invariants** explicit list (no retrofit, no D4 sealed switch call on these legs, read-only, etc.).
  - **§ 3 parity matrix NOT mutated** (count = 16 byte-unchanged — dashboards 는 § 3 row 아님, § 2.4.9.X composition route 카탈로그).

### Backend (`apps/console-bff/`)

- `domain/composition/` — 기존 `LegOutcome` + `DegradePolicy` 재사용 (variant 추가 부재). status 가 `{ok, degraded}` 만이라 sealed shape 그대로.
- `application/usecase/DomainHealthCompositionUseCase.java` — sibling to `OperatorOverviewCompositionUseCase`:
  - 동일 virtual-thread fan-out + `time()` catch + per-leg metric emission, *route label* `"domain-health"`.
  - **Credential pre-resolve 단계 부재** (no credential needed). main thread → fan-out 직행. ScopeNotActiveException 회귀 sound by construction.
  - 5 narrow port interfaces: `GapHealthReadPort` / `WmsHealthReadPort` / `ScmHealthReadPort` / `FinanceHealthReadPort` / `ErpHealthReadPort` — 모두 새 super-port `DomainHealthReadPort` (또는 기존 `DomainReadPort` shape 재사용; `read(tenantId, /*bearer*/ null)` 같은 명시 invariant 로 — 자세한 shape 은 impl PR design).
- `adapter/outbound/http/` — 5 새 `*HealthAdapter`:
  - `GET /actuator/health` against 기존 5 named `RestClient` (`gapRestClient`/`wmsRestClient`/`scmRestClient`/`financeRestClient`/`erpRestClient` — Operator Overview 와 동일 base URL 재사용; `consolebff.outbound.<d>.base-url` 환경변수 그대로).
  - **Authorization header 절대 부재** (D4 scope 외, public no-auth). `X-Tenant-Id` 도 부재 (actuator endpoint 가 tenant-scoped 가 아님).
  - `Map<String, Object> data` 반환 (Spring Boot health JSON: `{"status": "UP", ...}`).
- `adapter/inbound/web/DomainHealthController.java` — sibling to `OperatorOverviewController`. inbound auth: GAP OIDC Bearer + X-Tenant-Id (404/401/400 처리 동일; X-Operator-Token 부재 OK — actuator legs 안 씀).
- `adapter/inbound/web/DomainHealthResponse.java` — 5-card envelope DTO.
- `RestClientConfig.java` — 새 bean 추가 부재 (기존 5 named `RestClient` 재사용; Operator Overview 와 base URL / timeout 동일).
- Tests:
  - Unit (`DomainHealthCompositionUseCaseTest`) — sibling to `OperatorOverviewCompositionUseCaseTest`, STRICT_STUBS, 5 port mock, happy / per-leg-degrade / all-down 등.
  - Slice (`DomainHealthSliceTest`) — `@WebMvcTest` 또는 적절 layer.
  - IT (`DomainHealthIntegrationTest`) — sibling to `OperatorOverviewIntegrationTest`, MockWebServer × 5, `@DynamicPropertySource` 로 base-url override (cycle 3 함정 재현 방지 — 이미 application.yml binding 적용됨).

### Frontend (`apps/console-web/`)

- `src/features/domain-health/` — server-component-first:
  - `api/types.ts` — 5-card envelope zod schema.
  - `api/domain-health-api.ts` — server-only fetch wrapper to `/api/console/dashboards/domain-health` (server proxy).
  - `components/DomainHealthScreen.tsx` (server) — fixed 5-card grid.
  - `components/DomainHealthCard.tsx` (server) — 5 doman × 3 branches (`ok` + `data.status` rendering — UP/DOWN/OOS/UNKNOWN visual differentiation; `degraded` placeholder).
  - `components/DegradeBanner.tsx` (server) — all-down 시 surface.
  - `components/RetryButton.tsx` (client only — explicit user retry, no auto-poll).
- `src/app/(console)/dashboards/health/page.tsx` — route entry, server-component.
- `src/app/api/console/dashboards/domain-health/route.ts` — server-side proxy (Authorization + X-Tenant-Id forward; X-Operator-Token 부재 — backend 안 씀).
- `src/app/(console)/layout.tsx` — nav entry "도메인 상태" 추가 (additive, 기존 "통합 개요" 옆).
- Tests:
  - vitest unit: api / state / nav / DomainHealthScreen / DomainHealthCard (UP/DOWN/OOS/UNKNOWN/degraded branches).

### Task lifecycle

- 본 task md 를 `tasks/ready/` 에 author (spec PR), `tasks/done/` 으로 이동 (close chore).

## Out of Scope

- 다른 ADR / ADR-MONO-017 D1-D8 변경 부재 (HARDSTOP-04 discipline).
- `OperatorOverviewCompositionUseCase` 또는 § 2.4.9.1 / § 3 parity matrix 변경 부재 (count=16, attestation marker 유지).
- 5 producer 의 SecurityConfig / actuator endpoint / specs / impl 변경 부재 (zero-retrofit 7번째 confirmation).
- Throughput dashboard (§ 3.3 #4 두 번째 후보, 별 future task).
- Audit Activity Across Domains (별 design).
- Per-component (DB / Kafka / Redis) health drill-down — Spring Boot `management.endpoint.health.show-details` 가 `never` (안전) 이라 producer 측 detail 노출 부재; aggregated `status` 만 surface.
- Health check 의 *push* notification / threshold alert / 자동 polling — operator-initiated retry only.

# Acceptance Criteria

1. `console-integration-contract.md` § 2.4.9.2 신규 (§ 2.4.9 / § 2.4.9.1 / § 2.5 / § 3 byte-unchanged).
2. `console-bff` 5 새 HealthAdapter + 1 새 UseCase + 1 새 Controller + 1 새 Response DTO + tests (unit + slice + IT) — *credential pre-resolve 단계 부재* by construction.
3. `console-web` `features/domain-health/` + `(console)/dashboards/health` route + `api/console/dashboards/domain-health` proxy + nav entry — server-component-first.
4. **ADR-MONO-017 D1-D8 byte-unchanged**, `§ 3.3 / § D4` byte-unchanged (D4 scope clarification 는 § 2.4.9.2 안에서만, ADR 텍스트 미접촉).
5. **§ 3 attestation count = 16 byte-unchanged** (parity-verification.test.ts no-drift guard 통과).
6. 5 producer spec/impl + console-web/(other features) + console-integration-contract.md § 2.4.9 / § 2.4.9.1 / § 2.5 / § 2.6 byte-unchanged.
7. **No Authorization header / X-Tenant-Id / X-Operator-Reason / Idempotency-Key on actuator legs** — grep-asserted (production code + test).
8. self-CI ALL GREEN (frontend unit + console-bff IT 포함).
9. **BE-303 3-dimension 검증** 통과 — spec/impl/close 각 머지 시 (a)+(b)+(c) 셋 다 충족 (TASK-MONO-127 룰 적용).

# Related Specs

- `projects/platform-console/specs/services/console-bff/architecture.md` — Hexagonal 분리, virtual-thread fan-out.
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9 (BFF surface frame) + § 2.4.9.1 (MVP Operator Overview, pattern source).
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` § 3.3 #4 (사전 인가 `domain health`), § D4 (scope clarification at § 2.4.9.2 level).

# Related Contracts

- 각 producer 의 SecurityConfig 의 `/actuator/health` permitAll (변경 부재, 단순 reference).

# Edge Cases

- **5 producer 중 한 leg 이 503 / connection refused / 타임아웃**: 그 card 만 `degraded`, 나머지 4 card 정상 surface. D5.A discipline 그대로.
- **모든 5 leg DOWN**: 5 card 모두 `degraded`, 200 envelope. D5.B (전체 503) 거부.
- **producer 가 actuator endpoint 를 향후 private 화 (regression)**: BFF leg 가 401 → `degraded` (reason="DOWNSTREAM_ERROR"). 진단 surface 는 server log + Prometheus error counter. 별 fix-task (producer SecurityConfig 의 actuator permitAll 회복 또는 본 dashboard 폐기).
- **Inbound `X-Operator-Token` 부재**: actuator legs 가 안 쓰므로 OK. 단 controller 가 X-Operator-Token 없어도 401 절대 발생하지 않게 — inbound 검증 logic 이 (Operator Overview 의) X-Operator-Token 의무화 와 *분리* 되어야 (별 controller, 또는 controller-level 검증 logic 가 surface-aware).
- **Spring Boot actuator response shape 변화** (future Spring Boot upgrade): `data.status` 가 enum 깨지면 client zod schema fail → `degraded` mapping. defensive.

# Failure Scenarios

| 조건 | 본 PR 의 반응 |
|---|---|
| ADR-017 D4 scope 가 "actuator legs 도 D4 govern" 으로 재해석 필요 | 본 PR scope 외. ADR-018 PROPOSED 별 task. 본 PR 은 D4 scope = "data legs only" 가정 (§ 2.4.5/6/7/8 텍스트가 *data legs* 만 정의) 으로 진행. 본 가정이 review 단계에서 rejected 시 STOP. |
| 5 producer 중 actuator/health 가 *private* (auth required) 인 곳 발견 | 본 PR scope 외. 그 도메인의 actuator permitAll 추가 = producer spec-first task → 별 fix-task. 본 PR 의 사전 점검 (이 task md 작성 시 5/5 public 확인됨, 2026-05-21 grep) 결과 부재. |
| TASK-MONO-127 의 3-dim BE-303 검증이 본 PR 머지 시점에 fail (CI RED) | STOP. close chore 진입 부재. main GREEN 회복 fix-task 가 close chore 앞에 와야 함. |
| `OperatorOverviewCompositionUseCase` 의 credential pre-resolve 패턴이 본 task 의 `DomainHealthCompositionUseCase` 에서 의도 외 재현 (sealed switch 호출) | impl PR review 단계에서 grep-assert "no `credentialSelection.selectFor` call in DomainHealthCompositionUseCase". 위반 시 fix. |

---

# Implementation Notes (impl PR 단계 reference)

권장 use case shape:

```java
@Service
public class DomainHealthCompositionUseCase {
    private static final Duration COMPOSITION_TIMEOUT = Duration.ofSeconds(5);
    static final String ROUTE_LABEL = "domain-health";
    static final String DASHBOARD_LABEL = "domain-health";
    public static final List<DomainTarget> CARD_ORDER = List.of(GAP, WMS, SCM, FINANCE, ERP);

    private final MeterRegistry meterRegistry;
    private final GapHealthReadPort gapPort;
    // ... 4 more

    public List<HealthLeg> compose() {
        // NO credential pre-resolve. NO credential resolve in any virtual thread.
        // Each leg fires GET /actuator/health with NO Authorization header.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // sibling fan-out: 5 supplyAsync, allOf, resolve
        }
    }
}

public interface GapHealthReadPort { Map<String, Object> read(); }  // tenantId / bearer 부재
// ... 4 more

@Component
public class GapHealthReadAdapter implements DomainHealthCompositionUseCase.GapHealthReadPort {
    private final RestClient client;
    public GapHealthReadAdapter(@Qualifier("gapRestClient") RestClient client) { this.client = client; }
    @Override
    public Map<String, Object> read() {
        return (Map<String, Object>) client.get()
                .uri("/actuator/health")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
    }
}
```

추가 grep-assert (test 단계):

```java
// DomainHealthIntegrationTest 안:
// 5 leg 모두 Authorization / X-Tenant-Id / X-Operator-Token 부재
// MockWebServer 의 RecordedRequest 검증:
assertThat(req.getHeader("Authorization")).isNull();
assertThat(req.getHeader("X-Tenant-Id")).isNull();
assertThat(req.getHeader("X-Operator-Token")).isNull();
```

---

# Approval

- 분석 = Opus 4.7
- 구현 권장 = Opus 4.7 (BE composition design + FE 5-card screen, FE-011 패턴 재사용)
- 리뷰 = Opus 4.7 (dispatcher 독립 재검증 + acceptance criteria 9/9 단언 + ADR-017 D4 scope 가정 검증)
