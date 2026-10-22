# Task ID

TASK-BE-644

# Title

admin-service 재고 읽기 모델의 `lowStockFlag` 가 저재고 설정(`inventory.low_stock.default_threshold_qty`)을 무시하고 상수 10 을 쓴다 — 계약은 «현재 설정으로 재계산» 이라 적는다 · 곁: `domain-model.md` 의 «Mirrors Order status» 드리프트

# Status

review

# Owner

backend

# Task Tags

- code
- test
- spec

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (프로젝션 · 설정 소비 · 재계산 범위 판정)
> 기안 2026-10-22 UTC — `TASK-PC-FE-364` 측정의 곁 발견 + `TASK-PC-FE-362` 의 스펙 드리프트 관찰(소유자 «진행», 2026-10-22 KST). **ready 만 — 착수는 소유자 지시 때.** 서버 변경 ⇒ 배포는 **굽기**.

# Dependency Markers

- 짝: `projects/platform-console/tasks/ready/TASK-PC-FE-365-*.md`(콘솔의 저재고 설명을 사실대로 — 이 티켓이 바꾸면 그 문구도 따라간다).
- 선례: `TASK-BE-459`(done — inventory-service 가 같은 설정을 `admin.settings.changed` 로 소비해 **알림** 임계값을 라이브로 바꾼다).

# Goal

- 계약 `specs/contracts/http/admin-service-api.md:281-282` 는 `lowStockFlag` 가 «현재 `inventory.low_stock.default_threshold_qty` 설정으로 재계산» 된다고 적는다.
- 실제 코드: `apps/admin-service/.../application/projection/InventoryProjectionService.java:54-55` `DEFAULT_LOW_STOCK_THRESHOLD = 10` (주석 «v1 default») · `:341` `boolean lowStock = availableQty <= DEFAULT_LOW_STOCK_THRESHOLD;`
- 결과: 운영자가 콘솔 WMS 운영 설정에서 임계값을 25 로 바꾸면 **inventory-service 의 저재고 알림은 25 기준**(BE-459)으로 오는데, **콘솔의 «저재고» 표시 · `lowStockOnly` 필터 · 통합 개요 «저재고 재고 보기»** 는 계속 10 기준이다 — 같은 개념이 두 기준으로 갈린다.
- 곁(문서만): `specs/services/admin-service/domain-model.md:379` 의 «Mirrors Order status» 는 코드와 다르다 — 출고 주문 요약은 상태를 `RECEIVED` · `SHIPPED` · `CANCELLED` 셋으로 접는다(`OrderSummaryEntity.java:123,128,133`; 콘솔 `overview-state.ts:195-197` 이 이미 적음). `TASK-PC-FE-362` 가 이 드리프트 때문에 계획을 바꿨다.

# Scope

## In Scope

- **AC-0 측정** (file:line):
  - ① 계약 · 도메인 모델 · 코드의 세 진술 대조(`admin-service-api.md:281-282` · `domain-model.md:221, 435` · 위 코드).
  - ② admin-service 가 설정 변경을 알 수 있는 경로 — 자기 설정 표(설정의 주인)를 프로젝션 시점에 읽을 수 있는가 · 이벤트를 자기 자신이 소비해야 하는가.
  - ③ **재계산 범위**: 설정이 바뀌면 기존 스냅샷 행의 `lowStockFlag` 를 일괄 재계산해야 하는가(계약 «recomputed» 의 뜻) — 행 수 · 비용 · 다음 재고 이벤트까지 기다리는 대안.
  - ④ 알림(inventory-service)과 표시(admin)의 기준이 같은 값을 보게 되는 순서 · 경합.
- **소유자 결정 (AC-0 뒤 선택창)**: (가) 설정을 따르게 고친다(추천 — 계약대로) / (나) 상수를 계약에 명시하고 계약 문장을 고친다(설정은 알림 전용).
- 스펙 먼저: 결정에 맞게 계약 · 도메인 모델을 고치고, `domain-model.md:379` 드리프트도 «세 상태로 접는다» 로 바로잡는다(이유 · 출처 보존).
- 구현 · 시험(설정 변경 → 플래그 재계산 · 경계값 · 설정 없음 = 기본 10 · 잘못된 값 무시).

## Out of Scope

- SKU 별 · 창고별 임계값(지금 설정은 GLOBAL 하나).
- 콘솔 문구(`TASK-PC-FE-365`).

# Acceptance Criteria

