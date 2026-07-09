# Task ID

TASK-BE-488

# Title

이벤트 dedupe 무력화 버그 수정 — `repository.save(할당-@Id)` merge/upsert → 중복 재적용 (inventory-service + 형제 서비스 감사)

# Status

ready

# Owner

backend

# Task Tags

- code
- bug

---

# Goal

`inventory-service` 의 이벤트 중복제거(dedupe)가 **완전히 무력화**돼 있다. `EventDedupeRepositoryImpl.process` 는 다음 패턴을 쓴다:

```java
EventDedupeJpaEntity row = new EventDedupeJpaEntity(eventId, ...); // @Id = eventId (assigned)
repository.save(row);      // ← 버그
entityManager.flush();     // 중복이면 PK 위반을 기대(catch → IGNORED_DUPLICATE)
```

`EventDedupeJpaEntity` 는 `@Id` 가 **호출자가 할당한 non-null eventId** 이고 `Persistable`/`@Version` 을 구현하지 않는다. 이 경우 Spring Data `SimpleJpaRepository.save()` 는 엔티티를 "not new"(`id != null`)로 판정해 **`em.merge()`(upsert)** 를 호출한다. merge 는 **중복 eventId 에 대해 PK 위반을 던지지 않고 조용히 UPDATE** 한다 → `catch(DataIntegrityViolationException)` 이 걸리지 않음 → `work.run()` 이 **항상 실행** → 재전달 이벤트가 **재적용**된다.

**증거(CI, 결정론적)**: TASK-MONO-335 가 dormant IT 를 CI 에 배선하자 `PutawayCompletedConsumerIntegrationTest.redeliveryIsDeduped` 가 2 라운드 연속 `expected 50 but was 100`(같은 eventId 를 두 번 적용) 으로 실패. sentinel happens-before 로 재전달 소비를 보장한 결정론적 테스트에서도 재현 → 인프라 flake 아님, 실제 버그.

**영향**: `EventDedupePort.process` 를 쓰는 **모든 inventory-service 소비자**(`PutawayCompletedConsumer`, `PickingRequestedConsumer`, `PickingCancelledConsumer`, `ShippingConfirmedConsumer`, `MasterLocation/Sku/LotConsumer`, `AdminSettingsConsumer`) 가 재전달/재처리 시 이벤트를 중복 적용할 수 있다(T8 dedupe 불변 위반).

---

# Scope

## In Scope

- **`inventory-service` dedupe 수정**: `EventDedupeRepositoryImpl` 이 중복 eventId 에서 확실히 PK 위반을 일으켜 `IGNORED_DUPLICATE` 로 귀결되도록 한다. 후보:
  - (a) `EventDedupeJpaEntity implements Persistable<UUID>` + `isNew()`=true(append-only insert-once 이므로 항상 참) → `save()`=`persist()` → PK 위반. **주의**: 위반은 `save()` 가 아니라 이후 `entityManager.flush()`(raw EM) 에서 발생하며, raw EM 예외는 Spring 의 `DataIntegrityViolationException` 으로 **자동 번역되지 않을 수 있다** — flush 를 repository 프록시 경유로 바꾸거나(`repository.saveAndFlush` + 번역), `catch` 를 jakarta `PersistenceException`/Hibernate `ConstraintViolationException` 까지 넓히거나, 네이티브 `INSERT ... ON CONFLICT DO NOTHING` + 영향행수 판정으로 재설계. **예외 경로를 실제 CI IT 로 검증**(로컬 Windows Testcontainers 는 npipe 미감지로 skip → 권위 아님).
  - (b) 대안: 네이티브 upsert-없는 조건부 insert.
- **형제 서비스 감사**: 동일 `repository.save(할당-@Id) + flush` dedupe 패턴을 쓰는 서비스 점검 — 최소 `wms-platform` `outbound-service`(`EventDedupeRepositoryImpl`, `TmsRequestDedupeEntity`), `notification-service`(`AlertDedupeRepositoryImpl`, "inventory-service 학습" 주석). 이들의 중복-경로는 **단위 테스트(mock)로만** 덮여 실제 DB 재전달을 검증하지 않음 → 동일 버그 잠복 가능. 각 서비스에 **실제 DB 재전달 IT** 추가 + 필요 시 동일 수정.
- **타 프로젝트 확인(경량)**: `iam`/`ecommerce` 등에 동일 idiom 존재 여부 grep 수준 확인, 있으면 별도 task 발제(이 task 는 wms 범위).
- **테스트 재활성화**: `PutawayCompletedConsumerIntegrationTest.redeliveryIsDeduped` 의 `@Disabled("TASK-BE-488: real dedupe bug — see task")` 제거.

## Out of Scope

- TASK-MONO-335 가 이미 처리한 것(SecurityConfig 컨텍스트 로드, Instant 바인딩, append-only teardown TRUNCATE, transfer master-ref seed, CI 배선).
- `TransferStockService.resolveSameWarehouse` 의 미구현 fallback(코멘트 vs 코드 불일치) — 별건(정상 master-data-present 경로에선 도달 불가). 아래 Related 참조.

---

# Related Specs

> **Before reading**: `platform/entrypoint.md` — `PROJECT.md`(domain=wms, traits=[transactional, integration-heavy]) → `rules/traits/transactional.md`(**T8 dedupe/idempotency**) → event-consumer service-type.

- `rules/traits/transactional.md`(T8 — 이벤트 idempotency 불변)
- `specs/services/inventory-service/architecture.md`(Event Consumption / dedupe)
- `specs/contracts/events/inventory-events.md`(소비 이벤트 shape)

# Related Contracts

- 없음(내부 영속화 버그 수정, 계약 무변경).

# Target Service

- `inventory-service`(주). 감사 대상: `outbound-service`, `notification-service`(+ 발견 시 타 프로젝트는 별도 task).

---

# Edge Cases

- 재전달이 **원 처리 커밋 이전**에 도착(경합): 같은 파티션 key 순차 소비면 발생 불가하나, 소비자 concurrency>1 또는 무키 발행 시 가능 → 수정은 DB 유니크 제약(PK)에 의존해 경합에서도 정확해야 함(한 트랜잭션만 커밋, 나머지는 위반).
- `work` 가 던지면 dedupe row 도 롤백돼 재전달이 재시도 가능해야 함(기존 의도 유지).
- append-only dedupe 테이블에 update/delete 트리거가 있으면(향후) merge-UPDATE 자체가 트리거에 막혀 다른 실패로 나타날 수 있음 — insert-only 보장으로 회피.

# Failure Scenarios

- 수정 후에도 예외가 `catch` 로 안 잡히면(번역 경로 오판) → 재전달이 트랜잭션 롤백→재시도→DLT 로 흘러 조용한 중복 대신 시끄러운 실패가 됨. IT 로 `IGNORED_DUPLICATE`(조용한 skip) 경로를 명시 검증.
- 형제 서비스 감사에서 동일 버그 다수 발견 → 서비스별로 분할하거나 이 task 에서 일괄(원자적 lib 수준 공통화 여부 판단).

---

# Notes

- 발견 경위: TASK-MONO-335(dormant WMS integration suite 복구 + CI 배선) 중 노출. 사용자 결정(2026-07-09): MONO-335 는 해당 테스트를 `@Disabled` 로 격리하고 CI 배선을 완료, dedupe 프로덕션 버그는 본 전용 task 로 분리.
- 구현 권장 모델: **Opus**(프로덕션 correctness + 예외-번역 경로 + 크로스-서비스 감사).
