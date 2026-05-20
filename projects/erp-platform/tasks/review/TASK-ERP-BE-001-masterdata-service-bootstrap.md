# Task ID

TASK-ERP-BE-001

# Title

masterdata-service bootstrap — Hexagonal organization master data (부서/직원/직급/비용센터/거래처 / 참조 무결성 / 유효기간 / SSO + 권한 매트릭스 / 불변 audit_log)

# Status

review

# Owner

backend-engineer

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api
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

- **depends on**: TASK-MONO-119 (erp-platform bootstrap artifact — `projects/erp-platform/` tree + masterdata-service skeleton + GAP V0018 ×2 seed + monorepo wiring; ADR-MONO-016 ACCEPTED Option C). The masterdata-service skeleton (minimal `@SpringBootApplication`, `application.yml`, empty `db/migration/`) and the GAP `erp-platform-internal-services-client` + `erp` tenant V0018 seed are prerequisites; this task implements the domain on top.
- **prerequisite for**: erp-platform v2 (`approval-service` 결재 워크플로 / `read-model-service` 통합 조회 — ADR-MONO-016 § D3 v2). gateway-service activation.
- **precedent**: TASK-FIN-BE-001 (finance account-service Hexagonal bootstrap — state machine + idempotency + audit_log + OAuth2 RS) is the closest shape reference; TASK-SCM-BE-001 (gateway-service) for the edge/tenant-gate pattern.

---

# Goal

erp-platform 의 첫 도메인 service `masterdata-service` 를 Hexagonal architecture 로 구현한다. erp 도메인 룰 (`rules/domains/erp.md` E1–E8) 을 v1 범위에서 적용 — 조직 마스터데이터 라이프사이클(부서 계층 / 직원 조직속성 / 직급 / 비용센터 / 거래처 / 참조 무결성 / 유효기간(effective-dated) / 불변 audit_log / SSO + 권한 매트릭스 fail-closed / internal-only 경계). 결재 워크플로 풀 구현(다단 라우팅·대결·위임·결재함)·통합 read model 실 구현은 명시적으로 **v2 (approval-service / read-model-service)** — 본 task 범위 밖 (ADR-MONO-016 § D3, PROJECT.md § ADR-MONO-016 vs 7축 framing 정합).

본 task 의 service architecture 결정 (Hexagonal: domain ← application(use cases + ports) ← adapter(inbound web / outbound persistence)) 은 구현 전에 `projects/erp-platform/specs/services/masterdata-service/architecture.md` 로 author 되어야 한다 (HARDSTOP-09 — architecture decision before implementation; platform/architecture-decision-rule.md).

# Scope

## In Scope

1. **Service architecture spec** — `specs/services/masterdata-service/architecture.md` (Hexagonal; Service Type = `rest-api`; layer 경계; erp E1–E8 매핑; HARDSTOP-10 canonical form — Service Type 선언 + Identity 표 + `### Service Type Composition`).
2. **Domain layer** (pure Java, no framework): 마스터 aggregate 묶음 (`Department` 계층, `Employee` 조직속성, `JobGrade`, `CostCenter`, `BusinessPartner`), 참조 무결성 규칙 (E1 — 참조 중 레코드 물리 삭제 금지·논리 폐기만, 계층 순환 금지), effective-dating VO (E2), 도메인 예외 (erp Standard Error Codes).
3. **Application layer**: use case 서비스 + Command/Result, 마스터 변경 원자성 + 멱등성 (E1·transactional), audit_log port (E2·E8). Command/Result 네이밍 (`{UseCase}Command`/`{UseCase}Result`).
4. **Infrastructure layer**: MySQL JPA adapters + Flyway `V1__init.sql` (erp_db), OAuth2 Resource Server (GAP RS256 JWKS, `tenant_id=erp` fail-closed, internal-only), 권한 매트릭스 + 데이터 범위 인가 (E6 — fail-closed), audit_log append-only 저장 (E2).
5. **Presentation layer**: REST controllers (마스터 생성 / 조회 / 수정 / 폐기, 시점 조회) + Request/Response DTO + GlobalExceptionHandler (erp error code → HTTP 매핑).
6. **HTTP contract** — `specs/contracts/http/masterdata-api.md` (엔드포인트 + 에러 코드 표 + 데이터 범위 규칙). **Contracts precede code** (CLAUDE.md Layer Rules) — contract 를 구현 전에 author/갱신.
7. **Event contract** (해당 시) — `specs/contracts/events/erp-masterdata-events.md` (`erp.masterdata.*` envelope, libs/java-messaging 표준 shape).
8. **Dockerfile** (finance account-service Dockerfile 패턴; `masterdata-service.jar`).
9. **Tests** — 테스트 피라미드 (`testing-backend` skill + platform/testing-strategy.md): domain unit (참조 무결성 / 계층 순환 / effective-dating 불변식), application unit (`@ExtendWith(MockitoExtension.class)` STRICT_STUBS, Command 별 happy + edge), slice (`@WebMvcTest` + SecurityConfig + GlobalExceptionHandler), integration (`@SpringBootTest` + Testcontainers MySQL — H2 금지, tenant isolation / 참조 무결성 / 권한 매트릭스 fail-closed / audit_log append-only / 시점 조회).
10. **docker-compose.yml 활성화** — gateway-service/masterdata-service block 주석 해제 (masterdata-service Dockerfile + Flyway schema 존재 후), Traefik `erp.local` label 활성화.
11. **settings.gradle / CI** — masterdata-service 는 이미 settings.gradle 에 등록됨 (TASK-MONO-119); CI build matrix 에 masterdata-service 가 path-filter 로 들어오는지 확인.

