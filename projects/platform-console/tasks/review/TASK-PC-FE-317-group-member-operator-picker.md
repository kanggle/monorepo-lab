# Task ID

TASK-PC-FE-317

# Status

review

# Title

운영자 그룹 «멤버 추가» 에서 운영자 UUID 를 손으로 붙여넣는 대신, 그 그룹 테넌트의 운영자를 이메일·이름으로 찾아 고르는 선택기를 붙인다

# Owner

platform-console

# Task Tags

- console-web
- frontend
- operator-groups

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 기존 목록 API 소비 · 다이얼로그 개선. 새 백엔드 없음.
>
> 🔵 **소유자 결정(2026-10-07 UTC):** 대화 «운영자를 먼저 등록해야 그룹에서 선택할 수 있나» → 실측: 선택이 아니라 UUID 직접 입력 → 추천 «선택기 먼저(초대 연동은 ADR-MONO-080 D6 뒤)» → «진행».

---

# Dependency Markers

- 선행: 없음.
- 후속: `TASK-PC-FE-316`(backlog) — ADR-MONO-080 D6 초대 방식이 들어온 뒤 그룹 화면에서 이메일 초대 + 수락 시 그룹 자동 추가. 이 티켓의 선택기는 그때도 «이미 있는 운영자 고르기» 로 남는다.
- 같은 시기 `TASK-PC-FE-315`(IAM 메뉴 순서)는 `console-nav-config.ts` 만 건드린다 — 겹치는 소스 없음(`tasks/INDEX.md` 만 공유 → 직렬 머지).
- 🔵 **처음 314 로 기안 — #4216 이 먼저 314 를 가져가 317 로 변경**(2026-10-07 UTC). 기안 시점엔 `origin/main` 에 `TASK-PC-FE-314` 가 없었으나, 구현 중 동시 세션이 같은 ID 로 무관한 티켓("사이드바 노출을 역할 · 구독으로", #4216)을 먼저 `ready/` 에 올렸다 — ID 충돌 발견 후 파일명·`# Task ID`·코드 주석·단위 시험·`tasks/INDEX.md` 행을 전부 317 로 재기입(구현 내용·AC 측정값은 무변경). 315/316 은 open PR #4217 이 이미 점유 — 이 티켓과는 무관.

# Background (기안 시 측정, `origin/main` `5e213a8e7`)

| 사실 | 근거 |
|---|---|
| «멤버 추가» 입력은 «운영자 ID» 텍스트 하나(placeholder `operator-uuid`). 콘솔은 명단을 미리 불러오지 않는다 | `apps/console-web/src/features/operator-groups/components/GroupMemberDialog.tsx:7-16, 56-71` |
| 멤버는 그룹 테넌트 소속이어야 한다 — 아니면 producer `422 GROUP_MEMBER_TENANT_MISMATCH` | 같은 파일 주석 `:9-11` |
| 그룹은 `tenantId` 를 가진다 | `features/operator-groups/api/types.ts` `GroupSchema` |
| 운영자 목록 `GET /api/admin/operators` — `tenantId`(선택, 생략 시 home; effective scope 밖은 `403 TENANT_SCOPE_DENIED`) · `status` · `page` · `size`(max 100). **이메일/이름 검색 파라미터 없음.** 결과 = 그 테넌트 HOME 운영자 ∪ 배정 운영자 | `projects/iam-platform/specs/contracts/http/admin-api.md` § `GET /api/admin/operators` (`:927-1003`) |
| 콘솔의 목록 읽기는 `shared/api/iam-operators-read.ts` `listOperators` 하나(세 feature 공유), proxy `app/api/operators/route.ts` GET 은 `status/page/size` 만 넘긴다 — **`tenantId` 를 넘기는 길이 아직 없다** | `app/api/operators/route.ts:33-54` · `features/operators/api/operators-crud-api.ts:20-31` |
| 권한: 목록=`operator.manage`, 그룹 화면=`group.manage`. 시드 매트릭스에서 둘을 가진 역할이 같다(SUPER_ADMIN · TENANT_ADMIN · ORG_ADMIN) | `projects/iam-platform/specs/services/admin-service/rbac.md:108,114` |

