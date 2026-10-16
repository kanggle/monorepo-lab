# Task ID

TASK-PC-FE-344

# Title

`ADR-MONO-083` P7 콘솔 후속 — Finance 메뉴 «업무 권한 없음» · 쓰기 버튼을 Finance 세분 역할로 (`permission-map.ts` `serverRequires` / `writeRequires` · 계약 § 2.4.3.4) · 운영 개요 Finance 카드

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (데이터 행 · 계약 표 — 규칙 신설 없음)
> ⏳ **머지 게이트 — «`TASK-MONO-794` 를 실은 AMI 굽기가 라이브 확인된 뒤에만 머지»** (소유자 결정 2026-10-16 UTC, `TASK-MONO-794` § 소유자 결정 · 순서 — 792 · 793 과 같다). 콘솔은 머지 즉시 Vercel 로 나가고 백엔드는 굽기에서만 나간다. 굽기 전에 머지하면 라이브 `GET /api/admin/me/domain-access` 가 아직 `FINANCE_OPERATOR` 만 내므로 **모든 운영자의 Finance · 원장 메뉴에 거짓 «업무 권한 없음» + 대사 해소 · 환율 갱신 버튼 숨김** — 서버는 열어 주는데 화면이 닫는다. 반대(굽기 뒤 · 이 PR 전)는 조회 세트 운영자에게 쓰기 버튼이 보이고 누르면 서버 403(화면 안 «권한 없음») — 안전 쪽 실패.

# Dependency Markers

- **선행**: `TASK-MONO-794`(머지 + 그 커밋을 담은 굽기 + 라이브 확인 — `TASK-MONO-672` § Next window — TASK-MONO-794). 이 티켓의 AC-0 이 그 라이브 확인을 다시 잰다.
- 형제: `TASK-PC-FE-342`(P5 SCM) · `TASK-PC-FE-343`(P6 ERP) — 같은 모양 · `TASK-PC-FE-338`(c-2 — 메뉴 단위 `serverRequires` · `writeRequires` 규칙, 이 티켓은 **데이터만** 더한다 — 계약 § 2.4.3.4 «Scales with P5–P8»).

# Goal

`TASK-MONO-794` 가 Finance 두 서비스(account · ledger)의 업무 인가를 세분했다(finance `iam-integration.md` § 업무 역할 — 정본). 콘솔의 Finance 메뉴 · 버튼 신호와 운영 개요 Finance 카드를 서버 판정과 같게 만든다.

# Scope

## In Scope

