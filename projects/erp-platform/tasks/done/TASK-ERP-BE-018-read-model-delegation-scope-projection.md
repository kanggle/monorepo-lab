# Task ID

TASK-ERP-BE-018

# Title

**read-model-service delegation scope projection — project `scope`/`scopeRequestId` onto `delegation_fact_proj` (BE-017 의 read-model 짝).** TASK-ERP-BE-017 이 `erp.approval.delegated.v1` payload 에 `scope`(GLOBAL|REQUEST) + `scopeRequestId` 를 **producer-only forward interface** 로 추가했으나 현재 어떤 consumer 도 project 하지 않는 **recorded forward gap** 상태. 이 증분이 read-model 의 `DelegationFactEventConsumer`(기존, byte-unchanged 구독) 가 그 두 필드를 `delegation_fact_proj` 에 투영하도록 확장 → "이 위임이 전체 위임인가 특정 건 한정인가" 가 통합조회에 노출. **scope 는 grant-time 불변 메타데이터** — `delegated` 이벤트만 싣고 `revoke` 는 안 실으므로 `validFrom`/`validTo` 와 **동일 패턴**: `applyGrant` 가 (terminal 행이어도) 무조건 set, `applyRevoke` 는 보존, sticky-terminal 은 `status` 에만. revoke-before-grant out-of-order 행은 scope 미상(NULL, 미조작 E5) → 나중 grant 가 채움. **V4 마이그레이션이 `ck_delegation_fact_proj_scope` CHECK(값집합, NULL 허용) 선반영**(§16). 신규 토픽/이벤트 0 — 기존 `delegated` payload 의 가산 필드 소비.

# Status

done

# Owner

backend-engineer (dispatched, model=opus — projection sticky-terminal × immutable-metadata 상호작용; dispatcher 독립 재검증)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api
- event
- test

---

# Dependency Markers

- **consumes (producer)**: TASK-ERP-BE-017 (`erp.approval.delegated.v1` payload 에 `scope`/`scopeRequestId` 가산, main `dbb1cd97`; producer-only forward interface — read-model/notification 이 unknown-field tolerant 로 무시 중). 이 task 가 그 forward gap 을 닫는 read-model consumer.
- **builds on / mirror**: TASK-ERP-BE-015 (`delegation_fact_proj` + `DelegationFactEventConsumer`/`DelegationFactProjection`/`ApplyDelegationFactUseCase`/`DelegationEnvelopeToCommandMapper`/`QueryDelegationFactUseCase`/`DelegationFactController` + V3). 이 task = scope 필드 가산(projection 패턴 답습; ACTIVE/REVOKED sticky-terminal 로직 byte-unchanged).
- **realises**: approval-service architecture.md § v2.3 amendment "read-model + notification ignore the new fields … until TASK-ERP-BE-018 (read-model `delegation_fact_proj.scope`)". erp.md E5 (terminal consumer, no re-emit) + T8 (dedupe).
- **forward (NOT here)**: 콘솔 scope 카드 표시 = **TASK-PC-FE-056** (이 task 가 노출하는 read-model `scope` 를 소비).
- **decision (user, 2026-06-06)**: 다음 작업 = BE-018 read-model scope projection.
- [[feedback_spring_boot_diagnostic_patterns]] §16 (DB CHECK on STRING enum) + §17 (MySQL CHECK 위반 → `UncategorizedSQLException`, IT 단언은 `DataAccessException`+제약명) + §14 (Testcontainers IT 권위).

# Goal

위임 통합조회(`delegation_fact_proj`)가 각 grant 의 scope(전체 GLOBAL vs 특정 request REQUEST + 대상 requestId)를 read-only 로 노출. BE-017 의 producer-only forward 필드를 소비해 forward gap 종결. terminal consumer(재발행 0, E5).

# Scope

## In Scope (read-model-service only)

- **`DelegationFactProjection`** (domain): 신규 필드 `scope`(String; GLOBAL|REQUEST|null) + `scopeRequestId`(String; nullable).
  - `ofGranted(...)` 가 `scope`/`scopeRequestId` 수용.
  - `ofRevoked(...)` 는 scope=null/scopeRequestId=null (revoke 페이로드에 scope 없음 — 미조작 E5; 나중 grant 가 채움).
  - **`applyGrant(...)` 가 scope/scopeRequestId 를 무조건 set** (grant-time 불변 메타데이터 — `validFrom`/`validTo` 와 동형; sticky-terminal 은 `status` 에만 적용하므로 REVOKED 행이어도 scope 는 갱신). out-of-order revoke-before-grant: revoke 가 scope=null 행 생성 → 후속 grant 가 status 는 안 건드리고(REVOKED 유지) scope 만 채움.
  - **`applyRevoke(...)` 는 scope/scopeRequestId 미변경** (revoke 는 scope restate 안 함 — `validFrom`/`validTo` 보존과 동형).
  - accessor `scope()`/`scopeRequestId()` 추가.
