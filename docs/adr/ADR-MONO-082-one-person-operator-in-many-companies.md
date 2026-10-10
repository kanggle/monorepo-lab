# ADR-MONO-082 — 한 사람 · 여러 회사의 운영자: **운영자 행 하나에 회사별 소속**인가, **회사마다 행**인가

**Status:** ACCEPTED
**Date:** 2026-10-10 (PROPOSED · 같은 날 ACCEPTED — 갈래 b)
**주관 티켓:** `TASK-MONO-783`
**Decision driver:** 소유자 결정 `TASK-MONO-772` OD-1(2026-10-09 UTC, «772 는 한 사람 = 한 회사 (추천)») — 그 결정이 «다회사 운영자 모델은 **`TASK-MONO-773` 착수 전 ADR** 로 정한다» 를 함께 적었다(772 § 소유자 결정 확정 · `admin-api.md:1658` · `multi-tenancy.md:635`). `TASK-MONO-773` AC-3(«이미 운영자인 계정이 두 번째 회사를 생성») 이 이 결정에 묶인다.
**Supersedes:** 없음
**Related:** [`ADR-MONO-080`](ADR-MONO-080-workforce-on-the-consumer-pool.md) (A — D1 플랫폼 관리자 분리 · D5 회수 = 측면만 · D6 초대 → 인증된 본인 수락 · D9 = T1 «두 번째 회사») · [`ADR-MONO-020`](ADR-MONO-020-operator-multitenant-assignment.md) (D1 `operator_tenant_assignment` · D2 assume-tenant · 🔴 D5 «배정마다 권한 세트» — ACCEPTED, 평가에 미구현) · [`ADR-MONO-014`](ADR-MONO-014-platform-console-operator-auth-token-exchange.md) (D3 «결정적 · 운영자 행이 권위») · [`ADR-MONO-024`](ADR-MONO-024-tenant-admin-delegation.md) (D2 관리 범위 = 역할 행의 `tenant_id`) · [`ADR-MONO-035`](ADR-MONO-035-operator-auth-unification-model.md) (O1 — assume 토큰의 역할은 구독에서 파생) · [`ADR-MONO-044`](ADR-MONO-044-self-service-tenant-onboarding.md) (D2 감금 · D4 신원당 상한 · D5 측면 · D7 «한 신원 여러 조직» 미룸) · [`ADR-MONO-045`](ADR-MONO-045-cross-org-partner-delegation.md) (D6 관계 단위 회수 · D7 «한 계정에 측면 여럿») · [`ADR-MONO-046`](ADR-MONO-046-operator-group-model.md) (그룹 멤버 = 그 테넌트가 홈인 운영자) · [`ADR-MONO-060`](ADR-MONO-060-assumed-token-subject-identity.md) (A — assume 토큰 `sub` = 계정 UUID) · `TASK-MONO-772`(F2 · OD-1 · S1-11 · S6) · `TASK-MONO-773`

> 🟢 **ACCEPTED — 갈래 b** (2026-10-10 UTC, 소유자 정확형 `ADR-MONO-082 ACCEPTED — b`). 이름 · `ACCEPTED` · 갈래 letter 세 요건이 한 문장에 도착했다.
> 🔴 **라이더 뒤집기는 공급되지 않았다** — R1~R4 는 구현자 기본값 그대로다(§ 라이더 대조). 단계 티켓은 이 ACCEPT PR 안에서 기안했다(§ ACCEPT 가 만든 새 의무). 아래 PROPOSED 시점 본문은 **바이트 그대로** — ACCEPT 는 확정이지 재결정이 아니다.
> (PROPOSED 시점 머리말) 🟡 **PROPOSED** — 소유자 ACCEPT 는 정확형 **`ADR-MONO-082 ACCEPTED — <a|b|c>`** 로만 넘어간다(§ 수락 형식). «진행» · «좋아» · 갈래 letter 만 있는 문장 · 추천과 같은 선택창 응답은 수락이 아니다.
> 이 문서는 구현을 인가하지 않는다. 단계 티켓은 ACCEPT PR 안에서 기안한다(ADR-080 § ACCEPT 가 만든 새 의무와 같은 모양).

---

## Context

### 무엇을 정하나

ADR-080 은 GitHub · Slack 의 «개인 계정 + 조직 소속» 모양을 골랐고(Context § 무엇을 원하나), D5 는 회사 소속을 «`admin_operators.tenant_id`» 에 둔다고 적었다. 772 Edge Case 1 은 «같은 사람이 여러 회사에 초대 — 운영자 측면은 테넌트마다» 를 전제했다. 772 AC-0 F2 가 그 전제를 지금 스키마가 담지 못한다는 것을 찾았고, 소유자는 772 를 «한 사람 = 한 회사» 로 닫고 이 질문을 여기로 넘겼다.

🔴 이 질문은 스키마 질문이 아니다(티켓 Failure Scenario 1). 한 사람이 회사 A · B 의 운영자일 때 **«지금 어느 회사로?»** 를 답해야 하는 자리가 셋 있다 — ① 콘솔 로그인의 토큰 교환(어느 운영자 행) ② assume-tenant(어느 회사의 도메인 토큰) ③ 관리 API 의 권한 평가(A 의 `TENANT_ADMIN` 이 B 에서도 통하는가). 갈래는 이 셋 중 **어디를 바꾸는가**로 갈린다.

### 지금 어떻게 생겼나 (실측 — 코드, 2026-10-10 UTC, 772 F2 를 복사하지 않고 다시 쟀다 — AC-1)

#### ① 772 F2 의 세 벽 — 셋 다 그대로다

