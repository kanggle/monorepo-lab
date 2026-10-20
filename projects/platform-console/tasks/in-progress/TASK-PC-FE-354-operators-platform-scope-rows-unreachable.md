# Task ID

TASK-PC-FE-354

# Title

`/operators` 의 `'*'` 두 공백 — `*` 운영자의 «모든 테넌트» 업무 권한 행을 콘솔에서 주거나 회수할 수 없다(회수 = 404) · «플랫폼 운영자 등록» 양식이 한 번도 그려지지 않았다

# Status

in-progress (2026-10-20 UTC AC-0 측정 · 소유자 결정 — 새 문안은 머지 전 승인)

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus(권한 쓰기의 테넌트 귀속 — 잘못 보내면 다른 테넌트에 행이 생긴다)
> 기안 2026-10-20 UTC — `TASK-PC-FE-353` AC-0 ② 의 발견(소유자 결정 «(가) 문구만 + /operators 별도 티켓»). 스위처에 `*` 를 넣지 않는 **좁은 수선**이다.

# Dependency Markers

- 출처: `TASK-PC-FE-353` § AC-0 결과 ② «옆 화면 `/operators` 에 진짜 공백 둘».
- 선행 없음. 353 과 같은 파일을 건드리지 않는다(353 = 여섯 화면 문구 · 주석).

# Goal

`'*'` 범위를 가진 운영자(예 `demo-star-owner`)가 콘솔 `/operators` 에서:

1. `*` 운영자의 «모든 테넌트» 업무 권한 세트 행을 **부여 · 회수**할 수 있다. 지금은 콘솔이 늘 활성 테넌트를 `X-Tenant-Id` 로 보내므로, 부여는 그 테넌트(`demo-corp`) 행을 만들고 기존 `'*'` 행의 회수는 `demo-corp` 아래에서 0 건을 지워 **404** 가 된다(`OperatorDomainAccessUseCase.java:166-216` · 행은 `X-Tenant-Id` 가 `*` 이거나 없을 때만 `'*'`). 계약 `console-integration-contract.md:267` 은 이것이 된다고 적는다.
2. «플랫폼 운영자 등록» 양식(`OperatorsScreen.tsx:689-696`)과 «모든 회사에 적용» 정지 문구(`:419`)를 본다. 지금은 `app/(console)/operators/page.tsx:133` `isPlatformOperator = selectableTenants(...).includes('*')` 가 늘 false 다(`*` 는 스위처 목록에 없다 — 353 AC-0 ①).

# Scope

## In Scope

- **AC-0 측정**:
  - ① «이 운영자가 `'*'` 범위를 가졌는가» 의 출처 — `GET /api/admin/me` 의 `homeTenantId`(`iam-operators-types.ts:86`, `new-tenant/page.tsx:68` 이 이미 씀) 인가, grant 범위인가(`admin-api.md:88` 은 «grant 범위에 `*`» 로 정의). 둘이 갈라지는 운영자가 있는가.
  - ② 활성 테넌트가 실제 테넌트(`demo-corp`)일 때 `*` 대상 운영자가 `/operators` 목록에 나오는가(353 AC-0 의 추론 · 미측정 — `:267` 은 `*` 활성일 때만이라고 읽힐 수 있다). 안 나오면 1 의 수선은 그 행에 닿지도 못한다.
  - ③ 부여 · 회수 BFF 경로(`/api/admin/operators/{id}/domain-permission-sets` 계열)가 `X-Tenant-Id` 를 어디서 정하는가 — 호출 단위로 `'*'` 를 보낼 자리가 있는가(`shared/api/iam-gateway.ts:300-311`).
  - ④ 계약 `:267` 의 문장이 «활성 테넌트 `*`» 를 전제하는가 — 그렇다면 그 문장을 «대상이 `*` 운영자이면 `'*'` 를 보낸다» 로 먼저 고친다(계약 우선).
