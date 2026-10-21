# Task ID

TASK-BE-643

# Title

admin-service 일별 처리량 누계가 «마지막 이벤트 시각» 조건 때문에 순서가 뒤바뀐 이벤트를 버린다 — 서로 다른 ASN · 주문의 이벤트는 파티션이 달라 순서가 보장되지 않으므로 누계가 실제보다 적어진다

# Status

ready

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

- [ ] **AC-0** — ①~④ 를 file:line · IT 출력과 함께 기록. ① 이 red 가 아니면(조건이 실제로는 안 무는 경로가 있으면) STOP 하고 이유를 적는다.
- [ ] **AC-1** — 스펙 두 곳 갱신(이전 근거 보존) → 구현.
- [ ] **AC-2** — 시험: 순서 뒤바뀜 · 같은 시각 두 건 → 2 · 같은 `event_id` 두 번 → 1 · 출고 동일 · 기존 IT 비회귀(CI 의 wms 잡).
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