## Out of Scope

- `approval-service` (결재 워크플로 풀 구현) — v2 (ADR-MONO-016 § D3).
- `read-model-service` (통합 조회 read model) / permission-service / notification-service / admin-service — v2.
- 인사 깊이(급여/근태/평가) — erp 는 7축상 도메인 로직 미보유; 인사 깊이는 별 도메인.
- 다른 도메인(scm/wms/ecommerce) 사실의 권위적 변경 — erp 는 통합 read model 에서 read-only 합성만 (E5).
- frontend — 통합 platform console 이 렌더 (ADR-MONO-013 §3.3).

# Acceptance Criteria

- **AC-1**: `specs/services/masterdata-service/architecture.md` 가 구현 전에 존재 (Hexagonal, Service Type 선언, HARDSTOP-10 canonical form PASS).
- **AC-2**: 마스터 자연키 유일 + 참조 중 레코드 물리 삭제 차단 (논리 폐기만) + 계층 순환 차단 (erp E1). 위반 시 `MASTERDATA_REFERENCE_VIOLATION` / `MASTERDATA_DUPLICATE_KEY` / `MASTERDATA_PARENT_CYCLE`.
- **AC-3**: 마스터 변경이 effective-dated — 과거 시점 조회가 그 시점 값으로 재현 (E2). 변경 + 감사 기록이 한 트랜잭션.
- **AC-4**: 모든 마스터 생성/수정/폐기가 actor/timestamp/before/after 의 append-only audit_log 기록 (E2·E8). 사후 수정·삭제 차단.
- **AC-5**: 인가가 권한 매트릭스 + 데이터 범위로 fail-closed (E6) — 권한/범위 불충분 시 `PERMISSION_DENIED` / `DATA_SCOPE_FORBIDDEN`.
- **AC-6**: OAuth2 Resource Server — GAP RS256 JWKS 검증 + `tenant_id=erp` fail-closed (cross-tenant 403 `TENANT_FORBIDDEN`) + internal-only 경계 (E7).
- **AC-7**: 테스트 피라미드 — domain/application unit + slice + Testcontainers IT (MySQL, H2 금지). CI green.
- **AC-8**: `specs/contracts/http/masterdata-api.md` 가 구현과 일치 (contracts precede code). 추가 API 는 contract 에 정의된 것만.
- **AC-9**: `./gradlew :projects:erp-platform:apps:masterdata-service:check` green. docker-compose `erp.local` Traefik 활성화 + backing service `expose:` only (Local Network Convention).

# Related Specs

- `rules/domains/erp.md` (E1–E8 mandatory rules — governing 도메인 룰).
- `rules/traits/internal-system.md`, `rules/traits/transactional.md`, `rules/traits/audit-heavy.md` (선언 trait — 있으면 로딩).
- `projects/erp-platform/PROJECT.md` (classification, Service Map v1, GAP integration, v1 IN/OUT slice).
- `projects/erp-platform/specs/integration/gap-integration.md` (OAuth2 RS + tenant gate + internal-only).
- `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` § D2/D3 (v1 = masterdata-service; approval/read-model = v2).
- `platform/architecture-decision-rule.md`, `platform/testing-strategy.md`, `platform/service-types/rest-api.md`.
- precedent: `projects/finance-platform/specs/services/account-service/architecture.md` (Hexagonal shape reference).

# Related Contracts

