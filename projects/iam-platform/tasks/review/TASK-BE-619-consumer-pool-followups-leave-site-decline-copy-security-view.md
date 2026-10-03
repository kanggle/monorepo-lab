# Task ID

TASK-BE-619

# Status

review (2026-10-03 UTC — 세 항목 한 PR · 통합 시험(Docker)은 로컬 미실행 → CI 판정, § 구현 기록)

# Title

전역 소비자 계정 후속 셋 — 사이트 탈퇴 vs 계정 삭제 · 동의 거절 문구 · 콘솔 보안 이벤트 조회 (`TASK-BE-616` 에서 인계)

# Owner

iam-platform

# Task Tags

- account-service
- web-store
- platform-console
- follow-up

---

> **분석 모델:** Opus 5.5 / **구현 권장:** 항목 1 = Opus(삭제·탈퇴 의미 결정) · 항목 2·3 = Sonnet 5

---

# Dependency Markers

- **출처**: `TASK-BE-616` (done) § 검토 · § CORRECTION — 그 티켓이 «후속» 으로 남긴 세 의무. 이 티켓이 그 집이다.
- **선행**: 없음(616 머지 `2e7e2951f` 로 플래그가 켜져 있다).

# Goal

`TASK-BE-616` 이 플래그를 켜면서 드러났지만 그 범위 밖이라 남긴 세 가지를 닫는다. 서로 독립이라 항목별로 PR 을 나눠도 된다.

# Scope

## In Scope

1. **사이트 탈퇴 vs 계정 삭제** — 지금 사이트 운영자의 GDPR 삭제·사용자 `/me` DELETE 는 **풀 계정 하나**를 지워 모든 소비자 사이트에서 사라진다(616 이 계약 § 5 로 넓힌 결과, 데이터 주체 삭제 요청으로는 맞다). 그런데 «이 사이트만 그만 쓰기»(멤버십 `LEFT`) 경로가 없다. 사용자·운영자 각각에게 어떤 동작이 «탈퇴» 이고 어떤 동작이 «계정 삭제» 인지 정하고(🔴 소유자 결정이 필요할 수 있다 — 화면 문구·법적 의미), 멤버십 `LEFT` 경로를 만든다.
2. **동의 거절 문구** — 풀 계정이 사이트 첫 방문 동의를 거절하면 IAM 이 `error=access_denied` 로 돌려보낸다. NextAuth v5 가 그것을 `AccessDenied` 로 바꾸면 web-store `apps/web-store/src/features/auth/ui/LoginForm.tsx` → `normalizeErrorCode` 가 `role_denied` 로 묶어 «operator 계정으로는 web-store 에 접근할 수 없습니다…» 를 보인다(미측정). 측정 → 맞는 문구.
3. **콘솔 보안 이벤트 조회** — 풀 계정의 보안 이벤트는 `tenant_id=consumer-pool` 로 기록된다. 콘솔에서 사이트 테넌트(`ecommerce` · `fan-platform`)로 걸러 보면 보이지 않는다.

## Out of Scope

- 풀 모델 자체의 변경

# Acceptance Criteria

- [ ] **AC-1 (항목 1)** — «사이트 탈퇴» 와 «계정 삭제» 의 동작이 표로 정해져 있고(사용자 / 사이트 운영자 / 플랫폼 관리자 각각), 소유자 결정이 필요한 칸은 결정을 받아 기록했다. 멤버십 `LEFT` 경로가 있고, `LEFT` 뒤 그 사이트 토큰이 발급되지 않으며(615 의 멤버십 검사) 다른 사이트는 영향이 없다 — 대조 시험.
- [ ] **AC-2 (항목 2)** — 거절 시 web-store 가 실제로 받는 `?error=` 값을 **측정**하고 기록한다(nightly full-stack e2e `apps/web-store/e2e/consent-decline.spec.ts`, `e2e/account-type-guard.spec.ts` 를 본뜸 — 팬 전용 풀 시드 계정이 필요). 측정값에 맞는 문구로 고친다. 측정 전에 문구를 고치지 않는다.
- [ ] **AC-3 (항목 3)** — 콘솔 보안 이벤트 화면이 사이트 테넌트로 볼 때 그 사이트 멤버 풀 계정의 이벤트를 포함하거나, 포함하지 않는다면 그 사실을 화면이 말한다(조용히 빈 목록 금지). 선택과 근거를 기록.

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md` § 소비자 계정 풀 § 5
- `projects/iam-platform/tasks/done/TASK-BE-616-first-visit-site-consent.md` § 검토

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/auth-to-account.md` (consumer-members)

