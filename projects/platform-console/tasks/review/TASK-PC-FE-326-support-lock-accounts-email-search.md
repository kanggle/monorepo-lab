# Task ID

TASK-PC-FE-326

# Title

CS 2선(SUPPORT_LOCK)이 「계정 운영」(`/accounts`)을 전혀 쓸 수 없다 — 사이드바에 숨고, 직접 URL 로 가도 403

# Status

review

# Owner

platform-console

# Task Tags

- platform-console
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 기존 `admin-per-card` 게이트 종류 + `getSelfRolesOrNull()` 재사용으로 끝나는 화면 분기 작업. 백엔드 변경 없음.

---

# Dependency Markers

- 출처: 2026-10-09 UTC 소유자 대화 — 메뉴 순서 검토 중 발견.
- 관련: `TASK-PC-FE-314`(사이드바 노출 게이트 — `console-nav-exposure.ts`, `isHrefHiddenFor`/`admin-per-card`) · `TASK-BE-357`(iam-platform, email 단건 조회를 테넌트 스코프로 정정하면서 "SUPPORT_LOCK 이 잠글 계정을 찾는 길"을 열어 둔 선행 작업) · `TASK-MONO-202`(iam-platform, 무권한 전체 목록 호출을 403 으로 정정).
- **주의**: `TASK-PC-FE-325`(사이드바 그룹 순서 재배치)가 이 창과 동시에 `console-nav-config.ts`의 `GROUPS` 순서를 건드리는 중 — 이 태스크는 그 파일을 **건드리지 않는다**(Out of Scope로 명시, 충돌 회피).

# Goal

`SUPPORT_LOCK`(CS L2 — `rbac.md` 기준 보유 권한: `account.lock` · `account.unlock` · `account.force_logout` · `audit.read`, `account.read` **없음**)과 `SECURITY_ANALYST`(보유: `audit.read` · `security.event.read` · `account.force_logout`)는:

1. 사이드바에서 「계정 운영」(`/accounts`) 항목이 아예 **숨는다** — `console-nav-exposure.ts`가 `/accounts`의 게이트(`permission-map.ts`의 `{kind:'admin', permission:'account.read'}`)를 단일 권한으로만 판정하기 때문.
2. 직접 URL로 가도 `app/(console)/accounts/page.tsx`가 무조건 `getAccountsListState({page:0, size:20})`(전체 목록)을 호출 → 백엔드가 `account.read` 없음을 403 `PERMISSION_DENIED`로 거절(TASK-MONO-202) → `AccountsScreen`(검색창 + 잠금/해제 버튼)은 전혀 렌더되지 않고 `accounts-forbidden` 안내만 보인다.

그런데 백엔드는 이미 이 역할들을 위한 길을 열어 두고 있다 — `GET /api/admin/accounts`에 `email` 파라미터가 있으면 **권한 키를 요구하지 않는다**(테넌트 스코프 게이트만, `rbac.md:90`, "SUPPORT_LOCK 이 잠글 계정을 찾는 길", TASK-BE-357). 그 길이 콘솔 쪽에서 한 번도 열리지 않았다.

⇒ `account.read`가 없지만 `account.lock`/`account.unlock`/`account.force_logout` 중 하나라도 보유한 역할은 사이드바에 노출되고, `/accounts`에서 **이메일 검색 전용 모드**(전체 목록 없음)로 열려, 보유한 작업(잠금/해제/세션 종료)을 그 모드에서 수행할 수 있어야 한다. 네 권한을 전부 보유하지 않은 역할은 오늘과 동일(숨김 + 403) 하게 유지한다.

# Scope

## In Scope

