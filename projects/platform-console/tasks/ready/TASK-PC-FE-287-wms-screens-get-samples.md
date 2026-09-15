# Task ID

TASK-PC-FE-287

# Title

WMS 화면이 샘플로 선다 — 개요·입고·재고·마스터·작업·출고 (`ADR-MONO-074` 실행 6/8)

# Status

ready

# Owner

platform-console

# Task Tags

- code
- test
- demo

---

# Goal

`TASK-PC-FE-282` 샘플 모드 위에서 **wms GET 을 전부 `ready`** 로 만든다.

⏳ **`TASK-PC-FE-282` 머지 전 착수 금지.** 🔴 283~288 직렬 머지.

화면: `/wms` · `/wms/guide`(정적) · `/wms/inbound` · `/wms/inventory` · `/wms/master` · `/wms/operations` · `/wms/outbound`

코어: `callWmsGateway` (admin read-model · outbound). ADR 인벤토리: GET **11** · 쓰기 **6**.

---

# Scope

## In Scope

- 위 화면의 wms GET 픽스처 + 원장 `ready`
- 🔴 wms 는 **NESTED 에러 봉투**(`{ error: { code } }`)다 — 샘플 라우터의 거부/준비 중 응답이 이 도메인에서는 그 모양이어야 코드가 보존된다

## Out of Scope

- 피킹·패킹·출고 확정 — `SAMPLE_READ_ONLY`

---

# Acceptance Criteria

- [ ] **AC-0** 282 AC-0 표·원장에서 wms `pending` GET 목록을 이 파일에 적는다.
- [ ] **AC-1** 전부 `ready` + 실제 파서 통과 테스트.
- [ ] **AC-2** «(샘플)» 규칙 테스트 초록(창고명·SKU 이름·거래처명 끝). 🔴 `locationCode`·`skuCode`·`lotNo`·`warehouseCode`·수량·상태 enum 제외.
- [ ] **AC-3** 🔴 **코드 칸이 `null` 이 아니다** — `TASK-MONO-675` 가 라이브에서 본 «필드는 있는데 값이 전부 null» 을 샘플이 재현하지 않는다. 재고 행의 코드가 마스터 픽스처 안에서 해석된다.
- [ ] **AC-4** `SAMPLE_READ_ONLY` · `SAMPLE_NOT_READY` 가 wms 코어(`parseWmsError`)를 거쳐 **코드가 보존되는지** 테스트(평면 봉투로 주면 `HTTP_403` 으로 뭉개진다).
- [ ] **AC-5** `X-Read-Model-Lag-Seconds` 는 샘플에서 **안 싣는다**(지연 힌트는 사실이 아니므로) — 헤더 부재 시 `lagSeconds=null` 이 화면에 거짓 경고를 안 띄우는지 확인.
- [ ] **AC-6** 대표 쓰기 1개(출고 확정) → «샘플 화면에서는 실행되지 않습니다».
- [ ] **AC-7** `e2e-smoke` 익명 `/wms/inventory` 렌더 1칸.

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md`
- `TASK-PC-FE-282` (기반)
- `tasks/ready/TASK-MONO-675-the-denormalised-codes-are-null-because-the-ref-tables-are-empty.md` (라이브 결함 — 샘플이 그것을 **가리지도 재현하지도** 않는다)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.5 (wms)

# Edge Cases

- 출고 경로는 per-call baseUrl override 를 쓴다 — 샘플 라우터 매칭이 base 가 아니라 **경로**로 되는지 확인.

# Failure Scenarios

- 평면 봉투 거부 응답 → 코드 소실 → 거부 문구 대신 일반 에러 ⇒ AC-4.

# Test Requirements

- `pnpm lint` · `npx tsc --noEmit` · `pnpm test` · `pnpm test:e2e:smoke`(각각 독립 + `rc=$?`)

# Definition of Done

- [ ] 원장의 wms `pending` 0

분석=Opus 5 / 구현 권장=Sonnet 5.
