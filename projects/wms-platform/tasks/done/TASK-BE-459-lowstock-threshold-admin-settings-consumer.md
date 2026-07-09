# Task ID

TASK-BE-459

# Title

저재고 임계값을 admin `admin.settings.changed` 이벤트로 라이브 반영 (inventory-service Kafka 소비자)

# Status

done

# Owner

backend

# Task Tags

- code

---

# Goal

`inventory-service` 의 저재고(low-stock) 임계값은 현재 `InMemoryLowStockThresholdAdapter`(순수 in-memory 맵)로만 공급되어, **운영자가 임계값을 바꾸려면 프로세스 재배포가 필요**하다(TASK-BE-024 가 admin-service 연동을 명시적으로 유예). 한편 `admin-service` 는 `admin.settings.changed` 이벤트(topic `wms.admin.settings.v1`)를 **이미 라이브로 발행**하고, `inventory.low_stock.default_threshold_qty`(GLOBAL, integer 0..100000, 기본 10) 설정 키가 seed 돼 있다. 그러나 inventory-service 는 admin 설정을 **하나도 소비하지 않는다**(settings 소비자 부재).

이 task 는 그 갭을 닫는다: inventory-service 가 `wms.admin.settings.v1` 을 **Kafka 로 소비**해 저재고 기본 임계값을 **라이브로 갱신**한다. 운영자가 admin 콘솔에서 임계값을 바꾸면 재배포 없이 저재고 감지에 반영된다.

**설계 결정 (사용자 확정 2026-07-09, Option B)**: **Kafka 이벤트 소비 + config-default 부트스트랩**. 재시작 시 임계값은 기존 `${inventory.alert.low-stock.default-threshold}` config 기본값으로 되돌아가고, 다음 `admin.settings.changed` 이벤트가 오면 다시 갱신된다. **재시작 내구성(운영자-설정 값이 재시작을 넘어 유지)은 명시적 유예** — 그것은 시작 시 admin-service HTTP 조회를 요구하는데, **WMS 는 서비스 간 HTTP 인증 메커니즘이 아직 없다**(`wms-internal-services-client` client_credentials 플로우는 `iam-integration.md` 에 문서만 있고 코드 미구현). 재시작 내구성(Option A)은 그 WMS s2s 인증 기반이 구축될 때의 후속 task 로 분리한다.

---

# Scope

## In Scope

- **신규 Kafka 소비자** `AdminSettingsConsumer`(`adapter/in/messaging/settings/`) — `@KafkaListener(topics="${inventory.kafka.topics.admin-settings:wms.admin.settings.v1}", groupId="${spring.kafka.consumer.group-id:inventory-service}")`, `@Profile("!standalone")`, `@Transactional` — 기존 소비자(`MasterSkuConsumer`) 패턴 미러: envelope 파싱 → `EventDedupePort.process(eventId, eventType, apply)` → apply. 공유 `KafkaConsumerConfig` 에러 핸들러(retry+DLT) 자동 적용.
- **envelope 파싱**: admin-events.md 글로벌 envelope(`eventId/eventType/occurredAt/aggregateType/aggregateId/payload`) 파싱. 기존 `MasterEventParser` 재사용 or 소형 `SettingsEventParser` 신설(payload 는 `{key,scope,warehouseId,valueJson,previousValueJson,version}`).
- **키 필터 + 적용**: `payload.key == "inventory.low_stock.default_threshold_qty"` 且 `scope == "GLOBAL"` 인 이벤트만 처리(다른 setting 키는 net-zero 무시). `valueJson`(정수) → **저재고 기본 임계값 갱신**. 그 외 키는 dedupe 기록 후 skip(향후 다른 소비 확장 가능).
- **threshold writer seam**: `LowStockThresholdWriterPort`(신규, `updateDefaultThreshold(Integer)`) 또는 기존 `InMemoryLowStockThresholdAdapter.setDefaultThreshold` 노출. `AlertConfig` 가 동일 adapter 인스턴스를 read(`LowStockThresholdPort`) + write 포트 양쪽 빈으로 제공(부트스트랩 config-default 는 기존 wiring 유지).
- **config**: `application.yml` `inventory.kafka.topics.admin-settings: wms.admin.settings.v1` 추가.
- **계약 갱신**: `specs/contracts/events/admin-events.md` §10 Consumer expectations 에 inventory-service 가 `inventory.low_stock.default_threshold_qty` 를 소비해 `LowStockThresholdPort` 갱신(config-default 부트스트랩·재시작 내구성 유예 명기) 항목 추가.
- **spec 갱신**: `specs/services/inventory-service/architecture.md` Event Consumption 표에 `admin.settings.changed / wms.admin.settings.v1` 행 추가 + config-default 부트스트랩·재시작 내구성 유예 note. **동기 HTTP 호출 없음**이므로 "does NOT call any other WMS service synchronously in v1" 서술은 참으로 유지(수정 불요).
- **테스트**: `AdminSettingsConsumer` 단위 테스트(설정 키 매칭 → 임계값 갱신 / 다른 키 무시 / dedupe 중복 skip / 파싱 실패 → non-retryable) + inventory IT(Testcontainers, 소비→감지 반영, CI Linux 권위).