- **`DelegationFactCommand`** (application): 신규 필드 `scope`/`scopeRequestId` (granted 이벤트에서만 non-null; revoke → null).
- **`DelegationEnvelopeToCommandMapper`**: granted(status==ACTIVE) 일 때만 `payloadString("scope")`/`payloadString("scopeRequestId")` 추출, revoke → null. 기존 envelope validity/tenant 검증 byte-unchanged.
- **`ApplyDelegationFactUseCase`**: `ofGranted`/`applyGrant`/`ofRevoked` 호출에 scope 인자 전달(로직 분기 byte-unchanged — dedupe/sticky-terminal/out-of-order 동일).
- **`DelegationFactProjJpaEntity`**: `scope`(`@Column(length=16)`, nullable) + `scopeRequestId`(`@Column(name="scope_request_id", length=64)`, nullable).
- **repository impl (entity↔domain 매핑)**: 양방향 scope/scopeRequestId 매핑.
- **`DelegationFactResponse`** (web dto): `scope`(NON_NULL — revoke-only 행에서만 ABSENT) + `scopeRequestId`(NON_NULL — GLOBAL/미상 시 ABSENT).
- **V4 마이그레이션** `V4__delegation_scope_proj.sql`: `ALTER TABLE delegation_fact_proj ADD COLUMN scope VARCHAR(16) NULL, ADD COLUMN scope_request_id VARCHAR(64) NULL` + `ADD CONSTRAINT ck_delegation_fact_proj_scope CHECK (scope IS NULL OR scope IN ('GLOBAL','REQUEST'))`. **§16**: 값집합 DB 고정(NULL 허용 — revoke-only 행은 scope 미상). coherence CHECK 는 두지 않음(read-model 은 producer-검증된 데이터를 투영; revoke-only 행은 자연히 scope=null/scopeRequestId=null, GLOBAL 은 scopeRequestId=null, REQUEST 는 set — producer 가 이미 보장).
- **contracts/specs**: read-model-subscriptions.md(§ Delegation — scope/scopeRequestId 투영 노트) + read-model-api.md(delegation fact 응답에 scope/scopeRequestId) + read-model architecture.md(amendment).

## Out of Scope

- **콘솔 scope 카드 표시** → TASK-PC-FE-056.
- **scope 필터** (`?scope=`/`?scopeRequestId=` 쿼리) — 노출만, 필터는 후속(필요 시 PC-FE-056 와 함께). 기존 org_scope/activeAt 필터 byte-unchanged.
- approval-service / notification-service 변경.
- sticky-terminal/out-of-order/dedupe ACTIVE/REVOKED 로직 변경(byte-unchanged — scope 필드만 가산).
- revoke 페이로드 scope (producer 가 안 실음).

# Acceptance Criteria

- [ ] **AC-1** `erp.approval.delegated.v1` (scope=REQUEST, scopeRequestId=R1) 소비 → `delegation_fact_proj` 행 scope=REQUEST + scope_request_id=R1. scope=GLOBAL → scope=GLOBAL + scope_request_id NULL.
- [ ] **AC-2** GET delegation facts 응답이 scope(+REQUEST 시 scopeRequestId) 노출; GLOBAL 은 scopeRequestId ABSENT(NON_NULL); revoke-only 행은 scope ABSENT.
- [ ] **AC-3** scope 는 불변 메타데이터: revoke-before-grant out-of-order — revoke 가 scope=NULL 행 생성 → 후속 grant(scope=REQUEST) 가 status 는 REVOKED 유지하며 scope 만 REQUEST 로 채움(applyGrant unconditional). 정상 순서(grant→revoke): grant 가 scope set, revoke 가 보존.
- [ ] **AC-4** 기존 ACTIVE/REVOKED sticky-terminal + dedupe(T8) + org_scope + activeAt 동작 byte-unchanged(기존 unit/IT 통과).
- [ ] **AC-5** terminal consumer: 재발행/outbox 0(grep). 신규 토픽/이벤트 0.
- [ ] **AC-6** V4 가 `delegation_fact_proj` 에 scope/scope_request_id + `ck_delegation_fact_proj_scope`(NULL 또는 GLOBAL/REQUEST) 추가. `:read-model-service:check` GREEN(unit/slice). Testcontainers IT(CI Linux): scope 투영 + CHECK 거부(scope='BOGUS' → 거부) + out-of-order scope fill. H2 forbidden.

# Related Specs

- `specs/services/read-model-service/architecture.md` (delegation projection amendment). erp.md E5/T8. ADR-MONO-005 Category C(consumer resilience).

# Related Contracts

- consume: `erp-approval-events.md` `erp.approval.delegated.v1` § v2.3 (scope/scopeRequestId; BE-017 발행).
- subscriptions: `read-model-subscriptions.md` § Delegation (scope/scopeRequestId 투영 노트).
- serve: `read-model-api.md` delegation fact 응답 + scope/scopeRequestId.

# Edge Cases