- **신규 author**: `specs/contracts/http/masterdata-api.md` (masterdata-service HTTP API — 마스터 생성/조회/수정/폐기/시점조회 + 데이터 범위 + 에러 코드 표). v1 첫 contract.
- **신규 author (event-side 포함 시)**: `specs/contracts/events/erp-masterdata-events.md` (`erp.masterdata.*` — libs/java-messaging 표준 envelope; scm/finance events 패턴).
- **GAP-side (이미 존재, TASK-MONO-119 V0018)**: `erp-platform-internal-services-client` (client_credentials, scopes `erp.read`/`erp.write`) + `erp` tenant. 본 task 는 GAP contract 변경 없음 (consume only).
- platform/error-handling.md — erp Standard Error Codes 등록 (masterdata-service 가 emit 하는 코드).

# Edge Cases

- **참조 무결성**: 직원→부서, 비용센터→부서 등 참조 중 마스터의 폐기 → `MASTERDATA_REFERENCE_VIOLATION`. 부서 계층의 순환(자기 조상으로 설정) → `MASTERDATA_PARENT_CYCLE`.
- **유효기간 모순**: 종료 < 시작, 또는 같은 자연키의 기간 겹침 → `MASTERDATA_EFFECTIVE_PERIOD_INVALID`. 과거 시점 조회는 그 시점에 유효했던 레코드만.
- **데이터 범위**: 사용자 소속 조직 단위 밖의 마스터 조회/변경 → `DATA_SCOPE_FORBIDDEN` (fail-closed, 전사 조회 권한 없으면 본인 범위만).
- **cross-tenant 토큰**: `tenant_id != erp` (그리고 `*` 아님) → 403 `TENANT_FORBIDDEN` fail-closed (gateway 미가동 시 service-level enforcer 가 가드).
- **external traffic**: 비-내부망·비-SSO 접근 시도 → 거부 (`EXTERNAL_TRAFFIC_REJECTED`; internal-system 경계 E7).
- **MySQL/Hibernate 타입**: JSON 컬럼 (`@JdbcTypeCode(SqlTypes.JSON)`), effective-date `DATE`/`DATETIME` 컬럼 — Hibernate 6 바인딩 정합 (finance V1__init MySQL 대응 교훈).
- **audit_log append-only**: UPDATE/DELETE 금지 (트리거 또는 application 가드). 사후 수정 시도는 위반.

# Failure Scenarios

- **참조 중 마스터 물리 삭제 허용** → AC-2 reject. Mitigation: 논리 폐기 상태기계 + 참조 카운트 가드, 참조 무결성 IT.
- **계층 순환 미차단** → AC-2. Mitigation: 부모 설정 시 조상 경로 검사, 순환 시나리오 unit/IT.
- **권한 매트릭스 fail-open** → AC-5. Mitigation: 인가 진입점 단일 경로 + 데이터 범위 enforcer, fail-closed IT (권한 없음 = 거부 default).
- **마스터 변경 audit_log 누락/수정** → AC-4. Mitigation: 변경 경로에 audit 기록 의무, append-only 저장 + 가드 IT.
- **architecture.md 미작성 후 구현** → HARDSTOP-09 hook block. Mitigation: AC-1 — spec 선행 (spec PR → impl PR 분리, tasks/INDEX.md PR Separation Rule).
- **`./gradlew` regression** → AC-9. Mitigation: finance account-service build.gradle 패턴 답습, skeleton 이 이미 `:tasks` resolves (TASK-MONO-119 검증).
- **GAP V0018 미적용 환경** → 토큰 발급 불가. Mitigation: GAP V0018 seed 가 TASK-MONO-119 에서 머지됨 (AC 의 GAP IT green 전제).

# Verification

1. `specs/services/masterdata-service/architecture.md` 존재 + HARDSTOP-10 canonical form PASS (구현 commit 전).
2. 참조 무결성 / 계층 순환 / effective-dating 불변식 domain unit + IT.
3. 권한 매트릭스 fail-closed IT (권한 없는 사용자 = 거부 default) + `DATA_SCOPE_FORBIDDEN`.
4. cross-tenant 토큰 IT → 403 `TENANT_FORBIDDEN`; external traffic 거부.
5. `./gradlew :projects:erp-platform:apps:masterdata-service:check` exit 0 + Testcontainers IT (MySQL) green.
6. `docker compose --project-directory projects/erp-platform config -q` valid + `erp.local` Traefik label 활성 + backing service host port 0건.
7. `specs/contracts/http/masterdata-api.md` ↔ 구현 일치 (contract-first).
8. self-review APPROVED (review-checklist Spec/Arch/Quality/Security/Testing).