- 수선(AC-0 이 확정):
  - 대상이 `*` 운영자이고 호출자가 `'*'` 범위일 때만 그 부여 · 회수 호출이 `X-Tenant-Id: *` 를 보낸다. 그 밖의 모든 호출은 지금처럼 활성 테넌트.
  - `isPlatformOperator` 를 ① 의 출처로.
  - 시험 고정값 정정: `tests/unit/operators-page-parallel.test.tsx:91` `['wms','*']` 처럼 실제로 안 나오는 레지스트리 모양을 실제 모양으로(그 고정값이 `isPlatformOperator=true` 를 만들어 «양식이 보인다» 시험을 거짓으로 초록으로 만든다).

## Out of Scope

- 스위처에 `*` 항목 넣기(353 에서 기각).
- 서버(admin-service) 변경 — 서버는 이미 `X-Tenant-Id: *` 를 받으면 `'*'` 행을 쓴다. AC-0 이 서버도 바꿔야 한다고 보이면 IAM 티켓 먼저.

# Acceptance Criteria

- [x] **AC-0** — 위 ①②③④ 를 file:line 으로. ② 가 «안 나온다» 면 소유자에게 묻는다(목록까지 넓힐지). → § AC-0 결과 · § 소유자 결정.
- [ ] **AC-1** — `*` 대상의 부여 · 회수가 `'*'` 행에 닿는다. 일반 대상 · 일반 호출자는 그대로(활성 테넌트).
- [ ] **AC-2** — `'*'` 범위 운영자에게 «플랫폼 운영자 등록» 양식이 보이고, 아닌 운영자에게는 안 보인다.
- [ ] **AC-3** — 시험: 헤더 선택(`*` 대상 + `'*'` 호출자 → `*` · 일반 대상 → 활성 테넌트 · `'*'` 없는 호출자 → 활성 테넌트) · 양식 표시 양방향 · 거짓 고정값 정정 후에도 초록. 문구가 바뀌면 소유자 문안 승인.
- [ ] **AC-4** — 배포(Vercel) · 라이브(`star-owner@demo.com` 으로 `*` 운영자의 «모든 테넌트» 행 회수 → 200, 다음 데모 창).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`
- `projects/iam-platform/specs/contracts/http/admin-api.md` (§ 테넌트 결정 `:88` · `:102-117`)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` `:267`
- `projects/iam-platform/specs/contracts/http/admin-api.md` — 업무 권한 세트 부여 · 회수

# Edge Cases

- `'*'` 범위 없는 호출자가 `*` 대상을 연다 — 서버가 거절한다(`TenantScopeDeniedException`, `:203-207`). 콘솔은 그 호출에 `*` 를 보내지 않는다(보내도 거절이지만 감사에 tenant-denied 가 남는다).
- 일반 대상에게 `X-Tenant-Id: *` 를 보내면 서버가 400(`:209-212`) — 헤더 선택이 대상 기준이어야 하는 이유.
- 데모 데이터: 29–31차 창이 남긴 DIRECT 행 · TERMINATED 파트너십 등은 다음 굽기가 지운다 — 라이브 시험은 그 위에서 돈다.

# Failure Scenarios

1. 모든 호출에 `*` 를 보낸다 — 일반 대상의 부여가 400, 또는 플랫폼 행이 엉뚱하게 생긴다.
2. 거짓 고정값(`['wms','*']`)을 둔 채 양식 시험을 쓴다 — 초록이지만 라이브는 여전히 양식이 없다.
3. ② 를 재지 않고 1 만 고친다 — `demo-corp` 활성에서 `*` 대상이 목록에 없으면 고친 버튼에 닿을 길이 없다.

# AC-0 결과 (2026-10-20 UTC, origin/main `9a060da42` · 코드 · 시드 · 계약 판독, 라이브 측정 0)

