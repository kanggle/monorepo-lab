# Task ID

TASK-SCM-INT-001b

# Title

scm-platform e2e — SupplierCircuitBreaker 409 + WmsInventoryAdjusted snapshot=0 deeper investigation

# Status

ready

# Owner

backend / qa

# Task Tags

- test
- code

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Edge Cases
- Failure Scenarios

---

# Goal

[TASK-SCM-INT-001a](../review/TASK-SCM-INT-001a-e2e-environment-fixup.md) 가 6 fix 로 4/6 e2e scenario 를 회복했지만 **2건 잔존**:

- `SupplierCircuitBreakerE2ETest.supplierFailureStormOpensCircuitAndYields503` — 첫 `POST /api/v1/procurement/po` 응답이 **`409` instead of `201`**. 6 iteration 모두 첫 attempt 에서 fail.
- `WmsInventoryAdjustedConsumedE2ETest.crossProjectInventoryAdjustedFlowsToVisibilityReadModel` — Kafka publish 후 inventory-visibility 의 `totalQuantity` 가 **0.0 (expected 42.0)**. 001a 의 6번째 commit (`6ac01c5b`) 으로 topic 사전 생성했으나 효과 없음 — race 가 아닌 다른 cause.

본 task 는 두 잔존 fail 의 **deeper investigation + 별도 fix**. 두 root cause 가 분리됐다는 가정 하에 평행 진단.

선행: [TASK-SCM-INT-001a](../review/TASK-SCM-INT-001a-e2e-environment-fixup.md) 머지 후. 본 task 머지 시 PR #260 의 두 `@Disabled` 제거 + 6/6 PASS 회복이 최종 상태.

---

# Scope

## In Scope

### 1. Diagnostic logging 추가 (선제 조건)

두 시나리오 각각:
- `SupplierCircuitBreakerE2ETest`: 첫 draftResp 의 **응답 body 출력** (`System.err.println` 또는 logger). 409 의 error envelope (`error.code` + `error.message`) 가 root cause 의 1차 단서.
- `WmsInventoryAdjustedConsumedE2ETest`: inventory-visibility 컨테이너의 **Kafka consumer 로그 추출** (`@AfterEach` testFailed 에서 container.getLogs() — `WmsInventoryAdjustedConsumer` 가 message 를 받았는지 / dedupe 처리했는지 / DB write fail 했는지 확인).

### 2. SupplierCircuitBreaker 409 진단·fix

#### 의심 가설 (우선순위 순)

1. **Idempotency-Key 충돌**: procurement-service 의 idempotency cache (Redis) key 가 `(idempotencyKey)` 만이 아니라 다른 차원 포함이라 unique 보장 안 됨. 또는 Idempotency-Key header 형식 검증 fail (예: UUID 만 허용).
2. **PO aggregate state machine 의 conflict**: 동일 supplier + 동일 sku 조합이 이미 존재 → `ConflictException` (DRAFT 단계에서?). 비현실적이지만 가능.
3. **Validation fail → 400 이 아닌 409**: request body 의 `currency` / `unitPrice` / `lines.lineNo` 검증 실패가 409 로 mapping. exception handler 결함.
4. **Tenant/auth header**: `X-Tenant-Id` 가 누락되거나 token 의 tenant_id 와 mismatch → 보안 reject 가 401/403 이 아닌 409.

#### 진단 procedure

a. logging 추가 → cycle 1 → 응답 body 의 `error.code` 확인.
b. error.code 별 fix:
   - `IDEMPOTENCY_*` → idempotency 차원 분석 + test fixture 의 key generator 점검
   - `CONFLICT` (state machine) → DraftPoUseCase 의 invariant 점검
   - 기타 → exception handler 결함 fix

### 3. WmsInventoryAdjusted snapshot=0 진단·fix

#### 의심 가설 (우선순위 순)

1. **consumer 가 message 받았으나 처리 fail (silent swallow)**: `WmsInventoryAdjustedConsumer` 가 deserialize fail / EventDedupe race / JPA write fail 후 exception 만 log. consumer log 가 1차 단서.
2. **consumer subscription 미등록**: `@KafkaListener` 의 group.id / topic pattern mismatch. 부팅은 OK 지만 subscribe 안 됨.
3. **partition assignment race**: 001a 의 topic 사전 생성으로도 해소 안 된 race — consumer auto.offset.reset=earliest 인지 latest 인지 확인.
4. **inventory-visibility 의 GET endpoint 가 다른 view 반환**: snapshot 은 update 됐는데 GET endpoint 의 query 가 잘못된 dimension 으로 fold.