- [x] **AC-0** — 위 ①~④. (아래 `## AC-0 측정`)
- [x] **AC-1** — 소유자 결정(가/나) 선택창 · 스펙 먼저. ((가-2) · 경계 `<` · 칼럼 제거 — 아래 `### 소유자 결정`; 스펙은 별도 커밋으로 먼저)
- [x] **AC-2** — 구현 + 시험 · CI 의 wms 잡 초록. (구현 + 로컬 `admin-service:check` 초록 완료 · **남은 것**: Testcontainers IT 는 이 호스트에 Docker 가 없어 CI 전용 · CI 미실행 — 아래 `## 구현`)
- [ ] **AC-3** — 굽기 뒤 라이브: 설정을 바꾸면 콘솔 저재고 필터 결과가 따라 바뀌는가(다음 데모 창).

# Related Specs

- `projects/wms-platform/specs/services/admin-service/domain-model.md` (§ settings · § 저재고 · `:379`)
- `projects/wms-platform/specs/services/admin-service/architecture.md`

# Related Contracts

- `projects/wms-platform/specs/contracts/http/admin-service-api.md` (`:281-282` — 갱신 대상)
- `projects/wms-platform/specs/contracts/events/admin-events.md` (`:343` — `admin.settings.changed` 소비자 기대)

# Edge Cases

- 설정이 없거나(초기 기동) 범위 밖이면 기본 10.
- 설정 변경과 재고 이벤트가 동시에 오면 — 순서 무관하게 최종 상태가 맞아야 한다.

# Failure Scenarios

1. 플래그만 새 값으로 계산하고 기존 행은 그대로 둬, 필터 결과가 «바뀐 행 + 옛 행» 으로 섞인다 → AC-0 ③.
2. 계약 문장만 고치고 콘솔 문구(365)를 안 맞춘다 → 짝 티켓.

---

## AC-0 측정 (2026-10-22 UTC)

> 코드·스펙 읽기로 잰 것(브랜치 `task/be-644-impl`, base `origin/main` `02329254b`). 런타임 실측 아님 — 행 수는 시드·시드 스크립트에서 센 **추정**이다.

**① 세 진술 대조** — 계약·도메인 모델은 «현재 설정», 코드만 상수.

| 출처 | 진술 |
|---|---|
| `specs/contracts/http/admin-service-api.md:281-282` | «recomputed at projection time using the current `inventory.low_stock.default_threshold_qty` setting» |
| `specs/services/admin-service/domain-model.md:429` | «Set when `available_qty ≤ threshold` (from Settings); updated on each event» |
| `specs/services/admin-service/domain-model.md:434-436` | 계약과 같은 문장(«recomputed at projection time using the current Setting») |
| `specs/services/admin-service/domain-model.md:221` | 시드 기본값 `10` |
| `apps/admin-service/.../application/projection/InventoryProjectionService.java:54-55` | `DEFAULT_LOW_STOCK_THRESHOLD = 10` — 주석은 «recomputed at projection time» 이라 적지만 설정을 안 읽는다 |
| 같은 파일 `:341` | `boolean lowStock = availableQty <= DEFAULT_LOW_STOCK_THRESHOLD;` |

- 상수의 내력: `tasks/done/TASK-BE-299-admin-mixed-hygiene.md:46,67` 가 리터럴 `<= 10` 에 이름을 붙였을 뿐(«주석 보존»), 설정 연결은 처음부터 없었다.
- `lowStockFlag` 를 쓰는 곳 전수(admin-service `src` 전체 검색, 4파일): 엔티티 `readmodel/inventory/InventorySnapshotEntity.java:67-68,154` · 필터 `readmodel/inventory/InventorySnapshotRepository.java:18` (`:lowStockOnly = FALSE OR i.lowStockFlag = TRUE`) · 응답 `api/dashboard/dto/InventorySnapshotResponse.java` · 스키마 `db/migration/V2__init_readmodel.sql:288` + 부분 인덱스 `:298-300`. 컨트롤러 `api/dashboard/InventoryDashboardController.java:42-44,53-54` 가 저장소를 직접 부른다(Layered 예외). KPI 집계는 이 칼럼을 안 쓴다 — 콘솔 개요 «저재고 재고 보기» 는 `lowStockOnly=true` 목록을 쓴다.
- 🔴 **곁 발견 — 경계 불일치**: admin 은 `≤`(`InventoryProjectionService.java:341`, `domain-model.md:429`), inventory-service 알림은 **`<`**(`apps/inventory-service/.../application/service/LowStockDetectionService.java:81` `if (inventory.availableQty() >= threshold) return;`, javadoc `:33`). 임계값이 같아도 `available == threshold` 인 행은 **배지는 저재고 · 알림은 없음**. 콘솔 가이드 둘째 칸 «기준보다 적어지면»(`platform-console/apps/console-web/src/features/wms-guide/data.ts:176`)은 `<` 와 맞는다.