**시드**: `demo-star-owner` = home `tenant_id='*'` · SUSPENDED 기본(`R__seed_demo_star_owner_operator.sql:60`) · SUPER_ADMIN 은 `'*'` 에(`:78-82`) · `operator_tenant_assignment` 행 **없음**(`:84`) · `'*'` legacy-compat MIGRATION 세트 행 하나(`R__seed_zz_demo_domain_permission_sets.sql:55-69`). 자격은 인증 테넌트 `iam`(`:63`)이지만 admin home 은 `'*'`. 대조: `demo-platform` = home `'*'` · `fan-platform` 에 갇힘 · 역할 행 0.

## ① «이 운영자는 `'*'` 범위» 의 출처

- 서버 게이트 `QueryTenantScopeGate.java:128` `isPlatformScope = grantScope.contains('*')` — grant 범위(`AdminGrantScopeEvaluator.effectiveAdminScope` `:80-91`)이지 home 이 아니다. 대상 쪽 판정 `OperatorDomainAccessUseCase.java:269-271` `isPlatform(target)` 은 대상의 **home**. 생성은 home `'*'`(`CreateOperatorUseCase.java:66-82`) + `'*'` grant(`:87`) 둘 다.
- `GET /api/admin/me`(`OperatorAdminController.java:86-92`, 권한 없음 · `X-Tenant-Id` 선택) → `roles`(T 행 + `'*'` 행 — `'*'` 운영자의 SUPER_ADMIN 은 늘 보인다) · `memberships`(`'*'` 운영자는 `[]`) · `homeTenantId`(`OperatorQueryService.java:47-69,143`). 권한별 grant 범위는 안 낸다.
- 갈라지는가: home `'*'` + `'*'` grant 없음 = **있다**(`demo-platform`, 역할 0 ⇒ `operator.manage` 도 없어 `/operators` 403 — 추론). home 실제 테넌트 + `'*'` grant = 역할 쓰기 경로가 막는다(`PatchOperatorRoleUseCase.java:88-93` ADR-082 D-14 · `:165-168` `'*'` 대상은 `'*'` 에서만). `admin-api.md:88` 도 «`'*'` 운영자에게는 같은 값».
- 콘솔: 서버는 `/me` 를 테넌트 없이 받지만 콘솔 `iam-gateway.ts:302-306` 이 먼저 `400 NO_ACTIVE_TENANT` — `/operators` 는 `state.noTenant` 검사 뒤라 무관(`page.tsx:41`). 선례 `new-tenant/page.tsx:66-68` `homeTenantId === '*' && hasPermission(me.roles, 'tenant.manage')`(`getSelfOrNull`, `iam-operators-read.ts:238-247`). `page.tsx:133` 는 늘 false.

## ② 실제 테넌트 활성에서 `*` 운영자가 목록에 나오나 — **안 나온다**

콘솔 `iam-operators-read.ts:164-165` 가 `?tenantId=<활성>` → 서버 `OperatorQueryService.java:93` `allTenants = isPlatformScope && tenantId=='*'`, 아니면 `findOperatorsPageByTenant` = `operator_tenant_assignment` 소속만(`AdminOperatorJpaRepository.java:117-129`, home 은 안 본다). `'*'` 운영자는 소속 행이 0 ⇒ 어느 실제 테넌트 목록에도 없다(`demo-star-owner` 자신도). 나오게 하는 요청 = `'*'` `operator.manage` 호출자의 `GET /api/admin/operators?tenantId=*` — **모든 테넌트의 모든 운영자**(`:103-105`). BFF 는 이미 `tenantId` 를 통과시킨다(`app/api/operators/route.ts:52-55` · PC-FE-317). home `'*'` 만 거르는 서버 필터는 없다.

## ③ 부여 · 회수 경로의 `X-Tenant-Id`