# Edge Cases

- 모든 사이트에서 `LEFT` 한 풀 계정 — 계정은 살아 있는데 쓸 사이트가 없다. 다시 동의하면 돌아올 수 있나(616 은 `LEFT` 를 동의로 다시 열지 않는다).

# Failure Scenarios

1. «사이트 탈퇴» 버튼이 실제로는 풀 계정을 지워 다른 사이트 데이터까지 사라진다.
2. 측정 없이 거절 문구를 «고쳐» 다른 오류 코드의 문구를 깨뜨린다.

---

## 추가 관찰 (2026-10-02 UTC, 18차 창 — `TASK-MONO-744` 라이브 판정)

- ④ **동의 화면 부제**가 그 사이트의 로그인 문구를 재사용한다 — 스토어로 가는 동의 화면에 «쇼핑을 계속하려면 IAM 계정으로 로그인하세요.» 가 뜬다(이미 로그인한 사람에게 «로그인하세요»). 동의 화면 전용 부제가 필요한지 정한다. 증거: 세션 스크래치 `live18/shots/m3-03-store-first-visit.png`.
- ⑤ 가입 직후 자동 로그인이 아니라 IAM 로그인 화면이 비밀번호를 다시 묻는다(기존 동작 — «가입이 완료되었습니다. 로그인해 주세요.»). 의도된 동작이면 그대로 두고 적는다.

---

# 소유자 결정 (2026-10-03 UTC — 코디네이터 전달, 원문 그대로)

