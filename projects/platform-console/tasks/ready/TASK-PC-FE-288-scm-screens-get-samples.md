# Task ID

TASK-PC-FE-288

# Title

SCM 화면이 샘플로 선다 — 개요·조달·재고·보충 계획·보충 설정 (`ADR-MONO-074` 실행 7/8)

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

`TASK-PC-FE-282` 샘플 모드 위에서 **scm GET 을 전부 `ready`** 로 만든다.

⏳ **`TASK-PC-FE-282` 머지 전 착수 금지.** 🔴 283~288 직렬 머지.

화면: `/scm` · `/scm/guide`(정적) · `/scm/procurement` · `/scm/inventory` · `/scm/replenishment` · `/scm/config`

코어: `callScmGateway`(평면 봉투 코어 심 — 429 백오프 · 404-as-empty 센티널 · `X-Cache`). ADR 인벤토리: GET **10** · 쓰기 **4**.

---

# Scope

## In Scope

- 위 화면의 scm GET 픽스처 + 원장 `ready`

## Out of Scope

- 보충 정책 저장·제안 승인/기각 — `SAMPLE_READ_ONLY`

---

# Acceptance Criteria

- [ ] **AC-0** 282 AC-0 표·원장에서 scm `pending` GET 목록을 이 파일에 적는다.
- [ ] **AC-1** 전부 `ready` + 실제 파서 통과 테스트.
- [ ] **AC-2** «(샘플)» 규칙 테스트 초록(공급사명·품목명 끝). 🔴 SKU·공급사 코드·수량·상태 enum 제외.
- [ ] **AC-3** 🔴 발주의 공급사가 **UUID 로 보이지 않는다** — `TASK-MONO-677` 이 라이브에서 본 결함을 샘플이 재현하지 않되, 계약에 아직 없는 필드를 픽스처가 **지어내지도 않는다**(677 이 계약을 바꾸기 전이면 현재 계약 모양 + 공급사 목록 픽스처로 해석 가능한 범위만).
- [ ] **AC-4** `/scm/config` 의 404-as-empty: «설정 없음» 칸 하나를 픽스처로 **일부러** 두어 empty-state 가 보이는지 확인(센티널 경로가 샘플에서도 산다).
- [ ] **AC-5** `X-Cache` 헤더는 샘플에서 싣지 않는다 — 부재 시 화면이 거짓 캐시 표시를 안 하는지.
- [ ] **AC-6** 대표 쓰기 1개(제안 승인) → «샘플 화면에서는 실행되지 않습니다».
- [ ] **AC-7** `e2e-smoke` 익명 `/scm/procurement` 렌더 1칸.
- [ ] **AC-8** 🔴 **첫 화면 개요의 SCM 카드가 이 픽스처와 맞는다** (`TASK-PC-FE-285` CORRECTION 에서 추가). 지금 `fixtures/dashboards.ts` 의 scm 카드는
      손으로 적힌 노드 셋(`sample-node-01..03`, 평택·이천·부산)이다. console-bff 가 scm 카드에 부르는 조회를 **어댑터 코드에서** 확인하고, 같은 경로를
      이 티켓의 scm 픽스처 핸들러에 물어 카드 값을 **파생**한다 — 노드 수·노드 id·이름이 `/scm/inventory` 의 노드와 같아야 한다.
      `tests/unit/sample-overview-cards-match-lists.test.ts` 에 칸을 더하고 bite.

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md`
- `TASK-PC-FE-282` (기반)
- `tasks/ready/TASK-MONO-677-the-po-screen-shows-a-supplier-uuid-although-the-master-has-a-code-and-a-name.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.6 (scm)

# Edge Cases

- 샘플 라우터가 429 를 내지 않는다(백오프 경로 미진입) — 그 경로는 인증 테스트가 계속 지킨다.

# Failure Scenarios

- 677 머지 후 계약이 바뀌면 픽스처가 옛 모양 ⇒ AC-1 파서 테스트가 빨개져 알려 준다(의도된 결합).

# Test Requirements

- `pnpm lint` · `npx tsc --noEmit` · `pnpm test` · `pnpm test:e2e:smoke`(각각 독립 + `rc=$?`)

# Definition of Done

- [ ] 원장의 scm `pending` 0

분석=Opus 5 / 구현 권장=Sonnet 5.
