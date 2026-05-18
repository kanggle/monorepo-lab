# Task ID

TASK-FIN-BE-001

# Title

account-service bootstrap — Hexagonal Account lifecycle (KYC / available-ledger balance / hold-release-capture / account state machine / idempotent fund movement / immutable audit_log)

# Status

done

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

- **depends on**: TASK-MONO-114 (finance-platform bootstrap artifact — `projects/finance-platform/` tree + account-service skeleton + GAP V0017 ×2 seed + monorepo wiring; ADR-MONO-008 ACCEPTED Option C). The account-service skeleton (minimal `@SpringBootApplication`, `application.yml`, empty `db/migration/`) and the GAP `finance-platform-internal-services-client` + `finance` tenant V0017 seed are prerequisites; this task implements the domain on top.
- **prerequisite for**: finance-platform v2 (`ledger-service` double-entry / GL / AP — fintech accounting depth, ADR-MONO-008 § D3 v2). gateway-service activation.
- **precedent**: TASK-SCM-BE-002 (scm procurement-service Hexagonal bootstrap — PO state machine + idempotency + outbox + audit_log + OAuth2 RS) is the closest shape reference; TASK-SCM-BE-001 (gateway-service) for the edge/tenant-gate pattern.

---

# Goal

finance-platform 의 첫 도메인 service `account-service` 를 Hexagonal architecture 로 구현한다. fintech 도메인 룰 (`rules/domains/fintech.md` F1–F8) 을 v1 범위에서 적용 — Account 라이프사이클(KYC 단계 / 가용·장부 잔액 / hold·release·capture / 계좌 상태기계 / 자금 이동 멱등 / 불변 audit_log). 복식부기 원장(double-entry ledger)·GL·AP 회계 깊이는 명시적으로 **v2 (ledger-service)** — 본 task 범위 밖 (ADR-MONO-008 § D3, PROJECT.md § ADR-MONO-008 vs 7축 framing 정합).

본 task 의 service architecture 결정 (Hexagonal: domain ← application(use cases + ports) ← adapter(inbound web / outbound persistence + messaging)) 은 구현 전에 `projects/finance-platform/specs/services/account-service/architecture.md` 로 author 되어야 한다 (HARDSTOP-09 — architecture decision before implementation; platform/architecture-decision-rule.md).

# Scope

## In Scope

1. **Service architecture spec** — `specs/services/account-service/architecture.md` (Hexagonal; Service Type = `rest-api` + `event-consumer`; layer 경계; fintech F1–F8 매핑; HARDSTOP-10 canonical form — Service Type 선언 + Identity 표 + `### Service Type Composition`).
2. **Domain layer** (pure Java, no framework): `Account` aggregate + 상태기계 (`PENDING_KYC → ACTIVE → (RESTRICTED →) (FROZEN →) CLOSED`), `Money` VO (정수 minor-units 또는 `BigDecimal` — float/double 금지, F5), `Balance` (가용 vs 장부 분리, F2), `Hold` (capture/release), `Transaction` 상태기계 (`REQUESTED → VALIDATED → AUTHORIZED → SETTLED → COMPLETED` + `FAILED`/`REVERSED`, F3), KYC 단계 / AML 게이트 포트 (F4), 도메인 예외 (fintech Standard Error Codes).
3. **Application layer**: use case 서비스 + Command/Result, idempotency port (F1), outbox event port, audit_log port (F6). Command/Result 네이밍 (`{UseCase}Command`/`{UseCase}Result`).
4. **Infrastructure layer**: MySQL JPA adapters + Flyway `V1__init.sql` (finance_db), Redis idempotency store, OAuth2 Resource Server (GAP RS256 JWKS, `tenant_id=finance` fail-closed), 규제 PII 컬럼 암호화 (F7), audit_log append-only 저장.
5. **Presentation layer**: REST controllers (계좌 개설 / 조회 / hold / release / capture / 자금 이동) + Request/Response DTO + GlobalExceptionHandler (fintech error code → HTTP 매핑) + Idempotency-Key 헤더 처리.
6. **HTTP contract** — `specs/contracts/http/account-api.md` (엔드포인트 + Idempotency-Key + 에러 코드 표). **Contracts precede code** (CLAUDE.md Layer Rules) — contract 를 구현 전에 author/갱신.
7. **Event contract** (해당 시) — `specs/contracts/events/finance-account-events.md` (`finance.account.*` / `finance.balance.*` / `finance.transaction.*` envelope, libs/java-messaging 표준 shape).
8. **Dockerfile** (scm procurement-service Dockerfile 패턴; `account-service.jar`).
9. **Tests** — 테스트 피라미드 (`testing-backend` skill + platform/testing-strategy.md): domain unit (상태기계 전이 매트릭스 / Money VO / balance 불변식), application unit (`@ExtendWith(MockitoExtension.class)` STRICT_STUBS, Command 별 happy + edge), slice (`@WebMvcTest` + SecurityConfig + GlobalExceptionHandler), integration (`@SpringBootTest` + Testcontainers MySQL + Redis — H2 금지, multi-tenant isolation / idempotency / hold-capture-release / audit_log / state machine atomicity).
10. **docker-compose.yml 활성화** — gateway-service/account-service block 주석 해제 (account-service Dockerfile + Flyway schema 존재 후), Traefik `finance.local` label 활성화.
11. **settings.gradle / CI** — account-service 는 이미 settings.gradle 에 등록됨 (TASK-MONO-114); CI build matrix 에 account-service 가 path-filter 로 들어오는지 확인.

