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
- [x] **AC-1** — 🔴 **대조군 — 권한 누수**: A 에서 `TENANT_ADMIN`, B 는 배정만(역할 없음) → B 의 운영자 목록 · 감사 조회가 **거절**되고, 같은 시험에서 A 의 같은 조회는 통한다. 실패 쪽을 먼저 단언한다(지금 코드에서 이 앞 절반은 빨갛다 — 그것이 «전» 상태). — ✅ 2026-10-13 UTC (S3): 단위 칸 통과 + bite 빨강 확인 · IT 칸 작성(`OperatorMembershipReadAxisIntegrationTest`) — IT 실행은 ⚪ CI(이 호스트 Docker 없음). § S3 기록.
- [x] **AC-2** — 🔴 **대조군 — 한 회사만 떠나기**(ADR-082 D3): 같은 사람이 A · B 운영자 → A 에서 내보내기 → 같은 시험에서 A assume · A 관리 API 거절 · B assume · B 관리 API 성공 · 같은 계정의 스토어 · 팬 토큰 그대로. — ✅ 2026-10-13 UTC (S4): 단위 칸 통과(내보내기 범위 · B 바이트 불변 · 마지막 관리자 409) + bite 빨강 확인 · IT 칸 작성(`OperatorMembershipWriteAxisIntegrationTest#ac2_leaveOneCompany`) — IT 실행은 ⚪ CI(이 호스트 Docker 없음). «토큰 그대로» 는 admin 쪽 단언(계정 서비스 호출 0 · `ACCOUNT_*` 감사 0 · 운영자 행 ACTIVE · `oidc_subject` 그대로)이다 — auth 코드는 건드리지 않았다. § S4 기록.
- [x] **AC-3** — 두 번째 회사 소속 추가(초대 수락 · 셀프 온보딩) → 기존 행에 새 테넌트 역할 행 + 배정 · 기존 회사 역할 행 **바이트 불변** · 같은 테넌트 재소속 409 · `'*'` 행에 회사 소속 쓰기 거절 · 풀 행에 `'*'` 거절(D2). 772 AC-6 IT 의 «두 번째 회사 409 · 셀프 온보딩 409» 칸은 **바뀐 기대값으로 고친다**(걷지 않는다). — ✅ 2026-10-13 UTC (S5): 단위 칸 통과(`OperatorMembershipAdditionTest` · `OperatorOwnedTenantLimitTest` · `SelfServiceOnboardingUseCaseTest$ExistingFacet` · `OperatorInvitationAcceptanceUseCaseTest$MembershipAddition` · `OperatorInvitationAcceptanceWriterTest` · `FirstAdminProvisionerTest`) + bite 빨강 확인 · IT 칸 고쳐 씀(`OperatorInvitationAcceptanceIntegrationTest#ac6_*` 5 · 6단계 새 기대값 + 7단계 · `#platformAndSuspendedFacets_refusedBothPaths`) — IT 실행은 ⚪ CI(이 호스트 Docker 없음). § S5 기록.
- [x] **AC-4** — 정지(R3): 소속 둘 이상인 운영자의 행 정지는 회사 관리자에게 거절 · `SUPER_ADMIN` 은 성공 · 소속 하나면 지금과 같다. 역할 변경이 다른 회사의 역할 행을 바꾸지 않는다. — ✅ 2026-10-13 UTC (S4): 단위 칸 통과(`OperatorRowScopeGuardTest` · `PatchOperatorRoleUseCaseTest` · `UpdateOperatorProfileUseCaseTest`) + bite 빨강 확인 · IT 칸 작성(`#ac4_rowWideRule` · `#ac4_roleEditInA_leavesBByteUnchanged`) — IT ⚪ CI. «`SUPER_ADMIN`» = `'*'` grant scope(F11). § S4 기록.
- [x] **AC-5** — 권한 캐시: 한 회사의 역할 변경 · 내보내기 뒤 그 회사 판정이 다음 요청에서 바뀌고 다른 회사의 캐시 판정은 그대로(캐시 키 = 운영자 × 테넌트). — ✅ 2026-10-13 UTC (S4): D-5 읽기대로 «판정 값» 으로 단언 — 단위 칸(역할 변경 · 내보내기 · 소속 추가가 합 캐시를 무효화) 통과 · IT 칸 작성(`#ac5_perCompanyDecisionNextRequest` — A 다음 요청 403 · B 200 · 내보내기 뒤 `PERMISSION_DENIED` 로 캐시 무효화를 판별) — IT ⚪ CI. 테넌트 판정 캐시는 두지 않았다(D-5). § S4 기록.
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

---

# S1 기록 (2026-10-11 UTC)

> 분석=Opus 5.5 (api-designer) · **코드 0 · 계약/명세만** · worktree `feat/mono-784-s1-contracts`(origin/main 에서). 근거는 위 § AC-0 기록(F1~F18 · D-1~D-16) · § 소유자 결정 확정(OD-1~OD-3). 선례 = `TASK-MONO-772` § S1 기록.
> **AC 체크박스는 건드리지 않았다** — S1 은 AC 를 닫지 않는다(§ 7 표 S1 행: «AC-0 결정 고정»). 이 슬라이스의 완료 조건 셋: ① 새 코드가 `platform/error-handling.md` 에 먼저 등록 ② rbac 새 권한 키 없음 · seed 행렬 무변경 ③ 계약이 D-1~D-16 · OD-1~OD-3 을 보인다 — 아래 § 1 · § 2 가 그 근거다.

## 1. 쓴 것 (파일 · 절) — 어느 AC · 슬라이스를 위한 줄인가

| 파일 | 절 | 무엇 | 결정 · AC · 구현 슬라이스 |
|---|---|---|---|
| `projects/iam-platform/specs/contracts/http/admin-api.md` | § Authorization Model · § Tenant Confinement · **§ 행동 테넌트 (신설)** | 두 단 판정 · 용어(소속 · 홈 R1 · 회사별 역할 · grant scope · 행동 테넌트) · **생략 규칙**(명시값 > `{'*'}`→`'*'` > 하나 → 그것 > 0 → 403 > 둘 이상 → `400 VALIDATION_ERROR`) · **엔드포인트별 23행 표**(AC-0 ①, 슬라이스 열) · 감사 `tenant_id` = 행동 테넌트(D-13) · **행 전체 규칙 표**(R3) · `'*'` 와 소속 비혼합(D-14) · 캐시(D-5) | D-4 · D-5 · D-8 · D-13 · D-14 · AC-1 · AC-4 · AC-5 / S3 · S4 |
| 〃 | § GET /api/admin/me | `roles` = 행동 테넌트의 역할 · 새 `memberships[{tenantId, roles}]`(가산) · `homeTenantId` = «처음 소속된 회사» | D-16 · Edge 1 / S3 |
| 〃 | § GET /api/admin/operators | 소속 술어 = 배정 · grant scope 게이트 · 항목 `roles` = 조회 테넌트의 역할(S1-3) · `homeTenantId` 행(그룹 선택기 회색 처리 걷힘) · 오류표 | AC-1 · F10 / S2 · S3 · S6 |
| 〃 | § GET …/operators/lookup · § GET /api/admin/accounts · § GET /api/admin/audit · § GET /api/admin/operator-invitations | `tenantId` 판정을 § 행동 테넌트 로 · 권한 키 없는 읽기 = «역할 행 있는 테넌트» | AC-1 · F2 / S3 |
| 〃 | § GET …/operators/grantable-roles | ≤-own 을 행동 테넌트 안에서(D-6) · 오류표 | D-6 · F3 / S3 |
| 〃 | § POST /api/admin/auth/token-exchange 오류표 | 소속 0 인 비-`'*'` 행 = `401 TOKEN_INVALID`(콘솔 적격과 한 술어) | D-10 · F16 / S2 |
| 〃 | § POST …/assignments/{tenantId} | 배정 = 소속 추가 · R2(관리 권한 0) · `'*'` 대상 `400` · 🔴 **OD-2 위험 문장**(본인 동의 없는 소속 추가 — 지금대로 두고 기록, 그룹 `TENANT_ASSIGNMENT` 도) · 캐시 무효화 | OD-2 · D-14 · F12 · F13 / S4 |
| 〃 | § DELETE …/assignments/{tenantId} | **«이 회사에서 내보내기»** — 한 트랜잭션에 배정 + 그 회사 역할 행(직접 · 그룹 · 노드 기록) + 그 회사 그룹 멤버십 · fan-out + `partner = T` 참여 행 · 감사 detail · 캐시 · 마지막 소속이어도 행 유지(D-10) · 홈 R1 | D-9 · D-10 · AC-2 · Edge 2 · Edge 3 · F18 / S4 |
| 〃 | § PATCH …/identity:link · :unlink | 행 전체 규칙 · `OPERATOR_HAS_OTHER_MEMBERSHIPS` | D-8 / S4 |
| 〃 | § Operator Invitation 규칙 표 · 발급 4 · 7 · 취소 표 · «수락» 요약 | `:1658` «한 사람 = 한 회사 · 773 전 ADR» 행 → «ADR-082 = b · 소속 추가» · «대기 하나» · 발급 7 의 «이미 운영자» = 소속 기준(F15) · 발급 ≤-own 을 초대 테넌트 안에서 · 수락된 초대의 회수 길 = 내보내기 | D-12 · F15 · AC-3 / S3 · S5 |
| 〃 | § PATCH …/roles | **회사 축** 표(행동 테넌트 · 범위 · 대상 소속 · ≤-own · 교체 범위 = T 의 **직접** 행만) · `[]` = T 의 직접 역할 제거(소속은 남음) · 오류표 | D-7 · F6 · AC-4 · Failure 3 / S4 |
| 〃 | § PATCH …/status · § PATCH …/profile | 행 전체 규칙 · 새 403 · 정지 시 캐시 무효화 | R3 · D-8 · F7 · AC-4 / S4 |
| 〃 | § POST …/groups/{groupId}/members · § Partnership participant 추가 | 멤버 · 참여 술어 = 소속 · fan-out 역할 행 `tenant_id` = 그룹 테넌트 · `'*'` 멤버 `400` · 파생 도달 `partner ∈ 소속` | D-11 · D-14 · F10 / S2 · S4 |
| `projects/iam-platform/specs/contracts/http/internal/auth-to-admin.md` | § assignment check 판정 3 · § console-eligibility · § accept 판정 6 · 7 · **«소속 추가일 때» 신설** · 오류표 | assume 실효 범위 = 배정만(홈 제거 · 백필 전제) · 파트너십 도달 `partner ∈ 소속` · 2단계 요구 소속 기준 · 콘솔 적격 = `ACTIVE ∧ ('*' ∨ 소속 ≥ 1)` · 수락 6 = 새 행 / 소속 추가 / 409 셋 · 7 = 소속 기준(자기 행 제외) · 소속 추가 트랜잭션이 쓰는 것(운영자 행 쓰기 0) | D-2 · D-10 · D-11 · D-12 · F4 · F15 · F16 · F17 · AC-2 · AC-3 / S2 · S5 |
| `projects/iam-platform/specs/contracts/http/onboarding-api.md` | 오류표 · Side Effects · `:93` 문장 · **«다회사 운영자와 온보딩» · «신원당 상한» 신설** | `:93` «773 착수 전 ADR» → «ADR-082 = b · 784» · 409 의미 좁힘(`'*'` · 비-`ACTIVE`) · 판정 표 5행(테넌트 생성 전) · 기존 행 바이트 불변 · **OD-1 상한**: 단위 = 그 행의 `TENANT_ADMIN` 역할 행의 서로 다른 테넌트 수, 설정 `admin.onboarding.max-owned-tenants-per-operator` 기본 2, 초과 `422 OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED` | OD-1 · D-12 · F14 · AC-3 / S5 |
| `projects/iam-platform/specs/contracts/http/console-registry-api.md` | § Tenant selection rule 2 | `:282-283` «single tenant slug» 정정 → 소속(배정 행) · 784 이전 구현 = 배정 ∪ 홈 · S2 부터 배정만 | D-2 · F9 · ADR-082 § Outstanding / S2 |
| `projects/iam-platform/specs/services/admin-service/rbac.md` | § 권한 키 없는 읽기 · **§ 두 단 판정 (신설)** · § Target-Tenant(머리 · 대상 해소) · § Account-Data Mutation · § Grant-Menu · § Assign/Unassign · § Cross-Org(의사코드 주석) · § Group(D3 · Idempotence) · § Caching · § Assume-Tenant Assignment Check | 두 단 판정 · 읽기 게이트 = grant scope · 대상 해소에서 홈 제거 · R3 · ≤-own 회사 안 · 배정 = 소속(R2 · R4 · OD-2) · 내보내기 · 파트너십 · 그룹 술어 · 3칸 PK skip · 캐시 무효화 지점 · assume 범위 홈 제거. **새 권한 키 0 · seed 행렬 무변경** | D-2 · D-4 ~ D-9 · D-11 / S2 ~ S4 |
| `projects/iam-platform/specs/services/admin-service/data-model.md` | `admin_operators` 링크 키 노트 · `admin_operator_roles.tenant_id` · **WI-2 폐기 + «회사별 역할 불변식» 신설** · org-node 불변식 · PK · 그룹 멤버 · fan-out idempotence · 참여자 불변식 · `operator_tenant_assignment` = 소속 노트(R4 `permission_set_id` 미사용) · Migration Strategy `V0051` | D-1 · D-2 · D-3 · D-15 · R1 · R4 · OD-3 시드 위치 / S2 · S3 |
| `projects/iam-platform/specs/features/multi-tenancy.md` | § 8 OD-1 블릿(`:635`) · 회수 장치 블릿 | «773 착수 전 ADR» → «ADR-082 = b · 784» 요약 · 회수 = 내보내기 | — |
| `projects/platform-console/specs/contracts/console-integration-contract.md` | § 2.4.3 행 9 · **«한 사람 · 여러 회사» 블릿 신설** | 역할 편집 = 활성 회사 · 부여 메뉴 회사별 · «배정 해제» → «이 회사에서 내보내기»(확인 문구) · 행 전체 변경의 새 403 매핑 · `/me` nav · 그룹 선택기 회색 처리 걷힘 · § 3 패리티 행 무변경(16) | S6 · AC-6 |
| `platform/error-handling.md` · `rules/domains/saas.md` | Admin 절 · Operator Group 절 | **새 코드 2** 등록 + `OPERATOR_ALREADY_PROVISIONED` 의미 좁힘 · `GROUP_MEMBER_TENANT_MISMATCH` 의미 «소속» (아래 § 2) | — |
| `docs/adr/ADR-MONO-020-…md` · `ADR-MONO-024-…md` | D5 표 뒤 · D2 의 ADR-047 노트 뒤 | **Additive note** — «082 b 가 D5(배정마다 권한 세트 → 역할 행 테넌트 축 · R4) · D1 dual-read 홈 반쪽 · 역할 행 `tenant_id` 의미 · D2 대상 해소 · D3 ≤-own 을 바꾼다». 본문 · 상태 · History 바이트 그대로 | Scope 마지막 줄 |