**② admin-service 가 설정 변경을 아는 경로** — **설정의 주인이 admin-service 자신**이다. 이벤트 소비는 필요 없고, 계약은 오히려 그것을 부정한다.

- 소유: `specs/services/admin-service/architecture.md:47-49`(«Operational Settings … published as events»), 쓰기 `application/settings/SettingsService.java:63-89`, 포트 `application/repository/SettingRepository.java:14` `find(key, warehouseId)` — 프로젝션 시점이든 조회 시점이든 **같은 DB 에서 PK 한 번**으로 현재 값을 읽는다. 시드 `db/seed/R__seed_dev_data.sql:185-193`(GLOBAL, sentinel `warehouse_id 00000000-…`, 값 `10`, 스키마 `{"type":"integer","minimum":0,"maximum":100000}`).
- 발행: `SettingsService.java:84-87,137-151` — 값이 실제로 바뀔 때만 outbox 로 `admin.settings.changed`(토픽 `wms.admin.settings.v1`, `infra/messaging/AdminOutboxPublisher.java:71`).
- 소비: admin-service 의 `@KafkaListener` 는 `infra/messaging/` 의 `InboundProjectionConsumer` · `MasterProjectionConsumer` · `InventoryProjectionConsumer` · `OutboundProjectionConsumer` 넷뿐, 설정 토픽 없음. 계약 `specs/contracts/events/admin-events.md:355` **«`admin-service`: ignores its own emitted `admin.settings.changed`»** ⇒ 자기 이벤트를 되받는 설계는 계약 변경이 필요하고, 같은 DB 를 가진 서비스가 Kafka 왕복을 할 이유도 없다. 변경 시점 훅이 필요하다면 **`SettingsService.upsert` 의 같은 트랜잭션**이다.
- 잘못된 값: 쓰기 시점에 JSON Schema 로 거른다(`SettingsService.java:75,102-117`). 그래도 읽는 쪽은 «행 없음 / 정수 아님 → 10» 폴백을 가져야 한다(Edge Cases).

**③ 재계산 범위 · 비용**

- «projection time» 문장 그대로만 구현하면 **실패 시나리오 1 이 그대로 일어난다** — 설정을 25 로 바꿔도 다음 재고 이벤트가 온 행만 25 기준이고 나머지는 10 기준. 데모의 기초 재고처럼 시드 뒤 이벤트가 거의 없는 행은 **무기한** 옛 기준이다. ⇒ «다음 재고 이벤트까지 기다리는» 대안은 기각.
- 행 수: `admin_inventory_snapshot` 은 시드가 행을 심지 않는다(이벤트 프로젝션만 — `R__seed_dev_*.sql` 에 INSERT 없음). 데모 행은 `infra/demo/seed/seed-wms.sh:137-143`(SKU 1종 × 로케이션 1, 합격 95) + `:309-313`(ecommerce SKU 86종 × WH-MAIN 로케이션 1 × 100) ⇒ **약 87행**(주문·이동으로 로케이션·로트가 갈리면 늘어남). 일반 상한은 «재고가 있던 (로케이션 × SKU × 로트) 조합» 수.
- 일괄 재계산은 **한 문장**: `UPDATE admin_inventory_snapshot SET low_stock_flag = (available_qty <= :t), version = version + 1 WHERE low_stock_flag IS DISTINCT FROM (available_qty <= :t)` — 바뀌는 행만. 87행은 무시할 비용이고 10⁵ 행도 순차 스캔 한 번.
- 🔴 단 `InventorySnapshotEntity.java:76-78` 이 **JPA `@Version`** 이다. 일괄 UPDATE 가 `version` 을 안 올리면, 같은 행을 이미 읽은 프로젝션 트랜잭션이 옛 임계값으로 계산한 플래그를 나중에 커밋해 **재계산을 덮어쓴다**(lost update). `version = version + 1` 을 넣으면 그 프로젝션이 `OptimisticLockException` → 재시도(`config/ProjectionKafkaConsumerConfig.java:22,65` 지수 백오프 3회; 비재시도 목록 `:72-74` 에 없음) → 새 값으로 재계산 — 결과는 맞지만 **경합 때마다 재시도 경로**를 탄다. 또 설정 변경만으로 응답의 `version`(`admin-service-api.md:273`)이 오른다.
- **대안: 조회 시점 계산** — 플래그를 저장하지 않고 조회 때 현재 설정으로 계산(`lowStockOnly` → `i.availableQty <= :threshold`, 응답 `lowStockFlag = availableQty <= threshold`). 재계산 대상 **0행**, ③·④ 의 경합이 정의상 없다. 비용 = 조회 요청마다 설정 PK 조회 1회. 잃는 것 = 부분 인덱스 `V2__init_readmodel.sql:298-300`(이 규모에선 `warehouse_id` 인덱스 `:295-296` + 필터로 충분).

