# Task ID

TASK-BE-045

# Title

admin-service Spring Boot bootstrap — Layered (deliberate exception) module + write-side aggregates (User / Role / Assignment / Setting) + outbox + JWT/security wiring + REST write paths (v1 minimal slice)

# Status

ready

# Owner

backend

# Task Tags

- code
- spec

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`apps/admin-service/` 디렉토리는 v1 published portfolio 부터 placeholder 였음 (`project_wms_v1_published.md` § "다음 권장: notification + admin 부트스트랩"). [TASK-BE-044](../done/TASK-BE-044-admin-service-spec-open-items-closure.md) 가 8 Open Items 모두 close 하여 spec 입력 준비 완료. 본 task 는 그 spec 위에서 admin-service 의 **첫 production-shaped slice** 를 부트스트랩한다.

**구체 목표**: Layered architecture 예외 (rules 의 Hexagonal preference 에 대한 declared override — `architecture.md § Architecture Style`) 모듈 골격 + 4 write-side aggregate (User / Role / UserRoleAssignment / Setting) + 그 aggregate 의 mutation 경로가 outbox 를 통해 `wms.admin.{user,role,assignment,settings}.v1` 토픽으로 1 transaction 안에서 publish 될 때까지 (T3). REST 측면에서는 `admin-service-api.md` § 2-5 (User / Role / Assignment / Settings) 의 write 경로만 완성한다.

**Out of scope (TASK-BE-046 분리)**: § 1 Dashboard / Read-Model 14 source-topic projection consumer + 12 read-side table + ops 엔드포인트는 **별 task** 로 분리. 본 task 는 write-side 만 다뤄 PR 크기를 sibling bootstrap (BE-021 / BE-029 / BE-034 / BE-043) 수준으로 유지한다.

---

# Scope

## In Scope

### 1. Spring Boot 모듈 부트스트랩

- `apps/admin-service/build.gradle` — sibling 패턴 답습 (`master-service` 가 가장 가까움 — REST + write-side 위주). Spring Web, Spring Security, Spring Data JPA, Spring Kafka (outbox publisher 만 — consumer 는 BE-046), Postgres, Flyway, libs:java-messaging, libs:java-common, libs:java-test-support, testcontainers, awaitility, MockMvc.
- `apps/admin-service/src/main/java/com/wms/admin/AdminServiceApplication.java`
- `apps/admin-service/src/main/resources/application.yml` — REST + Postgres + Flyway + Kafka producer + JWT issuer (GAP, ADR-001 OIDC) + Spring Security
- `apps/admin-service/src/main/resources/application-test.yml` — `${random.uuid}` consumer group-id (BE-046 가 활성화될 때를 대비한 선제적 패턴)
- `apps/admin-service/Dockerfile` — sibling 패턴
- `projects/wms-platform/docker-compose.yml` — `admin_db` user 추가 (`docker/postgres/init.sh`), `admin-service` 는 host JVM `bootRun` 모델 (sibling 동일)
- 루트 `package.json` — `wms:admin:*` 단축 (sibling 패턴: check / bootrun / bootjar / it)
- `.github/workflows/ci.yml` — Build & Test (JDK 21, Linux) + Package boot jars (wms) + Integration (master-service or 별 wms-admin job? — sibling 답습 결정) 에 admin-service 추가

### 2. Layered 패키지 구조 (architecture.md § Package Structure 그대로)

```
com.wms.admin/
├── api/                         # REST controllers
│   ├── user/                    # UserController + dto/request, dto/response
│   ├── role/                    # RoleController, AssignmentController
│   └── settings/                # SettingsController
├── application/
│   ├── user/                    # UserService, AssignmentService
│   ├── role/                    # RoleService
│   └── settings/                # SettingsService
├── domain/                      # 단순 POJO — User, Role, UserRoleAssignment, Setting + state machines + invariants
├── infra/
│   ├── persistence/             # JPA entity + repository
│   └── security/                # JWT decoder + Spring Security wiring + role-based authz
└── config/
```

**dashboard / readmodel / 14 topic consumer 패키지는 본 task 미생성** — TASK-BE-046 에서 추가.

### 3. Domain layer (write-side only)

`domain-model.md` § 1-4 그대로:

- `User` aggregate + state machine (`ACTIVE ↔ INACTIVE` only — T4) + invariants (email unique, user_code immutable, default_warehouse_id soft-ref)
- `Role` aggregate + invariants (role_code unique/immutable, permissionsJson 검증, 4 built-in `WMS_*` 보호)
- `UserRoleAssignment` aggregate + 상태 (`ACTIVE | REVOKED` — REVOKED terminal) + invariants (user/role active 시점 grant, `(user_id, role_id, warehouse_id)` 유일 active)
- `Setting` aggregate + invariants (key + scope + warehouse_id immutable, `value_json` 이 `schema_json` 만족)
- `AdminDomainException` sealed + subtype: `USER_EMAIL_DUPLICATE`, `ROLE_CODE_DUPLICATE`, `USER_HAS_ACTIVE_ASSIGNMENTS`, `ROLE_IN_USE`, `ROLE_BUILTIN_IMMUTABLE`, `SETTING_VALIDATION_ERROR`, `SETTING_IMMUTABLE_FIELD` (이미 [`platform/error-handling.md § Admin`](../../../../../platform/error-handling.md) 등록됨, BE-044 추적)

### 4. Application layer

- `UserService` — create / update / deactivate (force flag for `WMS_SUPERADMIN`) / reactivate. 모두 `@Transactional`, outbox row 1 TX 안. force=true cascade 시 `admin.assignment.revoked` per assignment + `admin.user.deactivated` 1 TX.
- `RoleService` — 동일 패턴. built-in role 보호 (`ROLE_BUILTIN_IMMUTABLE`).
- `AssignmentService` — grant (idempotent: 이미 active 면 200 + existing) / revoke (terminal).
- `SettingsService` — upsert (스키마 검증 + outbox `admin.settings.changed`).

각 service 는 `@PreAuthorize` 또는 application-layer authz 검증 (`architecture.md § Security` — controller 가 아닌 application 에서 enforce).

### 5. Infra layer

- JPA entities + repositories (write-side 4 + outbox + dedupe — dedupe 는 BE-046 의 consumer 가 사용하지만 V1 마이그레이션에 포함). **JSONB 컬럼은 모두 `@JdbcTypeCode(SqlTypes.JSON)`** — TASK-SCM-INT-001b root cause #2 + BE-005 + BE-043 회귀 가드 학습 답습.
- `OutboxAdapter` (libs/java-messaging 패턴 — sibling outbox 와 동일. **단** notification-service 처럼 service-local 채택 가능, 결정 시점은 outbox 스키마 호환성 평가 후. 1차 시도 = libs subclass).
- `admin-outbox-polling-scheduler` (libs concrete subclass).
- `JwtDecoder` GAP JWKS 기반 RS256 검증 (`projects/wms-platform/specs/integration/gap-integration.md` 패턴). `tenant_id=wms` claim 강제.
- Spring Security: `SecurityConfig` 에서 `@EnableMethodSecurity(prePostEnabled = true)` + role hierarchy (`WMS_SUPERADMIN > WMS_ADMIN > WMS_OPERATOR > WMS_VIEWER`).

### 6. Flyway

- `V1__init.sql` — `domain-model.md § Persistence Layout` 그대로:
  - `admin_user` (write)
  - `admin_role` (write)
  - `admin_user_role_assignment` (write)
  - `admin_setting` (write)
  - `admin_outbox` (T3)
  - `admin_event_dedupe` (T8 — BE-046 consumer 가 사용하지만 본 V1 에서 미리 생성)

  **모든 JSONB 컬럼 (e.g., `admin_setting.value_json`, `admin_setting.schema_json`, `admin_role.permissions_json`, `admin_outbox.payload`) 명시 + 인덱스 + unique constraints**.

- `V99__seed_dev_data.sql` (profile `dev` / `standalone` 한정, `domain-model.md § Reference Data Snapshot`):
  - 4 built-in `WMS_*` 역할 (default `permissions_json`)
  - 1 seed `admin@wms.internal` 사용자 + `WMS_SUPERADMIN` global assignment
  - `domain-model.md § 4 Seed Settings (v1)` 4 setting key

### 7. REST controllers (write 경로만)

`admin-service-api.md` § 2-5 그대로:

- `UserController` — POST / GET / PATCH / `/deactivate` / `/reactivate` (§ 2.1-2.6)
- `RoleController` — POST / GET / PATCH / `/deactivate` / `/reactivate` (§ 3.1-3.6)
- `AssignmentController` — POST / GET / DELETE (§ 4.1-4.3)
- `SettingsController` — GET / PUT (§ 5.1-5.3)