## 2. 새 코드 · 키

**새 HTTP 오류 코드 (2 — admin)** — 둘 다 `platform/error-handling.md` § Admin 에 먼저, saas 도메인 코드라 `rules/domains/saas.md` § Admin Operations 에도. 공유 경로 문구는 프로젝트 비종속(서비스 이름 · 경로 없음 — 기존 행과 같은 모양: TASK/ADR id · 칸 이름만):
- `OPERATOR_HAS_OTHER_MEMBERSHIPS` (403) — R3 · D-8. 행 전체 변경 · 회사 범위 행위자 · 대상 소속 ≥ 2.
- `OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED` (422) — OD-1. 셀프 온보딩 · 테넌트 생성 전.

**의미가 바뀌는 기존 코드 (코드 문자열 유지 — IdP · 콘솔 매핑 무변경)**: `OPERATOR_ALREADY_PROVISIONED`(«측면이 있으면» → «이 소속을 받을 수 없음»: 이미 소속 · `'*'` · 비-`ACTIVE`) · `GROUP_MEMBER_TENANT_MISMATCH`(홈 → 소속) · `PARTICIPANT_NOT_OWN_OPERATOR`(공유 문구 «호출 테넌트 소속 아님» 이 이미 맞아 무변경 — 계약에만 «784 S4 부터 소속») · `TENANT_SCOPE_DENIED` · `VALIDATION_ERROR`(새 조건 — 생략 규칙 · D2 거절).

🔵 가드: `check-error-code-registry.sh` 는 코드→문서 한 방향이라 문서 먼저 넣어도 초록 · `check-domain-error-code-registry.sh` 는 saas.md 의 두 코드가 registry 에 있어야 한다 — 넣었다.

**권한 키 0 · seed 행렬 무변경**(rbac § 두 단 판정 마지막 블릿). JWT 클레임 0(ADR-082 D1).

**새 설정 키 (구현 몫 — S5)**: admin `admin.onboarding.max-owned-tenants-per-operator`(기본 `2`).

**새 마이그레이션 (구현 몫 — S2)**: `V0051__…`(가칭 — 작성 직전 다음 빈 버전 재확인; S1 시점 마지막 = `V0050`).

## 3. AC-0 계획이 정하지 않아 S1 이 정한 것 (🔴 = 소유자가 볼 만한 것)

| # | 결정 | 왜 | 어디 |
|---|---|---|---|
| S1-1 🔴 | **역할 변경의 행동 테넌트는 생략 규칙이 아니라 «대상 기준»**: 대상이 `'*'` 운영자면 헤더와 무관하게 T = `'*'` · 헤더 생략 시 대상 소속이 하나면 그것 · 둘 이상 `400` · 0 `404` | AC-0 의 생략 규칙(행위자 grant scope 기준)을 그대로 쓰면 ① `'*'` 행위자의 헤더 없는 호출이 T = `'*'` 로 해소돼 회사 운영자의 역할 편집이 `400` 이 되고 ② 콘솔은 활성 회사를 늘 실으므로 플랫폼 운영자끼리의 역할 편집(T = 활성 회사, 대상 소속 0)이 `404` 가 된다. 대상 기준이면 단일 회사 대상은 지금과 바이트 동일 | admin-api § PATCH …/roles |
| S1-2 | 행 전체 규칙의 거절 코드 분기: 대상 회사 중 **하나라도** 행위자 grant scope 안 → `OPERATOR_HAS_OTHER_MEMBERSHIPS` · 하나도 아님 → `TENANT_SCOPE_DENIED` · 비-`'*'` 소속 0 → `'*'` 만 | 새 403 이 «이 사람은 다른 회사에도 있다» 를 밖에 알려 주지 않게(열거 안전). 소속 0 은 D-8 의 «0 이면 `'*'` 만» 그대로 | admin-api § 행동 테넌트 «행 전체 규칙» |
| S1-3 🔴 | **`GET /operators` 항목의 `roles` = 조회 테넌트의 역할만**(`'*'` 조회는 전체) | AC-0 ① 표는 게이트만 정했다. 그대로 두면 B 의 관리자가 B 목록에서 그 사람의 **A 역할**을 본다(회사 간 정보 노출). `/me` 의 `memberships` 도 같은 DTO 를 쓰는 목록에는 싣지 않는다 | admin-api § GET …/operators · § GET …/me |
| S1-4 | 토큰 교환에도 «소속 ≥ 1» — 소속 0 인 비-`'*'` 행의 교환 = `401 TOKEN_INVALID` | D-10 이 «콘솔 적격 · 토큰 교환 술어 한 자리» 라 적었다. 이미 발급된 콘솔 토큰으로 교환을 시도하는 경로를 같이 닫는다 | admin-api § token-exchange 오류표 · auth-to-admin § console-eligibility |
| S1-5 | 상한: 판정 = `count ≥ 상한` → 거절 · 출처 무관(직접 · 그룹 · 초대 수락) · **초대 수락은 상한을 보지 않는다** · 코드 `422`(형제 `*_LIMIT_EXCEEDED` 와 같은 상태) | OD-1 은 셀프 온보딩의 질문이었다. ADR-044 D4 의 상한은 «스스로 만드는» 남용 방지 | onboarding-api § 신원당 상한 |
| S1-6 | D-14 거절 코드 = `400 VALIDATION_ERROR`(티켓 D-14 «772 D-2 와 같은 코드»). 그룹 멤버의 `'*'` 대상은 지금 `422 GROUP_MEMBER_TENANT_MISMATCH` 였는데 `400` 으로 바뀐다 | 티켓 문장을 따랐다. 의미상 `422` 가 더 맞을 수 있다(형식 오류가 아니라 대상 상태) — 아래 § 4-3 | admin-api § 행동 테넌트 · § assignments · § groups members |
| S1-7 | 소속 추가 수락은 기존 행의 `display_name` · `email` 을 쓰지 않는다(초대의 `displayName` 은 새 행일 때만) · 중앙 신원 링크 시도 안 함 · 감사 `downstream_detail` 에 `membershipAdded=true` | D-12 «홈 · `oidc_subject` 쓰기 0» 의 연장 — 기존 회사에서 보이는 이름이 바뀌지 않게 | auth-to-admin § accept «소속 추가일 때» |
| S1-8 | 행 전체 변경(신원 링크 · 프로필)의 감사 `target_tenant_id` = 대상 홈 그대로 | 행 전체 속성은 회사 하나로 귀속되지 않는다 — R1 «감사 기본값» | admin-api § identity:link |
| S1-9 | `GET /operators/lookup` 응답 항목 `tenantId` = 홈 그대로 | R1 표시값. 조회 테넌트 소속은 항목이 나온 것으로 이미 참 | admin-api § lookup |
| S1-10 | `'*'` 대상의 `DELETE …/assignments/{tenantId}` = 지금처럼 `404 ASSIGNMENT_NOT_FOUND`(별도 거절 없음) | 좁히는 방향 · F13 의 기존 행이 있으면 지워지는 것이 맞다 | admin-api § DELETE …/assignments |

## 4. 열린 질문 (S1 이 답하지 않은 것 — 해당 슬라이스 착수 전에 본다)

1. 🔴 **자기 자신 · 마지막 관리자의 «내보내기»** — 회사 관리자가 자기 자신을(또는 그 회사의 마지막 `TENANT_ADMIN` 을) 내보내면 그 회사는 관리자 0 이 된다(784 전에는 배정 삭제가 역할을 지우지 않아 이 일이 없었다). ADR-082 · 라이더 · AC-0 어디에도 없다. 후보: (a) 그대로(플랫폼이 복구) (b) 자기 자신 내보내기 = `400`(정지의 `SELF_SUSPEND_FORBIDDEN` 과 같은 모양) (c) 마지막 `TENANT_ADMIN` 보호 = `409`. **S4 착수 전 소유자 판단**(계약에 적지 않았다).
2. **상한의 세는 집합** — ADR-044 D4 는 «ACTIVE tenants owned per identity», ADR-082 D4 는 «`TENANT_ADMIN` 역할 행이 가리키는 서로 다른 테넌트 수» 다. S1 은 ADR-082 D4 그대로(테넌트 상태 무관) 적었다. `SUSPENDED` 테넌트를 빼면 계정 하나가 «만들고 → 정지되고 → 또 만들기» 를 할 수 있고, 넣으면 정지된 회사가 자리를 차지한다. 값과 함께 773 AC-0 이 다시 볼 것.
3. D-14 의 코드(`400 VALIDATION_ERROR`)가 `'*'` **대상**(요청 필드가 아니라 대상의 상태)에 맞는지 — 티켓대로 적었다. 새 코드를 원하면 S4 전에 바꾼다(registry 행 하나).
4. `GET /api/admin/me` 의 `roles` 가 `X-Tenant-Id` 를 읽는데 콘솔이 `/me` 에 그 헤더를 싣는지 — S6 에서 확인(안 실으면 nav 가 합으로 남는다 — 판정은 producer 가 하므로 누수는 아니다).
5. `multi-tenancy.md` § 7 시험 자리 표에 784 행(AC-1 · AC-2 · AC-3 · AC-4 · AC-5 의 시험 위치)을 더하는 것은 시험이 생기는 S3 ~ S5 몫으로 남겼다.

## 소유자 결정 추가 (2026-10-11 UTC) — 마지막 관리자

S1 에이전트가 남긴 열린 질문(«이 회사에서 내보내기» 로 마지막 `TENANT_ADMIN` · 자기 자신을 내보내면 관리자 0 회사가 생긴다 — 784 전엔 불가능)에 소유자 선택창 응답 **«거절 (409)»**(추천과 같음 — 티켓 범위 결정). 계약: `admin-api.md` § «이 회사에서 내보내기» · § 역할 PATCH 에 `409 TENANT_LAST_ADMIN_REQUIRED`(새 코드, `platform/error-handling.md` · `rules/domains/saas.md` 등록). 🔴 오케스트레이터 판단으로 **같은 술어를 역할 교체 · 그룹 멤버 제거 · 그룹 역할 변경에도** 건다 — 내보내기만 막으면 «역할을 먼저 떼고 내보낸다» 로 우회되어 결정이 공허해진다. 구현 = S4(AC-2 대조군에 «마지막 관리자 내보내기 → 409 · 쓰기 0» 칸 추가).

---

# S2 기록 (2026-10-11 UTC)