**④ 알림(inventory)과 표시(admin)가 같은 값을 보는 순서 · 경합**

- 전파 경로가 다르다: admin 은 자기 DB 에서 **동기**로(②). inventory-service 는 outbox → Kafka → `apps/inventory-service/.../adapter/in/messaging/settings/AdminSettingsConsumer.java:59-115` → 메모리 홀더(`adapter/out/alert/InMemoryLowStockThresholdAdapter.java:35,57-63`). ⇒ 변경 직후 **admin 이 먼저, inventory 는 outbox·소비 지연만큼 뒤에** 새 값을 본다. 결국 일관 — 짧고 무해한 창.
- 🔴🔴 **곁 발견 — inventory-service 는 재기동하면 임계값이 «없음» 이 되어 저재고 알림이 0건이다.** BE-459 Option B 는 «재시작 시 `inventory.alert.low-stock.default-threshold` config 기본값» 을 전제했지만(`tasks/done/TASK-BE-459-lowstock-threshold-admin-settings-consumer.md:29`, `specs/services/inventory-service/architecture.md:256-263`), 그 값은 **어디에도 설정돼 있지 않다** — `config/AlertConfig.java:44` `@Value("${inventory.alert.low-stock.default-threshold:#{null}}")`, `apps/inventory-service/src/main/resources/application.yml` 에 키 없음, 저장소 전체 yml·env·compose 검색(`(?i)default.?threshold`, `LOW_STOCK_DEFAULT`, `alert\.low-stock`)에 값 없음. 빈 값이면 `LowStockDetectionService.java:77-79` 가 **알림을 내지 않는다**. 즉 재기동 뒤 운영자가 설정을 다시 저장할 때까지 알림 없음(데모는 창마다 새로 뜬다). 티켓 Goal 의 «inventory-service 의 저재고 알림은 25 기준» 은 **재기동 전까지만** 참이다. 콘솔 가이드 `data.ts:176` «기준을 정하지 않았으면 알림이 가지 않습니다» 가 이 동작과 맞는다.
  - 이 티켓(admin 표시) 범위 밖 — **별도 티켓 권장**. 최소안 = inventory `application.yml` 에 `inventory.alert.low-stock.default-threshold: 10` (재기동 = 시드 기본 10 = admin 폴백과 같음). 운영자 설정값의 재기동 내구성은 BE-459 가 유예한 Option A 그대로.
  - (가) 로 가면 admin 은 «DB 의 현재 설정», inventory 는 재기동 뒤 «없음» — 위 별도 티켓과 같이 가야 «한 개념 두 기준» 이 실제로 닫힌다.
- 경계(`≤` vs `<`, ①) 는 순서와 무관하게 남는 차이 — (가)·(나) 어느 쪽이든 스펙에서 하나로 정해야 한다.

**`domain-model.md:379` 드리프트 재확인** — 표는 «`status` | enum | Mirrors Order status» 인데 코드는 세 값으로 접는다: `readmodel/outbound/OrderSummaryEntity.java:123` `"RECEIVED"` · `:128` `"CANCELLED"` · `:133` `"SHIPPED"`. 문서만 고치고, (가)/(나) 와 무관하게 같은 PR 에 싣는다.

### 소유자 결정 대기

**(가) 설정을 따르게 고친다 — 추천.** 계약·도메인 모델 문장이 이미 이쪽이다(①). 구현 방식이 둘이고 **(가-2) 를 추천**한다.