| 잰 것 | 결과 | 출처 |
|---|---|---|
| `oidc_subject` 유니크 | **플랫폼 전역** `UNIQUE (oidc_subject)` — 위치 변경 없음(V0027). 주석이 근거를 적는다: *"OIDC subject space is tenant-independent"* | `V0027__add_oidc_subject_to_admin_operators.sql:60-80`(인덱스 `:74`) · `data-model.md:33, 46` · 엔티티 주석 `AdminOperatorJpaEntity.java:65` |
| 교환이 행을 찾는 법 | `sub` **하나로 한 행** — `Optional` 단일값. 테넌트 입력 없음 | `TokenExchangeService.java:89-96` → `OperatorOidcSubjectResolver.java:45-50` → `AdminOperatorJpaRepository.java:41` |
| 역할 바인딩 PK | `(operator_id, role_id)` — 같은 역할을 두 테넌트에 줄 수 없다. 이후 V0007 · V0025 · V0042 · V0044 가 열을 더했지만 PK 는 그대로 | `V0004__create_admin_rbac_tables.sql:48` · `data-model.md:146` |
| 🔴 F2 가 적지 않은 넷째 벽 — **역할 행의 `tenant_id` = 운영자 홈** 불변식 | 역할 행의 `tenant_id` 는 «바인딩된 운영자의 `tenant_id` 를 그대로 미러링» 해야 한다(WI-2). PK 를 넓혀도 이 불변식이 «B 의 `TENANT_ADMIN`» 행을 금지한다 | `V0026__backfill_admin_operator_roles_tenant_id.sql:20-23` · `data-model.md:138, 142` · `PatchOperatorRoleUseCase.java:58-62` |
| 첫 관리자 프로비저너 | `oidc_subject` 쓰기는 «first-time onboarder 에게만 비어 있다» 는 전제 위에 있다 | `FirstAdminProvisioner.java:87-95` |

#### ② 772 가 그 벽 위에 둔 두 409 — 술어 하나, 자리 둘

| 자리 | 술어 | 출처 |
|---|---|---|
| 초대 수락(OD-1) | `oidcSubjectResolver.resolve(account).isPresent()` → `409 OPERATOR_ALREADY_PROVISIONED`(상태 무관). 경합은 UNIQUE 위반을 다시 읽어 같은 409 | `OperatorInvitationAcceptanceUseCase.java:127-130, 260-262` · 계약 `auth-to-admin.md:234, 267` · `admin-api.md:1658` |
| 셀프 온보딩(S1-11) | 같은 술어 · 같은 코드, **테넌트 생성 전** | `SelfServiceOnboardingUseCase.java:55-63` · 계약 `onboarding-api.md:80, 93` |
| CI 단언 | AC-6 IT 가 «두 번째 회사 수락 409 · 셀프 온보딩 409» 를 단언 — 이 ADR 이 무엇을 고르든 **그 칸이 바뀐다** | 772 § AC 판정 기록(`#4283` iam A «🔴 AC-6») |

772 S6 은 셀프 온보딩 운영자(`oidc_subject` 축)를 풀로 옮겼다 — 이동기는 facet 에 `axes=IDENTITY` 를 보내 `oidc_subject` 축을 건너뛰기에서 푼다(772 § S6 기록 · `auth-to-admin.md` § facet). 그래서 지금 «풀 계정 하나 = 운영자 행 하나» 가 셀프 온보딩 운영자에게도 참이다.

#### ③ 🔴 이미 있는 것 — **운영자 행 하나가 여러 테넌트에서 일하는 기전**(ADR-020 배정)

티켓이 적지 않은 사실: «행 하나 · 테넌트 여럿» 은 **이미 구현돼 있다**. 무엇을 주는지가 갈래의 크기를 정한다.

| 평면 | 배정(`operator_tenant_assignment`)이 주는 것 | 출처 |
|---|---|---|
| 저장 | `(operator_id, tenant_id)` PK · `permission_set_id`(NULL = «운영자 수준 역할 상속») · `org_scope` | `V0030__create_operator_tenant_assignment.sql:16-37` · `V0031…sql:28-29` |
| 도달(assume) | 실효 범위 = **배정 ∪ {홈}**. 셀프 온보딩도 자기 배정 행을 만든다 | `OperatorAssignmentCheckUseCase.java:184-193` · `FirstAdminProvisioner.java:114-116` |
| **도메인 역할** | 🔴 **회사와 무관하게 같다** — assume 토큰의 `roles` 는 선택한 테넌트의 ACTIVE 구독에서 파생되고 *"never admin_operator_roles"* | `TenantClaimTokenCustomizer.java:1010-1040` · `OperatorRoleDerivation.java:75-125` · ADR-035 O1 |
| `permission_set_id` | 🔴 **평가에서 읽는 곳 0** — main 코드의 참조는 엔티티 · DTO · 응답 매핑뿐. 시드가 이것을 명시한다: *"permission_set_id is not read on the assume path"* | `permission_set_id\|permissionSetId` grep(iam `apps/*/src/main`) · `R__seed_demo_cs_operator.sql:52-58` · ADR-035 O1 표 B행(*"`permission_set_id` is currently always-NULL/unused"*) |
| 관리 평면 **읽기** | 권한 판정은 **테넌트 무관**(운영자의 권한 집합 전체) × 읽기 게이트는 **배정 ∪ 홈** ⇒ 홈에서 받은 역할이 배정 테넌트 읽기에도 통한다(V0030 의 «상속» 그대로) | `PermissionEvaluatorImpl.java:39-45` · `QueryTenantScopeGate.java:22-24, 66-81` |
| 관리 평면 **쓰기** | 역할 행 `tenant_id` 의 집합만 — 배정은 **아무것도 주지 않는다**(같은 클래스 주석의 예: *"assigned to operate {acme, globex} but granted `operator.manage` only @ acme must be denied administering globex"*) | `AdminGrantScopeEvaluator.java:19-27, 80-114, 209-215` |
| 콘솔 테넌트 스위처 | 레지스트리의 테넌트 목록 = 실효 범위(배정 ∪ 홈) ∩ 구독 ∩ ACTIVE ⇒ **배정된 테넌트가 이미 스위처에 뜬다**. 스위처는 그 목록을 그대로 그린다 | `ConsoleRegistryUseCase.java:79-101` · `(console)/layout.tsx:200-205` · `TenantSwitcher.tsx:60-137` |
| 배정 회수 | B 의 관리자가 B 배정을 지운다 — 판정은 **대상 테넌트** 범위(운영자 홈이 아니다) | `ManageOperatorAssignmentUseCase.java:103-120` |
| 시드에 이미 있는 모양 | `demo-assigned-only` — 홈 `ecommerce` + `demo-corp` 배정(로그인 불가 · 역할 0, 그룹 화면 «배정만 됨» 시연용) | `R__seed_demo_assigned_only_operator.sql:18-28, 45-60` |