- `shared/guide/permission-map.ts` — `/accounts` 행의 게이트를 `{kind:'admin', permission:'account.read'}`에서 `{kind:'admin-per-card', permissions:['account.read','account.lock','account.unlock','account.force_logout']}`로 변경(OR 게이트 — 넷 중 하나만 있어도 사이드바에 보인다). `hasPermission`/`hasAnyPermission`을 이 파일로 승격해 `console-nav-exposure.ts`와 신설 `accountsAccessTier()`가 같은 구현을 공유하게 한다(두 번째 권한표 손으로 적지 않기).
- `shared/guide/permission-map.ts` — `accountsAccessTier(myRoles): 'full' | 'search-only' | 'none'` 신설. `/accounts` 행의 `admin-per-card` `permissions` 배열(위와 **같은** 배열)을 읽어 판정 — 별도 하드코딩 없음.
- `shared/ui/console-nav-exposure.ts` — 로컬 `hasPermission`/`KNOWN_ROLES` 복사본 제거, `permission-map.ts`의 `hasPermission` 재사용(행동 불변 — `isHrefHiddenFor`의 `admin-per-card` 분기가 이미 OR 판정이라 게이트 종류 변경만으로 사이드바 노출이 자동으로 바뀐다. nav 코드 자체는 특례 없음).
- `features/accounts/api/accounts-state.ts` — `getAccountsAccessTier()`(roles 조회 + 판정) · `getAccountsSearchOnlyState()`(테넌트 선택 여부만 확인, email 조회도 TASK-BE-357 에 따라 테넌트 스코프이므로) 신설. `features/accounts/index.ts` 배럴에 추가.
- `app/(console)/accounts/page.tsx` — `getAccountsAccessTier()`를 **먼저** 호출. `'search-only'`면 전체 목록 호출(`getAccountsListState`)을 **전혀 하지 않고** 테넌트 게이트만 거쳐 `AccountsScreen`을 검색 전용 모드로 렌더. `'full'`/`'none'`은 기존 `getAccountsListState` 경로로 그대로 낙하(동작 불변 — `'none'`은 오늘처럼 403→forbidden).
- `features/accounts/components/AccountsScreen.tsx` — `initial: AccountPage | null` + `searchOnly?: boolean` prop. 검색 전용 모드에서는 이메일 미입력 상태의 목록 쿼리를 비활성화(`useAccountsSearch`의 신설 `enabled` 옵션)하고, 검색 전 상태는 안내 문구(`accounts-search-only-prompt`)를 보인다. 이메일 제출 시 기존 `searchAccounts` 경로(이미 `email`만 보내는 분기 존재)를 그대로 탄다 — 신규 API 없음.
- `features/accounts/components/AccountsSearchBar.tsx` — `searchOnly` 모드에서 placeholder 문구만 조정("비우면 전체 목록"은 거짓이 되므로).
- `features/accounts/hooks/use-accounts.ts` — `useAccountsSearch`에 `{enabled}` 옵션 추가(기본값 `true`, 기존 호출부 행동 불변).
- `features/iam-guide/data.ts` — `SCREEN_ACCESS`의 `/accounts` 행: `SUPPORT_LOCK`/`SECURITY_ANALYST`를 `none`→`partial`(이메일 검색만)로 정정.
- `features/iam-guide/components/IamGuideScreen.tsx` — "SUPPORT_LOCK 은 목록을 열 수 없습니다" 문구를 새 동작에 맞게 정정(티켓 번호는 화면에 넣지 않음 — TASK-PC-FE-322/323 plain-language 가드).
- `specs/contracts/console-integration-contract.md` § 2.4.1 — 새 소비자 측 동작(역할 기반 검색 전용 디그레이드) 기록(스펙 먼저).
- 단위 테스트: `accountsAccessTier()` 순수함수(신규 파일), `console-nav-exposure.test.ts`에 `/accounts` admin-per-card 케이스 추가, `app/(console)/accounts/page.tsx` SSR 게이팅 테스트(신규), `AccountsScreen.test.tsx`에 검색 전용 모드 테스트 추가, `IamGuideScreen.test.tsx` 핀 정정.

## Out of Scope

- 백엔드(iam-platform) 변경 — `GET /api/admin/accounts`의 email 분기는 이미 권한 불필요(TASK-BE-357). 코드 변경 없음, 읽기만.
- RBAC seed matrix(`rbac.md`, `RBAC_SEED_MATRIX`) 변경.
- `console-nav-config.ts`의 `GROUPS` 순서 — `TASK-PC-FE-325`가 동시에 재배치 중. 이 파일은 건드리지 않는다.
- 검색 전용 모드에서의 일괄 잠금 UX 재설계 — 기존 다중 선택 메커니즘을 그대로 재사용(검색 결과가 이미 배열이라 선택 체크박스가 자연히 동작).
- `AccountRowActions`의 버튼별 권한 가드(클라이언트에서 권한 없는 버튼을 숨기는 것) — 오늘도 버튼은 무조건 렌더되고 백엔드 403 으로 걸린다; 이 티켓은 그 패턴을 바꾸지 않는다.

# Acceptance Criteria