- **(가-2) 조회 시점 계산 (추천)**
  - 스펙 먼저: `admin-service-api.md:281-282` · `domain-model.md:429,434-436` → «`lowStockFlag` 는 **조회 시점**에 현재 `inventory.low_stock.default_threshold_qty`(GLOBAL; 없거나 정수가 아니면 10)로 계산». `domain-model.md:379` 도 같이.
  - 코드: 설정 읽기 하나(`SettingRepository.find(KEY, null)` → 정수 파싱 · 폴백 10)를 작은 조회 컴포넌트로 · `InventorySnapshotRepository.java:18` → `(:lowStockOnly = FALSE OR i.availableQty <= :threshold)` · `InventorySnapshotResponse.from(entity, threshold)` · `InventoryDashboardController` 두 엔드포인트가 threshold 를 넘김 · 프로젝션 상수 `InventoryProjectionService.java:54-55,341` 제거 · 칼럼은 Flyway `V5__…` 로 `low_stock_flag` + 부분 인덱스 제거(추천 — 남기면 «저장된 플래그 vs 계산된 플래그» 두 진실이 생긴다).
  - 시험: 단위(폴백 10 · 정수 아님 → 10 · 경계값) · `@WebMvcTest` 슬라이스(임계 25 에서 `lowStockOnly` 결과·`lowStockFlag`) · Testcontainers IT 1개(설정 upsert → 재고 이벤트 없이 같은 행의 조회 결과가 바뀜).
  - 콘솔(같은 PR — PC-FE-365 약속): `platform-console/apps/console-web/src/features/wms-guide/data.ts:157-159,170-171` «고정값 · 가용 10개 이하» → «설정값 · 기본 10(운영 설정에서 변경)» 류 · 주석 `wms-ops/components/WmsOperationsScreen.tsx:35` · `wms-ops/api/overview-state.ts:65,168`. 이 문구를 고정하는 시험은 없다(문자열 검색 = `data.ts` 1곳). 새 문구는 소유자 승인.
  - 비용: 재계산 0행 · 경합 0 · 요청당 PK 조회 1회 · 마이그레이션 1개. 서버 변경 ⇒ 굽기.
- **(가-1) 쓰기 시점 + 설정 변경 시 일괄 재계산**
  - `applySnapshot` 이 설정을 읽어 계산 + `SettingsService.upsert` 가 저재고 키일 때 **같은 트랜잭션에서** ③의 한 문장 UPDATE(`version = version + 1` 포함).
  - 대가: 설정(쓰기 도메인)이 읽기 모델 표를 직접 갱신하는 결합 · `@Version` 경합 시 프로젝션 재시도 · 설정 변경만으로 응답 `version` 증가. 부분 인덱스는 남는다. 시험·콘솔 문구는 (가-2) 와 같다.

**(나) 상수를 유지하고 계약을 고친다**

- 스펙: `admin-service-api.md:281-282` · `domain-model.md:429,434-436` → «`lowStockFlag` 는 고정 기준 `availableQty ≤ 10` 의 읽기 편의 필드, 설정 `inventory.low_stock.default_threshold_qty` 는 **알림 전용**». `InventoryProjectionService.java:54` 주석의 «recomputed at projection time» 도 거짓이라 고침. 콘솔 가이드 «고정값 · 가용 10개 이하» 는 **그대로 참**.
- 비용: 문서 2곳 + 주석 1줄, 동작 불변(굽기 불필요). 대가: 임계값을 25 로 바꾸면 배지·필터·개요는 10, 알림은 25 — 티켓이 문제 삼은 «한 개념 두 기준» 이 **설계로 굳고**, 가이드 설명으로 버틴다.

**추천: (가-2).** ① 계약·도메인 모델이 이미 «현재 설정» 이다 — 틀린 것은 코드다. ② admin 이 설정의 주인이라 읽기가 같은 DB PK 한 번이고, 조회 시점 계산이면 실패 시나리오 1(새 행·옛 행 섞임)과 Edge Case «동시 도착 · 순서 무관» 이 **구조적으로** 사라진다 — (가-1) 은 같은 결과를 일괄 UPDATE + 낙관적 잠금 재시도로 맞춰야 한다. ③ 이 규모에서 부분 인덱스를 잃는 비용은 없다.

**같이 정할 것**: (i) 경계 `≤` 유지 vs inventory 의 `<` 로 맞춤(추천: `<` — 알림이 이미 그렇고 가이드 둘째 칸도 «적어지면»; 단 `available == threshold` 행의 배지가 바뀌는 사용자 가시 변경) · (ii) `low_stock_flag` 칼럼 제거 vs 존치(추천: 제거) · (iii) ④ 의 inventory 재기동 시 임계값 없음(알림 0건)을 별도 티켓으로 기안할지(추천: 기안).

### 소유자 결정 (2026-10-22 KST)