- 계약 먼저: `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4 — «SCM · ERP · Finance leaves» 줄에서 Finance 행을 떼어 아래 대조표로 · «Scales with P5–P8» 에 P7 완료 표시.
- `apps/console-web/src/shared/guide/permission-map.ts` Finance 행(`/finance` · `/finance/accounts` · `/ledger`, `/finance/guide` 제외 — 지금 `roles: ['FINANCE_OPERATOR']`, `:743-775`):
  - `gate.roles` = Finance 카탈로그 역할(상한) — `FINANCE_OPERATOR` 단독이 아니라 5개 업무 역할.
  - `serverRequires`(기본 조회) · `writeRequires`(변경 버튼) — 아래 대조표 대로.
- 운영 개요(`/dashboards/overview`)의 Finance 카드: 원장 조회 역할이 없는 운영자에게 `forbidden` 으로 정직하게 보이는지(지금 규칙) — 카드가 부르는 갈래(계좌 · 원장 시산표)를 AC-0 에서 재고 대조표에 적는다.
- 시험: `permission-map-drift` · 메뉴 신호 · 버튼 숨김 단위 시험.

## Out of Scope

- 서버 판정 변경(794 에서 끝났다) · E-commerce(P8 의 콘솔 후속).
- 원장 **통제 쓰기 7**(기간 개시 · 마감 · 환율 덮어쓰기 · 허용오차 · 원가흐름 설정)의 화면 — 콘솔에 호출자가 없다(`TASK-MONO-794` AC-0 § 3). 새 화면을 만들지 않는다.
- 새 신호 규칙(계약 § 2.4.3.4 의 규칙은 그대로 — 데이터만).

# 서버 판정 대조표 (`TASK-MONO-794` 기준 — AC-0 에서 다시 잰다)

| 화면 | 기본 조회가 부르는 것 | `serverRequires` | 변경 버튼 | `writeRequires` |
|---|---|---|---|---|
| `/finance` 개요 | (데이터 없이 렌더 — AC-0 에서 재확인) | — | — | — |
| `/finance/accounts` | account `GET /accounts/{id}` · `/balances` · `/transactions` | `FINANCE_ACCOUNT_READ` · `FINANCE_ACCOUNT_WRITE` | (READ-ONLY — 콘솔은 계좌 쓰기를 부르지 않는다) | — |
| `/ledger` | ledger 조회(시산표 · 기간 · 대사 · 환율 · 로트 …) | `FINANCE_LEDGER_READ` · `FINANCE_LEDGER_WRITE` · `FINANCE_LEDGER_CONTROL` | 대사 해소 · 환율 갱신 | `FINANCE_LEDGER_WRITE` (🔴 `FINANCE_LEDGER_CONTROL` 은 이 두 버튼을 열지 **않는다** — 독립) |
| 운영 개요 Finance 카드 | AC-0 에서 확정 | AC-0 | — | — |

# Acceptance Criteria

- [ ] **AC-0** — 🔴 머지 게이트 실측: `TASK-MONO-794` 를 담은 AMI 가 라이브인가(굽기 기록 · `GET /api/admin/domain-roles` 가 `FINANCE_ACCOUNT_READ` 를 내는가 · 라이브 `/me/domain-access` 가 데모 운영자에게 Finance 5 역할을 내는가). 아니면 **이 티켓은 시작하지 않는다**. 위 대조표를 서버 코드(account · ledger `SecurityConfig`)와 다시 대조.
- [ ] **AC-1** — 계약 § 2.4.3.4 개정이 코드보다 먼저.
- [ ] **AC-2** — 🔴 대조군: 조회 세트(`finance-read`) → Finance 메뉴 배지 없음 · 쓰기 버튼 없음 / 계좌 세트만(`finance-account-operate`) → `/ledger` 배지 / 원장 운영 세트(`finance-ledger-operate`) → `/finance/accounts` 배지, `/ledger` 버튼 보임 / 원장 통제만 → `/ledger` 배지 없음 · 버튼 없음 / Finance 세트 0 → Finance 메뉴 전부 배지.
- [ ] **AC-3** — 실패 경로: `/me/domain-access` 실패 · 시간 초과 → 배지 없음 · 버튼 보임(지금 규칙 그대로).
- [ ] **AC-4** — 배포 상태(Vercel · 라이브 화면)를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D9 · P7
- `projects/finance-platform/specs/integration/iam-integration.md` § 업무 역할 (서버 판정 정본)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4
- `projects/iam-platform/specs/contracts/http/admin-api.md` § 카탈로그 (`/me/domain-access`)

# Edge Cases

- `FINANCE_OPERATOR` 는 `/me/domain-access` 에 **나오지 않는다**(카탈로그 밖 · auth 파생) — 신호 입력으로 쓰지 않는다.
- `FINANCE_LEDGER_CONTROL` 의 카탈로그 `access` 는 `WRITE` 다(와이어 값 셋 — `READ`/`WRITE`/`OPERATE`). «쓰기 역할이 하나라도 있으면 버튼» 같은 `access` 기반 추론을 쓰면 통제 보유자에게 대사 해소 버튼이 거짓으로 보인다 — 역할 이름으로 판정한다.
- 테넌트를 고르지 않은 운영자(기본 토큰 — 역할 0)는 Finance 전 화면이 서버 403 이다(794 — `ROLE_FINANCE_VIEWER` 제거, SCM · ERP 와 같음) — 신호도 «업무 권한 없음» 이 맞다.

# Failure Scenarios

1. 굽기 전에 머지 — 위 머지 게이트의 거짓 배지 · 버튼 숨김.
2. `FINANCE_OPERATOR` 를 `serverRequires` 에 넣는다 — 진입 역할은 업무 API 를 열지 않으므로 신호가 서버보다 넓어진다.
3. `/ledger` 의 `writeRequires` 에 `FINANCE_LEDGER_CONTROL` 을 넣는다 — 통제 보유자에게 서버가 403 할 버튼이 보인다.