1. **사이트 운영자 삭제 권한 = «자기 사이트 멤버십만»**: a site operator (store / fan) can only make THEIR site's membership `LEFT`; full pool-account deletion (GDPR) is only by the person themself or a platform admin. One site can never delete another site's member data. (Today a site operator's delete removes the whole pool account — that must change.)
2. **탈퇴 후 복귀 = «다시 동의하면 복귀»**: a user who left a site themself can come back by logging in and consenting again (LEFT → ACTIVE via consent). BUT an operator-forced LEFT (kicked out) must NOT be reopened by re-consent — the two must be distinguishable (e.g. a reason/actor on the membership).
3. **동의 화면 부제 = «동의 화면 전용 부제»**: the consent screen gets its own subtitle that states what is being consented to (e.g. «<사이트>가 내 IAM 계정을 쓰도록 허용합니다»), not the site's login copy «…로그인하세요». Observation ⑤ (re-asking the password right after sign-up) stays as-is — record it as intended behaviour.

항목 2(거절 문구 — 먼저 `?error=` 측정)와 항목 3(콘솔 보안 이벤트 — 포함 또는 말하기, 근거 기록)은 소유자 결정 없이 AC 대로.

---

# 구현 기록 (2026-10-03 UTC)

> 분석=Opus 5.5 / 구현=Opus 5.5. 한 PR(세 항목 + 관찰 ④⑤). 브랜치 `iam-be-619-consumer-pool-followups`.

## 항목 1 — 사이트 탈퇴 vs 계정 삭제 (AC-1)

### 동작 표 (정본: `specs/features/multi-tenancy.md` § 소비자 계정 풀 § 5 «사이트 탈퇴 vs 계정 삭제»)

| 행위자 | «사이트 탈퇴» (그 사이트 멤버십 → `LEFT`) | «계정 삭제» (풀 계정 → `DELETED`, 모든 소비자 사이트에서) |
|---|---|---|
| 본인 (그 사이트 토큰) | **신설** `DELETE /api/accounts/me/site-membership` — 토큰의 사이트만, `left_by = SELF` | `DELETE /api/accounts/me` — 풀 계정 하나(616 그대로, 소유자 결정 1 «본인») |
| 사이트 운영자 (활성 테넌트 = 그 사이트) | 콘솔 «GDPR 삭제» · 내부 `/delete` 가 **풀 멤버**를 겨누면 그 사이트 멤버십만 `LEFT`, `left_by = OPERATOR` · 응답 `scope = SITE_MEMBERSHIP` | **할 수 없다**(소유자 결정 1). 그 사이트의 **자기 계정**(풀 아님)은 지금처럼 삭제 |
| 플랫폼 관리자 (SUPER_ADMIN) | 해당 없음 | 콘솔 «GDPR 삭제» — admin-service 가 플랫폼 스코프에 하류 `*` → account-service 가 계정 행으로 찾아 계정 자체를 지운다(`scope = ACCOUNT`) |

### 멤버십 상태 모델 — 본인 LEFT 와 운영자 LEFT 를 가르는 방법

- account-service `V0032`: `consumer_site_memberships` 에 `left_at` · `left_by`(`SELF` | `OPERATOR`, CHECK) · `left_by_actor_id`(운영자 id / 본인 계정 id) + CHECK «`ACTIVE` 면 탈퇴 기록 없음». 기존 행은 전부 `ACTIVE`(619 전엔 LEFT 작성자 0) → 백필 없음.
- 도메인 `ConsumerSiteMembership.leave(by, actor, at)` · `isReopenableByConsent()`(= `LEFT ∧ SELF`) · `rejoinOnConsent(at)`.
  - 본인이 떠난 뒤 운영자가 내보냄 → `OPERATOR` 로 다시 기록(내보냄이 남는다). 운영자가 내보낸 뒤 본인 «탈퇴» → 그대로(복귀 가능으로 낮추지 못함). 같은 탈퇴 반복 → 무변경.
  - 작성자 기록 없는 `LEFT`(없음 — 619 이전) → 운영자 쪽으로 읽는다(보수).
- 읽기 `GET /internal/tenants/{t}/consumer-members/{id}` 에 `leftBy` 추가 → auth-service 게이트: `membershipStatus == null` **또는** `LEFT ∧ leftBy = SELF` → 동의 화면. `OPERATOR` / 기록 없음 → 615 그대로 통과·발급 거절(`invalid_grant`).
- 동의 쓰기 `PUT …/consumer-members/{id}`: `LEFT ∧ SELF` → `ACTIVE`(새 `consented_at`), **`account.created` 다시 안 냄**(§ 6 «사이트마다 한 번»). `LEFT ∧ OPERATOR` → 무변경.

### 이 티켓이 정한 것 (소유자 결정 밖의 구현자 기본값 — 기록)

| # | 선택 | 값 | 이유 |
|---|---|---|---|
| D-1 | 사이트 운영자 삭제를 어디서 바꾸나 | 기존 «GDPR 삭제» 경로가 풀 멤버면 그 사이트 멤버십 `LEFT` 로 — 새 운영자 버튼 없음. 응답 `scope` · 콘솔 안내(`role=status`) · 확인 대화상자 문구 · 감사 행 `downstream_detail = SITE_MEMBERSHIP_LEFT site=…` 로 «삭제됨» 으로 읽히지 않게 | 결정 1 은 «사이트 운영자의 삭제 = 자기 멤버십만». 새 운영자 명령(admin-api · 권한 · 콘솔 화면)은 범위 확대 — 같은 버튼이 같은 의도(그 사람을 이 사이트에서 지워 달라)를 사이트 범위에서 이행 |
| D-2 | 플랫폼 관리자 판별 | 역할(`QueryTenantScopeGate.Resolved.isPlatformScope`)로. admin-service 가 SUPER_ADMIN 의 GDPR 삭제에 항상 `*` 를 찍고, account-service `/gdpr-delete` 가 `*`/없음/공백이면 계정 행으로 찾는다(MONO-735 의 `/lock`·`/delete` 와 같은 갈림 — `findByIdResolvingTenant` 의 세 번째 소비처, multi-tenancy.md § 격리 회귀 방지에 등록) | 콘솔은 늘 활성 테넌트를 보낸다 — 헤더로는 플랫폼 관리자를 가를 수 없다. 619 전엔 `*` → `fan-platform` 기본값이라 SUPER_ADMIN 의 비-fan 계정 GDPR 삭제는 404 였다(그 결함도 같이 풀림) |
| D-3 | 떠날 때 사이트 역할 | 그 사이트 `consumer_site_roles` 삭제 | 복귀(동의)가 운영자가 줬던 ARTIST·SELLER 를 몰래 되살리지 않게(보수). 소유자가 한 줄로 뒤집을 수 있다 |
| D-4 | 떠남의 이벤트 | 없음 | 토큰은 615 의 멤버십 검사로 다음 authorize·refresh 부터 막힌다. 사이트 쪽 회원 데이터 처리는 사이트의 일(이 티켓 밖) |
| D-5 | 사이트 계정(풀 아님) · 멤버십 없음의 «탈퇴» | 409 `SITE_MEMBERSHIP_REQUIRED`(기존 등록 코드) | 사이트 계정은 멤버십이 따로 없다 — 그 «탈퇴» 가 곧 `DELETE /api/accounts/me` |
| D-6 | 본인 탈퇴 엔드포인트 재인증 | 없음(본문 없음) | 되돌릴 수 있는 동작(다시 동의하면 복귀). 계정 삭제(`DELETE /me`)와 다르다 |

### 바꾼 616 기대값

| 시험 | 전 | 후 |
|---|---|---|
| account `PoolMemberSiteLookupTest#gdprDelete_…` | 사이트 운영자 GDPR → 풀 계정 DELETED | 사이트 운영자 → 그 사이트만 LEFT(OPERATOR) · 계정·이메일 무변경 · 이벤트 0 / 새 칸: 플랫폼(`executeResolvingTenant`) → DELETED · 이벤트 `consumer-pool` / 대조군: 사이트 자기 계정은 그대로 삭제 |
| account `PoolMemberSiteSurfacesIntegrationTest#gdprDelete_…` | ecommerce GDPR → DELETED | ecommerce → `scope=SITE_MEMBERSHIP` · 계정 ACTIVE · 이메일 그대로 → `*` → DELETED · 마스킹 · 이벤트 `consumer-pool` |
| account `GdprControllerSliceTest` 기존 3칸 | 헤더 없음 | `X-Tenant-Id: fan-platform`(이름 있는 테넌트 갈래) — 헤더 없음/`*`/공백은 새 칸이 `executeResolvingTenant` 로 |
| account `InternalControllerSliceTest#delete_concreteTenantHeader_staysConfined` | `deleteAccount(…, ecommerce)` | `deleteAccountAsTenantOperator(…, ecommerce)` |
| auth `SiteConsentPageSliceTest#page_branded…` · `#unknownDecision_400` | 로그인 부제 · `verifyNoInteractions` | 동의 전용 부제 · 사이트 이름 읽기 1회만, 쓰기 없음 |

## 항목 2 — 동의 거절 문구 (AC-2) — 측정 먼저

- **측정 스펙**: `apps/web-store/e2e/consent-decline.spec.ts`(nightly full-stack 레인, `assert-specs-ran.mjs` 필수 목록에 추가). 시드: `iam-consumer-seed.sql` 에 `consumer-pool` 자격 1행(`e2e-pool-fan-only@example.com`), `account-mock.nginx.conf` 에 그 계정의 스토어 멤버십 조회 → «소비자 사이트 · 멤버십 없음».
- **1차 측정 런** [37093051748](https://github.com/kanggle/monorepo-lab/actions/runs/37093051748) (`637c7aa05`, 문구 무변경): 동의 화면 도달 · 거절 → 스토어 `/login` 복귀 · NextAuth 서버 로그 `OAuthCallbackError: OAuth Provider returned an error`. 스펙의 `getByRole('alert')` 가 Next route announcer 와 겹쳐(strict mode) `?error=` 기록 전에 멈췄다 — 화면 alert 는 일반 fallback 문구였다.
- **2차 측정 런** [37094305963](https://github.com/kanggle/monorepo-lab/actions/runs/37094305963) (`c9155331c`, locator 만 고침 · 문구 무변경) — 기록 줄:
  `[TASK-BE-619 AC-2] consent decline -> web-store /login?error=OAuthCallbackError | alert=로그인 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.` · 레인 13 passed.
- **판정**: 티켓의 가설(`AccessDenied` → «operator 계정으로는…»)은 **틀렸다** — 실측값은 `OAuthCallbackError` 이고 화면은 일반 fallback. 그래도 틀린 안내다(거절한 사람에게 «잠시 후 다시 시도» — 고장처럼 읽힌다).
- **고침 (측정 뒤)**: `LoginForm.normalizeErrorCode` 에 `OAuthCallbackError → provider_error`(F5 어휘 «IdP 가 callback 에서 error 반환» 과 같은 뜻) · web-store 의 `provider_error` 문구를 «IAM 로그인이 끝나지 않았습니다. 이 사이트 이용에 동의하지 않으셨다면, 다시 «IAM 로그인»을 눌러 동의하면 계속할 수 있습니다.» 로. 다른 코드의 문구·매핑 무변경(Failure Scenario 2). 측정 스펙은 실측값(`OAuthCallbackError`)과 새 문구, «operator» · «잠시 후 다시 시도» 부재를 핀.
- 🔵 관찰(고치지 않음): `consumer-integration-guide.md` F5 의 마이그레이션 매핑은 `AccessDenied → access_denied` 인데 web-store 는 `AccessDenied → role_denied`(자기 `signIn` 가드용). 동의 거절은 `AccessDenied` 로 오지 않으므로 이 티켓과 무관 — 문서와 코드의 불일치로만 적는다.

## 항목 3 — 콘솔 보안 이벤트 조회 (AC-3) — «포함하지 않고, 화면이 말한다»

- **선택**: 포함하지 않는다. 콘솔 «감사 · 보안 조회» 에서 적용된 소스가 `login_history`/`suspicious` 일 때 필터 아래에 `role="note"` 안내(`data-testid="audit-consumer-pool-note"`): 풀 계정의 로그인·의심 활동은 `consumer-pool` 로 기록되어 사이트 테넌트 목록에 나오지 않는다 — 조용한 빈 목록 금지.
- **근거**: 풀 계정의 IAM 로그인 한 번은 **어느 한 사이트의 로그인이 아니다**(세션 하나가 그 사람이 멤버인 모든 사이트에 SSO — 615 D-8 로 이벤트 테넌트 = `consumer-pool`). 사이트 테넌트로 «포함» 하려면 ① 그 사이트 멤버의 풀 이벤트를 다 붙이면 **다른 사이트에서의 활동**까지 보여 주고(소유자 결정 1 의 취지 «한 사이트가 다른 사이트의 회원 데이터를…» 와 반대), ② 사이트별로 정확히 가르려면 이벤트에 사이트가 있어야 하는데 로그인 이벤트에는 없다(security-service · 이벤트 계약 변경 — 이 티켓 범위 밖). 그래서 말하기를 골랐다.

## 관찰 ④ ⑤

- ④ **동의 화면 부제** (소유자 결정 3): `consent.html` 의 부제를 사이트 로그인 문구(`branding.description`)에서 **동의 전용** «{사이트 이름}에서 내 IAM 계정을 쓰도록 허용합니다.» 로. 사이트 이름 = 보관된 client 의 사이트 테넌트 `display_name`(account-service `GET /internal/tenants/{id}` — 이미 있는 호출; ADR-007 불변 조건 1: 요청에서 읽지 않음), 실패·없음 → «이 사이트»(fail-soft — 라벨 때문에 화면이 실패하지 않는다). 조사 «에서» 는 받침과 무관. 본문의 «처음 이용합니다» → «이용합니다»(본인 탈퇴 후 돌아온 사람도 같은 화면을 본다). 🔵 지금 `display_name` 은 `Fan Platform` · `E-Commerce Platform`(영문) — 바꾸려면 테넌트 표시명 변경(이 티켓 밖).
- ⑤ **가입 직후 비밀번호 재입력**: 의도된 동작으로 둔다(소유자 결정 3 끝 문장). 코드 변경 없음.

## AC

| AC | 판정 | 증거 |
|---|---|---|
| **AC-1** | ✅ 단위·슬라이스 / ⚪ 통합은 CI | 표: 위 «동작 표»(소유자 결정 1·2 기록). `LEFT` 경로: `DELETE /api/accounts/me/site-membership`(본인) · 사이트 운영자 GDPR/`/delete`(운영자). **LEFT 뒤 토큰 없음**: 발급 경로는 615 그대로(`membershipStatus ≠ ACTIVE` → `invalid_grant`) — 게이트 단위 `AuthorizeSessionTenantGatePoolTest` 새 2칸(SELF → 동의 302 · OPERATOR → 통과·발급 거절). **다른 사이트 무영향 대조 시험**: `ConsumerSiteLeaveIntegrationTest`(스토어 운영자 GDPR → 스토어 LEFT · 팬 ACTIVE · 계정 ACTIVE · 이메일 그대로 · `account.deleted` 0 · 재동의로 안 열림 · 다시 GDPR 은 404 / 본인 팬 탈퇴 → 팬 LEFT · 스토어 ACTIVE → 재동의 ACTIVE · `account.created` 2회 그대로 / `*` → DELETED) — **⚪ 로컬 미실행(Docker 없음), CI 첫 실측** |
| **AC-2** | ✅ | 측정 먼저(2차 런 37094305963: `error=OAuthCallbackError`, 일반 fallback) → 그 값에 맞춘 매핑·문구. 단위 `login-form-error-vocab.test.tsx` +2칸(⚪ 로컬 vitest 기동 불가 — 아래) · 스펙이 실측값 · 새 문구를 핀 — 문구 변경 뒤 nightly 확인 런은 PR 본문에 기록 |
| **AC-3** | ✅ | «포함하지 않음 + 화면이 말함» · 근거 위. `AuditScreen.test.tsx` 새 칸(login_history 조회 → `role=note` · `consumer-pool` · 빈 목록과 함께), admin 소스엔 안내 없음 |

## 게이트 (각각 단독 · `cmd > log 2>&1; echo rc=$?`)

| 게이트 | rc | 비고 |
|---|---|---|
| `:projects:iam-platform:apps:account-service:check` | 0 | 새/바뀐 단위·슬라이스 실행 확인(test-results: `LeaveConsumerSiteUseCaseTest` 5 · `ConsumerSiteMembershipLeaveTest` 6 · `ConsumerSiteLeaveControllerSliceTest` 2 · `PoolMemberSiteLookupTest` 12 · `ConsentToConsumerSiteUseCaseTest` 9 · `GdprControllerSliceTest` 7) |
| `:projects:iam-platform:apps:auth-service:check` | 0 | 1차 rc=1 — `SiteConsentPageSliceTest#unknownDecision_400` 의 `verifyNoInteractions` 가 새 사이트 이름 읽기를 물었다 → «이름 읽기 1회만, 쓰기 없음» 으로 고침 |
| `:projects:iam-platform:apps:admin-service:check` | 0 | `GdprAdminUseCaseTest$GdprDelete` 7 · `AdminGdprControllerSliceTest` 13 |
| `@Tag("integration")` 새/바뀐 IT | ⚪ 로컬 미실행 | Docker 없음(`dockerDesktopLinuxEngine` 파이프 없음) — 컴파일만. CI 첫 실측: `ConsumerSiteLeaveIntegrationTest` · `PoolMemberSiteSurfacesIntegrationTest`(V0032 포함) |
| console-web `vitest`(AccountsScreen · AuditScreen · accounts-api, `--maxWorkers=4 --minWorkers=1`) | 0 | 45 passed |
| console-web `tsc --noEmit` · `lint` | 0 · 0 | |
| web-store `lint` · `tsc --noEmit`(e2e 포함) | 0 · 0 | |
| web-store `vitest`(login-form-error-vocab) | 1 — ⚪ **판정 아님** | `ERR_PACKAGE_IMPORT_NOT_DEFINED`(이 호스트 Node 24 × vitest 4 — 시험 0개 실행, 616 과 같은 한계). CI `frontend-unit-tests` 가 권위 |

## bite (되돌린 뒤 재실행 초록 · `false &&` 잔존 grep 0)

| 끈 것 | 돌린 시험 | 결과 |
|---|---|---|
| (A) `GdprDeleteUseCase` 사이트 운영자 → 멤버십 갈래 | `PoolMemberSiteLookupTest` + `ConsentToConsumerSiteUseCaseTest` | 정확히 «사이트 운영자 GDPR → 그 사이트만» 칸 1개 실패 |
| (B) `ConsentToConsumerSiteUseCase` 본인 탈퇴 복귀 갈래 | 〃 | 정확히 «본인 LEFT → 다시 ACTIVE» 칸 1개 실패 (A·B 합쳐 21 중 2) |
| (C) 게이트 `isReopenableByConsent` 갈래 | `AuthorizeSessionTenantGatePoolTest` | 13 중 1 — 정확히 «SELF → 동의 화면» 칸 |

## 배포 순서 · 남은 것

- **account-service 를 auth-service · admin-service 보다 먼저 또는 함께**(V0032 + 응답 `leftBy`/`scope`). 거꾸로면: auth 는 `leftBy` 없음 → 본인 탈퇴도 토큰 거절(보수 — 복귀만 막힘), admin 은 `scope` 없음 → `ACCOUNT` 로 읽음. 콘솔(Vercel)은 `scope` 가 없어도 파싱된다(optional).
- 🔵 **소유자에게 넘길 관찰(이 티켓의 결정 밖 — 고치지 않음)**: 사이트 운영자의 **잠금**은 여전히 풀 계정 하나에 걸린다(스토어 운영자 잠금 → 팬에서도 잠김, 616 결정). 소유자 결정 1 은 «삭제» 에 관한 것이라 잠금은 바꾸지 않았다 — «잠금도 사이트 범위여야 하나» 는 별도 결정.
- 웹 화면의 «사이트 탈퇴» 버튼은 만들지 않았다(스토어·팬 앱에 탈퇴 UI 자체가 없다 — 엔드포인트만). 필요해지면 각 앱 티켓.