- **(가-2) 조회 시점 계산** — 현재 설정 `inventory.low_stock.default_threshold_qty` 로 조회할 때 계산. 없거나 정수가 아니면 10.
- (i) 경계 **`<`** — inventory 알림과 맞춤. `available == threshold` 행은 이제 저재고가 **아니다**(사용자 가시 변경 — 수용).
- (ii) **`low_stock_flag` 칼럼 + 부분 인덱스 제거** — (가-2) 의 일부로 수용.
- (iii) inventory-service 재기동 → 임계값 없음 → 알림 0건 은 **별도 티켓 `TASK-BE-647`**(메인 세션 기안). 이 티켓 범위 아님.
- 콘솔 문구는 **이 브랜치에서 바꾸지 않는다** — 콘솔은 머지 즉시 배포(Vercel)되고 서버 변경은 굽기 뒤에 나가므로, 문구는 아래 `## 콘솔 후속 문구 (굽기 직후 머지)` 에 적어 두고 메인 세션이 굽기 직후 콘솔 PR 로 싣는다.

---

## 구현 (2026-10-22 UTC · Opus)

커밋 둘: 스펙 먼저(`docs(wms): …`) → 구현(`fix(wms): …`). 경로는 `projects/wms-platform/apps/admin-service/src/` 기준.

| 파일 | 변경 |
|---|---|
| `main/java/com/wms/admin/application/settings/LowStockThresholdReader.java` (신규) | `currentThreshold()` — `SettingRepository.find(KEY, null)` → JSON 정수 ≥ 0 이면 그 값, 행 없음 · 정수 아님 · 음수 · int 범위 밖 · 깨진 JSON → `10`(경고 로그, 요청은 실패하지 않음). `isLowStock(avail, t) = avail < t` 한 곳에 경계를 둔다. |
| `main/java/com/wms/admin/readmodel/inventory/InventorySnapshotRepository.java` | `search(…, boolean lowStockOnly, int lowStockThreshold, …)` — `(:lowStockOnly = FALSE OR i.availableQty < :lowStockThreshold)` |
| `main/java/com/wms/admin/api/dashboard/dto/InventorySnapshotResponse.java` | `from(entity, threshold)` — `lowStockFlag` 를 계산해 싣는다 |
| `main/java/com/wms/admin/api/dashboard/InventoryDashboardController.java` | 목록은 요청당 임계값을 **한 번** 읽어 필터와 플래그에 같이 쓴다(한 응답 안에서 둘이 어긋날 수 없다) · `by-key` 도 같은 규칙 |
| `main/java/com/wms/admin/readmodel/inventory/InventorySnapshotEntity.java` | `lowStockFlag` 필드 · 생성자/`apply` 인자 · getter 제거 |
| `main/java/com/wms/admin/application/projection/InventoryProjectionService.java` | 상수 `DEFAULT_LOW_STOCK_THRESHOLD` 와 플래그 쓰기 제거(되살리지 말라는 주석) |
| `main/resources/db/migration/V5__drop_inventory_low_stock_flag.sql` (신규) | `DROP INDEX IF EXISTS idx_admin_inventory_snapshot_low_stock` · `ALTER TABLE … DROP COLUMN IF EXISTS low_stock_flag`. 데이터 손실 없음(상수의 함수였다). 🔴 V5 적용 뒤 이미지를 그 아래로 **되돌리면** 옛 코드가 없는 칼럼에 써서 프로젝션이 깨진다 — 앞으로만 간다. |

**칼럼을 읽는 다른 곳 전수**(저장소 전체 `low_stock_flag|lowStockFlag`, `tasks/` 제외):
- admin-service 밖에서 **칼럼**을 쓰는 곳은 SQL 시드 둘뿐이었다 — `scripts/console-demo/seed/05-wms-admin.sql:24-38` · `tests/federation-hardening-e2e/fixtures/seed-wms-admin.sql:24-46`(수정 전 줄 번호, 둘 다 `INSERT … low_stock_flag … FALSE`). 칼럼과 값을 뺐다. 칼럼이 `NOT NULL DEFAULT FALSE` 였으므로 **V5 전 스키마(지금 굽힌 데모)와 V5 뒤 스키마 둘 다에서 이 INSERT 가 성공**한다 — 굽기 순서와 무관. (공유 경로 `scripts/` · `tests/` — 이 변경의 원자적 적응분, 프로젝트 고유 내용은 원래부터 WMS 시드.)
- 다른 서비스(inventory 등): 없음.
- 콘솔: **API 필드** `lowStockFlag` 만 읽는다(`wms-ops/api/types.ts`, `WmsInventoryDataTable.tsx`, `overview-state.ts`, 샘플 `shared/sample/fixtures/wms.ts`, 시험 둘). 응답 모양은 그대로이므로 콘솔 코드 변경 불필요 — 문구만 아래 후속.
- 스펙: `database-design.md` 의 V2 DDL 블록은 역사적 모양으로 두고, 근거 문단 · 인덱스 표 · 부분 인덱스 수 · 참조 목록에 V5 를 적었다(스펙 커밋).

