# Task ID

TASK-BE-643

# Title

admin-service 일별 처리량 누계가 «마지막 이벤트 시각» 조건 때문에 순서가 뒤바뀐 이벤트를 버린다 — 서로 다른 ASN · 주문의 이벤트는 파티션이 달라 순서가 보장되지 않으므로 누계가 실제보다 적어진다

# Status

done

# Owner

backend

# Task Tags

- code
- test
- spec

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (스펙 결정 변경 · 프로젝션 멱등성 설계 — `idempotency.md` § 2.6 을 먼저 고친다)
> 기안 2026-10-22 UTC — `TASK-PC-FE-358`(콘솔 WMS 일별 처리량 그래프) AC-0 의 곁 발견. 소유자 결정 2026-10-22 UTC: «ready 티켓만 기안» — 착수는 따로 지시.

# Dependency Markers

- 출처: `projects/platform-console/tasks/*/TASK-PC-FE-358-wms-daily-throughput-chart.md` § AC-0.
- 선행 없음. 이 티켓이 고치면 콘솔 358 의 그래프 값이 바뀐다(콘솔 변경 불필요).
- 🔴 서버 변경 → 배포는 **굽기**. 다음 굽기 대기 목록에 합류.

# Goal

일별 처리량 누계(`admin_throughput_inbound_daily` · `admin_throughput_outbound_daily`, PK `(date, warehouse_id)`)는 이벤트 하나마다 1을 더한다. 그런데 더하는 쿼리에 상태 덮어쓰기용 조건이 붙어 있다:

- `apps/admin-service/src/main/java/com/wms/admin/readmodel/throughput/ThroughputInboundDailyRepository.java:36` · `ThroughputOutboundDailyRepository.java:36` — `WHERE <table>.last_event_at < EXCLUDED.last_event_at`
- 스펙 `specs/services/admin-service/idempotency.md` § 2.6 (`:270-290`)이 이것을 의도로 적었다: «The `last_event_at` guard prevents out-of-order events from incorrectly incrementing counters using a stale source.» · 일반 규칙 `specs/contracts/events/admin-events.md:475-480`(«occurredAt > row.last_event_at 일 때만 upsert / append / **increment**»).

이 근거는 **상태를 덮어쓰는 행**(스냅샷 · 요약)에는 맞지만 **서로 다른 사건을 세는 누계**에는 맞지 않는다. 누계의 행 하나는 «창고 × 하루» 이고, 그 안에 들어오는 이벤트는 **서로 다른 ASN · 주문**에서 온다:

- 파티션 키 = `asnId`(`specs/contracts/events/inbound-events.md:18`) · 출고 = `orderId` / `sagaId`(`outbound-events.md:19, 146, 223`).
- 계약 자신이 «다른 `aggregateId` 사이의 순서를 가정하지 말 것»(`inbound-events.md:400`).

⇒ 같은 창고 · 같은 날의 두 ASN 적치 완료가 `occurredAt` 순서와 다르게 도착하면(다른 파티션 · 재시도 · 소비자 지연) **늦게 도착한 더 이른 이벤트는 0행 갱신 = 조용히 버려진다**(`InboundProjectionService.java:253-254` 주석 «0 affected rows = stale event silently»). `occurredAt` 이 같은 두 이벤트도 둘째가 버려진다(`<` 이므로). 중복 방지는 이미 `admin_event_dedupe` 의 `event_id` 가 하고 있어(`idempotency.md:288`), 이 조건은 누계에 대해 **중복 방지가 아니라 유실**만 더한다.

# Scope

## In Scope

- **AC-0 측정**:
  - ① 재현 IT(Testcontainers — 기존 `TASK-BE-047` 의 프로젝션 IT 하네스): 같은 `(date, warehouse)` 로 `occurredAt` T2 이벤트 → T1(< T2) 이벤트 순서로 소비 → 기대 `putaway_count=2`, 지금은 1 이 나와야 한다(red 를 먼저 본다). 같은 `occurredAt` 두 건도.
  - ② 출고 쪽도 같은지(`OutboundProjectionService.java:224-229`).
  - ③ (선택 · 데모 창) 데모 DB 에서 적치 완료 이벤트 수(`admin_event_dedupe` 의 해당 `event_type` · outcome 별 건수) vs `putaway_count` 합 — `IGNORED_DUPLICATE_LATE` 가 누계 행에서 몇 건인지. 창이 필요하면 ⚪ 로 남긴다.
  - ④ 같은 «덮어쓰기 조건 + 더하기» 모양이 admin-service 의 다른 누계에 있는지 전수(`+ EXCLUDED.` · `+ 1` grep).