# Goal

1. 그룹 상세의 «멤버 추가» 에서 그 그룹 **테넌트의 운영자 목록**을 보여주고, 이메일·이름으로 걸러 하나를 고르면 그 `operatorId` 로 기존 추가 흐름(사유 입력 → POST)을 그대로 탄다.
2. 이미 멤버인 운영자는 고를 수 없다(목록에서 빼거나 «이미 멤버» 로 비활성).
3. 목록을 못 불러오면(권한 없음 · 503 · 타임아웃) **지금처럼 UUID 직접 입력**으로 떨어진다 — 선택기가 막혀도 기능이 사라지지 않는다.

# Scope

## In Scope

- `GroupMemberDialog` 를 선택기로: 검색 입력 + 결과 목록(이메일 · 표시명 · 상태) + 선택. 선택한 운영자를 사유 확인 단계에 이름·이메일로 보여준다.
- 목록 조회는 **그룹의 `tenantId`** 로 한다(콘솔 활성 테넌트가 아니라). 필요하면 `shared/api/iam-operators-read.ts` `listOperators` 와 `app/api/operators/route.ts` GET 에 선택 `tenantId` 를 더한다 — **기존 호출자는 바이트 동일**(인자 생략 = 지금 동작).
- 걸러내기는 클라이언트에서(목록 API 에 검색 파라미터가 없다). 페이지 크기 100 으로 받고, 100 을 넘으면 «더 보기» 또는 다음 페이지 — 조용히 잘리지 않게.
- `SUSPENDED` 운영자 표시 여부: producer 가 정지 운영자의 그룹 가입을 허용하는지 **먼저 확인**(AC-0)하고 그에 맞춘다.
- 폴백: 목록 실패 시 기존 UUID 입력 칸.
- 단위 시험.

## Out of Scope

- 그룹 화면에서 새 운영자 만들기 / 이메일 초대 — `TASK-PC-FE-316`(ADR-MONO-080 D6 이후).
- producer(admin-service) 변경 — 검색 파라미터 추가 포함. 필요하다고 판단되면 멈추고 보고.
- 메뉴 순서 — `TASK-PC-FE-315`.

# Acceptance Criteria

