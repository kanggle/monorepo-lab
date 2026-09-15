# Task ID

TASK-PC-FE-285

# Title

ERP 화면이 샘플로 선다 — 개요·결재·위임·마스터·조직도 (`ADR-MONO-074` 실행 4/8)

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

`TASK-PC-FE-282` 샘플 모드 위에서 **erp 도메인 GET 을 전부 `ready`** 로 만든다.

⏳ **`TASK-PC-FE-282` 머지 전 착수 금지.** 🔴 283~288 직렬 머지.

화면: `/erp` · `/erp/approval` · `/erp/delegation` · `/erp/guide`(정적) · `/erp/masters` · `/erp/orgview`

코어: `callFlatEnvelopeGateway` (erp masterdata · approval · delegation 클라이언트). ADR 인벤토리: GET **18** · 쓰기 **20**.

---

# Scope

## In Scope

- 위 화면의 erp GET 픽스처 + 원장 `ready`
- 개요 타일의 집계(마스터 5종 수 · 결재 대기 · 활성 위임)가 **목록 픽스처에서 파생**되어 서로 맞는다

## Out of Scope

- 결재 전이·위임 부여/회수 — `SAMPLE_READ_ONLY`

---

# Acceptance Criteria

- [ ] **AC-0** 282 AC-0 표·원장에서 erp `pending` GET 목록을 이 파일에 적는다.
- [ ] **AC-1** 전부 `ready` + 실제 파서 통과 테스트.
- [ ] **AC-2** «(샘플)» 규칙 테스트 초록(부서명·직원명·결재 제목 끝). 🔴 사번·부서 코드·상태 enum 제외.
- [ ] **AC-3** 🔴 **개요 수 = 목록 행 수**(마스터 5종·결재 대기·활성 위임). 개요와 목록이 다른 말을 하면 합성이어도 «고장» 이다 — 테스트로 문다.
- [ ] **AC-4** 조직도의 부서 참조·결재선의 결재자가 마스터 픽스처 안에서 **해석된다**(`masterRefLabel` 가 id 로 되돌아가지 않는다).
- [ ] **AC-5** 대표 쓰기 1개(결재 승인) → «샘플 화면에서는 실행되지 않습니다».
- [ ] **AC-6** `e2e-smoke` 익명 `/erp/approval` 렌더 1칸.

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md`
- `TASK-PC-FE-282` (기반)
- `docs/adr/ADR-MONO-050-cross-service-identifiers-are-codes.md` D9 (참조는 코드)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` (erp 절)

# Edge Cases

- 결재 이유(`X-Operator-Reason`) 입력 후 제출 → 거부, 입력 유지.
- 404-as-empty 가 없는 도메인이므로 없는 id 는 일반 404.

# Failure Scenarios

- 개요와 목록의 수 불일치 ⇒ AC-3.
- 참조가 id 로 표시 ⇒ AC-4.

# Test Requirements

- `pnpm lint` · `npx tsc --noEmit` · `pnpm test` · `pnpm test:e2e:smoke`(각각 독립 + `rc=$?`)

# Definition of Done

- [ ] 원장의 erp `pending` 0

분석=Opus 5 / 구현 권장=Sonnet 5.