- **스펙 먼저**: `idempotency.md` § 2.6 · `admin-events.md:475-480` 을 «누계는 `event_id` 중복 제거만으로 멱등 · `last_event_at` 은 기록용(최대값 유지 `GREATEST`)» 으로 고친다. 근거와 이전 문장은 남긴다(왜 바꿨는지).
- 수선: 두 저장소의 `WHERE` 조건 제거 + `last_event_at = GREATEST(...)`. 프로젝션 서비스의 «0행 = stale» 판정 · outcome 기록이 누계 경로에서 무엇을 남기는지 맞춘다.
- 시험: ① 의 red → green · 같은 이벤트 두 번(같은 `event_id`) → 1 회만(중복 제거가 여전히 지킨다) · 기존 프로젝션 IT 비회귀.

## Out of Scope

- 하루를 UTC 로 자르는 것(`InboundProjectionService.java:246` · `OutboundProjectionService.java:228` `ZoneOffset.UTC` — 창고 `timezone` 미사용). 소유자가 콘솔 358 에서 «UTC 기준 표시» 를 골랐다. 바꾸려면 별도 결정.
- 이미 쌓인 누계의 소급 보정(데모는 다음 굽기의 새 볼륨이 다시 만든다 — 운영 데이터가 생기면 별도).

# Acceptance Criteria

- [x] **AC-0** — ①~④ 를 file:line · IT 출력과 함께 기록. ① 이 red 가 아니면(조건이 실제로는 안 무는 경로가 있으면) STOP 하고 이유를 적는다. — 아래 § AC-0. ① 의 «조건이 문다» 는 CI 런으로 확인(기존 시험이 버림을 단언한 채 초록), 새 역순 시험의 red 자체는 로컬 Docker 부재로 **관측 안 됨**. ③ ⚪(데모 창 필요).
- [x] **AC-1** — 스펙 두 곳 갱신(이전 근거 보존) → 구현. — § 구현.
- [x] **AC-2** — 시험: 순서 뒤바뀜 · 같은 시각 두 건 → 2 · 같은 `event_id` 두 번 → 1 · 출고 동일 · 기존 IT 비회귀(CI 의 wms 잡). — 🔵 시험은 썼고 단위 시험은 로컬 초록. 🔴 저장소 · Kafka IT(역순 · 같은 시각 · 출고)는 **CI 에서만** 돈다 — 그 초록을 보기 전에는 닫지 않는다.
- [ ] **AC-3** — 굽기 뒤 라이브: 데모 시드(`infra/demo/seed/seed-wms.sh` — WH-MAIN 적치 86건)의 `putaway_count` 합이 적치 완료 이벤트 수와 같은가(다음 데모 창).

# Related Specs

- `projects/wms-platform/specs/services/admin-service/idempotency.md` § 2 · § 2.6
- `projects/wms-platform/specs/services/admin-service/domain-model.md` § 13
- `projects/wms-platform/specs/services/admin-service/architecture.md` (Service Type 확인)

# Related Contracts

- `projects/wms-platform/specs/contracts/events/admin-events.md` (`:466-486`)
- `projects/wms-platform/specs/contracts/events/inbound-events.md` (파티션 키 · `:400`)
- `projects/wms-platform/specs/contracts/events/outbound-events.md`
- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 1.2 (응답 모양 불변)

# Edge Cases

- 같은 이벤트의 재전달(같은 `event_id`) — 중복 제거가 막아야 한다(조건을 지워도 이중 계산 없음을 시험으로).
- `admin_event_dedupe` 보존 기간이 지난 뒤의 재생(`idempotency.md:352-357`) — 조건을 지우면 오래된 이벤트 재생이 누계를 다시 올린다. 지금 조건은 이 경우를 **일부만** 막는다(그날 더 늦은 이벤트가 있었을 때만). 재생 절차(재구축 = 표를 비우고 다시)와 함께 판정해 스펙에 적는다.
- 하루 경계 직전 · 직후 이벤트(서로 다른 행 — 영향 없음).

# Failure Scenarios

