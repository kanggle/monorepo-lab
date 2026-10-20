# Task ID

TASK-PC-FE-355

# Title

ERP 결재 화면의 콘솔 오류 둘 — ① 콘솔에서 결재 요청을 상신하면 «요청을 처리하지 못했습니다»(같은 DRAFT 를 API 로 상신하면 200) ② 위임 «내가 위임한» 칸이 «결재 서비스를 일시적으로 사용할 수 없습니다» 와 «위임한 grant 가 없습니다» 를 함께 그린다(API 는 200 `[]`)

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet(원인이 콘솔 판정 · 스키마 한 곳이면) · Opus(BFF ↔ 게이트웨이 계약 차이면)
> 기안 2026-10-20 UTC — 32차 데모 창(`TASK-MONO-672` § 32nd window — harvest)의 발견. 측정 먼저(AC-0).

# Dependency Markers

- 출처: `TASK-MONO-672` 32차 창 · `TASK-PC-FE-308` · `TASK-PC-FE-347` 의 32차 기록.
- 선행 없음.

# Goal

1. **상신**: `requester@demo.com`(`demo-corp`, `<domain>-full` 다섯)이 콘솔 «결재 요청 작성» 으로 «308 반려 확인»(대상 `DEPT-HQ`, 결재자 `EMP-0001` 김본부)을 만들자 **DRAFT 는 생겼고**(18:14:02Z) 상신 단계에서 «요청을 처리하지 못했습니다» 가 떴다. 같은 DRAFT 를 같은 주체 토큰으로 `POST /api/erp/approval/requests/{id}/submit` 하면 **200 `SUBMITTED`**(18:16Z, 에이전트). erp approval · gateway 로그에 그 시각 오류 줄 없음(OTel 수출 실패 잡음 제외) ⇒ 요청이 서버에 안 닿았거나, 닿아 2xx 인데 콘솔이 실패로 읽었다.
2. **위임 «내가 위임한»**: `erp-read` 단독 · `erp-approval-operate` 단독 두 세트 모두, 새로고침 뒤에도 같은 칸에 오류 문구와 빈 목록 문구가 **함께** 나온다. 같은 주체 토큰으로 `GET /api/erp/approval/delegations?role=DELEGATOR` → **200 `{"data":[],…}`**, `?role=DELEGATE` → 200(1 건). ⇒ 콘솔이 성공 응답의 어딘가(빈 목록 · `meta.size=0` · 다른 호출)를 실패로 판정한다.

둘 다 콘솔이 성공을 실패로 말하는 모양이라 한 티켓에서 잰다. 원인이 다르면 AC-0 뒤에 나눈다.

# Scope

## In Scope

- **AC-0 측정**:
  - ① 콘솔 «결재 요청 작성» 의 호출 사슬(`ApprovalCreateDialog.tsx` → 훅 → BFF `app/api/erp/approval/requests/**` → `flat-envelope-gateway.ts`) — 만들기와 상신이 한 번에 도는가, 상신에 무엇을 싣는가(`Idempotency-Key` · `X-Operator-Reason` · 본문). 응답 스키마(zod)가 실제 200 본문(`data.route` 등)을 받아 주는가. Vercel 런타임 로그(18:14Z 근처)에 그 BFF 의 결과가 있으면 그것을 읽는다(소유자에게 요청).
  - ② «내가 위임한» 칸이 부르는 것 전부(목록 · 이름 해석 · 그 밖) 와 각 판정 — 빈 `data` · `meta.size=0` 을 어떻게 읽는가. 같은 응답의 «나에게 위임된» 은 정상으로 그린다(차이를 찾는다).
- 수선: 성공을 성공으로 그린다. 진짜 실패는 지금처럼 말한다.
- 시험: 실측 응답 모양을 고정값으로 — 상신 200 본문 → 성공 처리 · 빈 위임 목록 → 빈 목록 문구만(오류 없음) · 진짜 503 → 오류 문구.

## Out of Scope

- 위임 화면이 직원 id(`01a157fa-…`)를 이름 대신 그리는 것 — 따로 본다(AC-0 에서 원인만 적는다).
- 서버(approval-service) 변경 — AC-0 이 서버 문제를 보이면 erp 티켓 먼저.

# Acceptance Criteria

- [ ] **AC-0** — ①② 의 원인을 file:line 으로(실측 응답 · 콘솔 판정 지점). 원인이 둘이면 나눌지 적는다.
- [ ] **AC-1** — 수선(문구가 바뀌면 소유자 문안 승인).
- [ ] **AC-2** — 시험: 위 세 고정값 + 기존 비회귀(결재 화면 · 위임 화면 스위트).
- [ ] **AC-3** — 배포(Vercel) · 라이브(콘솔에서 상신 → `SUBMITTED` · «내가 위임한» 에 오류 문구 없음, 다음 데모 창).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/erp-platform/specs/contracts/http/approval-api.md`(있는 이름으로 — AC-0 에서 확인) · `projects/platform-console/specs/contracts/console-integration-contract.md` § ERP

# Edge Cases

- 상신이 실제로 두 번 나가 두 번째가 409 · 422 로 실패할 수 있다(만들기 + 상신 사이) — AC-0 이 호출 수를 센다.
- 데모 데이터: 이 창이 만든 «308 반려 확인» 은 반려됨 상태로 남는다(다음 굽기가 지운다).

# Failure Scenarios

1. 오류 문구만 지우고 진짜 실패도 조용해진다 — 시험의 503 고정값이 막는다.
2. 고정값을 손으로 만든 모양으로 쓴다 — 실측 응답(이 창의 200 본문)으로 쓴다.