⇒ **도메인 평면과 스위처는 이미 다회사다.** 빠진 것은 관리 평면 하나다 — B 에서의 **회사별 역할**(배정 = «상속» 또는 «0», 둘 중 «B 의 `TENANT_ADMIN`» 은 없다). ADR-020 D5 는 바로 이것(*"SUPPORT_READONLY for customer A and a SECURITY_ANALYST for customer B"*)을 **ACCEPTED 로 골라 두었고**(`ADR-MONO-020…md:83-88`), 저장 칸(`permission_set_id`)만 만들고 평가에 잇지 않았다. ADR-035 O1 은 도메인 평면에서 그 칸을 «deferred refinement» 로 미뤘다(`ADR-MONO-035…md:90`).
🔵 계약 문장 하나는 구현보다 좁다: `console-registry-api.md:282-283` 은 *"a single tenant slug ⇒ only that slug"* 라고 적지만 구현은 배정 ∪ 홈이다(위 표). 이 ADR 은 그 문장을 고치지 않는다 — 어느 갈래든 단계 티켓의 계약 정정 항목이다.

#### ④ «홈 테넌트» 를 읽는 곳 — 갈래 b 가 건드릴 모집단

`admin_operators.tenant_id`(홈)는 «회사 소속» 외에도 여러 판정의 입력이다. 코드 읽기로 센 것(전수 아님 — ⚪ 표의 마지막 행):

| 판정 | 홈을 어떻게 쓰나 | 출처 |
|---|---|---|
| 운영자 정지 | 행위자가 **대상의 홈**에 `operator.manage` 를 가져야 · 정지는 **행 전체**(리프레시 토큰 전부 회수) | `PatchOperatorStatusUseCase.java:49-67` |
| 역할 변경 | 같은 홈 판정 + 🔴 **역할을 전부 지우고 다시 쓴다**(`deleteOperatorRoles`) — 테넌트 축이 없다 | `PatchOperatorRoleUseCase.java:37-64` |
| 협력사 도달 | 파트너십 조회가 `partner = operator.tenantId()`(홈) | `OperatorAssignmentCheckUseCase.java:244-246` · 참여자 FK 주석 *"home tenant == partner"* `V0039…sql:46` |
| 그룹 멤버 | 멤버의 홈 == 그룹 테넌트(`422 GROUP_MEMBER_TENANT_MISMATCH`) | `data-model.md:182, 191` |
| 2단계 요구 | «홈 ∪ 배정» 중 정책 켜진 테넌트 | `TokenExchangeService.java:106-115` |
| 읽기 게이트 기본값 · 감사 라우팅 | 요청 테넌트가 없으면 홈 · `admin_actions.tenant_id` 의 출처 | `QueryTenantScopeGate.java:71-73` · `V0025__add_tenant_id_to_admin_tables.sql:44-55` |
| ⚪ 그 밖 | `UpdateOperatorProfileUseCase` · `OperatorQueryService` · `OperatorInvitationUseCase` 가 실효 범위를 읽는다(`resolveEffectiveTenantScope` 호출 8곳). 감사기가 지금 `admin_actions.tenant_id` 를 어디서 채우는지는 **열지 않았다** | `resolveEffectiveTenantScope` grep(admin `src/main`) |

#### ⑤ Edge Case 2 — 플랫폼 관리자이면서 고객사 운영자

| 잰 것 | 결과 | 출처 |
|---|---|---|
| 플랫폼 운영자 행의 링크 키 | `iam` 테넌트 **자격 계정**의 id(`'*'` 행 · 데모 `demo@demo.com`) | 772 F15 · `R__seed_demo_operator.sql`(772 AC-0 측정 4 인용) |
| 고객사 운영자 행의 링크 키 | **풀 계정** id(초대 수락은 풀 계정만 — 사이트 · `iam` 계정은 `403`) | 772 S1-3 · `OperatorInvitationAcceptanceUseCase.java:121-122` |
| 같은 이메일이 둘 다일 때 | 두 **다른 계정**(`iam` 자격 + 풀) → 두 다른 `sub` → OD-1 술어가 서로를 보지 않는다. 콘솔은 BE-610 재인증으로 어느 자격으로 들어왔는지를 가른다 | 772 S1-1 · S1-7 · `CredentialAuthenticationProviderInvitationTest`(772 S3 기록) |
| `'*'` 로 가는 초대 | 거절(D1 — 플랫폼 관리자는 초대로 생기지 않는다) | 772 D-2 마지막 줄 |

⇒ «두 모자» 는 지금도 **두 계정 · 두 행**이고, 다회사 질문은 **한 계정 안**의 질문이다. ADR-080 D1 과 충돌하는 지점은 «다회사 기전이 `'*'` 행이나 `iam` 계정에 회사 소속을 붙일 수 있게 되는가» 하나다(D2).

---

## Decision

D1 · D2 · D3 · D5 는 세 갈래 모두에서 참이다. D4 는 b · c 에서 참이다. D6 은 773 AC-3 이 갈래마다 어떻게 닫히는지를 적는다(AC-4). **갈래(a/b/c)는 정하지 않는다** — 수락 문장이 정한다.

### D1 — 토큰 모양은 바꾸지 않는다: 한 토큰 = 한 회사

- 어느 갈래에서도 토큰은 `tenant_id` **단일값**이고(`jwt-standard-claims.md:67`), assume 토큰 `sub` = 계정 UUID(ADR-060 A)다. 두 번째 테넌트 클레임 · `tenant_id` 목록은 만들지 않는다(ADR-020 D3-B 기각과 같은 이유 — A 를 보는 토큰으로 B 에 닿는다).
- 따라서 «어느 회사로» 는 언제나 **발급 시점의 선택**이고, 갈래는 그 선택을 **교환에서 하나(c) · assume 에서 하나(지금 · b)** 로 갈린다.

### D2 — 🔴 플랫폼 관리자 분리(ADR-080 D1)는 다회사 기전보다 앞선다