1. 조건만 지우고 재생 위험을 기록하지 않는다 → Edge Case 2 를 스펙에 적는 것이 AC-1.
2. IT 가 이벤트를 `occurredAt` 순서대로만 보낸다 → 결함을 못 본다(지금 시험이 그렇다 — `TASK-BE-047` 의 replay 시험은 «같은 순서 재생» 이었다). ① 은 반드시 역순.
3. 콘솔 쪽에서 값을 보정하려 한다 → 범위 밖, 원인은 서버다.

---

## AC-0 측정 (2026-10-22 UTC)

### ① 재현 — 조건이 실제로 무는가

- 🔴 **이 호스트에서는 red 를 직접 보지 못했다.** Docker 데몬이 없다(`docker info` → `open //./pipe/docker_engine: The system cannot find the file specified`). 새 역순 시험을 넣은 채 `:projects:wms-platform:apps:admin-service:integrationTest --tests '*ReadModelPersistenceIntegrationTest*'` → rc=1, 그러나 **20 개 전부**(손대지 않은 `warehouseRef_roundTrip` 등 포함)가 `IllegalStateException at DockerClientProviderStrategy.java:274` 로 실패 — 컨테이너를 못 띄운 것이지 단언이 진 것이 아니다. **판정 아님.**
- 🔵 **조건이 무는 증거는 CI 에 이미 있다 — 기존 시험이 «버림» 을 단언하고 초록이었다.** `ReadModelPersistenceIntegrationTest.throughputInboundDaily_nativeUpsertIncrement_isAtomicAndLwwGuarded`(수정 전 `:396-405`): `NOW` 로 1건 · `NOW+60s` 로 1건 → 2, 그 다음 **`NOW-60s`(더 이른 시각) 호출은 `affected=0` · `putawayCount` 그대로 2 · `qtyReceived` 그대로 75** 를 단언한다. 이 시험은 저장소 메서드를 직접 부르므로(`event_id` 가 없다) 막은 것은 dedupe 가 아니라 **`WHERE last_event_at < EXCLUDED.last_event_at` 자체**다. main CI 런 `39789590298` (커밋 `98ce32de2`, 2026-10-22 13:50Z) 의 잡 `Integration (master-service + notification-service + outbound-service, Testcontainers)` → `admin-service:integrationTest | 68 | 0 | 0 | 0`(실패 0 · 건너뜀 0). ⇒ **더 이른 `occurredAt` 의 새 이벤트는 조용히 버려진다 = 결함이 실재한다.** STOP 조건 아님.
- 같은 `occurredAt` 두 건: `<` 이므로 둘째가 0행 — SQL 로부터의 추론이고 **실행으로 본 것은 아니다**. 새 시험 `throughputInboundDaily_equalOccurredAt_countsBothEvents` 가 이것을 잰다.
- 새 red 시험(수정 전 코드에서 실패해야 할 칸) — 이 티켓의 첫 커밋에 시험만 넣었다:
  - `ReadModelPersistenceIntegrationTest` — `throughputInboundDaily_reverseOrderArrival_countsBothEvents`(T2 → T1, 기대 2 · 지금 1) · `throughputInboundDaily_equalOccurredAt_countsBothEvents`(기대 2 · 지금 1) · `throughputOutboundDaily_reverseOrderArrival_countsBothEvents` · `throughputOutboundDaily_equalOccurredAt_countsBothEvents`.
  - `InboundProjectionKafkaIT.putawayCompleted_reverseOrderAcrossAsns_countsBoth`(소비 경로 전체 — 서로 다른 `asnId` 키, 늦은 것 먼저; 더 이른 이벤트의 dedupe outcome 이 `APPLIED` 여야 한다 — 지금은 `IGNORED_DUPLICATE_LATE`) · `OutboundProjectionKafkaIT.shippingConfirmed_reverseOrderAcrossShipments_countsBoth`.
  - 🔴 이 칸들의 red 는 **첫 커밋만 CI 에 올렸을 때에만** 관측된다. 이 작업은 push 하지 않았으므로 관측되지 않았다.

### ② 출고 쪽

