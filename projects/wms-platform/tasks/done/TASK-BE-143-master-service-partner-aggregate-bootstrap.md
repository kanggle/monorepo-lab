# Task ID

TASK-BE-143

# Title

master-service Partner aggregate minimal v1 bootstrap — producer-side 누락 closure

# Status

done

# Owner

backend

# Task Tags

- code
- api
- event

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

# Goal

master-service 의 Partner aggregate producer-side 를 minimal v1 수준으로 implementation. TASK-BE-142 audit (Branch B = 누락 확정) 의 후속.

**현재 spec-code drift 상태**:

| 영역 | 상태 |
|---|---|
| `architecture.md` § Responsibility (line 32) | Partner 명시 (6 aggregate 중 하나) |
| `domain-model.md` § 5 (line 218~) | Partner aggregate **완전 정의** (partner_code 20 / partner_type enum SUPPLIER/CUSTOMER/BOTH / state machine) |
| `master-events.md` (architecture.md line 165) | `master.partner.created/.updated/.deactivated` topic `wms.master.partner.v1` 명시 |
| master-service `MasterOutboxPollingSchedulerTest:35` | `master.partner.created` → topic 매핑 검증 (**scaffolding 흔적만**) |
| master-service 실제 producer 자산 | **전부 부재** (domain/port/service/controller/persistence/Flyway 모두) |
| downstream consumer (inbound/outbound/admin) | **production code** — `PartnerSnapshot` + `MasterPartnerProjector` + read-model entity + repository |
| downstream actual usage | inbound `ReceiveAsnService:69` `findPartner(supplierPartnerId)` 필수 검증 / outbound `ReceiveOrderService:74,120` `validateCustomerPartner` 필수 검증 |

**Production risk**: master-service 가 publisher 가 아니므로 dev seed (`V99__seed_dev_masterref.sql`) 외에서 PartnerSnapshot 이 채워질 경로 없음. dev 환경 외 (staging/prod) 에서 ASN/Order 처리 시 `findPartner` 영원히 empty → 검증 실패.

본 task 가 closure 하는 것: master-service 의 Partner aggregate 를 v1 production 수준으로 완성. Lot.supplier_partner_id 의 hard FK 검증은 **v2 별도 task** (domain-model.md line 310 의 "v1 no hard FK to Partner" 명시 유지).

---

# Scope

## In Scope

### Domain layer

- `domain/model/Partner.java` (final POJO, framework-free):
  - fields: `id` (UUID), `partnerCode` (String, immutable, 20 char), `name` (100), `partnerType` (enum SUPPLIER/CUSTOMER/BOTH), `email` (255 nullable), `phone` (30 nullable), `status` (`MasterEntityStatus` 또는 `WarehouseStatus` — dry-run B2 finding 미해결이므로 본 task 는 기존 `WarehouseStatus` 재사용), `version`, `createdAt`/`createdBy`/`updatedAt`/`updatedBy`
  - static factory `Partner.create(...)` + `Partner.reconstitute(...)`
  - `applyUpdate(UpdatePartnerCommand cmd)` + `rejectImmutableChange` (partnerCode immutable)
  - state transitions: `deactivate()` / `reactivate()` (common state machine)
  - invariants: partner_code globally unique (`PartnerCodeDuplicateException` — new), pattern validation (optional), partner_type 변경 가능
- `domain/event/PartnerCreated.java` / `PartnerUpdated.java` / `PartnerDeactivated.java` / `PartnerReactivated.java` (record domain events)
- `domain/exception/PartnerCodeDuplicateException` + `PartnerNotFoundException` (필요시 `ReferenceIntegrityViolationException` 재사용)

### Application layer

- `application/port/in/CrudPartnerUseCase` + `PartnerQueryUseCase` (또는 단일 합본):
  - `create(CreatePartnerCommand) → PartnerResult`
  - `update(UpdatePartnerCommand) → PartnerResult`
  - `deactivate(UUID id, long version) → PartnerResult`
  - `reactivate(UUID id, long version) → PartnerResult`
  - `findById(UUID id) → PartnerResult`
  - `findByCode(String partnerCode) → PartnerResult`
  - `list(ListPartnersQuery) → PageResult<PartnerResult>`
