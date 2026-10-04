# Task ID

TASK-BE-621

# Status

done (2026-10-04 UTC — 4차원 검증 · PR #4141 squash `7a127c39a`)

# Title

사이트 운영자의 잠금은 그 사이트에만 — 풀 계정 멤버십 `LOCKED` · 계정 전체 잠금은 플랫폼 관리자만 (`TASK-BE-619` 의 삭제 결정을 잠금·해제로)

# Owner

iam-platform

# Task Tags

- account-service
- admin-service
- consumer-pool
- follow-up

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (상태 기계 · 멤버십 상태 추가 · 운영자 판별 — `TASK-BE-619` 와 같은 결)

---

# Dependency Markers

- **출처**: `TASK-BE-619` (done) § 배포 순서 · 남은 것 / § 닫기 — «사이트 운영자의 **잠금**은 여전히 풀 계정 하나에 걸린다(616 결정). 잠금도 사이트 단위여야 하나는 별도 결정.» 이 티켓이 그 결정의 집이다.
- **선행**: `TASK-BE-616`(잠금이 풀 계정 하나에 걸린다는 결정 — 이 티켓이 바꾼다) · `TASK-BE-619`(삭제의 같은 변경 — `V0032` · `ConsumerSiteMembership.isReopenableByConsent` · 운영자 판별 `isPlatformScope` → `'*'`). 둘 다 done.
- **후속**: 없음(아래 «소유자 결정 필요» 둘과 «후속» 은 결정·화면 몫).

# 소유자 결정 (2026-10-04 UTC — 코디네이터 전달, 원문 그대로)

> «사이트 운영자(예: 스토어 운영자)가 회원을 잠글 때, 그 잠금은 자기 사이트에만 걸린다 (Recommended 선택). 계정 전체 잠금은 플랫폼 관리자만.»

맥락: 지금 사이트 운영자의 잠금은 소비자 풀 계정 **하나**를 잠가, 스토어 운영자가 잠근 사람이 팬 사이트에도 못 들어간다(`TASK-BE-616` 의 «쓰기의 범위는 계정 하나»). `TASK-BE-619` 는 같은 문제를 **삭제**에 대해 «사이트 운영자의 삭제 = 그 사이트 멤버십만 `LEFT`(`OPERATOR`)» 로 풀었다. 이 티켓은 그 설계를 **잠금 · 해제**에 그대로 옮긴다.

# Goal

사이트 운영자가 풀 계정 회원을 잠그면 **그 사이트 멤버십만** 잠기고, 그 사람은 다른 소비자 사이트를 그대로 쓴다. 계정 전체 잠금(과 그 해제)은 플랫폼 관리자만 한다. 해제는 잠금과 대칭이다.

# Scope

## In Scope

1. **잠금 경로 전수와 분류** — 계정을 잠그는 모든 경로를 찾아 «사이트 운영자 / 플랫폼 관리자 / 자동» 으로 가른다(아래 § 잠금 경로 전수). 판별은 `TASK-BE-619` D-2 와 같다: 플랫폼 관리자 = admin-service `QueryTenantScopeGate.Resolved.isPlatformScope`(운영자의 home 테넌트 `'*'`), 그 밖의 운영자 = 사이트 운영자. 콘솔은 늘 활성 테넌트를 보내므로 헤더로는 가를 수 없다 — 역할로 가른다.
2. **사이트 운영자의 잠금 · 해제** (콘솔 lock · unlock · bulk-lock → account-service `/internal/accounts/{id}/lock` · `/unlock`, 헤더 = 사이트) — 대상이 그 사이트의 **풀 멤버**면 계정이 아니라 **그 사이트 멤버십**의 상태를 바꾼다. 그 사이트의 **자기 계정**(풀 아님)은 지금처럼 계정을 잠근다.
3. **플랫폼 관리자의 잠금 · 해제** — admin-service 가 플랫폼 스코프 운영자에게 하류 `'*'` 를 찍는다(619 의 GDPR 삭제와 같다) → account-service 는 계정 행 자신의 테넌트로 찾아 **계정 전체**를 잠근다(MONO-735 경로 그대로).
4. **멤버십 상태 `LOCKED` 추가** — account-service `V0033`(상태 CHECK 교체 + 잠금 기록 두 컬럼). 근거는 § 설계.
5. **사이트 테넌트로 찾는 표면의 술어** — «그 사이트 멤버» 를 `ACTIVE` 에서 `ACTIVE ∨ LOCKED` 로(잠긴 회원이 그 사이트 목록·단건 조회에서 사라지면 해제할 길이 없다). `LEFT` 는 여전히 보이지 않는다.
6. **계약 먼저**: `multi-tenancy.md` § 소비자 계정 풀 § 5 · `admin-to-account.md` · `admin-api.md` · `auth-to-account.md`(consumer-members `membershipStatus` 값) · account-service `data-model.md` · `account-lifecycle.md`.

## Out of Scope

- 자동 잠금(security-service `AUTO_DETECT`) · 본인 복구 해제(auth-service `USER_RECOVERY`) — 계정 전체 그대로(아래 «소유자 결정 필요» 1).
- 내부 프로비저닝 `PATCH /internal/tenants/{t}/accounts/{id}/status` — 바꾸지 않는다(아래 «소유자 결정 필요» 2).
- 콘솔·스토어·팬 화면 문구(아래 «후속»).

# 잠금 경로 전수 (2026-10-04 UTC, `main` `eebeda112` 에서 grep)

| # | 경로 | 호출자 · 행위자 | 하류 헤더 (지금) | 분류 | 이 티켓 |
|---|---|---|---|---|---|
| 1 | 콘솔 `POST /api/admin/accounts/{id}/lock` · `/unlock` · `/bulk-lock` → admin-service `AccountAdminUseCase` → account `POST /internal/accounts/{id}/lock` · `/unlock` | 운영자. 사이트 운영자(home ≠ `'*'`) | 활성 테넌트 | **사이트 운영자** | 풀 멤버면 그 사이트 멤버십만 |
| 1′ | 같은 경로 | 운영자. 플랫폼 스코프(SUPER_ADMIN, home `'*'`) | 활성 테넌트(콘솔이 늘 보낸다) → **`'*'` 로 바꾼다** | **플랫폼 관리자** | 계정 전체(계정 행의 테넌트로 찾음) |
| 2 | security-service 자동 잠금 → account `POST /internal/accounts/{id}/lock` `reason=AUTO_DETECT` | 시스템(탐지 규칙) | 없음(MONO-735 소유자 결정 — 싣지 않는다) | **자동** | 바꾸지 않음 — 계정 전체 |
| 3 | auth-service 비밀번호 재설정 확인 → account `POST /internal/accounts/{id}/unlock` `reason=USER_RECOVERY` | 본인(AUTO_DETECT 잠금만, `TASK-BE-612`) | 없음 | **본인 복구** | 바꾸지 않음 — 계정 전체 |
| 4 | product-service 셀러 정지 · 폐점 → account `PATCH /internal/tenants/{t}/accounts/{id}/status` `LOCKED` (`TASK-MONO-737`) | 사이트 백엔드(테넌트 토큰) | 경로 = 사이트 | 사이트 기계 경로 | 바꾸지 않음 — 대상은 셀러 **운영 계정**(합성 이메일 `seller+…@marketplace.local`, 사이트 자기 계정)이지 풀 계정이 아니다. 셀러 멤버(사람의 풀 계정)는 정지 때 역할을 회수할 뿐 잠그지 않는다(`TASK-MONO-752` `SellerMemberService`) |
| 5 | `AccountDormantScheduler` → `changeStatus(…)` `DORMANT` | 시스템 | — | 잠금 아님 | 무관 |

`AccountStatusController`(공개) 에는 잠금 경로가 없다.

# 설계

## 멤버십 상태 — `LOCKED` 를 새로 둔다 (V0033)

`LEFT`(`OPERATOR`) 를 재사용하지 **않는다**:
- `LEFT` 는 떠남이다 — 그 사이트 `consumer_site_roles` 를 지우고(619 D-3), 해제(=되돌림)의 의미가 없다. 잠금은 **일시**이고 해제하면 원래대로 돌아와야 한다(역할 포함).
- 운영자의 GDPR 삭제(→ `LEFT OPERATOR`)와 잠금이 같은 값이면 «해제» 가 «내보냄» 을 되돌릴 수 있게 된다.

그래서 `consumer_site_memberships.status` 에 `LOCKED` 를 더한다(enum `ACTIVE` · `LEFT` · `LOCKED`). 잠금 기록 `locked_at` · `locked_by_actor_id`(운영자 id). CHECK: `LOCKED` 면 잠금 시각 있음 · `LOCKED` 가 아니면 잠금 기록 없음 · `LOCKED` 면 탈퇴 기록 없음. 기존 행은 `ACTIVE`/`LEFT` 뿐이라 백필 없이 성립.

## 전이 (도메인 `ConsumerSiteMembership`)

| 지금 | 사이트 운영자 잠금 | 사이트 운영자 해제 | 운영자 «GDPR 삭제»(619) | 본인 «탈퇴»(619) | 동의(616/619) |
|---|---|---|---|---|---|
| `ACTIVE` | → `LOCKED` | 무변경(멱등) | → `LEFT OPERATOR` | → `LEFT SELF` | 무변경 |
| `LOCKED` | 무변경(멱등) | → `ACTIVE` | → `LEFT OPERATOR`(잠금 기록 지움) | **무변경** — 잠긴 사람이 떠났다 다시 동의해 잠금을 벗지 못한다 | **무변경** — 동의로 열리지 않는다 |
| `LEFT` | 404(그 사이트 멤버로 찾히지 않는다 — § 5 술어) | 404 | 619 그대로 | 619 그대로 | 619 그대로 |

멱등 칸은 계정 상태 기계와 같은 결(같은 상태 = 200, `previousStatus = currentStatus`).

## 구현자 기본값 (소유자 결정 밖 — 기록)

| # | 선택 | 값 | 이유 |
|---|---|---|---|
| D-1 | 어디서 가르나 | account-service 의 **이름 있는 테넌트** `/lock`·`/unlock` 갈래(`AccountLockController#changeStatus`) 에서만 — 새 메서드 `AccountStatusUseCase#changeStatusAsTenantOperator`. 기존 `changeStatus(cmd, tenant)` 는 손대지 않는다 | `changeStatus(cmd)` 는 휴면 스케줄러가 `fan-platform` 으로 부른다 — 그 안에서 풀 멤버를 사이트 갈래로 돌리면 휴면 전이가 멤버십 잠금으로 바뀐다. 619 의 `deleteAccountAsTenantOperator` 와 같은 모양 |
| D-2 | 사이트 갈래의 사유 | 사유와 무관하게 «이름 있는 사이트 헤더 ∧ 풀 멤버» 면 멤버십 | 이름 있는 헤더로 `/lock` 을 부르는 호출자는 admin-service 뿐(자동 잠금·본인 복구는 헤더 없음). 사유로 갈라 «사이트 헤더 + 다른 사유 = 계정 전체» 구멍을 남기지 않는다 |
| D-3 | 사이트 운영자가 **계정 전체 잠금**을 풀려 하면 | 풀 수 없다 — 사이트 해제는 그 사이트 멤버십만 본다(멤버십이 `ACTIVE` 면 멱등 200, 계정은 `LOCKED` 그대로). 계정 전체 해제는 플랫폼 관리자(`'*'`) 또는 본인 복구(`AUTO_DETECT` 만) | 소유자 결정의 대칭 — «계정 전체 잠금은 플랫폼 관리자만» 이면 그 해제도 같다. 🔵 **행동 변경**: 지금은 스토어 운영자가 풀 계정의 자동 잠금도 풀 수 있다 |
| D-4 | 이벤트 | 내지 않는다(`account.locked` 없음 → auth-service 의 전체 세션 폐기도 없음) | 619 D-4 와 같다. 그 사이트 토큰은 615 의 멤버십 검사로 다음 authorize · refresh 부터 발급되지 않는다(`invalid_grant`). IAM 세션·다른 사이트 토큰은 살아 있어야 한다 — 이것이 결정의 요점이다. 🔵 이미 발급된 그 사이트 access token 은 만료까지 산다(refresh 는 막힌다) |
| D-5 | 감사 | admin-service `admin_actions` 행(`ACCOUNT_LOCK`/`ACCOUNT_UNLOCK` · `SUCCESS`)의 `downstream_detail` = `SITE_MEMBERSHIP_LOCKED site=<사이트>` / `SITE_MEMBERSHIP_UNLOCKED site=<사이트>` + `admin.action.performed`(기존). 멤버십 행에 `locked_at` · `locked_by_actor_id`. `account_status_history` 행은 쓰지 않는다(계정 상태가 바뀌지 않았다 — 619 와 같다) | «ACCOUNT_LOCK · SUCCESS» 가 «계정이 잠겼다» 로 읽히지 않게(619 D-1 과 같은 이유) |
| D-6 | 응답 | account `/lock`·`/unlock` 응답에 `scope`(`ACCOUNT`·`SITE_MEMBERSHIP`) · `siteTenantId` 추가. 사이트 범위면 `previousStatus`·`currentStatus` = **멤버십** 상태. admin-api lock/unlock 응답도 같은 두 필드 | 619 의 `/delete`·`/gdpr-delete` 와 같은 모양. 없는 응답(옛 account-service)은 `ACCOUNT` 로 읽는다 |
| D-7 | 잠금 중 역할 | 그 사이트 `consumer_site_roles` 를 **지우지 않는다** | 해제하면 원래대로 — 잠금은 떠남이 아니다 |
| D-8 | 계정이 `DELETED` 인 풀 멤버의 사이트 잠금·해제 | 409 `STATE_TRANSITION_INVALID` | 계정 경로의 «이미 DELETED → 409» 와 같은 결 |

# Acceptance Criteria

- [x] **AC-0 (계약 먼저)** — 아래 «Related Contracts» 의 문서가 코드보다 먼저(같은 PR, 앞 커밋) 고쳐져 있다: 사이트 운영자 잠금·해제 = 그 사이트 멤버십 · 플랫폼 관리자 = `'*'` 계정 전체 · `LOCKED` 멤버십의 토큰 거절 · 동의로 안 열림 · 응답 `scope`.
- [x] **AC-1 (사이트 운영자 잠금)** — 스토어 운영자(활성 테넌트 `ecommerce`, 플랫폼 스코프 아님)가 스토어·팬 둘 다 멤버인 풀 계정을 잠그면: 스토어 멤버십 `LOCKED`(잠금 시각·운영자 id 기록) · **팬 멤버십 `ACTIVE`** · **계정 `ACTIVE`** · `account.locked`/`account.status.changed` 0 · 응답 `scope = SITE_MEMBERSHIP`, `siteTenantId = ecommerce`, `currentStatus = LOCKED`. 근거가 된 멤버십 상태 값과 마이그레이션 선택은 § 설계에 적혀 있다.
- [x] **AC-2 (다른 사이트 — 대조군)** — 같은 사람이 팬으로는 여전히 토큰을 받는다(멤버십 ACTIVE · 계정 ACTIVE → 발급 경로 그대로). 스토어 사이트 계정(풀 아님)을 스토어 운영자가 잠그면 지금처럼 **계정**이 잠긴다(`scope = ACCOUNT`).
- [x] **AC-3 (플랫폼 관리자)** — 플랫폼 스코프 운영자(SUPER_ADMIN)의 잠금·해제는 활성 테넌트와 무관하게 하류 `'*'` → 계정 전체 `LOCKED`/`ACTIVE`(기존 이벤트 · 이력 · 세션 폐기). 풀 계정이면 모든 사이트에서.
- [x] **AC-4 (해제 대칭)** — 사이트 운영자 해제: 그 사이트 멤버십 `LOCKED → ACTIVE`(잠금 기록 지움, 역할 그대로) · `scope = SITE_MEMBERSHIP`. 계정 전체 잠금은 사이트 운영자가 풀지 못한다(D-3 — 계정 `LOCKED` 그대로).
- [x] **AC-5 (잠긴 사이트 거절)** — 멤버십 `LOCKED` 인 사이트로는 토큰이 발급되지 않는다 — 기존 계약 오류 `invalid_grant`(615 «ACTIVE 멤버십 없으면 토큰 없음»; authorize 게이트는 동의 화면을 띄우지 않고 통과, 발급자가 거절). consumer-members 읽기가 `membershipStatus = LOCKED` 를 답한다.
- [x] **AC-6 (동의로 안 열림)** — `LOCKED` 멤버십에 동의 `PUT` 은 무변경(그대로 `LOCKED`), 본인 «사이트 탈퇴» 도 무변경(떠났다 다시 동의해 잠금을 벗는 길 없음). 운영자 «GDPR 삭제» 는 `LOCKED → LEFT OPERATOR`.
- [x] **AC-7 (감사)** — 사이트 범위 잠금·해제의 `admin_actions` 행 `downstream_detail` = `SITE_MEMBERSHIP_LOCKED|UNLOCKED site=<사이트>`; 멤버십 행에 잠금 기록.
- [x] **AC-8 (시험 · bite)** — 단위(도메인 전이 · use case · admin 판별) + 슬라이스(컨트롤러 갈래) + 통합(Testcontainers — 스토어 잠금 → 팬 ACTIVE 대조군 · 해제 · 동의 무변경 · `'*'` 계정 전체). 사이트 갈래를 끄면 정확히 그 칸이 빨개지는 bite 를 기록한다. 로컬에서 못 돈 통합 시험은 ⚪ 로 적고 CI 실측으로 닫는다.

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md` § 소비자 계정 풀 § 4 · § 5 · § 7 · § 격리 회귀 방지
- `projects/iam-platform/specs/features/account-lifecycle.md`
- `projects/iam-platform/specs/services/account-service/data-model.md` § consumer_site_memberships
- `projects/iam-platform/tasks/done/TASK-BE-619-consumer-pool-followups-leave-site-decline-copy-security-view.md` § 구현 기록
- `projects/iam-platform/tasks/done/TASK-BE-616-first-visit-site-consent.md` § 추가 — § 5 단건 표면

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/admin-to-account.md` (`/lock` · `/unlock` · Tenant Confinement)
- `projects/iam-platform/specs/contracts/http/admin-api.md` (lock · unlock · bulk-lock · Tenant Confinement)
- `projects/iam-platform/specs/contracts/http/internal/auth-to-account.md` (consumer-members `membershipStatus`)

# Edge Cases

- 스토어 운영자가 잠근 뒤 같은 운영자가 GDPR 삭제 → `LEFT OPERATOR`(잠금 기록 지움). 그 뒤 해제는 404(멤버가 아니다).
- 플랫폼 관리자가 계정 전체를 잠근 상태에서 스토어 운영자가 스토어 멤버십도 잠금 → 둘 다 `LOCKED`. 플랫폼 관리자가 계정을 풀어도 스토어 멤버십은 `LOCKED` 그대로(스토어만 막힘).
- 잠긴 사람이 스토어에 이미 받은 access token — 만료까지 유효(refresh 는 거절). 즉시 끊으려면 플랫폼 관리자의 세션 폐기.
- bulk-lock 에 풀 멤버와 사이트 계정이 섞임 — 행마다 각자의 범위(`outcome = LOCKED` 는 둘 다 같다).

# Failure Scenarios

1. 사이트 운영자의 잠금이 여전히 계정 전체를 잠가 다른 사이트에서도 막힌다(지금의 결함 — AC-2 대조군이 문다).
2. 잠긴 회원이 그 사이트 목록·단건 조회에서 사라져 해제할 수 없다(§ 5 술어를 넓히지 않은 경우).
3. 잠긴 사람이 «탈퇴 → 다시 동의» 로 잠금을 벗는다(AC-6).
4. 플랫폼 관리자의 잠금이 콘솔의 활성 테넌트 때문에 사이트 범위로 떨어진다(AC-3 — 역할로 가르지 않은 경우).
5. 휴면 스케줄러의 `DORMANT` 전이가 사이트 갈래로 빠진다(D-1).

# 소유자 결정 필요

1. **자동 잠금(security-service `AUTO_DETECT`)은 계정 전체로 남겨도 되나** — 이 티켓은 바꾸지 않았다. 사람(운영자)이 아니라 탐지 규칙이 거는 잠금이고, 풀 계정의 로그인은 IAM 세션 하나라(615 — 어느 사이트에서 로그인하든 같은 자격) 자격 탈취·대입 공격은 사이트 하나가 아니라 그 사람의 모든 사이트를 위협한다. **권장: 계정 전체 유지**(지금 그대로). 탐지 이벤트에는 사이트가 없다(619 항목 3 — 로그인 이벤트 테넌트 = `consumer-pool`)는 점도 사이트 범위를 만들 수 없게 한다.
2. **내부 프로비저닝 `PATCH /internal/tenants/{t}/accounts/{id}/status`** — 사이트 백엔드가 자기 테넌트 토큰으로 부르는 기계 경로. 지금 유일한 호출자(product-service 셀러 정지·폐점)는 풀 계정을 겨누지 않으므로 이 결정의 영향이 없다. 그러나 어떤 사이트 백엔드가 **풀 멤버**를 이 경로로 `LOCKED`/`ACTIVE`/`DELETED` 하면 지금도 **계정 전체**에 일어난다(619 도 이 경로의 `DELETED` 는 바꾸지 않았다). 이 경로도 사이트 운영자의 잠금으로 볼지(→ `LOCKED`/`ACTIVE` 는 멤버십, `DELETED` 는 `LEFT OPERATOR`) — **권장: 같은 규칙 적용**(별도 티켓). 이 티켓은 바꾸지 않았다.

# 후속 (이 티켓 밖 — 기록)

- **콘솔 계정 목록의 상태 표시** — 사이트 범위로 잠긴 풀 멤버도 목록의 `status` 는 **계정** 상태(`ACTIVE`)다(목록 응답에 멤버십 상태가 없다). 해제 버튼은 상태와 무관하게 있으므로(`AccountRowActions`) 해제는 된다. 콘솔이 `scope = SITE_MEMBERSHIP` 응답을 «이 사이트에서만 잠금» 으로 보이고, 목록이 사이트 범위 잠금을 보이려면 목록 응답에 멤버십 상태가 필요하다 — platform-console + account-service 목록 티켓. 잠금 현황 카운트(`status=LOCKED` 필터)도 사이트 범위 잠금을 세지 않는다.
- **스토어·팬의 거절 문구** — 잠긴 사이트에서 로그인하면 IAM 로그인은 되고 그 사이트 토큰만 `invalid_grant` → 사이트 앱은 일반 오류 문구(619 항목 2 의 측정 결과와 같은 갈래 — `OAuthCallbackError`)를 보인다. «이 사이트에서 이용이 제한되었습니다» 같은 문구는 IAM 이 authorize 단계에서 `access_denied` 로 돌려줄 때 가능하다 — 별도 결정·티켓.
- **bulk-lock 응답의 행 결과**는 `scope` 를 싣지 않는다(`outcome = LOCKED` 는 계정 잠금과 사이트 잠금이 같다) — 행마다의 감사 행 `downstream_detail` 이 구별한다. 콘솔 일괄 잠금 결과 화면이 구별해 보여야 하면 콘솔 티켓.
- 🔵 관찰(고치지 않음): `admin-to-account.md` 의 `/lock`·`/unlock` 응답 예시는 시각 필드를 `lockedAt`/`unlockedAt` 으로 적었지만 account-service 는 `changedAt` 을 낸다(`StatusChangeResponse`) — admin-service 는 그 값이 없어 자기 완료 시각을 쓴다. 계약 예시만 고쳤다(동작 무변경).
- 데모 반영은 다음 AMI 재굽기 뒤.

---

# 구현 기록 (2026-10-04 UTC)

> 분석=Opus 5.5 / 구현=Opus 5.5. 한 PR. 브랜치 `feat/be-621-site-scoped-lock`. 커밋 1 = 티켓 + 계약(코드 앞), 커밋 2 = 계약 보강(`account-api.md` 본인 탈퇴의 `LOCKED` 응답 · § 7 시험 이름 — 역시 코드 앞), 커밋 3 = 코드 + 시험 + 티켓 review.

## 바꾼 것

**계약 (커밋 1 — 코드보다 먼저)**: `multi-tenancy.md` § 4 · § 5(«사이트 잠금 vs 계정 잠금» 표 · «멤버» = `ACTIVE ∨ LOCKED` · § 7 시험 자리) · `admin-to-account.md`(`/lock`·`/unlock` `scope` 표 · 플랫폼 관리자 `'*'`) · `admin-api.md`(lock · unlock · bulk-lock · Tenant Confinement) · `auth-to-account.md`(`membershipStatus = LOCKED` · 동의 무변경) · `account-api.md`(본인 탈퇴의 `LOCKED` 응답) · `data-model.md`(`V0033`) · `account-lifecycle.md`(사이트 잠금은 계정 상태가 아니다).

**account-service**
- `V0033__add_site_lock_to_consumer_site_memberships.sql` — 상태 CHECK 교체(`ACTIVE`,`LEFT`,`LOCKED`) + `locked_at` · `locked_by_actor_id` + CHECK 셋. 적용된 마이그레이션 무변경. 다음 빈 번호 확인(`V0032` 가 마지막).
- 도메인 `ConsumerSiteMembershipStatus.LOCKED` · `ConsumerSiteMembership.lockBySiteOperator` / `unlockBySiteOperator` / `isLocked`, `leave()` 의 `LOCKED` 갈래(`OPERATOR` → `LEFT`, `SELF` → 무변경), 생성자 불변식(= V0033 CHECK).
- `SiteMembershipLockUseCase`(신설) — 멤버십만 쓴다. 계정 저장소·이벤트 발행기를 갖지 않는다(구조적으로 계정을 못 건드린다).
- `AccountStatusUseCase#changeStatusAsTenantOperator`(신설, D-1) — 이름 있는 테넌트 ∧ 풀 멤버 → 사이트 갈래. `AccountLockController` 의 이름 있는 갈래가 이것을 부른다. 기존 `changeStatus(cmd, tenant)` 무변경(휴면 스케줄러).
- `StatusChangeResult` / `StatusChangeResponse` + `scope` · `siteTenantId`(4-인자 생성자 유지).
- § 5 술어 4개 질의(`AccountJpaRepository` 목록 · 개수 · 이메일 · 단건) `m.status = ACTIVE` → `IN (ACTIVE, LOCKED)`.
- 영속 `ConsumerSiteMembershipJpaEntity` 두 컬럼 · `updateMembership` 이 잠금 기록까지 쓴다.

**admin-service**
- `AccountAdminController` lock · unlock · bulk-lock — 하류 테넌트 = 플랫폼 스코프면 `'*'`, 아니면 활성 테넌트(`downstreamTenant`). 619 의 `AdminGdprController` 와 같은 판별.
- `AccountAdminUseCase` — 하류 `scope = SITE_MEMBERSHIP` 이면 감사 `downstream_detail = SITE_MEMBERSHIP_LOCKED|UNLOCKED site=…`, 결과에 `scope` · `siteTenantId`.
- `AccountServiceClient.LockResponse` · `LockAccountResult` · `UnlockAccountResult` · 응답 DTO 두 개 + `scope` · `siteTenantId`(옛 생성자 유지 — 옛 account-service 의 응답은 `ACCOUNT` 로 읽는다).

**auth-service** — 코드 변경 없음(문자열 비교 — `LOCKED` 는 `isActiveMember()=false` · `isReopenableByConsent()=false` 로 이미 «토큰 없음 · 동의 화면 없음»). 결과 타입 javadoc · 게이트 시험 한 칸.

## 시험

| 새/바뀐 시험 | 무엇을 문다 |
|---|---|
| account `ConsumerSiteMembershipLockTest` 7 (신설) | 잠금·멱등·해제·본인 탈퇴 무변경·운영자 탈퇴 → LEFT·LEFT 는 못 잠금·V0033 과 같은 불변식 |
| account `SiteMembershipLockUseCaseTest` 7 (신설) | 멤버십만 쓴다 · 멱등 · 해제 · D-3(계정 전체 잠금은 못 푼다) · LEFT/없음 404 · DELETED 409 · 다른 목표 409 |
| account `PoolMemberSiteLookupTest` +4 (16) | 사이트 운영자 → 사이트만(계정 저장·이력·이벤트 0) · 플랫폼 관리자 → 계정 LOCKED·이벤트 `consumer-pool` · **대조군** 사이트 자기 계정 → 계정 잠금 · **대조군** 휴면 경로는 계정 전체 |
| account `ConsentToConsumerSiteUseCaseTest` +1 (10) · `LeaveConsumerSiteUseCaseTest` +2 (7) | `LOCKED` 는 동의로 안 열림 · 본인 탈퇴 무변경 · 운영자 탈퇴 → LEFT |
| account `InternalControllerSliceTest` +1, 기존 3칸 갈래 이름 변경 (24) | 이름 있는 헤더 → `changeStatusAsTenantOperator` · 응답 `scope`/`siteTenantId` |
| account `ConsumerSiteLockIntegrationTest` 3 (신설, `AbstractConsumerPoolIntegrationTest` 하위) | **실 MySQL V0033**: 스토어 운영자 잠금 → 스토어 LOCKED · **팬 ACTIVE(대조군)** · 계정 ACTIVE · `account.locked` 0 · 목록에 남음 · 재동의/본인 탈퇴 무변경 · 해제 → ACTIVE / 잠긴 회원 GDPR → LEFT OPERATOR / `'*'` → 계정 LOCKED · 사이트 운영자 해제로 안 풀림 |
| admin `AccountAdminControllerSliceTest` +3 (16) | 플랫폼 스코프 → lock·unlock 하류 `'*'`(활성 테넌트가 있어도) · 사이트 운영자 → 활성 테넌트 · 응답 `scope` |
| admin `AccountAdminUseCaseTest` +3 (14) | 감사 `SITE_MEMBERSHIP_LOCKED/UNLOCKED site=ecommerce` · 옛 응답(scope 없음) → `ACCOUNT` · detail null |
| auth `AuthorizeSessionTenantGatePoolTest` +1 (14) | `LOCKED` → 동의 화면 없음 · 통과(발급자 `invalid_grant`) |

## 게이트 (각각 단독 · `cmd > log 2>&1; echo rc=$?`)

| 게이트 | rc | 비고 |
|---|---|---|
| `:projects:iam-platform:apps:account-service:test` | 0 | 위 칸 실행 확인(test-results) |
| `:projects:iam-platform:apps:account-service:check` | 0 | 통합 시험은 컴파일만 |
| `:projects:iam-platform:apps:admin-service:check` | 0 | `AccountAdminUseCaseTest` 14 · `AccountAdminControllerSliceTest` 16 · `BulkLockAccountUseCaseTest` 10 · `BulkLockControllerSliceTest` 8 |
| `:projects:iam-platform:apps:auth-service:check` | 0 | `AuthorizeSessionTenantGatePoolTest` 14 |
| `@Tag("integration")` (`ConsumerSiteLockIntegrationTest` 등) | ⚪ 로컬 미실행 | Docker 없음(`dockerDesktopLinuxEngine` 파이프 없음) — **CI 첫 실측**. 통과했다고 적지 않는다 |

## bite (되돌린 뒤 재실행 rc=0 · `false &&`/`true ||` 잔존 grep 0)

| 끈 것 | 돌린 시험 | 결과 |
|---|---|---|
| (A) `changeStatusAsTenantOperator` 의 사이트 갈래(`if (false && …)`) | `PoolMemberSiteLookupTest` · `ConsumerSiteMembershipLockTest` · `LeaveConsumerSiteUseCaseTest` · `SiteMembershipLockUseCaseTest` · `ConsumerSiteMembershipLeaveTest` | 43 중 정확히 1 — «사이트 운영자 잠금 → 그 사이트만» 칸 |
| (C) `leave()` 가 `LOCKED` 를 본인(`SELF`)에게도 `LEFT` 로 | 〃 | 43 중 정확히 2 — 도메인 «본인 탈퇴 → LOCKED 그대로» · use case «본인 탈퇴 쓰기 0» (A·C 동시 주입, 합 3) |
| (B) admin `downstreamTenant` 의 `'*'` 갈래 | — | ⚪ **수행 못 함** — 편집이 자동 모드 분류기에 «Security Weaken» 으로 막혔다. 우회하지 않았다. 그 갈래를 무는 칸은 `AccountAdminControllerSliceTest` 의 플랫폼 스코프 두 칸(`…stampsWildcard…`)이다 — 소유자가 손으로 돌릴 수 있다 |

## AC

| AC | 판정 | 증거 |
|---|---|---|
| AC-0 | ✅ | 커밋 1 · 2 = 계약만(코드 0), 코드는 커밋 3 |
| AC-1 | ✅ 단위·슬라이스 / ⚪ 통합은 CI | `PoolMemberSiteLookupTest` · `SiteMembershipLockUseCaseTest` · `InternalControllerSliceTest`; DB 경로 `ConsumerSiteLockIntegrationTest#storeOperatorLock_locksTheStoreOnly` |
| AC-2 | ✅ 단위 / ⚪ 통합은 CI | 대조군: 사이트 자기 계정 → 계정 잠금(`PoolMemberSiteLookupTest`) · 팬 멤버십 ACTIVE + consumer-members 읽기 ACTIVE(IT) |
| AC-3 | ✅ 단위·슬라이스 / ⚪ 통합은 CI | admin 슬라이스 `'*'` 두 칸 · account `changeStatusResolvingTenant` 칸 · IT `platformAdminLock_locksThePoolAccount` |
| AC-4 | ✅ 단위 / ⚪ 통합은 CI | 해제 · D-3 칸 |
| AC-5 | ✅ | auth 게이트 칸 + 발급 규칙(`isActiveMember` = ACTIVE 만, 615 그대로) · consumer-members 읽기 `LOCKED`(IT) |
| AC-6 | ✅ 단위 / ⚪ 통합은 CI | 동의 무변경 · 본인 탈퇴 무변경 · 운영자 GDPR → LEFT |
| AC-7 | ✅ | `AccountAdminUseCaseTest` 감사 detail · 멤버십 행 잠금 기록(도메인·IT) |
| AC-8 | 🟡 | 단위·슬라이스 ✅ · account bite 2종 ✅ · admin bite ⚪(분류기) · 통합 ⚪ — **CI 의 `Integration (iam …)` 잡에서 `ConsumerSiteLockIntegrationTest` 3칸이 실제로 돈 것을 확인해야 닫힌다** |

## 배포 순서

- **account-service 를 admin-service 보다 먼저 또는 함께**(V0033 + 응답 `scope`). 거꾸로(새 admin + 옛 account)면: 플랫폼 관리자 `'*'` 는 MONO-735 경로라 그대로 계정 잠금 ✅, 사이트 운영자는 옛 동작(계정 전체 잠금 — 지금의 결함 그대로, `scope` 없음 → `ACCOUNT`). 새 account + 옛 admin 이면: 플랫폼 관리자가 활성 테넌트로 보내 **사이트 범위로 떨어진다**(Failure Scenario 4) — 그래서 함께 올린다.
- auth-service 는 배포 무관(코드 변경 없음).
- 🔵 데모 반영은 다음 AMI 재굽기 뒤(백엔드는 구워진 클론에서 돈다).

# 소유자 결정 (2026-10-04 UTC, § 소유자 결정 필요 의 답 — 원문 그대로)

1. 자동 잠금 → **«계정 전체 유지 (Recommended)»**. security-service `AUTO_DETECT` 잠금은 계정 전체 그대로 — 코드 변경 없음.
2. 내부 프로비저닝 PATCH → **«별도 티켓으로 적용 (Recommended)»** → `TASK-BE-622`(ready).

# 닫기 (2026-10-04 UTC) — 4차원 검증

| 차원 | 판정 |
|---|---|
| (a) | `gh pr view 4141` → `MERGED`, `7a127c39a` |
| (b) | `7a127c39a` 는 `origin/main` 의 조상 |
| (c) | #4141 `statusCheckRollup` 66 개 · FAILURE 0 |
| (d) | AC-0~7 `[x]`(구현 기록). **AC-8** — 단위·슬라이스 로컬 rc=0 · bite (A) 1칸 (C) 2칸(구현 기록), 그리고 ⚪ 이던 통합: PR 런 `37189155467` `Integration (iam B, Testcontainers)` 잡 `111397547167` 로그에 `TASK-BE-621 — 사이트 잠금(멤버십 LOCKED) vs 계정 잠금 (MySQL, 풀 켜짐)` 3칸(스토어 운영자 잠금→스토어만 LOCKED·팬 ACTIVE·해제 / 잠긴 회원 GDPR 삭제→LEFT OPERATOR / 플랫폼 관리자 `'*'`→계정 LOCKED) **PASSED** · `TEST-SUMMARY lane=integration tests=304 failures=0 errors=0 skipped=0` — V0033 이 실제 MySQL 에 적용됐다. 🔵 bite (B)(admin `'*'` 분기)는 분류기에 막혀 못 했다 — 슬라이스 두 칸 + 위 통합 셋째 칸이 그 분기를 지킨다 |

🔵 데모 반영: 다음 AMI 재굽기(account-service · admin-service 함께). § 후속(콘솔 표시 · 거절 문구)은 이 티켓 밖.