- [x] **AC-0** — 측정(코드 먼저 읽기, 추측 금지).

  | 질문 | 답 | 근거 |
  |---|---|---|
  | 콘솔 검색창이 email 을 실제로 보내는가 | 그렇다 — `searchAccounts()`(`shared/api/iam-accounts-types.ts` 공유 클라이언트)는 `email` 이 있으면 `page`/`size`/`status` 없이 `email` 만 쿼리스트링에 싣는다. BFF 프록시(`app/api/accounts/route.ts`)도 그대로 전달 | `shared/api/iam-accounts-read.ts:132-160`, `app/api/accounts/route.ts:21-41` |
  | 백엔드 email 분기가 정말 권한 불필요인가 | 그렇다 — `AccountAdminController.search()`가 `email` 있으면 권한 검사 없이(테넌트 스코프만) 바로 응답, `account.read` 검사는 email 없는 분기에만 있다 | `iam-platform/.../AccountAdminController.java:91-111`, `rbac.md:90,107` |
  | 사이드바 노출이 어떤 게이트 종류를 쓰는가 | `admin-per-card`(기존 `/iam` 행이 이미 사용 중 — "카드마다 다른 키로 부분 게이트"). `isHrefHiddenFor`의 `admin-per-card` 분기는 이미 OR(하나라도 있으면 숨지 않음) — nav 코드 수정 없이 게이트 종류만 바꾸면 된다 | `console-nav-exposure.ts:79-80` |
  | 콘솔 계약이 403→forbidden 을 이미 규정하는가 | 그렇다 — § 2.4.1 Resilience 불릿이 "account.read absent → 403 → 권한없음" 을 단언. 이 티켓은 그 문장을 깨지 않고(네 권한 모두 없는 역할엔 그대로 적용), **그 호출을 하지 않는 새 분기**를 추가하는 것이므로 계약을 먼저 갱신했다 | `specs/contracts/console-integration-contract.md` § 2.4.1(갱신 완료, 아래 "Role-based degrade to search-only" 불릿) |

- [x] **AC-1** — `permission-map.ts` `/accounts` 게이트를 `admin-per-card`(4개 권한 OR)로 변경 + `accountsAccessTier()` 신설(같은 배열 재사용, 하드코딩 없음) + `hasPermission`/`hasAnyPermission` 승격.
- [x] **AC-2** — `console-nav-exposure.ts`가 로컬 복사본을 버리고 승격된 `hasPermission`을 재사용(행동 불변 — 테스트로 핀).
- [x] **AC-3** — `app/(console)/accounts/page.tsx`가 `accountsAccessTier()`를 먼저 호출, `search-only`일 때 전체 목록 호출을 **하지 않는다**(바이트 단위 — `getAccountsListState` mock 의 호출 여부로 단언).
- [x] **AC-4** — `AccountsScreen`이 `searchOnly` 모드에서: 검색 전 안내 문구 렌더(목록/빈-상태 아님) · 이메일 제출 시에만 fetch · 보유한 작업(잠금 등)이 검색 결과 행에서 동작 · 빈 검색으로 되돌리면 안내 문구로 복귀(전체 목록로 떨어지지 않음).
- [x] **AC-5** — 단위 테스트:
  - `accountsAccessTier()`: SUPER_ADMIN/SUPPORT_READONLY → `full`; SUPPORT_LOCK/SECURITY_ANALYST → `search-only`; 넷 다 없는 역할(TENANT_BILLING_ADMIN 등) → `none`; roles=null/undefined(즉 `/me` 실패) → `full`(fail open, 오늘처럼 시도); 미지원/커스텀 역할 → `full`(fail open).
  - `console-nav-exposure.test.ts`: SUPPORT_LOCK·SECURITY_ANALYST·SUPER_ADMIN·SUPPORT_READONLY → `/accounts` 숨지 않음; TENANT_BILLING_ADMIN(넷 다 없음) → 숨음.
  - `accounts-page.test.tsx`(신규, SSR): search-only → `accounts-screen`(searchOnly=true, initial=null) 렌더 + `getAccountsListState` 미호출; search-only+noTenant → 동일 no-tenant 게이트; none → 기존처럼 forbidden(`getAccountsListState` 호출됨); full → 기존처럼 정상/noTenant/degraded 분기 불변.
  - `AccountsScreen.test.tsx`: searchOnly 초기 렌더 = 안내 문구(fetch 0회) · 이메일 제출 → email 쿼리만 fetch + 결과 렌더 · 그 행에서 잠금 액션 동작 · 빈 검색 재제출 → 안내 문구 복귀(fetch 0회).
  - `IamGuideScreen.test.tsx`: `/accounts` × SUPPORT_LOCK 핀을 `none`→`partial`로 갱신.