- 같은 SQL: `ThroughputOutboundDailyRepository.java:36` `WHERE admin_throughput_outbound_daily.last_event_at < EXCLUDED.last_event_at`.
- 🔴 **출고는 입고보다 한 겹 더 조용하다.** `OutboundProjectionService.java:229` 가 `upsertIncrement` 의 반환값을 **버리고** `:230` 에서 무조건 `APPLIED` 를 돌려준다. 그래서 누계가 버려져도 dedupe outcome 은 `APPLIED`, `admin.projection.dropped.count{reason=stale}` 도 안 오른다 — 유실이 지표 어디에도 안 남는다. 입고는 `InboundProjectionService.java:256-258` 이 0행을 `IGNORED_DUPLICATE_LATE` 로 매핑해 `markStale` + `recordDropped("stale")` 를 남긴다(그 기록이 ③ 의 측정 근거가 될 수 있었던 이유).
- 출고 핸들러의 다른 쓰기(`shipment_summary` `:186-189` · `order_summary` 의 `applyShipped` `:218-222`)는 **상태 덮어쓰기**이므로 LWW 가 맞다 — 손대지 않는다.

### ③ 데모 DB 의 유실 건수 — ⚪ 측정 안 함

- 데모 창이 필요하다(이 세션에서 데모 DB 에 닿지 않음). 덧붙여 ② 때문에 **출고 유실은 `admin_event_dedupe` 로는 셀 수 없다**(outcome 이 `APPLIED` 로 남는다). 입고만 `event_type='inbound.putaway.completed' AND outcome='IGNORED_DUPLICATE_LATE'` 로 셀 수 있다. AC-3 라이브 확인 때 함께.

### ④ 같은 모양의 다른 누계 — 전수

- `admin-service/src/main` 에서 `+ EXCLUDED.` · `+ 1` · `ON CONFLICT` · `last_event_at <` grep: 누계 SQL 은 **두 저장소뿐**(`ThroughputInboundDailyRepository.java:28-36` · `ThroughputOutboundDailyRepository.java:28-36`). 나머지 `ON CONFLICT` 는 `db/seed/*.sql` 의 `DO NOTHING` 뿐.
- 엔티티의 `increment()`(`ThroughputInboundDailyEntity.java:58` · `ThroughputOutboundDailyEntity.java:55`)는 main 코드에서 호출자 0(시험 `throughputInboundDaily_compositePk_increment` 만 쓴다) — 경로 아님.
- 다른 읽기 모델(`*_summary` · `*_snapshot` · `*_ref`)은 모두 «행 = 한 집계체의 현재 상태» 라 LWW 가 맞다. 범위 밖.
- 🔵 **스펙 안의 충돌도 하나 있었다**: `database-design.md` § 13(`:745-760`)은 이미 «`WHERE` 없음 + `last_event_at = GREATEST(...)` · dedupe 가 멱등을 맡는다» 로 적혀 있었고, `idempotency.md` § 2.6 과 `admin-events.md:475-480` 만 조건을 적었다. 구현은 후자를 따랐다. 이 티켓은 전자 쪽으로 맞춘다.

## 구현 (2026-10-22 UTC · Opus)

커밋 셋 — 시험(red 여야 할 칸) → 스펙 → 구현 순서. push · PR 은 하지 않았다.

### 스펙 (AC-1)

- `specs/services/admin-service/idempotency.md`
  - § 2.6: `WHERE ... last_event_at < EXCLUDED.last_event_at` 삭제 · `last_event_at = GREATEST(row, EXCLUDED)`. «누계는 `event_id` 중복 제거만으로 멱등 · `last_event_at` 은 기록용 최대값 · 누계 경로는 `IGNORED_DUPLICATE_LATE` 를 만들지 않는다». 새 절 «Why there is no `last_event_at` guard» 에 **이전 SQL 과 이전 근거 문장을 인용으로 보존**하고 왜 틀렸는지(행 = 창고 × 하루, 이벤트 = 서로 다른 ASN · 주문, 파티션 키 `asnId`/`orderId`, `inbound-events.md:400`) 적었다.
  - § 2.2 · § 2.3 (2a 에 누계 분기) · § 2.5 제목에서 `throughput_*` 제외(이전 제목 기록) · § 3 결정표(상태 덮어쓰기 행 / 누계 행 분리) · § 6 시험 7a 신설(«반드시 역순으로 보낸다»).
  - § 3.1 (Edge Case 2): dedupe 보존(30일) 뒤 재생은 누계를 **다시 올린다**. 예전 조건은 그날 더 늦은 이벤트가 있었을 때만 **일부** 막았을 뿐 재생 방어가 아니었다. 규칙: 30일 넘은 이벤트를 라이브 누계에 재생하지 않는다 · 안전한 재생은 재구축(`runbooks/read-model-rebuild.md` § Step 3 — 누계 **와** `admin_event_dedupe` 를 함께 비우고 재생)뿐. 재구축 재생이 오히려 가장 순서가 뒤섞인 상황이라 옛 조건 아래선 재구축이 덜 셀 수 있었다(SQL 로부터의 추론 — 측정 아님). 이전 문단은 인용으로 보존.
