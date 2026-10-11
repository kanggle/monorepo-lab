# Task ID

TASK-MONO-784

# Title

`ADR-MONO-082` 갈래 **b** — 운영자 행 하나에 **회사별 소속 · 역할**: 관리 평면 권한을 행동 테넌트 축으로 · «이 회사에서 내보내기» · 두 409 를 «소속 추가» 로

# Status

in-progress

# Owner

monorepo

# Task Tags

- iam
- security
- rbac

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (권한 평가 · 캐시 · 마이그레이션 · 권한 축소 대조군)
>
> ⏳ **DO NOT START — AC-0 이 참이 되기 전에는 착수하지 않는다.** 날짜 조건이 아니다. AC-0 은 verify-then-act 게이트다 — 재서 참이면 진행, 아니면 이 파일에 측정값과 날짜(UTC)를 덧붙이고 `ready/` 에 그대로 둔다.
> 🟢 **2026-10-11 UTC — AC-0 참**(772 `done/` · 측정 ①~③ 답 · ④ ⚪). 아래 § AC-0 기록. 🔴 슬라이스 착수 전 **소유자 결정 OD-1 · OD-2 · OD-3** 을 받는다(§ 5) — S1 계약이 그 답을 싣는다.

---

# Dependency Markers

- **선행**: `TASK-MONO-772` `done/` — 바꿀 두 409(초대 수락 OD-1 · 셀프 온보딩 S1-11)와 그 IT 칸(772 AC-6)이 772 의 산출물이다.
- **후속**: `TASK-MONO-773` AC-3(«이미 운영자인 계정이 두 번째 회사를 생성») 이 이 티켓에 묶인다.

# Goal

`ADR-MONO-082` ACCEPTED — b (2026-10-10 UTC). 한 사람(계정 `sub` 하나 = 운영자 행 하나)이 여러 회사의 운영자가 되게 한다. 교환 · assume · 콘솔 스위처는 이미 «행 하나 · 테넌트 여럿»(ADR-020 배정)으로 돈다 — 빠진 것은 **관리 평면의 회사별 역할**이다. 지금 권한 평가는 테넌트를 보지 않아(`PermissionEvaluatorImpl.java:39-45`) 홈의 역할이 배정 테넌트 읽기에 «상속» 되고(`QueryTenantScopeGate.java:66-81`), 역할 행은 홈에만 둘 수 있다(WI-2, `V0026:20-23`).

라이더는 기본값 그대로(ADR-082 § 라이더 대조): R1 홈은 남기되 판정에서 읽지 않음 · R2 역할 행 없는 배정 테넌트의 관리 권한 0 · R3 행 전체 정지는 소속 하나일 때만 회사 관리자 · R4 `permission_set_id` 미사용.

# Scope

## In Scope

- 계약 먼저: `projects/iam-platform/specs/contracts/http/admin-api.md`(두 409 → 소속 추가 · «이 회사에서 내보내기» · 역할 변경의 회사 축 · 정지 R3) · `rbac.md` · `data-model.md`(WI-2 폐기 · 역할 PK) · `onboarding-api.md:93` · `multi-tenancy.md:635`(«773 착수 전 ADR» 문장 → «ADR-082 = b») · `projects/platform-console/specs/contracts/console-registry-api.md:282-283` 정정(구현 = 배정 ∪ 홈)
- admin-service: 역할 PK `(operator_id, role_id, tenant_id)` 마이그레이션 · 권한 평가 + 캐시 키에 행동 테넌트 · 읽기 게이트를 «그 권한의 역할 행 테넌트» 로 · 정지(R3) · 역할 변경을 회사 축으로(지금 전부 지우고 다시 씀 — `PatchOperatorRoleUseCase.java:54`) · «이 회사에서 내보내기»(그 회사 역할 행 + 배정) · 파트너십(`partner = 홈`) · 그룹 멤버(«홈 == 그룹 테넌트») 술어를 소속 집합으로 · 초대 수락 · 셀프 온보딩의 409 를 «기존 행에 소속 추가» 로(같은 테넌트 · `'*'` 는 409 유지, ADR-082 D2 · D4)
- 데모 시드: 한 행에 두 회사 역할 행 + 배정 하나
- `ADR-MONO-020` · `ADR-MONO-024` 에 «082 b 가 D5 · 역할 행 의미를 바꾼다» 덧붙임(본문 바이트 그대로)

## Out of Scope

- 토큰 클레임 모양 · 도메인 역할 파생(ADR-082 D1 · D5) · 플랫폼 관리자 모델(D2)
- 773 의 «테넌트 생성» 화면 · 셸 상태 · 신원당 상한 **값**(773 AC-0)

# Acceptance Criteria

- [x] **AC-0 (게이트)** — 772 가 `done/` 이다. 아니면 착수하지 않는다. 참이면 재측정: ① 테넌트를 받지 않는 관리 API(자기 정보 · 목록)의 전수와 각각의 판정 기준(ADR-082 § 새로 생기는 위험 1 — 기본값 «홈» 은 R1 과, «모든 소속의 합» 은 상속 누수와 모순이므로 API 마다 정해 적는다) ② 감사기가 지금 `admin_actions.tenant_id` 를 무엇으로 채우는지 ③ R2 로 관리 화면이 줄어드는 데모 시드 행 수(`demo-operator` 의 `ecommerce` 배정 등) ④ ADR-082 § Outstanding 의 라이브 술어(홈 밖 배정 수) — 창이 없으면 ⚪ 와 이유. — ✅ 2026-10-11 UTC: 게이트 참 · ①②③ 답 · ④ ⚪(창 없음). § AC-0 기록.
- [ ] **AC-1** — 🔴 **대조군 — 권한 누수**: A 에서 `TENANT_ADMIN`, B 는 배정만(역할 없음) → B 의 운영자 목록 · 감사 조회가 **거절**되고, 같은 시험에서 A 의 같은 조회는 통한다. 실패 쪽을 먼저 단언한다(지금 코드에서 이 앞 절반은 빨갛다 — 그것이 «전» 상태).
- [ ] **AC-2** — 🔴 **대조군 — 한 회사만 떠나기**(ADR-082 D3): 같은 사람이 A · B 운영자 → A 에서 내보내기 → 같은 시험에서 A assume · A 관리 API 거절 · B assume · B 관리 API 성공 · 같은 계정의 스토어 · 팬 토큰 그대로.
- [ ] **AC-3** — 두 번째 회사 소속 추가(초대 수락 · 셀프 온보딩) → 기존 행에 새 테넌트 역할 행 + 배정 · 기존 회사 역할 행 **바이트 불변** · 같은 테넌트 재소속 409 · `'*'` 행에 회사 소속 쓰기 거절 · 풀 행에 `'*'` 거절(D2). 772 AC-6 IT 의 «두 번째 회사 409 · 셀프 온보딩 409» 칸은 **바뀐 기대값으로 고친다**(걷지 않는다).
- [ ] **AC-4** — 정지(R3): 소속 둘 이상인 운영자의 행 정지는 회사 관리자에게 거절 · `SUPER_ADMIN` 은 성공 · 소속 하나면 지금과 같다. 역할 변경이 다른 회사의 역할 행을 바꾸지 않는다.
- [ ] **AC-5** — 권한 캐시: 한 회사의 역할 변경 · 내보내기 뒤 그 회사 판정이 다음 요청에서 바뀌고 다른 회사의 캐시 판정은 그대로(캐시 키 = 운영자 × 테넌트).
- [ ] **AC-6** — 콘솔 e2e 디렉터리 grep(운영자 · 그룹 · 파트너 화면) 후 필요한 수정 · 머지 뒤 첫 `nightly-e2e.yml` 콘솔 잡 확인 · 다음 데모 창에서 R2 영향 행 라이브 확인(⚪ 허용, 이유 기록).

# Related Specs