## Out of Scope

- **재시작 내구성 / 시작 시 HTTP 조회(Option A)** — WMS s2s HTTP 인증 기반(`wms-internal-services-client`) 미구축이라 별도 선행 필요. 별도 backlog.
- **WMS 서비스 간 client_credentials 인증 플로우 구현** — 별건(foundational, ADR 감).
- **다른 setting 키 소비**(`inventory.reservation.ttl_hours` 등 — 계약상 약속됐으나 미구현) — 이 task 는 low-stock 키만. 확장 가능 seam 만 남김.
- **per-(warehouse,sku) 임계값 override** — seed 는 GLOBAL 단일. WAREHOUSE-scope override 는 별건(설정 seed 부재).
- **prod seed** — `inventory.low_stock.default_threshold_qty` 는 V99(dev-only) seed. prod seed 절차는 별건(전 setting 공통 이슈).

---

# Related Specs

> **Before reading**: `platform/entrypoint.md` — `PROJECT.md`(domain=wms, traits=[transactional, integration-heavy], service_types=[rest-api, event-consumer]) → `rules/common.md` + `rules/domains/wms.md`(있으면) + `rules/traits/transactional.md`(T8 dedupe) + `rules/traits/integration-heavy.md`(I3 retry/I5 DLQ). event-consumer service-type = `platform/service-types/event-consumer.md`.

- `specs/services/inventory-service/architecture.md`(Event Consumption·저재고 감지)
- `specs/contracts/events/admin-events.md`(§10 `admin.settings.changed`, 발행측 계약)
- `tasks/done/TASK-BE-024-adjustment-transfer-alert.md`(원 유예 근거)

# Related Contracts

- `specs/contracts/events/admin-events.md` (consumer expectation 추가)

# Target Service

- `inventory-service`(소비자 신설). admin-service = 발행측 무변경(이미 라이브).

---

# Edge Cases

- **부트스트랩 cold cache**: 재시작 직후 임계값 = config 기본값(운영자-설정 값 아님). 다음 이벤트까지 그 상태. **의도된 Option B 트레이드오프**(재시작 내구성 유예).
- **다른 setting 키 이벤트**: 같은 topic 에 `inventory.reservation.ttl_hours`·`inbound.*`·`outbound.*` 이벤트도 흐른다. low-stock 키 아닌 이벤트는 dedupe 기록 후 무시(에러 아님).
- **envelope 파싱 실패 / valueJson 비정수**: `IllegalArgumentException`(non-retryable) → DLT(기존 에러 핸들러). 정상 이벤트는 재시도 대상.
- **dedupe**: `eventId` 기준 T8 dedupe(기존 `EventDedupePort`) — 중복 재전달 idempotent.
- **standalone 프로파일**: 소비자 `@Profile("!standalone")` (기존 소비자와 동일) — standalone 에선 미기동.

---

# Failure Scenarios

- 키 필터 누락 → 무관 setting 이벤트가 임계값 오염. 완화: 정확 키+scope 매칭 단위 테스트.
- writer seam 이 read adapter 와 다른 인스턴스 → 갱신이 findThreshold 에 미반영. 완화: `AlertConfig` 가 동일 인스턴스를 양쪽 포트로 제공 + IT 로 소비→감지 왕복 검증.
- valueJson 파싱 오류가 retryable 로 분류되어 무한 재시도. 완화: 파싱 실패 = `IllegalArgumentException`(KafkaConsumerConfig 가 non-retryable → DLT).

---

# Test Requirements

- `AdminSettingsConsumerTest`(단위): low-stock 키 → 임계값 갱신 / 타 키 무시 / dedupe 중복 / 파싱 실패 non-retryable.
- inventory IT(Testcontainers): `admin.settings.changed`(low-stock, value=N) 소비 후 `LowStockThresholdPort.findThreshold` = N (CI Linux 권위).
- `:inventory-service:test` GREEN(단위/슬라이스, Docker-free).

---

# Definition of Done

- [ ] `AdminSettingsConsumer` + writer seam + config + 계약/spec 갱신
- [ ] 임계값 라이브 갱신(소비→감지 반영) IT GREEN
- [ ] `:inventory-service:test` GREEN + inventory Integration(Testcontainers, CI) GREEN
- [ ] Ready for review

---

# Provenance

Surfaced 2026-07-09 — 정식 ready 큐(BE-398/390 날짜게이트·MONO-328/330 신호게이트) 전량 착수 불가로 미티켓 백로그 3차원 발굴, code-marker 축이 이 갭을 REAL-GAP(module-liveness: `InMemoryLowStockThresholdAdapter` 미주입·admin 이벤트 라이브·no-covering-task)로 확증. 착수 전 landscape 매핑에서 **Option A(시작 시 HTTP 읽기)는 WMS s2s 인증 기반 부재로 foundational 선행 필요**가 드러나, 사용자가 **Option B(Kafka + config-default 부트스트랩, 자족적)** 로 확정 + 재시작 내구성 유예.

분석=Opus 4.8 / 구현 권장=Sonnet (event-consumer 패턴 미러 — mechanical; 키 필터·writer seam·계약/spec 갱신 주의).