**시험**

| 층 | 클래스 | 덮는 것 | 로컬 |
|---|---|---|---|
| 단위 (`MockitoExtension`, STRICT_STUBS) | `test/.../application/settings/LowStockThresholdReaderTest` (12) | 설정값 반환 · `0` 허용 · 행 없음 → 10 · 무효 8종(`"25"` 문자열, `2.5`, `-1`, `true`, `null`, 객체, 깨진 JSON, int 초과) → 10 · 경계 `9<10` 참 / `10<10` 거짓 | ✅ |
| 슬라이스 (`@WebMvcTest` + `SecurityConfig` + `GlobalExceptionHandler` + **실제** 리더, 설정 저장소만 mock) | `test/.../api/dashboard/InventoryDashboardControllerSliceTest` (8) | 설정 25 → 저장소에 `eq(25)` 전달 · 24 참 / **25 거짓** / 26 거짓 · 15 는 25 에서 참(옛 `<=10` 이면 거짓이던 행) · 설정 없음 → `eq(10)`, 9 참 / 10 거짓 · 정수 아님 → 10 · `by-key` 도 설정을 따름 · 401 · 404 | ✅ |
| 슬라이스 (기존) | `SecurityChainAssemblyParityTest` (11) | 새 의존(`LowStockThresholdReader`) mock 만 추가, 판정 불변 | ✅ |
| IT (Testcontainers Postgres, `@Tag("integration")`) | `test/.../integration/InventoryLowStockThresholdIT` (2) | 행 4개(5·10·15·25)를 **한 번만** 쓰고 설정만 10 → 25 → 0 으로 바꿔 `lowStockOnly` 결과가 `{5}` → `{5,10,15}` → `{}` 로 바뀌는지 + 모든 행 `lowStockFlag == avail < 25` · V5 뒤 칼럼 · 인덱스 0개 | ⚪ **CI 전용** — 이 호스트 Docker 데몬 없음(`docker info` rc=1). 컴파일은 로컬 `test` 에서 통과. CI `integration-tests` 잡이 `:projects:wms-platform:apps:admin-service:integrationTest` 를 돈다(`ci.yml:3639`). |
| IT (기존) | `ReadModelPersistenceIntegrationTest` | 생성자 인자 하나 줄인 것만(두 줄, throughput 부분은 안 건드림 — BE-643 동시 작업) | ⚪ CI 전용 |

로컬 실행: `./gradlew :projects:wms-platform:apps:admin-service:test` rc=0 — **341 tests, 0 failed, 0 skipped** (JUnit XML 합계). 이어 `:admin-service:check` rc=0.

## 콘솔 후속 문구 (굽기 직후 머지)

> 메인 세션이 **굽기가 끝난 직후** 콘솔 PR 로 싣는다(콘솔은 머지 즉시 Vercel 배포 — 서버보다 먼저 나가면 문구가 거짓이 된다). 사용자 문구는 PC-FE-365 AC-1 관례대로 소유자 승인 대상. 경로는 `projects/platform-console/apps/console-web/src/features/` 기준. 이 문자열을 고정하는 시험은 없다(`고정값 · 가용 10개 이하` 검색 = `data.ts` 1곳).