각 mutating endpoint:
- `Idempotency-Key` 헤더 강제 (`platform/api-gateway-policy.md` 패턴)
- Redis idempotency cache (`admin:idempotency:{method}:{path_hash}:{key}`, 24h TTL — `idempotency.md § 1` 그대로)
- `If-Match: "v{version}"` 선택 — 명시 시 fast-fail
- 응답 본문 / 에러 envelope `platform/error-handling.md` 준수

`/api/v1/admin/dashboard/**` + `/api/v1/admin/operations/**` 은 본 task 미구현 (BE-046).

### 8. Tests (≥ 50 — sibling bootstrap 평균 답습)

- **Unit (≥ 30)**:
  - `User` state machine + invariants (≥ 8): ACTIVE↔INACTIVE only, email validation, force-deactivate cascade rule
  - `Role` invariants (≥ 6): code unique/immutable, permissionsJson 검증, 4 built-in 보호
  - `UserRoleAssignment` invariants (≥ 6): grant idempotent + REVOKED terminal + 부모 active 검증
  - `Setting` invariants (≥ 6): immutable fields, `value_json` ↔ `schema_json` 검증
  - `AdminOutboxWriter` / `EventEnvelopeSerializer` (≥ 4): payload 직렬화 + partition key
- **Application slice (port fakes / repo fakes)**:
  - `UserService` happy + `USER_EMAIL_DUPLICATE` + `USER_HAS_ACTIVE_ASSIGNMENTS` + force cascade revoke
  - `RoleService` happy + `ROLE_CODE_DUPLICATE` + `ROLE_BUILTIN_IMMUTABLE` + `ROLE_IN_USE`
  - `AssignmentService` grant idempotent + revoke + state validation
  - `SettingsService` upsert + `SETTING_VALIDATION_ERROR` + `SETTING_IMMUTABLE_FIELD`
- **Persistence adapter (Testcontainers Postgres)**:
  - 4 write 테이블 round-trip
  - JSONB 컬럼 `@JdbcTypeCode(SqlTypes.JSON)` 회귀가드 (BE-043 패턴 답습)
  - V1 + V99 seed 검증
- **REST controllers (`@WebMvcTest` + `@MockitoBean`)**:
  - `admin-service-api.md` § 2-5 의 모든 endpoint × happy + 주요 error
  - Authorization: `WMS_VIEWER` / `WMS_OPERATOR` / `WMS_ADMIN` / `WMS_SUPERADMIN` 별 허용/거부 surface
  - `Idempotency-Key` 멱등성 (replay = cached 200, body diff = 409 `DUPLICATE_REQUEST`)
- **Outbox integration (Testcontainers Postgres + Kafka)**:
  - `UserService.create` → `wms.admin.user.v1` 에 `admin.user.created` event 도달 (≥ 4 이벤트 타입)
  - 동일 TX 안 outbox row 검증

### 9. Spec / contracts cross-link

