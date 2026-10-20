# Task ID

TASK-PC-FE-354

# Title

`/operators` 의 `'*'` 두 공백 — `*` 운영자의 «모든 테넌트» 업무 권한 행을 콘솔에서 주거나 회수할 수 없다(회수 = 404) · «플랫폼 운영자 등록» 양식이 한 번도 그려지지 않았다

# Status

review (2026-10-20 UTC AC-0~3 닫힘 · AC-4 = Vercel 배포 · 다음 데모 창 라이브)

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
- [x] **AC-1** — `*` 대상의 부여 · 회수가 `'*'` 행에 닿는다. 일반 대상 · 일반 호출자는 그대로(활성 테넌트). → § 구현 (코드 · 단위 시험까지 — 라이브는 AC-4)
- [x] **AC-2** — `'*'` 범위 운영자에게 «플랫폼 운영자 등록» 양식이 보이고, 아닌 운영자에게는 안 보인다. → § 구현 (`/me` 판정 · 단위 시험 양방향)
- [x] **AC-3** — 시험: 헤더 선택(`*` 대상 + `'*'` 호출자 → `*` · 일반 대상 → 활성 테넌트 · `'*'` 없는 호출자 → 활성 테넌트) · 양식 표시 양방향 · 거짓 고정값 정정 후에도 초록. 문구가 바뀌면 소유자 문안 승인. → 시험 부분은 완료(§ 구현) · 🟢 **새 · 바뀐 문안 소유자 승인 2026-10-20 UTC**(선택창 «이대로 승인» — «모든 테넌트» 보기에서 회사 행의 역할 · 조직 스코프 · 업무 권한 · 내보내기를 숨기는 것 포함).
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

# 구현 (2026-10-20 UTC)

**계약 먼저** — `console-integration-contract.md`: § 2.4.3 row 2(플랫폼 등록 양식의 판정 = `/me` `homeTenantId='*'` + `operator.manage`) · § 2.4.3.4 `:261`(활성 테넌트 원칙의 예외 하나) · `:267` 을 두 bullet 로(목록 = `?tenantId=*` 의 «모든 테넌트» 보기와 그 보기에서 숨는 행 동작 / 대화상자가 `*` 를 보내는 두 경우와 그 밖의 전부).

**헤더(AC-1)** — `shared/api/iam-gateway.ts` `AdminGatewayRequest.tenantOverride?: '*'`(리터럴 — 임의 테넌트로 못 겨눈다) · `prepareAdminHeaders` 의 `X-Tenant-Id` 만 `req.tenantOverride ?? tenant`. 활성 테넌트 검사는 그대로 먼저(없으면 override 여도 400). `iam-operators-read.ts` `CallOptions.tenantOverride` 는 설정됐을 때만 펼친다 → 다른 호출자의 요청 객체 · 헤더 바이트 동일. `operators-domain-access-api.ts` grant/revoke 에 `{ allTenants }` 선택 인자 · BFF `_proxy.ts` `GrantDomainSetBodySchema` · `DomainSetReasonBodySchema` 에 `allTenants?: boolean`(후자는 이제 배정 스키마의 별칭이 아님 — 배정 경로는 그 키를 계속 422) · 두 route 가 `{ allTenants: body.allTenants === true }` 로 넘김. 클라이언트 훅은 `true` 일 때만 body 에 싣는다(그 밖의 body 는 `{ setId, reason }` / `{ reason }` 그대로).

**대화상자** — `OperatorDomainAccessDialog` `targetIsPlatform`(행 `homeTenantId==='*'`) ∧ `callerIsPlatform`(페이지 판정) 일 때만: 부여에 «적용 범위» 라디오(기본 «현재 테넌트» · «모든 테넌트 (기본 제공 세트만)» 은 `systemKey!==null` 만 남기고 고른 세트 초기화) · 회수는 세트별 단일 버튼 대신 개인 출처(DIRECT/MIGRATION)의 테넌트마다 하나(«현재 테넌트에서 회수» / «모든 테넌트에서 회수» — 서버가 `(대상, 테넌트, 세트)` 단위로 지우므로 출처 행이 아니라 출처 테넌트 단위). 확인 문구가 고른 범위를 말한다. 그 밖의 경우는 이전과 같다.