- revoke-before-grant: scope NULL → 후속 grant 가 채움(status REVOKED 유지). grant 영영 안 옴: scope NULL 영속(미상; 응답 ABSENT) — 정직(미조작 E5).
- GLOBAL grant: scope=GLOBAL, scopeRequestId NULL.
- REQUEST grant: scope=REQUEST, scopeRequestId 존재.
- 중복(at-least-once): dedupe(eventId) skip — scope 재기록 없음.
- 미래 scope 값(producer 가 마이그레이션 없이 새 값 발행): DB CHECK 거부(§16; IT 적발) — 단 NULL 은 허용(revoke-only).
- 기존 V3 행(이 마이그레이션 이전 grant): scope NULL backfill(미상) — 응답 ABSENT, 정직.

# Failure Scenarios

- mapper 가 revoke 에서도 scope 추출 시도 → revoke 페이로드에 없어 null → 정상(의도). granted 에서만 추출.
- applyGrant 가 scope 를 sticky-terminal 가드 안에 넣으면(잘못) → revoke-before-grant 행이 scope 영영 NULL → 응답 부정확. Guard: scope 는 status 가드 **밖**에서 무조건 set(validFrom/validTo 와 동일 위치). unit 으로 검증.
- V4 CHECK 누락 → 잘못된 scope 값 투영 가능. Guard: `ck_delegation_fact_proj_scope` + IT 거부 테스트(§16). IT 단언은 `DataAccessException`+제약명(§17 — MySQL CHECK=HY000→UncategorizedSQLException, DataIntegrityViolationException 아님).
- 재발행 유혹 → E5 위반(no outbox); grep 게이트.

# Test Requirements

- **unit**: `DelegationFactProjectionTest`(ofGranted scope; applyGrant unconditional set on terminal row[revoke→grant out-of-order scope fill, status REVOKED 유지]; applyRevoke preserves scope; GLOBAL→scopeRequestId null, REQUEST→set). `DelegationEnvelopeToCommandMapperTest`(granted payload scope/scopeRequestId 추출; revoke→null). `ApplyDelegationFactUseCaseTest`(granted REQUEST 투영; revoke-before-grant 후 grant fill). `DelegationFactControllerSliceTest`(응답 scope; GLOBAL scopeRequestId `.doesNotExist()`, REQUEST present).
- **IT** (`DelegationFactProjectionIntegrationTest`, Testcontainers, CI Linux 권위 §14): (a) delegated REQUEST → 행 scope=REQUEST + scopeRequestId; GLOBAL → scope=GLOBAL scopeRequestId NULL; (b) out-of-order revoke→grant: 최종 행 status=REVOKED + scope=REQUEST; (c) CHECK 거부 — native insert scope='BOGUS' → 거부(`DataAccessException` + `.hasMessageContaining("ck_delegation_fact_proj_scope")` §17). H2 forbidden.
- 기존 read-model unit/IT(approval-fact + delegation-fact ACTIVE/REVOKED/org_scope) 회귀 통과.
- `:read-model-service:check` GREEN. publish/outbox grep 0. V4 CHECK 명시.

# Definition of Done

- [ ] `DelegationFactProjection`(scope/scopeRequestId; applyGrant unconditional, applyRevoke preserve) + `DelegationFactCommand` + mapper + UseCase + JpaEntity + repo 매핑 + `DelegationFactResponse`.
- [ ] V4 마이그레이션(`ck_delegation_fact_proj_scope` NULL|GLOBAL|REQUEST).
- [ ] ACTIVE/REVOKED/sticky-terminal/dedupe/org_scope byte-unchanged(회귀 통과).
- [ ] terminal consumer(grep 0) + 신규 토픽 0.
- [ ] spec/contract amendment.
- [ ] `:check` GREEN; Testcontainers IT GREEN(scope 투영 + out-of-order fill + CHECK 거부).
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Opus (projection sticky-terminal × grant-time-immutable-metadata 상호작용 — applyGrant 가 status 가드 밖에서 scope 무조건 set 하는 미묘함; dispatcher 독립 재검증). 사용자 "BE-018 read-model scope projection" 선택. 메타: ① **BE-017 producer-only forward 필드의 소비** — forward gap 종결(BE-013→014→015 패턴의 scope 판). ② **scope = grant-time 불변** → `validFrom`/`validTo` 와 동형 처리(applyGrant unconditional, applyRevoke preserve, sticky-terminal 은 status 만) — out-of-order revoke-before-grant 가 scope 를 잃지 않게. ③ **V4 CHECK 선반영(§16) + IT 단언 §17**(MySQL CHECK→UncategorizedSQLException, DataAccessException+제약명) — BE-017 의 CI #1 RED 반복 회피. ④ read-model CHECK 는 값집합만(NULL 허용 — revoke-only 행 scope 미상; coherence 는 producer 가 보장). [[project_monorepo_template_strategy]] [[feedback_spring_boot_diagnostic_patterns]] [[project_platform_console_adr_013]]
