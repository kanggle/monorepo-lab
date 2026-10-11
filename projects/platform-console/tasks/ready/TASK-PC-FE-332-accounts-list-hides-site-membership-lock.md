# Task ID

TASK-PC-FE-332

# Title

콘솔 «계정 운영» 목록이 풀 계정의 **사이트 소속 잠금**을 보여 주지 않는다 — 잠금이 성공해도 상태 칸은 `ACTIVE` 그대로

# Status

ready

# Owner

platform-console

# Task Tags

- platform-console
- iam
- ux

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 목록 응답에 소속 상태가 이미 있는지(AC-0)에 따라 콘솔만 또는 admin 응답 한 칸.

---

# Dependency Markers

- 출처: 26차 데모 창(2026-10-11 UTC, AMI `ami-00701c1fa528b5866`) — `TASK-PC-FE-326` 라이브 확인 중 소유자 발견.
- 관련: `TASK-PC-FE-326`(검색 전용 계정 운영) · `TASK-MONO-781`(`cs@demo.com` · `SUPPORT_LOCK`) · `TASK-BE-619`(GDPR `SITE_MEMBERSHIP` 범위 안내 — 같은 «계정 vs 사이트 소속» 구분을 이미 말로 보여 주는 선례).

# Goal

26차 창 실측: `cs@demo.com`(홈 `ecommerce`)이 `demo@demo.com`(풀 계정)을 검색해 **잠금** → account-service 로그 `consumer-site lock: account 0199de70-…ec01 on site ecommerce by operator demo-cs (ACTIVE -> LOCKED)`(09:47:06Z) → **해제** `(LOCKED -> ACTIVE)`(09:47:33Z). 둘 다 실제로 됐다. 그런데 목록의 상태 칸(`AccountsTable.tsx:86` `AccountStatusBadge status={acc.status}`)은 **계정** 상태라 풀 계정에선 사이트 잠금 뒤에도 `ACTIVE` 다. 운영자는 «잠금이 안 됐다» 로 읽었다(소유자 원문 *«잠금을 했는데 active상태가 변경되지 않아»*).

같은 구분을 GDPR 삭제는 이미 말로 보여 준다(`AccountsScreen.tsx` `siteScopeNotice` — `scope === 'SITE_MEMBERSHIP'`). 잠금 · 해제에는 그 신호가 없다.

# Scope

## In Scope

- **AC-0 = 재측정** — ① `GET /api/admin/accounts`(검색) 응답 item 에 이 사이트의 소속 상태가 이미 실려 있는가(admin `admin-api.md` § accounts · account-service 응답) ② 잠금 · 해제 응답에 `scope`(`SITE_MEMBERSHIP` / `ACCOUNT`)가 실려 있는가(`LockResultSchema` · `UnlockResultSchema`, `features/accounts/api/types.ts:45-65`) ③ 사이트 계정(비풀)의 잠금은 지금 상태 칸을 바꾸는가(대조군).
- AC-0 결과에 따라: 목록 행에 «이 사이트에서 잠김» 표시(소속 상태가 응답에 있으면) **또는** 잠금 · 해제 성공 뒤 GDPR 처럼 범위를 말로 알리는 안내 + 목록 재조회. 계약 변경이 필요하면 계약 먼저.

## Out of Scope

- 잠금의 의미 변경(사이트 소속 잠금 = 그 사이트만 — `ADR-MONO-080` D5 · 078 그대로).

# Acceptance Criteria

- [ ] **AC-0** — 위 ①~③ 을 file:line 으로 적고 방향을 정한다.
- [ ] **AC-1** — 풀 계정을 사이트 잠금 → 운영자가 화면에서 «이 사이트에서 잠김» 을 알 수 있다(행 표시 또는 안내) · 해제 → 사라진다(단위 시험).
- [ ] **AC-2** — 대조군: 사이트 계정(비풀)의 계정 잠금 표시는 지금과 같다.
- [ ] **AC-3** — 라이브: 다음 데모 창에서 `cs@demo.com` 으로 잠금 → 표시 확인 → 해제. ⚪ 다음 창.

# Related Specs

- `projects/platform-console/apps/console-web/src/features/accounts/components/AccountsTable.tsx` · `AccountsScreen.tsx`
- `projects/iam-platform/specs/features/multi-tenancy.md` § 소비자 계정 풀(사이트 소속)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § accounts(lock · unlock · search)

# Edge Cases

- 같은 풀 계정이 두 사이트에 소속 — 목록은 **지금 테넌트의 사이트** 소속만 말해야 한다(다른 사이트 잠금이 새지 않게).
- 계정 자체가 `LOCKED`(계정 잠금)이면서 사이트 소속도 잠김 — 두 신호를 섞지 않는다.

# Failure Scenarios

1. 상태 칸을 «소속 상태» 로 갈아 끼운다 — 사이트 계정(비풀)의 계정 잠금 표시가 사라진다(대조군 AC-2).
2. 클라이언트에서 «방금 잠갔다» 를 기억해 표시만 바꾼다 — 새로고침하면 사라지고 다른 운영자는 못 본다.