- `specs/contracts/events/admin-events.md` § Projection Idempotency Pattern: 2a 를 «누계 — 항상 증가» / «상태 덮어쓰기 — 기존 LWW» 로 나누고, 이전 문장(«upsert / append / **increment**») 과 바꾼 이유를 인용 블록으로 남겼다.
- `database-design.md` § 13 은 이미 목표 모양이라 손대지 않았다. 마이그레이션 `V2__init_readmodel.sql` 의 주석도 손대지 않았다(적용된 마이그레이션의 체크섬이 바뀐다).

### 코드

- `apps/admin-service/src/main/java/com/wms/admin/readmodel/throughput/ThroughputInboundDailyRepository.java` · `ThroughputOutboundDailyRepository.java` — `WHERE` 삭제, `last_event_at = GREATEST(...)`. javadoc 에 «다시 넣지 말 것» 과 이유.
- `apps/admin-service/src/main/java/com/wms/admin/application/projection/InboundProjectionService.java` — `onPutawayCompleted` 의 «0행 → `IGNORED_DUPLICATE_LATE`» 분기 삭제, 항상 `APPLIED`(출고와 같은 모양). 이제 누계 경로에서 `markStale` · `recordDropped("stale")` 가 나오지 않는다.
- `OutboundProjectionService.java` — 주석만(호출은 원래 반환값을 버리고 `APPLIED` 였다).
- `ThroughputInboundDailyEntity.java` — javadoc «LWW guarded» 정정.
- 다른 읽기 모델(`*_summary` · `*_snapshot` · `*_ref`)은 손대지 않았다.

### 시험

| 시험 | 종류 | 로컬 | 비고 |
|---|---|---|---|
| `InboundProjectionServiceTest.putawayCompleted_reverseOrderAcrossAsns_bothAppliedNeverStale` (옛 `..._zeroAffectedRowsMappedToIgnoredDuplicateLate` 대체) | 단위 | ✅ | 🔴 저장소가 mock 이라 **SQL 의 버림은 못 문다** — 지키는 것은 «누계 경로에 stale outcome 이 없다» 뿐 |
| `InboundProjectionServiceTest.putawayCompleted_sameEventIdTwice_incrementsOnce` | 단위 | ✅ | dedupe 대조군 |
| `OutboundProjectionServiceTest.shippingConfirmed_sameEventIdTwice_incrementsOnce` | 단위 | ✅ | dedupe 대조군 |
| `ReadModelPersistenceIntegrationTest.throughput{Inbound,Outbound}Daily_{reverseOrderArrival,equalOccurredAt}_countsBothEvents` (4) | IT (Postgres) | ⚪ CI 전용 | **이것이 결함을 무는 칸** — 실제 SQL |
| `ReadModelPersistenceIntegrationTest.throughputInboundDaily_nativeUpsertIncrement_isAtomicAndCountsLateArrivals` (옛 `..._isAtomicAndLwwGuarded`) | IT | ⚪ CI 전용 | 🔴 옛 이름의 셋째 칸은 **결함을 단언하고 초록**이었다 — 이제 3 · 75+999 · `last_event_at` 은 최대값 유지 |
| `InboundProjectionKafkaIT.putawayCompleted_reverseOrderAcrossAsns_countsBoth` · `putawayCompleted_sameEventIdTwice_countsOnce` · `OutboundProjectionKafkaIT.shippingConfirmed_reverseOrderAcrossShipments_countsBoth` | IT (Kafka + Postgres) | ⚪ CI 전용 | 소비 경로 전체, 서로 다른 키 · 늦은 것 먼저 |