- [x] **AC-6** — bite: `accountsAccessTier()`의 검색-전용 분기(마지막 `return hasAnyPermission(...)`)를 임시로 `return 'none'`으로 바꾸고 타게팃 3개 파일 실행 → `accounts-access-tier.test.ts`의 정확히 3개 테스트만 빨강(SUPPORT_LOCK/SECURITY_ANALYST/OR 확인 테스트), `console-nav-exposure.test.ts`·`accounts-page.test.tsx`(둘 다 mock 기반이라 실제 함수를 안 거침)는 그대로 초록. 분기를 되돌리자 29/29 복귀.
- [ ] **AC-7** — ⚪ 라이브 데모 확인(SUPPORT_LOCK 계정으로 로그인 → 사이드바 노출 → 이메일 검색 → 잠금 성공) — **측정 안 함**. 이 구현 세션은 실제 데모 환경(IAM+콘솔 기동)을 띄우지 않았다. 다음 데모 창에서 확인할 것 — 데모 시드가 `SUPPORT_LOCK` 전용 로그인 계정을 보유하는지부터 먼저 확인 필요(`infra/demo/seed/` 또는 `DEMO_TEST_ACCOUNT`는 `SUPER_ADMIN`만 — 이 티켓 범위 밖이면 수동 역할 부여로 확인).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` — `features/accounts` (§ 내부 구조 Rule), Layered-by-Feature.
- `projects/iam-platform/specs/services/admin-service/rbac.md` — § Permission Keys(:60-67), § 권한 키 없는 읽기(:85-93), § Seed Matrix(:98-118).

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.1 IAM accounts surface (이 티켓이 "Role-based degrade to search-only" 불릿 추가).
- `projects/iam-platform/specs/contracts/http/admin-api.md` `GET /api/admin/accounts` (변경 없음 — cross-reference만).

# Edge Cases

- `GET /api/admin/me` 실패/미해결(`myRoles` null) — fail open 으로 `full` 취급(오늘처럼 전체 목록을 시도; 서버가 최종 권한자이므로 무해).
- 커스텀/미지원 역할(RBAC_SEED_MATRIX 에 없는 role 문자열) — fail open 으로 `full`(숨기지 않는 원칙과 동일 — `hasPermission`이 이미 이 원칙).
- 검색 전용 모드에서 빈 이메일 제출 — 전체 목록로 떨어지지 않고 안내 문구로 복귀(그 분기는 403만 돌려주므로 떨어뜨리면 안 됨).
- 검색 전용 모드에서 보유하지 않은 작업 버튼(예: SECURITY_ANALYST 가 잠금 시도) — 오늘과 동일하게 버튼은 렌더되고 백엔드가 403으로 거절, 다이얼로그 에러로 표시(이 티켓이 바꾸지 않는 기존 패턴).
- 검색 결과가 여러 건(이론상 — email 은 정확 일치라 보통 0/1건이지만 타입은 배열) — 기존 다중 선택/일괄 잠금 메커니즘이 그대로 동작.

# Failure Scenarios

1. `/accounts` 게이트를 `admin-per-card`로 바꾸되 `accountsAccessTier()`가 별도의 하드코딩된 권한 배열을 쓰면, 둘이 갈라지는 순간(예: 다섯 번째 권한 키 추가) 사이드바는 보이는데 화면은 안 열리는 상태가 생긴다 — 이 티켓은 같은 배열을 공유해서 막는다.
2. `page.tsx`가 `search-only`일 때도 여전히 `getAccountsListState`를 호출하면(순서를 안 바꾸거나 분기를 빠뜨리면) 403 감사 행이 매번 쌓이고 forbidden 안내가 또 보인다 — AC-3/AC-6 bite 가 이걸 지킨다.
3. `useAccountsSearch`의 `enabled` 옵션 기본값을 잘못 두면(`false` 기본) 기존 전체 목록 화면(SUPER_ADMIN 등)이 깨진다 — 기본값을 `true`로 두고 전체 vitest 로 확인했다.