- `application/port/out/PartnerPersistencePort` (findById / findByCode / existsByCode / findPage / save / update)
- `application/service/PartnerService` (`@Service`, `@Transactional`):
  - `@PreAuthorize` 3-role (MASTER_READ / MASTER_WRITE / MASTER_ADMIN) — 형제 service 와 동일 패턴
  - **`AggregateVersionGuard` 활용** (TASK-BE-141 utility) — 5 service 의 패턴 재사용
  - 도메인 이벤트 발행 via `OutboxDomainEventPortAdapter` (이미 generic 으로 작동)
- `application/command/`: `CreatePartnerCommand` / `UpdatePartnerCommand` (record)
- `application/query/`: `ListPartnersQuery` + `PartnerListCriteria` (TASK-BE-141 dry-run B3 의 wrapper pattern 따름 — 추후 일괄 정리)
- `application/result/PartnerResult.java` (record, `from(Partner)` factory)

### Adapter (inbound — web)

- `adapter/in/web/controller/PartnerController.java`:
  - `POST /api/v1/master/partners` (MASTER_WRITE, Idempotency-Key)
  - `PATCH /api/v1/master/partners/{id}` (MASTER_WRITE, Idempotency-Key)
  - `POST /api/v1/master/partners/{id}/deactivate` (MASTER_ADMIN, Idempotency-Key)
  - `POST /api/v1/master/partners/{id}/reactivate` (MASTER_ADMIN, Idempotency-Key)
  - `GET /api/v1/master/partners/{id}` (MASTER_READ)
  - `GET /api/v1/master/partners?code=&type=&status=&page=&size=` (MASTER_READ)
- `adapter/in/web/dto/request/`: `CreatePartnerRequest` / `UpdatePartnerRequest`
- `adapter/in/web/dto/response/`: `PartnerResponse`

### Adapter (outbound — persistence)

- `adapter/out/persistence/entity/PartnerJpaEntity.java` (package-private, `@Entity` + `@Version`)
- `adapter/out/persistence/repository/JpaPartnerRepository.java` (extends `JpaRepository`)
- `adapter/out/persistence/mapper/PartnerPersistenceMapper.java` (package-private)
- `adapter/out/persistence/adapter/PartnerPersistenceAdapter.java` (implements `PartnerPersistencePort`)

### Migration

- `src/main/resources/db/migration/V7__init_partner.sql`:
  ```sql
  CREATE TABLE partners (
    id UUID PRIMARY KEY,
    partner_code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    partner_type VARCHAR(10) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(30),
    status VARCHAR(10) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    updated_by VARCHAR(64) NOT NULL
  );
  CREATE INDEX idx_partners_partner_type ON partners(partner_type);
  CREATE INDEX idx_partners_status ON partners(status);
  ```
- `src/main/resources/db/seed/V103__seed_dev_partners.sql` (dev 시드 — SUPPLIER 1 + CUSTOMER 1 + BOTH 1 sample). 기존 inbound/outbound 의 `V99__seed_dev_masterref.sql` 의 partner row 와 partner_code 정합.

### Spec 정합 (Open Items closure)

- `specs/contracts/http/master-service-api.md`: § 5 Partner endpoints 존재 확인 → 부재면 추가 / 존재면 implementation 과 정합 검증
- `specs/contracts/events/master-events.md`: Partner 이벤트 schema 존재 확인 → 부재면 추가 / 존재면 정합 검증
- `specs/services/master-service/architecture.md` § Open Items 의 "Register new error codes" 항목에 `PARTNER_CODE_DUPLICATE` 추가
- `platform/error-handling.md` 의 새 에러 코드 등록

### Test

- `PartnerTest` (domain unit, 모든 invariant + factory + 상태 전이) — 형제 aggregate test 답습 (~30 method)
- `PartnerServiceTest` (application service, in-memory `FakePartnerPersistencePort`) — happy path + 모든 도메인 error (~15 method)
- `PartnerPersistenceAdapterTest` + `PartnerPersistenceAdapterH2Test` (Testcontainers Postgres + H2) — 형제 aggregate 양식 답습
- `PartnerControllerTest` (`@WebMvcTest`, role matrix + Idempotency-Key + DTO validation)
- contract test: `partner-response.schema.json` + 4 event schemas
- `MasterOutboxPollingSchedulerTest` 의 Partner topic 매핑 검증 라인 그대로 작동 (기존 scaffolding test 통과)