- 로컬: `./gradlew :projects:wms-platform:apps:admin-service:test` → **rc=0**, 결과 XML 56 파일 · `<testcase` 326 · `<failure`/`<error` 0 (`InboundProjectionServiceTest` 10 · `OutboundProjectionServiceTest` 9 포함). `test` 는 `@Tag("integration")` 을 제외하므로 위 IT 는 **이 숫자에 없다**.
- IT 는 Docker 부재로 로컬 실행 불가(§ AC-0 ①). CI 의 `Integration (master-service + notification-service + outbound-service, Testcontainers)` 잡(`:projects:wms-platform:apps:admin-service:integrationTest`)이 돌린다 — 그 잡의 초록을 본 뒤 AC-2 를 닫는다. 이번 기준선: 68 개(2026-10-22 main), 이 변경 뒤 기대 **75**(68 + 저장소 4 + Kafka 3 · 이름만 바뀐 1 은 그대로).

### 남은 것

- AC-2 의 CI 부분 · AC-3 (굽기 뒤 라이브: 데모 `putaway_count` 합 = 적치 완료 이벤트 수, 출고도). AC-0 ③ 도 그때.

## CI 빨강 → 초록 증거 · 머지 전 확인 (2026-10-22 UTC · 분석=Opus 5.5)

- 로컬에는 Docker 가 없어, 메인 세션이 **시험만 담은 첫 커밋 `44cb66958`** 만 올려 PR #4475(draft)를 열었다. CI run `39813085070` 의 `admin-service:integrationTest` **빨강** — 75 tests · **6 failed**, 정확히 새 시험들: `ReadModelPersistenceIntegrationTest` 의 입고 · 출고 × 역순 · 같은 시각 4개 + `InboundProjectionKafkaIT.putawayCompleted_reverseOrderAcrossAsns_countsBoth` · `OutboundProjectionKafkaIT.shippingConfirmed_reverseOrderAcrossShipments_countsBoth`. (같은 `event_id` 두 번 → 1회 시험은 중복 제거가 원래 막으므로 빨강이 아닌 것이 맞다.) ⇒ 결함 실재 · 시험이 문다.
- 이어서 스펙 `528c12c2f` · 수선 `2aff22e9b` 를 앞으로 푸시(강제 아님) → 같은 PR 의 head `2aff22e9b` 체크 **pending 0 · fail 0** → AC-2 닫힘.
- `TASK-BE-645`(#4474) · 티켓 PR(#4476) 머지 뒤 main 을 이 브랜치에 병합 — 충돌 없음.
- 열림: **AC-3**(굽기 뒤 라이브 — 데모 시드 · 적치 · 출하 뒤 누계가 이벤트 수와 같은가). 서버 변경 = 굽기 대기.

## 라이브 확인 (2026-10-23 UTC · 33차 창)

- 배포 상태: 굽기 `283fd39cf` → AMI `ami-02b3ba00a3b2ff622`(51분 36초 · 태그 RepoCommit 일치) · 핀 PR #4498(`c5e3ddad3`) · 인스턴스 `i-0f39aec49ddbaa899`. impl 머지 `a0383c947`(#4475) 는 `283fd39cf` 의 조상.
- **AC-3 ✅** — `admin_db`: `admin_event_dedupe` 의 `inbound.putaway.completed` = **87** · `outbound.shipping.confirmed` = **1**. `admin_throughput_inbound_daily` 2026-10-23: WH …0001 = 1 + WH …0002 = 86 = **87**. `admin_throughput_outbound_daily`: WH …0001 = **1**. 이벤트 수와 누계가 입고 · 출고 모두 일치.
- AC-0 ③(데모 창 필요로 ⚪ 였던 칸)도 위 대조로 채워졌다 — 출고 쪽은 고치기 전에 결과 기록이 없었다(AC-0 ②), 지금은 dedupe 행 수 = 누계.

### 닫기 — 4차원 확인 (2026-10-23 UTC)

- (a) PR #4475 `state=MERGED` · `mergedAt` 2026-10-22T16:56:59Z.
- (b) 머지 커밋 `a0383c947` = `origin/main` 조상(`git merge-base --is-ancestor`), 굽기 커밋 `283fd39cf` 의 조상이기도 하다.
- (c) 머지된 PR 의 `statusCheckRollup` 67 칸 중 실패 0 (SUCCESS 17 · SKIPPED 50).
- (d) `# Acceptance Criteria` 를 열어 읽음 — AC-0 · AC-1 · AC-2 ✅(기존 — AC-2 의 CI 부분은 § CI 빨강 → 초록 증거) · AC-3 ✅(위). 체크 칸은 `review/` 동결 규칙으로 고치지 않았다 — 닫힘은 이 절이 기록한다.
- ⇒ `review/ → done/`.