- `docs/adr/ADR-MONO-082-one-person-operator-in-many-companies.md` (ACCEPTED — b) D1~D6 · 라이더 R1~R4 · § Verification
- `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` D1 · D2 · D5 · `ADR-MONO-024` D2 · `ADR-MONO-044` D2 · D4 · `ADR-MONO-045` D6 · D7 · `ADR-MONO-046` · `ADR-MONO-080` D1 · D5
- `projects/iam-platform/specs/features/multi-tenancy.md` · `projects/iam-platform/specs/services/admin-service/` (rbac · data-model)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` · `onboarding-api.md` · `projects/platform-console/specs/contracts/console-registry-api.md`

# Edge Cases

- 홈 회사를 떠난 운영자 — 홈 값은 남지만(R1) 판정에서 읽지 않으므로 그 회사 권한은 없다. 표시 화면이 «홈» 을 «소속» 처럼 보여 주지 않는가.
- 소속이 하나 남은 운영자가 그 회사에서 내보내진다 — 측면 0 = 운영자 아님(772 의 콘솔 적격 거절로 떨어진다). 행을 지울지 남길지 AC-0 에서 정한다.
- 그룹(ADR-046) 멤버가 그 회사를 떠난다 — 그룹 멤버십도 함께 걷히는가.

# Failure Scenarios

1. 권한 평가에 테넌트를 더하고 캐시 키는 운영자만 둔다 — A 의 판정이 B 에서 재사용된다(누수가 캐시로 되살아난다).
2. 테넌트를 받지 않는 관리 API 가 기본값으로 «홈» 을 읽는다 — 홈을 떠난 운영자가 그 회사 권한을 계속 쓴다(R1 위반).
3. 역할 변경이 «전부 지우고 다시 씀» 을 그대로 둔다 — A 의 역할 변경이 B 의 역할 행을 지운다(D3 위반).

---

# AC-0 기록 (2026-10-11 UTC)

> 분석=Opus 5.5 (architect) · **코드 변경 0** · 정적 읽기, worktree `feat/mono-784-operator-membership-per-company`(origin/main 에서). 라이브 ⚪ — DB · 데모 미기동. 경로는 따로 적지 않으면 `projects/iam-platform/apps/admin-service/src/main/java/com/example/admin/` 아래다(`app/` = `application/`, `pres/` = `presentation/`, `rbac/` = `infrastructure/persistence/rbac/`).
> ADR-082 · 라이더 R1~R4 · 티켓이 정한 것은 다시 묻지 않고 § 4 **구현자 결정**에 적었다. 그것들이 정하지 않은 것만 § 5 **소유자 결정(OD)** 으로 분리했다. 🔵 «열지 않았다» 고 적은 것은 grep 줄만 봤다는 뜻이다.

## 0. 선행 (게이트)

| 잰 것 | 결과 | 출처 |
|---|---|---|
| `TASK-MONO-772` `done/` | ✅ **참** — DONE 2026-10-11 UTC (4-dim verified), review→done PR #4300 | `tasks/done/TASK-MONO-772-operator-invite-verified-acceptance.md` · `tasks/INDEX.md` § done 첫 행 |
| ADR-082 상태 | ✅ ACCEPTED — b, 라이더 뒤집기 없음 | `docs/adr/ADR-MONO-082-…md:10-11, 253, 255-264` |
| 772 AC-6 IT 칸(바꿀 대상) | 존재 — 두 409 단언 그대로 | `src/test/…/integration/OperatorInvitationAcceptanceIntegrationTest.java:471-534`(`:517-522` 두 번째 회사 409 · `:524-533` 셀프 온보딩 409) |

## 1. 티켓 AC-0 의 네 측정

### ① 테넌트를 받지 않는 관리 API — 전수와 API 별 판정 기준

**먼저 기전(F1)**: 권한 판정의 유일한 자리 `RequiresPermissionAspect` 는 **테넌트 입력이 없다** — `OperatorContextHolder.require()` → `permissionEvaluator.hasPermission(operatorId, perm)`(`pres/aspect/RequiresPermissionAspect.java:94-112`). `OperatorContext` 는 `(operatorId, jti)` 뿐이고(`app/OperatorContext.java:14-17`), 운영자 토큰에도 테넌트가 없다(`app/TokenExchangeService.java:117-121` — `mint(operatorId)` 만). ⇒ «행동 테넌트» 는 **요청마다 엔드포인트 입력에서만** 온다: `tenantId` 쿼리(operators · audit · accounts · operator-invitations · lookup · groups) 또는 `X-Tenant-Id` 헤더(잠금 계열 · GDPR · 세션 · 파트너십 · 배정 · 정책). 콘솔은 활성 테넌트를 **항상 둘 다** 보낸다(`projects/platform-console/apps/console-web/src/shared/api/iam-operators-read.ts:55-56, 151-163` · `iam-audit-read.ts:42-48, 139-146`).

**공통 규칙 — 안전한 «합» 은 이미 있다**: ADR 의 두 금지 기본값(«홈» = R1 위반, «모든 소속의 합» = 상속 누수)이 아닌 셋째가 코드에 있다 — `AdminGrantScopeEvaluator.effectiveAdminScope(operator, permission)` = **그 권한을 주는 역할 행의 테넌트 집합**(`rbac/AdminGrantScopeEvaluator.java:79-114`). 이 집합의 합은 «그 권한을 실제로 가진 회사» 만 담는다 — 배정만 있는 회사는 들어갈 수 없으므로 상속이 없고, 홈을 읽지 않는다. `GET /api/admin/groups` 가 이미 이 규칙으로 돈다(`app/GroupAdminUseCase.java:97-116`). **784 의 판정 기준 = 이것**(D-4).

**생략된 테넌트의 해소(D-4)**: 명시값(쿼리 `tenantId` > `X-Tenant-Id`) → 없으면 그 권한의 grant scope 가 `{'*'}` 면 `'*'`(지금 홈=`'*'` 와 같은 결과) · 구체 테넌트 **하나**면 그것 · **0** 이면 `403 TENANT_SCOPE_DENIED` · **둘 이상**이면 `400 VALIDATION_ERROR`(«tenantId 필요», 새 코드 없음). 🔵 단일 회사 운영자는 WI-2 덕에 grant scope = {홈} 이므로 지금 기본값과 **바이트 동일**하다 — 행동이 바뀌는 것은 다회사 운영자뿐.

| # | API | 지금 테넌트 입력 · 생략 시 | 지금 판정 | 784 뒤 판정 | 왜 |
|---|---|---|---|---|---|
| 1 | `GET /api/admin/me` | 없음 | 판정 없음 · `roles` = 모든 역할 행 이름(`app/OperatorQueryService.java:27-38`) | 신원 필드 그대로 · `roles` = `X-Tenant-Id` 테넌트의 역할 행(+ `'*'` 행) · 헤더 없으면 지금처럼 전체 · **새 `memberships[{tenantId, roles}]`**(가산) | 권한 판정에 안 쓰인다 — 콘솔 nav 노출만 읽는다(`console-web/src/shared/ui/console-nav-exposure.ts:21, 57`). 합을 보여도 누수는 아니지만 nav 가 활성 회사를 따라야 한다 |
| 2 | `PATCH /operators/me/password` · `/me/profile` | 없음 | `@SelfServiceEndpoint`(`pres/OperatorAdminController.java:311-345`) | 그대로 | 자기 행의 행 전체 속성 — 회사 판정 대상 아님 |
| 3 | `GET /roles` · `/permissions` | 없음 | aspect 합(`pres/RoleAdminController.java:57-58, 74-75`) | 그대로 — 어느 소속이든 `operator.manage` 하나 | 응답에 테넌트 데이터가 없다(카탈로그) |
| 4 | `GET /console/registry` | 없음 | 실효 범위 = 배정 ∪ 홈(`app/console/ConsoleRegistryUseCase.java:79-82`) | **소속(배정 행)** ∩ 구독 ∩ ACTIVE · `'*'` 그대로 | R1(홈 안 읽음) + D3(떠난 회사가 스위처에서 사라진다) — D-2 |
| 5 | `GET /operators/grantable-roles` | 없음 | ≤-own 을 **권한 합**으로(`app/RoleGrantGuard.java:94-109, 130-142` — `:141 hasAllPermissions`) | `X-Tenant-Id` 테넌트 안에서 보유한 권한으로 ≤-own · `'*'` grant → 전부 · 생략 규칙 | 합으로 두면 A 의 권한 집합으로 B 에서 상향 부여 메뉴가 열린다(같은 술어의 쓰기 `requireGrantable` 도 — F3) |
| 6 | `GET /operators` | `tenantId` 생략 = 홈(`app/OperatorQueryService.java:64-65`) | 실효 범위(`:61-72`) | grant scope(`operator.manage`) + 생략 규칙 | AC-1 누수 자리 |
| 7 | `GET /audit` | 생략 = 홈(`app/QueryTenantScopeGate.java:71-73`) | 실효 범위(`:66-81`) | grant scope(`audit.read`) | AC-1 누수 자리 |
| 8 | `GET /accounts` 목록 | 생략 = 홈 | 실효(`pres/AccountAdminController.java:88-89`) | grant scope(`account.read`) | 상속 누수 |
| 8b | `GET /accounts?email=` | 생략 = 홈 · **권한 키 없음**(`:93-95`, rbac.md:84-93) | 실효 | **«역할 행이 하나라도 있는 테넌트»**(역할 무관) | 권한 키 없는 읽기를 R2 에 맞춘다 — 배정만 = 0 |
| 9 | `GET /operators/lookup` | 생략 = 홈 · 범위 밖 = 빈 목록(`app/OperatorEmailLookupUseCase.java:45-53`) | 실효 | 8b 와 같은 술어 · 빈 목록 응답 그대로 | 같은 «권한 키 없는 읽기» 행(rbac.md:91) |
| 10 | `POST /accounts/{id}/lock` · `unlock` · bulk-lock · `POST …/gdpr-delete` · `GET …/export` · `POST /sessions/{id}/revoke` | `X-Tenant-Id` 생략 = 홈 | 🔴 **쓰기인데 읽기 게이트**(실효 범위) — `pres/AccountAdminController.java:154-156, 189-192` · `pres/AdminGdprController.java:36, 46, 68, 78` · `pres/SessionAdminController.java:37, 50` | grant scope(각 annotation 권한) | 지금 배정만 있는 회사에서 **잠금 쓰기가 통한다**(F2) |
| 11 | `GET /operator-invitations` | 생략 = 홈(`app/OperatorInvitationUseCase.java:137`) | 실효(`:139-145`) — 게이트 사본 | grant scope(`operator.manage`) | 상속 누수 |
| 12 | `PATCH /operators/{id}/profile` | 대상 홈 | 대상 홈 ∈ **호출자 실효 범위**(`app/UpdateOperatorProfileUseCase.java:105-115`) | **R3 규칙**(D-8) | 행 전체 속성 — 사본이 배정 범위로 판정하는 누수 |
| 13 | `PATCH …/identity:link` · `:unlink` | 대상 홈 | 대상 홈의 grant scope(`app/LinkOperatorIdentityUseCase.java:95-99` · `UnlinkOperatorIdentityUseCase.java:52`) | R3 규칙 | 행 전체 속성(`identity_id` 는 행에 하나) |
| 14 | `PATCH /operators/{id}/status` | 대상 홈 | 대상 홈의 grant scope(`app/PatchOperatorStatusUseCase.java:49-52`) | R3 규칙 | 라이더 R3 |
| 15 | `PATCH /operators/{id}/roles` | 대상 홈 · 전체 교체 | 대상 홈 grant scope(`app/PatchOperatorRoleUseCase.java:37-40`) + `deleteOperatorRoles`(`:54`) | `X-Tenant-Id` = T: T ∈ 대상 소속 ∧ T ∈ actor grant scope(`operator.manage`) · **T 의 직접 행만** 교체 · ≤-own 은 T 안에서(D-7) | D3 · Failure Scenario 3 |
| 16 | `GET /tenants` | 없음 | 홈 = `'*'` 만(`pres/tenant/TenantAdminController.java:128-141`) | 그대로 | `'*'` 는 소속이 아니다(D2) |
| 17 | `GET /tenants/{id}` | path | 실효 범위(`:167-175`) | grant scope(`tenant.manage`) | 게이트 사본 |
| 18 | `GET /groups` | 생략 = grant scope 합 | grant scope(`app/GroupAdminUseCase.java:97-116`) | 그대로 | 이미 공통 규칙 — 안전한 합의 실례 |
| 19 | `GET /partnerships` | `X-Tenant-Id` 필수, 빈 값 = 빈 페이지 | grant scope(`app/PartnershipManagementUseCase.java:244-260`) | 그대로 | MONO-737 이 이미 행동 테넌트 축으로 고쳤다 |
| 20 | `GET /operators/{id}/assignments` | `X-Tenant-Id` 필수 | grant scope(`app/ManageOperatorOrgScopeUseCase.java:85-97`) | 그대로 | 같은 |
| 21 | org-node 읽기 4종 | path · 없음 | 노드 grant(🔵 `app/OrgNodeScopeGuard.java:192-224` grep 만 — use case 는 열지 않았다) | 그대로 | 노드 행 스코프는 `org_node_id` 가 구동(data-model.md:144) — 홈 무관 |
| 22 | entry-policy GET · placement preview | path | grant scope(`app/TenantEntryPolicyUseCase.java:56` 등) | 그대로 | 이미 행동 테넌트 |
| 23 | `POST /onboarding/organizations` | self-service | § 2 «두 409» | D-12 | — |

⇒ **누수 모집단은 ADR 이 센 것보다 넓다**: 읽기 게이트(`QueryTenantScopeGate`) 하나가 아니라 그 게이트를 쓰는 **쓰기 5종**(#10) + 게이트를 **다시 구현한 사본 4곳**(`OperatorQueryService.java:61-72` · `OperatorInvitationUseCase.java:139-145` · `UpdateOperatorProfileUseCase.java:109-115` · `TenantAdminController.java:169-172`)이다(F2). 사본은 게이트 하나로 합친다(D-4).

### ② 감사기가 지금 `admin_actions.tenant_id` 를 채우는 법

| 행 | `tenant_id` | `target_tenant_id` | 출처 |
|---|---|---|---|
| IN_PROGRESS · 완료 · 단발(`record`) | **행위자 홈**(`resolveOperatorOrFail` — `admin_operators.tenant_id`, null 이면 `"fan-platform"`) | 호출자가 준 값, 없으면 홈 | `app/AdminActionAuditWriter.java:53-64`(해소) · `:75-76, 93-94`(start) · `:150-151, 171-172` |
| 로그인 | 홈 | 홈(«always self-tenant») | `AdminActionAuditWriter.java:216-217` |
| DENIED 계열(권한 · 교차 테넌트 · 배치 · 역할 부여) | 홈 | 홈(«no leak») | `app/AdminActionDenyWriter.java:85-86, 122, 145-146, 219-220, 295-296, 363-364` |

🔴 **조회가 그 칸으로 거른다**: 비플랫폼 감사 조회 = `WHERE a.tenantId = :tenantId`(`infrastructure/persistence/AdminActionJpaRepository.java:31-40`), 플랫폼의 특정 테넌트 조회 = `tenant_id='*' AND target_tenant_id = :t`(`:55-65`). ⇒ b 에서 홈 A 인 사람이 B 에서 한 행위는 **A 의 감사에 쌓이고 B 의 관리자는 못 본다**(F5) → D-13.

### ③ R2 로 관리 화면이 줄어드는 데모 시드 행

admin `db/migration-dev` 의 배정 행 **4개** 중 **2개**가 «역할 행 없는 배정 테넌트» 다.

| 운영자 | 배정 | 그 테넌트의 역할 행 | R2 영향 | 출처 |
|---|---|---|---|---|
| `demo-operator`(demo@demo.com) | `demo-corp` | `SUPER_ADMIN` @ **`demo-corp`**(🔴 `'*'` 아님 — `o.tenant_id` 를 복사) | 없음 | `R__seed_demo_operator.sql:83-87, 96-98` |
| `demo-operator` | **`ecommerce`** | **없음** | 🔴 **영향** | `:155-157` |
| `demo-requester` | `demo-corp` | `SUPER_ADMIN` @ `demo-corp` | 없음 | `:216-224` |
| `demo-assigned-only` | **`demo-corp`** | 없음(역할 0 · 로그인 불가) | 🔴 영향 — 단 로그인 못 하므로 자기 화면 축소는 0. 대신 그룹 «배정만 됨» 회색 처리의 **의미가 사라진다**(F10) | `R__seed_demo_assigned_only_operator.sql:23-28, 58-62` |
| `demo-cs` · `demo-platform` · `demo-viewer` | 없음 | — | R2 무관 — 단 `demo-cs` · `demo-viewer` 는 **홈만으로** assume/콘솔에 닿는다 → D-2(홈 제거)의 영향(F4) | `R__seed_demo_cs_operator.sql:47-48, 102` · `R__seed_demo_viewer_operator.sql:37-47, 85` · `R__seed_demo_platform_operator.sql:78` |

**`demo-operator` 가 `ecommerce` 로 전환했을 때 줄어드는 화면**(지금 실효 범위로 통하고 784 뒤 grant scope {demo-corp} 로 막히는 것 — ① 표 #6~#12, #17): 콘솔 **운영자**(`/operators` 목록) · **감사**(`/audit`) · **계정**(`/accounts` 목록 · 이메일 검색 · 잠금/해제/일괄 잠금 · GDPR 삭제/내보내기 · 세션 종료) · **운영자 초대 목록** · erp 연결용 **운영자 이메일 조회**(빈 목록) · 테넌트 상세 · `ecommerce` 홈 운영자(demo-cs · demo-assigned-only)의 **프로필 편집**. 🔵 지금도 `ecommerce` 에서 막혀 있는 것(grant scope 판정): 그룹 · 파트너십 · 구독 · 진입 정책 · 배정 목록/변경 · 역할/상태 변경(`R__seed_demo_assigned_only_operator.sql:15-17` 이 이미 기록). → 보존 여부 = **OD-3**.

### ④ 라이브 술어 — ⚪ (창 없음)

ADR-082 § Outstanding 그대로: `SELECT COUNT(DISTINCT operator_id) FROM operator_tenant_assignment a JOIN admin_operators o ON o.id = a.operator_id WHERE a.tenant_id <> o.tenant_id`. 이유: DB 가 필요하고 이 세션에 데모 창이 없다. 🔵 정적 기대값(신선 볼륨 시드만이라면): **2**(`demo-operator`→`ecommerce` · `demo-assigned-only`→`demo-corp`, 위 표). 라이브가 2 를 넘으면 그 차이가 방문자가 만든 행이다(시드 주석의 «KNOWN LIMIT»). AC-6 의 «R2 영향 행 라이브 확인» 과 같은 창에서 같이 잰다.

## 2. 지시된 재측정 (그 밖)

| 대상 | 지금 | 784 가 바꾸는 것 | 출처 |
|---|---|---|---|
| 역할 PK 를 읽는 곳 | `PRIMARY KEY (operator_id, role_id)` · JPA `@IdClass` PK 2칸 · 그룹 fan-out 의 «이미 있으면 skip» 이 PK 2칸으로 판정 · 노드 grant 조회 · 전체 삭제 | PK 3칸 · fan-out skip 을 `(op, role, 그룹 테넌트)` 로 | `db/migration/V0004__create_admin_rbac_tables.sql:48` · `rbac/AdminOperatorRoleJpaEntity.java:18, 148-163` · `rbac/AdminOperatorRoleJpaRepository.java:12-13, 23-26, 39-40` · `app/GroupFanOutService.java:128-134` · data-model.md:146, 229 |
| WI-2(역할 행 = 홈)를 쓰거나 믿는 곳 | 명세 · 백필 · 팩토리 javadoc · 역할 변경 · 그룹 fan-out(멤버 홈) · 그룹 멤버 해소 · 시드 3개(`o.tenant_id`) · 시험 5곳 | 불변식 교체(D-1) | data-model.md:138, 142, 144 · `V0026__backfill_admin_operator_roles_tenant_id.sql:20-23` · `AdminOperatorRoleJpaEntity.java:38-41, 80-88, 105, 131` · `app/PatchOperatorRoleUseCase.java:58-62` · `GroupFanOutService.java:51-52, 132-133` · `app/GroupAdminUseCase.java:190, 211, 363` · 시험 `integration/OperatorAdminIntegrationTest.java:290-299` · `rbac/AdminGrantScopeEvaluatorTest.java:159` · `persistence/V0042MigrationShapeTest.java:30, 90` · `application/OrgNodeAdminUseCaseTest.java:126` · `integration/OrgNodeAdminIntegrationTest.java:152` |
| 역할 행을 쓰는 곳(테넌트 값) | 수락 = 초대 테넌트 · 셀프 온보딩 = 새 테넌트(이미 홈 ≠ 개념적으로 «그 회사») · 334 생성 = `'*'` 전용(772 S7) · 노드 grant = 대상 홈 | 수락 · 온보딩은 기존 행에도 같은 값으로(D-12) | `app/OperatorInvitationAcceptanceWriter.java:82-87` · `app/FirstAdminProvisioner.java:97-105` · `app/CreateOperatorUseCase.java:127` · `app/OrgNodeAdminUseCase.java:199`(grep 만) |
| 권한 평가 | `operator → ACTIVE → 역할 행 전부 → 권한 합`, 테넌트 없음 | 앞단(aspect) 합은 유지, 테넌트 판정은 게이트에서(D-4) | `rbac/PermissionEvaluatorImpl.java:39-45, 74-93` · rbac.md:151-193 |
| 캐시 | 키 `admin:operator:perm:{operator_id}` · TTL 10s · 무효화 = 역할 변경 · 그룹 fan-out 만 | 🔴 **상태 변경은 무효화하지 않는다** — 정지된 운영자의 캐시 합이 TTL(≤10s) 동안 산다(평가 원천은 상태를 보지만 캐시 히트는 원천을 안 부른다) · D-5 | `rbac/CachingPermissionEvaluator.java:26, 61-62, 71-84, 91-99, 140-142` · `PatchOperatorRoleUseCase.java:81, 87-95` · `GroupFanOutService.java:145-155` · `PatchOperatorStatusUseCase.java:30-85`(무효화 0) |
| 읽기 게이트 | 실효 범위(배정 ∪ 홈) · 생략 = 홈 · 비플랫폼 = 홈 ≠ `'*'` | grant scope · 생략 규칙 | `app/QueryTenantScopeGate.java:54-84` · `app/TenantScopeResolver.java:48-59` |
| 쓰기 게이트 | 역할 행 `tenant_id` 집합(`'*'` 선검사 · 노드 subtree · 그 밖 `{tenant_id}`) | PK 만 따라간다 — 술어 그대로(ADR 갈래 비교 RBAC 행 그대로) | `rbac/AdminGrantScopeEvaluator.java:79-114, 175-215` · `app/TenantScopeGuard.java:49-96` |
| `PatchOperatorStatusUseCase` | 대상 홈 grant scope · 행 전체 SUSPENDED · 리프레시 토큰 전부 회수 | R3(D-8) + 캐시 무효화 | `app/PatchOperatorStatusUseCase.java:49-67` |
| `PatchOperatorRoleUseCase` | 대상 홈 grant scope → ≤-own(합) → **`deleteByOperatorId`(그 운영자의 모든 행)** → 새 행 전부 `tenant_id = 홈` | 회사 축(D-7). 🔴 `deleteByOperatorId` 는 `group_origin` · `org_node_id` 를 거르지 않는다 → 지금도 역할 변경이 **그룹 fan-out 행과 노드 grant 를 지우고 직접 행으로 다시 쓴다**(F6, 코드 읽기 — 시험 대조는 안 했다) | `PatchOperatorRoleUseCase.java:37-64` · `AdminOperatorRoleJpaRepository.java:23-26` · data-model.md:230 |
| 파트너십 술어 | 도달 = `findActivePartnership(host, partner = operator.tenantId())` · 참여자 추가 = 홈 == 파트너 | 소속 집합(D-11) | `app/OperatorAssignmentCheckUseCase.java:242-246` · `app/ManagePartnershipParticipantUseCase.java:71-74` |
| 그룹 멤버 술어 | 멤버 홈 == 그룹 테넌트 → `422 GROUP_MEMBER_TENANT_MISMATCH` · fan-out 행 테넌트 = 멤버 홈 | 소속 집합 · fan-out 행 테넌트 = 그룹 테넌트(D-11) | `app/GroupAdminUseCase.java:171-176, 189-191` · data-model.md:182, 191 · `GroupFanOutService.java:132-133` |
| 409 ① 초대 수락(OD-1) | 6단계 `oidcSubjectResolver.resolve(account).isPresent()`(상태 무관) → 409 · 7단계 `(초대 테넌트, 이메일)` 운영자 존재 → 409 · 경합 재해석 같은 둘 | D-12 | `app/OperatorInvitationAcceptanceUseCase.java:127-135, 260-267` · 계약 `internal/auth-to-admin.md:234, 267` · `admin-api.md:1658` |
| 409 ② 셀프 온보딩(S1-11) | 같은 술어 · 테넌트 생성 전 | D-12 + OD-1 | `app/SelfServiceOnboardingUseCase.java:55-63` · `onboarding-api.md:80, 93` |
| 발급 단계 «이미 운영자» | `existsByTenantIdAndEmail(초대 테넌트, 이메일)` = **홈** 기준 | 소속 기준(F15) | `app/OperatorInvitationWriter.java:73` |
| 772 AC-6 IT 칸 | `ac6_selfOnboardedOrgAdmin_invites_inviteeBecomesOperator` — 5단계 두 번째 회사 수락 409 + 운영자 행 1 + 초대 PENDING · 6단계 셀프 온보딩 409 + 테넌트 생성 호출 0 | 기대값 교체(걷지 않는다): 5단계 → 200 · 운영자 행 **여전히 1**(같은 행) · `TENANT_X` 역할 행 + 배정 추가 · 기존 `newOrg` 행 바이트 불변 / 6단계 → 201(OD-1 답에 따라 상한) · 같은 테넌트 재시도 409 | `integration/OperatorInvitationAcceptanceIntegrationTest.java:517-533`. 같은 코드를 단언하는 단위 · 슬라이스 시험: `application/OperatorInvitationAcceptanceUseCaseTest.java` · `application/SelfServiceOnboardingUseCaseTest.java` · `presentation/internal/OperatorInvitationAcceptanceControllerSliceTest.java` · auth `AdminOperatorInvitationClientUnitTest.java`(코드 문자열 — 코드가 남으므로 무변경 예상) |
| 콘솔이 행동 테넌트를 보내야 하는 화면 | 운영자 목록 · 감사 · 계정은 **이미** `tenantId` + `X-Tenant-Id` 를 보낸다(위 ①). 새로 필요한 곳: 역할 편집(회사 축 — 헤더는 이미 실림, 의미가 바뀜) · 상태 변경(R3 새 거절 코드 매핑) · «배정 해제» → «이 회사에서 내보내기»(D-9) · 부여 가능 역할(활성 회사별) · `/me` 역할 → nav · 그룹 멤버 선택기(`homeTenantId` 회색 처리 → 소속) | S6 | `console-web/src/features/operators/hooks/use-operator-mutations.ts:56, 80` · `features/operators/api/operators-crud-api.ts:48, 106, 128` · `features/operators/api/operators-self-api.ts:87` · `shared/ui/console-nav-exposure.ts:21` · `app/api/operators/grantable-roles/route.ts:9-24` · `OperatorQueryService.java:132-137`(BE-626 `homeTenantId` 주석) |
| 콘솔 nightly e2e | `nightly-e2e.yml` 콘솔 잡 = `tests/e2e/` 4 스펙(operators-profile · operators-admin-profile · overview-consolidation · sample-visitor-transition) · 픽스처 `seed.sql` = `'*'` SUPER_ADMIN + 홈 `fan-platform` 대상(배정 0) | 정적 기대 무영향 — `'*'` 행위자는 R3 규칙에서도 통한다(D-8). 그룹 · 파트너 화면 e2e 는 **없다** | `.github/workflows/nightly-e2e.yml:1152-1174, 1303-1313, 1391` · `console-web/tests/e2e/fixtures/seed.sql:29-39, 135-215` |

## 3. 새로 찾은 것 (티켓 · ADR 이 적지 않은 것)

| # | 무엇 | 출처 | 귀결 |
|---|---|---|---|
| F1 | 🔴 판정 자리(aspect)와 토큰에 **테넌트가 없다** — «권한 평가에 테넌트를 더한다» 는 aspect 에서 할 수 없다(입력이 없다) | ① 머리말 | 두 단 판정(D-4): aspect = 합(거친 거름) · 테넌트 = 게이트(grant scope). 지금 쓰기가 이미 이 모양이다(`TenantScopeGuard`) |
| F2 | 🔴 누수 자리 = 읽기 게이트 + 그 게이트를 쓰는 **쓰기 5종** + 게이트 **사본 4곳** | ① 표 #10 · #6 · #11 · #12 · #17 | 사본을 게이트 하나로 합친다(D-4). AC-1 은 운영자 목록 · 감사만 단언하지만 S3 시험은 잠금 쓰기 1칸을 더한다 |
| F3 | ≤-own(`RoleGrantGuard`)이 권한 **합**을 본다 — b 에서 A 의 권한으로 B 에서 상향 부여 | `app/RoleGrantGuard.java:130-142` | D-6 |
| F4 | 🔴🔴 **R1 × D3 × assume 의 충돌** — assume 의 실효 범위에 **홈이 들어 있다**(`TenantScopeResolver.java:53-58` · `OperatorAssignmentCheckUseCase.java:184-187`). 떠난 회사가 홈이면 배정을 지워도 A assume 이 통한다 → AC-2 빨강. ADR 갈래 비교 «assume 변화 0» 행은 **떠난 회사가 홈인 경우에 틀리다** | ADR-082 갈래 비교 assume 행 ↔ R1 «판정에서 읽지 않는다» ↔ D3 «A assume 거절» | R1 · D3 의 명시 문장을 따른다(D-2): 홈을 실효 범위에서 빼고 **소속 = 배정 행**만. 전제 = 백필(홈 배정 없는 기존 행) + 시드 `demo-cs` · `demo-viewer` 배정 추가 + 홈만으로 assume 하는 IT 픽스처 수선. 🔵 HARDSTOP 아님 — 상위 문장(R1 · D3)이 명시적이고 표 칸은 «지금 코드의 서술» 이다. 소유자가 보도록 여기 적는다 |
| F5 | 🔴 감사 `tenant_id` = 행위자 홈이고 테넌트 감사 조회가 그 칸으로 거른다 | ② | D-13 |
| F6 | 역할 변경의 전체 삭제가 그룹 fan-out 행 · 노드 grant 를 지우고 직접 행으로 되쓴다(784 이전부터 — data-model.md:230 «cascade 는 직접 grant 를 안 건드린다» 의 거울상) | 위 § 2 `PatchOperatorRoleUseCase` 행 | D-7 이 같이 고친다(T 의 **직접** 행만 교체) |
| F7 | 상태 변경이 권한 캐시를 무효화하지 않는다(≤10s) | 위 § 2 캐시 행 | D-5 |
| F8 | 🔴 `TENANT_ADMIN` 에 `audit.read` 가 **없다** — AC-1 의 «A 의 같은 감사 조회는 통한다» 는 `TENANT_ADMIN` 하나로는 A 에서도 403 이다 | rbac.md:107, 119 | AC-1 픽스처: A = `TENANT_ADMIN` + `SUPPORT_READONLY`(또는 `audit.read` 를 가진 역할). AC 문장은 고치지 않고 S3 기록에 적는다 |
| F9 | 계약 경로 오기 — `console-registry-api.md` 는 `projects/platform-console/…` 이 아니라 **`projects/iam-platform/specs/contracts/http/console-registry-api.md`** 다(282-283 문장은 그 파일에 있다) | Glob · `console-registry-api.md:282-283` | 계약 표에 실제 경로로 적는다(티켓 Scope 문구는 원문 유지) |
| F10 | b 에서 소속 = 배정이면 `demo-assigned-only` 같은 «배정만» 운영자가 **그룹에 넣을 수 있게** 된다 — PC-FE-319 의 «배정만 됨» 회색 처리(`homeTenantId` 기반)와 그 시연 시드의 목적이 사라진다 | `R__seed_demo_assigned_only_operator.sql:9-19` · `OperatorQueryService.java:132-137` | 맞는 결과다 — 그룹이 그 사람에게 그 회사 역할을 주는 길이 된다(R2 아래 «소속 · 역할 0»). 콘솔 선택기 술어 · 시드 주석 · `DemoOperatorSeedIntegrationTest` 핀을 S4 · S6 에서 고친다 |
| F11 | 데모 `SUPER_ADMIN` 은 `'*'` 가 아니라 **`@demo-corp`** 행이다 | `R__seed_demo_operator.sql:83-87` | R3 의 «`SUPER_ADMIN`» = **역할 이름이 아니라 `'*'` grant scope**(D-8). 아니면 데모 운영자가 다회사 운영자를 정지할 수 있게 된다 |
| F12 | T 의 관리자는 **어느 회사의 운영자든** T 에 배정할 수 있다(대상 제한 없음) — b 에서 이것은 **본인 동의 없는 소속 추가**가 되고, 이어서 T 의 역할 부여가 가능해진다 | `app/ManageOperatorAssignmentUseCase.java:51-93` · 그룹 `TENANT_ASSIGNMENT` fan-out `GroupAdminUseCase.java:244-286` | **OD-2** |
| F13 | `'*'` 운영자에게 배정을 만드는 쓰기가 막혀 있지 않다(해소기가 무시하므로 지금은 무해) | `ManageOperatorAssignmentUseCase.java:76-89` · `TenantScopeResolver.java:50-52` | D2 «쓰기에서 거절» → D-14 |
| F14 | 🔴 셀프 온보딩 409 는 지금 **신원당 소유 상한 1** 의 역할을 겸한다 — 걷으면 773 이 상한을 들이기 전까지 측면 있는 계정이 회사를 **무제한** 만든다(콘솔 토큰을 가진 사람 = `iam` 계정 + 측면 있는 풀 계정, 772 D-5) | `SelfServiceOnboardingUseCase.java:55-63` · `FirstAdminProvisioner.java` 상한 없음 · ADR-082 D4(세는 단위만, 값은 773 AC-0) | **OD-1** |
| F15 | 발급 · 수락 7단계의 «이미 이 테넌트 운영자» 가 **홈** 기준 — b 에서 B 소속인 A-홈 사람은 통과했다가 수락에서 막힌다 | `OperatorInvitationWriter.java:73` · `OperatorInvitationAcceptanceUseCase.java:133, 264` | 소속 기준으로(D-12). `(tenant_id, email)` 유니크(V0025)는 새 행 생성에만 남는다 |
| F16 | 콘솔 적격 = `oidc_subject ∧ ACTIVE` 뿐 — 티켓 Edge 2 의 «측면 0 = 772 적격 거절로 떨어진다» 는 술어를 바꾸지 않으면 **거짓**(소속 0 인 ACTIVE 행이 빈 콘솔에 들어간다) | `app/OperatorConsoleEligibilityQueryUseCase.java:33-38` · `TokenExchangeService.java:89-104` | D-10 |
| F17 | 2단계 요구가 «홈 ∪ 배정» 의 정책 켜진 테넌트를 본다 | `TokenExchangeService.java:106-115`(주석) | 해소기를 따라 소속으로 바뀐다(D-2) — 별도 수정 없음, 시험 1칸 |
| F18 | 파트너십 참여자 행은 «떠난 회사의 사람» 을 남긴다 — 술어를 소속으로 바꾸면 비활성이 되지만 재소속 시 **되살아난다** | `ManagePartnershipParticipantUseCase.java:71-74` · `OperatorAssignmentCheckUseCase.java:250-255` | 내보내기가 같이 지운다(D-9, ADR-045 D6 관계 단위 회수와 같은 방향) |

## 4. 구현자 결정 (ADR · 라이더 · 티켓이 «어떻게» 를 정하지 않은 것)

| D | 결정 | 구현하는 것 |
|---|---|---|
| **D-1 소속의 정의** | 소속 = `operator_tenant_assignment` 행(ADR 갈래 비교 assume 행 «소속 회사 = 배정 행»). 새 불변식(WI-2 대체): **비-`'*'` 역할 행의 `tenant_id` ∈ 그 운영자의 소속** · `'*'` 운영자는 `'*'` 역할 행만 · 소속 0. 노드 grant 행: 스코프는 `org_node_id`(불변), `tenant_id` = 부여 당시의 소속 테넌트(기록 · 내보내기 단위) | R1 · R4 · D2 |
| **D-2 홈을 판정에서 뺀다** | V0051 백필: 비-`'*'` 운영자마다 (홈) · (역할 행 테넌트) 중 배정이 없는 것에 배정 행을 만든다(멱등). 그 뒤 `TenantScopeResolver` = **배정만**(`'*'` 그대로). 홈 칸은 남는다 — 화면 표기는 «처음 소속된 회사» 이지 «소속» 이 아니다(Edge 1). 🔴 R__ 시드는 V 마이그레이션 **뒤에** 돌아 백필이 못 덮으므로 `demo-cs`(`ecommerce`) · `demo-viewer`(미등록 홈 `demo-viewer`)에 배정 행을 시드에서 더하고 `DemoOperatorSeedIntegrationTest` 의 «배정 0» 핀을 고친다 | R1 · D3 · F4 |
| **D-3 PK** | V0051 한 문장 `DROP PRIMARY KEY, ADD PRIMARY KEY (operator_id, role_id, tenant_id)` — 선행 칸 `operator_id` 가 남아 FK 인덱스 요건을 깨지 않는다. 데이터 안전(ADR 갈래 비교 «기존 데이터 이동» 행). 형태 시험은 V0042/V0044 선례 | ADR 갈래 비교 스키마 행 |
| **D-4 두 단 판정** | aspect = 지금 그대로(권한 합 · 캐시 키 운영자 — «어딘가에 이 권한이 있나» 의 거친 거름). 테넌트 판정 = `QueryTenantScopeGate` 를 **grant scope(그 권한)** 로 다시 겨누고, 사본 4곳을 그 게이트로 합친다. 권한 키 없는 읽기(① #8b · #9)는 «역할 행이 하나라도 있는 테넌트». 생략 규칙은 ① 머리말 | R2 · 새로 생기는 위험 1 · Failure Scenario 2 |
| **D-5 캐시** | 테넌트 판정은 `AdminGrantScopeEvaluator`(DB, 지금도 캐시 없음 — `:79-114`)가 답하므로 **테넌트를 모르는 캐시 값이 테넌트 질문에 답하는 일이 없다**. 합 캐시는 운영자 키 그대로 두고 역할 변경 · 내보내기 · 소속 추가 · **상태 변경**(F7)에서 무효화. 🔵 AC-5 의 «(캐시 키 = 운영자 × 테넌트)» 는 «테넌트 판정을 캐시한다면 그 키» 로 읽는다 — 성능 때문에 테넌트 판정 캐시를 들이면 키는 `admin:operator:perm:{operator_id}:{tenant_id}` 이고 무효화는 그 회사 키 + 합 키(행 전체 변경은 소속을 DB 에서 열거해 지운다 — SCAN 금지). S4 시험은 AC-5 를 판정 값으로 단언한다 | Failure Scenario 1 · AC-5 |
| **D-6 ≤-own 을 회사 안에서** | `RoleGrantGuard` 의 «보유 권한» = 대상 테넌트 T 에서 보유한 권한(T 의 역할 행 + `'*'` 행 + T 를 덮는 노드 행) | F3 |
| **D-7 역할 변경 = 회사 축** | 행동 테넌트 = `X-Tenant-Id`. 대상이 T 소속이 아니면 `404 OPERATOR_NOT_FOUND`(열거 안전 · 새 코드 없음). 교체 범위 = `(op, *, T) ∧ group_origin IS NULL ∧ org_node_id IS NULL` — 다른 회사 행 · 그룹 행 · 노드 행 불변(F6). `'*'` 대상은 `'*'` 행만. 비-`'*'` 대상에게 `'*'` 행 거절(D2) | D3 · AC-4 · Failure Scenario 3 |
| **D-8 R3 규칙 = 행 전체 속성 전부** | 상태 · 프로필(`finance_default_account_id`) · 신원 링크/해제: 대상 소속 = {T} 이면 T 의 grant scope 에 해당 권한 · 둘 이상 또는 0 이면 **`'*'` grant scope 만**(F11 — 역할 이름이 아니다). 거절 = 새 코드 `403 OPERATOR_HAS_OTHER_MEMBERSHIPS`(콘솔이 «다른 회사에도 소속 — 이 회사에서 내보내기를 쓰세요» 로 매핑). 정지 성공 시 캐시 무효화 | R3 · AC-4 |
| **D-9 «이 회사에서 내보내기»** | 기존 `DELETE /api/admin/operators/{operatorId}/assignments/{tenantId}` 의 의미를 넓힌다(새 엔드포인트 없음 — «소속 = 배정 행» 이므로 소속 삭제가 그 회사 측면 전부를 가져가야 역할 행만 남는 무의미한 상태가 안 생긴다). 한 트랜잭션: 배정 + T 역할 행(직접 · 그룹 · T 로 기록된 노드) + T 그룹의 멤버십과 그 fan-out 행 + `partner = T` 파트너십의 참여자 행 + 감사 `OPERATOR_ASSIGNMENT_DELETE`(detail 에 지운 수) + 캐시 무효화. 판정 = 지금 그대로 T 의 grant scope(`ManageOperatorAssignmentUseCase.java:110`). 콘솔 문구 «배정 해제» → «이 회사에서 내보내기». 🔵 대안(새 colon-verb + 기존 해제는 역할 행이 있으면 409)은 코드 하나 · 엔드포인트 하나가 더 생겨 기각 | D3 · Edge 2 · Edge 3 · AC-2 |
| **D-10 소속 0 = 행은 남기고 문을 닫는다** | 마지막 소속을 내보내도 행은 `ACTIVE` 로 남는다 — 재초대 · 재온보딩이 같은 행에 소속을 더하고(D-12), 감사 해소가 행을 요구한다(`AdminActionAuditWriter.java:53-64` fail-closed). 대신 콘솔 적격 · 토큰 교환 술어 = `ACTIVE ∧ ('*' ∨ 소속 ≥ 1)` 한 자리(772 D-5 «교환과 같은 술어»). 그래서 Edge 2 의 «772 적격 거절로 떨어진다» 가 참이 된다(F16). `demo-viewer` 는 미등록 홈 배정(D-2)으로 지금 시연 동작 유지 | D3 · Edge 2 |
| **D-11 파트너십 · 그룹 술어** | 도달 = `partner ∈ 소속` 인 ACTIVE 파트너십 · 참여자 추가 = `partner ∈ 대상 소속`. 그룹 멤버 = `group.tenant ∈ 대상 소속`(위반 코드 `422 GROUP_MEMBER_TENANT_MISMATCH` 그대로, 의미만 «소속») · fan-out 역할 행 `tenant_id` = 그룹 테넌트 · skip 판정 PK 3칸 | ADR 갈래 비교 협력사 · 그룹 행 |
| **D-12 두 409 → 소속 추가** | 초대 수락 · 셀프 온보딩 공통: 기존 행이 `ACTIVE` ∧ 비-`'*'` ∧ 대상 테넌트가 아직 소속 아님 → 한 트랜잭션에 대상 테넌트 역할 행 + 배정만 더한다(기존 회사 행 **바이트 불변**, 홈 · `oidc_subject` 쓰기 0). 같은 테넌트 이미 소속 · `'*'` 행(D2) · 비-`ACTIVE` 행 → `409 OPERATOR_ALREADY_PROVISIONED`(**코드 유지**, 계약 의미를 «이 측면은 이 소속을 받을 수 없다» 로 좁힌다 — IdP · 콘솔 매핑 무변경). 온보딩은 여전히 **테넌트 생성 전** 판정 + OD-1 상한. 발급 · 수락 7단계 «이미 운영자» = 소속 기준(F15). 신원 링크는 새 행일 때만 | D2 · D4 · AC-3 |
| **D-13 감사 `tenant_id`** | 테넌트로 판정된 비플랫폼 행위 = **행동 테넌트**, `'*'` 행위자 = `'*'`(플랫폼 교차 조회 `:55-65` 그대로) · 자기 흐름(로그인 · 리프레시 · 자기 프로필) · DENIED 행 = 홈 그대로(R1 «감사 기본값» · DENIED «no leak» 유지). 과거 행 백필 없음(audit-heavy — 감사 행 불변) | R1 · F5 |
| **D-14 D2 쓰기 거절** | `'*'` 대상에 배정 생성 · 그룹 멤버 추가 · 비-`'*'` 역할 행 → 거절 · 비-`'*'` 대상에 `'*'` 역할 행 → 거절. 코드 = 772 D-2 의 `'*'` 초대 거절과 같은 것(S1 이 확정) | D2 · AC-3 |
| **D-15 R4** | `permission_set_id` 무접촉(쓰기 · 읽기 0) | R4 |
| **D-16 `/me`** | `memberships[{tenantId, roles[]}]` 가산 · `roles` = 활성 회사(① #1) | 콘솔 nav |

## 5. 소유자 결정 (OD) — ADR-082 · 라이더 · 티켓이 정하지 **않은** 것

| OD | 질문 | 왜 ADR/티켓이 답하지 않나 | 권고 (🔴 구현자 선호 — 소유자 결정 아님) | 대안 · 결과 |
|---|---|---|---|---|
| **OD-1** | 셀프 온보딩의 두 번째 회사를 784 에서 **상한 없이** 여는가 (F14) | ADR-082 D4 는 세는 단위만 정하고 값은 773 AC-0 에 넘겼다. 티켓 AC-3 은 셀프 온보딩 소속 추가를 요구한다 — 둘을 합치면 784 ~ 773 사이에 무제한 창이 생긴다 | **784 가 세는 장치를 싣는다** — D4 단위(그 행의 `TENANT_ADMIN` 역할 행이 가리키는 서로 다른 테넌트 수) + 설정 키(가칭 `admin.onboarding.max-owned-tenants-per-operator`) **기본값 2**(AC-3 이 필요한 모양이 정확히 둘). 초과 → 새 코드(S1 이 이름 확정). 773 AC-0 이 값을 다시 정한다 | (b) 셀프 온보딩 409 는 773 까지 **유지** — AC-3 의 온보딩 절반을 773 으로 옮긴다(AC 문장 수정 필요) / (c) 상한 없음 — 773 전까지 무제한 |
| **OD-2** | 회사 관리자가 **다른 회사 사람을** 자기 회사에 배정(= b 에서 소속 추가)하는 것을 그대로 두는가 (F12) | ADR-082 는 배정의 의미를 «소속» 으로 바꿨지만 배정을 **누가** 만들 수 있는지는 다루지 않았다. ADR-080 D6 은 회사 소속의 입구를 «초대 → 인증된 본인 수락» 으로 정했다 | **그대로 두고 기록한다** — ADR-020 D1(ACCEPTED)의 «내 직원에게 내 테넌트 접근을 준다» 동작이고, T 안에 갇히며(R2 로 관리 권한 0에서 시작), 그 사람의 다른 회사 행은 바이트 불변이다. 동의 문제는 지금도 있다(도메인 평면 assume). 원하면 후속 ADR | (b) 대상이 아직 T 소속이 아니면 배정 생성은 `'*'` 만 — 회사는 초대로만 사람을 들인다(그룹 `TENANT_ASSIGNMENT` 도 같은 제한) / (c) 배정 생성에 본인 수락 단계 신설(가장 큼) |
| **OD-3** | `demo-operator` 의 `ecommerce` 관리 화면(③)을 보존하는가 | 티켓 Scope 는 «한 행에 두 회사 역할 행 + 배정 하나» 만 적고 어느 역할인지 정하지 않았다. 지금 화면을 **정확히** 재현하는 시드 역할 조합은 없다(운영자 목록 = `operator.manage` 는 `TENANT_ADMIN` 에만 있고 그것은 그룹 · 파트너십 · 역할 관리를 **새로** 연다 — rbac.md:103-109) | **`SUPPORT_READONLY` + `SUPPORT_LOCK` @ `ecommerce`** — 계정 · 감사 · 잠금 화면이 살고, `ecommerce` 운영자 목록은 403 이 된다. 데모가 «한 사람 · 회사마다 다른 역할» 을 그대로 보여 준다(b 의 시연). S3(축소를 만드는 PR)에서 같이 시드 | (b) 시드 안 함 — ③ 의 화면이 `ecommerce` 에서 전부 403 / (c) `TENANT_ADMIN` @ `ecommerce` — 운영자 목록은 살지만 오늘 닫혀 있던 그룹 · 파트너십 · 역할 관리가 열린다(공개 자격증명 계정이므로 방문자 변조 면이 넓어진다) |

## 6. 계약 먼저 — 변경 표 (S1, 코드 0)

| 파일 | 바꾸는 것 |
|---|---|
| `projects/iam-platform/specs/contracts/http/admin-api.md` | 행동 테넌트 규칙(① 표 · 생략 규칙)을 공통 절로 · `GET /me` `memberships` · `PATCH …/roles` 회사 축(D-7) · `PATCH …/status` · `…/profile` · `identity:*` R3(D-8, 새 코드) · `DELETE …/assignments/{tenantId}` = 내보내기(D-9) · `GET …/grantable-roles` 회사별 · `:1658` «한 사람 = 한 회사» 행 → «ADR-082 = b · 소속 추가» · 초대 발급 «이미 운영자» = 소속 · D2 거절(D-14) · (OD-1 답) 온보딩 상한 코드 |
| `projects/iam-platform/specs/services/admin-service/rbac.md` | Permission Evaluation(`:151-193`) 두 단 판정 · «권한 키 없이 테넌트 게이트만» 표(`:84-93`) 술어 → «역할 행 있는 테넌트» · Target-Tenant 절(`:242-298`) «대상 테넌트 해소» 의 «대상 operator 의 home» 문구 → 소속 · Grant-Menu(`:313-333`) 회사 안 ≤-own · Cross-Org(`:338-` · `:351` «home = partner») · Group(`:447` «home == group») · Caching(`:502-`) 무효화 대상에 상태 · 내보내기 |
| `projects/iam-platform/specs/services/admin-service/data-model.md` | `admin_operator_roles` `tenant_id` 설명(`:138`) · WI-2 불변식(`:142`) → D-1 불변식 · org-node 불변식(`:144`) 의 «WI-2» 참조 · PK(`:146`) 3칸 · 그룹 멤버 불변식(`:182, 191`) · fan-out idempotence(`:229`) PK 문구 · `operator_tenant_assignment` = 소속 · 홈 칸 = R1 · V0051 마이그레이션 목록 |
| `projects/iam-platform/specs/contracts/http/onboarding-api.md` | `:80` 409 의미 좁힘 · `:85` Side Effects(기존 행이면 소속 추가) · `:93` 문장 → ADR-082 b · (OD-1) 상한 |
| `projects/iam-platform/specs/contracts/http/internal/auth-to-admin.md` | 수락 판정 6단계(`:234`) · 오류(`:267`) 의미 좁힘 · 콘솔 적격(`:131-`) 술어 = `ACTIVE ∧ ('*' ∨ 소속 ≥ 1)`(D-10) — 🔵 지시 목록에 없지만 같은 409 의 계약 정본이라 필요 |
| `projects/iam-platform/specs/features/multi-tenancy.md` | `:635` «한 사람 = 한 회사 … 773 착수 전 ADR» → «ADR-082 = b — 행 하나 · 회사별 소속 · 역할» |
| `projects/iam-platform/specs/contracts/http/console-registry-api.md`(🔴 실제 경로 — F9) | `:282-283` «a single tenant slug ⇒ only that slug» → «소속(배정 행) 집합» (784 이전 구현 = 배정 ∪ 홈 · 784 뒤 = 배정) |
| `projects/platform-console/specs/contracts/console-integration-contract.md` | 운영자 화면 패리티(역할 편집 회사 축 · 내보내기 · R3 코드 매핑 · `/me` `memberships` · 그룹 선택기 술어) |
| `platform/error-handling.md`(`:698`) + `rules/domains/saas.md`(`:96`) | `OPERATOR_ALREADY_PROVISIONED` 설명 갱신 · 새 `OPERATOR_HAS_OTHER_MEMBERSHIPS`(403) · (OD-1) 상한 코드 — 🔴 공유 경로: 프로젝트 비종속 문구 확인(HARDSTOP-03) |
| `docs/adr/ADR-MONO-020-…md` · `ADR-MONO-024-…md` | «082 b 가 D5(배정마다 권한 세트 → 역할 행 테넌트 축) · D2(역할 행 테넌트 의미)를 바꾼다» 덧붙임 — 본문 바이트 그대로 |

## 7. 슬라이스

머지 순서: **S1 → S2 → S3 → S4 → S5 → S6**. 전부 admin-service 안의 같은 파일군(게이트 · 유스케이스 · `AdminExceptionHandler`)을 건드리므로 **한 worktree 직렬**(CLAUDE.md § Shared-file task series). 각 슬라이스는 그 자체로 `main` 초록이어야 한다.

| S | 무엇 (한 PR) | 서비스 | 이 슬라이스의 단언 | 티켓 AC | 크기 · 모델 |
|---|---|---|---|---|---|
| **S1** 계약 · 명세 | § 6 전부 · OD 답 반영 · ADR-020/024 덧붙임 | 문서만 | 새 코드가 error-handling 에 먼저 · rbac 새 권한 키 없음 · 계약이 D-1~D-16 을 보인다 | (AC-0 결정 고정) | M · **Opus** |
| **S2** 스키마 + 소속 기반 | V0051(PK 3칸 · 소속 백필) · JPA PK · `TenantScopeResolver` 홈 제거(D-2) · 콘솔 적격 · 교환 술어(D-10) · 파트너십 도달 술어(D-11 앞 절반) · 시드 `demo-cs` · `demo-viewer` 배정 + 핀 · 홈만으로 assume 하던 IT 픽스처 수선 | admin · (seed) | 형태 시험(PK · 멱등 백필) · 홈만 있던 행이 백필 뒤 assume 그대로(대조군) · 소속 0 → 적격 false · 교환 401 · `'*'` 그대로 | — (AC-2 의 전제) | M · **Opus** — 🔴 IT 픽스처 파급이 가장 큰 슬라이스(역할 테이블을 만지는 시험 29파일, 홈-only assume 픽스처 수는 S2 AC-0 에서 센다) |
| **S3** 읽기 축 | 게이트 → grant scope · 사본 4곳 합침 · 생략 규칙 · 권한 키 없는 읽기 술어 · ≤-own 회사 안(D-6) · `grantable-roles` · `/me` · 감사 `tenant_id`(D-13) · 상태 변경 캐시 무효화 · (OD-3) `demo-operator` 시드 | admin · (seed) | 🔴 **AC-1** 대조군(실패 먼저 — F8 픽스처) + 잠금 쓰기 1칸 · 단일 회사 운영자 생략 기본값 바이트 동일(대조군) · B 행위의 감사 행이 B 조회에 보인다 | **AC-1** | L · **Opus** |
| **S4** 쓰기 축 · 내보내기 | 역할 변경 회사 축(D-7, F6 수선) · R3(D-8) · 내보내기(D-9) · 그룹 술어 · fan-out 테넌트(D-11) · 참여자 술어 · D2 거절(D-14) · 캐시 무효화 지점 · `demo-assigned-only` 주석 · 핀 | admin · (seed) | 🔴 **AC-2** 대조군(같은 시험: A assume · A 관리 거절 / B assume · B 관리 성공 / 스토어 · 팬 토큰 그대로 — auth 쪽은 계정 잠금 없음을 단언) · **AC-4**(소속 둘 → 회사 관리자 403 · `'*'` 200 · 소속 하나 = 지금과 같음 · B 행 바이트 불변) · **AC-5**(T 변경 뒤 T 판정 다음 요청에서 바뀜 · U 판정 그대로) · 그룹/파트너 행 걷힘 · bite: 내보내기에서 역할 행 삭제를 빼면 AC-2 의 «A 관리 거절» 칸만 빨강 | **AC-2 · AC-4 · AC-5** | L · **Opus** |
| **S5** 두 409 → 소속 추가 | 수락 · 온보딩(D-12) · 발급 «이미 운영자» 소속 기준(F15) · (OD-1) 상한 · 772 AC-6 IT 칸 기대값 교체 · 단위/슬라이스 시험 | admin | **AC-3**: 기존 행에 새 테넌트 역할 행 + 배정 · 기존 회사 행 바이트 불변 · 같은 테넌트 409 · `'*'` 행 409 · 풀 행에 `'*'` 거절 · (OD-1) 상한 초과 거절 · 772 AC-6 5 · 6단계 새 기대값 | **AC-3** | M · **Opus** |
| **S6** 콘솔 | 역할 편집(활성 회사) · «이 회사에서 내보내기» · R3 코드 매핑 · `grantable-roles` · `/me` nav · 그룹 멤버 선택기(소속) · 원장 사본(permission-map · iam-guide · sample fixtures `shared/sample/fixtures/iam.ts:256-428`) · e2e 디렉터리 grep | **console-web** | 401 만 «운영자 아님» 불변 · 새 코드 문구 · e2e grep 결과 기록 · 머지 뒤 첫 nightly 콘솔 잡 확인 | **AC-6**(코드 · nightly) — 라이브 칸은 다음 데모 창 ⚪ 허용 | M · **Sonnet**(화면) — 오류 매핑만 Opus 검토 |

- **console-web 을 건드리는 슬라이스 = S6 하나**(S1 은 콘솔 계약 문서만). S6 은 S3 · S4 의 계약 표면(새 코드 · `/me` · 내보내기 의미)이 `main` 에 있어야 착수한다.
- **773 을 막지 않는 조건**: ① D-12 의 «기존 행에 소속 추가» 가 한 자리(수락 · 온보딩 공용)라 773 «테넌트 생성» 이 그 자리를 부른다 ② OD-1 의 세는 장치가 773 AC-0 의 값만 받으면 된다 ③ 773 AC-3 의 «기존 테넌트 권한 불변» 은 S5 의 바이트 불변 단언을 재사용한다.

## 8. AC-0 판정

- 게이트(772 `done/`) **참** · ① 23행 전수 + API 별 기준 · ② 출처 3종 + 조회 칸 · ③ 시드 배정 4행 중 **2행** + 줄어드는 화면 이름 · ④ ⚪(창 없음, 술어 · 정적 기대 2 기록). 지시된 재측정 14종 § 2. ⇒ **AC-0 ✅**.
- 🔴 착수 조건: **OD-1 · OD-2 · OD-3** 소유자 답 → S1 계약에 반영. 답 전에 S1 을 머지하지 않는다(OD-1 은 S1 · S5 의 오류 코드 · 상한을, OD-3 은 S3 시드를 정한다).

## 소유자 결정 확정 (2026-10-11 UTC)

선택창 응답(셋 다 추천과 같음 — 이 결정들은 ADR 수락이 아니라 티켓 범위 결정이라 정확형 문장을 요구하지 않는다):

| OD | 결정 | 걸리는 슬라이스 |
|---|---|---|
| OD-1 | **784 에서 신원당 상한 카운터 · 설정 기본값 2** — 세는 단위 = ADR-082 D4(그 행의 `TENANT_ADMIN` 역할 행이 가리키는 서로 다른 테넌트 수). 실제 값은 773 AC-0 이 정한다 | S5 |
| OD-2 | **지금대로 두고 기록** — 회사 관리자의 다른 회사 운영자 배정은 그대로(b 에서 동의 없는 소속 추가가 됨). 위험을 계약(`admin-api.md` § assignments)과 이 티켓에 명시 | S1(문구) |
| OD-3 | **`demo-operator` 의 `ecommerce` = `SUPPORT_READONLY` + `SUPPORT_LOCK`** — 계정 · 감사 · 잠금 유지, ecommerce 운영자 목록은 403 | S3(시드) |