- `tenant_id = '*'` 행은 회사 소속(역할 행 · 배정 · 새 행)을 **받지 않는다**. 고객사 운영자 측면(풀 계정)은 `'*'` 를 **받지 않는다**. 위반은 쓰기에서 거절 — 지금 `'*'` 초대 거절(772 D-2)과 같은 자리의 규칙이다.
- 실측(⑤)대로 «플랫폼 관리자 + 고객사 운영자» 는 지금 두 계정이고 그대로 둔다. 다회사 기전이 그 둘을 한 행으로 합치는 경로가 되면 D1 이 풀린다 — 그래서 이 줄은 갈래와 무관하게 고정이다.
- 🔵 `iam` 계정으로 셀프 온보딩하는 사람(콘솔 토큰을 받으므로 가능)이 이미 `'*'` 행을 가지면 지금은 S1-11 409 다(772 S1-11 «기존 `iam` 운영자도 같다»). b · c 에서도 **이 409 는 남는다**(D2 의 쓰기 거절).

### D3 — 한 회사를 떠나는 것은 그 회사 측면만 지운다 (ADR-080 D5 의 확장)

- 티켓 Edge Case 1 의 술어 그대로: A 에서 퇴사 → **B 의 역할 · 배정 · 콘솔 진입 · 도메인 토큰이 그대로**. 계정은 잠그지 않는다(080 D5).
- 갈래마다 기전이 다르다(§ 갈래 비교 «퇴사» 행). 어느 갈래든 **대조군 시험**은 같다: A 회수 → A assume 거절 · A 관리 API 거절 **그리고 같은 시험에서** B assume · B 관리 API 성공.

### D4 — (b · c) 두 번째 회사의 쓰기는 **새 테넌트에만** (ADR-044 D2 그대로)

- 셀프 온보딩 · 773 «테넌트 생성» 이 이미 운영자인 사람에게 만드는 행은 전부 `tenant_id = 방금 만든 테넌트` 다 — 기존 회사의 역할 행을 **읽지도 고치지도 않는다**(773 AC-3 «기존 테넌트 권한 불변» · 773 Failure Scenario 2).
- 신원당 소유 상한(044 D4)의 **세는 단위**: b = 그 운영자 행의 `TENANT_ADMIN` 역할 행이 가리키는 서로 다른 테넌트 수 · c = 같은 `oidc_subject` 의 행 중 `TENANT_ADMIN` 을 가진 행 수. 상한 **값**은 773 AC-0 의 몫이다.

### D5 — 도메인 평면은 건드리지 않는다

- assume 토큰의 `roles` 는 선택한 테넌트의 구독에서 파생된다(ADR-035 O1, 위 ③) — 어느 갈래도 이것을 회사별 역할로 바꾸지 않는다. 회사별 역할은 **관리 평면**(`admin_operator_roles` · `/api/admin/**`)의 이야기다.
- ADR-020 D5 의 «배정마다 권한 세트» 를 도메인 평면에 들이는 것은 이 ADR 의 범위 밖이다(035 O1 B행 «deferred refinement» 그대로).

### D6 — 773 AC-3 이 갈래마다 닫히는 법 (AC-4)

| 갈래 | 773 AC-3 «이미 운영자인 계정이 두 번째 회사를 생성» |
|---|---|
| **a** | AC-3 을 «거절» 로 고쳐 닫는다 — 이미 측면 있는 계정의 생성 → `409 OPERATOR_ALREADY_PROVISIONED`, 테넌트 생성 0(S1-11 술어 재사용). 773 Goal 표 둘째 줄(«이미 운영자 → 본인 · 두 번째 회사»)은 지운다 |
| **b** | AC-3 문장 그대로 닫는다 — 기존 운영자 행에 새 테넌트의 `TENANT_ADMIN` · `TENANT_BILLING_ADMIN` 역할 행 + 배정을 더한다. 🔴 단언: 기존 회사의 역할 행 **바이트 불변** · 새 회사에서 기존 회사 권한이 통하지 않음(대조군) · 상한 초과 거절 |
| **c** | AC-3 문장 그대로 닫되 **콘솔 흐름이 하나 더 붙는다** — 같은 `oidc_subject` 로 새 운영자 행(홈 = 새 테넌트). 기존 행은 구조적으로 불변. 🔴 생성 직후 «재교환» 이 **어느 행으로** 가는지(회사 선택)를 773 이 함께 구현해야 닫힌다 |

---

## 갈래 — ACCEPT 단위

| 갈래 | 무엇 | 한 줄 |
|---|---|---|
| **a** | 현 상태 유지: 한 계정 = 운영자 행 하나 = 회사 하나 (772 OD-1 의 영구화) | 대조군. 두 409 가 정책이 된다 |
| **b** | 운영자 행 하나 + **회사별 소속 · 역할** — 역할 행에 테넌트 축, 배정 = 소속, 홈은 판정에서 물러난다 | ADR-020 D5 의 관리 평면 완성. 교환 · assume · 스위처는 그대로 |
| **c** | **회사마다 운영자 행** — `oidc_subject` 유니크를 `(tenant_id, oidc_subject)` 로, 교환이 행을 고른다 | 회사 하나 안의 판정은 그대로. 로그인 흐름이 바뀐다 |

### 갈래 비교 (AC-2) — 바뀌는 표면