#### 진단 procedure

a. logging 추가 → cycle 1 → consumer log 에서 다음 패턴 검색:
   - `Subscribed to topic(s): ...wms.inventory.adjusted.v1` → subscription OK
   - `Consumed event eventId=...` 또는 비슷한 application log → consumer 가 process 시작
   - JdbcSQLException / `failed to update snapshot` → DB write fail
   - silent (no log at all) → subscribe 실패 또는 다른 group 으로 lost
b. 단서별 fix:
   - subscribe 안 됨 → application.yml 의 `spring.kafka.consumer.*` + `@KafkaListener.topics` mismatch fix
   - silent swallow → 예외 handler 점검 + retry/DLQ 설정
   - GET fold issue → `InventoryVisibilityQueryService` 의 query 점검

### 4. PR 동작

본 task 의 fix commit 들이 **두 `@Disabled` annotation 을 제거** + 새 cycle 6/6 PASS 검증. `TASK-SCM-INT-001a` 의 PR (#260) 머지 후 새 PR 로 진행 (본 task 는 새 branch).

## Out of Scope

- TASK-SCM-INT-001a 가 이미 fix 한 6 root cause 의 재변경.
- 다른 4 시나리오 (HappyPath / AsnReceive / SupplierAck / CrossTenant) 의 추가 검증.
- production code 의 새 기능 도입 — fix 만.
- TASK-SCM-INT-001 spec 자체의 deviation (CB cooldown / cross-tenant 401 / supplier JDBC seeding) 재논의.

---

# Acceptance Criteria

1. `SupplierCircuitBreakerE2ETest` `@Disabled` 제거 + 단독 PASS.
2. `WmsInventoryAdjustedConsumedE2ETest` `@Disabled` 제거 + 단독 PASS.
3. 본 task PR 의 self-CI `E2E (scm-platform v1 cross-service, Testcontainers)` job 6/6 PASS.
4. 다른 14 jobs 회귀 0.
5. 두 root cause 가 PR description 에 1-2 줄씩 명시 (재발 방지 reference).
6. 진단 logging 이 production code 에 추가됐다면 fix 후 적절히 정리 (e2e test 에 추가된 logging 은 보존 — 향후 회귀 가드).

---

# Related Specs

- [TASK-SCM-INT-001](../review/TASK-SCM-INT-001-procurement-inventory-visibility-e2e.md) — 6 시나리오 정의
- [TASK-SCM-INT-001a](../review/TASK-SCM-INT-001a-e2e-environment-fixup.md) — 6 fix 후 4/6 PASS 의 직접 선행
- `projects/scm-platform/specs/services/procurement-service/architecture.md` — DraftPoUseCase / Idempotency 패턴
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` — WmsInventoryAdjustedConsumer / cross-project event consumption
- `projects/scm-platform/specs/contracts/http/procurement-api.md` — POST /api/v1/procurement/po 의 409 가 valid 한지

---

# Target Service / Component

**Supplier 409**:
- `projects/scm-platform/apps/procurement-service/src/main/java/.../usecase/DraftPoUseCase.java` 또는 동등
- `projects/scm-platform/apps/procurement-service/src/main/java/.../adapter/inbound/web/advice/` (exception handler)
- `projects/scm-platform/tests/e2e/src/test/java/.../scenario/SupplierCircuitBreakerE2ETest.java` (logging + @Disabled 제거)

**WMS snapshot=0**:
- `projects/scm-platform/apps/inventory-visibility-service/src/main/java/.../adapter/inbound/messaging/WmsInventoryAdjustedConsumer.java` 또는 동등
- `projects/scm-platform/apps/inventory-visibility-service/src/main/resources/application.yml` (consumer config)
- `projects/scm-platform/tests/e2e/src/test/java/.../scenario/WmsInventoryAdjustedConsumedE2ETest.java` (logging + @Disabled 제거)

---

# Implementation Notes

- **답습 reference**:
  - SupplierCircuit 의 응답 body 추출: `objectMapper.readTree(draftResp.body())` 패턴 (이미 동일 클래스에서 사용).
  - WMS 의 consumer log 추출: SCM `ServiceContainerLogDumper` 가 testFailed 시 dump 하지만, **시나리오 통과 케이스에도 dump 하도록 임시 변경** (또는 `@AfterEach` 에서 강제 dump) — 진단 후 원복.
- **본 task 시작 시점**: TASK-SCM-INT-001a PR (#260) 머지 후. 본 task 는 새 branch + 새 PR.
- **production code 변경 정책**: 002b/002d/001a 동일 — small fix 만 별 commit + PR description 정당화.
- **timeout 늘리기 금지** — Awaitility 기본 windows 충분. fail 의 root cause 는 timing 이 아니라 logic.
- **두 fail 의 root cause 가 같을 가능성도 고려**: 예를 들어 둘 다 `application-default.yml` vs `application-e2e.yml` profile 누락이면 단일 fix 가능. cycle 1 의 logging 후 결정.

---

# Edge Cases

1. **두 fail 이 동일 root cause 로 환원**: 예 — `application-e2e.yml` profile 누락이라 e2e 환경에서 idempotency cache + Kafka consumer 둘 다 misbehave. 단일 fix 가능성 ↑, PR 더 단순.
2. **logging 추가 후에도 silent — 즉 application 자체가 message 를 못 받음**: Kafka topic 명에 `tenantId` prefix 같은 의도된 namespace 가 production 코드에 있고 e2e fixture 가 그걸 모름. consumer log 의 `Subscribed to topic(s)` 직접 비교 필요.
3. **409 가 valid behavior 일 수 있음**: 예 — production 의 PO 생성 endpoint 가 `Idempotency-Key` 를 require 하고 e2e 는 보냄, 그러나 server side 는 (Idempotency-Key + body hash) 로 cache key 를 만들어 다른 body 인데 같은 key 로 보낼 때 conflict. test 의 unique key 는 매번 다르지만 server 가 expect 와 다른 패턴. 이 경우 spec 수정도 후보.
4. **production code fix 가 architecture spec 변경을 동반할 가능성**: 만약 PO 생성 endpoint 의 409 mapping 이 결함이면 `procurement-api.md` § Error Codes 갱신 필수.

---

# Failure Scenarios

## A. logging 만으로는 root cause 불명

cycle 1 의 logging 추가 후에도 단서가 부족 → cycle 2 에서 더 invasive logging (예: WmsInventoryAdjustedConsumer 에 `@PostConstruct` log + 매 record onMessage log). production code 변경이지만 이번엔 진단 목적이라 정당화.

## B. 두 fail 이 production code 결함

각각 별 commit + PR description 정당화. 회귀 가드를 IT 에 추가하는 별 task 발행 권장 (예: `TASK-SCM-BE-005` IdempotencyConflictRegressionTest, `TASK-SCM-BE-006` WmsConsumerSubscriptionRegressionTest).

## C. 본 task 도 6 cycle 후 통과 안 됨

매 cycle ~6 min — 6 cycle ≈ 36 min. 그 시점이면 production code 의 deeper architectural 결함 가능성. TASK-SCM-INT-001 의 spec deviation 도 검토 (예: CB scenario 자체가 cross-project 이슈로 e2e 부적합 → IT 만으로 충분이라 시나리오 제거).

---

# Test Requirements

- 두 시나리오 각각의 logging 추가 PR (작은 commit) → cycle 1.
- root cause 별 fix → cycle 2 또는 3.
- 최종 cycle: `@Disabled` 제거 + 6/6 PASS.

---

# Definition of Done

- [ ] 두 시나리오의 root cause 가 PR description 에 명시
- [ ] 두 `@Disabled` annotation 제거
- [ ] PR self-CI 6/6 PASS
- [ ] 회귀 0 (다른 14 jobs)
- [ ] Production code fix 가 있다면 별 commit + 정당화
- [ ] 회귀 가드 IT 추가 권장 시 별 task 발행
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — Kafka consumer 동작 / Spring Boot bean lifecycle / Resilience4j idempotency 의 깊은 cross-cut 분석 필요.
- **분량 추정**: small-to-medium — 2 개 root cause 의 fix 자체는 작지만 진단 cycle 이 시간 비용. 최악 6 cycle (≈ 36 min CI 만).
- **dependency**:
  - `선행`: TASK-SCM-INT-001a (PR #260 머지).
  - `후속`: 본 task 머지 → 새 PR 머지 → review→done chore PR (TASK-SCM-INT-001 + TASK-SCM-INT-001a + TASK-SCM-INT-001b 묶음).
- **monorepo Phase 5 trigger**: 본 task 머지 시 verify-template-readiness exit 0 후보.
