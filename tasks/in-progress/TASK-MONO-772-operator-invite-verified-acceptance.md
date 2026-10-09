# Task ID

TASK-MONO-772

# Title

`ADR-MONO-080` 단계 3 (D6 · R4) — 운영자 규칙을 **«대상 테넌트 계정» 에서 «초대 → 인증된 본인 수락»** 으로 · 풀 계정의 콘솔 진입 · 셀프 온보딩 운영자 풀 이동

# Status

in-progress

# Owner

monorepo

# Task Tags

- iam
- platform-console
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (운영자 권한 평면 · 권한이 처음 붙는 지점)

---

# Dependency Markers

- **선행**: `TASK-MONO-770` · `TASK-MONO-771` `done/` (ADR-080 D2).
- **후속**: `TASK-MONO-773`(D9 테넌트 생성 하나) · (`TASK-MONO-774` 가 연결 쓰기를 «초대 수락의 부산물» 로 고르면 774).

# Goal

지금 풀 계정 → 회사 운영자의 길은 세 곳에서 닫혀 있다(ADR-080 Context): 생성(`TASK-MONO-334` — 대상 테넌트에 가입 계정 필요, 풀 멤버 안 셈, `CreateOperatorUseCase.java:86-108`) · 로그인(`TASK-BE-615` D-5 — 풀 세션은 콘솔 토큰 없음) · 셀프 온보딩(044 — 풀 계정은 `platform-console-web` 토큰을 못 받음, `OnboardingController.java:27-28, 57`). 셋을 연다:

- 운영자(회사 관리자)가 이메일로 **초대** → 그 이메일을 **인증한** 풀 계정이 **로그인한 상태로** 수락 → 운영자 측면 생성. 이메일 일치만으로는 안 붙는다(ADR-034 § 1.3).
- 내부 프로비저닝(`/internal/tenants/{tenantId}/accounts`, 회사 테넌트에 사이트 계정 생성)을 **풀 계정 초대**로 바꾼다.
- 풀 세션의 콘솔 진입 — **운영자 측면이 있는** 풀 계정만 운영자 토큰을 받는다.
- 사이트 계정으로 남아 있는 셀프 온보딩 운영자를 풀로 옮기고, 그 이메일의 풀 가입 거절(`TASK-BE-614` AC-6)을 걷는다.

라이더 R4(기본값): 초대 = **1회용 · 만료(기본 `P7D`) · 토큰 원문 미저장**(셀러 구성원 초대와 같은 모양).

# Scope

## In Scope

- 계약 먼저: `admin-api.md` 운영자 초대 · 수락(신설), 운영자 생성(`POST /api/admin/operators`)의 334 규칙 대체 방식 · `admin-to-account.md` · `multi-tenancy.md` § 소비자 계정 풀 § 3 운영자 측면 표
- admin-service: 초대 저장(해시) · 만료 · 수락(770 의 공용 인증 술어 재사용) · 334 계정 확인 대체 · 테넌트 범위 제한(ADR-024 D2)과 역할 무상승(D3) 그대로
- auth-service: 운영자 측면 있는 풀 계정에 `platform-console-web` 토큰 발급(615 D-5 갱신)
- 데이터 이동: 셀프 온보딩 운영자 사이트 계정 → 풀(`oidc_subject` 보존 방침 포함) · AC-6 거절 해제
- 콘솔: 운영자 관리 화면의 «등록» 을 «초대» 로(대기 중 초대 목록 · 취소 · 재발송)

## Out of Scope

- «테넌트 생성» 화면 통합(773) · 직원 마스터 연결(774) · 플랫폼 관리자(`'*'`) 모델(D1 — 그대로)

# Acceptance Criteria