- 본 PR 은 spec 변경 0 (admin-service spec 4 file + error codes 모두 PR #273 머지 완료).
- impl PR 본문에 `admin-service-api.md` / `admin-events.md` / `idempotency.md` cross-link.

## Out of Scope (별 task)

- **TASK-BE-046 (read-model + dashboard)**:
  - 14 source-topic `@KafkaListener` consumer (master 6 + inbound 3 + outbound 2 + inventory 7 = 14 topic — admin-events.md § Consumed Events)
  - 12 read-side table Flyway V2 마이그레이션
  - Read-model projection services (one per source service for clarity)
  - `/api/v1/admin/dashboard/**` REST endpoints (`admin-service-api.md` § 1)
  - `/api/v1/admin/dashboard/alerts/{id}/acknowledge` (read-model write 1건)
  - `/api/v1/admin/operations/projection-status`
  - `read-model-rebuild` runbook 실행 검증

- 후속 v2: 승인 워크플로 (Domain 로직 비-trivial 추가 시 Layered → Hexagonal 재평가 — `architecture.md § Architecture Style § expiry` 참고), multi-tenant, SSO/SCIM sync, time-series read-model

---

# Acceptance Criteria

- [ ] AC-01 — `apps/admin-service/` 모듈 부트스트랩 (build.gradle / Application / application.yml / Dockerfile / docker-compose entry / package.json shortcuts / ci.yml 편입). `./gradlew :projects:wms-platform:apps:admin-service:bootJar` SUCCESS.
- [ ] AC-02 — Layered 패키지 구조 (`api / application / domain / infra / config`) 적용. dashboard / readmodel 패키지 미생성 (BE-046 분리 명시).
- [ ] AC-03 — 4 write-side aggregate + state machine + invariants 구현. AdminDomainException sealed + 7 subtype.
- [ ] AC-04 — Flyway V1 적용 시 6 테이블 (`admin_user` / `admin_role` / `admin_user_role_assignment` / `admin_setting` / `admin_outbox` / `admin_event_dedupe`) 생성. V99 seed (4 built-in role + 1 seed user + 4 default setting) 적용.
- [ ] AC-05 — JSONB 컬럼 모두 `@JdbcTypeCode(SqlTypes.JSON)` 회귀가드 자동 검증 (BE-043 패턴).
- [ ] AC-06 — `admin-service-api.md` § 2-5 endpoints 구현. Idempotency-Key 멱등성 보장 (Redis 24h TTL).
- [ ] AC-07 — write-side mutation 시 outbox row + Kafka publish 1 TX. `admin.user.created/updated/deactivated/reactivated` + `admin.role.*` + `admin.assignment.granted/revoked` + `admin.settings.changed` (총 ≥ 9 이벤트 타입).
- [ ] AC-08 — Spring Security 4 role authz: `WMS_VIEWER` (no write) / `WMS_OPERATOR` (no admin write) / `WMS_ADMIN` (write 가능, force=false) / `WMS_SUPERADMIN` (force=true override). application-layer enforcement (controller 아닌).
- [ ] AC-09 — 테스트 ≥ 50 (Unit ≥ 30 + slice + persistence + REST + outbox integration). 로컬 PASS.
- [ ] AC-10 — CI Build & Test (JDK 21, Linux) + Package boot jars (wms) job 에 admin-service 편입 + 회귀 0.
- [ ] AC-11 — 다른 wms service 회귀 0 (`master-service` / `inventory-service` / `inbound-service` / `outbound-service` / `gateway-service` / `notification-service`).
- [ ] AC-12 — D4 churn freeze 면제 카테고리만 변경 — `apps/admin-service/**` (project-internal, 신규 모듈 wiring 면제) + `.github/workflows/ci.yml` + root `package.json` + `docker-compose.yml` (모두 신규 모듈 wiring, freeze 면제).

---

# Related Specs

- [`projects/wms-platform/specs/services/admin-service/architecture.md`](../../specs/services/admin-service/architecture.md) — **authoritative**, Layered 예외 declaration
- [`projects/wms-platform/specs/services/admin-service/domain-model.md`](../../specs/services/admin-service/domain-model.md) — write-side aggregate + 4 table + outbox + dedupe
- [`projects/wms-platform/specs/services/admin-service/idempotency.md`](../../specs/services/admin-service/idempotency.md) — REST 24h Redis (write-side만 본 task)
- [`projects/wms-platform/PROJECT.md § Overrides`](../../PROJECT.md) — admin-service Layered 예외 mirror
- [`projects/wms-platform/specs/integration/gap-integration.md`](../../specs/integration/gap-integration.md) — JWT issuer / JWKS / `tenant_id=wms` claim
- [`projects/wms-platform/tasks/done/TASK-BE-044-admin-service-spec-open-items-closure.md`](../done/TASK-BE-044-admin-service-spec-open-items-closure.md) — 선행

# Related Contracts

- [`projects/wms-platform/specs/contracts/http/admin-service-api.md`](../../specs/contracts/http/admin-service-api.md) — REST § 2-5 (write 경로). § 1 dashboard / § 6 ops 는 BE-046.
- [`projects/wms-platform/specs/contracts/events/admin-events.md`](../../specs/contracts/events/admin-events.md) — publish § 1-10. consumed events (read-model projection) 는 BE-046.
- [`platform/error-handling.md § Admin [domain: wms]`](../../../../../platform/error-handling.md) — 11 코드 (PR #273 등록).

---

# Edge Cases

- **Force-deactivate cascade**: User 또는 Role 의 force-deactivate 시 active assignment 전부 cascade-revoke. 1 TX 안 outbox 에 `admin.user.deactivated` (1) + `admin.assignment.revoked` (N) 모두 publish. 분기 시점은 `force=true && caller hasRole(WMS_SUPERADMIN)`. 그 외엔 `USER_HAS_ACTIVE_ASSIGNMENTS` / `ROLE_IN_USE` 422.
- **Built-in role 보호**: `WMS_VIEWER` / `WMS_OPERATOR` / `WMS_ADMIN` / `WMS_SUPERADMIN` 은 `permissions_json` 만 PATCH 가능, deactivate / 삭제 불가 (`ROLE_BUILTIN_IMMUTABLE`). seed 시점에 `is_builtin=true` 컬럼 또는 hardcoded code 매칭으로 분기.
- **Idempotent grant**: 동일 `(user_id, role_id, warehouse_id)` active assignment 가 이미 있으면 새 row 생성 안 함, 기존 row 200 응답 (`DUPLICATE_REQUEST` 아님). `Idempotency-Key` 다르고 body 동일이면 cached response 가 아닌 fresh return — Redis 가 expired 됐을 때 안전망.
- **Setting `value_json` 검증**: `schema_json` 이 JSON Schema draft-07 fragment. validator 는 `com.networknt:json-schema-validator` 또는 동등 라이브러리. 검증 실패 시 `SETTING_VALIDATION_ERROR` 400.
- **Optimistic locking**: `If-Match` 헤더 선택 — 미제공 시 version-checked UPDATE 만 안전망. 응답에 항상 `version` 포함.

# Failure Scenarios

- **Outbox publish 시 RDB transaction 실패**: rollback 으로 outbox row + aggregate row 동시 unwritten. T3 보장.
- **Kafka producer 장애**: outbox row 만 commit, polling scheduler 가 재시도 (libs/java-messaging 패턴). API 응답은 200/201 — eventual delivery.
- **Redis idempotency cache 장애**: API filter 가 fail-open (architecture.md `idempotency.md § 1.4` 정책). 단, 단기 race window 에서 동일 key 의 두 mutation 이 모두 실행될 수 있음 — 도메인 unique constraint (`USER_EMAIL_DUPLICATE` / `ROLE_CODE_DUPLICATE`) 가 안전망.
- **JWT 검증 실패** (`tenant_id != wms` 또는 만료): 401 `UNAUTHORIZED` (`platform/error-handling.md`).
- **Built-in role seed 시점 충돌**: V99 seed 가 dev/standalone profile 에서만 실행. prod 에서는 별 manual procedure (또는 별 V_seed_prod migration). 본 task 는 dev/standalone 가정.
- **읽기 endpoint 누락 영향**: dashboard endpoint 는 본 task 미구현 → admin UI 가 v1 부터 요청해도 404. 사용자 contract 상 dashboard = BE-046 분리이며 BE-045 머지만으로 admin UI launch 못 함을 명시.

---

# Notes

- **모델 권장**: 분석=Opus 4.7 / 구현=Opus 4.7 — Layered 예외 architecture 디자인 + dual service-type wiring 의 절반 (rest-api, event-consumer 는 BE-046) + 4 write aggregate + outbox + 4-tier security 매트릭스 = complex domain work. Sonnet 으로 진행 시 built-in role 보호 / force cascade / idempotent grant 같은 invariant subtle 누락 위험.
- **PR 분할 가이드**: BE-045 = bootstrap + write-side (예상 ~50 file, BE-043 와 비슷한 크기). BE-046 = read-model + dashboard (예상 ~60 file, 14 consumer + 12 read table + dashboard REST + ops). 둘 다 일주일 내 종결 가능.
- **D4 churn freeze 면제 근거**: `apps/admin-service/**` 신규 모듈 wiring + sibling 답습. `.github/workflows/ci.yml` + root `package.json` + `docker-compose.yml` 변경은 신규 모듈 등록 카테고리 (TASK-MONO-040 / TASK-BE-043 선례 동일). shared 영역 (`libs/`/`platform/`/`rules/`/`.claude/`) 변경 0 가 AC-12.
- **연관 메모리**: `project_wms_v1_published` (admin placeholder 상태), `project_master_service_v1_design` (Layered architecture 가 깨는 sibling Hexagonal preference 의 declared override 패턴), `project_046_series_close` (sibling bootstrap 패턴 참조 가능 — 4 PR 시리즈로 종결).
- **선행/후속 task 관계**:
  - 선행 = TASK-BE-044 (PR #273 admin-service spec 8/8 close)
  - 후속 = TASK-BE-046 admin-service-readmodel-projection (별 spec PR 로 발행 권장)