> 구현=Opus 5.5 (backend-engineer) · worktree `wt-784` · 브랜치 `feat/mono-784-s2-membership-axis`(origin/main `519a609c7` = S1 #4308 위). 근거 = § AC-0 기록 D-2 · D-3 · D-10 · D-11 앞 절반 · S1 계약(`auth-to-admin.md` § check 판정 3 · § console-eligibility · `admin-api.md` § token-exchange 오류표 · § GET /api/admin/operators ① · `data-model.md` V0051 항목 · `console-registry-api.md` rule 2).
> **AC 체크박스는 건드리지 않았다** — S2 는 티켓 AC 를 닫지 않는다(§ 7 표 S2 행: «— (AC-2 의 전제)»).

## 1. 마이그레이션 `V0051__admin_operator_roles_tenant_pk_and_membership_backfill.sql`

- 버전: 디렉터리 마지막 = `V0050` · 열린 PR(#4309 하나, tasks 만) · 원격 브랜치에 `V0051` 주장 없음 → `V0051`.
- ① **PK 3칸**: `ALTER TABLE admin_operator_roles DROP PRIMARY KEY, ADD PRIMARY KEY (operator_id, role_id, tenant_id)` **한 문장**. FK 처리: `fk_admin_operator_roles_operator(operator_id)` 는 PK 의 선행 칸을 인덱스로 쓴다 — 따로 `DROP PRIMARY KEY` 하면 MySQL ER 1553, 한 ALTER 안에서 같은 선행 칸의 새 PK 로 바꾸면 인덱스 공백이 없다. `fk_..._role` 은 `idx_admin_operator_roles_role`, `group_origin` 은 V0044 인덱스 — 무관. `tenant_id` 는 V0025 부터 NOT NULL. 데이터 안전: 784 이전 모든 행 `tenant_id = 홈`(V0026) ⇒ 2칸 유일 ⇒ 3칸 유일. 멱등: `information_schema.key_column_usage` 에 PRIMARY 의 `tenant_id` 가 없을 때만(V0042/V0044 의 PREPARE/EXECUTE/DEALLOCATE 3종 · NULL-safe `COUNT(*) = 0`).
- ② **소속 백필**: 비-`'*'` 운영자마다 (홈) ∪ (비-`'*'` 역할 행 `tenant_id`) 중 배정 행이 없는 것에 whole-tenant · direct · `permission_set_id` NULL 행을 INSERT. 멱등 = `NOT EXISTS (… FROM (SELECT operator_id, tenant_id FROM operator_tenant_assignment) a …)` (대상 표를 파생 표로 읽는 auth-service V0032 형태) — 두 번째 실행 0행. 기존 행(`org_scope` 포함)은 UPDATE/DELETE 0. `'*'` 운영자 · `'*'` 역할 행 제외(ADR-082 D2).
- 🔵 홈이 `fan-platform`(V0025 의 레거시 기본값)인 비-`'*'` 운영자도 백필된다 — 바이트 불변 원칙(그 홈은 784 전에도 실효 범위였다). assume 은 여전히 2b(`fan-platform` = 플랫폼 운영자 전용)가 막는다.

## 2. 행동 변경 (파일)

| 무엇 | 파일 | 근거 |
|---|---|---|
| JPA PK 3칸(`@Id tenantId` · `PK(operatorId, roleId, tenantId)`, 2-arg 생성자 제거) | `infrastructure/persistence/rbac/AdminOperatorRoleJpaEntity.java` | D-3 |
| 그룹 fan-out skip 키 = `(멤버, 역할, 멤버 홈)` — 멤버 불변식(홈 == 그룹 테넌트)으로 rbac.md 의 `(멤버, 역할, group.tenant_id)` 와 같다. S4 가 둘 다 그룹 테넌트로 | `application/GroupFanOutService.java` | rbac.md § Group Idempotence |
| 실효 범위 = **배정 행만**(홈 제거, `'*'` 그대로) | `application/TenantScopeResolver.java` | D-2 · R1 · F4 |
| assume: 3번 판정이 해소기 따라 소속 · 파트너십 도달 = `partner ∈ 소속`(정렬 순회, 첫 일치) | `application/OperatorAssignmentCheckUseCase.java` | D-11 앞 절반 |
| 「살아 있는 운영자」 술어 한 자리 = `ACTIVE ∧ ('*' ∨ 소속 ≥ 1)` (신설) | `application/OperatorConsoleAdmission.java` | D-10 · F16 |
| 콘솔 적격 = 위 술어 | `application/OperatorConsoleEligibilityQueryUseCase.java` | auth-to-admin § console-eligibility |
| 토큰 교환: 비-ACTIVE 다음에 소속 0 → `401 TOKEN_INVALID`(본문 = 비-ACTIVE 와 같은 문구 «Operator is not active» — 계약 «같은 답»), 2단계 요구(403)보다 먼저 | `application/TokenExchangeService.java` | admin-api § token-exchange 오류표 · S1-4 |
| 2단계 요구(교환)의 정책 테넌트 = 소속만(홈 제거) | `application/OperatorSecondFactorRequirement.java` | auth-to-admin 판정 3 «2단계 요구도 소속 기준» · F17 |
| 운영자 목록 · 진입 정책 로스터 · 운영자 이메일 조회의 «속한» 술어 = 배정 행만(`o.tenantId = :tenantId OR` 삭제, 4 쿼리) | `infrastructure/persistence/rbac/AdminOperatorJpaRepository.java` | admin-api § GET /api/admin/operators ① «S2 — 홈은 판정에서 빠진다» (lookup · 로스터는 «같은 소속 술어» 문장으로 묶여 있다) |
| 주석만(술어 설명 갱신) | `QueryTenantScopeGate` · `console/ConsoleRegistryUseCase` · `port/AdminOperatorPort` · `TenantEntryPolicyPrecheckUseCase` · `presentation/internal/OperatorAssignmentCheckController` | — |
| 시드: `demo-cs` → `ecommerce` 배정 · `demo-viewer` → 미등록 `demo-viewer` 배정(헤더 «choice 4 / 3» 갱신) | `db/migration-dev/R__seed_demo_cs_operator.sql` · `R__seed_demo_viewer_operator.sql` | D-2 · D-10 |
| e2e 픽스처: 로그인하는 비-`'*'` 운영자 3명(`acme-corp-operator` · `tenant-admin-umbrella` · `tenant-billing-admin-umbrella`)에 홈 배정. 대상 전용(`deleg-target-umbrella` · `ip-pilot-target` · `rt-*`)은 **안 넣음** — 스펙이 그 홈으로 배정 생성 201 을 기대한다 | `tests/federation-hardening-e2e/fixtures/seed.sql` | 시드가 Flyway 뒤에 돈다 — 백필이 못 본다 |

🔵 그대로 둔 것: `QueryTenantScopeGate` 와 사본 4곳의 «생략 = 홈» 기본값 · grant scope 재조준(S3) · 콘솔 e2e `seed.sql`(로그인하는 운영자는 `'*'` 뿐) · `scripts/console-demo/seed/01-iam.sql`(`multi-operator` 는 이미 `acme-corp` 배정 보유).

## 3. 시험 · 픽스처

- **새 시험**: `V0051MigrationShapeTest`(6 — 한 ALTER PK 스왑 · 가드 · 사용자 변수 1개 · 백필 소스 홈 ∪ 역할 행 · `'*'` 제외 · NOT EXISTS 멱등 · 평이한 INSERT · 데이터 UPDATE/DELETE 0) · `integration/V0051MembershipBackfillIntegrationTest`(Testcontainers — 마이그레이션 파일의 **자기 문장**을 읽어 재실행: PK 칸 순서 · 가드 재실행 no-op · 홈만 있는 행 → 홈 + 역할 행 테넌트 소속 · `'*'` 0 · 기존 `org_scope` 행 불변 · **두 번째 실행 0행**; 데이터 절반은 롤백 트랜잭션 — 백필이 다른 클래스의 «홈만» 픽스처에 소속을 주지 않게) · `integration/MembershipFixture`(V0051 백필 행 하나를 픽스처 운영자에게).
- **단위 대조군**(실패 쪽 먼저): `TenantScopeResolverTest`(홈만 → 빈 집합 / 홈 ∉ 배정 → 배정만 · 대조군 배정 그대로 / 백필 뒤 단일 회사 = {홈}) · `OperatorAssignmentCheckUseCaseTest` +4(진짜 해소기로 홈 assume 거절 · 대조군 배정 assigned / 백필 홈 assigned / 파트너십 비-홈 소속으로 도달 / 홈이 파트너여도 홈 비소속이면 도달 0 · 홈으로는 조회조차 안 함) · `OperatorConsoleEligibilityQueryUseCaseTest` +2(소속 0 → false · 같은 행 소속 1 → true / `'*'` → 소속 안 읽고 true) · `TokenExchangeServiceTest` +3(소속 0 → 401 · 발급 0 · 2단계 요구 안 읽음 / 같은 행 소속 1 → 발급 / `'*'` 소속 0 → 발급 / 홈 정책 ON · 홈 비소속 → 요구 없음).
- **IT 대조군**(Docker 필요 — CI 판정): `ConsoleRegistryIntegrationTest`(홈만 → 스위처에 홈 없음 / 대조군 소속 → wms) · `OperatorAssignmentCheckIntegrationTest`(홈 배정 없음 → `assigned=false` / 대조군 globex · 백필 홈 → true; 소속 0 → `eligible=false` / 대조군 · `'*'` → true) · `TokenExchangeIntegrationTest`(소속 0 → 401 / 대조군 200) · `DemoOperatorSeedIntegrationTest`(`demo-viewer` 소속 = 정확히 `{demo-viewer}` · `demo-cs` 소속 = 정확히 `{ecommerce}` · 둘 다 콘솔 적격 true).
- **홈만으로 assume/조회하던 IT 픽스처 수선**(`MembershipFixture.addHomeMembership` — «자기 회사의 평범한 운영자» 인 곳만): `AdminAuditTenantScopeIntegrationTest` · `ConsoleRegistryIntegrationTest` · `OperatorInvitationIntegrationTest` · `OperatorLookupIntegrationTest` · `OperatorProfileAdminIntegrationTest` · `PartnershipCrossOrgLeakIntegrationTest`(2 헬퍼) · `TenantEntryPolicyIntegrationTest` · `TokenExchangeIntegrationTest`(2 헬퍼) · `OperatorAssignmentCheckIntegrationTest`(facet 운영자). 셈법: 운영자 행을 손으로 넣는 IT 29파일 중 해소기 · 적격 · 교환 · 목록 술어를 타는 경로를 실제로 부르는 것만(나머지 — `'*'` 만 · 권한 aspect 에서 먼저 403 · grant scope 판정 · 플랫폼 전용 2FA reset — 는 무변경, 파일별로 확인).
- **auth-service 핀**: `DemoCsOperatorSeedTest`(소속 = 정확히 `ecommerce → demo-cs` · 대조군 ROLE_GRANT 1) · `DemoViewerOperatorSeedTest`(역할 0 · 소속 = 정확히 `demo-viewer → demo-viewer`) · `DemoCsOperatorDerivedRolesTest`(공개 역할 파생의 테넌트 = 소속 = 홈 = 한정 테넌트).

## 4. 실행 (rc 는 각자 따로 — 파이프 없음)

| 명령 | rc | 결과 |
|---|---|---|
| `./gradlew :projects:iam-platform:apps:admin-service:compileTestJava :projects:iam-platform:apps:auth-service:compileTestJava` | **0** | IT 포함 컴파일(이 프로젝트의 `integrationTest` 는 별 소스셋이 아니라 `test` 소스셋의 `@Tag("integration")` — `compileIntegrationTestJava` 태스크는 없다) |
| `./gradlew :projects:iam-platform:apps:admin-service:test` (최종) | **0** | 1119 tests · fail 0 · skip 58 (`V0051MigrationShapeTest` 6/6) |
| `./gradlew :projects:iam-platform:apps:auth-service:test` | **0** | 1246 tests · fail 0 · skip 33 (세 시드 핀 5 · 4 · 3 모두 통과, 타임스탬프로 이번 실행 확인) |
| 🔴 **bite**: `TenantScopeResolver` 에 홈을 되돌리고 + `OperatorConsoleAdmission` 의 소속 절을 `true` 로 → 네 클래스만 | **1** | 48 중 8 빨강 — 해소기 2 · assume 홈 거절 · 파트너십 홈 비소속 · 적격 소속 0 · 교환 소속 0 등(새 대조군이 문다). 원본 복원 후 같은 네 클래스 rc=**0** |
| `integrationTest` | ⚪ | 이 호스트에 Docker 없음 — Testcontainers IT(위 § 3 IT 전부, 새 `V0051MembershipBackfillIntegrationTest` 포함)는 **CI 가 판정**한다 |

## 5. 계약과 다르게 / 계약이 틀렸던 곳

1. **AC-0 F17 «2단계 요구는 해소기를 따라 바뀐다 — 별도 수정 없음» 은 틀렸다**: `OperatorSecondFactorRequirement.requiredForTokenExchange` 는 해소기를 쓰지 않고 «홈 ∪ 배정» 을 **직접** 조립했다. 계약(auth-to-admin 판정 3 «2단계 요구도 소속 기준») 쪽을 따라 고쳤다.
2. **S2 범위가 슬라이스 표보다 넓다**: `admin-api.md` § GET /api/admin/operators ① 이 «속한 = 소속» 을 **S2** 로 적었다(슬라이스 표에는 없다). 목록 쿼리를 바꾸면 «같은 소속 술어» 로 묶인 로스터(진입 정책 사전 점검) · 이메일 조회도 같이 바뀌어야 해서 4 쿼리를 함께 바꿨다. 백필 덕에 기존 목록은 그대로.
3. 🔵 `admin-api.md` § GET /api/admin/operators 쿼리 파라미터 표의 `tenantId` 행(«effective scope(home ∪ 배정, BE-326 dual-read)») · § lookup(`:1199`) · 테넌트 개요 `operators` 행(`:2511`) 의 «HOME 또는 배정» 문장이 위 ① 과 어긋난 채 남아 있다 — S3 가 같은 절을 다시 쓸 때 정리한다(코드는 ① 을 따른다).
4. 🔵 rbac.md § Group Idempotence 의 «784 S2 부터 skip = `(operator, role, group.tenant_id)`» — 코드는 멤버 홈을 쓴다. 멤버 불변식(홈 == 그룹 테넌트, S4 전까지 유효)으로 같은 값이라 행동 차이 0, S4 가 명시적으로 바꾼다.

## 6. 남은 것

- **CI**: admin-service `integrationTest`(위 IT 전부) · `federation-hardening-e2e`(시드 3행 추가 — 위임 스펙 · 스위처) — 이 PR 의 CI 결과로만 판정된다.
- **S3+**: 읽기 게이트를 grant scope 로 · 사본 4곳 · 생략 규칙 · 권한 키 없는 읽기 · ≤-own 회사 안 · `/me` · 감사 `tenant_id` · 상태 변경 캐시 무효화 · OD-3 `demo-operator` `ecommerce` 역할 시드(S3) / 역할 PATCH 회사 축 · R3 · 내보내기 · 마지막 관리자 409 · 그룹 · 참여자 술어 · fan-out 그룹 테넌트 · D2 거절(S4) / 두 409 → 소속 추가(S5) / 콘솔(S6).
- 라이브 ⚪: 데모 창에서 V0051 백필 수(§ AC-0 ④ 술어와 같은 창) — 배포 후 `operator_tenant_assignment` 가 «비-`'*'` 운영자 수 + (홈 밖 역할 행 테넌트 수)» 만큼 늘었는지.

---

## S3 기록 (2026-10-13 UTC)

> 구현=Opus 5.5 (backend-engineer) · worktree `wt-784` · 브랜치 `feat/mono-784-s3-read-axis`(origin/main `187fd8544` = S2 #4312 위). 근거 = § AC-0 D-4 · D-5 · D-6 · D-13 · D-16 · OD-3 · S1 계약 `admin-api.md` § 행동 테넌트(23행 표의 S3 행 #1 · #5 ~ #11 · #17) · § GET /me · § GET /operators ②③.
> 경로는 따로 적지 않으면 `projects/iam-platform/apps/admin-service/src/main/java/com/example/admin/` 아래.

### 1. 행동 변경

| # | 무엇 | 파일 | 근거 · 23행 |
|---|---|---|---|
| 1 | **게이트 = grant scope** — `QueryTenantScopeGate` 가 소속(배정)·홈 대신 `AdminGrantScopeEvaluator.effectiveAdminScope(운영자, 권한)` 으로 판정. 생략 규칙: `{'*'}`→`'*'` · 하나 → 그것 · 0 → 403 · 둘 이상 → `IllegalArgumentException`(400 `VALIDATION_ERROR`). `isPlatformScope` = grant scope 에 `'*'`(홈 칸 아님). DENIED 행 유무를 고르는 `resolveWithoutDenyRow` · 권한 키 없는 읽기용 `resolveAnyRole` 신설 | `application/QueryTenantScopeGate.java` (전면 재작성 — `decide` :102-128) | D-4 · #6 ~ #11 |
| 2 | 권한 키 없는 읽기 술어 = «역할 행이 하나라도 있는 테넌트»(`anyRoleScope`) · ≤-own 의 «보유» = 그 테넌트에서 보유(`permissionsHeldIn` — 그 테넌트 행 · `'*'` 행 · 그 테넌트를 덮는 노드 행) · `scopeOf` 로 공용화 | `infrastructure/persistence/rbac/AdminGrantScopeEvaluator.java` (`anyRoleScope` · `permissionsHeldIn` · `scopeOf` · `activeRoleRows`) | R2 · D-6 |
| 3 | 계정 검색: e-mail 분기 → `resolveAnyRole` · 목록 분기 → **권한 검사 뒤** `resolve(account.read)` (권한 없는 운영자의 grant scope 는 비어 있어 게이트가 먼저면 `PERMISSION_DENIED` 가 `TENANT_SCOPE_DENIED` 로 바뀐다 — 순서를 바꿔 계약 코드 유지). 잠금·해제·일괄잠금·GDPR·내보내기·세션 종료는 호출 코드 무변경 — 게이트 의미가 바뀐 것만으로 grant scope(각 annotation 권한) 판정이 된다(F2) | `presentation/AccountAdminController.java` (search) · `AdminGdprController` · `SessionAdminController`(무변경) | #8 · #8b · #10 |
| 4 | 운영자 이메일 조회 → `resolveAnyRole` | `application/OperatorEmailLookupUseCase.java` | #9 |
| 5 | **사본 3곳 합침**: 운영자 목록(`OperatorQueryService` — 해소기 사본 삭제, `resolveWithoutDenyRow(operator.manage)`) · 초대 목록(`OperatorInvitationUseCase.list`, 같은 호출) · 테넌트 상세(`TenantAdminController.getTenant` — `TenantScopeGuard.requireTenantReadable(tenant.manage)`; `PermissionEvaluator.isTenantAllowed` · 해소기 의존 제거). 🔵 4번째 사본 `UpdateOperatorProfileUseCase` 는 계약 23행 #12 가 **S4(행 전체 규칙)** 로 적어 두어 손대지 않았다 | `application/OperatorQueryService.java` · `application/OperatorInvitationUseCase.java` · `presentation/tenant/TenantAdminController.java` | #6 · #11 · #17 |
| 6 | 운영자 목록 항목 `roles` = **조회 테넌트의 역할 행만**(`'*'` 목록은 전체) — 포트 `bulkLoadRoleNamesByOperatorInTenant` 신설 | `OperatorQueryService.listOperators` · `port/AdminOperatorPort.java` · `persistence/rbac/JpaAdminOperatorAdapter.java` | § GET /operators ③ (S1-3) |
| 7 | `/me`: `X-Tenant-Id` = T 면 `roles` = T 행 + `'*'` 행 · 헤더 없으면 전체(그대로) · 새 `memberships[{tenantId, roles}]`(소속 오름차순, `'*'` 운영자 `[]`, 목록 항목엔 null = 생략). 포트 `findRoleNamesByTenant` 신설 | `OperatorQueryService.getCurrentOperator(uuid, tenant)` · `presentation/OperatorAdminController.java` (`/me`) · `presentation/dto/OperatorSummaryResponse.java` (`memberships`, NON_NULL) | #1 · D-16 |
| 8 | **≤-own 회사 안**: `RoleGrantGuard.requireGrantable` · `grantableRoleNames` 에 테넌트 인자(필수 — 합 기반 오버로드 삭제, 그래서 어느 호출자도 조용히 합을 쓰지 못한다). 테넌트 = **부여되는 행이 사는 곳**: 초대 생성 = 초대 테넌트 · 재발송 = 초대 테넌트 · 수락 재검사 = 초대 테넌트 · 그룹 역할 부여·멤버 추가 = 그룹 테넌트 · 역할 PATCH = 대상 홈(S4 가 행동 테넌트로) · 직접 생성 = `'*'` · 노드 grant = 대상 홈(행의 `tenant_id`). `PermissionEvaluator`(합) 의존 제거 | `application/RoleGrantGuard.java` · `CreateOperatorUseCase` · `OperatorInvitationUseCase` · `OperatorInvitationAcceptanceUseCase` · `GroupAdminUseCase` · `PatchOperatorRoleUseCase` · `OrgNodeAdminUseCase` | D-6 · F3 |
| 9 | `grantable-roles`: `X-Tenant-Id` 를 `resolveWithoutDenyRow(operator.manage)` 로 해소(밖 → 403, 감사 행 없음) → 그 테넌트의 메뉴 | `OperatorAdminController.grantableRoles` | #5 |
| 10 | **감사 `tenant_id` = 행동 테넌트**(D-13): `'*'` 행위자 → `'*'` · 자기 흐름(`<self_*>` · `auth.*` 권한) → 홈 · 대상 테넌트 없음/`'*'`/홈과 같음 → 홈(조회 0) · 대상 테넌트가 행위자의 **소속**이면 그 테넌트 · 그 밖(파트너 회사 등) → 홈. IN_PROGRESS · 단발 행 둘 다. DENIED 행(`AdminActionDenyWriter`)은 무변경(홈) | `application/AdminActionAuditWriter.java` (`actingTenantId`) | D-13 · F5 |
| 11 | 상태 변경 성공 뒤 권한 캐시 무효화(역할 PATCH 와 같은 best-effort) | `application/PatchOperatorStatusUseCase.java` | D-5 · F7 |
| 12 | **OD-3 시드**: `demo-operator` 에 `SUPPORT_READONLY` + `SUPPORT_LOCK` @ `ecommerce`(§ 4b) — `ecommerce` 의 계정 · 감사 · 잠금 유지, `ecommerce` 운영자 목록 403 | `src/main/resources/db/migration-dev/R__seed_demo_operator.sql` | OD-3 |
| 13 | 계약 정리(S2 보고 3건 + 관련 1건): GET /operators `tenantId` 행 · lookup `tenantId` 행 · lookup «속한» 술어(:1199) · 테넌트 개요 `operators` 행(:2511) — «home 또는 배정» → 소속 / grant scope + 생략 규칙. § Tenant Confinement 의 784 블릿을 «S3 부터 · 플랫폼 = grant scope 의 `'*'`» 로. lookup 오류표에 생략 → 둘 이상 = 400 | `projects/iam-platform/specs/contracts/http/admin-api.md` | S2 § 5-3 |

### 2. 시험 (실패 쪽 먼저)

- **AC-1 단위 반쪽** — `QueryTenantScopeGateTest`(전면 재작성, **진짜 `AdminGrantScopeEvaluator`** + 목 저장소): 🔴 역할 행 @alpha 만 → beta 의 `operator.manage` · `audit.read` · `account.lock` **거절**(+ DENIED 행 2) → 같은 시험에서 alpha 통과 · 권한별 scope(SUPPORT_LOCK @beta → beta 잠금 O · beta 운영자 목록 X) · **대조군** 생략 + 단일 회사 → 그 테넌트(=예전 홈 기본값) · 생략 + 둘 → 400 · 생략 + 0 → 403 · `'*'` · 비-플랫폼의 `'*'` 요청 403 · 미지 운영자 · R2 any-role(beta 거절 · alpha 통과 · 생략 → alpha). 9칸.
- **AC-1 IT 반쪽** — `integration/OperatorMembershipReadAxisIntegrationTest`(신규, Testcontainers · F8 픽스처: A 에 `TENANT_ADMIN` + `SUPPORT_READONLY` + `SUPPORT_LOCK`, 소속 A · B, B 역할 0): ① 🔴 B 운영자 목록 · B 감사 403 `TENANT_SCOPE_DENIED` → 같은 시험 A 둘 200 ② 🔴 잠금 쓰기 B → 403 · IN_PROGRESS 행 0 ③ 대조군 단일 회사 생략 → 200 · 메타 감사 행 `tenant|target` = `gamma|gamma`(바이트 동일) ④ D-13 B 의 행위 행이 B 관리자의 감사 조회에 `totalElements=1` · `operatorId` 일치.
- D-6: `RoleGrantGuardTest`(재작성) 🔴 `TENANT_ADMIN @alpha` 가 beta 에서 `TENANT_ADMIN` 부여 거절 + 메뉴 빈 배열 → 같은 시험 alpha 통과 · 나머지 기존 칸을 테넌트 인자로.
- D-13: `AdminActionAuditWriterTest` +5(🔴 소속 beta 행위 → `tenant_id=beta` / 대조군 단일 회사 → 홈 · 소속 조회 0 / 소속 밖 대상 → 홈 / `'*'` → `'*'` · 조회 0 / 자기 흐름 → 홈).
- `/me` · 목록: `OperatorQueryServiceTest`(재작성 — beta 헤더 → `SUPPORT_READONLY` 만 · memberships 3(역할 0 소속 포함) · 헤더 없음 → 전체 / `'*'` → `[]` · 소속 미조회 / 목록 항목 memberships null · 회사 목록은 `…InTenant` 만) · `OperatorAdminControllerSliceTest`(+/me 헤더·memberships 직렬화 · grantable 403).
- 계정: `AccountAdminControllerSliceTest` +2(e-mail 분기 역할 0 테넌트 403 · 검색 0 / 목록 `account.read` 없음 → `PERMISSION_DENIED` · 게이트 미호출). `TenantAdminControllerSliceTest` +1(테넌트 상세 grant scope 밖 403). `PatchOperatorStatusUseCaseTest` +1(성공 → 무효화 · 거절 → 무효화 0).
- 시그니처 따라 고침(기대값 무변경): `CreateOperatorUseCaseTest` · `GroupAdminUseCaseTest` · `OperatorInvitationUseCaseTest`(목록 3칸 → 게이트 목) · `OperatorInvitationAcceptanceUseCaseTest` · `OrgNodeAdminUseCaseTest` · `OperatorLookupControllerSliceTest`(`resolveAnyRole`).
- 시드 핀: `DemoOperatorSeedIntegrationTest` — `demo-operator` 역할 집합 정확히 `{SUPER_ADMIN|demo-corp, SUPPORT_LOCK|ecommerce, SUPPORT_READONLY|ecommerce}` + 🆕 ecommerce 감사 200 · ecommerce 운영자 목록 403 `TENANT_SCOPE_DENIED` · demo-corp 목록 200(대조군).

### 3. 실행 (rc 는 각자 따로 — 파이프 없음)

| 명령 | rc | 결과 |
|---|---|---|
| `./gradlew :projects:iam-platform:apps:admin-service:compileTestJava` | **0** | IT 포함 컴파일 |
| `./gradlew :projects:iam-platform:apps:admin-service:test` (최종) | **0** | 178 suites · 1135 tests · fail 0 · error 0 · skip 58(Testcontainers IT — Docker 없음) |
| 🔴 **bite**: `QueryTenantScopeGate.decide` 의 명시값 분기를 «요청 테넌트 무조건 통과»(= 픽스처의 B 가 소속이므로 784 전 «home ∪ 배정» 게이트와 같은 결과)로 바꾸고 `--tests QueryTenantScopeGateTest` | **1** | 9 중 **4 빨강** — AC-1 · 권한별 scope · R2 any-role · 비-플랫폼 `'*'`. 대조군(생략 단일 · 둘 → 400 · 0 → 403 · `'*'`) 5칸은 초록 그대로(물 것만 문다). 복원 후 같은 클래스 rc=**0** |
| auth-service | — | 이번 슬라이스는 auth-service 를 건드리지 않아 돌리지 않았다 |
| `integrationTest` | ⚪ | 이 호스트에 Docker 없음 — 위 IT(신규 `OperatorMembershipReadAxisIntegrationTest` · 갱신 `DemoOperatorSeedIntegrationTest`)는 **CI 가 판정** |

### 4. CI 가 판정할 것 · 계약과 다르게 한 곳

1. 🔴 **IT 전반의 파급**: 게이트가 grant scope 로 바뀌어 «역할 행 테넌트 ≠ 조회 테넌트» 인 IT 픽스처(역할을 홈에만 두고 배정 테넌트로 감사·계정·운영자 목록을 부르던 것)는 이제 403 이다. 정적으로 찾은 곳은 없지만 29개 수기 픽스처를 모두 열지는 않았다 — CI 빨강이면 픽스처가 R2 를 어긴 것이다(역할 행을 그 테넌트에 더한다).
2. **생략 규칙의 새 400**: 역할 행이 두 테넌트 이상인 운영자가 `tenantId`/`X-Tenant-Id` 없이 부르면 400. 콘솔은 늘 활성 테넌트를 보내므로 무영향 예상(S1 계약 그대로).
3. **D-13 의 판정 술어**: 계약은 «테넌트로 판정된 행위 = 행동 테넌트» 이고 구현은 «기록의 `target_tenant_id` 가 행위자의 소속이면 그것» 이다. 노드 grant(ORG_ADMIN) 로 subtree 테넌트에서 한 행위는 그 테넌트가 소속이 아니어서 **홈에 남는다**(784 이전과 같음) — S4 이후 필요하면 판정 지점에서 행동 테넌트를 명시 전달하도록 바꾼다.
4. **lookup 생략 + 역할 테넌트 둘 이상** = 400(계약 일반 규칙을 따름) — lookup 오류표에 한 줄 더했다.
5. `TenantAdminController.getTenant` 는 이제 DENIED 행을 쓰지 않는 `requireTenantReadable`(784 전 사본도 행을 쓰지 않았다 — 행동 동일).

### 5. S4+ 에 넘기는 것

- `UpdateOperatorProfileUseCase` 의 게이트 사본(행 전체 규칙 #12) · `PatchOperatorRoleUseCase` 의 ≤-own 테넌트(지금 대상 홈 → D-7 행동 테넌트) · 역할 PATCH · 내보내기 · 그룹/파트너 술어의 캐시 무효화 지점(S4) — 상태 변경 무효화는 S3 에서 끝.
- `AdminGrantScopeEvaluator` 클래스 javadoc 의 «operational scope = home ∪ assignment» 문구(코드 무관, S2 이후 낡음).
- `multi-tenancy.md` § 7 시험 자리 표의 784 행(AC-1 위치 = 위 § 2) — S1 § 4-5 가 시험 생기는 슬라이스 몫으로 남긴 것.
- 라이브 ⚪: 데모 창에서 `demo-operator` 의 ecommerce 화면(계정 · 감사 · 잠금 200 · 운영자 403) 확인 — AC-6 과 같은 창.

### 6. CI 1회차 · 소유자 결정 (2026-10-13 UTC)

- **실패**: PR #4320 `Integration (iam A)` — `OperatorLookupIntegrationTest` «HOME ∪ ASSIGNED operators … (operator holding no role)» `$.content.length()` 기대 2 · 실제 0.
- **원인**: § 1 #4 가 `GET /operators/lookup`(#9)을 #8b 와 같은 «역할 행이 하나라도 있는 테넌트» 로 묶었다. 그 칸의 호출자(erp HR)는 역할 행이 0 → 범위 밖 → 빈 200. TASK-MONO-777 이 이 조회를 권한 키 없이 연 이유가 바로 **관리 역할이 없는 HR** 이라, S3 가 그 기능을 조용히 깼다. 그 IT 칸은 의도된 것이므로 약하게 고치지 않았다.
- **소유자 결정** (선택 «그 회사 소속이면 허용»): **#9 만** 게이트 = 호출자의 **소속**(배정 행 · `'*'` 호출자는 그대로 전 테넌트 · 홈 안 읽음) + 생략 규칙을 소속 집합 위에서. 범위 밖 = 빈 200 + best-effort DENIED 행 그대로. **#8b 는 «역할 행» 그대로.**
- **수정**: `QueryTenantScopeGate.resolveMembership`(신설 — `OperatorLookupPort` + `TenantScopeResolver`, S2 의 배정만 해소기) · `OperatorEmailLookupUseCase` → `resolveMembership` · `OperatorLookupControllerSliceTest` 목 이름. 계약 `admin-api.md` #9 행 · § lookup 의 `tenantId` 행 · 784 규칙 블릿 · 오류표 문구(«소속») — #8b 문구 무변경. `rbac.md` § 권한 키 없는 읽기 — lookup 예외 블록 추가, 역할 술어 블록은 accounts e-mail 에만.
- **시험**: `QueryTenantScopeGateTest` +3 — 🔴 소속 beta · 역할 0 → lookup beta 해소(생략 → beta) + 같은 호출자로 #8b(`resolveAnyRole`) beta 거절(두 키 없는 읽기를 한 시험에서 갈라 핀) / 대조군 비소속 gamma · 소속 아닌 홈 alpha 둘 다 거절 + DENIED 행 / `'*'` → `*` · 소속 미조회.
- **rc**: `:admin-service:test` **0**(1138 tests · fail 0 · skip 58). bite — `resolveMembership` 을 `anyRoleScope` 로 되돌림 → `QueryTenantScopeGateTest` 12 중 **3 빨강**(새 #9 칸 셋) · 나머지 9 초록 → 복원 후 그 클래스 + `OperatorLookupControllerSliceTest` rc **0**. IT(`OperatorLookupIntegrationTest`) 는 다시 CI 판정.

---

## S4 기록 (2026-10-13 UTC)

> 구현=Opus 5.5 (backend-engineer) · worktree `wt-784` · 브랜치 `feat/mono-784-s4-membership-writes`(origin/main `4c09136f0` = S3 #4320 위). 근거 = § AC-0 D-5 · D-7 · D-8 · D-9 · D-10 · D-11 · D-14 · F6 · F10 · F11 · F13 · F18 · 소유자 결정(마지막 관리자 409) · S1 계약 `admin-api.md` § 행동 테넌트(23행 표 #12 ~ #15 · «행 전체 규칙» · «`'*'` 와 회사 소속») · § DELETE …/assignments/{tenantId} · § PATCH …/roles · § PATCH …/status · § groups members · § participant.
> 경로는 따로 적지 않으면 `projects/iam-platform/apps/admin-service/src/main/java/com/example/admin/` 아래.

### 1. 행동 변경

| # | 무엇 | 파일 | 근거 |
|---|---|---|---|
| 1 | **역할 변경 = 회사 축**: T 해소(`'*'` 대상 → `'*'` · 헤더 · 생략 시 대상 소속 하나 / 둘 이상 400 / 0 404) → T ∈ grant scope(403 + DENIED) → 비-`'*'` 대상이 T 소속(아니면 404) · 헤더 `'*'` 400 → ≤-own **T 안에서**(S3 가 남긴 «대상 홈» 교체) → 마지막 관리자 → **T 의 직접 행만** 교체(`deleteDirectOperatorRolesInTenant`). 응답 `roles` = 요청 역할 + T 의 그룹 · 노드 역할 · 감사 `target_tenant_id` = T. 컨트롤러가 `X-Tenant-Id` 를 넘긴다 | `application/PatchOperatorRoleUseCase.java` · `presentation/OperatorAdminController.java` · 포트 `findRoleRowsOfOperator` · `deleteDirectOperatorRolesInTenant`(+ `deleteOperatorRoles` 삭제 — 포트에 «운영자 행 전부 삭제» 가 더는 없다) | D-7 · F6 · Failure 3 · S1-1 |
| 2 | **행 전체 규칙 한 자리** `OperatorRowScopeGuard`: `'*'` grant scope → 통과 · 대상 소속 하나 {T} → T ∈ scope · 둘 이상 → 행위자가 그중 하나라도 관리하면 `403 OPERATOR_HAS_OTHER_MEMBERSHIPS`, 아니면 `TENANT_SCOPE_DENIED` · 0 / `'*'` 대상 → `'*'` 만. 역할 **이름** 무관(F11). 거절은 best-effort DENIED 행 | `application/OperatorRowScopeGuard.java`(신설) | R3 · D-8 · S1-2 |
| 3 | 위 규칙을 상태 · 프로필(**네 번째 게이트 사본 제거** — 호출자 «배정 ∪ 홈» 으로 대상 홈을 보던 코드) · 신원 링크/해제에 | `PatchOperatorStatusUseCase` · `UpdateOperatorProfileUseCase` · `LinkOperatorIdentityUseCase` · `UnlinkOperatorIdentityUseCase` | #12 ~ #14 · F2 |
| 4 | **«이 회사에서 내보내기»**: 범위(403) → 소속 존재(404) → 마지막 관리자(409) → `OperatorCompanyExitCascade`(T 그룹 멤버십 + 그 fan-out → `partner = T` 참여 행 → T 역할 행 **모든 출처**) → 배정 삭제 → 감사 `detail` = `roleRows=…,groupMemberships=…,participations=…` → 캐시 무효화. 운영자 행 · 계정 무접촉, 소속 0 이어도 행 ACTIVE | `ManageOperatorAssignmentUseCase.unassignOperator` · `application/OperatorCompanyExitCascade.java`(신설) · 포트 `deleteOperatorRolesInTenant` · `TenantPartnershipPort.removeParticipationsWithPartner` · repo 질의 3(`deleteByOperatorIdAndTenantId` · `deleteDirectByOperatorIdAndTenantId` — 둘 다 flush/clear · `OperatorGroupMemberJpaRepository.findByOperatorIdAndGroupTenantId` · 참여 삭제) | D-9 · D-10 · F18 · Edge 2 · 3 |
| 5 | **마지막 관리자 보호** `TenantLastAdminGuard`: 쓰기 뒤 T 에 ACTIVE `TENANT_ADMIN`@T(직접 · 그룹 · 노드) 0 → `409 TENANT_LAST_ADMIN_REQUIRED`, 쓰기 전 판정. T = `'*'` · 대상이 T 관리자 아님 → 검사 없음. 호출처: 내보내기 · 역할 변경 · 그룹 멤버 제거 · 그룹 역할 회수 · **그룹 삭제**(아래 § 4-1) | `application/TenantLastAdminGuard.java`(신설) · 포트 `findActiveRoleRowsInTenant` | 소유자 결정 2026-10-11 |
| 6 | 그룹 멤버 술어 = **소속**(그룹 테넌트의 배정 행) · `'*'` 대상 400 · fan-out 역할 행 `tenant_id` = **그룹 테넌트**(skip 키 같음) — `Member.tenantId` 의 의미를 «그룹 테넌트» 로 | `GroupAdminUseCase`(addMember · removeMember · resolveMembers) · `GroupFanOutService`(주석 · `revokeMemberFanOut` 이 지운 역할 행 수 반환) | D-11 · F10 |
| 7 | 파트너십 참여자 술어 = `partner ∈ 대상 소속` | `ManagePartnershipParticipantUseCase` | D-11 |
| 8 | 배정 생성: `'*'` 대상 400(D-14 · F13) · 성공 시 캐시 무효화(소속 추가) | `ManageOperatorAssignmentUseCase.assignOperator` | D-14 · D-5 |
| 9 | 새 코드 2 매핑(`OPERATOR_HAS_OTHER_MEMBERSHIPS` 403 · `TENANT_LAST_ADMIN_REQUIRED` 409 — S1 에 등록 완료) | `presentation/advice/AdminExceptionHandler.java` · `application/exception/*` 2 | — |
| 10 | 시드 주석: `demo-assigned-only` = 이제 «demo-corp 소속 · 역할 0» — 그룹이 받아들인다(콘솔 «배정만 됨» 회색 처리는 S6 이 소속 술어로) | `db/migration-dev/R__seed_demo_assigned_only_operator.sql`(데이터 무변경) | F10 |
| 11 | 시험 자리 표 784 행 4줄(AC-1 · AC-2 · AC-4 · AC-5) | `projects/iam-platform/specs/features/multi-tenancy.md` § 7 | S1 § 4-5 |

### 2. 시험 (실패 쪽 먼저)

- **`InMemoryRoleRowsPort`**(신설, 시험 전용): 목은 «운영자 행 전부 삭제» 를 조용한 no-op 으로 삼켜 «B 행 바이트 불변» 을 증명하지 못한다 — 진짜로 지우는 가짜 포트(쓰기 축이 안 쓰는 메서드는 throw → 운영자 행 쓰기 0 도 덤으로 단언).
- **AC-4 · F6** `PatchOperatorRoleUseCaseTest`(재작성, STRICT_STUBS): 🔴 alpha 편집 → beta 직접 행 · alpha 그룹 행 · alpha 노드 행 **바이트 불변**(`grantedAt` 포함) · alpha 직접 = 요청 · 응답 = 요청 + alpha 그룹/노드 역할 / 대조군 헤더 없음 + 소속 하나 / 헤더 없음 + 둘 → 400 · 0 → 404 · 범위 검사 전 / 비소속 헤더 404 / `'*'` 헤더 400 · `'*'` 대상 T=`'*'` / 🔴 마지막 관리자 409(SUSPENDED 관리자 불산입) → 대조군 ACTIVE 관리자 / 재부여 · 미지 역할 · 미지 운영자.
- **R3** `OperatorRowScopeGuardTest`(신설, 6): 🔴 소속 둘 · 회사 관리자 → `OPERATOR_HAS_OTHER_MEMBERSHIPS` + DENIED 행 / `'*'` 통과 · 아무것도 관리 안 함 → `TENANT_SCOPE_DENIED` · 🔴 F11 회사 범위 SUPER_ADMIN ≠ 플랫폼 · 소속 하나 = 그 회사 관리자(홈 관리자 거절 — R1) · 소속 0 · `'*'` 대상.
- `UpdateOperatorProfileUseCaseTest`(재작성 — **진짜** 가드 + 목 grant scope/해소기): 🔴 F2 «배정만 된» 호출자 거절(이전 시험은 이 누수를 핀하고 있었다) · 🔴 소속 둘 → 403 · 대조군 단일 회사.
- **AC-2 단위** `OperatorCompanyExitUseCaseTest`(신설, 진짜 cascade + 가드 + 가짜 포트): 🔴 alpha 내보내기 → alpha 역할 행(직접 · 그룹 · 노드) 0 · beta 바이트 불변 · 다른 사람 행 무접촉 · 그룹 fan-out 이 alpha 로 · 멤버십 삭제 · 참여 삭제 · 배정 삭제(alpha 만) · 감사 detail · 캐시 / 🔴 유일 관리자 자기 내보내기 409 · 쓰기 0 · cascade 무호출 / 대조군 그룹 출처 관리자도 관리자 · 다른 ACTIVE 관리자(노드 출처) 있으면 허용 / 404 / `'*'` 배정 400 · 소속 추가 무효화.
- `TenantLastAdminGuardTest`(신설, 3) · `GroupAdminUseCaseTest` +4(🔴 홈 = 그룹 테넌트지만 비소속 → 422 · 🔴 홈 globex · acme 소속 → 추가 + fan-out `tenantId`=acme · `'*'` 400 · 🔴 가드 거절 시 멤버 제거 / 역할 회수 / 그룹 삭제 쓰기 0) · `ManagePartnershipParticipantUseCaseTest` +1(🔴 홈 = 파트너지만 비소속 → 422 / 홈 밖 · 소속 → 추가) · `OperatorAdminControllerSliceTest` +3(헤더 전달 · 409 · 403 매핑).
- 시그니처 따라 고침: `PatchOperatorStatusUseCaseTest` · `LinkOperatorIdentityUseCaseTest` · `UnlinkOperatorIdentityUseCaseTest`(가드 검증이 «대상 홈» 이 아니라 «대상 행») · `ConsumerPoolTenantRefusalTest` · `FanPlatformPlatformOperatorOnlyTest`.
- **IT**(Testcontainers — CI 판정) `integration/OperatorMembershipWriteAxisIntegrationTest`(신설): `#ac2_leaveOneCompany`(🔴 유일 관리자 자기 내보내기 409 · 쓰기 0 → 내보내기 → A assume `assigned=false` · A 감사 403 / B assume true · B 감사 200 · A 역할 행 0 · B 바이트 불변(granted_at 까지) · A 그룹 멤버십 0 · 참여는 partner=B 만 · detail `roleRows=2,groupMemberships=1,participations=1` · 운영자 행 `ACTIVE|subject|홈` 그대로 · `ACCOUNT_*` 감사 0, 계정 서비스 URL 은 아무도 듣지 않는 포트) · `#ac4_rowWideRule`(🔴 회사 관리자 정지 403 `OPERATOR_HAS_OTHER_MEMBERSHIPS` · `'*'` 200 · 단일 회사 대조군 200) · `#ac4_roleEditInA_leavesBByteUnchanged`(`[]` → A 0 · B 바이트 불변 · 소속 유지) · `#ac5_perCompanyDecisionNextRequest`(A 역할 제거 → 다음 요청 A 403 `TENANT_SCOPE_DENIED` · B 200 / B 내보내기 → `PERMISSION_DENIED` = 합 캐시가 무효화됐다는 판별 — 낡은 캐시면 aspect 가 통과해 `TENANT_SCOPE_DENIED` 였다).
- **IT 픽스처 수선**(R2 · D-7 · D-8 · D-11 로 빨개질 자리 — 손 시드 운영자에 V0051 소속 행): `GroupItSeeds`(그룹 IT 3 클래스) · `OrgNodeAdminIntegrationTest`(상태 = 행 전체 규칙) · `OperatorAdminIntegrationTest`(WI-2 칸) · `OperatorAdminScopeConfinementIntegrationTest` · `TenantAdminRoleSeedIntegrationTest` · `TenantAdminDelegationIntegrationTest`(역할 칸 셋만 — 배정 칸은 201 을 기대하므로 넣지 않음). `GroupAdminIntegrationTest#crossTenantMember` 기대값 교체: `'*'` 멤버 422 → **400**(S1-6 계약) + 다른 회사 운영자 422 칸 신설. `DemoOperatorSeedIntegrationTest` +1(`demo-assigned-only` 를 demo-corp 그룹이 받는다 · 역할 행 여전히 0).
- **e2e 시드**: `tests/federation-hardening-e2e/fixtures/seed.sql` — `rt-protected-target` · `rt-untagged-target` 에 `ip-pilot-corp` 소속(그 스펙은 헤더 없이 역할만 바꾼다 → 소속 0 이면 404).

### 3. 실행 (rc 는 각자 따로 — 파이프 없음)

| 명령 | rc | 결과 |
|---|---|---|
| `./gradlew :projects:iam-platform:apps:admin-service:compileJava` · `compileTestJava` | **0** · **0** | IT 포함 컴파일 |
| `./gradlew :projects:iam-platform:apps:admin-service:test` (최종) | **0** | 1166 tests · fail 0 · skip 58(Testcontainers IT — Docker 없음) |
| 🔴 **bite 1**: 역할 교체를 «운영자 행 전부 삭제»(`findRoleNamesByTenant` 의 모든 테넌트에 `deleteOperatorRolesInTenant`)로 되돌림 → `--tests PatchOperatorRoleUseCaseTest` | **1** | 9 중 **1 빨강** — F6/«B 바이트 불변» 칸(:86, beta 행 단언)만. 복원 |
| 🔴 **bite 2**: `TenantLastAdminGuard` 의 거절 분기 비활성 → `TenantLastAdminGuardTest` · `PatchOperatorRoleUseCaseTest` · `OperatorCompanyExitUseCaseTest` | **1** | 17 중 **5 빨강** — 409 칸 전부(가드 2 · 역할 1 · 내보내기 2), 나머지 12 초록. 복원 후 같은 세 클래스 rc **0** |
| auth-service | — | 건드리지 않아 돌리지 않았다(AC-2 «계정 무변경» 은 admin 쪽 단언) |
| 가드 6종(`git add` 뒤): `check-index-queue-drift` · `check-task-id-collision` · `check-walkthrough-ledger-drift` · `check-error-code-registry` · `check-flyway-version-collision` · `check-dev-seed-migration-band` | **0** ×6 | — |
| `integrationTest` | ⚪ | Docker 없음 — 위 IT · 수선한 픽스처 8 클래스는 CI 가 판정 |

### 4. 계약과 다르게 / 계약이 정하지 않아 정한 곳 (🔴 = 소유자가 볼 만한 것)

1. 🔴 **그룹 삭제에도 마지막 관리자 술어**를 걸었다. 계약(§ DELETE …/assignments «같은 술어를 역할 변경 · 그룹 멤버 제거 · 그룹 역할 변경에도»)은 그룹 삭제를 적지 않았지만, 삭제는 그룹이 준 모든 역할을 회수하므로 빠지면 같은 우회가 된다. 계약 문장 갱신은 S6 전 문서 정리 몫.
2. 🔴 **T 그룹의 `TENANT_ASSIGNMENT` fan-out**: 내보내기는 계약대로 T 그룹 멤버십의 fan-out 을 **전부** 걷는다 — 그 그룹이 다른 회사 U 로의 배정을 준 경우 U 의 **소속**(배정 행)도 사라지는데 U 의 역할 행은 남는다(D-1 «비-`'*'` 역할 행 테넌트 ∈ 소속» 위반 상태). 결과: U assume 은 거절되지만 grant scope 는 역할 행 기준이라 U 의 **관리 권한은 남는다**. 계약이 이 경우를 말하지 않는다 — 결정 필요(후보: fan-out 배정 행은 남긴다 / U 의 역할 행도 함께 걷는다).
3. 내보내기의 참여 행 삭제는 `partnership.participant_removed` outbox 이벤트를 **내지 않는다**(감사 detail 의 수만). 계약 § 내보내기가 이벤트를 요구하지 않는다 — 소비자가 생기면 다시 본다.
4. `OPERATOR_HAS_OTHER_MEMBERSHIPS` 의 DENIED 행 `attempted_tenant_id` = 대상 **홈**(R1 «감사 기본값» — 행 전체 거절은 회사 하나로 귀속되지 않는다). 소속 하나 거절은 그 회사.
5. 역할 변경: 헤더 생략 + 소속 둘 이상 400 · 0 → 404 가 **범위 검사 전**이다(계약 표 순서 T → 범위). 범위 밖 행위자가 400/404 로 대상의 소속 수를 구분할 수 있다 — 계약 그대로 두었다.
6. 노드 grant(`OrgNodeAdminUseCase`)는 아직 `tenant_id` = 대상 **홈**(D-1 «부여 당시 소속»). S4 슬라이스 행에 없어 손대지 않았다 — 홈 비소속 대상에게 노드 grant 를 주면 그 행은 내보내기 단위 밖에 남는다.
7. 🔵 e2e `tenant-admin-delegation.spec.ts` 의 `finally` 복원(`patchRoles(TARGET, ['SUPPORT_READONLY'])`)은 이제 대상이 소속 0 이라 404 — `.catch` 로 삼켜져 결과는 같다(기준 역할이 사라질 뿐, 다음 실행은 배정 → 역할 부여로 다시 만든다). nightly 에서 확인.

### 5. CI 가 판정할 것 · 남은 것

- **CI**: admin-service `integrationTest`(신규 IT 1 · 수선 8 · 그룹 IT 3 클래스 공용 시드) · nightly `federation-hardening-e2e`(시드 2행 · 위임 스펙의 내보내기 의미 변화).
- **S5**: 두 409 → 소속 추가(D-12) · 발급 «이미 운영자» 소속 기준(F15) · OD-1 상한 · 772 AC-6 IT 기대값 교체 · 소속 추가 시 캐시 무효화.
- **S6**: 콘솔 — 역할 편집 활성 회사 · «이 회사에서 내보내기» 문구 · `OPERATOR_HAS_OTHER_MEMBERSHIPS` · `TENANT_LAST_ADMIN_REQUIRED` 매핑 · 그룹 선택기 회색 처리 → 소속 술어 · `homeTenantId` 표기.
- ~~위 § 4-1 · 4-2 · 4-6 소유자/후속 판단.~~ → § 6 소유자 결정 반영.
- **S5/S6 이후 (미배정 — 잃지 않게)**: 노드 grant(`OrgNodeAdminUseCase`)가 아직 `tenant_id` = 대상 **홈**을 쓴다(D-1 «부여 당시 소속» 위반 가능 — 홈 비소속 대상의 노드 행은 내보내기 단위 밖에 남는다). 슬라이스 S5 · S6 어디에도 없으므로 착수 시 어느 슬라이스(또는 후속 티켓)가 가져갈지 정한다. (§ 4-6 에서 옮김, 소유자 지시 2026-10-13 UTC)

### 6. S4 소유자 결정 반영 (2026-10-13 UTC)

> PR #4321 첫 CI 전부 초록(16 pass) 뒤 소유자 결정. 같은 브랜치에 추가 커밋.

| § 4 항목 | 결정 | 반영 |
|---|---|---|
| 4-2 T 그룹 fan-out 이 U 소속을 걷는 경우 | **«U 역할 행도 함께 지움»** — 그 그룹이 이 사람에게 만든 것(`group_origin` = 그 그룹)은 **모든 테넌트에서** 걷는다: U 배정 행 · U 역할 행. 이 사람이 U 에 직접(또는 U 그룹으로) 가진 행은 바이트 불변. 그 그룹 fan-out 이 `TENANT_ADMIN`@U 였다면 U 에도 마지막 관리자 술어(쓰기 전, 같은 409) | `OperatorCompanyExitCascade` 재작성 — fan-out 엔진 대신 포트 `AdminOperatorPort.deleteOperatorRolesByGroupOrigin` · `OperatorTenantAssignmentPort.deleteGroupOriginAssignments`(모든 테넌트) + 떠나는 그룹들의 다른 테넌트 행에 대해 `TenantLastAdminGuard` 를 먼저. 감사 detail 에 `groupAssignments` 추가. 계약 `admin-api.md` § 내보내기 표 · 감사 · 마지막 관리자 블릿 |
| 4-5 역할 변경이 400/404 로 소속 수를 드러냄 | **«범위 확인을 먼저»** — 헤더 생략 · 비-`'*'` actor 가 대상 소속 중 어느 것도 관리하지 않으면 400/404 보다 먼저 `403 TENANT_SCOPE_DENIED`(DENIED 행 = 대상 홈) | `PatchOperatorRoleUseCase` 사전 검사 · `TenantScopeGuard.recordTenantDenied`(신설). 계약 § PATCH …/roles 표에 «0. 범위 먼저» 행 · 오류표 |
| 4-1 그룹 삭제의 마지막 관리자 | 구현 유지 · 계약 문구 갱신 | `admin-api.md` 마지막 관리자 블릿(그룹 역할 회수 · 그룹 삭제 명시) · `platform/error-handling.md` · `rules/domains/saas.md` 의 `TENANT_LAST_ADMIN_REQUIRED` 경로 나열 |
| 4-3 · 4-4 · 4-6 | 지금 변경 없음(기록대로). 4-6 은 § 5 «S5/S6 이후» 로 옮김 | — |

- ~~🔵 남는 경우 하나: U 소속이 그 그룹 fan-out 배정뿐이고 U 에 직접 역할 행이 있으면 그 직접 행이 소속 없이 남는다.~~ → § 6-2 «U 소속을 직접 소속으로 남김» 으로 닫힘.
- **시험**: `OperatorCompanyExitUseCaseTest` +2 — 🔴 그룹 9(alpha)가 uu 에 만든 역할 행 제거 · 이 사람의 직접 uu 행 · vv 행(vv 그룹 행 포함) 바이트 불변 · `deleteGroupOriginAssignments(9)` · uu/vv 배정 직접 삭제 0 / 🔴 그룹 9 fan-out 이 uu 의 유일 `TENANT_ADMIN` → 409 · 쓰기 0 → 대조군 다른 uu 관리자 있으면 통과. 기존 칸 detail 기대값 갱신. `PatchOperatorRoleUseCaseTest` +1 — 🔴 헤더 없음 · 아무 소속도 관리 안 함 → 소속 둘 / 0 둘 다 403(400 · 404 아님) + DENIED 행 = 홈; 기존 400 · 404 칸은 «소속 하나를 관리하는 actor» · «`'*'` actor» 로 고정. IT `#ac2_groupFanOutIntoOtherCompanies_goesWithTheGroup` 신설(A 그룹 G 가 U · V 배정 부여 · V 는 직접 소속이라 skip · G 의 U 역할 행은 pre-S4 모양으로 jdbc 삽입 · 직접 U 역할 → 내보내기 뒤 G 의 U 배정 · U 역할 행 없음, 직접 U 역할 · 직접 V 소속 · V 역할 바이트 불변) · `#ac2` detail 기대값 갱신. U 마지막 관리자의 IT 칸은 만들지 않았다(post-S4 API 로는 그룹 역할 fan-out 이 그룹 테넌트에만 생겨 U 의 `TENANT_ADMIN` 그룹 행을 만들 수 없다 — 단위 칸이 판정).
- **rc**: `:admin-service:test` **0**(1169 tests · fail 0 · skip 58). 🔴 **bite 3**: cascade 를 «그룹의 T 행만»(`deleteOperatorRolesByGroupOrigin` 호출 제거 — T 행은 테넌트 삭제가 걷으므로 T 만 남는다)으로 → `OperatorCompanyExitUseCaseTest` 7 중 **2 빨강**(새 uu 칸 · uu 마지막 관리자 칸의 대조군 절반), 나머지 5 초록 → 복원 후 rc **0**. 가드(`git add` 뒤): index-queue-drift · task-id-collision · walkthrough-ledger-drift · error-code-registry · domain-error-code-registry 모두 **0**. IT 는 ⚪ CI.

#### 6-2. 소유자 결정 추가 (2026-10-13 UTC) — «U 소속을 직접 소속으로 남김»

- **결정**: 떠나는 T 그룹의 fan-out 배정 행(U)을 지우려 할 때, 이 사람이 U 에 **떠나는 그룹들이 만들지 않은** 역할 행(직접 · 다른 그룹 · U 에 기록된 노드 grant)을 하나라도 가지면 U 배정을 지우지 않고 **직접 소속으로 바꾼다**. 없으면 지금처럼 지운다. 그룹 자신의 U 역할 행은 그대로 걷는다(결정 1 불변). 위 «남는 경우 하나» 를 닫는다.
- **구현**: `OperatorCompanyExitCascade` — 쓰기 전 역할 행 스냅샷에서 «떠나는 그룹 출처가 아닌 역할 행이 있는 T 밖 테넌트» 집합을 만들고, 그룹마다 `findGroupOriginAssignmentTenants` 로 그 그룹의 배정 테넌트를 열거 → 그 집합에 있으면 `convertToDirectAssignment`(신설 — `group_origin` → NULL **만**), 그 뒤 `deleteGroupOriginAssignments` 가 나머지를 지운다. 🔵 `granted_by` 는 **바꾸지 않았다**(nullable 칼럼이라 요구되지 않음 — 원래 부여자 기록을 남긴다; 변환은 감사 detail `keptAsDirect` 와 `OPERATOR_ASSIGNMENT_DELETE` 행의 행위자로 추적된다). `granted_at` · `org_scope` · `permission_set_id` 그대로. 포트 `OperatorTenantAssignmentPort` +2. 계약 `admin-api.md` 내보내기 표(예외 문장) · 감사 detail(`keptAsDirect`).
- **시험**: `InMemoryAssignmentPort`(신설 — 진짜 변환 · 삭제) · `OperatorCompanyExitKeepMembershipTest`(신설, 3): 🔴 (a) 직접 uu 역할 + uu 소속은 G 출처뿐 → 내보내기 뒤 uu 배정 존재 · `group_origin` NULL · `granted_at` · `granted_by` · `org_scope` 그대로 · 직접 uu 역할 바이트 불변 · G 의 uu 역할 없음 · alpha 소속 없음 / (b) 대조군 다른 uu 역할 없음 → uu 배정 삭제 / 떠나지 않는 다른 그룹의 uu 역할도 소속을 지킨다. 기존 detail 기대값에 `keptAsDirect=0`. IT `#ac2_groupFanOutIntoOtherCompanies_goesWithTheGroup` 확장: G 가 U · V · W 배정 부여(V skip) · 직접 U 역할 → U 배정 **직접으로 남음**(granted_at 동일) · W 배정 삭제 · G 의 U 역할 없음 · V 불변.
- **rc**: `:admin-service:test` **0**(1172 tests · fail 0 · skip 58). 🔴 **bite 4**: 변환 분기 비활성(무조건 삭제) → `OperatorCompanyExitKeepMembershipTest` 3 중 **2 빨강**((a) · 다른 그룹 칸), 대조군 (b) 초록 → 복원 후 그 클래스 + `OperatorCompanyExitUseCaseTest` rc **0**. 가드(`git add` 뒤): index-queue-drift · task-id-collision · walkthrough-ledger-drift · error-code-registry 모두 **0**. IT ⚪ CI.

---

## S5 기록 (2026-10-13 UTC)

> 구현=Opus 5.5 (backend-engineer) · worktree `wt-784` · 브랜치 `feat/mono-784-s5-add-membership`(origin/main `8590f6636` = S4 #4321 위). 근거 = § AC-0 D-5 · D-12 · D-14 · F14 · F15 · OD-1 · S1 계약 `onboarding-api.md` § 다회사 운영자와 온보딩(판정 표 5행) · § 신원당 상한 · `internal/auth-to-admin.md` § accept 6 · 7 · «소속 추가일 때» · `admin-api.md` § Operator Invitation 발급 7 · «대기 하나».
> 경로는 따로 적지 않으면 `projects/iam-platform/apps/admin-service/src/main/java/com/example/admin/` 아래.

### 1. 행동 변경

| # | 무엇 | 파일 | 근거 |
|---|---|---|---|
| 1 | **소속 추가 한 자리** `OperatorMembershipAddition`(신설): `requireCanReceive` = 회사 테넌트인가(`'*'` → 400) → `'*'` 행(홈 `'*'` **또는** `'*'` 역할 행 — 엄격한 쪽) → 비-`ACTIVE` → 이미 그 테넌트 소속 — 셋 다 `409 OPERATOR_ALREADY_PROVISIONED`. `write` = 그 테넌트의 직접 역할 행 + 배정**만**(호출자 트랜잭션 안) · `invalidatePermissionCache`(커밋 뒤, best-effort). 773 «테넌트 생성» 이 부를 자리 | `application/OperatorMembershipAddition.java` | D-12 · D2 · D-14 · D-5 |
| 2 | **OD-1 상한** `OperatorOwnedTenantLimit`(신설): 단위 = 그 행의 `TENANT_ADMIN` 역할 행의 서로 다른 비-`'*'` 테넌트 수(출처 무관 · 테넌트 상태 안 읽음) · `count ≥ 상한` → `422 OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED`(새 예외 + 핸들러) · 설정 `admin.onboarding.max-owned-tenants-per-operator`(기본 2, `< 1` 기동 거절) | `application/OperatorOwnedTenantLimit.java` · `application/exception/OperatorOwnedTenantLimitExceededException.java` · `presentation/advice/AdminExceptionHandler.java` · `src/main/resources/application.yml` | OD-1 · S1-5 |
| 3 | **셀프 온보딩** — 판정 표 1~5행을 **테넌트 생성 전에**: 측면 없음 → 새 행(그대로) · 측면 있음 → `requireCanReceive` → 상한 → 테넌트 생성 → `FirstAdminProvisioner.addMembership`(신설 — 그 행에 `TENANT_ADMIN` + `TENANT_BILLING_ADMIN`@새 테넌트 + 배정, 한 트랜잭션, 운영자 행 · `oidc_subject` · 신원 해소 쓰기 0) → 실패 시 SUSPEND 보상(그대로) → 캐시 무효화. 응답 `operatorId` = 기존 행 | `application/SelfServiceOnboardingUseCase.java` · `application/FirstAdminProvisioner.java` | D-12 · OD-1 · F14 |
| 4 | **초대 수락** 6단계 = ① 측면 없음 → 새 행 ② `requireCanReceive` 통과 → `OperatorInvitationAcceptanceWriter.acceptAsMembership`(신설 — 선점 → 초대 역할 행(직접)@초대 테넌트 + 배정 → `accepted_operator_id` = 기존 행 → 감사 행위자 = 기존 운영자 · `downstream_detail` = `accountId=…,membershipAdded=true`) ③ 그 밖 409. 7단계 = 초대 테넌트 **소속** 중 같은 이메일(받는 행 제외 — `existsMemberWithEmail`). 경합: 소속 추가 중 배정 PK 위반 → 다시 읽어 6 ③ 409. 신원 링크 시도 0 · 커밋 뒤 캐시 무효화. 상한은 보지 않는다(S1-5) | `application/OperatorInvitationAcceptanceUseCase.java` · `application/OperatorInvitationAcceptanceWriter.java` | D-12 · F15 · auth-to-admin «소속 추가일 때» |
| 5 | **발급 «이미 운영자»** = 초대 테넌트 **소속**(배정 행)인 같은 이메일 — 홈 기준 아님 | `application/OperatorInvitationWriter.java` · 포트 `AdminOperatorPort.existsMemberWithEmail` · `infrastructure/persistence/rbac/AdminOperatorJpaRepository.existsMemberWithEmail`(JPQL — 목록과 같은 소속 술어) · `JpaAdminOperatorAdapter` | F15 · admin-api 발급 7 |
| 6 | `OperatorAlreadyProvisionedException` javadoc — 의미 좁힘 기록(코드 문자열 무변경) · 시험 자리 표 784 AC-3 행 | `application/exception/OperatorAlreadyProvisionedException.java` · `projects/iam-platform/specs/features/multi-tenancy.md` § 7 | — |

### 2. 시험 (실패 쪽 먼저)

- **진짜 쓰는 가짜 포트**: `InMemoryAssignmentPort` 에 `createAssignment` 를 붙였다 — 기본은 여전히 throw(S4 내보내기 시험의 «배정을 만들지 않는다» 를 지킨다), S5 시험만 `allowCreate()`. `InMemoryRoleRowsPort` 는 운영자 행 쓰기(생성 · `oidc_subject` · 신원 링크)에 throw 하므로 «운영자 행 쓰기 0» 이 구조로 단언된다.
- 🔴 **AC-3 바이트 불변** — `OperatorMembershipAdditionTest`(6): 새 테넌트 역할 행 둘(직접) + 배정 하나 · 기존 alpha(직접 · 그룹 · 노드) · beta 역할 행과 배정(`org_scope` 포함) · 다른 사람 행 **바이트 불변**(`grantedAt` 포함) / 🔴 같은 테넌트 409 · 쓰기 0 / 🔴 `'*'`(홈 · 역할 행 둘 다) 409 → 대조군 회사 행 통과 / 비-ACTIVE 409 / `'*'` 테넌트 400 · 쓰기 0 / 캐시 무효화 best-effort.
- `OperatorOwnedTenantLimitTest`(3): 🔴 세는 단위(직접 · 그룹 · 노드 출처 산입 · 같은 테넌트 두 역할 = 1 · 다른 역할 · `'*'` · 남의 행 제외) / 🔴 기본 2 — 하나 통과 · 둘 422 / 설정 3 → 통과 · 0 기동 거절.
- `SelfServiceOnboardingUseCaseTest$ExistingFacet`(6, **진짜** 소속 추가 · 상한 · 프로비저너 + 가짜 포트): 🔴 5행 — 201 · `operatorId` = 기존 · 새 테넌트 두 역할 + 배정 · 기존 행 바이트 불변 · 캐시 무효화 / 🔴 4행 상한 422 · 테넌트 생성 0 · 쓰기 0 / 대조군 상한 3 통과 / 🔴 2행 `'*'` 409 · 생성 0 / 3행 SUSPENDED 409 / 생성 뒤 실패 → SUSPEND 보상 · 무효화 0. 기존 새 행 경로 4칸은 그대로(772 «측면이면 409» 칸은 이 표로 대체).
- `OperatorInvitationAcceptanceUseCaseTest$MembershipAddition`(5): 🔴 소속 추가 writer · 응답 = 기존 행 · 새 행 writer 0 · 신원 링크 0 · 무효화 · 7단계 자기 행 제외 / 🔴 같은 테넌트 409 / 🔴 `'*'` 409 / F15 다른 소속 운영자 같은 이메일 409 / 배정 PK 경합 → 409. 기존 «SUSPENDED 측면 409» 칸은 6 ③ 으로 이름만 고침.
- `OperatorInvitationAcceptanceWriterTest` +2(소속 추가 트랜잭션 — 가짜 포트로 초대 테넌트 행만 · 다른 회사 바이트 불변 · 표시 이름 · 홈 그대로 · 감사 / 선점 실패 → 쓰기 0) · `FirstAdminProvisionerTest` +1(`addMembership` 새 테넌트 행만 · 계정 서비스 호출 0) · `OperatorInvitationWriterTest`(발급 술어 = 소속, 홈 술어 호출 0).
- **IT 기대값 교체**(`integration/OperatorInvitationAcceptanceIntegrationTest`, Testcontainers — CI 판정): `#ac6_*` 5단계 «두 번째 회사 409» → **200 · 같은 운영자 · `TENANT_X` 역할 행 + 배정 · newOrg 역할 행 · 배정 · 운영자 행(홈 · 이메일 · 표시 이름 · `oidc_subject` · `identity_id` · `updated_at` · `version`) 바이트 불변 · `downstream_detail` `membershipAdded=true`** / 5b 같은 테넌트의 (소속 전에 발급된) 두 번째 초대 → 409 · PENDING / 5c F15 발급 → 409 `OPERATOR_EMAIL_CONFLICT`(홈 기준이면 201 이었다) / 6단계 «셀프 온보딩 409» → **422 상한**(newOrg · `TENANT_X` 둘) · 테넌트 생성 호출 0 / 7단계(신설) 창업자 두 번째 조직 → 201 · 같은 행 · 새 조직 두 역할 + 배정 · 첫 조직 바이트 불변 → 세 번째 422. 신설 `#platformAndSuspendedFacets_refusedBothPaths`(`'*'` · SUSPENDED 측면 → 수락 409 · 온보딩 409 · 생성 0 · 쓰기 0).

### 3. 실행 (rc 는 각자 따로 — 파이프 없음)

| 명령 | rc | 결과 |
|---|---|---|
| `./gradlew :projects:iam-platform:apps:admin-service:compileJava` · `compileTestJava` | **0** · **0** | IT 포함 컴파일 |
| `./gradlew :projects:iam-platform:apps:admin-service:test` (최종) | **0** | 186 suites · 1194 tests · fail 0 · error 0 · skip 58(Testcontainers IT — Docker 없음) |
| 🔴 **bite 1a**: `OperatorMembershipAddition.write` 가 역할 행을 새 테넌트에 가두지 않고 그 행의 **모든 소속 테넌트**에 쓰게 → 4 클래스 | **1** | 19 중 **4 빨강** — 바이트 불변 칸만(`OperatorMembershipAdditionTest` AC-3 · `SelfServiceOnboardingUseCaseTest$ExistingFacet` 5행 · `OperatorInvitationAcceptanceWriterTest` 소속 추가 · `FirstAdminProvisionerTest` addMembership), 나머지 15 초록 → 복원 |
| 🔴 **bite 1b**: 수락 · 온보딩을 «측면이면 409» 로 되돌림 → 두 클래스 | **1** | 32 중 **8 빨강** — 소속 추가 · 같은 테넌트 · F15 · 경합 · 5행 · 4행 상한 · 상한 3 · 보상 칸; `'*'` · SUSPENDED 409 칸과 새 행 경로는 초록(물 것만 문다) → 복원 |
| 🔴 **bite 2**: 상한 비활성(`false &&`) → `OperatorOwnedTenantLimitTest` · `SelfServiceOnboardingUseCaseTest` | **1** | 13 중 **2 빨강** — 422 칸 둘(단위 · 4행) → 복원 후 전체 `test` rc **0**(위 최종) |
| 가드 6종(`git add` 뒤): `check-index-queue-drift` · `check-task-id-collision` · `check-walkthrough-ledger-drift` · `check-error-code-registry` · `check-domain-error-code-registry` · `check-flyway-version-collision` | **0** ×6 | — |
| auth-service · account-service | — | 건드리지 않아 돌리지 않았다(코드 문자열 `OPERATOR_ALREADY_PROVISIONED` 무변경 — auth `AdminOperatorInvitationClient` 매핑 그대로) |
| `integrationTest` | ⚪ | Docker 없음 — `OperatorInvitationAcceptanceIntegrationTest` 는 CI 가 판정 |

### 4. 계약과 다르게 / 계약이 정하지 않아 정한 곳 (🔴 = 소유자가 볼 만한 것)

1. 🔴 **«이미 그 슬러그 소속» 을 온보딩도 테넌트 생성 전에 409 로 거른다.** 계약은 «새 슬러그라 생기지 않는다» 고 적었지만, 미등록 테넌트 id 로의 배정(D-2 시드 `demo-viewer` 같은)이 있으면 생긴다 — 그 경우 계약 표에 행이 없다. fail-closed 쪽(409 `OPERATOR_ALREADY_PROVISIONED`, 생성 0)을 골랐다.
2. **`'*'` 행 판정 = 홈 `'*'` 또는 `'*'` 역할 행**(S4 `ManageOperatorAssignmentUseCase` 는 홈만 본다). D-1 불변식상 둘은 같은 집합이어야 하므로 엄격한 쪽 — 계약 «`'*'` 측면» 과 충돌하지 않는다.
3. 🔴 **상한 경합**: 같은 계정의 동시 온보딩 둘은 둘 다 상한 미만을 읽고 둘 다 통과할 수 있다(상한 + 1). 잠금을 두지 않았다 — 773 AC-0 이 값과 함께 볼 것(행 잠금 또는 생성 뒤 재검사 + 보상).
4. `'*'` 테넌트로의 소속 추가는 `400 VALIDATION_ERROR`(D-14 · S1-6) — `'*'` 초대는 발급에서 이미 막혀(772 D-2) 수락 경로로는 닿지 않는다. 계약의 수락 오류표에는 없는 행이다(도달 불가 방어).
5. 수락의 새 행 경로 경합 처리(`DataIntegrityViolationException`)는 홈 기준 `(tenant_id, email)` UNIQUE 를 먼저 보고 그다음 소속 기준을 본다 — DB 제약이 홈 칸이라 새 행 생성은 여전히 홈 UNIQUE 에 걸릴 수 있다(«홈이 T 인데 T 에서 내보내진 같은 이메일의 다른 운영자» 가 있으면 새 행 수락이 409 `OPERATOR_EMAIL_CONFLICT`). 계약 «UNIQUE 위반 → 각 409» 그대로.
6. 감사 `tenant_id`(D-13): 수락은 `<self_invitation_accept>` 자기 흐름이라 행위자 **홈** · `target_tenant_id` = 초대 테넌트 — S3 규칙 그대로(계약 «새 행일 때와 같다»).

### 5. CI 가 판정할 것 · 남은 것

- **CI**: admin-service `integrationTest` — `OperatorInvitationAcceptanceIntegrationTest`(AC-6 5 · 6 · 7단계 새 기대값 · `'*'`/SUSPENDED 칸 신설). 새 JPQL `existsMemberWithEmail` 은 MySQL 위에서 처음 돈다(단위 시험은 포트 목/가짜).
- **S6 (콘솔)**: 온보딩 화면의 `422 OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED` 문구 매핑(지금 콘솔은 409 `OPERATOR_ALREADY_PROVISIONED` 만 안다) · 수락 뒤 다회사 운영자의 스위처에 새 회사가 뜨는지(소속 = 배정 — 백엔드는 그대로) · 그밖 S4 기록 § 5 의 S6 목록.
- **S5/S6 이후 (미배정 — S4 § 5 에서 옮겨 온 것 그대로)**: 노드 grant `tenant_id` = 대상 홈(D-1 «부여 당시 소속»).
