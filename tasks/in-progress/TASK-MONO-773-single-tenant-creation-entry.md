# Task ID

TASK-MONO-773

# Title

`ADR-MONO-080` D9 = **T1** — «조직 만들기»(`/onboarding`) · «테넌트 등록»(`/tenants`)을 **«테넌트 생성» 하나**로: 콘솔 셸 안 · 첫 관리자는 들어온 계정이 정한다 · 두 번째 회사

# Status

in-progress

# Owner

monorepo

# Task Tags

- iam
- platform-console
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (권한 원점 · 셸 가드 상태 · 새 테넌트 상태)

---

# Dependency Markers

- 🔴 **Owner decision (2026-10-15 UTC) — start date gate.** Start this ticket only after this sequence:
  1. Re-bake window: backfill gate passes · `TASK-BE-632` AC-4~8 checked live · `TASK-MONO-784` ⚪② checked live.
  2. `TASK-MONO-784` and `TASK-BE-632` move to `done/`.
  3. Then start 773.

  The earlier option — rewrite the 784 prerequisite as «S1~S6 merged + nightly green» and start at once — is **rejected**, for three reasons:
  - AC-2 · AC-3 assert P3 behaviour (assume `roles` = sets), and P3 can still be rolled back at the backfill gate (revert of #4351).
  - «Second company» *is* 784's mechanism, which is not yet checked live.
  - Merging 773's backend before the bake would ride the same AMI and blur what the window measures.

- **선행**: `TASK-MONO-772` `done/` — `SUPER_ADMIN` 이 남을 첫 관리자로 지정하는 길은 D6 초대가 있어야 하고, 운영자 아닌 풀 계정의 콘솔 세션도 772 의 토큰 경로 위에 선다.
- **선행**: `ADR-MONO-083` ACCEPTED — A(2026-10-14 UTC) **P9** · `TASK-BE-631` 머지(세트 · 배정 모델 — 생성자 기본 세트를 쓸 자리) — 🔴 OD-5 기본값(구현자 선택): 회사 생성자에게 **새 테넌트의 entitled 도메인 «전체 업무» 세트**를 준다(ADR-044 D2 — 새 테넌트에만). 이것이 없으면 ADR-083 P3 뒤 생성자는 자기 회사에서 업무 권한 0 으로 시작한다. AC-2 · AC-3 이 그 단언을 싣는다.
- **선행**: `TASK-MONO-784` `done/` — `ADR-MONO-082` ACCEPTED — **b**(2026-10-10 UTC). «두 번째 회사» 는 기존 운영자 행에 새 테넌트 소속(역할 행 + 배정)을 더하는 것이고, 그 기전(두 409 → 소속 추가 · 회사별 권한 평가)이 784 다.

# Goal

같은 행위(테넌트 생성)가 두 화면으로 갈라져 결과가 다르다 — 온보딩은 «테넌트 + 관리자», `/tenants` 는 «테넌트만»(`console-web/src/app/api/tenants/_proxy.ts:22-30`). `/tenants` 로 만든 B2B 테넌트에는 `SUPER_ADMIN` 도 운영자를 못 앉힌다(면제는 대상 `'*'` 만, `CreateOperatorUseCase.java:100-107`). 하나로 합친다:

| 들어온 계정 | 셸 | 첫 관리자 |
|---|---|---|
| 운영자 아님 | 콘솔 셸 — 사이드바 «가이드 · 테넌트 생성» 만, 운영자 토큰 없음 | **본인** |
| 이미 운영자(다른 회사) | 지금 셸 | **본인** — 두 번째 회사 |
| `SUPER_ADMIN` | 지금 셸 | **지정한 이메일에 D6 초대** — 수락까지 테넌트 = «관리자 대기» |

# Scope

## In Scope

- 계약 먼저: `onboarding-api.md` 와 `admin-api.md` § tenants 를 «테넌트 + 첫 관리자» 하나의 원자 묶음으로(입구 둘의 인증 방식 — 비운영자 OIDC 토큰 / 운영자 토큰) · «관리자 대기» 상태와 그 만료·재초대·정리
- admin-service: 생성 원자 묶음 일반화(ADR-044 D1 · D2 — 셀프 경로의 권한은 **새 테넌트에만**) · `SUPER_ADMIN` 경로 = 테넌트 + 첫 관리자 초대(772 재사용) · 044 D4 트러스트 게이트(인증된 이메일 · 속도 제한 · 신원당 소유 상한)를 **모든 비-`SUPER_ADMIN` 경로**에
- console-web: `(console)` 셸 가드에 «로그인 · 운영자 아님» 상태 — 그 상태에서 레지스트리 등 운영자 토큰 호출을 건너뛴다(401 재로그인 고리 방지; 샘플 방문자 분기 `(console)/layout.tsx:148-160` 이 선례) · «테넌트 생성» 화면 하나 · 사이드바 진입점 · 빈 상태 안내(«회사에 관리자가 있다면 초대를 요청하세요» 포함) · 생성 직후 운영자 토큰 재교환
- `/onboarding` 은 새 화면으로 넘겨주는 주소로 남긴다(콜백 · 리프레시가 지금 그리로 보낸다 — `callback/route.ts:191` · `refresh/route.ts:205`)
- B2C 테넌트 생성은 `SUPER_ADMIN` 전용 유지, 첫 관리자는 같은 초대 규칙

## Out of Scope

- 조직 계층 노드에 테넌트 넣기(`ADR-MONO-047` 개정 — 별도)
- «한 신원 여러 조직» 의 조직 전환 화면 개선(지금 상단 스위처로 충분한지 AC-0 에서 확인만)

# Acceptance Criteria

- [ ] **AC-0** — 착수 시 재측정: 두 입구의 file:line · 온보딩 · `/tenants` 계약 · 셸 가드 · 콜백/리프레시의 `/onboarding` 보냄. «관리자 대기» 상태의 집(account-service `tenants.status` 확장 또는 별도 표)을 정하고 이유를 적는다.
- [ ] **AC-1** — 운영자 아닌 계정: 셸이 열리고 사이드바는 «가이드 · 테넌트 생성» 뿐 · 🔴 **운영자 토큰 없음** · 관리 API 는 거절(대조군).
- [ ] **AC-2** — 운영자 아닌 계정이 생성 → 테넌트 + 본인 `TENANT_ADMIN` · `TENANT_BILLING_ADMIN` + 배정 → 재교환 뒤 개요로. (ADR-083 P9 · OD-5) + 새 테넌트의 «전체 업무» 세트 배정(직접 출처) — 같은 시험에서 그 테넌트 assume `roles` = 그 테넌트 entitled 도메인의 카탈로그 전체 · 🔴 다른 테넌트 세트 행 0.
- [ ] **AC-3** — 이미 운영자인 계정이 두 번째 회사를 생성 → 새 테넌트에만 관리자 · 🔴 기존 테넌트 권한 불변 · 신원당 상한을 넘으면 거절. (`ADR-MONO-082` D6 **b** 행: 기존 운영자 행에 새 테넌트의 `TENANT_ADMIN` · `TENANT_BILLING_ADMIN` 역할 행 + 배정 · 기존 회사 역할 행 **바이트 불변** · 새 회사에서 기존 회사 권한이 통하지 않음(대조군) · 상한 **값**은 AC-0 에서 정한다 — 세는 단위는 082 D4: 그 행의 `TENANT_ADMIN` 역할 행이 가리키는 서로 다른 테넌트 수) · (ADR-083 P9 · OD-5) 새 테넌트에만 «전체 업무» 세트 · 기존 회사의 세트 행 **바이트 불변**(대조군).
- [ ] **AC-4** — `SUPER_ADMIN` 이 생성 + 첫 관리자 이메일 → 테넌트 «관리자 대기» + 초대 · 수락 뒤 활성 · 만료 시 정리 규칙대로. 🔴 관리자 없는 활성 테넌트가 생기지 않는다(ADR-044 D3).
- [ ] **AC-5** — `/onboarding` 직접 접근 · 콜백의 «운영자 아님» 분기 → 새 화면으로.
- [ ] **AC-6** — 콘솔 e2e 디렉터리 grep(`/onboarding`, `onboarding-` testid) 후 수정 · 머지 뒤 첫 `nightly-e2e.yml` 콘솔 잡 확인.
- [ ] **AC-7** — (`TASK-PC-FE-331` 미룸, 2026-10-10 UTC 소유자 결정) 운영자 측면 없는 풀 계정이 비운영자 셸에서 콘솔 로그아웃 → **IAM 세션까지 끝나** 다른 계정(운영자)으로 로그인할 수 있다(스토어 탭 없이). 그리고 773 뒤에도 콘솔 콜백의 `sso_wrong_account` 분기(`console-web/src/app/api/auth/callback/route.ts:113-126`)에 닿는 모집단이 남는지 적는다 — 0 이면 PC-FE-331 은 구현 없이 종결 근거.

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` D9 · D6 · § 새로 생기는 위험
- `docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md` D1~D4 · D7
- `projects/platform-console/specs/services/console-web/architecture.md` (셸 · 인증 흐름)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/onboarding-api.md` · `admin-api.md` § tenants · `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.6

# Edge Cases

- 초대받은 첫 관리자가 수락하지 않고 만료 — 테넌트를 정리할지 «관리자 대기» 로 둘지(AC-0 결정).
- 운영자 아닌 계정이 직원으로 초대받은 상태(772) — 빈 상태 화면이 «받은 초대» 를 먼저 보여 준다.

# Failure Scenarios

1. 셸 가드에 상태를 더하고 레지스트리 호출을 안 건너뛴다 — 운영자 아닌 계정이 401 재로그인 고리에 빠진다.
2. 셀프 경로의 권한 행이 기존 테넌트를 가리킬 수 있다 — ADR-044 D2 위반(권한 상승).

---

# AC-0 기록 (2026-10-15 UTC · 분석=Opus 5.5 — 코드 · 계약 재측정, 티켓 표를 복사하지 않고 다시 쟀다)

> 규칙: 현재 코드가 문서를 이긴다. 문서가 낡은 곳은 «낡음» 으로 적는다. ⚪ = 이번에 재지 않은 것(이유 함께). 이 절은 **설계 제안**이고, «소유자 결정» 표의 항목은 소유자가 답할 때까지 정해지지 않는다.

## 0. 착수 게이트 (Dependency Markers) — 이 트리에서 잰 것

| 조건 | 결과 | 근거 |
|---|---|---|
| `TASK-MONO-772` `done/` | ✅ | `tasks/done/TASK-MONO-772-operator-invite-verified-acceptance.md` |
| `TASK-MONO-784` `done/` | ✅ | `tasks/done/TASK-MONO-784-operator-membership-per-company.md` |
| `TASK-BE-631` · `TASK-BE-632` `done/` | ✅ | `projects/iam-platform/tasks/done/TASK-BE-631-…md` · `…/TASK-BE-632-…md` (main `d819e3781` «BE-632 → done» #4366) |
| 784 이 773 에 넘긴 미결 둘 | 열림 — 아래 OD-3 · § 6 R6 | `TASK-MONO-784…md:359`(상한이 `SUSPENDED` 테넌트를 세는가) · `:635`(상한 경합 cap+1) |

## 1. 두 입구 — 지금 무엇을 만드나 (측정 1)

| | `/onboarding` «조직 만들기» | `/tenants` «테넌트 등록» |
|---|---|---|
| 화면 가드 | `(onboarding)/layout.tsx:24-25` — 운영자 쿠키 있으면 `/`, IAM access 없으면 `/login?error=session_expired`. 셸 밖(사이드바 · 스위처 없음, `:16-17`) | `(console)` 셸 안 · 화면이 SUPER_ADMIN 전용 안내(`(console)/tenants/page.tsx:14,47,68`) |
| BFF | `app/api/onboarding/organizations/route.ts:40-126` — 운영자 토큰 **아님**: IAM access 쿠키를 `subjectToken` 으로(`:43,68`) · 201 뒤 같은 access 로 운영자 토큰 **재교환** + `TENANT_COOKIE`=새 테넌트(`:103-116`) · 재교환 실패면 `{ready:false}`(`:117-125`) | `app/api/tenants/route.ts:58-82` → `createTenant` · 본문 `{tenantId, displayName, tenantType, reason, idempotencyKey?, orgNodeId?}`(`_proxy.ts:24-33`) — **첫 관리자 칸 없음** |
| 백엔드 | `POST /api/admin/onboarding/organizations` — `OnboardingController.java:49-74`(`@SelfServiceEndpoint`, `subjectToken` 검증 `:57`, 이메일 · 이름은 권위 계정에서 `:61-66`) → `SelfServiceOnboardingUseCase.java:58-101` | `POST /api/admin/tenants` → `CreateTenantUseCase.java:56-125` |
| 인증 | 콘솔 client(`platform-console-web`) OIDC access token, `token_type` 클레임 없는 것만(`onboarding-api.md:18-24`) | 운영자 JWT, `SUPER_ADMIN` 만(`admin-api.md:2274,2297,2339`) |
| account-service `tenants` | 1행 `B2B_ENTERPRISE` 고정 · `ACTIVE`(`SelfServiceOnboardingUseCase.java:38,82` · `Tenant.java:98-108` «initial status always ACTIVE») | 1행 · `B2C_CONSUMER`/`B2B_ENTERPRISE` · `ACTIVE`(`CreateTenantUseCase.java:89`) |
| 구독 | **0**(ADR-044 D6 · `onboarding-api.md:63-65`) | 0 |
| `admin_operators` | 측면 없음 → 새 행(홈 = 새 테넌트, OIDC 전용, `oidc_subject`=`sub`)(`FirstAdminProvisioner.java:84-97`) · `ACTIVE` 회사 측면 → **만들지 않음**(784 S5, `:137-145`) | 0 |
| 역할 행 | `TENANT_ADMIN` · `TENANT_BILLING_ADMIN` @ 새 테넌트, `granted_by=NULL`(`FirstAdminProvisioner.java:52,99-107` / `OperatorMembershipAddition.java:84-93`) | 0 |
| `operator_tenant_assignment` | 1행(`FirstAdminProvisioner.java:116-118` / `OperatorMembershipAddition.java:92`) | 0 |
| 업무 세트 행(`operator_domain_set_grant`) | **0** — 두 메서드 어디에도 세트 쓰기가 없다(`FirstAdminProvisioner.java:69-145` 전체) | 0 |
| 감사 · 이벤트 | 감사 행 없음(관리 행위자 없음) | `TENANT_CREATE` 감사 + outbox `tenant.created`(`CreateTenantUseCase.java:80-101`) |
| 실패 보상 | 테넌트 생성 뒤 실패 → `SUSPENDED` 로 보상(`SelfServiceOnboardingUseCase.java:92-119`) | 없음(관리자 0 이 정상 결과) |
| 판정 순서(생성 전) | 측면 없음 / `'*'`·비-`ACTIVE` → 409 / 상한 → 422 / 아니면 소속 추가(`:71-77` · `onboarding-api.md:100-108`) | 슬러그 · 예약어 · 이름 · 타입(`:60-74`) |

## 2. `SUPER_ADMIN` 이 `/tenants` 테넌트에 운영자를 못 앉히는 이유 — 그리고 772 가 이미 푼 것 (측정 2)

- 🔴 **티켓 Goal · ADR-080 D9 의 인용(`CreateOperatorUseCase.java:100-107` «면제는 대상 `'*'` 만») 은 낡았다.** 772 S7 이후 그 클래스는 비-`'*'` 생성을 **누가 하든** 첫 줄에서 `422 OPERATOR_INVITATION_REQUIRED` 로 거절한다(`CreateOperatorUseCase.java:36-46, 58-63`). 결론(«직접 생성으로는 못 앉힌다»)은 같지만 기전이 다르다. (ADR-080 본문은 바이트 고정이라 고치지 않는다.)
- ✅ **772 초대가 이미 길을 열었다**: `SUPER_ADMIN`(`'*'`)은 «운영자가 하나도 없는 테넌트» 에도 초대한다 — 클래스 주석이 773 을 이름으로 부른다(`OperatorInvitationUseCase.java:33-36`). 발급은 테넌트 **존재**만 본다(`:101-102`), 상태는 안 본다.
- 🔴 **남는 구멍 둘** (773 이 닫을 것):
  1. 생성과 초대가 두 호출이라 그 사이 · 수락 전까지 **관리자 없는 `ACTIVE` 테넌트**가 있다(ADR-044 D3 위반 — AC-4 의 🔴).
  2. 수락은 테넌트가 **`ACTIVE` 여야** 한다(`OperatorInvitationAcceptanceUseCase.java:207-217`). «관리자 대기» 를 비-`ACTIVE` 로 두면 첫 관리자가 수락하지 못한다 → 수락 경로에 «첫 관리자 초대 + 대기 테넌트» 갈래가 필요하다(§ 5).
- 참고: 수락은 발급자가 여전히 `ACTIVE` · 범위 안이어야 한다(`:219-229`) — 발급한 `SUPER_ADMIN` 이 정지되면 첫 관리자 초대가 무효가 된다. 재발송은 재발송자를 `invitedBy` 로 바꾼다(`OperatorInvitationUseCase.java:168-187`) — 다른 `SUPER_ADMIN` 이 살릴 수 있다.

## 3. 콘솔 셸 가드 · 콜백 · 리프레시 (측정 3)

**지금의 셸 가드** (`(console)/layout.tsx`):

| 상태 | 처리 | 근거 |
|---|---|---|
| 샘플 방문자(access · operator · refresh 쿠키 셋 다 없음) | 샘플 셸 | `:152-156` · `session.ts:345-351` (🔵 티켓의 `:148-160` → 지금 `:152-164`) |
| 인증 안 됨 + refresh 있음 | `GET /api/auth/refresh` | `:156-163` |
| 인증 안 됨 + refresh 없음 | `/login?redirect=` | `:159-163` |
| 인증 = access **그리고** operator 쿠키 | 셸 | `session.ts:297-299` |
| **«로그인 · 운영자 아님»(access 만)** | 셸에 못 들어온다 — 위 «인증 안 됨» 가지로 떨어져 refresh 로 간다 → 재교환 401 → `/onboarding` | `:156` · `refresh/route.ts:235-240` |

**운영자 토큰이 필요한 셸 호출 (비운영자 상태에서 건너뛸 것)**: 레지스트리 `getCatalog()`(`:211-239` — 401 이면 `RE_LOGIN_PATH`, `:237`) · `getSelfRolesOrNull()`(`/api/admin/me`, `:199`) · `listOrgNodes()` · `getOrgNodeTenants()`(`:259-278`) · `NotificationBell`(브라우저 폴링, `apiClient` — `features/notifications/hooks/use-notifications.ts:4`; 401 → 브라우저 refresh → 재교환 401 → `/onboarding`, 이것이 Failure Scenario 1 의 고리) · `DemoHeartbeat`(`:303`, 라우트가 세션 재확인) · `needsReassume`(`:182` — 활성 테넌트 없으면 false, 무해). 그리고 **셸 안 각 페이지**의 직접 URL 접근(페이지마다 운영자 API → 401 → `/login?error=session_expired` → SSO 무음 로그인 → 다시 비운영자 셸): 무한은 아니지만 고리처럼 보인다 → 경로 허용 목록 리다이렉트가 필요하다(§ 7 S6).

**콜백 · 리프레시가 `/onboarding` 으로 보내는 곳** (🔵 티켓 줄 번호 낡음):

| 자리 | 지금 | 티켓 인용 |
|---|---|---|
| 콜백 — 교환 `401`(`fail_closed`)만 | `callback/route.ts:304-322` (`:319-321` 이 redirect) | `:191` (지금 `:190` 은 `redirect_uri` 줄) |
| 리프레시 — `operator_not_provisioned` | `refresh/route.ts:235-240` | `:205` (지금 `:203-204` 는 `no_refresh_token`) |
| `sso_wrong_account` | 판별 함수 `callback/route.ts:113-126` · 분기 `:232-235` | `:113-126` (함수 쪽만 맞다) |

🔴🔴 **티켓 Scope 가 빠뜨린 서비스 — auth-service.** 지금 **측면 없는 풀 계정은 콘솔 토큰 자체를 못 받는다**(`auth-api.md:729-733` «측면 없음 → 거절 `invalid_grant` `'consumer-pool'`»). 그래서 그 사람은 콜백의 토큰 단계(`callback/route.ts:209-236`)에서 `sso_wrong_account` 로 떨어지고 **교환 401 → `/onboarding` 에 닿지 않는다**(`console-integration-contract.md:3825` 가 이를 명시). 계약이 773 의 변경 자리를 이미 지목한다: «773 이 넓힐 때 바꾸는 곳은 위 표의 «측면 없음» 행 한 곳 — 두 거절 상수는 그대로»(`auth-api.md:739`). 이것 없이는 AC-1 · AC-2 · AC-5 · AC-7 이 도달 불가다. ⇒ 슬라이스 S5(§ 7).

**AC-7 (로그아웃)**: 콘솔 로그아웃은 revoke + 쿠키 삭제 + `id_token_hint` 가 있으면 IdP `end_session`(`logout/route.ts:35-92`) — `id_token` 이 없으면 로컬 로그아웃만(`:85-90`). 비운영자 셸 토큰도 지금 콘솔 토큰과 같은 규칙으로 `id_token` 을 받으면 IAM 세션까지 끝난다(`AccountMenu` 는 비샘플 셸에 이미 마운트, `layout.tsx:336-338`). **773 뒤 `sso_wrong_account` 모집단** = `auth-api.md:732` 의 «측면이 `ACTIVE` 아님(퇴사 · 정지)» 행이 남는지에 달렸다 → OD-6. ⚪ `'consumer-pool'` 거절의 **다른 발생원**(콘솔 client 외 경로)은 전수하지 않았다(`TenantClaimTokenCustomizer.refuseConsumerPoolTenant` 호출처 미열람).

## 4. ADR-044 D4 트러스트 게이트 — 지금 어디에 있나 (측정 4)

| 게이트 | 셀프 온보딩 지금 | 근거 |
|---|---|---|
| 인증된 이메일 | **없음** — 컨트롤러는 계정 상세만 읽고(`OnboardingController.java:61-66`), 그 응답에 인증 시각 칸이 없다(`AccountServiceClient.java:403-409`). 계약도 «이메일 인증 강제 = 범위 밖»(`onboarding-api.md:90`) | 재사용 후보: 772 수락의 `VerifiedEmailMatchPort`(`AccountServiceInvitationClient.java:96-114` — `MATCHED`+`emailVerifiedAt` / `EMAIL_NOT_VERIFIED` 403) · 단 그 판정은 «ACTIVE 풀 계정» 만 `MATCHED`(`OperatorInvitationAcceptanceUseCase.java:192-193`) — `iam` 자격 계정 호출자는 `NOT_ELIGIBLE` 이 된다(⚪ 그 모집단 크기 미측정; `'*'` 측면이면 어차피 409) |
| 속도 제한 | 게이트웨이 **global(IP 당 100/1s)만** — 전용 스코프 없음(`RouteConfig.java:52-77` · `application.yml:116-129`). admin-service 쪽 카운터 없음 | signup 스코프(5/60s, `application.yml:121-123`)가 같은 모양의 선례 |
| 신원당 소유 상한 | ✅ 784 S5 — `OperatorOwnedTenantLimit.java:59-67` · 값 `admin.onboarding.max-owned-tenants-per-operator` **기본 2**(`application.yml:155-156` · `OperatorOwnedTenantLimit.java:37`) · 단위 = 그 행의 `TENANT_ADMIN` 역할 행의 서로 다른 비-`'*'` 테넌트 수, **상태 무관 · 출처 무관**(`:17-21, 50-57`) · **측면 있는 호출자에게만** 묻는다(`SelfServiceOnboardingUseCase.java:74-77`; 측면 없음은 셀 것이 0 — `onboarding-api.md:119`) · 초대 수락은 안 묻는다(`:24-25`) | 경합: 잠금 없음 → 동시 두 요청이 cap+1(`TASK-MONO-784…md:635`) |

⇒ «모든 비-`SUPER_ADMIN` 경로» = 이 엔드포인트 하나(측면 없음 · `ACTIVE` 회사 측면 두 갈래). 773 은 이메일 · 속도 둘을 **더하고** 상한은 **값만** 정한다.

## 5. «관리자 대기» 의 집 (측정 5 — 추천, 결정은 OD-1 · OD-2)

**테넌트 상태를 지금 읽는 곳**:

| 읽는 곳 | 술어 | 대기 상태를 새 값으로 두면 | 근거 |
|---|---|---|---|
| account `ActiveTenantGuard` (가입 · 프로비저닝) | `!isActive()` → `TENANT_SUSPENDED` | 막힌다(옳다) — 오류 이름만 어색 | `ActiveTenantGuard.java:30-44` · `Tenant.java:73` |
| account `ProvisionAccountUseCase` · `BulkProvisionAccountUseCase` | 정지면 거절 | 같다 | `:63` · `:92` |
| auth `TenantSignupEligibilityResolver` | `"ACTIVE"` 문자열 | 막힌다 | `:51` |
| admin 콘솔 레지스트리(스위처) | `ACTIVE` 목록만 | 안 보인다(옳다) | `ConsoleRegistryUseCase.java:62, 86-88, 251-262` |
| admin 초대 **수락** | `ACTIVE` 아니면 `INVALIDATED` | 🔴 첫 관리자가 수락 못 함 — 갈래 필요 | `OperatorInvitationAcceptanceUseCase.java:207-217` |
| admin 테넌트 목록 · PATCH | 상태 enum `ACTIVE`·`SUSPENDED`, 전이표 | 계약 · 전이표 개정 | `admin-api.md:2283-2291, 2361, 2419` · `Tenant.java:122-135` |
| 콘솔 `/tenants` 목록 | `z.enum(TENANT_STATUSES)` | 🔴 새 값이 오면 파싱 실패 → 같은 PR 에서 넓혀야 | `_proxy.ts:5, 42` |
| assume | 테넌트 상태를 읽지 않는다(배정 · 구독만) | ⚪ `'*'` 운영자가 대기 테넌트를 assume 할 수 있는지 미측정 | `OperatorAssignmentCheckUseCase.java:182` (운영자 상태만) |
| 저장 칸 | `tenants.status VARCHAR(10)` | 🔴 `PENDING_ADMIN`(13자)은 안 들어간다 — 이름을 10자 이하로 하거나 마이그레이션으로 넓힌다 | `account-service …/V0009__create_tenants.sql:8` (이후 `ALTER TABLE tenants` 는 `V0027:52-54` 의 `org_node_id` 뿐) |

**772 초대 만료가 이미 하는 것**: TTL `P7D`(`application.yml:148-151` · `OperatorInvitationWriter.java:44,89`) · 만료는 **읽을 때 판정, 잡(job) 없음**(같은 주석 · 수락 `:123-127`) · 만료된 대기 초대도 취소 · 재발송 가능(`OperatorInvitationUseCase.java:151-187`). 테넌트 하드 삭제 엔드포인트는 없다(`onboarding-api.md:67-70`).

**선택지와 추천**:

| 집 | 무엇 | 평가 |
|---|---|---|
| **(가) account-service `tenants.status` 에 새 값**(예: `PENDING` — 10자 이내) | 생성 시 대기 → 첫 관리자 수락이 `ACTIVE` 로 올린다 · PATCH 로는 대기→`ACTIVE` 불가(409) | **추천.** ADR-044 D3 을 글자 그대로 지킨다(관리자 없는 `ACTIVE` 테넌트 0) · 기존 «`ACTIVE` 만» 읽기가 전부 옳은 쪽으로 닫힌다(위 표) · `/tenants` 목록에 «관리자 대기» 가 보인다. 대가: enum · 전이표 · 이벤트 · 콘솔 enum · 수락 갈래 · (이름이 길면) 마이그레이션 |
| (나) `SUSPENDED` 재사용 + admin-service 표식 표 | 계정 쪽 무변경 | 정지의 뜻을 겹쳐 쓴다 — `SUPER_ADMIN` 이 PATCH 로 재활성하면 관리자 없는 `ACTIVE` 가 생긴다(가드 추가 필요) · 목록에 «정지» 로 보인다 |
| (다) admin 쪽 파생(테넌트는 `ACTIVE`, «첫 관리자 대기 초대가 있음» 으로 판정) | 스키마 최소 | **기각** — 관리자 없는 `ACTIVE` 테넌트를 정상 상태로 만든다(ADR-044 D3 · AC-4 🔴 위반) |

## 6. ADR-082 두 번째 회사 — 재사용할 자리 (측정 6)

- «소속 추가» 의 자리는 **하나**다: `OperatorMembershipAddition` — `requireCanReceive`(`'*'` · 비-`ACTIVE` · 이미 소속 → 409, `:61-75`) · `write`(대상 테넌트 역할 행 + 배정**만**, 호출자 트랜잭션 안, `:84-93`) · 캐시 무효화(`:96-104`). 클래스 주석이 773 을 이름으로 부른다(`:17-20`).
- 셀프 온보딩은 이미 그것을 쓴다: `SelfServiceOnboardingUseCase.java:74-77, 89-99` → `FirstAdminProvisioner.addMembership` `:137-145`. 784 AC-0 의 «773 을 막지 않는 조건» ①~③(`TASK-MONO-784…md:274`).
- ⇒ **773 은 두 번째 회사용 새 백엔드 경로를 만들지 않는다.** 콘솔 «테넌트 생성» 화면이 운영자(비-`SUPER_ADMIN`)일 때도 **같은 온보딩 엔드포인트**(OIDC `subjectToken`)를 부르면 표 5행이 탄다. 773 이 더하는 것은 (OD-5 세트 행 · 이메일 · 속도 게이트)뿐이고, 그 쓰기는 `provision` · `addMembership` **두 메서드 모두**의 같은 트랜잭션 안에 둔다.
- Out of Scope 확인(스위처로 충분한가): ✅ 충분하다 — 스위처 = (`iam` 제품은 모든 테넌트에 묶임 `ConsoleRegistryUseCase.java:166-169`) ∩ 실효 범위(배정 ∪ 홈, `:83-84`) ∩ `ACTIVE`(`:86-88`) → 소속 추가된 새 회사는 재교환 뒤 스위처에 뜬다(`tenant-options.ts:13-20`). 대기 테넌트는 안 뜬다.

## 7. 콘솔 e2e grep (측정 7 · AC-6)

| 디렉터리 | `/onboarding` · `onboarding-` · `onboard`(대소문자 무시) · `조직 만들기` · `테넌트 등록` · `/tenants` |
|---|---|
| `projects/platform-console/apps/console-web/tests/e2e/` (4 spec) | **0** |
| `projects/platform-console/apps/console-web/e2e-smoke/` (11 spec) | **0** |
| `tests/federation-hardening-e2e/` (16 spec + fixtures — 티켓의 경로는 콘솔 아래가 아니라 **리포 루트**다) | **0** |
| `projects/iam-platform/tests/` | **0** |
| (참고) 단위 테스트 — 바뀔 곳 | `tests/unit/auth-routes.test.ts:411-448` · `auth-idle-refresh.test.ts:217-223, 321` · `mfa-step-up-routes.test.ts:226-238, 376-382` · `onboarding-proxy.test.ts` · `onboarding-client.test.ts` · `features/onboarding/CreateOrganizationForm.test.tsx` · `sample-fetch-allowlist.test.ts:131-136` · `session-end-demo-stopped.test.tsx:214-215`(샘플 모드가 `/onboarding` 을 `/` 로 바꾸는 목록 — 새 화면 경로도 넣어야 한다) |

⇒ AC-6 의 «grep 후 수정» 은 **고칠 e2e 가 없다**(0). 대신 지금 `/onboarding` 은 e2e 커버리지 0 이다 — 773 은 새 spec 을 **추가**해야 AC-1 · AC-2 · AC-5 · AC-7 이 브라우저에서 재진다(S6).

## 8. 티켓에서 틀린 것 · 낡은 것

1. 🔴 **Scope 에 auth-service 가 없다** — 측면 없는 풀 계정의 콘솔 토큰 발급(`auth-api.md:731-732, 739`)을 바꾸지 않으면 비운영자 셸에 들어올 사람이 없다(§ 3).
2. 🔴 **AC-2 «entitled 도메인의 카탈로그 전체» 는 생성 시점에 공집합이다** — 새 테넌트는 구독 0 으로 태어난다(ADR-044 D6 · `onboarding-api.md:63-65`). 그리고 `all-domains-full` 은 `fan` 을 포함하는데(`admin-api.md:3664-3665` · `V0052…sql:137, 174-177`) `fan` 은 `fan-platform` 만 구독할 수 있어(`admin-api.md:2586`) **어떤 고객 테넌트에도 배정 API 로는 영원히 못 준다**(`admin-api.md:3613` `422 DOMAIN_NOT_SUBSCRIBED` — 소유자 실측 2026-10-14 와 같다). 그대로 두면 AC-2 의 단언은 «∅ = ∅» 로 공허하게 초록이다 → OD-4.
3. Goal 의 `CreateOperatorUseCase.java:100-107` 기전 인용 낡음(§ 2). 그리고 «`SUPER_ADMIN` 이 못 앉힌다» 의 해법(초대)은 772 가 이미 냈다 — 773 의 일은 **원자성(D3) · 대기 상태 · 수락 갈래**다.
4. 줄 번호 낡음: `callback/route.ts:191` → `:319-321` · `refresh/route.ts:205` → `:235-240` · `(console)/layout.tsx:148-160` → `:152-164`(§ 3).
5. AC-3 «신원당 상한을 넘으면 거절» 은 **이미 구현**(784 S5) — 773 은 값(+ `SUSPENDED` 셈 · 경합)만 정한다.
6. AC-7 «0 이면 PC-FE-331 종결» — 773 뒤에도 «측면 비-`ACTIVE`» 거절 행을 남기면 모집단은 0 이 아니다(OD-6).
7. Edge Case 2 «빈 상태 화면이 받은 초대를 먼저 보여 준다» — 운영자 토큰 없는 사람이 **자기 초대 목록**을 읽는 표면이 없다(초대 목록 `GET /api/admin/operator-invitations` 는 `operator.manage` 게이트, `OperatorInvitationUseCase.java:115-147`; iam specs 에 «받은 초대» 표면 grep 0) → 새 계약(OD-7).
8. AC-6 의 `tests/federation-hardening-e2e` 는 리포 루트 경로다(§ 7).
9. 문서 낡음(773 S1 에서 같이 고칠 것): `console-web/architecture.md:279`(교환 401 → «강제 재로그인 `not_provisioned`» — 코드는 `/onboarding`) · `admin-api.md:2564`(구독 POST «Granted to roles: SUPER_ADMIN» — `rbac.md:113` 은 `TENANT_BILLING_ADMIN` 에도 `subscription.manage`) · `onboarding-api.md:90`(이메일 인증 «범위 밖») · `onboarding-api.md:94` · `console-integration-contract.md:3820, 3825`(«773 이 연다» 문장들).

## 9. 소유자 결정 — S1 전에 필요 (각 항목: 선택지 · 추천. 추천은 **구현자 선호**, 결정 아님)

| OD | 질문 | 선택지 | 추천 · 이유 |
|---|---|---|---|
| **OD-1** | «관리자 대기» 의 집 | (가) account `tenants.status` 새 값 / (나) `SUSPENDED` + admin 표식 / (다) admin 파생(`ACTIVE` 유지) | **(가)**, 값 이름 10자 이내(예: `PENDING`) 또는 칸 확장 마이그레이션. D3 를 글자대로 지키고 기존 `ACTIVE` 술어가 전부 닫힌 쪽으로 동작한다(§ 5). (다)는 D3 위반 |
| **OD-2** | 첫 관리자 초대가 **만료**되면 | (a) **대기로 둔다** — 잡 없음, `/tenants` 에 «관리자 대기 · 초대 만료» 표시, `SUPER_ADMIN` 이 재발송 또는 «정리»(= 초대 취소 + 테넌트 `SUSPENDED`) / (b) 만료 시 자동 정리(새 스케줄 잡 → `SUSPENDED`) / (c) 자동 삭제 | **(a)**. 772 의 «만료는 읽을 때, 잡 없음» 설계(`application.yml:148-149`)와 같은 모양이고, 대기 테넌트는 로그인 · 가입 · 스위처 모두 닫혀 있어(§ 5) 남아도 해가 없다. 하드 삭제는 없다(c 불가). 슬러그 점유만 대가 |
| **OD-3** | 신원당 상한 **값** · `SUSPENDED` 테넌트를 세는가 | 값: 2(지금) / 3 / 더 크게. 셈: 상태 무관(지금) / `ACTIVE` 만 | **값 3 · 상태 무관 유지.** 단위가 출처 무관(초대받은 `TENANT_ADMIN` 소속도 센다, `OperatorOwnedTenantLimit.java:17-20`)이라 2 면 «초대받은 회사 1 + 내 회사 1» 인 사람이 자기 두 번째 회사를 못 만든다. `ACTIVE` 만 세면 «만들기 → 정지 → 또 만들기» 순환이 열린다(784 `:359`). 설정 키라 코드 변경 없이 올릴 수 있다 |
| **OD-4** | AC-2 · AC-3 의 «전체 업무»(ADR-083 OD-5)를 무엇으로 | (A) **`all-domains-full` 한 행, 전용 출처**(예: `ORIGIN` — `legacy-compat`/`MIGRATION` 과 같은 «API 배정 불가 · 시스템만 쓰는» 모양), 쓰기 때 entitled 검사를 하지 않는다 · 토큰은 P3 의 `∩ entitled` 가 거른다 / (B) 생성 시점 «구독한 도메인의 `<d>-full`» = 태어날 때 ∅ + 이후 OD-4(자기 배정) 안내 / (C) 구독 활성 시점에 생성자에게 `<d>-full` 자동 배정(새 훅 + «생성자» 기억) | **(A).** 이유: ① 세트 행이 entitled 밖 도메인을 덮는 상태는 이미 **합법**이다(«구독이 해지돼도 행은 남는다 — 토큰에서 걸러진다», `data-model.md:386` · `admin-api.md:3607`) ② 토큰 상한은 쓰기 게이트가 아니라 auth 의 `∩ entitled` 다(ADR-083 D6) — 권한이 넓어지지 않는다 ③ 행 하나가 «구독을 켜는 순간 그 도메인 전체» 를 뜻해 OD-5 의 의도(«생성자가 업무 0 으로 시작하지 않는다»)를 실제로 이룬다 ④ P5~P8 카탈로그 확장이 그대로 따라온다(«전체» 의 뜻). 🔴 대가: ADR-083 D5 «구독 밖 도메인의 세트 배정은 서버가 거절» 의 **예외**를 계약 · ADR 덧붙임으로 적어야 한다(배정 API 가 아니라 생성 원자 묶음 한 자리만) · 개인 회수(`DELETE …/domain-permission-sets/{setId}`)가 이 출처도 지우게 한다(`MIGRATION` 과 같은 취급, `admin-api.md:3622-3623`). (B)는 OD-5 를 사실상 «0 에서 시작» 대안으로 바꾼다 — 고르면 ADR-083 OD-5 기록을 고쳐야 한다. **AC-2 단언(A 일 때)**: 같은 시험에서 구독 전 assume `roles`=[](대조군) → `wms` 구독 → assume `roles` = `wms` 카탈로그 8개 · 다른 테넌트 세트 행 0 · `fan` 역할 0 |
| **OD-5** | `SUPER_ADMIN` 경로에서 초대로 들어온 **첫 관리자**도 OD-4 의 세트를 받는가 | 받는다(수락 때, 첫 관리자 초대일 때만) / 안 받는다(OD-5 는 «생성자» 만) | **받는다.** 그 사람이 그 테넌트의 사실상 창립 관리자다 — 안 주면 셀프 경로와 결과가 갈린다(이 티켓이 없애려는 «같은 행위 · 다른 결과»). 일반 초대 수락은 지금처럼 0(ADR-083 D8) |
| **OD-6** | 측면이 **비-`ACTIVE`**(퇴사 · 정지)인 풀 계정의 콘솔 토큰 | (a) 비운영자 셸 토큰을 준다 — 운영자 토큰 없음, «테넌트 생성» 은 409(`onboarding-api.md:106`)로 설명 / (b) 지금처럼 거절(`'consumer-pool'`) | **(a).** 술어가 «풀 계정이면 셸» 하나로 줄고, 콘솔 client 의 `'consumer-pool'` 거절 모집단이 0 이 되어 `TASK-PC-FE-331` 을 구현 없이 닫을 근거가 생긴다(AC-7). 셸에는 운영자 토큰도 관리 API 도 없으니 넓어지는 권한 0. (b)면 PC-FE-331 모집단 ≠ 0 |
| **OD-7** | 비운영자의 «받은 초대» 읽기(Edge Case 2) | (a) 새 읽기 — 온보딩 엔드포인트와 같은 `subjectToken` 인증, **그 계정의 인증된 이메일로 온** `PENDING` · 미만료 초대만 / (b) 표면 없이 «메일함을 확인하세요» 안내만 | **(a)**, 인증된 이메일일 때만(미인증이면 빈 목록 + 인증 안내). 남의 이메일 초대 열거가 생기지 않는다. (b)면 Edge Case 2 를 «안내 문구» 로 고쳐 닫는다 |
| **OD-8** | 속도 제한 값 | per-IP: 게이트웨이 새 스코프 `onboarding`(signup 과 같은 5/60s) · per-identity: (i) 상한(OD-3)으로 갈음 / (ii) 별도 카운터 | **per-IP 5/60s + per-identity = 상한으로 갈음.** 신원당 총량은 상한이 이미 묶고, 새 계정 스팸은 «인증된 이메일» + per-IP 가 문다. ADR-044 D4 가 «per-identity + per-IP» 를 적었으므로 소유자 확인이 필요하다 |

(구현자 결정 — 소유자 질문 아님: 상한 경합은 `addMembership` 트랜잭션 안에서 운영자 행 잠금 + 재계수로 닫는다 / 비운영자 상태는 «운영자 쿠키 없음» 추론이 아니라 **교환 `401` 에서만 세우는 명시 표식**으로 판정한다(§ 10 R2) / 새 화면 경로는 `/tenants/[tenantId]` 와 겹치지 않게 한다 — `/tenants/new` 를 쓰면 슬러그 `new` 를 예약어에 더한다.)

## 10. 슬라이스 제안 (784 S1~S6 모양 — 계약 먼저)

| 슬라이스 | 무엇 | 파일(주요) | 시험 |
|---|---|---|---|
| **S1 계약** | 위 OD 답을 계약으로. 온보딩: 이메일 게이트(`403 EMAIL_NOT_VERIFIED` — 770 공용 이름) · `429` · 상한 값 · OD-4 세트 행 · OD-7 읽기 · 판정 표에 게이트 자리. 테넌트: `POST /api/admin/tenants` 에 `firstAdmin{email, displayName}`(B2B · B2C 공통, `SUPER_ADMIN`) · 대기 상태 · 전이표(대기→`ACTIVE` 는 수락만) · OD-2 정리 행위. 초대: «첫 관리자 초대» 표지 · 대기 테넌트 수락 갈래 · 수락 = 활성화. 세트: OD-4 출처 · 회수 규칙. auth: `auth-api.md:729-739` 표 «측면 없음»(+OD-6) 행 → 발급. 콘솔: § 2.6 · 2.6.1 · 2.6.3 비운영자 상태 · `/onboarding` → 새 화면. 게이트웨이 스코프. § 8-9 낡은 문장 | `onboarding-api.md` · `admin-api.md`(§ Tenant Lifecycle · § Operator Invitation · § 도메인 업무 권한 세트) · `auth-api.md` · `internal/auth-to-admin.md` · `internal/account-*.md`(테넌트 상태) · `events/tenant-events.md` · `gateway-api.md` · `admin-service/data-model.md` · `rbac.md` · `features/multi-tenancy.md` · `console-integration-contract.md` · `console-web/architecture.md` · ADR-083 · ADR-044 덧붙임(본문 불변) | 문서 — 링크 · 앵커 가드 |
| **S2 account-service** | 대기 상태 값 · 전이(생성 시 대기 허용, 대기→`ACTIVE`/`SUSPENDED`) · (필요 시) `status` 칸 확장 마이그레이션 · 내부 생성에 초기 상태 | `TenantStatus.java` · `Tenant.java:98-135` · `TenantProvisionUseCase.java` · 새 `V00xx` | 대기 테넌트 가입 거절 IT · 전이 단위 · 마이그레이션 IT |
| **S3 admin 셀프 경로** | 이메일 게이트(`VerifiedEmailMatchPort` 재사용, 테넌트 생성 **전**) · 상한 값 · 경합 잠금 · OD-4 세트 행(`provision`·`addMembership` 같은 트랜잭션) · OD-7 읽기 · (OD-8) 게이트웨이 스코프 | `OnboardingController.java` · `SelfServiceOnboardingUseCase.java` · `FirstAdminProvisioner.java` · `OperatorOwnedTenantLimit.java` · `application.yml:156` · 세트 쓰기 포트 · `gateway …/RouteConfig.java:52` · `application.yml:116` | `SelfServiceOnboardingIntegrationTest` 확장: 🔴 미인증 이메일 → 403 · 테넌트 0(대조군: 인증 뒤 201) · AC-2 행 단언 · AC-3 기존 회사 역할 행 + 세트 행 **바이트 불변** · 새 회사에서 기존 권한 불통 · 상한 초과 422 · 동시 두 요청 = 하나만 |
| **S4 admin `SUPER_ADMIN` 경로** | `POST /tenants` + `firstAdmin` → 대기 테넌트 + 첫 관리자 초대(772 `OperatorInvitationWriter` 재사용) · 초대 실패 보상 · 수락: 대기 테넌트 허용(첫 관리자 초대만) → 쓰기 → 활성화(+OD-5) · PATCH 대기→`ACTIVE` 거절 · OD-2 정리 | `CreateTenantUseCase.java` · `OperatorInvitationUseCase.java` · `OperatorInvitationAcceptanceUseCase.java:207-217` · `UpdateTenantUseCase.java` | AC-4 IT: 생성 직후 상태 = 대기 · 대기 테넌트 로그인/가입/스위처 0 · 수락 → `ACTIVE` + 역할 둘 · 🔴 어느 순간에도 «`ACTIVE` ∧ `TENANT_ADMIN` 0» 이 없음 · 만료 후 OD-2 규칙 · 일반 초대(비-첫관리자)는 대기 테넌트에서 여전히 거절(대조군) |
| **S5 auth-service** | `auth-api.md:731-732` «측면 없음»(+OD-6) 행 → 콘솔 토큰 발급(`id_token` 포함 · `roles` 없음) · `operator_eligibility_unavailable` 행 그대로 | 콘솔 토큰 발급자(`TenantClaimTokenCustomizer` 주변 — S5 AC-0 이 정확한 자리를 잰다) | 발급 표 IT · 🔴 AC-1 대조군: 그 토큰을 `/api/admin/operators` 에 Bearer 로 → 401 · 교환 → 401 · 온보딩 엔드포인트만 통과 |
| **S6 console-web** | 비운영자 표식(교환 `401` 에서만 — 콜백 `:304-322` · 리프레시 `:235-240`) · 셸 가드 갈래(레지스트리 · `/me` · org-nodes · 벨 · 하트비트 건너뜀) · 경로 허용 목록(가이드 · 테넌트 생성 · 받은 초대) 밖은 새 화면으로 · 사이드바 둘 · «테넌트 생성» 화면 하나(역할로 갈림: 비운영자/회사 운영자 → 온보딩 BFF, `SUPER_ADMIN` → `/api/tenants` + 첫 관리자) · 빈 상태(«받은 초대» · «관리자에게 초대를 요청하세요» · 이메일 미인증 안내) · 생성 뒤 재교환 + reassume · `/onboarding` → 리다이렉트 · `/tenants` 목록 대기 표시 · 샘플/데모 종료 목록에 새 경로 | `(console)/layout.tsx` · `api/auth/callback/route.ts` · `api/auth/refresh/route.ts` · `shared/lib/session.ts` · `ConsoleSidebarNav` · `(onboarding)/**` · `api/onboarding/**` · `features/onboarding/**` · `api/tenants/_proxy.ts` · `features/tenants` | 단위(§ 7 목록 갱신) + **새 e2e**(`tests/e2e/` 또는 `tests/federation-hardening-e2e/specs/`): AC-1(셸 · 사이드바 둘 · 관리 API 401 대조군) · AC-2(생성 → 개요) · AC-5(`/onboarding` 직접 → 새 화면) · AC-7(로그아웃 → IdP 세션 끝 → 다른 계정 로그인 폼). 머지 뒤 첫 `nightly-e2e.yml` 콘솔 잡 확인(AC-6) |

**순서**: S1 → S2 → S3 → S4 → S5 → S6. 🔴 **S5 는 S3(이메일 게이트) 보다 먼저 머지하지 않는다** — 772 가 측면 없는 풀 계정에 콘솔 토큰을 주지 않은 이유가 바로 «온보딩에 인증 이메일 게이트가 없다» 였다(`onboarding-api.md:94` · 772 AC-0 F9). 백엔드 S2~S5 는 한 AMI 에, S6 은 Vercel — S6 은 백엔드가 데모에 올라간 뒤.

## 11. 위험

- **R1 401 고리** — 벨 폴링 · 셸 안 페이지의 운영자 API 직접 호출. 대책: 셸 가드 갈래 + 경로 허용 목록 + 단위 테스트로 «비운영자 상태에서 운영자 토큰 호출 0» 을 단언(샘플 방문자의 fetch 허용 목록 가드 `sample-fetch-allowlist.test.ts` 와 같은 모양).
- **R2 상태 술어의 표류** — «operator 쿠키 없음 = 비운영자» 로 추론하면 유휴 만료 · 교환 장애가 비운영자 셸로 잘못 떨어진다(지금 operator TTL 3600 > access 1800 이라 평소엔 안 생기지만, `admin-api.md:571` 설정 하나로 뒤집힌다). § 2.6 «`401` 만 운영자 아님» 그대로 명시 표식으로.
- **R3 게이트 없는 개방** — S5 가 S3 보다 먼저 배포되면 미인증 계정이 무제한(측면 없음 = 상한 0 셈)으로 테넌트를 만든다.
- **R4 서비스 간 활성화** — 수락(admin 트랜잭션) 뒤 활성화(account 호출)가 실패하면 «관리자 있는 대기 테넌트» 가 남는다 — 멱등 재시도(다음 수락 재요청 · 운영 재활성)로 닫는 쪽이 «관리자 없는 `ACTIVE`» 쪽보다 안전하다(D3 방향). S4 AC-0 이 순서를 정한다.
- **R5 상태 enum 확장의 조용한 깨짐** — 콘솔 `z.enum`(`_proxy.ts:42`) · `VARCHAR(10)` · 이벤트 소비자 · 상태 필터. 같은 PR 묶음 안에서.
- **R6 상한 경합** cap+1(784 `:635`) — 잠금 없으면 AC-3 «상한 초과 거절» 이 동시 요청에서 깨진다.
- **R7 OD-4 (A) 행의 자동 확장** — 카탈로그가 넓어질 때 생성자의 실효 권한이 함께 넓어진다(«전체» 의 뜻이지만 감사에서 보이게 출처를 남긴다).
- **R8 e2e 공백** — 지금 `/onboarding` e2e 0(§ 7). 콘솔 full-stack e2e 는 nightly 에서만 돈다(CLAUDE.md § Post-merge nightly check) — 머지 초록 ≠ 실행됨.
- **R9 AC-7 의 전제** — 비운영자 셸 토큰에 `id_token` 이 없으면 로그아웃이 로컬로만 끝나(`logout/route.ts:85-90`) IAM 세션이 남는다. S5 계약에 `id_token` 발급을 명시.
- ⚪ 재지 않은 것: `'*'` 운영자가 대기/정지 테넌트를 assume 할 수 있는가 · `'consumer-pool'` 거절의 콘솔 외 발생원 · `iam` 자격 계정 중 측면 없는 모집단 크기(이메일 게이트가 `NOT_ELIGIBLE` 로 막을 사람).

## 12. 소유자 결정 확정 (2026-10-15 UTC — 선택창, 8건 전부 추천안)

| OD | 결정 |
|---|---|
| OD-1 | «관리자 대기» = account-service `tenants.status` 의 새 값(≤10자, 예 `PENDING` — 이름 · 칸 폭은 S1 이 정한다). ADR-044 D3 «관리자 없는 활성 테넌트 0» 을 문자 그대로 지킨다 |
| OD-2 | 첫 관리자 초대 만료 → 테넌트는 대기 유지 · 배치 없음(읽기 시점 판정, 772 와 같은 모양) · `/tenants` 에 «초대 만료» · `SUPER_ADMIN` 재초대 / 정리(초대 취소 + 테넌트 SUSPENDED) |
| OD-3 | 신원당 소유 상한 = **3** · 테넌트 상태 무관 셈(784 의 미결 두 건 — SUSPENDED 포함 여부 · cap+1 경합 — 을 여기서 닫는다: 포함 · 운영자 행 잠금 + 트랜잭션 안 재셈) |
| OD-4 | 회사 생성자의 «전체 업무» = `all-domains-full` 한 행을 **시스템 전용 출처**(예 `ORIGIN` — `MIGRATION` 과 같은 종류)로, 구독 검사 없이 쓴다. 상한은 P3 의 토큰 `∩ entitled`. ADR-083 D5 예외 한 줄 · 개인 회수가 이 출처도 지운다. AC-2 시험 = 구독 전 `roles` 없음(대조) → `wms` 구독 → wms 역할 전부 |
| OD-5 | `SUPER_ADMIN` 이 초대한 **첫 관리자** 도 수락 시 같은 세트(같은 출처) — 첫 관리자 초대에만. 일반 직원 초대는 지금처럼 세트 없음 |
| OD-6 | 운영자 측면이 없거나 **활성이 아닌** 계정에도 비운영자 셸 토큰(운영자 토큰 없음 → 권한 증가 없음) — `sso_wrong_account` 모집단 0 → `TASK-PC-FE-331` 구현 없이 종결 근거(AC-7) |
| OD-7 | «받은 초대» = 새 읽기 API(로그인 subject token 인증 · 호출자의 **인증된 이메일** 로 온 초대만) |
| OD-8 | 속도 제한 = 게이트웨이 `onboarding` 범위 IP 당 5회/60초 · 신원당은 소유 상한(OD-3)으로 충족(ADR-044 D4 «신원당» 의 충족 근거로 계약에 적는다) |

## 13. AC-0 이 고친 티켓 범위 (§ 8 반영)

- **Scope 에 auth-service 추가** — 운영자 측면 없는 풀 계정에 콘솔 셸 토큰(`id_token` 포함)을 발급하는 변경이 없으면 AC-1 · 2 · 5 · 7 에 도달하지 못한다(`auth-api.md:729-739`). 🔴 순서: 이메일 게이트(S3)가 셸 토큰(S5)보다 **먼저** 머지된다(772 F9 와 같은 이유).
- **AC-2 정정** — «그 테넌트 assume `roles` = entitled 도메인의 카탈로그 전체» 는 생성 직후 구독 0 이라 공허하다. 대신: 생성 직후 assume `roles` 없음(대조) → 구독 하나 추가 → 그 도메인의 카탈로그 역할 전부 · 🔴 다른 테넌트 세트 행 0(그대로).
- **AC-3** — 상한 거절은 784 에 이미 있다. 773 은 값(3)과 경합 수선만.
- **Goal 의 `CreateOperatorUseCase.java:100-107` 인용은 낡았다** — 772 S7 뒤 비-`'*'` 생성은 `422 OPERATOR_INVITATION_REQUIRED`. 남은 결함은 «초대 수락까지 관리자 없는 ACTIVE 테넌트» 와 «수락은 ACTIVE 테넌트만» 이다(§ 2).
- **Edge Case 2(«받은 초대»)** → OD-7 의 새 읽기 API 가 In Scope.
- 슬라이스 = § 10(S1 계약 → S2 account → S3 admin 셀프 경로 → S4 admin `SUPER_ADMIN` 경로 → S5 auth → S6 console). S2~S5 는 한 AMI · S6 은 그 뒤 Vercel.

---

# S1 기록 (2026-10-15 UTC)

> 분석=Opus 5.5 (api-designer) · **코드 0 · 계약/명세만** · worktree `monorepo-lab-773` · 브랜치 `task/mono-773-s1-contracts`(origin/main `a6e83afff`). 근거 = § AC-0 기록 1 ~ 11 · § 12 OD-1 ~ OD-8(최종 — 다시 열지 않았다) · § 13. 선례 = `TASK-MONO-784` § S1 기록.
> **AC 체크박스는 건드리지 않았다** — S1 은 AC 를 닫지 않는다.

## 1. 정한 이름

| 무엇 | 이름 | 근거 |
|---|---|---|
| 테넌트 «관리자 대기» 상태 | **`PENDING`**(7자 — `VARCHAR(10)` 그대로, 칸 확장 없음) | OD-1 · AC-0 § 5 |
| 첫 관리자 초대 표지 | `operator_invitation.first_admin` BOOLEAN + 생성 칸 `first_admin_pending_key`(테넌트당 대기 하나) · API `firstAdmin` | OD-5 |
| 생성자 세트 출처 | **`ORIGIN`**(`all-domains-full` 만 · 시스템 전용 · 개인 회수 · 내보내기가 지운다) | OD-4 |
| 새 이벤트 | **`tenant.activated`**(`PENDING → ACTIVE`, `trigger` = `FIRST_ADMIN_ACCEPTED` \| `OPERATOR_COMPLETED`) · `tenant.suspended` 에 `previousStatus=PENDING` | OD-1 |
| 새 엔드포인트 | `POST /api/admin/onboarding/received-invitations`(OD-7 — 읽기, 본문 subject token) · `POST /api/admin/tenants/{tenantId}/first-admin-invitation`(OD-2 재초대 — 다른 주소 · 초대 없는 대기 테넌트) | OD-7 · OD-2 |
| 새 오류 코드 | `ONBOARDING_ACCOUNT_NOT_ELIGIBLE`(403) · `TENANT_FIRST_ADMIN_PENDING`(409) · `TENANT_NOT_PENDING`(409) · 콘솔 `NOT_AN_OPERATOR`(403) — 앞 셋 `platform/error-handling.md` + `rules/domains/saas.md`, 넷째는 registry 의 Console Composition 절만 | — |
| 게이트웨이 scope | `onboarding` IP 당 5/60s — `POST …/onboarding/organizations` 한 경로 | OD-8 |
| 콘솔 | 표식 쿠키 `console_non_operator` · 화면 `/new-tenant`(예약어 추가 없음) · 허용 목록 `/new-tenant` · `/guide` | AC-0 § 9 구현자 결정 · R2 |

## 2. 쓴 파일

`onboarding-api.md`(773 머리말 · 트러스트 게이트 · 상한 3/상태 무관/잠금 재셈 · 생성자 세트 · received-invitations) · `admin-api.md`(Exceptions 표 · 행동 테넌트 23b/23c · 초대 규칙 «첫 관리자 초대» · item `firstAdmin` · Tenant Lifecycle 전면(한 묶음 · `PENDING` 전이표 · 만료 · POST `firstAdmin` · first-admin-invitation · PATCH 정리/복구) · 구독 POST «Granted to roles» 정정 · 세트 출처 `ORIGIN` + D5 예외) · `internal/auth-to-admin.md`(console-eligibility 773 노트 · preview `firstAdmin` · accept 5 갈래 · «첫 관리자 초대일 때» 8~10) · `internal/admin-to-account.md`(Tenant lifecycle 773 변경분 — `initialStatus` · 전이표) · `events/tenant-events.md` · `auth-api.md`(콘솔 토큰 표 · 순서 🔴 · AC-1 대조군 · `sso_wrong_account` 모집단) · `gateway-api.md` · `gateway-service/redis-keys.md` · `admin-service/data-model.md`(`ORIGIN` CHECK 개정 필요 · `first_admin`) · `admin-service/rbac.md` · `features/multi-tenancy.md` · `features/consumer-integration-guide.md` · `console-integration-contract.md`(§ 2.6 · 2.6.1 · 2.6.2 · 2.6.3 정정 + **§ 2.6.4 신설**) · `console-web/architecture.md`(:279 정정 · 4a · 6) · `platform/error-handling.md` · `rules/domains/saas.md` · ADR-083 OD 표 행 덧붙임 · ADR-044 Amendment note(본문 바이트 그대로).

## 3. S1 이 정한 것 (OD 가 정하지 않은 설계 — 🔴 = 소유자가 볼 만한 것)

| # | 결정 | 왜 |
|---|---|---|
| S1-1 🔴 | `POST /api/admin/tenants` 의 `firstAdmin` **필수**(breaking — 없으면 400) · B2C 포함 | «관리자 없는 ACTIVE 0» 과 «같은 행위 = 같은 결과» 를 문자 그대로. 콘솔 폼 · 시험 · 가이드가 같은 묶음에서 바뀐다(S4 · S6) |
| S1-2 | 수락 = «관리자 행 커밋 → account 활성화 → `TENANT_ACTIVATE` 감사 + 이벤트». 활성화 실패 = `503`, 같은 링크 재제출이 재시도 · `SUPER_ADMIN` 은 초대가 `ACCEPTED` 일 때만 PATCH `ACTIVE` 로 마칠 수 있다 | R4 — 실패해도 «관리자 있는 대기» 쪽으로만 남게 |
| S1-3 | 정리 = PATCH `SUSPENDED` 하나(초대 취소 먼저 → 테넌트 정지) · 정리된 테넌트(첫 관리자 초대 행 있음 ∧ `ACCEPTED` 없음)의 PATCH `ACTIVE` = `409 TENANT_FIRST_ADMIN_PENDING` · 773 이전 테넌트는 지금 그대로 | 정리 뒤 재활성이 관리자 없는 ACTIVE 를 만드는 길을 닫는다. 첫 관리자 초대 행이 없는 플랫폼 테넌트(`TENANT_ADMIN` 0 이 원래 정상)를 막지 않게 |
| S1-4 | 일반 초대 발급은 테넌트 상태를 계속 안 본다 — `PENDING` 테넌트의 일반 초대는 **수락에서** `409 INVALIDATED` | 발급 규칙 무변경 · AC-4 대조군 |
| S1-5 | 셀프 경로는 지금처럼 `ACTIVE` 로 만들고 실패 시 SUSPEND 보상(PENDING 을 거치지 않는다) | ADR-044 D3 가 고른 모양 · 재시도 없는 엔드포인트에 실패 모드를 더하지 않는다 |
| S1-6 | 이메일 게이트 = 772 매치(풀 전용) 그대로 — 풀이 아닌 콘솔 토큰 보유자(`iam` 자격 등)는 `403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE` | 사본 금지(772). 대가는 § 4-2 |
| S1-7 | 상한 경합 패자는 테넌트 생성 **뒤** `422` + SUSPEND 보상(잠금을 원격 호출 너머로 들지 않는다) | 잠금은 admin 트랜잭션 안에서만 |
| S1-8 | received-invitations = `200` + `accountState`(`VERIFIED` · `EMAIL_NOT_VERIFIED` · `NOT_ELIGIBLE`) · 판정 실패만 `503` · 미만료 `PENDING` 만 · 50 상한 · 수락 링크 없음 · rate scope 없음 | «없음» 과 «모른다» 를 섞지 않는다 · R4 |
| S1-9 | 콘솔 refresh POST 의 교환 `401` = `403 NOT_AN_OPERATOR`(IAM 쿠키 유지 · 표식) — 지금은 `401 TOKEN_INVALID` + 세션 전체 삭제 | 비운영자 셸 BFF 호출 중 액세스 만료가 로그아웃이 되지 않게 |
| S1-10 | `tenant.activated` 새 토픽(`tenant.reactivated` 재사용 안 함) — S4 가 compose 토픽 목록 · `topicFor` 에 더한다 | 의미가 다르다(처음 개시 vs 재개) |

## 4. 열린 질문 (S1 이 답하지 않았다 — 해당 슬라이스 착수 전에)

1. 🔴 **콘솔 토큰 발급자의 `console-eligibility` 호출을 남기나 (S5)** — OD-6 뒤 그 답(`true`/`false`)은 발급을 가르지 않는다. 티켓 § 10 S5 문장대로 «`operator_eligibility_unavailable` 행 그대로» 적었지만, 그러면 쓰지 않는 답 때문에 admin-service 장애가 콘솔 로그인을 막는다(fail-closed 할 이유가 사라진 판정). 걷으면 그 거절 상수 · 콘솔 `operator_check_unavailable` 매핑이 도달 불가가 된다. 소유자 판단.
2. 🔴 **`iam` 자격 계정 회사 운영자의 두 번째 회사** — 이메일 게이트가 풀 계정만 판정하므로, 772 이전 방식(`iam` 자격)의 `ACTIVE` 회사 운영자는 S3 뒤 셀프 경로로 두 번째 회사를 못 만든다(지금은 된다 — 784 S5). 모집단 ⚪(AC-0 § 4). 받아들이나, 아니면 772 매치를 `iam` 자격까지 넓히나(772 계약 변경).
3. **게이트웨이 `/internal/tenants/{tenantId}/**` 가 `PATCH /internal/tenants/{tenantId}` 자체에 걸리는가** — 걸리면 테넌트 워크로드 토큰이 자기 상태를 바꾼다(`SUSPENDED → ACTIVE` 는 773 이전부터 같은 질문). S2 AC-0 이 `RouteConfig` 로 잰다(admin-to-account § Tenant lifecycle).
4. `'*'` 운영자가 `PENDING` 테넌트를 assume 할 수 있는가(AC-0 ⚪) — S4 AC-0.
5. 첫 관리자 수락의 `tenant.activated` 감사 + outbox 는 활성화 **뒤** 별도 트랜잭션이라 그 쓰기가 실패하면 이벤트 · 감사 한 행이 빠진다(서비스 소비자 0). 받아들이는지.
6. received-invitations 에서 «링크 없이 수락» (subject token + 인증 이메일로 수락)을 열지 — OD-7 은 읽기만 정했다. 열면 772 R4(메일함 = 증거)의 해석이 바뀐다.
7. S4 와 S6 사이 콘솔 `/tenants` 등록 폼이 `400` 을 받는 창(`firstAdmin` 필수) — S4 PR 이 콘솔 프록시 본문을 함께 넓힐지, 창을 받아들일지.

## 소유자 결정 (2026-10-15 UTC) — § 4 열린 질문의 답 (선택창 — 매번 추천안)

| Q | 결정 | 반영 자리 | 걸리는 슬라이스 |
|---|---|---|---|
| 1 | **걷는다** — auth 콘솔 토큰 발급자의 `console-eligibility` 호출과 상수 `operator_eligibility_unavailable` 을 S5 가 지운다. 운영자인지는 admin 토큰 교환(`401` → 비운영자 표식)만 정한다 → admin-service 장애가 콘솔 로그인을 막지 않는다. 호출자 0 이 되는 엔드포인트도 S5 가 grep 뒤 지운다(기본값) | `auth-api.md` § 풀 계정의 콘솔 토큰(표 셋째 행 · «매번 다시 묻는다» 폐기 · 구현 메모) · `auth-to-admin.md` § console-eligibility · 콘솔 § 2.6.3 · `error-handling.md` `OPERATOR_CHECK_UNAVAILABLE` 행 | S5 (콘솔 매핑 정리는 S6 선택) |
| 2 | **받아들인다** — S3 뒤 `iam` 자격 회사 운영자는 셀프 두 번째 회사를 잃는다(`403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE`). 거절에 안내 문구 «개인 계정으로 로그인해 만드세요» 필수. 🔴 **S3 의무**: AC-0 에서 «신원이 `iam` 자격인 `ACTIVE` 비-`'*'` 운영자 행 수» 를 라이브 · 시드 둘 다 세어 S3 기록에 적는다(술어 · 쿼리 · 시각) — **0 이 아니면 머지 전에 소유자에게 다시 묻는다** · 못 재면 그 사실도 질문으로 | `onboarding-api.md` «트러스트 게이트» · 콘솔 § 2.6.4 오류 표 | S3 · S6 |
| 3 | (구현자 몫 — 기록만) 게이트웨이 `/internal/tenants/**` 가 `PATCH /internal/tenants/{tenantId}` 에 걸리는지 **S2 가 잰다** | `admin-to-account.md` § Tenant lifecycle | S2 |
| 4 | (구현자 몫 — 기록만) `'*'` 운영자의 `PENDING` 테넌트 assume — **S4 가 잰다** | `admin-api.md` § 전이 매트릭스 읽는 곳 표 | S4 |
| 5 | (구현자 몫 — 기록만) `tenant.activated` 감사 · 이벤트 한 행 손실 가능 — **받아들인다**(서비스 소비자 0) | `auth-to-admin.md` § accept «첫 관리자 초대일 때» 10 | S4 |
| 6 | **읽기 전용** — «받은 초대» 는 빈 상태 화면에서 보여 주기만, 수락은 772 메일 링크 경로 그대로(링크 없는 수락 열지 않음) | `onboarding-api.md` § received-invitations · 콘솔 § 2.6.4 | S3 · S6 |
| 7 | **S4 PR 이 콘솔 `/tenants` 등록 폼 + 프록시 본문(`firstAdmin`) + `PENDING` enum 을 함께 넓힌다**(교차 프로젝트 원자 PR) — 머지 사이 `400` 창 없음. 배포 순서 창(AMI 먼저 · Vercel 나중)은 남으므로 S4 PR 본문에 적는다 | `admin-api.md` § POST /api/admin/tenants «호환» · 콘솔 § 2.6.4 모드 표 | S4 |

## S2 기록 (2026-10-15 UTC) — account-service `PENDING`

- **변경**: `TenantStatus.PENDING` · `Tenant.create(…, initialStatus, now)`(ACTIVE · PENDING 만) · `updateStatus` 가 `PENDING` 을 요청값으로 거절(no-op 판정보다 먼저) · `CreateTenantRequest.initialStatus`(선택, 생략 = ACTIVE · 그 밖 400). 마이그레이션 없음(`tenants.status VARCHAR(10)` · CHECK 없음 — `V0009:8`). ACTIVE 만 받는 읽기(`ActiveTenantGuard` · 개별/일괄 프로비저닝)는 무변경 — `PENDING` 은 `!isActive()` 로 409 `TENANT_SUSPENDED`. 이벤트 없음(`tenant.*` 는 admin outbox — S4).
- **빨강 측정**(#4369 첫 커밋 = 시험만, iam B 레인): `TenantPendingStatusIntegrationTest` **12 FAILED · 2 PASSED(대조군)** — 12 전부 `AssertionError`(500 × 8 = 옛 enum 이 `'PENDING'` 행을 못 읽음 · 201≠400 × 2 · `$.status` ACTIVE · 400≠200). 단위 `TenantPendingStatusTest` · `…UseCaseTest` 는 로컬 9 FAILED → 수선 뒤 10/10.
- **정정**: S1 이 `TENANT_SUSPENDED` 를 423 으로 적었다 — 코드와 다른 계약 셋은 **409**. `admin-to-account.md` · `admin-api.md` 와 그 출처인 공유 레지스트리 `platform/error-handling.md:596` 을 함께 고쳤다(유일한 발신 = account-service 409 — `GlobalExceptionHandler:263-267`).
- **S1 Q3 측정 → `TASK-BE-638` 기안(#4370)**: 게이트웨이 `/internal/tenants/**` 가 `PATCH /internal/tenants/{self}` 를 그 테넌트 자기 토큰으로 통과시키고, `POST`/`GET /internal/tenants` 는 테넌트 범위 검사가 없다(코드 읽기 — 라이브 미확인). 773 S2 의 `PENDING` 이 나가면 «관리자 대기» 를 건너뛸 수 있어 시급해진다 — 773 과 같은 굽기에 싣는 것을 권한다.
- **S2 초록 (#4369 → `19d0a0a6b`, 2026-10-15 UTC)**: 수선 커밋 뒤 iam B 레인 로그에서 `TenantPendingStatusIntegrationTest` **14 PASSED · 0 FAILED** · 레인 FAILED 0 · 머지 시점 실패 체크 0. 🔴 중간에 한 번 더 빨강: 수선 뒤 대조군(활성화 → 프로비저닝)이 실제 계정을 만들자 IT 의 `@AfterEach` 가 append-only `account_status_history`(V0004 트리거)를 지우려다 45000 으로 막혀 13건이 **정리 단계**에서 실패했다(단언은 통과) — 형제 규칙(«Deliberately NO DELETE», R__05)대로 그 테넌트는 남기되 ACTIVE 로 두게 고쳤다(`ee91f08dd`).

## S3 기록 (2026-10-14T22:30Z UTC) — admin-service 셀프 경로 · 게이트웨이 `onboarding` scope

> 분석=Opus 5.5 (backend-engineer) · worktree `monorepo-lab-773` · 브랜치 `task/mono-773-s3-admin-self`(origin/main `19d0a0a6b` — S1 계약 · S2 account `PENDING` 위). 방법 = BE-636 / S2 와 같다: **PHASE 1 = 시험만** 먼저 밀어 빨강을 재고, PHASE 2 에서 수선.

### PHASE 1 — 시험 (구현 0)

| 파일 | 무엇 | 지금 코드에서 빨강이 나는 자리(예상) |
|---|---|---|
| `admin-service …/integration/SelfServiceOnboardingTrustGateIntegrationTest.java` (신규) | 게이트 · AC-2 · AC-3 · 상한 3 · `ORIGIN` CHECK · API/회수/내보내기 · received-invitations | 아래 표 |
| `admin-service …/integration/SelfServiceOnboardingCapRaceIntegrationTest.java` (신규 — 상한을 3 으로 고정한 별도 컨텍스트) | 상한 −1 인 행으로 동시 두 생성(테넌트 생성 1.5 s 지연으로 두 사전 판정이 겹치게) | `[201, 201]` ≠ `[201, 422]` — 784 S5 기록의 cap+1 그대로 |
| `admin-service …/integration/SelfServiceOnboardingIntegrationTest.java` | 소유자 계정의 `verified-email:match` 200 스텁만 더함 | 초록 유지(게이트가 들어와도 초록이게 하는 준비) |
| `admin-service …/integration/OperatorInvitationAcceptanceIntegrationTest.java` | ac6: 창업자 match 스텁 · 상한 2 → 3 기대(피초대자 세 번째 201 · 네 번째 422 / 창업자 org3 201 · org4 422) | 6단계 `onboard(invitee, own)` 기대 201 ← 지금 422(상한 2) |
| `gateway-service …/route/RouteConfigTest.java` | `resolveRateLimitScope("/api/admin/onboarding/organizations") = "onboarding"` · received-invitations · 다른 admin 경로 = `null` | 로컬 실측 빨강 — `RouteConfigTest.java:323` `AssertionFailedError`(`null` ≠ `"onboarding"`) |
| `gateway-service …/ratelimit/TokenBucketRateLimiterUnitTest.java` | 기본값 `onboarding` = 5/60s: 5번째 허용 · 6번째 거부(`Retry-After` 60) · 키 `rate:onboarding:{ip}` · TTL 70 | 로컬 실측 빨강 — `:106` `AssertionFailedError`(scope 미지 → 늘 허용) |

`SelfServiceOnboardingTrustGateIntegrationTest` 의 빨강 예상(Docker 없음 — CI 가 잰다):

| 시험 | 빨강 자리 |
|---|---|
| `unverifiedEmail_403_nothingCreated_thenVerified_201` | 첫 `status 403` ← 지금 201(게이트 없음) |
| ~~`notAPoolAccount_403_notEligible_withCopy_rowsUnchanged`~~ | 아래 «소유자 결정 — option A» 로 넷으로 갈렸다 (그 시험의 `iam` 계정 detail 200 스텁은 불가능한 입력이었다 — `iam` 자격에는 account-service 계정 행이 없다) |
| `iamCredentialOperator_exemptFromEmail_secondCompany_201` | `status 201` ← 지금 **503**(컨트롤러가 측면 전에 계정 읽기 → 404 → `NonRetryableDownstreamException` → 503) |
| `iamCredentialOperator_stillBoundByTheCap` | `status 422` ← 지금 503(같은 이유) |
| `noAccountRecordAndNoFacet_403_notEligible` | `status 403` ← 지금 503 |
| `siteAccount_403_notEligible_withCopy` | `status 403` ← 지금 201(사이트 계정 · 측면 없음 → 1행 새 운영자) |
| `noVerdict_503_nothingCreated` | `status 503` ← 지금 201 |
| `ac2_newCreator_originRowInTheNewTenantOnly` | `originRows hasSize(1)` ← 0 |
| `ac3_secondCompany_newTenantOnly_existingCompanyByteUnchanged` | 새 테넌트 세트 행 `containsExactly("ORIGIN/all-domains-full")` ← `[]` |
| `cap3_countsSuspendedTenant_thirdOk_fourthRefused` | 세 번째 `status 201` ← 422(기본 상한 2) |
| `check_admitsOriginOnACompanyTenant_refusesItOnStar` | `doesNotThrowAnyException` ← V0052 CHECK 위반(`DataIntegrityViolationException`) |
| `origin_notGrantableThroughTheApi` · `personalRevoke_deletesOrigin` · `companyExit_deletesOrigin` | 전제 `originRows hasSize(1)` ← 0 (🔵 이 셋은 지금 `ORIGIN` 행을 만들 길이 없어 — 생성은 안 쓰고, JDBC 직접 INSERT 는 CHECK 가 막는다 — 회수 · 내보내기 자체의 결함으로 빨강을 낼 수 없다. 수선 뒤 초록이 그 자리의 증거다. `origin_notGrantable…` 의 본체는 지금 코드에서도 성립하는 대조군) |
| `receivedInvitations_onlyLivePendingToTheVerifiedEmail` · `receivedInvitations_statesAndRefusals` | 첫 `status 200` ← 401(엔드포인트 없음 — `OperatorAuthenticationFilter` 가 운영자 토큰을 요구). 후자에 `iam` 자격 호출자(계정 404) → `NOT_ELIGIBLE` + `[]` · 매치 호출 0 도 실었다 |

로컬 `compileTestJava`(admin · gateway) rc=0.

### 소유자 의무 (S1 Q2) — «신원이 `iam` 자격인 `ACTIVE` 비-`'*'` 운영자 행 수»

- **술어**: `admin_operators.status = 'ACTIVE'` ∧ `tenant_id <> '*'` ∧ `'*'` 역할 행 없음(= `OperatorMembershipAddition.isPlatformRow` 의 반대) ∧ `oidc_subject` = auth `credentials.account_id` 중 `tenant_id = 'iam'` 인 행.
- **시드 (이 트리 · 2026-10-14T22:30Z 읽음)**: **4** — `demo-operator`(홈 `demo-corp`, `…ad03`, `R__seed_demo_operator.sql:61-76`) · `demo-requester`(`demo-corp`, `…ad04`, `:220-237`) · `demo-cs`(`ecommerce`, `…ad08`, `R__seed_demo_cs_operator.sql:82-98`) · `demo-viewer`(`demo-viewer`, `…ad05`, `R__seed_demo_viewer_operator.sql:78-91`). 각 `oidc_subject` 는 auth-service `migration-dev` 의 `'iam'` 자격 행과 같은 값(`R__01_seed_demo_single_identity_credentials.sql:112` · `R__seed_demo_second_operator_credential.sql:103` · `R__seed_demo_cs_operator_credential.sql:39` · `R__seed_demo_viewer_operator_credential.sql:38`). 제외: `demo-platform`(홈 `'*'`) · `demo-assigned-only`(`oidc_subject` NULL) · dev `V0014`/`V0023` 두 행(`SUPER_ADMIN` → V0025 가 `'*'`, `oidc_subject` 는 실재 자격과 안 맞는 자리표시자 — `V0028`). 🔵 `demo-operator` · `demo-requester` 는 `SUPER_ADMIN` 역할 행을 갖지만 그 행의 `tenant_id` 가 `demo-corp`(`'*'` 아님)라 술어상 회사 행이다.
- **영향 (S1 결정 기준 — 아래 option A 로 대체됨)**: ~~S3 뒤 이 넷은 셀프 «테넌트 생성» 에서 `403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE`~~. 데모 화면 · e2e 가 이 넷으로 온보딩을 부르는 곳은 AC-0 § 7 grep 에서 0.
- **라이브(데모) — ⚪ 못 쟀다**: 데모 창이 없고(로컬 Docker 없음) admin_db · auth_db 에 닿을 수 없다. 재는 쿼리(같은 MySQL 서버, 두 스키마 — 이름은 배포 설정을 확인):
  ```sql
  SELECT o.operator_id, o.tenant_id, o.oidc_subject
    FROM admin_db.admin_operators o
    JOIN auth_db.credentials c ON c.account_id = o.oidc_subject AND c.tenant_id = 'iam'
   WHERE o.status = 'ACTIVE' AND o.tenant_id <> '*'
     AND NOT EXISTS (SELECT 1 FROM admin_db.admin_operator_roles r WHERE r.operator_id = o.id AND r.tenant_id = '*');
  ```
  (스키마가 다른 서버면 admin 쪽 `oidc_subject` 목록을 뽑아 auth 쪽 `credentials` 에 `IN (…)` 으로 대조.)
- 🔴 **결론 = 0 이 아니다(시드 4) · 라이브는 못 쟀다** → 소유자 결정 S1 Q2 의 조건대로 소유자에게 다시 물었다(아래 option A).

### 🔴 S3 측정 — 773 이전에도 `iam` 자격 운영자는 셀프 생성이 **안 됐다** (503)

S1 Q2 의 전제(«지금은 된다 — 784 S5») 는 시드 기준으로 거짓이다: `OnboardingController.java:61` 이 측면 판정 **전에** `AccountServiceClient.getDetail(sub)`(account-service `GET /internal/accounts/{id}` — `AccountSearchController.java:44-49`)를 부르고, `iam` 자격(ad03 · ad04 · ad05 · ad08)에는 account-service 계정 행이 없다(그 id 는 auth `credentials` · admin `admin_operators` 에만 있다 · account-service main 코드에 `iam` 테넌트 언급 0) → 404 → `AccountServiceClient.execute` 가 `NonRetryableDownstreamException`(`DownstreamFailureException` 하위) → `AdminExceptionHandler.handleDownstream:239` = **`503 DOWNSTREAM_ERROR`**. ⚪ 라이브 미측정. 그래서 아래 면제는 기능을 «지키는» 것이 아니라 «여는» 것이다. 콘솔 토큰 클레임으로는 두 종류를 못 가른다(풀 주체의 콘솔 토큰도 `tenant_id=iam` — `TenantClaimTokenCustomizer.java:638-641`).

### 소유자 결정 (2026-10-15 UTC) — option A: `iam` 자격 운영자는 이메일 면제 (S1 Q2 «수용 + 안내» 와 그 사이의 «면제» 문구를 대체)

- **계기**: 위 S1 Q2 의무 측정의 시드 **4**(demo@demo.com 포함).
- **판별(대리)**: account-service 계정 읽기 `404` ∧ `oidc_subject = sub` 운영자 측면 있음 ⟹ `iam` 자격 운영자. 근거: `iam` 자격은 관리자가 만든 자격(셀프 가입 없음) — ADR-044 D4 의 이메일 게이트는 익명 셀프 가입을 겨눈다. 772 매치는 넓히지 않는다.
- **그 호출자**: 이메일 판정 면제 · 측면 표 2~5행만(`'*'`/비-`ACTIVE` → 409 · 상한 → 422 · 상한 미만 → 소속 추가 + `ORIGIN`) · **상한 3 · 게이트웨이 rate limit 은 그대로**. 컨트롤러는 측면 조회 전에 계정 행을 요구하지 않는다(404 는 판정 입력).
- **계정 404 ∧ 측면 없음** → `403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE`(fail-closed).
- **received-invitations**: 계정 404 호출자(`iam` 자격 포함) → `NOT_ELIGIBLE` + `[]`(인증된 이메일이 없다 — 면제는 옮겨지지 않는다).
- 🔴 **알려진 위험(대리 판별)**: ① 넓은 쪽 — 계정 행이 지워졌는데 측면이 남은 개인 계정도 면제된다. 상한 3 이 그 행을 묶고(측면 없는 404 는 거절이라 새 행은 못 만든다) rate limit 도 그대로. ② 좁은 쪽 — account-service 계정 행이 **있는** `iam` 자격은 개인 계정 후보로 가서 매치 404 → `NOT_ELIGIBLE`(안전 쪽). 정의형 판별(auth 의 자격 종류 신호)은 별도 결정.
- **남는 `NOT_ELIGIBLE` 모집단**: 계정 행이 있지만 `ACTIVE` 개인 계정이 아닌 호출자(사이트 · B2B · 비-`ACTIVE` 개인) + 계정 행도 측면도 없는 `sub`. 콘솔 문구는 중립 머리 + 사이트 · B2B 대상 «개인 계정으로 로그인해 만드세요» 유지.
- **반영한 문서(Phase 1, 편집만)**: `onboarding-api.md`(Errors 표 · 판정 표 0행 · «트러스트 게이트» 의 «두 자격» 표 · 대리 위험 · «773 이전 동작» · received-invitations `accountState`) · `console-integration-contract.md` § 2.6.4 오류 표 · `platform/error-handling.md:705` · `rules/domains/saas.md:100`(프로젝트 무관 문구 — «consumer-pool» 제거).
- 시험: 위 빨강 표 갱신(`iam` 셋 · 사이트 계정 · received-invitations `iam` 호출자). 경쟁 · 상한(기본값) 시험은 인증된 개인(풀) 계정 호출자 그대로, `iam` 자격의 상한은 `iamCredentialOperator_stillBoundByTheCap` 가 맡는다. 로컬 `compileTestJava`(admin) rc=0 (재실행).
- **S3 빨강 측정 (#4371 첫 커밋 = 시험 + 계약 정정, iam A 레인 job 116298303145)**: `SelfServiceOnboardingTrustGateIntegrationTest` **15 FAILED** · `SelfServiceOnboardingCapRaceIntegrationTest` **1 FAILED**(«exactly one wins the race») · `OperatorInvitationAcceptanceIntegrationTest` ac6 **1 FAILED**(201≠422, 옛 상한 2) — 17 전부 결함 단언(`AssertionError`: 503 × 3 = iam 운영자 계정 읽기 404 → 503 · 201≠403 × 2 · 401 × 2 = 엔드포인트 없음 · ORIGIN 행 0 · V0052 CHECK). SQL/컴파일 오류 0. 대조: `SelfServiceOnboardingIntegrationTest` 2 PASSED · 같은 클래스의 나머지 acceptance 5 PASSED. gateway 단위 2건은 로컬 실측 빨강.
- **S3 초록 (#4371, job 116313854354)**: `SelfServiceOnboardingTrustGateIntegrationTest` **15 PASSED** · `SelfServiceOnboardingCapRaceIntegrationTest` **1 PASSED**(운영자 행 FOR UPDATE 를 첫 문장으로 → 둘 중 하나만 성공, CI 실측) · `OperatorInvitationAcceptanceIntegrationTest` 6 · `SelfServiceOnboardingIntegrationTest` 2 PASSED · 레인 FAILED 0. 🔴 중간 빨강 1건: V0054 CHECK 는 실제로 거절했는데(3819) 시험이 `DataIntegrityViolationException` 을 기대 — MySQL CHECK 위반은 Spring 이 `UncategorizedSQLException` 으로 분류한다(형제 `OrgNodeAdminIntegrationTest:202-211` 이 이미 적어 둔 함정, CLAUDE.md step 10). 제약 이름 단언으로 좁혔다(`f15e0009f`).

## S4 기록 (2026-10-15T08:46Z UTC) — admin-service `SUPER_ADMIN` 경로 · 콘솔 `/tenants` 폼 (교차 프로젝트 원자 PR)

> 분석=Opus 5.5 (backend-engineer) · worktree `monorepo-lab-773` · 브랜치 `task/mono-773-s4-admin-superadmin`(origin/main `bf2d0236f` — S1 · S2 · S3 위). 방법 = S2 / S3 와 같다: **PHASE 1 = 시험만** 먼저 밀어 빨강을 재고, PHASE 2 에서 수선. 소유자 결정 S1 Q7 = 이 PR 이 콘솔 폼 · 프록시 · `PENDING` enum 을 함께 넓힌다.

### PHASE 1 — 시험 (구현 0)

| 파일 | 무엇 | 지금 코드에서의 빨강(예상 · 🔵 = 로컬 실측) |
|---|---|---|
| `admin-service …/integration/TenantFirstAdminIntegrationTest.java` (신규) | POST 한 묶음 · B2C · `firstAdmin` 필수/검증 · 보상(트리거로 초대 INSERT 거절 주입) · 마이그레이션(`first_admin` · `first_admin_pending_key`) · 첫 관리자 수락(새 계정 · 상한 3 인 기존 운영자 = 소속 추가) · 활성화 실패 → 재제출 재시도 · 복구 PATCH · 정리(순서 · 실패 재시도 · 재활성 409) · 요약 4 상태 + `null` · 재초대 엔드포인트 · Q4 측정 · 대조군 3 | 아래 표 |
| `admin-service …/integration/TenantAdminIntegrationTest.java` | POST 본문에 `firstAdmin` · account 생성 스텁이 `initialStatus=PENDING` 일 때만 `PENDING` 을 답한다(S2 실제 동작) · 기대 `PENDING` | `superAdmin_post_creates_tenant_and_emits_outbox` `$.status` ACTIVE ≠ PENDING |
| `admin-service …/integration/RbacTenantEndpointMatrixIntegrationTest.java` · `TenantOrgNodePlacementIntegrationTest.java` | POST 본문에 `firstAdmin` 만 더함(admin-api «breaking 의 범위» 의 호출자) | 초록 유지(옛 DTO 는 모르는 칸을 무시) |
| `admin-service …/presentation/tenant/TenantAdminControllerSliceTest.java` | `firstAdmin` 없는 본문(B2B · B2C · `null` · `orgNodeId` 경로) → 400 · 유스케이스 0 / 다른 본문에 `firstAdmin` | 🔵 로컬 실측 `Status expected:<400> but was:<500>`(`:194` — 옛 컨트롤러가 유스케이스까지 감) |
| `admin-service …/infrastructure/outbox/AdminOutboxPublisherTest.java` | `topicFor("tenant.activated")` | 🔵 로컬 실측 `AssertionError :94`(IAE «Unknown admin event type») |
| `console-web tests/unit/tenants-proxy.test.ts` | POST 본문 `firstAdmin` 전달 · 없으면 422 · 형식 · `?status=PENDING` 전달 · 대조: PATCH `status=PENDING` 422 | 🔵 아래 콘솔 표 |
| `console-web tests/unit/features/tenants/tenants-api-create-org-node.test.ts` | 생산자 본문 = 4 칸(`firstAdmin` 포함) | 🔵 |
| `console-web tests/unit/features/tenants/tenants-first-admin.test.tsx` (신규) | 스키마가 `firstAdmin` 요약을 지킴 · 목록 «관리자 대기» + `· 초대 만료` / `· 초대 없음` / `· 활성화 재시도` · 필터 `PENDING` · 상세 상태 · `PENDING` 테넌트 이름 변경 PATCH 에 `status` 없음 · 🔵 옛 응답 파싱 · 렌더(대조) | 🔵 |
| `console-web tests/unit/features/tenants/TenantsScreen.test.tsx` | 등록 폼 `tenant-form-first-admin-email` · `…-display-name` — 둘 다 유효해야 제출 · 본문 `firstAdmin`(trim) · `PENDING` 응답 수락 | 🔵 |

`TenantFirstAdminIntegrationTest` 빨강 예상(Docker 없음 — CI 가 잰다):

| 시험 | 빨강 자리 |
|---|---|
| `post_superAdmin_pendingTenantAndFirstAdminInvitation` | `status 201` 은 **통과**(= 아래 배포 순서 측정) → `$.status` ACTIVE ≠ PENDING |
| `post_b2c_sameFirstAdminRule` | `$.status` ACTIVE |
| `post_firstAdminRequiredAndValidated_400_nothingCreated` | 첫 `status 400` ← 201 |
| `post_invitationWriteFails_compensatedBySuspend_neverActive` | `5xx` ← 201(초대를 쓰지 않으니 주입이 발화하지 않는다) |
| `migration_firstAdminColumns_onePendingPerTenant` | `first_admin` 칸 존재 단언(information_schema) ← null |
| `accept_*` 4 · `cleanup_cancelsInvitationThenSuspends_andRefusesReactivation` · `cleanup_accountFails_…` · `recovery_…` · `summary_states_onList` · `reinvite_flow` | 공통 도우미 `createPending` 의 `$.status` ACTIVE ≠ PENDING. 🔵 이 아홉은 지금 코드에서 «첫 관리자 초대 행» 을 만들 길이 없어(칸이 없다) 수락 · 정리 · 요약 자체의 결함으로 빨강을 낼 수 없다 — 수선 뒤 초록이 그 자리의 증거다(S3 의 `ORIGIN` 셋과 같은 사정) |
| `ordinaryIssuance_cannotMintAFirstAdminInvitation` | `$.firstAdmin` 없음(item 가산 칸) |
| `patch_pendingToActive_withoutAcceptedFirstAdmin_409` | `status 409` ← 200(지금은 `TENANT_REACTIVATE` 로 활성화한다 — 관리자 없는 ACTIVE 를 만드는 길) |
| `cleanup_pendingWithoutInvitation_eventSaysPreviousPending` | outbox `previousStatus` ACTIVE(하드코딩, `OutboxTenantEventPublisher.java:80`) |
| `summary_tenantWithoutFirstAdminRows_isNull` | `No value at JSON path "$.items[0].firstAdmin"` |
| `reinvite_notPending_409` | `status 409` ← 엔드포인트 없음 |
| 대조군 `control_ordinaryInvitationIntoPendingTenant_refusedAtAcceptance` · `control_patchToPending_400` · `control_pre773SuspendedTenant_reactivates` | 초록(지금 코드 그대로가 계약) |
| 측정 `measure_starOperatorAssumesPendingTenant` | 초록(아래 Q4) |

🔴 **불변식(ADR-044 D3) 측정 방법**: WireMock 변환기 `ActivationProbe` 가 account-service 에 `PATCH /internal/tenants/{id} status=ACTIVE` 가 들어오는 **그 순간** 별도 커넥션으로(커밋된 행만 보인다) 그 테넌트의 `ACTIVE` 운영자 중 `TENANT_ADMIN` 역할 행 보유자 수를 센다. `assertEveryActivationSawAnAdmin` = 모든 활성화 요청에서 ≥ 1. 활성화(auth-to-admin § accept 9)를 관리자 커밋(8) 앞으로 옮기는 bite 는 이 단언을 빨갛게 한다. 생성 쪽은 «account 에 `initialStatus=PENDING` 으로만 생성 요청» + «생성 · 정리 · 재초대 경로에서 활성화 요청 0» 으로 잰다.

**콘솔 로컬 실측** (`npx vitest run tests/unit/tenants-proxy.test.ts tests/unit/features/tenants/` · rc=1): **31 FAILED · 26 PASSED** · 31 전부 `AssertionError`(422 ≠ 201 · 프록시가 `firstAdmin` 을 strict 로 거절 / 필드 없음 «expected null not to be null» / 상태 칸 `PENDING` 그대로 / PATCH 본문에 `status: 'PENDING'` / 필터에 `PENDING` 없음). `npx tsc --noEmit -p .` rc=0(시험 파일 포함 — 입력은 `Parameters<typeof createTenant>[0]` 캐스트로 두 단계 모두 컴파일). 🔵 `firstAdmin` 형식 422 다섯 케이스는 지금 **틀린 이유로 초록**이다(바깥 객체가 `.strict()` 라 `firstAdmin` 칸 자체가 거절) — 수선 뒤에도 초록이어야 하는 회귀 가드.

admin `compileTestJava` rc=0 · 로컬 `test --tests *TenantAdminControllerSliceTest *AdminOutboxPublisherTest *TenantFirstAdminIntegrationTest` = 25 completed · 2 FAILED(위 둘 — IT 는 Docker 없음으로 건너뜀).

### S1 측정 의무 둘

- **Q4 — `'*'` 운영자가 `PENDING` 테넌트를 assume 할 수 있나 = 예(코드 읽기 · IT 는 admin 절반을 고정)**. admin `OperatorAssignmentCheckUseCase.java:199-203` — `'*'` 행은 테넌트를 읽지 않고 `assigned=true`(그 앞의 거절은 consumer-pool 테넌트 · 운영자 행 없음 · 운영자 비-`ACTIVE` · `confined_tenant_id` 밖 뿐 — `:162-197`). auth `AssumeTenantAuthenticationProvider` 와 `infrastructure/oauth2/**` 에 테넌트 상태 술어 0(grep `SUSPENDED` · `"ACTIVE".equals` — 토큰 · 리프레시의 `isActive()` 만). 그래서 `PENDING`(그리고 773 이전부터 `SUSPENDED`) 테넌트도 assume 된다 — 토큰의 `entitled_domains` 는 그 테넌트의 `ACTIVE` 구독(대기 테넌트는 보통 0). 콘솔 스위처엔 `ACTIVE` 만 뜨므로 UI 길은 없다. 데모의 `'*'` 운영자는 `fan-platform` 확정(TASK-MONO-751)이라 데모에선 닿지 않는다. 🔵 IT `measure_starOperatorAssumesPendingTenant` 는 «측정 핀» 이다(요구가 아니다) — 막기로 하면 같이 뒤집는다. **소유자 결정 필요 여부: 열린 질문으로 올린다**(막으려면 assignment-check 가 테넌트 상태를 읽어야 한다 — admin → account 호출이 assume 경로에 생긴다).
- **배포 순서 창(admin-api § POST /tenants «호환»)**. ① 옛 요청 DTO: admin-service 에 Jackson `FAIL_ON_UNKNOWN_PROPERTIES` 재정의 · `ObjectMapper` 빈 · `Jackson2ObjectMapperBuilderCustomizer` 모두 0(grep — `new ObjectMapper()` 는 다운스트림 응답 파싱용 클라이언트 필드뿐) → Spring Boot 기본(모르는 칸 무시) → **새 콘솔 폼 + 옛 백엔드 = `201` · `ACTIVE` · 초대 없음**(400 아님). CI 빨강 런의 `post_superAdmin_…` 가 `status 201` 통과 뒤 `$.status` ACTIVE 로 떨어지는 줄이 그 실측이다. ② 콘솔이 옛 응답을 그리나: `TenantSchema` 는 non-strict `z.object` · `status: z.string()` — 옛 응답 파싱 OK · 옛 행 렌더 OK(🔵 로컬 실측 — `tenants-first-admin.test.tsx` 의 대조 둘이 지금 초록). PHASE 2 는 `firstAdmin` 을 **optional · nullable** 로 더해야 이 대조가 초록으로 남는다. 창의 길이 = Vercel 머지 → 다음 재굽기(AMI) — PR 본문에 적는다.

### 콘솔 e2e grep (`/tenants` 생성 흐름)

`projects/platform-console/apps/console-web/tests/e2e/` · `…/e2e-smoke/` · 리포 루트 `tests/federation-hardening-e2e/` 에서 `/tenants` · `tenant-create` · `tenant-form` · `테넌트 등록` · (루트 e2e) `api/admin/tenants` — **0 건**. 고칠 e2e 없음 · 지금 `/tenants` 생성 흐름의 e2e 커버리지도 0.

### PHASE 2 — 수선 (2026-10-15T09:43Z UTC · 커밋 전 · PHASE 1 단언 무변경)

- **admin-service**: `V0055__operator_invitation_first_admin.sql`(`first_admin` · 생성 칸 `first_admin_pending_key` · `uk_operator_invitation_first_admin_pending_key`) · `CreateTenantUseCase`(account `initialStatus=PENDING` → 한 트랜잭션 `Bundle` = 첫 관리자 초대 행 + 그 감사 + `tenant.created(PENDING)` → 실패 시 `PENDING→SUSPENDED` 보상 → 커밋 뒤 메일; 전체 `@Transactional` 제거) · `TenantActivation`(9 account `PATCH ACTIVE` → 10 `TENANT_ACTIVATE` 감사 + `tenant.activated`, 10 실패는 로그 — S1 Q5) · 수락: 첫 관리자 초대 = `PENDING` 테넌트만 · writer 트랜잭션에 `ORIGIN` 행(S3 경로를 `OriginSetWriter` 로 뽑아 공유) · 커밋 **뒤** 활성화 · 같은 계정 재제출이 `PENDING` 이면 재시도 · 상한 안 봄 · `UpdateTenantUseCase`(PENDING→ACTIVE 409 `TENANT_FIRST_ADMIN_PENDING` / `ACCEPTED` 면 복구 = `OPERATOR_COMPLETED` · 정리 = 초대 취소 커밋 → 정지 · `tenant.suspended(previousStatus=실제)` · 정리된 테넌트 재활성 409; 전체 `@Transactional` 제거 — 취소가 정지 실패에 롤백되지 않게) · `POST /api/admin/tenants/{id}/first-admin-invitation`(409 `TENANT_NOT_PENDING` · `OPERATOR_INVITATION_ALREADY_PENDING`) · `firstAdmin` 요약(`TenantFirstAdminSummaryQuery` — 페이지당 한 번 읽기; 응답은 `@JsonInclude(ALWAYS)` — test 프로필 `non_null` 이 `null` 키를 지우지 않게) · invitation item · preview 에 `firstAdmin` · `topicFor("tenant.activated")` + compose 두 파일 토픽 · `ActionCode.TENANT_ACTIVATE`.
- **console-web**: 프록시 `firstAdmin` 필수(trim 뒤 IAM 규칙) · PATCH enum = `TENANT_PATCH_STATUSES`(PENDING 불가) · 목록 `?status=PENDING` · `TenantSchema.firstAdmin` optional+nullable(옛 응답 호환) · 등록 폼 첫 관리자 두 칸(둘 다 유효해야 제출) · 상태 «관리자 대기 · 초대 만료/초대 없음/활성화 재시도»(`tenants-row-status-{id}`) · 대기 테넌트 수정은 `status` 를 보내지 않음.
- **Q4** — 측정 사실 그대로(막지 않음, 소유자에게 질문 중).
- **로컬 검증**: admin `compileTestJava` rc=0 · `:admin-service:test` rc=0(ITs 는 Docker 없음 → CI) · console 해당 7 파일 77 PASSED · 전체 `npx vitest run` 386 파일 4392 PASSED · `tsc --noEmit` rc=0 · `next lint` rc=0 · 스테이지 뒤 `check-error-code-registry` · `check-flyway-version-collision`(320 / 27 디렉터리) · `check-flyway-unresolvable-placeholder`(400) · `check-dev-seed-migration-band` 모두 rc=0.
- **S4 빨강 측정 (#4372 첫 커밋 = 시험만)**: iam A 레인(job 116334237061) — `TenantFirstAdminIntegrationTest` **19 FAILED · 4 PASSED**(대조군 3 + Q4 측정) · `TenantAdminIntegrationTest` 1 FAILED(PENDING 기대) · 20 전부 `AssertionError`(`$.status` ACTIVE≠PENDING × 12 · `$.firstAdmin` 없음 × 2 · 201≠400 · 201≠5xx · 200≠409 · 404≠409 · V0055 칸 없음 · 불변식 프로브). SQL/컴파일 오류 0. 콘솔 vitest(job 116334235424): 손댄 4파일에서만 **31 FAILED** · 나머지 4361 PASSED.
- **S4 소유자 결정 (2026-10-15 UTC)** — Q4 `'*'` 운영자의 PENDING(·SUSPENDED) 테넌트 assume: 773 은 측정 사실로만 기록(S4 IT 가 `assigned=true` 를 측정으로 고정) · **`TASK-BE-639`** 로 따로 다룬다(같은 PR 에서 기안).
- **S4 초록 (#4372, job 116390247407)**: `TenantFirstAdminIntegrationTest` **23 PASSED**(불변식 프로브 — 모든 활성화 요청 시점에 커밋된 ACTIVE TENANT_ADMIN ≥ 1) · `TenantAdminIntegrationTest` 13 · `RbacTenantEndpointMatrix` 7 · `TenantOrgNodePlacement` 5 · `OperatorInvitationAcceptance` 6 · `SelfServiceOnboarding*` 18 PASSED · 레인 FAILED 0 · 콘솔 vitest 386 파일 PASSED. 🔴 중간 빨강 5건은 둘 다 시험 결함: ① 프로브가 WireMock 3.9.1 `RequestMethod` 를 `==` 로 비교(요청마다 새 인스턴스 → 늘 거짓, 같은 시험의 «활성화 호출 1회» 는 통과) ② MySQL `BOOLEAN` 을 `Number` 로 캐스팅. 운영 코드 무변경(`787200555`).

## S5 기록 (2026-10-15T11:53Z UTC) — auth-service 콘솔 «비운영자 셸» 토큰 · console-eligibility 폐기

> 분석=Opus 5.5 (backend-engineer) · worktree `monorepo-lab-773` · 브랜치 `task/mono-773-s5-auth-shell-token`(origin/main `c98e80ab5` — S1 ~ S4 위) · PR #4373(draft). 방법 = S2 ~ S4 와 같다: **PHASE 1 = 시험만** 먼저 밀어 빨강을 재고(`87e684c42`), PHASE 2 에서 수선. 🔴 머지 순서 조건(auth-api.md «S5 는 S3 보다 먼저 main 에 들어가지 않는다»): S3 머지 커밋 `bf2d0236f`(#4371)는 이미 main 에 있다.

### S5 AC-0 — 정확한 자리 (코드 읽기)

- 판정 지점은 **하나**: `TenantClaimTokenCustomizer.customizeForPoolPrincipalOnConsole` — `customizeForAuthorizationCode` 에서 «풀 principal ∧ client 테넌트 = `iam`» 일 때만 탄다. `authorization_code`(access · id 토큰 둘 다) · `refresh_token` 모두 이 메서드를 지난다(`customize` 가 refresh 를 `customizeForAuthorizationCode` 로 보낸다). 측면 없음 = 클레임을 `consumer-pool` 로 두어 `refuseConsumerPoolTenant` 가 BE-614 문구로 거절 · 판정 실패 = `operator_eligibility_unavailable`.
- **refresh 미러 행** · **authorize 게이트**는 I/O 없는 `AuthorizationSessionTenant` 가 이미 «풀 principal × 콘솔 → `iam`» 을 답한다(772 S4) — 바꿀 것 없음. 측면 없는 풀 계정의 미러 행도 이제 `iam` 이다(IT 가 잰다).
- `'consumer-pool'` 문구의 **다른 발생원**(AC-0 § 3 의 ⚪): `refuseConsumerPoolTenant` 호출처 = **1**(`customize` 끝 — 모든 grant 공통). S5 뒤 그 문구가 나가는 길은 클레임이 `consumer-pool` 로 남는 셋뿐 — ① 풀 principal × 테넌트 없는 / `consumer-pool` 자체인 client(또는 폴백 메타데이터가 풀) ② `client_credentials` client 테넌트 = 풀 ③ assume 선택 테넌트 = 풀(공급자가 먼저 거절). **콘솔 client 는 어느 것도 아니다** → 콘솔 경로 `sso_wrong_account` 모집단 = **0**(773 AC-7 · `TASK-PC-FE-331` 무구현 종결 근거). 대조 시험 `poolPrincipal_onTenantlessClient_stillRefusedWithPoolText`(새) · `TenantClaimConsumerPoolRefusalTest`(그대로). auth-api.md 해당 줄에 적었다.
- **console-eligibility 호출자 grep**(`console-eligibility` · `ConsoleEligib` · `isConsoleEligible`, 리포 전체 `.java`/`.ts`/`.yml`): 운영 코드 호출자 = auth `AdminConsoleEligibilityClient` **하나**(gateway · console-web · 다른 서비스 0 — 콘솔은 그 IAM 상수의 **문구**만 매핑하고 엔드포인트를 부르지 않는다). ⇒ 소유자 기본값대로 admin 엔드포인트도 지웠다. resilience 설정(`adminConsoleEligibility`)은 yml 에 행이 없었다(코드 기본값) — 지울 설정 0.

### PHASE 1 — 시험 (`87e684c42`, 운영 코드 0)

| 파일 | 무엇 |
|---|---|
| auth `TenantClaimPoolPrincipalTest` | 콘솔 셀 넷을 새 표로: 측면 묻지 않고 셸 토큰(access) · id_token · refresh 2회 · 대조(스토어 토큰 그대로 / 테넌트 없는 client 는 BE-614 문구 그대로). 772 의 적격 true/false/판정 실패 셀(4)은 폐기 행이라 지웠다. 발급자에 **`NOT_ASKED` 포트**(물으면 `AssertionError` — `Error` 라 발급자의 `RuntimeException` catch 가 못 삼킨다)를 넣어 «묻지 않음» 을 단언으로 만들었다 |
| auth `ConsumerPoolSsoIntegrationTest` | admin WireMock 이 **모든 계정에 503**(이 질문엔 누구에게도 답 못 함) · 측면 없는 풀 계정 → 콘솔 `200` · access + `id_token` 둘 다 `tenant_id=iam` · `sub`=풀 계정 · `roles`/`entitled_domains`/`token_type` 없음 · 미러 행 `iam` · refresh `200` · admin 호출 0 / 폼 로그인 교차 조회도 `200` / 측면 있는 운영자: 772 AC-3 «회수 뒤 refresh 400» → **200(셸)** · 스토어 · 팬 대조 그대로 / 772 F5 → `adminServiceDown_consoleLoginStillIssues`(본문에 `operator_eligibility_unavailable` 없음 · `200`) |
| auth `ConsumerPoolLegacyMoveIntegrationTest` | 772 AC-5 끝: console-eligibility 스텁 제거 · `verify(≥1)` → `verify(0)` |
| admin `OperatorAssignmentCheckIntegrationTest` | `GET /internal/operators/console-eligibility` → **404**(새) · 술어 셀 넷은 엔드포인트 대신 공용 빈 둘(`OperatorOidcSubjectResolver` + `OperatorConsoleAdmission` — 엔드포인트가 조합하던 그 둘, 교환이 계속 쓴다)을 직접 묻는다 · 공백 400 셀은 엔드포인트 동작이라 지웠다 |
| admin `PartnershipCrossOrgLeak` · `OperatorInvitationAcceptance` · `DemoOperatorSeed` IT | 같은 치환(HTTP 탐침 → 공용 빈 둘). 단언 값 무변경 |
| admin `SelfServiceOnboardingIntegrationTest` | 🔴 **AC-1 대조군** `ac1_consoleShellToken_isNotAnOperatorToken` — S5 셸 토큰과 **같은 클레임 집합**(IAM `iss` · 콘솔 `aud` · `sub`=풀 계정 · `tenant_id=iam` · `tenant_type` · `roles`/`token_type` 없음)으로 ① `GET /api/admin/operators` Bearer → `401 TOKEN_INVALID` ② 토큰 교환 → `401 TOKEN_INVALID`(운영자 행 없음) ③ `received-invitations` → `200 VERIFIED` · `organizations` → `201` ④ 대조: 생성 뒤 **같은 토큰** 교환 → `200`(②의 401 이 «나쁜 토큰» 이 아니라 «운영자 아님» 이었다는 증거) |

**빨강 측정 (#4373 `87e684c42`, run 38729482533 · 레인 로그에서 셈)**:

| 레인 (job) | 클래스 | FAILED | PASSED | 실패 형태 |
|---|---|---|---|---|
| Build & Test (116403525826) | `TenantClaimPoolPrincipalTest` | **3** | ⚪ 레인 로그가 PASSED 를 찍지 않는다 — 레인 합계 1267 completed · 3 failed(셋 다 이 클래스). 로컬 실측(같은 커밋): 이 클래스 19 중 **16 PASSED · 3 FAILED** · `TenantClaimConsumerPoolRefusalTest` 5 PASSED | `AssertionFailedError` ×3(`assertThat(t).isNull()` ← `NOT_ASKED` 의 AssertionError — 지금 코드가 admin 에 묻는다) |
| iam B (116403527692) | `ConsumerPoolSsoIntegrationTest` | **4** | 8 | `AssertionFailedError` ×3 «token status — body: `operator_eligibility_unavailable` expected 200 but 400» · `AssertionError` ×1 «not to contain `operator_eligibility_unavailable`» |
| iam B | `ConsumerPoolLegacyMoveIntegrationTest` | **1** | 5 | `AssertionFailedError` 200≠400(같은 본문) — 스텁을 뺀 admin 이 404 → 지금 코드가 거절 |
| iam A (116403527840) | `OperatorAssignmentCheckIntegrationTest` | **1** | 25 | `AssertionError: Status expected:<404> but was:<200>` |
| iam A | `SelfServiceOnboardingIntegrationTest` · `PartnershipCrossOrgLeak` · `OperatorInvitationAcceptance` · `DemoOperatorSeed` | 0 | 3 · 10 · 6 · 21 | — (AC-1 대조군과 치환 셀은 S5 전에도 참이어야 하는 것 — 초록이 맞다) |

9 FAILED 전부 단언 실패 — 컴파일 · SQL · 컨텍스트 오류 0. 🔵 AC-1 대조군(admin 절반)은 PHASE 1 에서도 **초록**이다: admin-service 는 S5 에서 바뀌지 않으므로(그 토큰을 운영자 토큰으로 받지 않는 것은 772 이전부터의 성질) 빨강이 날 자리가 없다. 그 시험의 몫은 «S5 가 여는 토큰이 권한을 넓히지 않는다» 의 고정이다.

### PHASE 2 — 수선 (PHASE 1 단언 무변경)

- **auth-service**: `customizeForPoolPrincipalOnConsole` = 늘 셸 클레임(`tenant_id=iam` · 콘솔 client `tenant_type`) — admin 호출 · 두 거절 갈래 삭제. 생성자에서 포트 제거(`TenantClaimTokenCustomizer(AccountServicePort, CredentialRepository)`). 상수 `OPERATOR_ELIGIBILITY_UNAVAILABLE` 삭제(자리에 «다시 넣지 말 것» 주석). 삭제: `OperatorConsoleEligibilityPort` · `OperatorEligibilityUnavailableException` · `AdminConsoleEligibilityClient`(+ `AdminConsoleEligibilityClientUnitTest`). 주석 정정: `AuthorizationSessionTenant` 둘 · `AdminOperatorInvitationClient`.
- **auth 시험(기계적)**: 생성자 호출 6곳에서 둘째 인자(`NOT_ASKED`) 제거 · `ConsoleEligibilityStubs` 에서 `NOT_ASKED` 제거(포트가 없으니 «묻지 않음» 은 구성으로 성립 — 단언이 아니라 타입이 지킨다). PHASE 1 단언 줄은 그대로.
- **admin-service**: 삭제 `OperatorConsoleEligibilityController` · `OperatorConsoleEligibilityQueryUseCase`(+ slice · unit 시험). `OperatorConsoleAdmission` 은 **남긴다**(토큰 교환이 쓴다) — 주석만 정정(`OperatorConsoleAdmission` · `TokenExchangeService` · `OperatorInvitationAcceptanceController` · `TokenExchangeServiceTest`). dev 시드 `R__seed_demo_*_operator.sql` 의 «console-eligibility» 주석 두 줄은 **고치지 않았다** — 반복 마이그레이션 내용이 바뀌면 체크섬이 바뀌어 다음 기동에 다시 돈다(주석 때문에 시드를 재실행하지 않는다).
- **계약 (S1 이 이미 «지운다» 로 적은 것을 구현 사실로)**: `auth-to-admin.md` § console-eligibility 머리에 **«폐기 (773 S5)»**(제목은 `admin-api.md` 가 거는 앵커라 그대로) · 노출 경로 줄 · 머리글 772 노트 / `auth-api.md` 구현 메모 «✅ 구현 = S5 (#4373)» · `'consumer-pool'` 다른 발생원 측정 결과 / `admin-api.md:671 · :1573`(교환이 운영자 판정의 유일한 자리) / `features/multi-tenancy.md`(콘솔 행 · § 4 끝 문단) / `platform/error-handling.md` `OAUTH_INVALID_GRANT` 행의 `operator_eligibility_unavailable` = «773 S5 에서 퇴역 — 재사용 금지 때문에 행에 남긴다». `OPERATOR_CHECK_UNAVAILABLE` 행은 **그대로** — 그 코드를 내는 것은 콘솔 BFF 이고(S6 몫), 행 자체가 «마지막 발신자와 함께 지운다» 고 적고 있다. 콘솔 계약 § 2.6.3 는 S1 이 이미 «S5 뒤 도달 불가 · S6 이 정리» 로 적어 두었다 — 무변경.
- **계약 이탈 0** — S1 의 계약이 이미 «호출 · 상수 · 엔드포인트를 지운다» 로 쓰여 있어 결정을 다시 하지 않았다. 티켓 § 10 S5 행의 «`operator_eligibility_unavailable` 행 그대로» 는 S1 소유자 답 1 이 대체했다(그 행은 고치지 않는다 — AC-0 기록).
- **로컬 검증**: `:auth-service:test` · `:admin-service:test` rc=0(ITs 는 Docker 없음 → CI) — `TenantClaimPoolPrincipalTest` 19/19(테스트 결과 XML). 스테이지 뒤 `check-error-code-registry` · `check-domain-error-code-registry` · `check-jwt-claims-registry` rc=0. `scripts/` 추가 · 삭제 0.

**S5 초록 (#4373 `9cbecf2a4`, run 38730783016 · 레인 로그에서 셈)**:

| 레인 (job) | 클래스 | PASSED | FAILED |
|---|---|---|---|
| iam B (116407817355) | `ConsumerPoolSsoIntegrationTest` | **12** | 0 |
| iam B | `ConsumerPoolLegacyMoveIntegrationTest` | **6** | 0 |
| iam A (116407817719) | `OperatorAssignmentCheckIntegrationTest` | **26**(404 셀 포함) | 0 |
| iam A | `SelfServiceOnboardingIntegrationTest` | **3**(AC-1 대조군 포함) | 0 |
| iam A | `PartnershipCrossOrgLeakIntegrationTest` · `OperatorInvitationAcceptanceIntegrationTest` · `DemoOperatorSeedIntegrationTest` · `TokenExchangeIntegrationTest` | 10 · 6 · 21 · 19 | 0 |
| Build & Test (116407815777) | auth · admin 단위(`TenantClaimPoolPrincipalTest` 외) | ⚪ 이 레인은 성공 시 클래스별 PASSED 를 찍지 않는다 — `BUILD SUCCESSFUL` · 로그의 ` FAILED` 줄 **0**. 클래스별 수는 위 로컬 XML(19/19) | 0 |

세 레인 FAILED 0 · `E2E smoke (iam docker-compose)` pass · 그 밖 실행된 체크 전부 pass(실패 0). 이 기록 커밋(티켓만)은 iam 레인을 다시 돌리지 않는다(경로 필터) — 초록의 근거는 `9cbecf2a4` 의 위 런이다.

**⚪ 재지 못한 것**:
- AC-1 의 두 절반은 **다른 IT 에서** 잰다 — auth IT 가 «실제 발급자가 그 클레임 집합을 낸다» 를, admin IT 가 «그 클레임 집합의 토큰은 운영자 토큰이 아니다» 를. 한 런에서 실제 auth 토큰을 admin 에 내미는 교차 서비스 시험은 없다(두 서비스를 함께 띄워 이 경로를 걷는 시험을 찾지 못했다 — `E2E smoke (iam docker-compose)` 가 풀 계정 콘솔 로그인을 걷는지는 열어 보지 않았다). 다리는 «클레임 집합이 같다» 는 두 시험의 단언뿐이고, 서명 키 · `iss` 값의 일치는 시험 설정이 맞춘 것이다.
- 콘솔이 셸 토큰을 받은 뒤의 화면(교환 `401` → 비운영자 표식 · 셸)은 S6 몫 — 여기서 재지 않았다. 그래서 S5 머지 ~ S6 배포 사이에는 측면 없는 풀 계정이 콘솔 콜백에서 교환 `401` → 지금의 `/onboarding` 으로 간다(AC-0 § 3 의 코드 읽기 `callback/route.ts:304-322` 기준 — S6 전 동작, 이번에 다시 재지 않았다). `sso_wrong_account` 에는 더 닿지 않는다.
- 라이브(데모) 미측정 — 데모 창 없음.

## S6 기록 (2026-10-15T14:43Z UTC) — console-web 비운영자 셸 · «테넌트 생성» 한 화면 · `/onboarding` 307

> 분석=Opus 5.5 (frontend-engineer) · worktree `monorepo-lab-773` · 브랜치 `task/mono-773-s6-console-shell`(origin/main `f6e850cb0` — S1 ~ S5 위) · PR #4374(draft). 방법 = S2 ~ S5 와 같다: **PHASE 1 = 시험만** 먼저 밀어 빨강을 재고(`ecd578fd4`), PHASE 2 에서 수선. 계약은 S1 이 쓴 § 2.6 – 2.6.4 그대로 구현했다(다시 정하지 않았다 — 아래 «계약 손댄 곳» 둘만).

### PHASE 1 — 시험 (`ecd578fd4`)

🔵 **새 모듈 넷은 tsc 용 빈 껍데기(stub)로 함께 들어갔다** — TS 시험은 없는 모듈을 import 하면 컴파일되지 않는다. 껍데기는 S6 이전 동작을 그대로 낸다: `/new-tenant` 페이지 = `redirect('/onboarding')`(옛 주소) · `POST /api/onboarding/received-invitations` = `404`(Next 의 «라우트 없음») · `ReceivedInvitations` · `TenantCreateScreen` = `null`. 그래서 그 넷을 겨눈 칸의 빨강 일부는 단언(`AssertionError`)이 아니라 **요소 없음**(`TestingLibraryElementError` — 껍데기가 아무것도 안 그린다)이다(아래).

| 파일 | 무엇 |
|---|---|
| `tests/unit/non-operator-auth-routes.test.ts` (신규) | 표식 생애: 콜백 · GET · POST 갱신의 교환 `401` 만 세움(HttpOnly · `maxAge`=access `expires_in`) · 허용 목록 목적지 · `POST` = `403 NOT_AN_OPERATOR` + IAM 쿠키 유지 · 성공 · 로그아웃이 지움(+ `id_token_hint`) · 대조군 `403 MFA_REQUIRED` · `503` 은 표식 없음 · 퇴역 상수 본문 = 보통 `invalid_grant` |
| `tests/unit/non-operator-client.test.ts` (신규) | 브라우저 클라이언트: 갱신 `403 NOT_AN_OPERATOR` → `/new-tenant` · 원 호출 재시도 0 · 대조 MFA · 퇴역 `503 OPERATOR_CHECK_UNAVAILABLE` = 보통 실패 |
| `tests/unit/console-shell-non-operator.test.tsx` (신규) | 가드 3행 — 리다이렉트 없음 · **fetch 0**(레지스트리 · `/me` · org-nodes 가 전부 fetch 를 지난다 — R1 단언) · 벨 · 하트비트 · 스위처 미마운트 · 계정 메뉴 · 데모 알림 마운트 · 사이드바 `variant="non-operator"` · 허용 목록(경로 같음 · 쿼리 허용) 밖 7경로 → `/new-tenant` · 대조군 3(표식 없는 access = 갱신 홉 · 표식은 자격 아님 · 운영자 셸의 남은 표식 무시) |
| `tests/unit/sidebar-non-operator.test.tsx` (신규) | 비운영자 사이드바 = 정확히 «가이드 → 테넌트 생성» · 운영자 사이드바에 «테넌트 생성»(역할 무관) · 대조 `/tenants` 유지 |
| `tests/unit/onboarding-address-redirect.test.ts` (신규) | `/onboarding` → 307 `/new-tenant`(세 상태) · 쿼리 · 대조 `/new-tenant` · `/onboardingx` · 비운영자 셸 `no-store` |
| `tests/unit/new-tenant-onboarding-bff.test.ts` (신규) | `createOrganization` 이 `403 EMAIL_NOT_VERIFIED` · `403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE` · `409 OPERATOR_ALREADY_PROVISIONED` · `429 RATE_LIMITED` 를 ApiError 로(«unavailable» 아님) · received-invitations BFF(형제 경로 · `subjectToken` = access 쿠키 · `Authorization` 없음 · `503` 은 `503` · 쿠키 없음 `401` 호출 0) |
| `tests/unit/new-tenant-page.test.tsx` (신규) | 세 모드 — 샘플 → `/` · 비운영자 = 받은 초대 → «관리자에게 초대를 요청하세요» → 첫 회사 양식(순서 단언) · 유일한 네트워크 = IAM received-invitations(운영자 토큰 없음) · `503` → `unavailable` · `'*'` SUPER_ADMIN → `TenantCreateScreen` · 회사 운영자 → 두 번째 회사 양식 · 대조: 회사 범위 `SUPER_ADMIN` 역할 행 = 회사 운영자 · `/me` 실패 → 셀프 양식 |
| `tests/unit/features/onboarding/ReceivedInvitations.test.tsx` (신규) | 행(회사 · 역할 · 만료 · «첫 관리자») · 🔴 읽기 전용(링크 · 버튼 0) · 없음 · 미인증 안내 + 링크 · `NOT_ELIGIBLE` · `unavailable` ≠ «없음» |
| `CreateOrganizationForm.test.tsx` · `TenantsScreen.test.tsx` · `tenants-page.test.tsx` · `onboarding-proxy.test.ts` | 트러스트 게이트 문구 넷 + 인증 링크 · 로그아웃 / «테넌트 만들기» · 두 번째 회사 설명 / 등록 폼 시험 11칸의 대상을 `TenantCreateScreen` 으로 옮김(단언 무변경) + `/tenants` 는 폼 대신 `/new-tenant` 링크 · 생성 성공 → `/tenants` / 목록 화면은 org-nodes 를 읽지 않음 / 생성 성공 = 표식 삭제 + 같은 기본 assume 함수(`preferredTenant` = 새 테넌트) · 테넌트 쿠키 단독 설정 없음 |
| `auth-routes` · `auth-idle-refresh` · `mfa-step-up-routes` · `session-end-demo-stopped` · `sample-fetch-allowlist` · `login-error-messages` · `relogin-loop` | `/onboarding` 단언 → `/new-tenant` · 데모 종료 목적지에 `/new-tenant…` · onboarding-client fetch 수 2 · 퇴역 `operator_check_unavailable` 문구 · 단락 회로 예외 제거 단언 |
| `operator-check-unavailable.test.ts` · `…-client.test.ts` | **삭제** — 지우는 동작(772 S4 매핑)을 재던 시험. 그 반대 명제는 위 신규 두 파일이 잰다 |
| 새 e2e `tests/e2e/non-operator-tenant-creation.spec.ts` + `fixtures/seed.sql` · `fixtures/login.ts` · `docker-compose.e2e.yml` | 아래 «e2e» |

**빨강 측정 (#4374 `ecd578fd4`, run 38749979885, job 116473657469 «Frontend unit tests» — 레인 로그에서 셈)**: console-web `Test Files 20 failed | 372 passed (392)` · `Tests **99 failed** | 4370 passed (4469)`. 로컬 같은 커밋도 똑같이 99 / 4370. 실패 모양: `AssertionError`(+ jest-dom `toHaveTextContent` / `toHaveAttribute` 단언 `Error`) 과 `TestingLibraryElementError` 22(위 껍데기 — 요소 없음). **컴파일 · 모듈 해석 오류 0** — 로컬 `npx tsc --noEmit -p .` rc=0(시험 포함). 파일별: console-shell-non-operator 16 · non-operator-auth-routes 13 · TenantsScreen 13 · new-tenant-page 9 · ReceivedInvitations 7 · new-tenant-onboarding-bff 7 · CreateOrganizationForm 6 · sidebar-non-operator 5 · onboarding-address-redirect 5 · mfa-step-up 3 · login-error-messages 3 · auth-idle-refresh 2 · non-operator-client 2 · session-end-demo-stopped 2 · auth-routes · onboarding-proxy · relogin-loop · sample-fetch-allowlist · sample-coverage-ledger(껍데기 페이지에 원장 행 없음) · tenants-page 각 1. 같은 레인의 ecommerce · fan 스위트 초록.

### PHASE 2 — 수선

- **표식** `console_non_operator`(`shared/lib/session.ts`): `setNonOperatorMarker` · `clearNonOperatorMarker`(보낸 브라우저에만 — 운영자 갱신에 빈 `Set-Cookie` 를 키우지 않는다) · `clearFullSession` 이 지운다 · `isNonOperatorSession` = access ∧ 표식 ∧ 운영자 없음. 옛 `hasPreOperatorSession`(«운영자 쿠키 없음» 추론 — R2 가 금한 술어)은 유일 호출자 `(onboarding)` 과 함께 지웠다.
- **세우는 곳 셋**: 콜백(`nonOperatorLandingFor(postLoginPath)`) · `refreshSessionCookies` 의 `fail_closed` 한 자리(GET · POST 가 공유). 교환 성공 두 곳이 지운다. `POST` 갱신 = `403 NOT_AN_OPERATOR`(`errors.ts` `NOT_AN_OPERATOR_CODE`) · GET = 허용 목록 목적지 또는 `/new-tenant`. 브라우저 클라이언트 → `/new-tenant`.
- **허용 목록** `shared/lib/non-operator.ts` 한 곳(가드 · 콜백 · 갱신 · 클라이언트가 같은 목록).
- **가드**(`(console)/layout.tsx`): 2행(운영자) 뒤 · § 2.6.1 홉 앞에 3행 — 허용 목록 밖은 `redirect('/new-tenant')`, 안은 `nonOperatorShell()`(레지스트리 · `/me` · org-nodes · 데모 정지 확인 · re-assume 호출 없음 · 벨 · 하트비트 · 스위처 없음 · 데모 알림 · bfcache 가드 · 계정 메뉴 · 사이드바 두 항목). 🔵 처음엔 3행을 1행 바로 뒤에 두었다가 `console-guard-catalog-401.test.tsx`(세션 모듈을 모의하고 `isAuthenticated` 만 true 로 둔다)가 빨개져 계약 순서(2행 먼저)로 옮겼다 — 계약이 맞았다.
- **사이드바**: `ConsoleSidebarNav variant` · `NON_OPERATOR_NAV`(`GROUPS` 에서 href 로 파생 — 라벨 출처 하나) · 운영자 사이드바 맨 위 그룹에 «테넌트 생성»(`nav-new-tenant`) · `permission-map.ts` 행(`operator` 게이트 — 역할로 숨기지 않는다).
- **`/new-tenant`**: 세 모드 · `'*'` SUPER_ADMIN 술어 = `/me` 의 `homeTenantId === '*'` ∧ 역할이 `tenant.manage` 를 가짐(`/me` 의 `roles` 는 활성 테넌트와 무관하게 `'*'` 행을 싣는다 — admin-api § 행동 테넌트). `/me` 를 못 읽으면(활성 테넌트 없음 등) 셀프 양식 + «플랫폼 관리자라면 테넌트를 고른 뒤 다시» 안내 — 더 넓은 양식을 짐작으로 내지 않는다. 이메일 인증 링크 = `${OIDC_ISSUER_URL}/email-verification`(auth-api § `/email-verification`).
- **받은 초대**: `listReceivedInvitations`(onboarding URL 의 형제 경로 — env 하나) · `getReceivedInvitationsState`(실패 = `unavailable`, 양식은 막지 않음) · BFF `POST /api/onboarding/received-invitations` · `ReceivedInvitations`(초대에 링크 · 버튼 0).
- **생성 성공**: 프록시가 표식 삭제 + `establishDefaultTenant(…, preferredTenant: 새 테넌트)`(콜백 · 갱신과 같은 함수 — 테넌트 쿠키 + assumed 토큰 함께). 옛 «테넌트 쿠키만» 설정을 지웠다. `ready:false` 는 표식 유지.
- **`createOrganization`**: `403` · `429` 를 ApiError 로(옛: `403` 이 «unavailable» `503` 으로 접혔다 — 미인증 계정에게 «잠시 후 다시» 를 말했을 것).
- **SUPER_ADMIN 폼 이동**: `TenantCreateScreen`(S4 폼 · 대화상자 · 변이 그대로 — 성공 → `/tenants`) · `TenantsScreen` 은 폼 대신 `/new-tenant` 링크 · `/tenants` 페이지는 org-nodes 를 안 읽는다.
- **`/onboarding`**: 미들웨어 `307 → /new-tenant`(`/onboarding/**` 포함) · `(onboarding)` 그룹 삭제 · 비운영자 셸도 `no-store` · 데모 종료 목적지 거절에 `/new-tenant…` · 샘플 화면 원장 `/new-tenant: pending`(샘플 방문자는 `/` 로 간다).
- **퇴역 매핑 제거**(계약 § 2.6.3 가 «S6 이 지울 수 있다» 로 열어 둔 것): 콜백 분기 · 갱신 결과 · 두 갱신 답 · 클라이언트 분기 · `/login` 문구와 단락 회로 예외 · `shared/lib/iam-token-refusal.ts` 삭제.
- 🔴 **PHASE 2 에서 고친 시험 (PHASE 1 이 놓친 것 — 단언을 계약 쪽으로 정정)**: ① `auth-routes.test.ts` «re-exchange 401s → 세션 전체 삭제» ② `mfa-step-up-routes.test.ts` «POST re-exchange 401 keeps its 401» ③ `auth-refresh-parallel.test.ts` «operator re-exchange 401 with an active tenant» — 셋 다 S1-9 가 바꾼 `POST 401 → 403 NOT_AN_OPERATOR · IAM 쿠키 유지` 의 옛 단언이다(PHASE 1 에서 `/onboarding` 만 grep 해서 못 찾았다 — 같은 동작을 `401` 로 적은 칸이었다). ④ `console-shell-non-operator` «표식만 · access 없음» 칸은 **내 시험의 결함**이었다: refresh 쿠키도 없으면 ADR-074 술어상 샘플 방문자다 — refresh 쿠키를 더해 «갱신 홉» 을 단언하게 고쳤다. ⑤ `onboarding-proxy` 두 칸에 표식 쿠키를 먼저 심었다(조건부 삭제로 바꾼 뒤 — 지울 대상이 있어야 «지웠다» 가 성립). ⑥ `TenantsScreen.test` 의 `next/link` 모의가 `data-testid` 를 버려 나머지 props 를 넘기게 넓혔고, 목록 렌더 도우미에서 `orgNodeOptions` 를 뺐다(단언 무변경).
- **로컬 검증**: 전체 `npx vitest run` **392 파일 / 4469 PASSED** · `npx tsc --noEmit -p .` rc=0 · `npx next lint` rc=0(경고 1 — 시험이 넘기는 `_req` 미사용). 🔵 병렬 부하에서 `CreateOrganizationForm` 두 칸이 한 번 5 s 타임아웃 · 단독 재실행 14/14(메모리 «프런트 스위트 동시 실행 = 5 s 타임아웃» 과 같은 모양 — 코드 결함 아님).

### 계약 손댄 곳

- `console-integration-contract.md` § 2.6.3 — «S6 이 매핑을 지울 수 있다» → **지웠다**(무엇을 · 옛 북마크는 일반 문구 · 레지스트리 행은 퇴역 표시) · 브라우저 클라이언트 줄을 취소선 + 지금 동작(`403 NOT_AN_OPERATOR` → `/new-tenant`).
- `platform/error-handling.md` — `OPERATOR_CHECK_UNAVAILABLE` 행을 **삭제하지 않고 RETIRED + 취소선**(행 자신은 «마지막 발신자와 함께 지워라» 였지만, S5 가 같은 이유로 퇴역한 `operator_eligibility_unavailable` 상수를 «재사용 금지» 로 남긴 선례를 따랐다 — 이름 재사용을 막는 쪽). 머리 문장도 정정.
- 그 밖(§ 2.6.4 · `console-web/architecture.md` 4a · 6)은 S1 이 이미 S6 동작대로 적어 두어 무변경.

### e2e — 🔴 nightly 에서만 돈다 · 머지 뒤 첫 nightly 전까지 미측정

- 새 spec `projects/platform-console/apps/console-web/tests/e2e/non-operator-tenant-creation.spec.ts` — `nightly-e2e.yml` 잡 `platform-console-e2e-fullstack` 만 실행한다(`ci.yml` 은 안 돈다). 셋: **AC-1 · AC-5**(셸 · 사이드바 링크 정확히 2 · 벨 없음 · `console_operator_token` 없음 · 표식 `1` · 🔴 대조군: 콘솔 access 토큰을 admin-service `GET /api/admin/operators` 에 Bearer 로 → `401` · 받은 초대 · ask-admin · 양식 보임 · `/operators` 직접 → `/new-tenant` · `/onboarding` → `/new-tenant`) · **AC-2**(생성 → `/dashboards` · 운영자 토큰 · 표식 없음 · `console_active_tenant` = 새 슬러그 · `console_assumed_token` 있음 — CI 재시도 때 이미 운영자가 된 계정도 같은 양식을 쓰게 착지 술어를 둘로) · **AC-7**(계정 메뉴 로그아웃 → `/login` · 쿠키 없음 → 🔴 `/api/auth/login` 이 IdP 폼(`input[name=username]`)을 보인다 = IdP 세션 끝 → SUPER_ADMIN 으로 로그인 → `/dashboards`).
- 신원: `fixtures/seed.sql` 에 개인(풀) 계정 둘 — `auth_db.credentials`(`consumer-pool`) + `account_db` identities · accounts(`email_verified_at` 설정 — S3 트러스트 게이트) · admin 행 **없음**. 둘인 이유: `fullyParallel` 이고 AC-2 계정은 생성하는 순간 운영자가 된다.
- 🔴 `docker-compose.e2e.yml` 의 console-web 에 `CONSOLE_ONBOARDING_URL` 이 **없었다** — `env.ts` 기본값 `http://iam.local/…` 로 가 e2e 에서 온보딩은 늘 실패했을 것이다(지금까지 온보딩 e2e 0 이라 드러나지 않았다). `http://admin-service:8085/api/admin/onboarding/organizations` 를 넣었다.
- 로컬: `npx playwright test --list` rc=0 — 새 spec 3 칸이 목록에 뜬다(11 tests / 5 files). **실행은 못 했다** — 로컬 Docker 없음(S3 · S4 와 같은 사정). ⚪ AC-6 «머지 뒤 첫 nightly 콘솔 잡 확인» 은 머지 뒤 몫. 🔵 워크플로 스텝 이름 «Run Playwright e2e (2 specs)» 는 이미 낡아 있다(지금 5 spec) — 리포 루트 공유 파일이라 이 PR 에서 손대지 않았다.
- e2e grep(AC-6): `tests/e2e/` · `e2e-smoke/` · 리포 루트 `tests/federation-hardening-e2e/` 에서 `onboard` · `tenant-form` · `nav-` · `조직 만들기` · `테넌트 등록` · `new-tenant` — 고칠 기존 spec **0**.

### ⚪ 재지 못한 것 · 🔴 열린 것

- 🔴 **`/tenants` 의 «관리자 대기» 행 동작 셋(계약 § 2.6.4 — 다시 초대 · 정리 · 활성화 마치기)은 이 PR 에 없다.** S4 가 표시(«관리자 대기 · 초대 만료/초대 없음/활성화 재시도»)는 넣었고, S6 슬라이스 행(§ 10)도 «`/tenants` 목록 대기 표시» 만 적었다. 그런데 S4 의 상세 화면은 `PENDING` 테넌트 수정에서 `status` 를 보내지 않으므로 **지금 콘솔 UI 로는 대기 테넌트를 정리 · 재초대 · 활성화할 길이 없다**(IAM API 로는 된다 — `PATCH status` · `POST …/first-admin-invitation` · 초대 `:resend`). 계약은 그 셋을 normative 로 적었다 → 후속 티켓으로 떼는지 · 이 PR 에 더하는지 **소유자 결정**.
- ⚪ e2e 세 칸 미실행(위) · 라이브(데모) 미측정 — S6 은 Vercel 배포이고 S2 ~ S5 백엔드가 AMI 에 올라간 뒤여야 한다(§ 10 순서).
- ⚪ 비운영자 셸에서의 § 2.6.2 «라이브 세션» 데모 정지 확인은 계약이 받아들인 공백대로 돌리지 않는다(다음 IAM 갱신 실패가 샘플 착지로 간다).
- 🔵 AC-7 의 `sso_wrong_account` 모집단: S5 기록 그대로(콘솔 client 경로 = 0) — S6 은 그 판별자를 바꾸지 않았다(바이트 그대로).

### S6 초록 (#4374 `bf4ebc01e`, run 38754577106)

- job 116489600931 «Frontend unit tests» — console-web `Test Files 392 passed (392)` · `Tests 4469 passed (4469)` · 같은 잡의 `npx tsc --noEmit` · `next lint` 스텝 통과. 그 런의 체크 전부 pass(실패 · 대기 0).

### 소유자 결정 (2026-10-16 UTC) — `/tenants` «관리자 대기» 행 동작 셋을 이 PR 에

> 위 «🔴 열린 것» 첫 줄의 답: 계약 § 2.6.4 그대로 **같은 PR #4374 에** 넣는다 — «다시 초대»(`INVITED`/`EXPIRED` 같은 주소 → 그 초대의 `:resend` · 새 주소 또는 `NONE` → `POST /api/admin/tenants/{tenantId}/first-admin-invitation`) · «정리»(`PATCH status=SUSPENDED` · 계약 문구의 화면 안 확인 대화상자 — `window.confirm` 금지) · `firstAdmin.state = ACCEPTED` 면 대신 «활성화 마치기»(`PATCH status=ACTIVE`). 오류 코드 셋(`TENANT_NOT_PENDING` · `TENANT_FIRST_ADMIN_PENDING` · `OPERATOR_INVITATION_ALREADY_PENDING`)에 한국어 문구.

- **PHASE 1 (`db3ffb759`)** — 시험만 + tsc 용 껍데기 하나(`POST /api/tenants/[tenantId]/first-admin-invitation` = 지금처럼 `404`). `tests/unit/features/tenants/tenants-pending-row-actions.test.tsx`(행마다 어떤 동작 · 같은 주소 = `:resend`(사유만) · 새 주소 / `NONE` = first-admin-invitation(이메일 · 이름 · 사유 · 멱등 키) · 정리 = 계약 문구 대화상자 → `PATCH SUSPENDED` · `window.confirm` 호출 0 · 활성화 마치기 = `PATCH ACTIVE` · 세 오류 코드 문구) · `tests/unit/tenants-first-admin-invitation-proxy.test.ts`(BFF 전달 · trim · 422 넷 · 409/404 통과 · 대조: PATCH 가 `SUSPENDED` · `ACTIVE` 를 이미 통과시킨다 — 3칸은 지금도 초록).
- **빨강 측정 (run 38760377728, job 116509906552 — 레인 로그)**: console-web `Test Files 2 failed | 392 passed (394)` · `Tests **24 failed** | 4474 passed (4498)`(행 동작 16 · BFF 8). 로컬 같은 커밋 24 / 4474. 전부 `AssertionError`(«control … is rendered: expected null not to be null» · `404≠201/409/422` · 일반 문구 = 미매핑) — 컴파일 · 모듈 오류 0 · 로컬 tsc rc=0.
- **PHASE 2**: `tenants-api.ts` `inviteFirstAdmin`(`X-Operator-Reason` + `Idempotency-Key`) · BFF `app/api/tenants/[tenantId]/first-admin-invitation/route.ts`(`FirstAdminInvitationBodySchema` — 생성의 `firstAdmin` 규칙 재사용 · 키 필수) · `useReinviteFirstAdmin`(같은 주소 = 대소문자 · 공백 무시 비교 → 기존 `/api/operator-invitations/{id}/resend` BFF, 아니면 새 BFF) · `TenantReinviteDialog`(주소를 바꾸면 이름 칸이 열린다) · `TenantsTable` 동작 열(`PENDING` 행만) · `TenantsScreen` 이 대화상자 셋(공유 `ConfirmDialog` 위 — 정리 문구 = 계약 문장) · `errors.ts` 세 코드. PATCH 프록시는 이미 `TENANT_PATCH_STATUSES`(`ACTIVE` · `SUSPENDED`)라 바꾸지 않았다. PHASE 1 단언 무변경.
- 🔵 계약 그대로 둔 것: 새 주소인데 그 테넌트에 아직 대기 초대가 있으면(`INVITED` · `EXPIRED`) IAM 이 `409 OPERATOR_INVITATION_ALREADY_PENDING` 을 답한다(admin-api — 다른 주소는 «먼저 `:cancel`»). 콘솔은 § 2.6.4 글자대로 새 초대를 부르고 그 409 를 «같은 주소로 다시 보내거나, 그 초대를 취소한 뒤 새 주소로» 로 보인다 — 취소를 대신 해 주지 않는다(계약에 없는 자동 동작).
- «테넌트 등록» 폼 → `/new-tenant` 링크(계약 § 2.6.4 끝 문장): S6 PHASE 2(`bf4ebc01e`)에서 이미 했다(`tenants-create-link`).
- e2e / 스모크 grep(`tenants-row` · `tenant-confirm` · `테넌트 관리` · `/tenants`): `tests/e2e/` · `e2e-smoke/` · 리포 루트 `tests/federation-hardening-e2e/` **0** — 고칠 spec 없음. 행 동작의 브라우저 시험은 없다(⚪ — 대기 테넌트를 만드는 e2e 시드가 없다).
- **로컬 검증**: 전체 `npx vitest run` **394 파일 / 4498 PASSED** · tsc rc=0 · `next lint` rc=0(경고 1 그대로).

---

# 닫기 (2026-10-15 UTC)

> 분석=Opus 5.5 · worktree `monorepo-lab-773` · 브랜치 `chore/mono-773-close`(origin/main `712629cac`) · 측정 시각 2026-10-15T16:08Z. 규칙 = CLAUDE.md «Objective merge verification before any close chore»(네 차원). 🔴 **결론: (a)(b)(c) 통과 · (d) 실패 — AC-2 하나가 그 AC 의 동사대로 닫히지 않았다.** 그래서 `done/` 으로 옮기지 않고 `in-progress/` 에 둔다(남은 일 = 시험 한 칸 또는 소유자 판정 — 아래 § CORRECTION). AC 체크박스는 건드리지 않았다.

## 1. 네 차원

| 차원 | 결과 | 근거 |
|---|---|---|
| (a) PR `MERGED` | ✅ 6/6 | `gh pr view <n> --json state,mergeCommit,statusCheckRollup` — #4368 S1 `460fddbfb` · #4369 S2 `19d0a0a6b` · #4371 S3 `bf2d0236f` · #4372 S4 `c98e80ab5` · #4373 S5 `f6e850cb0` · #4374 S6 `712629cac` |
| (b) `origin/main` 이 스쿼시 커밋을 담음 | ✅ | `git fetch` 뒤 `origin/main` 끝 = `712629cac`(S6), 그 아래 S5 · S4 · S3 · S2 · (#4370 BE-638 기안) · S1 순서 그대로 |
| (c) 머지 시점 실패 체크 0 | ✅ 6/6 | `statusCheckRollup` 결론 집계 — S1 SUCCESS 12 · SKIPPED 54 / S2 33 · 36 / S3 37 · 32 / S4 31 · 36 / S5 34 · 35 / S6 38 · 31. **FAILURE 0 · 대기 0**(여섯 모두). 🔵 (c) 는 «필수 체크 실패 0» 이지 스위트 초록이 아니다 — 스위트 초록은 각 슬라이스 기록의 레인 로그 셈이 근거 |
| (d) AC 를 열어 동사대로 | 🔴 **7/8** — AC-2 미닫힘 | 아래 § 2 |

## 2. AC 별 판정 (동사 기준)

| AC | 판정 | 닫는 근거(슬라이스 · 시험 · 잡) | 주의 |
|---|---|---|---|
| **AC-0** «재측정 · 집을 정하고 이유를 적는다» | ✅ | § AC-0 기록 1~11(두 입구 file:line · 계약 · 셸 가드 · 콜백/리프레시) · § 12 OD-1 = account `tenants.status` 새 값(이유: ADR-044 D3 글자대로 · 기존 `ACTIVE` 술어가 닫힌 쪽) · S1 이 이름 `PENDING`(7자, 칸 확장 없음) 확정 · AC-3 이 요구한 상한 **값** = OD-3 **3** | — |
| **AC-1** 셸 · 사이드바 둘 · 🔴 운영자 토큰 없음 · 관리 API 거절(대조) | ✅ | nightly run **38763381565** 콘솔 잡 **116520791861** — `non-operator-tenant-creation.spec.ts` «AC-1 · AC-5» 첫 시도 통과: 사이드바 링크 정확히 2(`/guide` → `/new-tenant`) · 벨 0 · `console_operator_token` 없음 · 표식 `1` · 🔴 대조: 실제 IAM 이 발급한 콘솔 access 를 실제 admin-service `GET /api/admin/operators` 에 Bearer → **401**. 단위: S6 `console-shell-non-operator`(fetch 0 — R1) · `sidebar-non-operator`. IT: S5 `SelfServiceOnboardingIntegrationTest.ac1_consoleShellToken_isNotAnOperatorToken` | 🔵 S5 기록의 ⚪(«두 절반이 다른 IT — 실제 auth 토큰을 admin 에 내미는 교차 시험 없음»)를 이 e2e 가 **한 런에서** 닫았다(실제 auth 발급 → 실제 admin 거절) |
| **AC-2** 생성 → 테넌트 + 본인 두 역할 + 배정 → 재교환 뒤 개요 · `ORIGIN` 세트 · 🔴 **같은 시험에서** assume `roles` = entitled 도메인 카탈로그 전체 · 다른 테넌트 세트 행 0 | 🔴 **미닫힘 (부분)** | 닫힌 부분: S3 IT `SelfServiceOnboardingTrustGateIntegrationTest.ac2_newCreator_originRowInTheNewTenantOnly`(job 116313854354 PASSED) — 새 테넌트 `TENANT_ADMIN` · `TENANT_BILLING_ADMIN` · 배정 1 · `ORIGIN` `all-domains-full` 정확히 1행(`granted_by` NULL) · **다른 테넌트 세트 행 0**. 브라우저: nightly «AC-2» 첫 시도 통과 — 생성 → `/dashboards` · 운영자 토큰 · 표식 없음 · `console_active_tenant` = 새 슬러그 · `console_assumed_token` 있음 | 🔴 **«assume `roles`» 는 같은 시험에서 재지 않았다.** 그 시험이 재는 것은 admin 의 assignment-check `domainRoles`(= `all-domains-full` 카탈로그 **전체**, `:399-400` 주석 «auth 의 ∩ entitled 가 좁힌다») — 토큰 `roles` 의 **admin 절반**이다. auth 절반(`∩ entitled`)은 BE-632 의 기전(`AssumeTenantExchangeIntegrationTest:533` · 27차 창 라이브 AC-4)으로 따로 서 있다. OD-4 가 정한 시험 모양(«구독 전 assume `roles` 없음(대조) → `wms` 구독 → `wms` 역할 전부», § 12 OD-4 · § 13)은 **어느 슬라이스에도 없다** — IT 의 entitled 스텁은 처음부터 `wms` 고정(`:194-197`)이라 «구독 전» 대조도 없다. e2e 는 assumed 토큰의 **존재**만 본다. ⇒ AC 가 «같은 시험에서» 를 명시했으므로 두 절반의 합성은 이 동사를 닫지 않는다 |
| **AC-3** 두 번째 회사 → 새 테넌트에만 · 🔴 기존 권한 불변 · 상한 초과 거절 | ✅ | S3 IT `ac3_secondCompany_newTenantOnly_existingCompanyByteUnchanged` — 운영자 행 하나 유지 · 새 테넌트 역할 = 두 개뿐(`SUPPORT_LOCK` 안 옮겨감) · 새 테넌트 세트 = `ORIGIN/all-domains-full` 뿐(기존 회사 `DIRECT wms-inventory-read` 안 옮겨감 — 대조) · 🔴 기존 회사 **역할 행 · 배정 행 · 세트 행 · 운영자 행 · `domainRoles`** 전후 `isEqualTo`(바이트 불변). `cap3_countsSuspendedTenant_thirdOk_fourthRefused`(상한 **3** · `SUSPENDED` 셈 · 넷째 `422 OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED` · 생성 호출 0) · `SelfServiceOnboardingCapRaceIntegrationTest`(동시 둘 = 하나만, 784 의 cap+1 닫힘). 모두 job 116313854354 PASSED | «새 회사에서 기존 회사 권한이 통하지 않음(대조군)» 은 **행 수준**(옮겨지지 않음)으로 쟀다 — 회사별 평가 자체는 784 의 기전(done). AC 문장에 «같은 시험» 요구가 없어 이것으로 닫힌다고 판정 |
| **AC-4** `SUPER_ADMIN` 생성 + 첫 관리자 → 대기 + 초대 · 수락 뒤 활성 · 만료 시 정리 규칙 · 🔴 관리자 없는 `ACTIVE` 0 | ✅ | S4 IT `TenantFirstAdminIntegrationTest` **23 PASSED**(job 116390247407) — 생성 직후 `PENDING` · 초대 실패 보상 = `SUSPENDED`(활성 안 됨) · 수락 → `ACTIVE` · 🔴 불변식 프로브 `ActivationProbe`: **모든** account 활성화 요청 시점에 커밋된 `ACTIVE` `TENANT_ADMIN` ≥ 1 · `patch_pendingToActive_withoutAcceptedFirstAdmin_409` · 만료 = OD-2(대기 유지 · 요약 «초대 만료» · 재초대 · 정리 = 취소 → 정지 · 정리 뒤 재활성 409) · 대조 3(일반 초대는 대기 테넌트 수락 거절 등). 콘솔 행 동작 셋: S6 owner decision 절 `tenants-pending-row-actions` 단위 | 라이브 ⚪ — AC 가 «라이브» 를 요구하지 않는다(재굽기 창 몫, `TASK-MONO-672`) |
| **AC-5** `/onboarding` 직접 · 콜백 «운영자 아님» 분기 → 새 화면 | ✅ | nightly «AC-1 · AC-5» — `/onboarding` → `/new-tenant` · 로그인 자체가 콜백 교환 `401` → `/new-tenant` 에 착지(`loginAsPoolAccount` 기본 착지 술어 `/new-tenant`). 단위: S6 `onboarding-address-redirect`(307 · 쿼리 · 대조 `/onboardingx`) · `non-operator-auth-routes`(콜백 · GET/POST 갱신) | — |
| **AC-6** e2e grep 후 수정 · 머지 뒤 첫 nightly 콘솔 잡 확인 | ✅ | grep: AC-0 § 7 · S4 · S6 기록 — 고칠 기존 spec **0**(새 spec 추가). 첫 nightly: § 3 | 🔵 flaky 1 은 773 의 새 spec 이 아니다 — § 4 |
| **AC-7** 비운영자 셸 로그아웃 → IAM 세션까지 끝 → 다른 계정 로그인(스토어 탭 없이) · `sso_wrong_account` 모집단을 **적는다** | ✅ | 로그아웃: nightly «AC-7» 첫 시도 통과 — 계정 메뉴 로그아웃 → `/login` · 쿠키 없음 · 🔴 `/api/auth/login` 이 IdP 폼(`input[name=username]`)을 보임 = 무음 SSO 없음 → `SUPER_ADMIN` 로그인 → `/dashboards`. 모집단: S5 AC-0 기록 — `refuseConsumerPoolTenant` 호출처 1 · 그 문구가 나가는 세 길(테넌트 없는/풀 client · `client_credentials` 풀 · assume 풀) 중 **콘솔 client 는 없음** ⇒ **콘솔 경로 `sso_wrong_account` 모집단 = 0**(OD-6: 측면 없음 · 비-`ACTIVE` 측면 모두 셸 토큰 — `customizeForPoolPrincipalOnConsole` 이 측면을 묻지 않는다). 대조 시험 `poolPrincipal_onTenantlessClient_stillRefusedWithPoolText` | 모집단 0 은 **코드 읽기 + 단위 대조**의 결론이다(라이브 로그 셈 아님) — AC 의 동사가 «적는다» 라 닫힌다 |

## 3. AC-6 — 머지 뒤 첫 nightly

- run **38763381565** — `push` of `712629cac`(S6 머지 커밋), 생성 2026-10-15T15:47:11Z(머지 15:47:07Z 의 4 초 뒤 — 머지 뒤 **첫** 런), 결론 **success**. 잡 15 중 success 12 · skipped 3(AMI 세대 · 팬 표면 · 론처 — 각 조건부).
- 콘솔 잡 **116520791861** «Platform Console E2E full-stack (Playwright + docker compose)»: `Running 11 tests` · `10 passed (59.1s)` · `1 flaky`.
- main 의 spec 별 칸 수: operators-admin-profile 1 · operators-profile 1 · overview-consolidation 5 · sample-visitor-transition 1 · non-operator-tenant-creation 3 = **11** ⇒ 773 의 새 3 칸(AC-1·AC-5 / AC-2 / AC-7)이 돌았고 **첫 시도에 통과**했다(list 리포터는 실패만 찍는다 · 세 칸의 Playwright 보고 폴더에 `-retry1` 없음).

## 4. 열린 관측 — `sample-visitor-transition.spec.ts:19` flaky (판정 아님)

- 첫 시도: «anonymous sees samples…» 에서 `getByTestId('operator-overview-cards')` 를 5 s 안에 못 찾음 — 페이지 스냅샷 = «통합 개요를 일시적으로 불러올 수 없습니다»(개요 저하) · 문서 `/dashboards/overview` 응답 **10063 ms**. 재시도 통과.
- 앞선 nightly 다섯(38745574967 · 38727544958 · 38704767456 · 38645649227 · 38642293093) 모두 `8 passed` · flaky 0 ⇒ **S6 과 함께 처음 나타났다**(칸 3 증가 · 워커 2).
- 🔴 **원인 미확정.** 가설: SSR 개요 fetch 가 병렬로 도는 비운영자 흐름(새 spec — 로그인 · 테넌트 생성 · 재교환)과 경합. **재지 않았다.** «flake = 인프라» 로 읽지 않는다 — 새 후속 `TASK-PC-FE-341`(platform-console `ready/`) 의 AC-0 이 다음 nightly 들에서 재발 · 동시 실행 상관을 잰다. 773 의 AC 를 막지 않는다(실패한 칸은 773 의 칸이 아니고, 773 의 세 칸은 첫 시도 통과).

## 5. AC-7 의 결과 — `TASK-PC-FE-331`

- 근거는 섰다: 773 뒤 콘솔 경로에서 `sso_wrong_account` 에 닿는 모집단 = 0(§ 2 AC-7) ⇒ PC-FE-331 은 **구현 없이 종결** 가능(그 티켓 Dependency Markers 의 «0 이면 구현 없이 종결»).
- 🔴 **지금 닫지 않는다** — PC-FE-331 의 착수 게이트가 «773 `done/`» 이고 773 은 아직 아니다(§ CORRECTION). 773 이 `done/` 으로 간 뒤 그 티켓에서: ① AC-0 = 이 절과 S5 AC-0 기록을 인용해 «모집단 0» 기록 ② 남은 코드 = 콘솔 콜백의 `sso_wrong_account` 판별 함수 · 분기(`callback/route.ts`, S6 이 바이트 그대로 둠)와 `widgets/sso-wrong-account-logout/` — 콘솔 경로에선 도달 불가가 됐지만 **지우는지 남기는지는 그 티켓의 결정**(다른 client 발생원 셋은 남는다) ③ AC-3 라이브(스토어 탭 없이 탈출)는 773 AC-7 e2e 가 같은 명제를 이미 쟀다 — 라이브로 다시 잴지도 그 티켓 몫.

## 6. 다음 재굽기에 넘기는 라이브 확인

AC 가 아니다(어느 AC 도 «라이브» 를 요구하지 않는다). `TASK-MONO-672` § Next window 에 773 행으로 적었다 — 그 표가 순서만 갖고, 결과는 이 티켓(또는 닫힌 뒤엔 672 의 수확 절)에 적는다.

## CORRECTION (2026-10-15 UTC) — 닫기 보류: AC-2 의 «같은 시험에서 assume `roles`»

- **무엇이 빠졌나**: AC-2 의 «같은 시험에서 그 테넌트 assume `roles` = 그 테넌트 entitled 도메인의 카탈로그 전체» — OD-4 결정(§ 12)이 그 시험의 모양을 «구독 전 `roles` 없음(대조) → `wms` 구독 → `wms` 역할 전부» 로 정했다. 실제로 잰 것은 admin 절반(`domainRoles` = `all-domains-full` 카탈로그 전체)뿐이다. 토큰의 `roles` 클레임을 그 새 테넌트에서 본 시험이 없다.
- **왜 중요한가**: `ORIGIN` 행은 구독 검사 없이 쓰이고(OD-4 · ADR-083 D5 예외) 상한은 auth 의 `∩ entitled` 하나다. 그 두 기전이 **이 새 출처에서** 합성되는지는 각 절반의 초록으로는 보이지 않는다(«두 각각 옳은 것이 합쳐서 구멍»). AC-0 § 8-2 가 «∅ = ∅ 로 공허하게 초록» 을 경고한 바로 그 자리다.
- **닫는 길 (둘 중 하나 — 소유자 선택)**:
  1. **시험 한 칸** — 한 런에서 실제 auth 와 admin 을 함께 걷는 자리(콘솔 nightly `non-operator-tenant-creation.spec.ts` «AC-2» 끝에 이어 붙이거나, `E2E full (iam docker-compose)`): 생성 직후 assume 토큰의 `roles` 없음(대조) → `SUPER_ADMIN` 이 그 테넌트에 `wms` 구독 → 재교환 · re-assume → `roles` = `wms` 카탈로그 전부 · `fan` 역할 0 · 같은 런에서 다른 테넌트 세트 행 0. 머지 뒤 첫 nightly 로 닫는다(e2e 면 `ci.yml` 이 돌리지 않는다 — R8).
  2. **소유자 판정** — admin 절반(S3 IT) + auth 절반(BE-632 `AssumeTenantExchangeIntegrationTest:533` · 27차 창 AC-4 라이브)의 합성을 AC-2 의 충족으로 받아들인다고 기록. 그러면 이 CORRECTION 아래에 결정을 적고 닫기를 다시 돈다.
- **그때까지**: 이 파일은 `in-progress/` 에 둔다(남은 것이 시험 = 구현이라 `review/` 로 얼리지 않는다). `TASK-PC-FE-331` 의 게이트(773 `done/`)도 그때까지 닫히지 않는다.

### 소유자 결정 (2026-10-15 UTC, `date -u` 기준) — AC-2 = 위 길 1 «시험 하나 추가»

- **고른 길 = 1.** 실제 auth 와 admin 이 **한 런에서 함께 도는** 시험 한 칸을 더한다. 하네스(콘솔 nightly `non-operator-tenant-creation.spec.ts` «AC-2» 또는 `E2E full (iam docker-compose)`)는 **구현자가** 그 단언을 할 수 있는 쪽으로 고른다. 단언: 생성 직후 assume `roles` **없음**(대조) → `SUPER_ADMIN` 이 그 테넌트에 `wms` 구독 → re-assume → `roles` = `wms` 카탈로그 **전부** · `fan`(그리고 구독하지 않은 다른 도메인) 역할 **0**.
- **기각**: 길 2(두 절반의 합성을 소유자 판정으로 수용) · «라이브 창에서만 잰다»(`TASK-MONO-672` § Next window 6 의 AC-2 라이브 줄은 확인일 뿐 이 AC 를 닫는 근거가 아니다).
- **순서**: 🔴 빨강 먼저(단언이 지금 코드에서 실패할 자리를 재거나 — 지금 코드가 이미 맞으면 bite 로 단언이 무는지 증명) → 머지 → 그 하네스의 **다음 nightly(또는 그 레인)에서 통과 측정** → 773 닫기를 다시 돌아 `done/`.
- **그 측정 전까지 이 티켓은 `in-progress/` 에 남는다.**

### 소유자 결정 (2026-10-15 UTC) — 배포 순서 = «재굽기 전엔 창을 열지 않는다»

- S4 · S6 콘솔은 이미 Vercel 운영(배포 7091340401 · 7097384302 success)이고 배포된 AMI 는 27차(`73daae263`, S2~S5 없음)다. 옛 admin 은 `SUPER_ADMIN` 생성 양식의 `firstAdmin` 을 모르는 칸으로 **무시**한다(S4 § «배포 순서 창» 실측) → **`201` `ACTIVE` · 관리자 0 · 초대 0** — 773 이 닫는 ADR-044 D3 구멍이, 양식은 «첫 관리자를 받았다» 고 보이는 채로 열린다.
- ⇒ **773 S2~S5 를 실은 굽기 전에는 27차 AMI 로 데모 창을 열지 않는다. 콘솔 롤백은 하지 않는다.** 상설 게이트로 `TASK-MONO-672` § Next window 6 에 적었다.

## AC-2 시험 (2026-10-15 UTC — 위 소유자 결정 «길 1»)

> 분석=Opus 5.5 · worktree `monorepo-lab-773` · 브랜치 `task/mono-773-ac2-roles-e2e`(origin/main `a01005367`) · 드래프트 PR **#4376** · 측정 시각 2026-10-15T17:52Z. 🔴 **이 기록은 AC-2 를 아직 닫지 않는다** — 결정의 «순서» 대로 머지 뒤 `main` 의 nightly 콘솔 잡에서 이 칸이 통과한 것을 재고 닫기를 다시 돌아야 한다. 아래 초록은 **브랜치** dispatch 런이다.

### 하네스 — 콘솔 nightly (`platform-console-e2e-fullstack`), `E2E full (iam docker-compose)` 아님

- 고른 이유: 한 compose(`projects/platform-console/docker-compose.e2e.yml`)에 실제 auth · account · admin 이 함께 돌고, ① 시드된 `SUPER_ADMIN`(global-setup 이 OIDC PKCE 로 받은 운영자 토큰이 storageState 에 있다) ② 이메일 인증된 비운영자 풀 계정(S6 시드) ③ 온보딩 URL 배선(S6 이 넣은 `CONSOLE_ONBOARDING_URL`) ④ 콘솔의 실제 assume 경로(`POST /api/tenant` → auth token-exchange)가 **이미** 있다. iam compose 하네스에는 ②③ 이 없고 «생성 → 같은 브라우저 세션의 assume» 을 새로 지어야 한다.
- 시험: `non-operator-tenant-creation.spec.ts` 의 새 칸 **«AC-2 roles»**. 시드에 세 번째 풀 계정 `e2e-pool-roles@example.com`(`…c775`) — `fullyParallel` 에서 기존 «AC-2» 칸과 생성자를 나누지 않게(상한 3 · 착지 술어 공유 방지).
- 단계(한 시험 · 한 런):
  0. 카탈로그 = `GET /api/admin/domain-roles`(단일 소유자 `DomainRoleCatalog` — 손으로 적지 않음). 비공허: wms 쪽 길이 > 0 · «그 밖» 쪽에 `FAN_OPERATOR` 포함.
  1. 풀 계정 → 실제 온보딩 → `/dashboards` · `console_active_tenant` = 새 슬러그.
  2. admin 절반(같은 런): 생성자 운영자 토큰 `sub` 로 `GET …/operators/{id}/domain-access`(`X-Tenant-Id` = 새 슬러그) → 세트 = 정확히 `all-domains-full:ORIGIN` · 역할 = **카탈로그 전체**(fan 포함 — ∩ 가 자를 것이 있다). 다른 테넌트(`fan-platform`) → `404`(소속 없음 ⇒ 세트 행 없을 자리 — 행 수준 «다른 테넌트 세트 행 0» 은 S3 IT 가 쟀다).
  3. 대조: 생성 교환의 assume 토큰 · 구독 전 새 전환 교환(`POST /api/tenant`)의 토큰 둘 다 `roles` **없음**(`expect.soft` — 빨강이어도 뒤 단계가 보고되게).
  4. `SUPER_ADMIN` → `POST /api/admin/subscriptions {tenantId: 새 슬러그, domainKey: wms}` = `201`.
  5. `POST /api/tenant` 로 **새 교환**(TTL 300 s 를 기다리지 않음 · 토큰이 바뀐 것을 단언) → `entitled_domains = ["wms"]` · 정렬한 `roles` = wms 카탈로그 · 그 밖 도메인 역할 0 · `FAN_OPERATOR` 없음.

### Bite (빨강 측정) — CI, 임시 커밋 + 되돌림

- 로컬 Docker 없음 → 브랜치에 임시 커밋 `cda01f129`(auth `TenantClaimTokenCustomizer.assignedWithinCeiling` 이 `∩` 없이 `domainRoles` 를 그대로 반환) → `gh workflow run nightly-e2e.yml --ref task/mono-773-ac2-roles-e2e` → 되돌림 `0fb71293a`(그 파일 `main` 과 바이트 동일 — `git diff a01005367 --stat` 에 시험 · 시드 두 파일만).
- 런 **38777797324** · 콘솔 잡 **116570399831** — conclusion `failure`: `Running 12 tests using 2 workers` → **`1 failed` · `11 passed (1.1m)`**. 실패 칸 = «AC-2 roles» 하나(첫 시도 + `-retry1` + `-retry2` 셋 다). 시도마다 같은 세 단언이 빨강: «right after creation … ⇒ no role»(`+ Received +16`) · «a fresh exchange before any subscription ⇒ still no role»(`+16`) · «roles = the whole wms catalog»(`+ Received +6` = 그 밖 도메인 역할 여섯). 로그 줄: 생성 시점 · 구독 전 `roles` = 카탈로그 14 개 전부(`FAN_OPERATOR` 포함, `entitled=null`) · 구독 뒤 14 개(`entitled=["wms"]`). ⇒ 단언이 ∩ 를 문다. 🔵 비-단언 단계(카탈로그 읽기 · admin 절반 · `404` · 구독 `201` · 재교환 `200`)는 bite 런에서도 모두 통과 — 빨강은 술어에서만 났다.
- 🔵 부수 효과: 그 런의 빨강이 자동 감시 이슈 **#4377** 을 열었다(`nightly-red-arrives` — 브랜치 dispatch 도 연다). 의도한 bite 런이라고 코멘트만 달고 닫지 않았다 — 규칙대로 `main` 런에서 콘솔 잡이 실제로 돌아 success 일 때 닫힌다.

### 초록 (되돌린 커밋 · 브랜치)

- 런 **38779408841**(head `0fb71293a`) — conclusion `success` · 콘솔 잡 **116575829694** success: `Running 12 tests using 2 workers` · **`12 passed (59.5s)`** · flaky 0. «AC-2 roles» 로그 줄이 한 벌뿐 = 첫 시도 통과(재시도 없음).
- spec 별 칸: operators-admin-profile 1 · operators-profile 1 · overview-consolidation 5 · sample-visitor-transition 1 · non-operator-tenant-creation **4**(3 → 4) = 12. 로컬 `npx playwright test --list` 도 `12 tests in 5 files`.
- 측정 줄: 카탈로그 14 · wms 8 `[INBOUND_READ,INBOUND_WRITE,INVENTORY_READ,INVENTORY_WRITE,MASTER_READ,OUTBOUND_READ,OUTBOUND_WRITE,WMS_OPERATOR]` · 그 밖 6 `[ECOMMERCE_OPERATOR,ERP_OPERATOR,FAN_OPERATOR,FINANCE_OPERATOR,MES_OPERATOR,SCM_OPERATOR]` · 생성 시점 `roles=null entitled=null` · 구독 전 `roles=null entitled=null` · 구독 뒤 `roles 8 [WMS_OPERATOR,OUTBOUND_READ,OUTBOUND_WRITE,INBOUND_READ,INBOUND_WRITE,INVENTORY_READ,INVENTORY_WRITE,MASTER_READ] entitled=["wms"]`.
- 로컬: `npx tsc --noEmit -p .` rc=0 · `./gradlew :…:auth-service:compileJava`(bite 커밋) rc=0.

### ⚪ 남은 것 · 한계

- ⚪ **AC-2 를 닫는 측정은 아직 없다** — 이 칸은 `ci.yml` 이 돌리지 않는다. 머지 뒤 `main` 의 첫 nightly 콘솔 잡에서 «AC-2 roles» 통과(spec 별 칸 수로 12 중 이 칸이 돈 것 확인)를 잰 뒤 닫기를 다시 돈다.
- ⚪ «다른 테넌트 세트 행 0» 은 이 시험에서 **소속 수준**(`fan-platform` domain-access `404`)으로만 본다 — 행 수준은 S3 IT `ac2_newCreator_originRowInTheNewTenantOnly` 몫 그대로.
- ⚪ 대조의 «`roles` 없음» 은 «구독 0» 과 «entitled 조회 실패(fail-soft — 클레임 생략)» 를 토큰만으로는 가르지 못한다. 같은 런의 구독 뒤 `entitled=["wms"]` 가 조회 경로가 살아 있음을 보이고, admin 절반이 배정 = 카탈로그 전체임을 보이므로 «없음» 의 원인은 ∩ 다 — 다만 그 두 사실의 결합이지 한 술어는 아니다.
