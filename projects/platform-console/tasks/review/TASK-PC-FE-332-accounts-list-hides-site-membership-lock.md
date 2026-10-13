# Task ID

TASK-PC-FE-332

# Title

콘솔 «계정 운영» 목록이 풀 계정의 **사이트 소속 잠금**을 보여 주지 않는다 — 잠금이 성공해도 상태 칸은 `ACTIVE` 그대로

# Status

review

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

- [x] **AC-0** — 위 ①~③ 을 file:line 으로 적고 방향을 정한다.

  **① 검색/목록 응답(`GET /api/admin/accounts`)에 이 사이트의 소속 상태가 이미 실려 있는가 → 아니다.**
  - 계약: `projects/iam-platform/specs/contracts/http/admin-api.md:194-209`(Response 200 — `id`/`email`/`status`/`createdAt` 뿐, 사이트별 필드 없음).
  - 생산자: `projects/iam-platform/apps/account-service/src/main/java/com/example/account/infrastructure/persistence/AccountQueryPortImpl.java:103-106`(`toItem` 은 `e.getStatus().name()` — **계정 자신의** 상태 — 풀 포함 분기 `findAllIncludingPoolMembers`/`findByEmailIncludingPoolMembers`(동 파일 64-87줄)도 같은 `toItem` 을 쓴다. 멤버십 테이블(`ConsumerSiteMembership`, `account-service/.../domain/consumerpool/ConsumerSiteMembership.java`)과 조인하지 않는다).
  - 콘솔 zod: `projects/platform-console/apps/console-web/src/shared/api/iam-accounts-types.ts:29-35`(`AccountSummarySchema` 필드 = `id`/`email`/`status`/`createdAt` 뿐).

  **② 잠금·해제 응답에 `scope`(`SITE_MEMBERSHIP`/`ACCOUNT`)가 실려 있는가 → 그렇다(계약에 이미 있었다). 콘솔만 그걸 안 읽고 있었다.**
  - 계약: `admin-api.md:260-271`(lock Response 200: `scope`, `siteTenantId`) · `admin-api.md:388-400`(unlock 동일).
  - 생산자: `account-service/.../application/service/AccountStatusUseCase.java:146-154`(`changeStatusAsTenantOperator` — 풀 계정 + 이름 있는 사이트 테넌트면 `siteMembershipLockUseCase.execute(...)` 로 가 `scope=SITE_MEMBERSHIP` 를 만든다) · `SiteMembershipLockUseCase.java:76-78`(`StatusChangeResult.siteMembership(...)`).
  - 콘솔(수정 전): `features/accounts/api/types.ts` 의 `LockResultSchema`/`UnlockResultSchema` 에 `scope`/`siteTenantId` 필드가 **없었다** — zod 는 안 선언된 필드를 조용히 버리므로, 하류가 이미 보낸 신호를 파서가 버리고 있었다(계약 변경 불필요 — `scope`는 TASK-BE-621 때부터 이미 응답에 있었다).

  **③ 대조군: 사이트 계정(비풀)의 잠금은 지금 상태 칸을 바꾸는가 → 그렇다(그대로 둬야 한다).**
  - `AccountStatusUseCase.java:146-154` — `changeStatusAsTenantOperator`: `account.getTenantId().isConsumerPool()` 이 거짓(비풀)이면 `else` 분기로 가 `applyStatusChange(account, command)`(동 파일 176줄)를 그대로 호출 → `account.changeStatus(...)` 로 **계정 자신의** `status` 가 실제로 바뀐다. 그래서 `AccountSearchResponse.Item.status` 도 다음 조회에서 바뀐다 — 지금 그대로.

  **방향(스코프 In Scope 2번째 항목에 따름)**: ①=아니오(검색 응답엔 신호 없음) · ②=예(잠금·해제 응답엔 이미 `scope`/`siteTenantId` 가 있다) → **계약 변경 불필요**. GDPR `siteScopeNotice`(TASK-BE-619)와 같은 모양으로, 콘솔 쪽에만 (a) `LockResultSchema`/`UnlockResultSchema` 에 `scope`/`siteTenantId` 파싱 추가, (b) 잠금 성공 시 `scope===SITE_MEMBERSHIP` 이면 행 안내 배너 표시, 해제 성공(또는 새 액션 오픈) 시 안내 사라짐. 상태 칸(= 계정 자신의 상태)은 고의로 그대로 둔다(Failure Scenario 1 회피). Failure Scenario 2(클라이언트 전용 기억)는 — 이 안내가 "방금 한 그 작업"의 **서버 응답을 직접 반영**하는 것이라 클라이언트가 지어낸 기억이 아니고, 새로고침 시 사라지는 것은 GDPR 선례와 같은 허용된 동작이다(AC-1 이 "다음 창"으로 미룬 라이브 미러링과는 별개 — 영구 신호가 필요했다면 거기서 STOP 했을 것).

- [x] **AC-1** — 풀 계정을 사이트 잠금 → 운영자가 화면에서 «이 사이트에서 잠김» 을 알 수 있다(행 표시 또는 안내) · 해제 → 사라진다(단위 시험).
  - 구현: `projects/platform-console/apps/console-web/src/features/accounts/components/AccountsScreen.tsx:104-107`(`siteLockNotice` state) · `:182-199`(잠금 성공 시 `scope===SITE_MEMBERSHIP` → 안내 설정) · `:200-211`(해제 성공 시 안내 해제) · `:311-323`(배너 렌더 `data-testid="accounts-site-lock-notice"`).
  - 단위 시험(bite 확인됨 — 구현 되돌려 재현): `projects/platform-console/apps/console-web/tests/unit/AccountsScreen.test.tsx`(`TASK-PC-FE-332 AC-1: a SITE_MEMBERSHIP lock says so … and an unlock clears the notice`) — 잠금 응답 `scope=SITE_MEMBERSHIP` 로 안내 노출 + 행 상태 칸은 `ACTIVE` 그대로임을 같이 단언, 이어서 해제 응답으로 안내 소거를 단언.

- [x] **AC-2** — 대조군: 사이트 계정(비풀)의 계정 잠금 표시는 지금과 같다.
  - 단위 시험: 동 파일 `TASK-PC-FE-332 AC-2 control: a non-pool (site account) lock shows no site-lock notice — unchanged from before`(`scope=ACCOUNT` 응답 → 안내 미노출) 및 `TASK-PC-FE-332 control: an old admin-service response with no \`scope\` field (back-compat) shows no site-lock notice`(옛 admin-service 응답 — `scope` 필드 자체가 없음 — 도 안내 미노출, 계약의 "scope 없는 응답은 ACCOUNT 로 읽는다" 그대로).

- [ ] **AC-3** — 라이브: 다음 데모 창에서 `cs@demo.com` 으로 잠금 → 표시 확인 → 해제. ⚪ 다음 창(27차, 날짜 미정) — 콘솔 코드·단위 시험은 이번 PR 에 있으나 실제 데모 환경에서의 눈으로 보는 확인은 그 창에서만 가능.

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