## Out of Scope

- **Lot.supplier_partner_id 의 hard FK 검증 활성화** — `domain-model.md` line 310 "v1 no hard FK to Partner; supplier_partner_id is a soft reference" 유지. v2 별도 task.
- inbound/outbound 의 `V99__seed_dev_masterref.sql` 의 Partner seed 제거 (현재는 dev fallback, master-service publisher 작동 후 자연 deprecated).
- `MasterEntityStatus` rename (dry-run B2 finding) — 별도 평가.
- ERP/PIM 외부 동기화 (`SkuSyncPort` 같은 외부 어댑터) — architecture.md § Extensibility Notes 의 v2 영역.
- inbound/outbound 의 `MasterPartnerProjector` 변경 — 이미 production 동작, master-service publisher 작동 시 자연 정상 흐름.

---

# Acceptance Criteria

- [ ] `Partner` domain model + 4 domain events + 2 exception 추가
- [ ] application/port/in + port/out + service + command + query + result 신규
- [ ] `PartnerController` 6 endpoint (`POST`/`PATCH`/`POST :deactivate`/`POST :reactivate`/`GET {id}`/`GET ?...`) 동작
- [ ] persistence adapter (Postgres + H2 양쪽 PASS)
- [ ] Flyway V7 + dev seed V103
- [ ] master-service 가 outbox 통해 `master.partner.*` event 발행 (기존 `MasterOutboxPollingScheduler` topic 매핑 활용)
- [ ] downstream `MasterPartnerProjector` 가 master-service 발행 event 를 정상 consume (e2e 확인 — IT 가 가능)
- [ ] spec 정합:
  - `master-service-api.md` § 5 Partner endpoints 정합 (없으면 추가)
  - `master-events.md` Partner schema 정합 (없으면 추가)
  - `architecture.md` § Open Items 의 Partner 관련 항목 closure (status 갱신)
- [ ] `PARTNER_CODE_DUPLICATE` 에러 코드 `platform/error-handling.md` 등록
- [ ] `./gradlew :master-service:test` PASS — 신규 ~50-70 test, 기존 587 → 640+
- [ ] sibling regression 0 (inbound/outbound/admin 의 PartnerSnapshot consumer 동작 유지)
- [ ] BE-142 audit 의 결정 (Branch B 누락 closure) 명시

---

# Related Specs

- `specs/services/master-service/architecture.md` — § Responsibility, § Open Items, § Persistence, § Event Publication, § Concurrency Control, § Idempotency, § Testing Requirements
- `specs/services/master-service/domain-model.md` § 5 Partner — fields, invariants, state
- `specs/contracts/http/master-service-api.md` § 5 Partner — endpoint contract
- `specs/contracts/events/master-events.md` — Partner event schemas
- `rules/domains/wms.md` — Master Data bounded context
- `rules/traits/transactional.md` — T1-T8 (idempotency-key, optimistic lock, outbox)
- `platform/error-handling.md` — error code registration

# Related Skills

- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/implement-task`

---

# Related Contracts

- `specs/contracts/http/master-service-api.md` § 5 Partner
- `specs/contracts/events/master-events.md` Partner section
- `apps/master-service/src/test/resources/contracts/events/` — partner event JSON schemas (4건)

---

# Target Service

- `master-service`

# Indirect Verification (regression-only)

- `inbound-service` (consumer side, `MasterPartnerProjector` + `ReceiveAsnService`)
- `outbound-service` (consumer side, `MasterPartnerProjector` + `ReceiveOrderService`)
- `admin-service` (read-model projection, `PartnerRefEntity`)

---

# Architecture

Follow:

- `specs/services/master-service/architecture.md` — Hexagonal (Ports & Adapters)
- 형제 aggregate (Warehouse / Zone / Location / SKU / Lot) 패턴 1:1 답습
- **`AggregateVersionGuard` utility 활용** (TASK-BE-141 의 산출물)

---

# Implementation Notes

- 형제 aggregate (특히 Sku — 가장 유사한 단순 CRUD aggregate + business event) 의 풀 구조 1:1 답습이 권장. file 단위 mirror 패턴.
- `MasterOutboxPollingScheduler` 가 이미 `master.partner.*` event 의 topic 매핑을 알므로 producer-side 만 발행하면 자동 작동. `MasterOutboxPollingSchedulerTest:35` 가 이를 검증.
- dev seed 시 partner_code 가 inbound/outbound 의 `V99__seed_dev_masterref.sql` 의 partner_code 와 정합해야 dev e2e 가 작동.
- contact info (email/phone) 는 `PROJECT.md data_sensitivity: internal` + architecture.md § Security 의 "operational contact data, not consumer PII" 정책 명시. PII 처리 정책 별도 적용 안 됨.

---

# Edge Cases

- partner_code 중복 → `PartnerCodeDuplicateException` (HTTP 409)
- partner_type enum 외 값 → `VALIDATION_ERROR` (HTTP 400)
- email/phone null OK (operational 영역, 검증은 format 만 — optional)
- deactivate 시 W6 cross-aggregate 검증 — domain-model.md line 244 "Deactivation blocked if any ACTIVE Lots list this partner as supplier (v1: no such link)" → **v1 은 local check 0** (Lot.supplier_partner_id hard FK 활성화가 v2)
- 같은 partner_code 의 SUPPLIER → CUSTOMER 변경 — partner_type 가변 가능 (immutable 아님)

---

# Failure Scenarios

- Flyway V7 migration 실패 (table 존재 충돌) — staging/prod 첫 deployment 시 partners table 부재 가정. dev 환경에서 cleanup 필요.
- downstream `MasterPartnerProjector` 가 영원히 empty 였던 환경 (dev seed 없는 staging) 에서 master-service 가 발행한 PartnerCreated 가 처음 들어오면 PartnerSnapshot 채워짐. ASN/Order 검증 정상화.
- 기존 dev seed 의 partner_code 와 master-service v1 first POST 의 partner_code 충돌 가능 — V103 dev seed 가 inbound/outbound `V99__seed_dev_masterref.sql` 의 partner row 와 같은 partner_code 사용 권장.

---

# Test Requirements

- domain unit: `PartnerTest` (~30 method) — factory, immutability, state machine, validation
- application slice: `PartnerServiceTest` (in-memory fake port, ~15 method)
- persistence: `PartnerPersistenceAdapterTest` (Testcontainers) + `PartnerPersistenceAdapterH2Test`
- REST: `PartnerControllerTest` (WebMvcTest, role + Idempotency-Key + validation)
- contract: 4 event schema validation + HTTP response schema
- outbox: master-service 가 발행한 event 가 outbox row 에 정상 등재되는 IT (기존 형제 outbox IT pattern)
- e2e (optional): downstream `MasterPartnerProjector` consume IT (cross-service, CI integration job)

---

# Definition of Done

- [ ] Implementation completed (domain + application + adapter + migration)
- [ ] Tests added (unit + slice + persistence + REST + contract)
- [ ] Tests passing (`./gradlew :master-service:test` 587 → 640+)
- [ ] sibling regression 0 (`:inbound-service:test`, `:outbound-service:test`, `:admin-service:test`)
- [ ] Spec drift 0 (architecture.md / domain-model.md / master-service-api.md / master-events.md 정합)
- [ ] `platform/error-handling.md` 에 `PARTNER_CODE_DUPLICATE` 등재
- [ ] Lot.supplier_partner_id 의 v1 soft reference 명시 유지 (hard FK = v2)
- [ ] downstream e2e 정상 (CI Linux Integration job)
- [ ] Ready for review

---

# Notes

- 분석=Opus 4.7 / 구현=Opus 4.7 (large bootstrap task, dispatch 권장)
- 본 task 는 TASK-BE-142 audit 의 결정 closure (Branch B = 누락). 본 task ready 진입 게이팅 = TASK-BE-142 의 done 이동