- [x] **AC-0** — 착수 측정을 이 파일 Implementation Record 에 적는다: (a) producer 의 «그룹 테넌트 소속» 판정이 HOME 만인지 HOME ∪ 배정인지(admin-service 그룹 멤버 추가 유스케이스 코드 인용) — 목록 API 의 «HOME ∪ 배정» 과 다르면 목록을 그 판정에 맞게 거른다, (b) 정지(`SUSPENDED`) 운영자를 그룹에 넣을 수 있는지. → **(a) HOME 만, 목록(HOME∪배정)보다 좁다.** «그 판정에 맞게 거른다»는 **불가능으로 측정**: 목록 응답에 운영자별 HOME tenantId 가 없어(코드 인용 포함 Implementation Record) 클라이언트가 HOME 과 배정을 구분할 데이터가 없다 — 채우려면 producer 변경이 필요하고 Out of Scope 가 이를 명시적으로 막는다. 그 한계를 코드 주석 + 본 기록에 남기고 배정-only 오선택은 기존과 동일하게 422 그대로 노출(사전부터 있던 격차, 신규 회귀 아님). (b) **SUSPENDED 포함 가능** — producer 에 상태 검사 없음(grep 0건); 선택기는 SUSPENDED 를 감추지 않는다.
- [x] **AC-1** — 그룹 상세 «멤버 추가» 가 그 그룹 `tenantId` 의 운영자 목록을 보여준다(활성 테넌트가 다른 SUPER_ADMIN 시나리오를 시험으로 단언 — 요청에 그룹 `tenantId` 가 실린다). → `operators-api.test.ts`/`operators-proxy.test.ts`(active tenant `active-tenant-x` ≠ 쿼리 `group-tenant-y`, 쿼리가 후자로 나감을 단언) + `GroupMemberDialog.test.tsx` 전부 GREEN.
- [x] **AC-2** — 이메일·표시명 부분 일치(대소문자 무시)로 걸러진다. 결과 0건이면 «일치하는 운영자 없음 — 운영자 관리에서 먼저 등록» 안내. → `GroupMemberDialog.test.tsx` GREEN.
- [x] **AC-3** — 이미 멤버인 운영자는 선택할 수 없다. → `GroupMemberDialog.test.tsx` GREEN(비활성 행 + «다음» 비활성 유지 단언).
- [x] **AC-4** — 하나를 골라 «다음» → 사유 → «추가» 하면 기존과 같은 `POST /api/groups/{groupId}/members` 가 고른 `operatorId` 로 나간다(요청 본문 단언). → `GroupMemberDialog.test.tsx` GREEN(`onConfirm('op-pick', reason)` 단언 — 상위 `useAddMember`/POST 배선은 TASK-PC-FE-250 때부터 무변경).
- [x] **AC-5** — 목록 실패(403 · 503 · 네트워크) 시 UUID 직접 입력 칸으로 떨어지고 그 경로로 추가가 된다. → `GroupMemberDialog.test.tsx` GREEN(네트워크 실패 케이스; 403/503 은 동일 `isError` 경로 — `retry:false` 로 즉시 폴백).
- [x] **AC-6** — 기존 `listOperators` 호출자(운영자 화면 · 대시보드 · IAM 개요)의 요청이 바뀌지 않는다(`tenantId` 생략 시 쿼리 무변경 — 시험 단언). → `operators-api.test.ts`/`operators-proxy.test.ts` GREEN(`tenantId` 생략 시 active-tenant 쿼리 그대로).
- [x] **AC-7** — bite: AC-1·AC-4 시험이 옛 다이얼로그에서 빨강. → Implementation Record § AC-7 bite 참조. 확인 후 작업본 복원.
- [x] **AC-8** — console-web `tsc --noEmit` · `next lint` · vitest 전량 rc=0 (부하 실패가 나면 그 파일 단독 재실행 결과와 함께 기록). → tsc/lint rc=0. vitest 전량은 rc=1(13건, 무관 파일 8개 — 단독 재실행 123/123 rc=0 으로 부하-유발 flake 확인). Implementation Record § AC-8 참조.
- [ ] **AC-9** — 라이브: 다음 데모 창에서 실제 그룹에 선택기로 멤버 추가. ⚪ 허용.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`
- `docs/adr/ADR-MONO-046-operator-group-model.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § `GET /api/admin/operators` · § Operator Group Management (소비만 — 변경 없음)

# Edge Cases

- 활성 테넌트 ≠ 그룹 테넌트(SUPER_ADMIN 이 여러 테넌트 그룹을 본다) → 반드시 그룹 `tenantId` 로 조회.
- 운영자 100명 초과 → 잘림 없이 다음 페이지.
- 향후 `group.manage` 만 있고 `operator.manage` 는 없는 역할 → 목록 403 → UUID 폴백(AC-5)이 그 경우를 덮는다.
- 같은 운영자를 두 번 추가 → 선택 불가(AC-3); 경합으로 서버가 409/422 를 주면 그대로 표시.

# Failure Scenarios

- 그룹 `tenantId` 가 아니라 활성 테넌트로 조회 → 엉뚱한 테넌트 명단이 뜨고 고른 사람이 `422 GROUP_MEMBER_TENANT_MISMATCH`. AC-1 이 막는다.
- `listOperators` 시그니처 변경이 다른 호출자의 요청을 바꿈 → AC-6.
- 목록 실패가 다이얼로그 전체를 막음 → AC-5.