| 파일:줄 | 종류 | 지금 | 바꿀 문구 |
|---|---|---|---|
| `wms-guide/data.ts:157-159` | 주석 | `🔴 첫 항목의 «10» 은 지금 서버 사실(admin-service InventoryProjectionService 의 상수 availableQty <= 10)이다. TASK-BE-644 가 (가) «설정을 따르게 고침» 으로 결정되면 이 항목(threshold · desc)이 거짓이 되므로 그 티켓과 같은 PR 에서 고칠 것.` | `🔵 TASK-BE-644 (굽기 완료): 재고 테이블 배지·필터도 이제 설정 inventory.low_stock.default_threshold_qty 를 따른다(조회 시점 계산, availableQty < 기준, 설정 없음 = 10). 두 메커니즘의 차이는 «기준값» 이 아니라 «무엇을 하는가»(표시 vs 알림) 와 알림 쪽의 창고·SKU별 재정의다.` |
| `wms-guide/data.ts:154-155` | 주석 | `저재고(저재고) — **두 개의 다른 메커니즘**이며 임계값이 다르다. 가이드에서 반드시 구분: 테이블 배지와 운영자 알림이 서로 불일치할 수 있다.` | `저재고 — **두 개의 다른 메커니즘**(표시 · 알림)이다. 기본 기준은 같은 설정값이지만, 알림은 창고·SKU별 재정의가 있을 수 있어 배지와 알림이 어긋날 수 있다.` (157-159 와 함께 고쳐야 같은 블록 안에서 모순이 안 생긴다) |
| `wms-guide/data.ts:170` | 사용자 문구 `threshold` | `고정값 · 가용 10개 이하` | `설정값 · 가용 수량이 기준(기본 10)보다 적을 때` |
| `wms-guide/data.ts:171` | 사용자 문구 `desc` | `가용 수량이 10개 이하면 자동으로 표시되는 고정 기준입니다. 상품(SKU)마다 다르게 정할 수는 없습니다.` | `가용 수량이 운영 설정의 저재고 기준(기본 10)보다 적으면 표시됩니다. 기준을 바꾸면 모든 재고에 바로 적용됩니다. 상품(SKU)마다 다르게 정할 수는 없습니다.` |
| `wms-ops/components/WmsOperationsScreen.tsx:31-35` | 주석 | `NOTE (TASK-PC-FE-365): inventory.low_stock.default_threshold_qty drives the inventory-service low-stock ALERT only; the 재고 표 저재고 배지 / 개요 저재고 count come from admin-service's lowStockFlag, a fixed availableQty <= 10 today (TASK-BE-644 may change that).` | `NOTE (TASK-BE-644): inventory.low_stock.default_threshold_qty drives BOTH the inventory-service low-stock alert and admin-service's lowStockFlag (재고 표 저재고 배지 / 개요 저재고 count) — the latter derived at query time as availableQty < threshold (missing setting → 10).` |
| `wms-ops/api/overview-state.ts:62-66` | 주석 | `… flags lowStockFlag (lowStockOnly=true; currently availableQty <= 10, a constant in admin-service that does NOT follow the inventory.low_stock.default_threshold_qty setting — TASK-BE-644; it is NOT a per-SKU reorder point), …` | `… flags lowStockFlag (lowStockOnly=true; availableQty < the current inventory.low_stock.default_threshold_qty setting, derived at query time — TASK-BE-644; it is NOT a per-SKU reorder point), …` |
| `wms-ops/api/overview-state.ts:166-168` | 주석 | `// SKU-locations the read model flags low-stock (availableQty <= 10 today, // fixed in admin-service — TASK-BE-644).` | `// SKU-locations the read model flags low-stock (availableQty < the current // low-stock setting, derived at query time — TASK-BE-644).` |

- 🔴 둘째 칸(`data.ts:174-176` «운영자 저재고 알림» · «기준을 정하지 않았으면 알림이 가지 않습니다») 은 **이 티켓이 건드리지 않는다** — 그 문장의 사실성은 `TASK-BE-647`(inventory 재기동 시 임계값 없음)의 결과에 달렸다. 647 이 고치면 그쪽 PR 에서 함께 본다.

## 머지 전 확인 (2026-10-22 UTC · 분석=Opus 5.5)

- PR #4478(head `b7e172245`) 체크 pending 0 · fail 0 — CI 의 wms 통합 잡이 `InventoryLowStockThresholdIT`(설정만 바꿔 결과가 바뀌는가 · V5 뒤 칼럼 0)를 돌렸다.
- 공유 경로 시드 두 파일을 고쳤으므로 브랜치에서 e2e 둘을 돌렸다: 콘솔 nightly run `39815095104` **success** · federation-hardening-e2e run `39815099153` **success** → AC-2 닫힘.
- `TASK-BE-643`(#4475) · 티켓 PR(#4476) 머지 뒤 main 병합 — `INDEX.md` 충돌 2곳(ready = BE-647 만 · in-progress = BE-644) 해소.
- 🔴 **굽기 직후 할 일**: § 콘솔 후속 문구(굽기 직후 머지) 의 표를 콘솔 PR 로 — 사용자 문구 두 줄(`wms-guide/data.ts:170-171`)은 머지 전 소유자 승인. 지금 머지하면 굽기 전까지 화면 설명이 서버와 어긋난다.
- 🔴 V5 적용 뒤 이전 이미지로 되돌리면 프로젝션이 깨진다 — 앞으로만 고친다.
- 열림: **AC-3**(굽기 뒤 라이브 — 설정을 바꾸면 콘솔 저재고 필터 결과가 따라 바뀌는가).