**목록(B)** — `OperatorsScreen`: `isPlatformOperator` 일 때만 «현재 테넌트 / 모든 테넌트» 전환 · 모든 테넌트 = `useOperatorsList({..., tenantId:'*'})` → BFF 기존 override(`listKey` 는 tenantId 가 있을 때만 끝에 붙음 — 기존 키 불변). 행 동작 점검: 모든 테넌트 보기의 **회사 행**은 역할 변경 · 조직 스코프 · 업무 권한 · 내보내기 숨김(그 목록의 `roles` 는 모든 회사의 행이라 역할 편집 초기값이 활성 테넌트 것이 아니게 된다 — 값싸게 참으로 만들 수 없음), 정지/활성화 · 프로파일 편집은 행 전체라 유지. **`'*'` 행**은 조직 스코프 · 내보내기 숨김(배정 행이 없다), 역할 변경은 확인문 · 편집기 legend · 전부 제거 경고를 «모든 테넌트(*)» 로(서버가 `'*'` 로 강제), 정지 확인은 «여러 회사에 소속» 문장을 뺐다(소속 0). 배정 양식(`AssignOperatorForm`)은 활성 테넌트로의 배정이라 문구가 참 — 무변경.

**양식(AC-2)** — `operators/page.tsx` `getSelfOrNull()` 하나로 self id + `isPlatformOperator = me.homeTenantId==='*' && hasPermission(me.roles,'operator.manage')`. `selectableTenants(...).includes('*')` 제거. 카탈로그는 `subscribedDomains` 용으로만 남아, 활성 테넌트가 없을 때 await 되지 않으므로 거절을 미리 처리해 둔다.

**시험(AC-3 시험 부분)** — `operators-page-parallel.test.tsx` 고정값 `['wms','*']` 제거 · `/me` 를 실제 모양으로 mock · 양식 판정 양방향 5건(`*`+SUPER_ADMIN → true / 회사 TENANT_ADMIN · `*` 역할 0 · homeTenantId 없음 · `/me` 실패 → false). 신규 `features/operators/operators-domain-access-tenant-header.test.ts`(grant/revoke `*` · 활성 · 활성 테넌트 없으면 400 무 fetch · 손대지 않은 호출자 5종 헤더 집합 고정). 신규 `OperatorsScreen-platform-scope.test.tsx`(전환 양방향 · `tenantId=*` 호출 · 행 동작 · 역할 확인 문구 대조 · 대화상자 행렬 6칸). `operators-domain-access-proxy.test.ts` 인자 단언 정정 + `allTenants` 전달 · 비불리언 422. `OperatorInvitations.test.tsx` 불가능 상태 `activeTenant:'*'` → 실제 상태.
- bite 3건(`git stash push -- <src>` → 실행 → `pop`): `page.tsx` 되돌림 → 13 중 9 실패 · `iam-gateway.ts` → 헤더 시험 11 중 정확히 `*` 2건 실패(고정 9건은 양쪽 초록 = 바이트 동일) · `OperatorDomainAccessDialog.tsx` → 화면 시험 14 중 플랫폼 모드 4건 실패.
- 게이트: `tsc --noEmit` rc=0 · `next lint` rc=0(경고 1 — 무관 파일 기존) · `vitest run` 전체 rc=0, 412 파일 / 4960 시험.
- e2e: `tests/e2e/operators-admin-profile.spec.ts` 가 `/operators` 를 쓰지만 `action-edit-profile-*` 만 — 이 변경이 숨기지 않는 버튼. 바꾼 testid · 문구를 가리키는 spec 없음.

**남은 것** — 🔴 새 · 바뀐 문안 소유자 승인(AC-3) · AC-4 라이브. 라이브 전 확인 거리: 모든 테넌트 보기에서 `'*'` 대상의 프로파일 편집 · 정지가 서버에서 통하는지는 코드로 재지 않았다(문구에 «이 회사» 가 없어 숨기지 않음).