- BFF `app/api/operators/[operatorId]/domain-permission-sets/route.ts:34`(POST) · `…/[setId]/route.ts:38`(DELETE) · `…/domain-access/route.ts:20`(GET) → `operators-domain-access-api.ts:43-75` → `callGapOperators`(`iam-operators-read.ts:124-140`) → `prepareAdminHeaders`(`iam-gateway.ts:302-311`) 가 늘 `X-Tenant-Id: getActiveTenant()`. 호출 단위 덮어쓰기 없음 — `AdminGatewayRequest`(`:72-86`)에 선택 필드를 더하면 안 쓰는 호출은 바이트 동일(추론).
- 대상이 `'*'` 인지: 목록 행 `homeTenantId`(`OperatorQueryService.java:121` · `iam-operators-types.ts:86`) = 서버 대상 판정과 같은 술어. 권한 응답의 `sources[].tenantId` 가 행이 T 인지 `'*'` 인지 말한다(`OperatorDomainAccessDialog.tsx:246-253`).
- 서버 의미: `'*'` 대상은 T 행도 `'*'` 행도 받는다(OD-3 `admin-api.md:3743`) · 읽기는 둘 다 보인다(`:235-240`) · 회수는 `(대상, T, 세트)` 삭제(`:175`) ⇒ **회수는 출처 행의 `tenantId` 로** · `'*'` 부여는 **프리셋만**(`:139`, `admin-api.md:3745`) · 일반 대상 + `'*'` = 400 · `'*'` 없는 호출자 = 403 + DENIED 감사. 역할 PATCH 는 `'*'` 대상에 헤더를 무시하고 `'*'` 로 강제(`PatchOperatorRoleUseCase.java:165-168`) — 세트 쓰기는 아니다.
- 고칠 시험: `operators-domain-access-proxy.test.ts:103,152` 인자 단언.

## ④ 계약 `console-integration-contract.md:267`

> «Platform-scope (`'*'`) target operators appear only to a platform-scope actor; with the active tenant `*` the operator screen grants/revokes the «모든 테넌트» row.»

활성 테넌트 `*` 는 될 수 없다(353 AC-0 ①) · 앞 절반도 지금 UI 에서 거짓. `:261` «Every call carries … `X-Tenant-Id` = the active tenant» 도 예외를 적어야 한다.

## 시험 고정값

`operators-page-parallel.test.tsx:91` `['wms','*']` — `true` 에 기대는 시험은 `:113-128` 하나. 양식을 prop 으로 직접 그리는 시험(`OperatorsScreen.test.tsx:150,160,169,201,391,448`)은 무관. `OperatorInvitations.test.tsx:176` `activeTenant: '*'` 도 불가능한 상태 — 함께 정정.

## 위험

- «모든 테넌트» 보기의 행 동작 문구: 역할 편집 확인이 «이 회사(demo-corp)에서의 역할»(`operators-confirm-copy.tsx:162-168`)이라 하지만 서버는 `'*'` 대상에 `'*'` 로 강제 — 배정 · 상태 동작도 같은 점검.
- `hasPermission` 은 모르는 역할에 열린다(`permission-map.ts:144-153`) — UI 게이트일 뿐, 서버가 거절.
- `homeTenantId` 는 스키마상 선택 — 없으면 false.
- `iam-gateway` 는 다섯 클라이언트의 공유 핵심 — 덧붙이기만, 손대지 않은 호출의 헤더는 바이트 동일로 고정.
- 라이브(AC-4)는 소유자의 `owner-operator.sh activate` 필요(기본 SUSPENDED).

# 소유자 결정 (2026-10-20 UTC, 선택창)

- **목록**: `'*'` 범위 호출자에게만 `/operators` 에 **«모든 테넌트» 보기**를 더한다 — 기존 `?tenantId=*`(서버 무변경). 그 보기의 행 동작 문구(«이 회사») 점검 포함.
- **부여 · 회수**: 부여 대화상자에서 «모든 테넌트(기본 제공 세트만)» / «현재 테넌트» 를 **고른다** · 회수는 **출처 행마다** 그 행의 테넌트로.
- 양식(공백 2): `isPlatformOperator = me.homeTenantId === '*' && hasPermission(me.roles, 'operator.manage')`(`new-tenant` 선례).
- 순서: 계약(`:261` · `:267`) 먼저 → 구현. 🔴 새 문안(보기 전환 · 부여 선택지 · 바뀌는 확인 문구)은 머지 전 소유자 승인.