| 표면 | **a** 유지 | **b** 행 하나 + 회사별 소속 | **c** 회사마다 행 |
|---|---|---|---|
| 스키마 | 변화 0 | `admin_operator_roles` PK → `(operator_id, role_id, tenant_id)` · WI-2 불변식(역할 행 = 홈) **폐기** — 역할 행의 `tenant_id` = 그 역할이 사는 회사. `oidc_subject` UNIQUE **유지** | `uk_admin_operators_oidc_subject` → `UNIQUE (tenant_id, oidc_subject)`. 역할 PK · WI-2 그대로 |
| 토큰 교환 | 변화 0 | **변화 0** — `sub` → 행 하나(`TokenExchangeService.java:89-96` 그대로) | 🔴 `sub` 하나에 행 N 개 → 입력이 하나 더 필요(어느 회사). 콘솔 토큰의 테넌트는 `iam`(772 S4)이라 subject token 은 회사를 모른다 ⇒ 교환 요청에 회사 파라미터 · ADR-014 D3(«결정적 · 행이 권위») 개정 · `admin-api.md` token-exchange 계약 개정 |
| assume | 변화 0 | **변화 0** — 실효 범위 = 배정 ∪ 홈(`OperatorAssignmentCheckUseCase.java:184-193`). 소속 회사 = 배정 행 | 🔴 `(sub, 테넌트)` → 행 해소가 새로 필요. 행 1 의 배정이 B 를 가리키고 행 2 의 홈이 B 이면 **두 행이 같은 테넌트에 닿는다** — 금지 규칙(배정 쓰기 거절) 또는 우선순위가 필요 |
| RBAC 평가 | 변화 0 | 🔴 **본체.** `hasPermission(operator, perm)` 이 테넌트 무관(`PermissionEvaluatorImpl.java:39-45`) · 캐시 키도 운영자(`CachingPermissionEvaluator.java:71-76`) ⇒ `(운영자, 권한, 행동 테넌트)` 로. 읽기 게이트(`QueryTenantScopeGate`)도 «배정 ∪ 홈» 이 아니라 **그 권한의 역할 행 테넌트**로 좁힌다 — 안 그러면 A 의 역할로 B 를 읽는다(③ «관리 평면 읽기» — 지금 배정의 «상속» 이 바로 그것). 쓰기 게이트(`AdminGrantScopeEvaluator`)는 이미 역할 행 테넌트를 읽으므로 PK 변경만 따라간다 | 변화 0 — 행마다 홈 하나, 지금 판정 그대로 |
| 콘솔 로그인 · 스위처 | 변화 0 | 로그인 변화 0. 스위처 **변화 0**(이미 배정 ∪ 홈, ③). 관리 화면이 «지금 테넌트» 를 관리 API 에 실어야 하는 곳이 생긴다(읽기 게이트 · 역할 판정 입력) | 🔴 IdP 로그인 뒤 «회사 선택»(행이 둘 이상일 때) · 운영자 쿠키가 행 하나에 묶임 · **스위처는 지금 행의 범위만** 보인다 ⇒ 회사를 바꾸는 것은 «테넌트 전환» 이 아니라 **재교환**(두 종류의 전환이 생긴다) |
| 셀프 온보딩 409 (S1-11) | 정책으로 남음 | 걷는다 → 기존 행에 새 테넌트 역할 행 + 배정(D4). `'*'` 행 · 같은 테넌트는 409 남음(D2) | 걷는다 → 새 행(같은 `oidc_subject`). 같은 테넌트 · `'*'` 는 409 남음 |
| 초대 수락 409 (OD-1) | 정책으로 남음 | 걷는다 → 기존 행에 역할 행 + 배정 추가. 같은 테넌트에 이미 소속이면 409 | 걷는다 → 새 행. `(tenant_id, oidc_subject)` 충돌만 409 |
| 퇴사(한 회사만) | 해당 없음(회사 하나) | 🔴 새 동작: «이 회사에서 내보내기» = 그 회사 역할 행 + 그 회사 배정 삭제. 지금 `PATCH …/status` 는 **행 전체**를 정지하고(`PatchOperatorStatusUseCase.java:61-67`) 역할 변경은 **전부 지우고 다시 쓴다**(`PatchOperatorRoleUseCase.java:54`) — 둘 다 회사 축으로 고쳐야 B 가 산다. 배정 해제는 이미 대상 테넌트 범위(`ManageOperatorAssignmentUseCase.java:110`) | ✅ **공짜** — A 행 정지 · 역할 변경은 B 행을 건드리지 않는다. 지금 장치 그대로 |
| 감사 | `admin_actions.operator_id` = 행 = 사람 | 한 사람 = 운영자 id 하나 유지. `admin_actions.tenant_id`(지금 홈 기반 — V0025 STEP 4)를 **행동 테넌트**로(⚪ 감사기의 현재 출처는 열지 않았다) | 🔴 한 사람이 운영자 id N 개로 갈라진다 — «이 사람이 한 일» 은 `oidc_subject` · `identity_id` 로 다시 모아야 한다. 도메인 쪽 귀속은 계정 `sub`(ADR-060)라 무영향 |
| 협력사 · 그룹 | 무영향 | 홈을 읽는 둘(④): 파트너십 `partner = 홈` · 그룹 멤버 «홈 == 그룹 테넌트» → **소속 회사 집합**으로 | 무영향(행마다 홈) — 단 같은 사람이 A 행으로 B 의 파트너십 참여자, B 행으로 B 운영자가 되는 조합을 막을지 정해야 한다 |
| 데모 시드 | 변화 0 | 행 하나에 두 회사 역할 행 + 배정 하나(`demo-assigned-only` 모양을 «역할 있는» 판으로) | 같은 `oidc_subject` 의 두 행 |
| 기존 데이터 이동 | 0 | PK 넓히기는 **데이터 안전** — 지금 모든 역할 행이 `tenant_id = 홈`(V0026)이라 `(op, role)` 유일 ⇒ `(op, role, tenant)` 유일. 🔴 의미 이동 하나: 지금 «배정만» 인 테넌트의 «운영자 수준 역할 상속»(V0030:16-18)을 유지할지(라이더 R2) | UNIQUE 완화라 **데이터 안전**. 단 «행 하나 + 배정» (지금 기전)과 «행 여럿» 이 **같은 사실(이 사람은 B 에서 일한다)의 두 저장소**로 공존한다 |
| 크기(코드 읽기 추정) | 0 | 중 — admin-service 안: 마이그레이션 1 · 권한 평가 · 캐시 · 읽기 게이트 · 정지/역할 변경 · 파트너십/그룹 술어 · 두 409. auth-service · 콘솔 로그인 무변경 | 대 — admin(유니크 · 해소기 호출 6곳: `TokenExchangeService.java:90` · `OperatorAssignmentCheckUseCase.java:142` · `OperatorConsoleEligibilityQueryUseCase.java:35` · `SelfServiceOnboardingUseCase.java:61` · `OperatorInvitationAcceptanceUseCase.java:128, 261`) + auth(콘솔 적격 질의) + console-web(회사 선택 · 쿠키 · 재교환 전환) + ADR-014 개정 |

### 두 Edge Case 의 답

