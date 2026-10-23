# Task ID

TASK-MONO-808

# Title

`ADR-MONO-084` P3 — WMS 실적 지표: 일별 집계를 한국 시간 날짜로 옮기고 읽기 모델을 다시 쌓는다 · `metrics` (입고 수량 · 출고 수량 · 출고 건수) · 콘솔 개요 실적 줄

# Status

ready

# Owner

monorepo

# Task Tags

- code
- api
- data-migration
- wms
- platform-console
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (읽기 모델 다시 쌓기 = 운영 데이터 작업 · 투영 변경)
> 🔴 **이 티켓의 본체는 코드가 아니라 데이터다**(ADR-084 D3). 화면만 KST 로 바꾸면 숫자가 틀린 날에 붙는다.

# Dependency Markers

- **선행**: `TASK-MONO-806`(P1) `main` 머지.
- 형제: `TASK-MONO-807` · `809`~`812`.

# Goal

WMS 의 일별 입고 · 출고 집계는 지금 UTC 날짜로 잘려 저장된다(`InboundProjectionService.java:246` · `OutboundProjectionService.java:230`). 그래서 00:00–09:00 KST 의 작업이 전날에 붙는다. 이것을 KST 날짜로 옮기고, P1 공용 모양의 `metrics` 를 내려주고, 콘솔 개요 맨 위에 실적 줄을 붙인다.

# Scope

## In Scope

- 계약 먼저: `admin-service-api.md` 에 `GET /api/v1/admin/dashboard/metrics?period=week|month[&warehouseId=]` · 지표 `qtyReceived` · `qtyShipped` · `shipmentCount`(throughput 정의 그대로) · 경계 = KST. 기존 `/throughput` 의 날짜 의미가 KST 로 바뀐다는 것도 그 절에 적는다.
- 투영: 날짜 자르기를 `KstPeriodBounds`(P1)로 · 서비스 안 `ZoneOffset.UTC` 날짜 자르기 제거.
- 🔴 읽기 모델 다시 쌓기: 기존 `throughput_*_daily` 행을 KST 날짜로 다시 만든다. 방법(원천 이벤트 재생 · 원천 테이블에서 재계산 · 마이그레이션 SQL)은 AC-0 이 정한다.
- 콘솔: `features/wms-ops` 개요에 P1 «실적 줄» · 추세 카드의 «UTC 기준» 문구를 다시 쌓기가 끝난 뒤 제거(`lib/throughput.ts:11-17` 의 이유 주석도 함께) · 콘솔이 출고 목록을 잘라 세던 오늘/주간/월간(`kst-period.ts` 사용처)을 새 응답으로 대체할지 AC-0 에서 정한다.

## Out of Scope

- 새 WMS 지표(재고 조정 사유별 등) — 계약 변경으로 나중에.
- 창고 간 비교 화면.

# Acceptance Criteria

- [ ] **AC-0** — 🔴 **데이터 질문을 코드 질문보다 먼저**: 읽기 모델 행의 원천(투영이 받는 이벤트 · 그 이벤트가 아직 남아 있는가 · 원천 서비스 테이블에서 다시 셀 수 있는가) · 행 수(로컬 · 데모 시드) · 데모 구워진 시드에 이 행이 들어 있는가 · `/throughput` 소비자 전수. 방법을 하나 고르고 이유를 이 파일에 적는다.
- [ ] **AC-1** — 투영 시험: 15:30 UTC(= 다음 날 00:30 KST) 이벤트가 KST 다음 날에 붙는다(`Clock` 주입, `TZ=UTC` · `TZ=Asia/Seoul` 둘 다).
- [ ] **AC-2** — 🔴 보존 대조군: 다시 쌓기 전후로 **같은 전체 기간의 합계가 같다**(날 경계만 옮겨지고 총량은 보존 — ADR-084 Verification).
- [ ] **AC-3** — `metrics` 시험: 현재/직전 구간 · 일별 0 채우기 · 창고 필터 · 테넌트 격리.
- [ ] **AC-4** — 콘솔: 실적 줄 렌더 · 칸 실패 · 401 · 샘플 모드 응답. 다시 쌓기 전에는 «UTC 기준» 문구가 남는다.
- [ ] **AC-5** — 배포 상태(`main` · AMI · 데모 창 라이브 · 다시 쌓기를 데모에서 언제 돌리나)를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md` D3 · Consequences(🔴 다시 쌓기)
- `projects/wms-platform/specs/services/admin-service/architecture.md`

# Related Contracts

- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § `GET /api/v1/admin/dashboard/throughput` · 새 `/metrics`
- P1 공용 실적 응답 모양

# Edge Cases

- 다시 쌓는 도중 들어오는 새 이벤트 → 같은 날짜 행을 두 번 세지 않게(AC-0 방법에서 정한다).
- 이벤트 시각이 없는 옛 행 → AC-0 에서 수를 세고 처리를 적는다.

# Failure Scenarios

- 투영만 바꾸고 옛 행을 그대로 둔다 → 바꾼 날 이전은 UTC 날짜, 이후는 KST 날짜인 섞인 차트.
- 다시 쌓기를 기존 볼륨에서 안 돌린다 → 신선 볼륨인 CI 는 영원히 초록이다(신선 볼륨은 옛 행이 없다).