## Out of Scope

- `ledger-service` (복식부기 / GL / AP) — v2 (ADR-MONO-008 § D3).
- wallet-service / kyc-service / notification-service / admin-service — v2.
- 외부 은행·송금망·KYC 제공사 실제 통합 — v1 = 도메인 모델 + 멱등/감사 골격 + 포트 (실 adapter 는 v2 또는 별도 task).
- reconciliation 실 외부 연동 — F8 모델/큐는 forward-decl, 실 연동 v2.
- frontend — 통합 platform console 이 렌더 (ADR-MONO-013 §3.3).

# Acceptance Criteria

- **AC-1**: `specs/services/account-service/architecture.md` 가 구현 전에 존재 (Hexagonal, Service Type 선언, HARDSTOP-10 canonical form PASS).
- **AC-2**: `Money` 가 정수 minor-units 또는 `BigDecimal` 로만 표현 — `float`/`double` 금액 표현/계산 0건 (fintech F5). 통화 불일치 연산 `CURRENCY_MISMATCH`.
- **AC-3**: 자금 이동(hold/capture/release/transfer)이 client Idempotency-Key 로 멱등 — 동일 키 재요청은 최초 결과 반환, 자금 재이동 없음 (F1). 잔액 변경 + 상태 전이 + 이벤트 발행이 한 트랜잭션 (outbox).
- **AC-4**: 가용/장부 잔액 분리, 음수 가용 잔액으로의 이동 차단 (F2). SETTLED/COMPLETED 거래 immutable — 정정은 reversal 거래로만 (F3).
- **AC-5**: KYC 단계·AML 게이트가 자금 이동의 선행 조건 — 미충족 시 `KYC_*`/`AML_*`/`SANCTION_HIT` 거부 (F4).
- **AC-6**: 모든 자금·규제 영향 연산이 actor/timestamp/before/after 의 append-only audit_log 기록 (F6). 규제 PII 암호화 + 로그/에러 마스킹 (F7).
- **AC-7**: OAuth2 Resource Server — GAP RS256 JWKS 검증 + `tenant_id=finance` fail-closed (cross-tenant 403 `TENANT_FORBIDDEN`).
- **AC-8**: 테스트 피라미드 — domain/application unit + slice + Testcontainers IT (MySQL+Redis, H2 금지). CI green.
- **AC-9**: `specs/contracts/http/account-api.md` 가 구현과 일치 (contracts precede code). 추가 API 는 contract 에 정의된 것만.
- **AC-10**: `./gradlew :projects:finance-platform:apps:account-service:check` green. docker-compose `finance.local` Traefik 활성화 + backing service `expose:` only (Local Network Convention).

# Related Specs