| Edge Case | a | b | c |
|---|---|---|---|
| A 퇴사 · B 잔류(B 권한 그대로) | 해당 없음 — 두 회사를 가질 수 없다(같은 사람이 두 번째 회사를 하려면 **두 번째 이메일**의 풀 계정이 필요 — 풀은 이메일당 계정 하나, ADR-078) | 새 «회사에서 내보내기» 로 충족 — 행 정지 · 역할 전부 교체가 회사 축으로 바뀐 뒤에만(위 «퇴사» 행). 🔴 홈이 A 이면 A 를 떠난 뒤 «홈» 의 의미(라이더 R1) | 지금 장치로 충족 |
| 플랫폼 관리자(`iam`) + 고객사 운영자 | 지금처럼 두 계정 · 두 행(⑤) | D2 — `'*'` 행은 소속을 못 받고 풀 행은 `'*'` 를 못 받는다. 두 계정 유지 | D2 — `'*'` 행과 회사 행이 같은 `oidc_subject` 를 공유하지 못하게(`(tenant_id, oidc_subject)` 만으로는 `'*'` + 회사가 **허용**된다 — 별도 거절 필요) |

## 추천 — 🔴 **구현자의 선호**다. 소유자 결정이 아니다

**b.** 이유:

1. **이미 ACCEPTED 된 결정을 마저 하는 것이다.** ADR-020 D5 가 «배정마다 권한 세트 · 활성 배정의 권한이 실효 권한» 을 골랐고(③), ADR-045 D7 은 «한 계정에 측면 여럿» 을, ADR-080 은 «개인 계정 + 조직 소속» 을 골랐다. b 는 그 셋이 가리키는 모양이고, c 는 셋 모두와 반대 방향(한 사람 = 행 여럿)이다.
2. **바뀌는 자리가 한 서비스 안에 모인다.** 교환 · assume · 콘솔 로그인 · 스위처는 이미 «행 하나 · 테넌트 여럿» 으로 돈다(③). b 의 본체는 admin-service 의 권한 평가와 행 수준 장치 둘(정지 · 역할 교체)이다. c 는 교환 계약 · ADR-014 · 콘솔 로그인 흐름 · auth 질의를 바꾸고 «테넌트 전환» 과 «회사 전환(재교환)» 이라는 **두 종류의 전환**을 화면에 남긴다.
3. **c 는 한 사실을 두 저장소에 둔다.** «이 사람은 B 에서 일한다» 가 «행 1 의 B 배정» 과 «홈이 B 인 행 2» 로 동시에 표현될 수 있고, assume 이 그 둘 중 하나를 골라야 한다. 이 저장소에서 한 사실의 두 자리는 한쪽만 고쳐진다.

b 를 가볍게 보지 않는다 — 🔴 **권한 평가를 테넌트 축으로 바꾸는 것은 지금 «배정의 상속» 으로 열려 있는 읽기(A 의 역할로 B 를 읽는다)를 닫는 일이기도 하다.** 그 변화는 지금 배정만 가진 운영자의 화면을 줄일 수 있다(라이더 R2). c 의 «퇴사 공짜» 는 실재하는 장점이고, Edge Case 1 이 가장 중요하다면 c 가 가장 싸게 그것을 준다.
a 는 773 AC-3 을 «미지원» 으로 닫는 정당한 선택이다 — 다회사 수요가 데모 · 실사용에서 측정되기 전이라면 a 로 두고 수요가 생길 때 이 ADR 을 다시 여는 것이 c 보다 낫다.

## 라이더 (갈래 b 에서만) — 🔴 **내 선택**이지 소유자 결정이 아니다

| 번호 | 기본값 | 뒤집기 |
|---|---|---|
| R1 | `admin_operators.tenant_id`(홈)는 «처음 소속된 회사» 로 **남기되 판정에서 읽지 않는다**(감사 기본값 · 표시만). 그 회사를 떠나도 값은 그대로 | «홈 컬럼 은퇴»(ADR-020 D6 step 4) / «떠나면 남은 회사로 옮긴다» |
| R2 | 역할 행이 **없는** 배정 테넌트에서 관리 평면 권한은 **0**(지금의 «운영자 수준 역할 상속» 을 끊는다). 도메인 평면(구독 파생)은 그대로 | «상속 유지» — 지금 배정만 가진 운영자의 읽기 화면 불변 |
| R3 | 행 전체 정지(`PATCH …/status`)는 소속이 **하나뿐**일 때만 회사 관리자에게, 둘 이상이면 플랫폼(`SUPER_ADMIN`)만. 회사 관리자의 퇴사 동작은 «이 회사에서 내보내기» | «행 정지는 언제나 플랫폼만» |
| R4 | ADR-020 D5 의 `permission_set_id` 는 **쓰지 않고**(단일 FK 라 첫 관리자의 두 역할을 못 담는다 — `FirstAdminProvisioner.java:52`) 역할 행의 테넌트 축으로 소속 역할을 표현한다. `permission_set_id` 는 계속 미사용으로 둔다 | «`permission_set_id` 를 역할 세트 표로 넓혀 그것을 쓴다» |

---

## Alternatives Considered

- **b2 — 배정의 `permission_set_id` 를 평가에 잇는다(역할 PK 무변경)** — ADR-020 D5 의 글자 그대로의 구현. 기각(지금 크기에서): 칸이 `admin_roles.id` 단일 FK 라(`V0030…sql:31, 36`) 첫 관리자의 `TENANT_ADMIN` + `TENANT_BILLING_ADMIN` 을 못 담는다 — 역할 세트 표가 새로 필요하고, 그러면 «회사 역할» 이 역할 행과 세트 표 **두 곳**에 산다. b 의 R4 로 남긴다.
- **회사마다 IAM 계정(두 번째 이메일)** — a 아래에서 사용자가 할 수 있는 우회. 정책으로 고르지 않는다: ADR-080 의 «개인 계정 하나» 와 반대이고, 풀은 이메일당 계정 하나라 진짜 두 번째 메일함이 필요하다.
- **플랫폼 관리자도 다회사 기전에 태운다(`'*'` 행에 회사 소속)** — 🔴 기각(D2 · ADR-080 D1). 전 테넌트에 닿는 신원과 한 회사의 직원 신원을 한 행으로 합친다.
- **토큰에 테넌트 목록** — 🔴 기각(D1 · ADR-020 D3-B). 한 회사를 보는 토큰으로 다른 회사에 닿는다.
- **도메인 평면도 회사별 역할로** — 범위 밖(D5). 관리 평면이 먼저고, 도메인 평면의 세분은 ADR-035 O1 B행의 미룬 항목이다.