- [x] **AC-0** — 착수 시 재측정: 334 규칙 file:line · 615 D-5 거절 지점 · 온보딩 audience 요구 · 사이트 계정으로 남은 셀프 온보딩 운영자 수(정적 · 라이브 ⚪ 가능).
- [ ] **AC-1** — 🔴 대조군: 인증 안 된 계정 · 다른 이메일 계정 · 만료/재사용 초대 → 수락 거절. 같은 시험에서 인증된 본인 수락만 성공.
- [ ] **AC-2** — 🔴 닫힌 경로(D9 = T1 반영): 운영자 측면 **없는** 풀 계정은 **운영자 토큰을 받지 못한다**(셸 진입 여부가 아니라 토큰 · 관리 API 로 단언).
- [ ] **AC-3** — 퇴사(운영자 측면 회수) 뒤 그 풀 계정의 스토어 · 팬 로그인은 그대로이고 콘솔 운영자 토큰은 없다(ADR-080 D5).
- [ ] **AC-4** — 파트너십 해지 → 협력사 직원(풀 계정)의 회사 A 진입이 다음 요청에서 거절.
- [ ] **AC-5** — 셀프 온보딩 운영자 이동 뒤 같은 사람이 같은 `sub` 로 콘솔에 들어오고(또는 방침대로) · 그 이메일의 풀 가입 거절이 사라진다.
- [ ] **AC-6** — 셀프 온보딩으로 만든 새 조직의 관리자가 직원을 **초대 → 수락**으로 운영자로 만들 수 있다(이 대화가 찾은 빈틈의 종단 확인, 라이브 ⚪ 가능).

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` D5 · D6 · R4 · § Verification · § ACCEPT 가 만든 새 의무
- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` § 8 (334 개정 — 이 티켓이 대체)
- `docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md` D5
- `projects/iam-platform/specs/features/multi-tenancy.md` § 소비자 계정 풀

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` · `internal/admin-to-account.md` · `onboarding-api.md`

# Edge Cases

- 같은 사람이 여러 회사에 초대 — 운영자 측면은 테넌트마다(`(tenant_id, email)` 유니크, V0025).
- 초대 이메일을 아직 풀에 가입하지 않은 사람 — 수락 화면이 가입 → 인증 → 수락으로 안내.

# Failure Scenarios

1. 334 를 걷고 초대 수락에 인증 술어를 빠뜨린다 — ADR-080 위험 ① 이 그대로 열린다.
2. 풀 계정 전부에 콘솔 토큰을 준다 — 운영자 측면 없는 소비자가 관리 API 표면에 닿는다.

---

# AC-0 기록 (2026-10-09 UTC)

> 분석=Opus 5.5 (architect) · **코드 변경 0** · 정적 읽기, worktree `feat/mono-772-operator-invite`(origin/main `2a3b328e6`). 라이브 ⚪ — DB · 데모 미기동.
> 🔵 AC-0 체크박스는 건드리지 않았다(오케스트레이터 판단). ADR-080 · 라이더 R4 · 티켓이 이미 정한 것은 다시 묻지 않고 § 2 **구현자 결정**에 적었고, 그것들이 정하지 않은 것만 § 4 **소유자 결정(OD)** 으로 분리했다. ⚠️ 날짜는 호스트 날짜로 적었다 — 이 세션에서 `date -u` 를 돌리지 못했다(KST 00–09 시면 하루 앞선다).

## 0. 선행

| 잰 것 | 결과 | 출처 |
|---|---|---|
| `TASK-MONO-770` `done/` | ✅ | `tasks/done/TASK-MONO-770-verification-mail-and-email-gate.md` |
| `TASK-MONO-771` `done/` | ✅ (25차 창 AC-3 라이브로 닫힘) | `tasks/done/TASK-MONO-771-iam-two-factor-and-entry-policy.md:652-656` |
| INDEX 행 | 🔵 `tasks/INDEX.md:185` 는 아직 «READY» — 파일은 `in-progress/` 로 옮겨졌다. 큐 드리프트 가드 대상(오케스트레이터) | `tasks/INDEX.md:185` |

## 1. 티켓 AC-0 의 네 측정

| # | 티켓 주장 | 판정 | 실측 (file:line) |
|---|---|---|---|
| 1 | 334 규칙 = `CreateOperatorUseCase.java:86-108` | ✅ 맞다 | 주석 `:86-99`(BE-615 AC-8 «풀 멤버 안 셈» 포함 `:96-99`) · 판정 `:100-108`(`'*'` 면제 `:100`, `searchSiteAccounts` → `excludePoolMembers=true` — `admin-to-account.md:81`, 없으면 `422 OPERATOR_ACCOUNT_NOT_FOUND`). 🔵 정정: ADR-080 D1 이 든 «면제 규칙 `:92-100`» 은 지금 주석 줄이다(면제 술어는 `:100`). 계약 `admin-api.md:1582` · ADR-035 § 8 `:220-228` |
| 2 | 615 D-5 거절 지점 | ✅ 맞다 — 단 **거절은 한 곳**, 거절이 «되게 하는» 자리가 셋 | ① 🔴 **거절**: 발급자 `TenantClaimTokenCustomizer.refuseConsumerPoolTenant` `:251`(호출) · `:272-287`(`invalid_grant`, 문구 `"tenant_id 'consumer-pool' is a reserved storage value…"` `:283-285`). ② 거기까지 가게 하는 것: 콘솔 client 는 풀 principal 을 사상하지 않는다 — `TenantContext.poolPrincipalMapsTo` `:67-71`(`iam` 제외) → `AuthorizationSessionTenant.of` `:66-74` · `mapsPoolPrincipalTo` `:104-106` → 커스터마이저 `:419-430` 이 풀 값을 남긴다. ③ 🔵 authorize 게이트는 **거절하지 않는다**: `AuthorizeSessionTenantGate.decide` `:193-204` — 콘솔 client 면 풀 분기(`:193-195`)를 타지 않고 콘솔 분기로 가서, 같은 이메일의 `iam` 자격이 없으면 `PASS`(`:201-203`). 즉 코드는 나가고 토큰 엔드포인트가 거절한다(`multi-tenancy.md:478-479` 와 일치). 콘솔 쪽 판별: `TASK-PC-FE-324` 의 `'consumer-pool'` 부분 문자열 → `sso_wrong_account`(`callback/route.ts` `isConsumerPoolSsoRefusal`, PC-FE-324 기록 `:73`) |
| 3 | 온보딩 audience 요구 = `OnboardingController.java:27-28, 57` | ✅ 맞다 | javadoc `:27-28` · 호출 `:57` `validateAndExtractSubject` — 771 S4 이후 포트 default = `validate().subject()`, **`amr` 은 보지 않는다**(771 S4 기록 «온보딩 불변»). audience 값 `admin-service/src/main/resources/application.yml:132` `${OIDC_CONSOLE_CLIENT_ID:platform-console-web}` · 검증 `IamOidcJwksSubjectTokenValidator.java:97`. ⇒ 풀 계정은 이 토큰을 못 받으니(측정 2) 셀프 온보딩도 못 한다 — ADR Context 위험 ④ 표 그대로 |
| 4 | 사이트 계정으로 남은 셀프 온보딩 운영자 수 | **정적 0 · 라이브 ⚪** | 그 운영자를 만드는 유일한 쓰기 = `FirstAdminProvisioner.java:87-95`(`oidc_subject` = 호출자 account id). 시드: admin `db/migration-dev` 의 운영자 행 6개(`demo-operator`·`demo-requester` `demo-corp` · `demo-platform` `'*'` · `demo-viewer` · `demo-cs` `ecommerce` · `demo-assigned-only` `ecommerce`)는 **전부 `iam` 테넌트 자격**에 묶이거나(`R__seed_demo_operator.sql:38-40, 68-69` 등) `oidc_subject` NULL(`R__seed_demo_assigned_only_operator.sql:24-25`) — 소비자 사이트 계정에 묶인 행 0. 온보딩 API 를 부르는 시드·스크립트 0(`onboarding/organizations` 저장소 grep — 코드·시험·문서만). 라이브 판정 술어(다음 창): `admin_db.admin_operators.oidc_subject` ∈ `account_db.accounts.id WHERE tenant_id IN (소비자 사이트)` 의 수 — 또는 이동기 응답의 `OPERATOR_FACETED` 수(`LegacyMoveOutcome.java:19`). 데모는 재굽기마다 신선 볼륨이라 구조적으로 0 이 예상값 |

### 1.1 티켓이 적지 않은 것 (새로 찾음)

| # | 무엇 | 출처 | 귀결 |
|---|---|---|---|
| F1 | 🔴 **334 로 만든 운영자는 `oidc_subject` 를 받지 않는다** — 생성은 그 칸을 쓰지 않고, 그 칸을 쓰는 곳은 셀프 온보딩과 일회성 백필 둘뿐이다. 즉 334 운영자는 **OIDC 주 경로로 로그인할 수 없다**(교환 = `sub` 단독 조회 → 401 → 콘솔 `/onboarding`). break-glass 비밀번호를 준 경우만 로컬 로그인 | `CreateOperatorUseCase.java:128-131` · `JpaAdminOperatorAdapter.java:109-119` · 쓰기 지점 `FirstAdminProvisioner.java:94` · `OperatorOidcSubjectBackfillUseCase.java:139` · 명세 `admin-service/data-model.md:33, 76-79`(«채움은 별도 운영 경로의 책임») · `TASK-MONO-766` AC-4 는 **생성만** 확인 | D6 이 여는 «로그인 문» 의 실체는 **수락 때 `oidc_subject` = 수락한 풀 계정 id 를 쓰는 것**이다. 334 의 비-`'*'` 생성을 걷어도 OIDC 쪽으로 잃는 것은 없다(§ 2 D-7) |
| F2 | 🔴 **한 사람 · 여러 회사(Edge Case 1)를 지금 스키마가 담지 못한다** — `oidc_subject` 는 **플랫폼 전역** UNIQUE, 교환은 `sub` 로 **한 행**만 찾는다, 역할 바인딩 PK 는 `(operator_id, role_id)` 라 같은 역할을 두 테넌트에 못 준다. 티켓이 든 `(tenant_id, email)` 유니크(V0025)는 이메일 축일 뿐 `sub` 축이 막는다 | `V0027`(`uk_admin_operators_oidc_subject`) · `data-model.md:46-49` · `TokenExchangeService.java:89-96` · `V0004__create_admin_rbac_tables.sql:48` · 771 S5 CI 1회차 #2(같은 PK 가 두 번째 `TENANT_ADMIN` 행을 조용히 버림) · `FirstAdminProvisioner.java:91-92`(«first-time onboarder 에게만 비어 있다») | 두 번째 회사 초대 수락은 UNIQUE 위반. `TASK-MONO-773` AC-3(«이미 운영자인 계정의 두 번째 회사»)도 같은 벽 → **HS-A · OD-1** |
| F3 | 🔴 770 의 «공용 술어» 는 **다른 서비스에서 그대로 부를 수 없다** — account-service 안의 정적 메서드이고 account-service 도메인 `Account` 를 받는다. 초대는 admin-service 에 산다 | `VerifiedEmailRequirement.java:13-14, 31-36` · 770 AC-5 노트 `:136-141` · 771 S2b 는 auth-service 에서 같은 술어를 **읽기 체인으로 다시 만들었다**(771 S2b 기록 «명세와 다르게» 1) | 772 는 사본을 만들지 말고 **admin → account 내부 판정 호출**을 둔다 — account-service 가 `ConsumerSiteRoleWriteUseCase.grant` 의 순서(`:38-57` — 풀 계정 → 이메일 일치 → `VerifiedEmailRequirement.require`)로 판정하고 같은 `403 EMAIL_NOT_VERIFIED` 를 낸다(§ 2 D-3) |
| F4 | 🔴 콘솔 토큰의 테넌트 판정은 **세 곳이 같은 함수**를 써야 한다 — 발급자 · authorize 게이트 · refresh 미러 행(`AuthorizationSessionTenant`). 그런데 «풀 → 콘솔» 을 `poolPrincipalMapsTo` 에 그냥 넣으면 게이트가 풀 분기(`:193-195`)로 가서 `iam` 의 소비자 사이트 멤버십을 묻고 → «소비자 사이트 아님» → **재인증 무한 반복** | `TenantClaimTokenCustomizer.java:419-423`(«the three never disagree») · `AuthorizeSessionTenantGate.java:193-195, 330-343` · `AuthorizationSessionTenant.java:49-54, 66-74` | 콘솔-풀 매핑은 **풀-사이트 매핑과 다른 갈래**로 넣는다. 측면 판정은 I/O 라 순수 함수에 못 넣는다 → 발급자에서 판정(§ 2 D-5) |
| F5 | 🔴 거절 판별자 — 콘솔은 `'consumer-pool'` 문자열로 «다른 계정» 을 판정한다. 측면 조회 **실패**(admin 장애)를 같은 문구로 거절하면 운영자에게 «소비자 계정으로 로그인돼 있다 · 로그아웃하라» 가 뜬다 | PC-FE-324 기록 `:68-75` · 발급자 문구 `:283-285` | 측면 없음 = 기존 문구 그대로(→ `sso_wrong_account` 경로 불변 — 문구 상수가 바뀌지 않음을 실측), 조회 실패 = **새 고정 상수**(→ 콘솔 `token_exchange_failed` 류). S1 계약 · S5 콘솔 분기 |
| F6 | 퇴사자(AC-3)의 콘솔 문구 — `ACTIVE` 운영자만 콘솔 토큰을 받으면 퇴사자는 F5 의 «측면 없음» 거절 → `sso_wrong_account` «다른(소비자) 계정으로 로그인돼 있습니다» 를 본다. 안전하지만 문구가 사실과 다르다 | PC-FE-324 기록 `:52` | 문구 정정은 773(비운영자 셸이 이 거절 자체를 없앤다) 또는 PC-FE-331 몫 — OD-6(경미) |
| F7 | 🔴 PC-FE-331 의 전제가 772 · 773 에서 바뀐다 — 772 뒤 **측면 있는** 풀 계정은 거절이 사라지고, 773 뒤에는 측면 없는 풀 계정도 (비운영자 셸로) 거절이 사라진다 | `projects/platform-console/tasks/ready/TASK-PC-FE-331-…:36-38` | PC-FE-331 은 773 AC-0 뒤로 미루거나 773 과 함께 재판정하는 것이 맞다(오케스트레이터 판단) — 지금 IdP 로그아웃 표면을 만들면 773 뒤 쓸모가 줄어든다 |
| F8 | 🔴 **계정 없는 피초대자(Edge Case 2)의 «가입» 이 갈 곳이 없다** — 콘솔(`iam`)은 가입을 안 보이고, 풀 가입은 **소비자 사이트 client** 로만 생기며 그 사이트 멤버십(= 이용 동의)을 같이 만든다 | `TenantSignupEligibilityResolver.java:81-85`(`iam` = tenants 행 없음 → 가입 안 보임) · `ConsumerAccountPool.java:39-41`(`signupGoesToPool` = 소비자 사이트만) · `:115-122`(가입 = 멤버십) · `multi-tenancy.md:404-406` | 직원이 회사 초대를 받으려고 팬 · 스토어 회원이 되는 부작용 → **OD-3**. 스토어 경로라면 `TASK-FE-108`(가입 직후 첫 스토어 로그인 실패)도 밟는다 |
| F9 | 🔴 측면 없는 풀 계정은 콘솔 토큰이 없다 ⇒ 772 에서 **수락 화면을 콘솔에 둘 수 없다**(두려면 모든 풀 계정에 콘솔 토큰 = 773 의 일). 그리고 그렇게 열면 `/onboarding`(044)이 풀 계정에 열리는데, **온보딩에는 인증된 이메일 게이트가 없다** — 셀프 온보딩도 «회사 권한이 붙는 쓰기» 인데 770 은 손대지 않았다 | `onboarding-api.md:87`(«이메일 인증 강제 out of scope») · `FirstAdminProvisioner.java:67-121`(검사 없음) · 770 파일 `onboard` grep 0 · ADR-080 D3 «붙는 쓰기 전부» | 772 는 측면 있는 풀 계정에만 콘솔 토큰(§ 2 D-5) — 이 구멍을 **열지 않는다**. 773 이 044 D4 트러스트 게이트로 닫으며 연다(773 Scope 에 이미 있음). 수락 화면 = IdP(§ 2 D-4) |
| F10 | AC-5 «풀 가입 거절이 사라진다» 는 **코드가 아니라 데이터**다 — 거절은 «소비자 사이트에 그 이메일의 사이트 계정이 있다» 는 **일반 규칙**이고 운영자 전용 조회가 없다. 계정을 옮기면 저절로 걷힌다. 코드를 지우면 두 사이트 계정 · 소셜 연결 계정의 공존 금지까지 사라진다 | `ConsumerAccountPool.java:56-62`(«no operator-specific lookup») · `:73-82` · `multi-tenancy.md:455-458` | S6 은 이동만 한다. 소셜 신원이 있는 셀프 온보딩 운영자는 이동기가 건너뛰므로(`MoveCredentialToConsumerPoolUseCase.java:80-82` `SOCIAL_LINKED`, BE-617 결정) 그 사람의 거절은 **남는다** — 정적 0 이라 AC-5 는 비소셜 대상으로 판정, 남는 모집단은 기록 |
| F11 | 이동기의 «운영자 측면» 은 **두 축**이다 — `oidc_subject = 계정 id`(셀프 온보딩) **또는** `identity_id = 그 계정의 신원`(ADR-034 U3 링크 — 334 생성이 `resolveOrCreateIdentity` 로 붙인다). ADR D6 이 옮기라고 한 것은 앞 축뿐 | `OperatorFacetQueryUseCase.java:14-21` · `JpaAdminOperatorAdapter.java:133-141` · `CreateOperatorUseCase.java:152-159` · `MoveCredentialToConsumerPoolUseCase.java:87-96` | S6 은 `oidc_subject` 축만 건너뛰기에서 푼다(§ 2 D-6). 신원 축(소비자 사이트 테넌트에 334 로 만든 운영자 — 정적 0)은 그대로 건너뛴다 |
| F12 | 🔴 ADR D6 둘째 줄 «내부 프로비저닝 → 풀 초대» 를 문자 그대로 하면 **셀러 온보딩이 깨진다** — `POST /internal/tenants/{t}/accounts` 의 저장소 안 운영 호출자는 product-service 셀러 **기계 계정** 하나이고, 셀러 계정은 옮기지 않기로 소유자가 정했다. 사람 직원을 그 엔드포인트로 만드는 호출자는 저장소에 **없다** | `AccountServiceSellerProvisioner.java:199` · 호출자 grep(`internal/tenants/` src/main — 그 밖은 iam 내부 읽기) · `multi-tenancy.md:450`(2026-10-02 소유자 결정) · 078 D1(B2B 는 테넌트별 계정) · `account-internal-provisioning.md:4` | **HS-B · OD-2** |
| F13 | 파트너십 참여자는 **B 의 운영자 행**이다(FK `admin_operators`) — 계정에 직접 붙지 않는다. 인증 이메일 게이트는 그 운영자가 생길 때(초대 수락) 이미 물었다. 770 AC-5 노트의 «협력사 참여자 붙이기에서도 부른다» 는 구조상 새 위험을 막지 않는다 | `V0039__create_tenant_partnership_tables.sql:44-54` · 770 AC-5 `:140` | 참여자 붙이기에는 새 검사를 넣지 않는다(기록). AC-4 = 해지 → **다음 assume 발급** 거절(`TenantPartnershipPortImpl.java:37` ACTIVE 만 읽음 — ADR Context 위험 ③). 🔴 이미 발급된 assume 토큰은 만료(TTL)까지 산다 — «다음 요청» 은 «다음 assume · 재교환» 으로 읽고 잔여 창을 기록한다 |
| F14 | admin-service 에 **메일 발송 어댑터가 없다**(SMTP 는 account-service 인증 메일 · auth-service 재설정/2FA 통지뿐). 셀러 초대 선례는 메일 없이 **토큰을 초대자에게 한 번 돌려주고**, 상태는 `PENDING · ACCEPTED` 둘(취소 없음) | `SmtpEmailVerificationNotifier.java` · `SmtpEmailSender.java` · `SellerMemberService.java:75-98` · `V22__create_seller_members.sql:45` | 티켓 Scope 의 «취소 · 재발송» 은 선례보다 크다 → 상태 · 재발송 의미는 § 2 D-2, 전달 수단은 **OD-4** |
| F15 | 회사 운영자인데 **`iam` 테넌트 자격**에 묶인 사람들 — `demo-requester`(demo-corp) · `demo-viewer` · `demo-cs`(ecommerce) · `demo-operator`(SUPER_ADMIN, 홈 demo-corp). ADR 목표는 «플랫폼 관리자 빼고 전부 풀» 인데 D6 넷째 줄이 옮기라고 한 것은 **셀프 온보딩 운영자뿐** | admin `migration-dev` R__ 시드 4개 · ADR-080 D1(플랫폼 = `'*'`) · D6 `:146` | **OD-5** |
| F16 | 334 를 시연하려고 만든 시드가 있다 — `newhire@demo-corp.example`(demo-corp 사이트 계정, «아직 운영자 아님»). 334 를 걷으면 시연 대상이 사라진다. 콘솔 계약 § 3 패리티 표 12행 «operators: create» 도 다시 써야 한다 | account `migration-dev/R__07_seed_demo_corp_new_hire_account.sql:10-20, 61-68` · `console-integration-contract.md:3827` | S7: 인증된 이메일의 풀 계정 시드로 바꿔 «초대 → 수락» 을 시연하거나 걷는다. 패리티 행은 S1 |
| F17 | 771 과의 접점 — 풀 운영자의 콘솔 로그인도 `AuthorizeSecondFactorGate` 를 지난다(등록 계정이면 2단계). TOTP 는 `account_id` 키 → S6 이동 뒤에도 산다(771 § 5 «772 를 막지 않는 조건» ①). 교환 · assume 의 2단계 요구(역할 · 테넌트 정책)는 그대로 문다. 초대 수락이 인증된 이메일을 요구하므로 771 OD-4 의 «등록 전제 = 인증된 이메일» 도 수락한 사람에겐 이미 참 | 771 S2b · S4 기록 | 새 일 없음. 수락 자체에 `amr ∋ mfa` 를 요구할지는 § 2 D-3 (요구 안 함) |
| F18 | AC-6 의 «셀프 온보딩으로 만든 새 조직» 은 772 뒤에도 **새로** 만들 사람이 거의 없다 — 078 이후 새 가입은 전부 풀이고(F9), 풀은 773 전까지 온보딩을 못 한다 | 위 측정 2 · 3 | AC-6 = 시험(셀프 온보딩 픽스처 → 초대 → 수락)으로 판정, 라이브는 773 뒤 ⚪ |
| F19 | 774 접점 — D7 = E1 의 «연결을 누가 쓰나» 를 «초대 수락의 부산물» 로 고르면 수락이 그 자리다 | ADR-080 § ACCEPT 가 만든 새 의무 `:316` | 수락의 감사 행에 `(accountId, tenantId, operatorId)` 를 남겨 774 가 읽을 수 있게만 한다 — 772 에서 직원 id 를 받지 않는다 |

## 2. 구현자 결정 (ADR · R4 · 티켓이 «어떻게» 를 정하지 않은 것)

### D-1 초대가 사는 곳 — **admin-service**(`admin_db` 새 표 `operator_invitation`, 다음 번호 `V0050`)

| 기준 | **admin-service** | account-service |
|---|---|---|
| 수락이 만드는 것 | `admin_operators` 행 · 역할 바인딩 · 배정 — 전부 admin 평면. 같은 트랜잭션 | 수락 때마다 admin 에 쓰기 호출 — 두 DB 쓰기의 보상 설계가 필요 |
| ADR-024 D2 · D3 검사 | `TenantScopeGuard` · `RoleGrantGuard` 가 그 자리에 있다(`CreateOperatorUseCase.java:76-77, 115`) | admin 에 다시 물어야 한다 |
| 선례 | 셀러 초대도 **측면을 소유한 서비스**(product-service)에 산다 | — |
| 대가 | 이메일 · 인증 판정은 account-service 에 물어야 한다(F3) — 비 hot-path, 수락 1회 | — |

### D-2 R4 의 모양

- 토큰: 32바이트 `SecureRandom` → base64url, **SHA-256 hex 만 저장**(셀러 `SellerMemberService.java:187-199` 와 같은 계산) · `token_hash` UNIQUE.
- 만료: `expires_at = created_at + P7D`(설정 `admin.operator-invitation.ttl`, 기본 `P7D` — R4). **`EXPIRED` 는 읽을 때 판정**(스케줄러 없음 — 정리 잡을 만들지 않는다).
- 상태: `PENDING · ACCEPTED · CANCELLED`(셀러보다 하나 많다 — 티켓 «취소»). 1회용 = `UPDATE … SET status='ACCEPTED' WHERE id=? AND status='PENDING'`(영향 행 1 이 아니면 재사용 거절) + `@Version`.
- 재발송 = **같은 행에 새 토큰 · 새 만료**(옛 해시를 덮어써 옛 링크는 죽는다). 새 행 + 옛 행 취소는 «같은 이메일에 PENDING 하나» 를 깨기 쉽다.
- `(tenant_id, email)` 에 PENDING 은 하나(애플리케이션 검사 + 경합은 낙관적 락). 이미 그 테넌트의 운영자인 이메일은 초대 거절(`409`).
- 초대 대상 테넌트 `'*'` 거절(D1 — 플랫폼 관리자는 초대로 생기지 않는다).

### D-3 수락 술어 — 순서 · fail-closed · 거절이 아무것도 쓰지 않는다

1. 토큰 해시로 초대를 찾는다 — 없음 · `CANCELLED` → `404`(둘을 구별하지 않는다 — 열거 방지).
2. 만료 → `410`. 이미 `ACCEPTED` 이고 **같은 계정** → 첫 결과를 돌려준다(셀러 `:122-127` 선례), 다른 계정 → `409`.
3. 호출자 = **IdP 세션의 풀 계정**(로그인한 상태 — 요청 본문은 계정을 말하지 않는다). 사이트 계정 · `iam` 계정 → `403`.
4. 🔴 account-service 내부 판정(F3): 풀 계정인가 → **이메일 == 초대 이메일**(`403`) → **`VerifiedEmailRequirement.require`**(`403 EMAIL_NOT_VERIFIED`, 770 의 공용 이름). 이메일 불일치가 미인증보다 먼저(770 이 고른 순서와 같다).
5. 대상 테넌트 `ACTIVE` · 초대자 아직 `ACTIVE` · 초대된 역할이 **지금도** 초대자의 부여 메뉴 안(ADR-024 D3 재검 — 7일 사이 초대자 권한이 줄었을 수 있다).
6. `oidc_subject` 가 비어 있음(OD-1 (a) 이면 아니면 `409 OPERATOR_ALREADY_PROVISIONED`).
7. 한 admin 트랜잭션: 운영자 행(홈 = 초대 테넌트 · `password_hash` NULL · **`oidc_subject` = 계정 id** — F1) · 역할 바인딩(테넌트 = 초대 테넌트, D2) · 전체 테넌트 배정 · `ACCEPTED` · 감사 `OPERATOR_INVITATION_ACCEPT`. 신원 링크는 `FirstAdminProvisioner.java:107-112` 처럼 fail-soft.

- `amr ∋ mfa` 는 수락에서 요구하지 않는다 — 측면을 만드는 일이고, 진입 정책은 교환 · assume 에서 문다(771). 소유자가 원하면 한 줄.
- 🔴 AC-1 대조군은 770 모양 그대로: **같은 시험, 같은 초대**로 ① 미인증 계정 실패 ② 다른 이메일 계정 실패 ③ 만료 · 재사용 실패를 먼저 단언하고 ④ 인증된 본인만 성공.

### D-4 수락 화면의 자리 — **IdP(auth-service Thymeleaf)**

| 기준 | **IdP 화면** `/operator-invitations/accept` | 콘솔 화면 | admin 공개 엔드포인트 + 본문 `subjectToken` |
|---|---|---|---|
| «로그인한 상태» 를 무엇으로 아나 | IdP 브라우저 세션 principal(`/consent` · `/email-verification` · `/mfa/setup` 과 같은 자리) | 콘솔 토큰 — 🔴 측면 없는 풀 계정은 못 받는다(F9) | `platform-console-web` 토큰 필요 — 같은 이유로 불가 |
| 772 에서 가능한가 | ✅ | ❌ 모든 풀 계정에 콘솔 토큰을 줘야 함 = 773 + 온보딩 구멍(F9) | ❌ |
| 새 표면 | auth → admin 내부 `POST /internal/operator-invitations/accept`(워크로드 JWT, 기존 `/internal/**` 체인) | — | — |
| 수락 뒤 | 콘솔 로그인으로 보냄 — 이제 측면이 있으니 콘솔 토큰이 나온다(D-5) | — | — |
| 773 과 | 773 Edge Case «받은 초대» 는 콘솔 빈 화면에서 이 IdP 화면으로 **링크**만 하면 된다 | | |

### D-5 «운영자 측면 있는 풀 계정에 콘솔 토큰» — **발급자가 admin 에 묻는다, fail-closed**

| 기준 | **P1 측면 있는 풀 계정만**(권고) | P2 모든 풀 계정에 콘솔 토큰, 운영자 토큰은 교환이 막음 |
|---|---|---|
| ADR D6 셋째 줄 | 문장 그대로 | 넘어선다 — D9 = T1 의 셸 개방은 773 의 일 |
| 온보딩(044) | 계속 닫힘 — 인증 이메일 게이트 없는 구멍을 열지 않는다(F9) | 🔴 풀 계정 전부에 열림 — 773 의 044 D4 게이트 전에 |
| AC-2 술어 | 토큰 없음 + 교환 401 + 관리 API 401 | 교환 401 + 관리 API 401 |
| `sso_wrong_account`(PC-FE-324) | 측면 없음은 **그대로**(같은 문구 상수) | 사라지고 `/onboarding` 으로 감 |

- 판정 술어 = **교환과 같은 것**: `admin_operators.oidc_subject = 계정 id ∧ status = ACTIVE`(`TokenExchangeService.java:89-104`). 이동기의 «측면»(F11 — 두 축 · 상태 무관)과 **다른** 질문이라 새 내부 읽기를 둔다(가칭 `GET /internal/operators/console-eligibility?accountId=`, `auth-to-admin.md`). 상태 무관 술어를 재사용하면 퇴사자 · 정지자가 콘솔 토큰을 받고 교환 401 → `/onboarding` 으로 간다.
- 매핑(F4): `AuthorizationSessionTenant` 에 «풀 principal × 콘솔 client → `iam`» 갈래를 **풀-사이트 갈래와 따로** 둔다 — 게이트는 세션 테넌트 = client 테넌트로 `PASS`(재인증 고리 없음), refresh 미러 행도 `iam`. `poolPrincipalMapsTo` 는 그대로(`iam` 제외).
- 발급자: 커스터마이저의 풀 분기(`:419-430`)에 콘솔 갈래 — 적격이면 `tenant_id=iam` · `tenant_type` 은 기존 콘솔 토큰과 같게 · 역할 없음 · `amr` 그대로. 부적격이면 지금처럼 풀 값을 남겨 `refuseConsumerPoolTenant` 가 거절(문구 불변). 조회 실패 → **다른 고정 상수**의 `invalid_grant`(F5).
- refresh 마다 다시 묻는다(멤버십 615 와 같은 모양) — AC-3 퇴사 뒤 다음 refresh 부터 콘솔 토큰도 없다.
- `jwt-standard-claims.md` 새 클레임 없음 → `check-jwt-claims-registry.sh` 무관. `consumer-pool` 은 여전히 토큰에 나오지 않는다.

### D-6 셀프 온보딩 운영자 이동 — **같은 id 로**, 이동기의 `oidc_subject` 축만 푼다

| 안 | 결과 |
|---|---|
| **기존 이동기 재실행, `OPERATOR_FACETED` 의 `oidc_subject` 축만 해제**(권고) | 계정 id 불변 ⇒ `admin_operators.oidc_subject` 쓰기 0 · AC-5 «같은 `sub`» · TOTP(`account_id` 키) · 주문 · 팔로우 그대로. 재실행 가능 · 실패 시 무변경(`multi-tenancy.md:426-429`) |
| 새 풀 계정 + `oidc_subject` 재기록 | `sub` 가 바뀐다 — AC-5 위반 · 데이터 이동 필요. 기각 |
| 사람마다 수동 | 정적 0 이지만 라이브 모집단을 모른다. 기각 |

- 🔴 **S4(D-5)가 먼저 머지돼야 한다** — 옮긴 순간 그 사람의 세션은 풀 principal 이고, D-5 없이는 콘솔에서 `sso_wrong_account` 로 잠긴다.
- 거절 해제는 코드가 아니다(F10). 신원 축 · 소셜 연결은 계속 건너뛴다(F10 · F11).

### D-7 `POST /api/admin/operators` — **`'*'` 전용으로 좁힌다**(비-`'*'` → 초대로 안내하는 4xx)

- ADR D6 이 334 규칙을 «대체» 한다. 전제(가입 계정)만 지우고 생성을 남기면 334 가 닫은 «계정 없는 운영자» 가 다시 열린다. 남기는 `'*'` 는 D1(플랫폼 관리자 분리 · 부트스트랩).
- 잃는 것(실측): 회사 테넌트의 **break-glass 전용** 운영자를 새로 만들 수 없다. OIDC 쪽으로는 잃는 것 없음(F1). 기존 행 무변경.
- `excludePoolMembers` 파라미터와 콘솔 사전 게이트(`CreateOperatorAccountAdvisory`)는 호출자가 사라진다 — 같은 PR 에서 걷는다(죽은 계약을 남기지 않는다).

### D-8 AC-2 · AC-3 단언 자리

- AC-2: auth IT — 측면 없는 풀 principal 의 콘솔 authorize → 코드 교환 `invalid_grant`(토큰 없음) · admin — 그 `sub` 의 교환 401 · 운영자 토큰 없이 관리 API 401. 대조군: 같은 시험에서 측면 있는 풀 계정은 토큰을 받는다.
- AC-3: 기존 장치(`PATCH …/status` DISABLED) 뒤 → 콘솔 refresh `invalid_grant` · 교환 401 · **같은 계정의 스토어 · 팬 토큰은 그대로**(D5 — 계정 잠금 없음).

## 3. 슬라이스

머지 순서: **S1 → (S2 ∥ S4) → S3 → S5 → S6 → S7**. 🔴 S6 은 S4 뒤(D-6). S7 은 S5 뒤(콘솔이 비-`'*'` 생성을 더는 부르지 않아야 걷을 수 있다). S2 · S4 는 서비스가 달라 병렬 가능 — 단 둘 다 admin `internal/` 컨트롤러 · `AdminExceptionHandler` 를 건드리면 같은 파일 충돌이 나므로 **한 worktree 직렬**도 대안(CLAUDE.md § Shared-file task series).

| S | 무엇 (한 PR) | 서비스 | 이 슬라이스의 AC | 티켓 AC | 모델 |
|---|---|---|---|---|---|
| **S1** 계약 · 명세 (코드 0) | `admin-api.md` § 운영자 초대(발급 · 목록 · 취소 · 재발송, 권한 `operator.manage` · D2 · D3) · § `POST /operators` 를 `'*'` 전용으로 · `internal/auth-to-admin.md`(수락 · 콘솔 적격) · `internal/admin-to-account.md`(수락 판정 — 풀 · 이메일 · 인증, `excludePoolMembers` 퇴역) · `auth-api.md`(IdP 수락 화면 · 풀 운영자 콘솔 토큰 · 새 거절 상수) · admin `data-model.md`(`operator_invitation`) · `multi-tenancy.md` § 소비자 계정 풀 § 3 운영자 측면 표 · § 4 콘솔 행(`:467`, `:478-479`) · `:453`/`:455-458` · `account-maintenance-internal.md`(이동기 축) · `console-integration-contract.md` § 2.4.3 · § 2.6 · § 3 12행 · `platform/error-handling.md` + `rules/domains/saas.md`(새 코드) · ADR-035 § 8 · ADR-044 에 «080 D6 이 대체» **덧붙임**(본문 불변) · OD 답 반영 | 문서만 | ① HS-A · HS-B 처리가 계약에 보인다 ② 새 코드가 error-handling 에 먼저 등록 ③ 771 S1 처럼 rbac 무변경 확인(새 권한 키 없음) | AC-0 결정 고정 | **Opus** |
| **S2** 초대 발급 쪽 | admin `V0050 operator_invitation` · 발급(D2 · D3 · `'*'` 거절 · 중복 PENDING · 이미 운영자) · 목록 · 취소 · 재발송 · 감사 · (OD-4 답에 따라) 전달 + account-service 수락 판정 내부 엔드포인트(`VerifiedEmailRequirement` 호출) | admin · account | 범위 대조군(다른 테넌트 `403`) · 역할 상승 `403` · 해시만 저장(원문 0 단언) · 재발송 뒤 옛 토큰 무효 | — (기반) | **Opus** (권한 평면) |
| **S3** 수락 | auth IdP 화면 · auth → admin 수락 · admin 수락 유스케이스(D-3 1~7) | auth · admin | 🔴 **AC-1** 대조군(같은 시험, 실패 셋 먼저) · 동시 수락 1회만 · 재검(초대자 권한 축소 → 거절) · bite: 인증 술어 제거 → AC-1 의 미인증 칸만 빨강 | **AC-1 · AC-6**(시험) | **Opus** |
| **S4** 풀 운영자 콘솔 토큰 | admin 콘솔 적격 읽기 · auth 매핑(F4) · 발급자 갈래 · refresh 재질의 · 거절 상수 둘 | auth · admin | **AC-2**(닫힌 경로 먼저) · **AC-3** · 고리 없음 시험(풀 principal 콘솔 authorize 가 재인증으로 돌지 않는다) · 조회 실패 ≠ `'consumer-pool'` 문구 · 측면 없음 문구 바이트 불변 | **AC-2 · AC-3** | **Opus** (인증 경로) |
| **S5** 콘솔 | 운영자 화면 «등록» → «초대»(발급 · 대기 목록 · 취소 · 재발송) · 플랫폼(`'*'`) 생성만 남김 · 콜백에 «측면 조회 실패» 분기 · 원장 사본(permission-map · iam-guide · sample coverage) | console-web | 401 만 «운영자 아님»(771 S3 술어 불변) · `sso_wrong_account` 회귀 불변 · e2e 디렉터리 grep + 머지 뒤 nightly 확인 | — | **Opus** (콜백 분기) — 화면만 떼면 Sonnet |
| **S6** 셀프 온보딩 운영자 이동 | 이동기 `oidc_subject` 축 해제 · 신원 축 유지 · 재실행 | auth · admin(facet 응답에 축) · account | **AC-5**: 이동 전 «사이트 계정 + 풀 가입 거절» 단언 → 이동 → 같은 `sub` 로 콘솔 토큰 · 교환 성공 · 그 이메일 풀 가입 거절 없음 · 소셜 연결 운영자는 그대로(대조군) | **AC-5** | **Opus** (데이터 이동) |
| **S7** 334 퇴역 · 시드 · 회수 | `CreateOperatorUseCase` 비-`'*'` 거절 · `excludePoolMembers` 경로 정리 · `R__07` 대체(F16) · AC-4 IT(파트너십 해지 → 다음 assume 거절, 풀 계정 직원) | admin · account · (seed) | 비-`'*'` 생성 거절 · `'*'` 생성 불변(대조군) · AC-4 | **AC-4** | **Sonnet** (기계적 퇴역 · 시드) — AC-4 IT 는 Opus 검토 |

- **773 을 막지 않는 조건**: ① 초대 API 가 «운영자 0명인 테넌트» · 초대자 `SUPER_ADMIN`(`'*'`) 을 받는다(773 AC-4 의 «관리자 대기» 첫 관리자 초대). ② 콘솔 적격 판정이 **한 지점**(D-5)이라 773 이 «비운영자 셸» 로 넓힐 때 그 지점만 바꾼다 — 거절 상수 둘은 그대로 둔다. ③ 🔴 **OD-1 을 773 착수 전에 정한다** — 773 AC-3(두 번째 회사)이 F2 벽에 그대로 부딪힌다. ④ 셀프 온보딩의 인증 이메일 게이트는 773(044 D4) 몫 — 772 는 그 구멍을 열지 않는다(F9). ⑤ `IamOidcSubjectTokenValidator` · `OnboardingController.java:57` 은 772 에서 안 바꾼다.

## 4. 소유자 결정 (OD) — ADR-080 · R4 · 티켓이 정하지 **않은** 것

| OD | 질문 | 왜 ADR/티켓이 답하지 않나 | 권고 (구현자 선호 — 소유자 결정 아님) | 대안 · 결과 |
|---|---|---|---|---|
| **OD-1** (HS-A) | 한 사람 · 여러 회사의 운영자 모델 | ADR D5 는 «회사 소속 = `admin_operators.tenant_id`» 라 쓰고 Edge Case 1 은 «테넌트마다 측면» 을 전제하는데, 코드는 `sub` 하나에 운영자 행 하나만 허용한다(F2) | **(a) 772 는 한 사람 · 한 회사** — 두 번째 수락은 `409 OPERATOR_ALREADY_PROVISIONED`, Edge Case 1 은 «미충족 · 기록». 다회사 모델은 **773 착수 전** ADR(080 개정 또는 새 ADR)로 정한다. 이유: 어느 쪽이든 교환 · RBAC 평가의 뼈대를 바꾸는 결정이고, 772 의 AC 는 이것 없이 닫힌다 | (b) 한 운영자 행 + 둘째 회사부터 배정 · 회사별 역할 — 역할 바인딩 PK 에 `tenant_id`(마이그레이션), D5 문장 개정, 홈 회사 퇴사 처리 규칙 필요 / (c) 회사마다 행 — `oidc_subject` 유니크를 `(tenant_id, oidc_subject)` 로, 교환이 회사를 골라야 함 = ADR-014 D3 개정 · 콘솔 로그인 흐름 변경(가장 큼). ⚠️ (a) 를 고르면 773 AC-3 은 그 ADR 까지 막힌다 |
| **OD-2** (HS-B) | «내부 프로비저닝 → 풀 초대» 를 어떻게 읽나 | ADR 문장은 «회사 테넌트에 사이트 계정을 만드는 길» 을 바꾸라지만, 그 엔드포인트의 유일한 운영 호출자는 셀러 **기계** 계정이고(옮기지 않기로 결정됨) 사람 직원 호출자는 없다(F12) | **엔드포인트는 그대로 두고 계약에 «사람 직원의 입구는 초대 하나 — 이 엔드포인트는 기계 · 시스템 계정 전용» 을 적는다**(S1). 코드 변경 0 | (b) 문자 그대로 — 사이트 계정 생성 금지 → 셀러 온보딩 · B2B 시스템 계정이 깨진다(078 D1 · 2026-10-02 결정과 충돌) / (c) «사람» 판별 플래그를 요청에 추가 — 호출자 자기 신고라 막는 힘이 없다 |
| **OD-3** | 풀 계정이 없는 피초대자의 가입 경로(Edge Case 2) | 티켓은 «가입 → 인증 → 수락으로 안내» 만. 풀 가입은 소비자 사이트에서만 생기고 그 사이트 멤버십을 같이 만든다(F8) | **IdP 수락 화면에서 «사이트 없는 풀 가입»** — 풀 계정만, 멤버십 · `account.created` 없음(소비자 사이트는 첫 방문 동의 때 지금처럼). 직원이 팬 · 스토어 회원이 되지 않는다. `multi-tenancy.md` § 2 에 한 갈래 추가(가산) | (b) 스토어/팬 가입으로 보냄 — 코드 최소, 대가 = 원치 않는 사이트 멤버십 · FE-108 경로 / (c) «먼저 스토어/팬에 가입하세요» 안내만 — 같은 대가 + 사용자가 길을 잃는다 |
| **OD-4** | 초대 전달 수단 · «재발송» 의 뜻 | R4 는 «셀러와 같은 모양» 인데 셀러는 메일 없이 토큰을 초대자에게 한 번 돌려준다. 티켓은 «재발송» 을 적는다. admin-service 엔 발송기가 없다(F14) | **메일 — account-service 의 770 발송기를 내부 호출로 재사용**(분류기 · Mailpit 데모 경로 · 실패 표기 규칙 그대로 — ADR-080 «발송이 새 장애 지점»). 발송 실패여도 초대 행은 남기고 화면이 «보내지 못함 · 다시 보내기» 를 보인다. 링크는 초대자에게 돌려주지 않는다 | (b) 셀러처럼 링크를 초대자에게 한 번 보여 주고 메일 없음 — 수락에 로그인 · 인증 이메일이 필요해 링크 유출 위험은 낮다, 대신 «재발송» = 새 링크 복사 / (c) 둘 다 — 표면이 가장 큼. ⚠️ admin-service 에 SMTP 를 새로 두는 안은 권하지 않는다(발송기 셋째 사본 · 공용 모듈은 ADR 필요 — 770 기록 `:134`) |
| **OD-5** | 회사 운영자인데 `iam` 자격에 묶인 사람(F15)도 풀로 옮기나 | ADR 목표는 «플랫폼 관리자 빼고 전부», D6 이 옮기라고 한 것은 셀프 온보딩 운영자뿐 | **772 에서 옮기지 않는다** — 정적 모집단은 데모 시드 4명이고, `demo@demo.com` 은 같은 이메일의 풀 계정이 이미 있어(F15 · 078 데모 시드) 옮기면 공존 · 병합 문제가 된다. 후속 티켓 기안 여부만 정한다 | (b) 772 에서 함께 — `iam` → 풀 이동 경로 신설(이동기는 소비자 사이트 전용), 데모 운영자 로그인이 바뀜 / (c) 영구히 `iam` 유지 — «전부 풀» 목표와 어긋남을 ADR 에 기록 |
| **OD-6** (경미) | 퇴사자 · 정지 운영자가 콘솔에서 보는 문구(F6) | 문구는 ADR 범위 밖 | **772 에선 그대로**(`sso_wrong_account`) · 773 의 비운영자 셸이 이 거절을 없애므로 거기서 정리 | 772 에서 «운영자 권한이 없습니다» 전용 문구 — 거절 상수 하나 더 · 콘솔 분기 하나 더 |

## 5. HARDSTOP 성격 노트

| ID | 무엇 | 어디 | 왜 막나 | 해소 |
|---|---|---|---|---|
| **HS-A** (HARDSTOP-09 성격 — 명세에 없는 아키텍처 결정) | 다회사 운영자 모델 | Edge Case 1 · ADR-080 D5 ↔ `V0027` · `V0004:48` · `TokenExchangeService.java:89-96` | 772 Edge Case 1 과 773 AC-3 이 지금 스키마로 불가능 | OD-1. (a) 면 772 는 막히지 않고 773 이 그 ADR 에 묶인다 |
| **HS-B** (HARDSTOP-06 성격 — 명세 충돌) | 내부 프로비저닝 문장 | ADR-080 D6 `:144` ↔ `multi-tenancy.md:450`(2026-10-02 소유자 결정) · 078 D1 · `AccountServiceSellerProvisioner.java:199` | 문자 그대로 구현하면 셀러 온보딩이 깨진다 | OD-2 → S1 계약 문장 |
| **HS-C** (계약 부재) | 초대 · 수락 · 콘솔 적격 · 수락 판정 · 새 거절 코드 | `admin-api.md` · `internal/*.md` · `auth-api.md` · `error-handling.md` | 계약 먼저(CLAUDE.md Layer Rules) | S1. **S1 머지 전 S2~S7 착수 금지** |
| — (충돌 아님) | ADR D6 셋째 줄 «셀프 온보딩이 열린다 — 측면 있는 계정만» 의 자기모순 | ADR-080 D9 2번 | D9 = T1 이 773 으로 풀었다 — 772 는 측면 있는 계정의 콘솔 토큰만(D-5), 온보딩 개방은 773 | 없음(기록) |

## 소유자 결정 확정 (2026-10-09 UTC)

| OD | 결정 | 귀결 |
|---|---|---|
| OD-1 (HS-A) | **772 는 한 사람 = 한 회사** — 두 번째 회사 수락은 `409` | 772 는 막히지 않는다. 🔴 다회사 운영자 모델은 **773 착수 전 ADR** 로 정한다(773 AC-3 이 거기에 묶인다) |
| OD-2 (HS-B) | **엔드포인트 유지 · 기계(시스템) 계정 전용으로 계약에 명시** — 사람 운영자는 초대로만 | 셀러 온보딩 무영향. S1 이 ADR-080 D6 문장의 해석을 계약에 적는다 |
| OD-3 · OD-4 | **IdP 수락 화면에서 사이트 없는 풀 가입 + 이메일 발송**(770 발송기 재사용 · 발송 실패는 화면에 · 재발송 = 같은 초대에 새 토큰) | 피초대자가 스토어 · 팬 멤버가 되는 부산물 없음 |
| OD-5 · OD-6 | **둘 다 772 에서 손대지 않는다** — `iam` 자격에 묶인 회사 운영자 4명 이동은 후속 여부를 따로 · 퇴사/정지 문구는 773 비운영자 셸에서 | 772 범위 = 셀프 온보딩 운영자 이동만 |

- AC-0 ✅ — 네 측정 · F1~F19 · 구현자 결정 D-1~D-8 · 슬라이스 S1~S7 · OD 확정. 🔵 정적 재측정 «사이트 계정으로 남은 셀프 온보딩 운영자 = 0» 은 라이브 ⚪(다음 데모 창 질의는 위 기록에).