- `rules/domains/fintech.md` (F1–F8 mandatory rules — governing 도메인 룰).
- `rules/traits/transactional.md`, `rules/traits/regulated.md`, `rules/traits/audit-heavy.md` (선언 trait — 있으면 로딩).
- `projects/finance-platform/PROJECT.md` (classification, Service Map v1, GAP integration, v1 IN/OUT slice).
- `projects/finance-platform/specs/integration/gap-integration.md` (OAuth2 RS + tenant gate).
- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` § D2/D3 (v1 = account-service; ledger = v2).
- `platform/architecture-decision-rule.md`, `platform/testing-strategy.md`, `platform/service-types/rest-api.md` (+ `event-consumer.md` if event-side included).
- precedent: `projects/scm-platform/specs/services/procurement-service/architecture.md` (Hexagonal shape reference).

# Related Contracts

- **신규 author**: `specs/contracts/http/account-api.md` (account-service HTTP API — 개설/조회/hold/release/capture/transfer + Idempotency-Key + 에러 코드 표). v1 첫 contract.
- **신규 author (event-side 포함 시)**: `specs/contracts/events/finance-account-events.md` (`finance.account.*`/`finance.balance.*`/`finance.transaction.*` — libs/java-messaging 표준 envelope; scm-procurement-events.md 패턴).
- **GAP-side (이미 존재, TASK-MONO-114 V0017)**: `finance-platform-internal-services-client` (client_credentials, scopes `finance.read`/`finance.write`) + `finance` tenant. 본 task 는 GAP contract 변경 없음 (consume only).
- platform/error-handling.md — fintech Standard Error Codes 등록 (account-service 가 emit 하는 코드).

# Edge Cases

- **금액 정밀도**: 모든 금액 연산이 정수 minor-units / `BigDecimal`. JSON 직렬화 시 float 변환 금지 (String 또는 정수). 통화별 minor-unit scale (KRW=0, USD=2) 명시.
- **Idempotency-Key 충돌**: 동일 키 + 다른 본문 → `IDEMPOTENCY_KEY_CONFLICT` (422). 동일 키 + 동일 본문 → 최초 결과 재반환 (재실행 금지).
- **Hold 만료**: 만료된 hold capture 시도 → `HOLD_ALREADY_SETTLED`. 만료 hold 는 자동 release (자금 가용 복귀) — sweep 정책 명시.
- **외부 자금 이동 불명확 응답**: 낙관적 성공 확정 금지 — 거래 미정 상태 유지 + reconciliation 수렴 (F8 forward-decl).
- **cross-tenant 토큰**: `tenant_id != finance` (그리고 `*` 아님) → 403 `TENANT_FORBIDDEN` fail-closed (gateway 미가동 시 service-level enforcer 가 가드).
- **MySQL/Hibernate 타입**: JSON 컬럼 (`@JdbcTypeCode(SqlTypes.JSON)`), `DECIMAL`/`BIGINT` 금액 컬럼 — Hibernate 6 바인딩 정합 (scm V1__init currency CHAR→VARCHAR / JSONB 교훈의 MySQL 대응).
- **audit_log append-only**: UPDATE/DELETE 금지 (트리거 또는 application 가드). 사후 수정 시도는 위반.

# Failure Scenarios

- **float/double 금액 누락 오차** → AC-2 reject. Mitigation: domain `Money` VO 강제, 정적 분석/리뷰 게이트, float 금액 필드 0건 grep.
- **idempotency 미보장 → 이중 인출** → AC-3. Mitigation: Idempotency-Key store (Redis + DB) + 트랜잭션 경계 단일성, IT 의 동시 재요청 시나리오.
- **KYC/AML 게이트 우회 경로** → AC-5. Mitigation: 자금 이동 진입점이 compliance 포트를 반드시 통과하는 단일 application 경로, 우회 경로 부재 IT.
- **확정 거래 in-place mutation** → AC-4. Mitigation: SETTLED/COMPLETED 불변 가드 + reversal-only 정정, 상태기계 전이 매트릭스 테스트.
- **audit_log 누락/수정** → AC-6. Mitigation: 자금·규제 연산 경로에 audit 기록 의무, append-only 저장 + 가드 IT.
- **architecture.md 미작성 후 구현** → HARDSTOP-09 hook block. Mitigation: AC-1 — spec 선행 (spec PR → impl PR 분리, tasks/INDEX.md PR Separation Rule).
- **`./gradlew` regression** → AC-10. Mitigation: scm procurement-service build.gradle 패턴 답습, skeleton 이 이미 `:tasks` resolves (TASK-MONO-114 검증).
- **GAP V0017 미적용 환경** → 토큰 발급 불가. Mitigation: GAP V0017 seed 가 TASK-MONO-114 에서 머지됨 (AC 의 GAP IT green 전제).

# Verification

1. `specs/services/account-service/architecture.md` 존재 + HARDSTOP-10 canonical form PASS (구현 commit 전).
2. `grep -rn 'float\|double' apps/account-service/src/main/java/.../domain` → 금액 표현 0건 (Money VO 만).
3. Idempotency-Key 동시 재요청 IT → 자금 1회만 이동.
4. cross-tenant 토큰 IT → 403 `TENANT_FORBIDDEN`.
5. `./gradlew :projects:finance-platform:apps:account-service:check` exit 0 + Testcontainers IT (MySQL+Redis) green.
6. `docker compose --project-directory projects/finance-platform config -q` valid + `finance.local` Traefik label 활성 + backing service host port 0건.
7. `specs/contracts/http/account-api.md` ↔ 구현 일치 (contract-first).
8. self-review APPROVED (review-checklist Spec/Arch/Quality/Security/Testing).