## Consequences

### ACCEPT 가 인가하는 것

- (a) 772 OD-1 의 두 409 를 정책으로 확정 · `admin-api.md:1658` · `onboarding-api.md:93` · `multi-tenancy.md:635` 의 «773 착수 전 ADR» 문장을 «ADR-082 = a» 로 갱신 · 773 AC-3 · Goal 표 둘째 줄 개정 · 772 Edge Case 1 «미충족» 을 «정책상 지원 안 함» 으로.
- (b) admin-service: 역할 PK 마이그레이션 · WI-2 불변식 폐기(data-model) · 권한 평가 · 캐시 · 읽기 게이트를 테넌트 축으로 · 정지 · 역할 변경을 회사 축으로(R3) · 파트너십 · 그룹 술어를 소속 집합으로 · 두 409 를 «소속 추가» 로 · D2 거절 · 773 AC-3 · 시드 · 계약(`admin-api.md` · `rbac.md` · `data-model.md` · `console-registry-api.md:282-283` 정정) · ADR-020 · ADR-024 에 «082 b 가 D5 · 역할 행 의미를 바꾼다» 덧붙임(본문 불변).
- (c) admin-service 유니크 · 해소기 6곳 · 교환 계약 + ADR-014 D3 개정 · auth 콘솔 적격 질의 · console-web 회사 선택 · 쿠키 · 재교환 전환 · assume 의 «두 행이 같은 테넌트» 금지 · D2 거절 · 773 AC-3 · 시드.
- 단계 티켓 기안 — ACCEPT PR 안에서.

### 건드리지 않는 것

- 토큰 클레임 모양(D1) · 도메인 역할 파생(D5 · ADR-035 O1) · 플랫폼 관리자 모델(D2 · ADR-080 D1) · 772 의 초대 · 인증 술어(D-3 의 1~5) · 773 의 셸 상태 · «관리자 대기» 상태.

### 새로 생기는 위험

- 🔴 (b) 권한 평가를 테넌트 축으로 바꾸는 순간 **«행동 테넌트» 를 모르는 호출**이 생긴다 — 관리 API 중 테넌트를 받지 않는 것(자기 정보 · 목록)이 무엇을 기준으로 판정하는지 단계 티켓 AC-0 에서 전수해야 한다. 기본값을 «홈» 으로 두면 R1 과 모순이고 «모든 소속의 합» 으로 두면 지금의 상속 누수가 그대로다.
- 🔴 (b) R2 기본값은 지금 배정만 가진 운영자의 관리 화면을 줄인다 — 데모 시드 중 영향받는 행(`demo-operator` 의 `ecommerce` 배정 등)을 AC-0 에서 센다.
- 🔴 (c) 회사 선택이 로그인 경로에 들어간다 — 선택 화면 · 기억된 회사 · 선택한 회사의 행이 정지됐을 때의 문구가 새 실패 표면이다(772 F5 · F6 의 문구 문제와 같은 부류).
- (a) 같은 사람이 두 회사를 하려면 두 번째 이메일이 필요하다 — ADR-080 의 목표 모양에서 한 칸 비켜선 상태가 정책이 된다.

## Verification (구현 단계에서)

- 🔴 **대조군 — Edge Case 1(b · c)**: 같은 사람이 A · B 의 운영자 → A 에서 내보내기 → **같은 시험에서** A assume 거절 · A 관리 API 거절 · B assume 성공 · B 관리 API 성공 · 같은 계정의 스토어 · 팬 토큰 그대로(080 D5).
- 🔴 **대조군 — 권한 누수(b)**: A 에서 `TENANT_ADMIN`, B 에서 역할 없음(또는 읽기 전용) → B 의 운영자 목록 · 감사 조회가 A 의 권한으로 **통하지 않는다**. 실패 쪽을 먼저 단언하고 같은 시험에서 A 의 같은 조회가 통하는 것을 단언한다. 🔵 지금 코드에서는 이 시험의 앞 절반이 **빨갛다**(③ «관리 평면 읽기») — 그것이 시험의 «전» 상태다.
- (b · c) 두 번째 회사 생성 뒤 기존 회사의 역할 행 바이트 불변 · 상한 초과 거절(D4).
- (모든 갈래) `'*'` 행에 회사 소속 쓰기 → 거절 · 풀 행에 `'*'` → 거절(D2).
- (c) 행 1 의 B 배정 + 행 2 의 홈 B 를 만들려는 쓰기 → 거절(또는 고른 우선순위의 단언).
- 772 AC-6 IT 의 «두 번째 회사 409 · 셀프 온보딩 409» 칸은 a 에서 그대로, b · c 에서 **바뀐 기대값**으로 — 걷지 말고 고친다.

## Outstanding follow-ups

- ⏸️ ACCEPT 뒤 단계 티켓 기안 — ACCEPT PR 안에서(산문에만 남기지 않는다). 773 은 그 티켓(또는 773 의 슬라이스) 뒤에 착수한다.
- ⏸️ ⚪ 라이브 모집단 — «운영자 행 하나가 홈 밖 배정을 가진 수» · «같은 이메일의 `iam` 자격 + 풀 계정을 가진 사람 수» 는 DB 가 필요해 재지 않았다. 다음 데모 창 술어: `SELECT COUNT(DISTINCT operator_id) FROM operator_tenant_assignment a JOIN admin_operators o ON o.id = a.operator_id WHERE a.tenant_id <> o.tenant_id`.
- ⏸️ ⚪ 감사기가 지금 `admin_actions.tenant_id` 를 무엇으로 채우는지(④ 마지막 행) — b 의 단계 티켓 AC-0.
- ⏸️ `console-registry-api.md:282-283` 의 «single tenant slug» 문장 — 구현(배정 ∪ 홈)과 다르다. 갈래와 무관한 계약 정정(가장 가까운 단계 티켓에 싣는다).

## 수락 형식

```
ADR-MONO-082 ACCEPTED — <a|b|c>
```