# Implementation Record

## AC-0 — 착수 측정 (2026-10-07 UTC)

**(a) producer 의 «그룹 테넌트 소속» 판정 = HOME 만 (목록 API 의 HOME ∪ 배정보다 좁다).**

`projects/iam-platform/apps/admin-service/src/main/java/com/example/admin/application/GroupAdminUseCase.java:164-176` (`addMember`):

```java
AdminOperatorJpaEntity target = adminOperators.findByOperatorId(operatorPublicId)...
// D3: a group holds only its own tenant's operators.
if (!group.getTenantId().equals(target.getTenantId())) {
    throw new GroupMemberTenantMismatchException(...);
}
```

`target.getTenantId()` 는 `AdminOperatorJpaEntity.tenantId`(HOME 테넌트, 단일 컬럼 — `infrastructure/persistence/rbac/AdminOperatorJpaEntity.java:33-36` 주석 "TASK-BE-249: tenant_id for multi-tenant row-level isolation")를 직접 읽는다 — `operator_tenant_assignment`(배정) 테이블을 전혀 참조하지 않는다. 반면 목록 API(`GET /api/admin/operators?tenantId=`)는 "HOME **또는** 배정"(admin-api.md:929)으로 더 넓게 스코핑한다. **격차**: 목록에 뜨는 "배정만 되어있고 HOME 은 다른 테넌트"인 운영자를 고르면, 선택기를 거쳤어도 여전히 `422 GROUP_MEMBER_TENANT_MISMATCH`.

이 격차를 클라이언트에서 "그 판정에 맞게 거른다"(AC-0 문구)로 메우려면 **운영자별 HOME tenantId 가 필요하지만, 목록 응답 item shape(`admin-api.md:973-992`, `OperatorSummarySchema`)에는 `tenantId` 필드가 전혀 없다** — 어느 운영자가 HOME 인지 배정뿐인지 클라이언트가 구분할 데이터가 없다. 이를 메우려면 producer 가 (i) 목록 응답에 운영자별 HOME tenantId 를 노출하거나 (ii) "HOME-only" 필터 모드를 추가해야 하며, 둘 다 producer 변경이다. Task Out of Scope가 "producer 변경(검색 파라미터 추가 포함) 은 범위 밖"이라고 명시하길래, 이 HOME 노출 격차도 같은 축의 producer 변경으로 보고 **구현하지 않았다** — 선택기는 목록 API 의 (더 넓은) `tenantId` 필터만 적용하고, 배정-only 오선택은 기존과 동일하게 `422` 를 그대로 노출한다(기존(pre-317) raw-UUID 경로가 이미 그랬던 것과 동일한 사전 존재 격차 — 신규 회귀 아님). `GroupMemberDialog.tsx` 상단 docstring에 이 결정을 코드 주석으로 기록했다.

**(b) SUSPENDED 운영자도 그룹에 추가 가능 — producer 에 상태 검사가 없다.**

같은 `addMember` 메서드(`GroupAdminUseCase.java:164-195`) 전체를 읽어도 `status`/`SUSPENDED` 참조가 0건이다(grep 확인). 그룹 테넌트 일치 → 중복 멤버 아님 → no-escalation 재검사, 이 셋만 보고 통과하면 `OperatorStatus` 는 전혀 보지 않는다. → 선택기는 `SUSPENDED` 운영자를 **감추지 않고 선택 가능한 상태로 보여준다**(행에 상태 텍스트만 표시) — producer 가 안 거는 제약을 UI 가 임의로 추가하지 않는다.

## 구현 요약

