# Task ID

TASK-PC-FE-286

# Title

Finance·원장 화면이 샘플로 선다 — 개요·계좌·원장(시산표·분개·기간·대사·환율) (`ADR-MONO-074` 실행 5/8)

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

`TASK-PC-FE-282` 샘플 모드 위에서 **finance + ledger GET 을 전부 `ready`** 로 만든다.

⏳ **`TASK-PC-FE-282` 머지 전 착수 금지.** 🔴 283~288 직렬 머지.

화면: `/finance` · `/finance/accounts` · `/finance/guide`(정적) · `/ledger`

코어: `callFlatEnvelopeGateway` (finance account-service · ledger-service). ADR 인벤토리: GET **15**(finance 3 + ledger 12) · 쓰기 **2**.

---

# Scope

## In Scope

- 위 화면의 finance/ledger GET 픽스처 + 원장 `ready`
- 🔴 **복식부기 산술이 맞는** 합성 세계(시산표 차변 합 = 대변 합, 분개 합 = 계정 잔액 변동)

## Out of Scope

- 기간 마감·대사 확정 등 쓰기 — `SAMPLE_READ_ONLY`

---

# Acceptance Criteria

- [ ] **AC-0** 282 AC-0 표·원장에서 finance/ledger `pending` GET 목록을 이 파일에 적는다.
- [ ] **AC-1** 전부 `ready` + 실제 파서 통과 테스트.
- [ ] **AC-2** «(샘플)» 규칙 테스트 초록(계정명·적요 끝). 🔴 금액·통화·계정 코드·기간 상태 enum 제외.
- [ ] **AC-3** 🔴 **산술 불변식 테스트**: 시산표 차변 합 = 대변 합 · 분개 라인의 계정별 합 = 시산표 해당 계정 값. 합성 원장이 안 맞으면 이 화면의 존재 이유(정합)를 거꾸로 보여 준다.
- [ ] **AC-4** 🔴 금액은 **합성임이 분명한 규모**로 두고(실재 회사 재무로 오인되지 않게), 통화·소수 자릿수는 실제 파서가 기대하는 표현(`money` 타입)을 따른다.
- [ ] **AC-5** 대표 쓰기 1개 → «샘플 화면에서는 실행되지 않습니다».
- [ ] **AC-6** `e2e-smoke` 익명 `/ledger` 렌더 1칸.
- [ ] **AC-7** 🔴 **첫 화면 개요의 finance 카드가 이 픽스처와 맞는다** (`TASK-PC-FE-285` CORRECTION 에서 추가). 지금 `fixtures/dashboards.ts` 의 finance 카드는
      손으로 적힌 값(계정 `sample-account-0001` · 잔액 `1250000000` KRW)이다. console-bff 가 finance 카드에 부르는 조회를 **어댑터 코드에서** 확인하고,
      같은 경로를 이 티켓의 finance 픽스처 핸들러에 물어 카드 값을 **파생**한다(IAM·ERP·E-Commerce 카드가 이미 그렇게 한다). 그 계정은 `/finance/accounts`
      에서 찾아져야 하고 잔액이 같아야 한다. `tests/unit/sample-overview-cards-match-lists.test.ts` 에 칸을 더하고 bite.

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md`
- `TASK-PC-FE-282` (기반)
- `projects/platform-console/tasks/done/TASK-PC-FE-160-finance-landing-overview-snapshot.md` (🔴 finance 는 목록 GET 이 없다 — 개요가 합성 ₩ 를 만들지 않는다는 기존 결정을 픽스처가 뒤집지 않는다)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` (finance · ledger 절)

# Edge Cases

- 기본 계좌 id(`getFinanceDefaultAccountId`)는 세션/env 에서 온다 — 샘플 방문자일 때의 값이 픽스처 계좌와 일치해야 한다(282 가 판정한 분기에서 확인).
- 환율 표의 기준일이 «미래» 로 보이지 않게(🔴 KST 호스트 주의 — 날짜는 UTC 로).

# Failure Scenarios

- 차대 불일치 ⇒ AC-3.
- 기본 계좌 id 불일치로 개요 칸 degrade ⇒ Edge Case 1.

# Test Requirements

- `pnpm lint` · `npx tsc --noEmit` · `TZ=UTC pnpm test` · `pnpm test:e2e:smoke`(각각 독립 + `rc=$?`)

# Definition of Done

- [ ] 원장의 finance/ledger `pending` 0

분석=Opus 5 / 구현 권장=Sonnet 5.