- 이름 · `ACCEPTED` · **갈래 letter** 세 요건이 모두 있어야 한다. letter 만(«b 로»), «진행», 또는 **추천과 같은 선택창 응답**은 수락이 아니다 — 추천이 b 이므로 «b» 를 고른 선택을 수락으로 읽으면 구현자 추천을 소유자 결정으로 읽는 귀속 오류와 구별되지 않는다(`ADR-MONO-080` § ACCEPT 게이트 기록 · `ADR-MONO-079` § ACCEPT 게이트 기록과 같은 이유). 선택창에서 고른 경우 정확형 문장을 만들어 보여 주고 그 문장이 도착한 메시지에 ACCEPT 를 귀속한다.
- 선택 — 라이더 뒤집기(갈래 b 에서만): R1~R4 의 «뒤집기» 문구를 같은 문장에 덧붙인다. 없으면 기본값이 **구현자 선택**으로 남는다(ACCEPT 시점 라이더 대조로 기록).
- 소유자가 정하는 것(이 문서가 정하지 않은 것): ① 갈래 a/b/c ② (b) R1~R4 를 뒤집을지 ③ (c) 두 행이 같은 테넌트에 닿을 때의 규칙(금지 / 우선순위) ④ 신원당 상한 **값**(773 AC-0 — 이 문서는 세는 단위만 정했다, D4).

## History

| 날짜 (UTC) | 상태 | 내용 | 근거 |
|---|---|---|---|
| 2026-10-10 | PROPOSED | 772 F2 의 세 벽을 재측정 — 셋 다 그대로(`V0027:74` · `TokenExchangeService.java:89-96` · `V0004:48`). 넷째 벽(WI-2 «역할 행 = 홈», `V0026:20-23`)과 **이미 있는 다회사 기전**(ADR-020 배정 — 도메인 역할은 구독 파생으로 회사 무관 · `permission_set_id` 평가 미사용 · 관리 읽기는 «상속», 쓰기는 0 · 스위처는 이미 배정 ∪ 홈)을 더함. D1~D6 · 갈래 a/b/c · 추천 b(구현자 선호) · 라이더 R1~R4(b 만) · 773 AC-3 갈래별 닫기 | `TASK-MONO-783` · 위 Context 실측 |
| 2026-10-10 | PROPOSED → **ACCEPTED (b)** | 소유자 정확형 `ADR-MONO-082 ACCEPTED — b`. D1~D6 본문은 **바이트 그대로**. 라이더 뒤집기 미공급 → R1~R4 기본값. 단계 티켓 `TASK-MONO-784` 기안 · `TASK-MONO-773` 선행에 784 추가 · AC-3 을 D6 b 행으로 | 소유자(이 대화) · § ACCEPT 게이트 기록 |

## ACCEPT 게이트 기록 (2026-10-10 UTC)

- PROPOSED PR(#4288) 머지 뒤, 정확형 문장을 요청한 다음 메시지로 *«ADR-MONO-082 ACCEPTED — b»* 가 도착했다. 선택창은 쓰지 않았다 — 소유자가 문장을 직접 보냈다. ACCEPT 는 그 메시지에 귀속된다.
- 갈래 b 는 § 추천(구현자 선호)과 같다. 추천이 결정으로 바뀐 근거는 그 문장이지 추천이 아니다.
- 소유자가 정하는 것 넷 중: ① = **b** · ② 라이더 = 뒤집기 없음(기본값) · ③ 해당 없음(c 전용) · ④ 신원당 상한 **값**은 여전히 열림 — `TASK-MONO-773` AC-0 몫(D4).

## 라이더 대조 (ACCEPT 시점)

수락 문장에 라이더 뒤집기 문구가 없다. 아래 기본값이 **구현자의 선택**으로 남는다 — 소유자가 한 줄로 뒤집을 수 있고, 그때 걸리는 티켓의 AC 를 고친다.

| 번호 | 적용되는 기본값 | 걸리는 티켓 |
|---|---|---|
| R1 | 홈(`admin_operators.tenant_id`)은 남기되 판정에서 읽지 않는다 · 떠나도 값 그대로 | `TASK-MONO-784` |
| R2 | 역할 행 없는 배정 테넌트의 관리 평면 권한 = **0**(상속 끊음) · 도메인 평면 그대로 | `TASK-MONO-784` |
| R3 | 행 전체 정지는 소속 하나일 때만 회사 관리자, 둘 이상이면 `SUPER_ADMIN` 만 · 회사 관리자의 퇴사 = «이 회사에서 내보내기» | `TASK-MONO-784` |
| R4 | `permission_set_id` 미사용 유지 · 소속 역할 = 역할 행의 테넌트 축 | `TASK-MONO-784` |

## ACCEPT 가 만든 새 의무 — 티켓으로 기안했다

| 티켓 | 무엇 | 선행(AC-0) |
|---|---|---|
| `TASK-MONO-784` | 갈래 **b** — admin-service 관리 평면을 회사별 소속으로: 역할 PK `(operator_id, role_id, tenant_id)` · WI-2 폐기 · 권한 평가 · 캐시 · 읽기 게이트를 행동 테넌트 축으로 · 정지(R3) · 역할 변경을 회사 축으로 · «이 회사에서 내보내기» · 파트너십 · 그룹 술어를 소속 집합으로 · 두 409(OD-1 · S1-11)를 «소속 추가» 로 · D2 거절 · 계약(`admin-api.md` · `rbac.md` · `data-model.md` · `console-registry-api.md:282-283` 정정) · 시드 · ADR-020 · ADR-024 덧붙임 | 772 `done/` (OD-1 409 와 S1-11 이 772 의 산출물이고, 772 AC-6 IT 칸을 바뀐 기대값으로 고친다) |
| `TASK-MONO-773` (개정) | AC-3 = D6 **b** 행 — 기존 행에 새 테넌트 역할 행 + 배정 · 기존 회사 역할 행 바이트 불변 · 상한 초과 거절 | 772 · **784** `done/` |

- `ADR-MONO-020` · `ADR-MONO-024` 에 «082 b 가 D5 · 역할 행 의미를 바꾼다» 덧붙임은 784 의 계약 단계에서 한다(이 PR 은 결정만 싣는다 — 두 ADR 본문은 그때도 바이트 그대로).