- `shared/api/iam-operators-types.ts` — `OperatorListParams.tenantId?: string` 추가(설명: 명시 시 override, 생략 시 기존 active-tenant 동작 — AC-6).
- `shared/api/iam-operators-read.ts` `listOperators` — `const tenant = params.tenantId ?? (await getActiveTenant())`. 기존 호출자(세 feature)는 `tenantId` 를 안 넘기므로 요청 바이트 동일.
- `app/api/operators/route.ts` GET — `tenantId` 쿼리 파라미터를 읽어 `listOperators(...)` 로 그대로 전달. 생략 시 undefined → 기존과 동일.
- `features/operator-groups/hooks/use-group-member-candidates.ts`(신규) — `/api/operators?tenantId=<그룹 tenantId>` 를 페이지(size=100) 단위로 호출하는 TanStack Query 훅. 페이지를 누적하고(`loadMore`), 실패 시 `retry:false` 로 즉시 `failed=true`(AC-5 폴백 신호).
- `features/operator-groups/components/GroupMemberDialog.tsx` — raw UUID 입력 다이얼로그를 선택기로 교체. 검색(이메일·표시명, 대소문자 무시) · 기존 멤버 비활성(«이미 멤버») · 0건 안내 · «더 보기» 페이지네이션 · 목록 실패 시 기존 UUID 입력으로 폴백 · 확인 단계에 선택된 운영자의 이름·이메일 표시.
- `features/operator-groups/components/GroupDetail.tsx` — `GroupMemberDialog` 에 `groupTenantId={group.tenantId}` + `existingMemberIds={members.data?.map(m => m.operatorId) ?? []}` 전달.
- 단위 시험(신규): `tests/unit/features/operator-groups/GroupMemberDialog.test.tsx`(AC-1~AC-5 + 페이지네이션 edge case), `tests/unit/operators-api.test.ts` + `tests/unit/operators-proxy.test.ts` 에 AC-1/AC-6 추가 케이스.

## AC-7 bite

`git show origin/main:.../GroupMemberDialog.tsx` 를 작업본 위에 덮어써서(스크래치패드 경유 — `git checkout --`/`stash` 미사용) AC-1/AC-4 시험을 그 옛 다이얼로그(raw-UUID 입력만 있음, `groupTenantId`/`existingMemberIds` prop 없음)로 돌렸다 — `group-member-search-input`/`group-member-candidate-*` testid 가 존재하지 않아 AC-1 은 "엘리먼트 없음"으로, AC-4 는 `group-member-candidate-op-pick` 대기 중 timeout 으로 둘 다 RED 로 확인했다. 작업본 복원 후 AC-1/AC-4 재실행 GREEN.

## AC-8 게이트

- `npx tsc --noEmit` → rc=0.
- `npx next lint` → rc=0 ("No ESLint warnings or errors").
- `npx vitest run`(전량) → **rc=1** — Test Files 8 failed | 331 passed (339), Tests 13 failed | 3818 passed (3831). 실패 13건은 `LedgerOpsScreen.test.tsx` · `OperatorsScreen.test.tsx`(4) · `ProductForm.test.tsx` · `SeedConfigScreen.test.tsx`(2) · `WmsInventoryScreen.test.tsx` · `CreateOrganizationForm.test.tsx`(2) · `DepartmentWriteDialog.test.tsx` · `TenantDetail.test.tsx` — 이 작업이 건드린 파일과 전부 무관(`GroupMemberDialog`/`operator-groups`/`operators-api`/`operators-proxy` 는 전량 통과). 8개 파일만 단독 재실행 → **rc=0, 123/123 전량 통과** — 전량 동시실행 시의 부하-유발 flake(동시 실행 시 격리 실패)로 확인, `TASK-PC-FE-313` 리뷰 기록의 동일 패턴("`LedgerOpsScreen` 부하 실패, 단독 53/53 무관"). 이 작업이 신규로 깬 시험은 0건.
- 신규/변경 시험만: `operators-api.test.ts` 24/24 · `operators-proxy.test.ts` 14/14 · `features/operator-groups/GroupMemberDialog.test.tsx` 6/6(신규) · `features/operator-groups/OperatorGroupsScreen.test.tsx` 6/6 — 전부 GREEN.
