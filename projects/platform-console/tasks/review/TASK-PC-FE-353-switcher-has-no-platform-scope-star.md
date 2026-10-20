# Task ID

TASK-PC-FE-353

# Title

다섯 화면이 «테넌트 스위처에서 플랫폼 스코프(*)를 선택하라» 고 안내하고 한 화면은 활성 테넌트 `*` 를 전제하지만 스위처에 `*` 가 없다 — 문구를 고칠지 `'*'` 운영자에게 `*` 를 줄지 (결정 먼저)

# Status

review (2026-10-20 UTC AC-0~2 닫힘 · AC-3 = Vercel 배포 · 다음 데모 창 라이브)

# Owner

frontend

# Task Tags

- code
- test
- copy

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet(문구만일 때) · Opus(`*` 를 스위처에 넣을 때 — 세션 · assume · 화면별 의미가 바뀐다)
> 기안 2026-10-20 UTC — 30차 데모 창(`TASK-MONO-672` § Order «Still open» 3 · 4)의 발견. **결정 티켓이 먼저다** — AC-0 에서 재고 소유자가 고르기 전에는 구현하지 않는다.

# Dependency Markers

- 출처: `TASK-MONO-672` «Still open» 4 의 🔴 Finding · 3 의 🔵(30차, 소유자 브라우저 `star-owner@demo.com`).
- 선행 없음.

# Goal

소유자 전용 `'*'` SUPER_ADMIN(`TASK-MONO-798`)으로 콘솔에 로그인하면, 테넌트를 고르지 않은 상태에서 `/tenants` 가 «테넌트 관리는 SUPER_ADMIN 전용이며 활성 테넌트가 선택되어 있어야 합니다. 상단의 테넌트 스위처에서 플랫폼 스코프(*)를 선택한 뒤 다시 시도하세요.» 라고 말한다. 그런데 **스위처에 `*` 가 없다**(소유자 실측 «* 없어»). 스위처 목록은 `features/tenant/lib/tenant-options.ts` `selectableTenants()` = 레지스트리 제품들의 `tenants` 합집합이고, 그 안에 `*` 는 들어가지 않는다. 소유자는 `demo-corp` 를 골라서 `/tenants` 목록을 봤다 — 화면의 실제 게이트는 «아무 활성 테넌트» 다(`tenants/page.tsx` `state.noTenant`).

같은 «플랫폼 스코프(*)» 전제는 모두 여섯 곳에 있다(안내 다섯 · 전제 하나):

| 파일 | 문구 |
|---|---|
| `app/(console)/tenants/page.tsx:43` | «… 스위처에서 플랫폼 스코프(*)를 선택한 뒤 …» |
| `app/(console)/tenants/[tenantId]/page.tsx:43` | 같음 |
| `app/(console)/org-hierarchy/page.tsx:35` | 같음 |
| `app/(console)/operator-groups/page.tsx:48` | «… 테넌트(SUPER_ADMIN 은 플랫폼 스코프 *)를 …» |
| `app/(console)/domain-permission-sets/page.tsx:66` | 같음 |
| `app/(console)/security-settings/page.tsx:53` | «플랫폼 스코프(*)에는 진입 정책이 없습니다 …» — 활성 테넌트가 `*` 일 수 있다는 전제 |

안내를 따를 수 있게 만든다 — 문구를 사실에 맞추거나, `'*'` 운영자가 `*` 를 고를 수 있게 하거나.

# Scope

## In Scope

- **AC-0 측정 (코드 · 계약 · 이력)**:
  - ① `*` 가 스위처에 들어간 적이 있는가 — `git log -S` 로 `selectableTenants` · 레지스트리 계약(`console-registry-api.md` § Tenant selection rule)의 이력. 예전엔 들어갔다면 언제 · 왜 빠졌나.
  - ② 화면별로 «활성 테넌트 = 실제 테넌트(예 `demo-corp`)» 와 «활성 테넌트 = `*`» 가 **무엇을 다르게 보여 주는가**. 특히 운영자 그룹 · 업무 권한 세트는 활성 테넌트가 **목록의 범위**를 정한다 — `demo-corp` 를 고르면 `demo-corp` 그룹만 보이고, 플랫폼(`*`) 그룹은 볼 길이 없을 수 있다(그렇다면 문구만 고치는 것은 기능 공백을 감춘다).
  - ③ `audience='*'` assume 은 이미 된다(30차: 200, `tenant_id='*'`, 역할 0). 그 토큰을 콘솔 세션에 넣으면 도메인 화면 · 개요 · 알림이 어떻게 되는가(코드 판독 — `getDomainFacingToken` · 개요 카드 · 샘플 게이트).
- 결과로 소유자에게 묻는다(추천과 함께):
  - (가) 문구만 — 화면마다 실제 게이트에 맞는 문장(소유자 문안 승인).
  - (나) `'*'` 운영자(그 grant 를 가진 운영자)에게 스위처에 «플랫폼(*)» 항목을 넣는다 + 화면별 동작 정리.
  - (다) 둘 다 — 지금 (가), (나) 는 별도 기안.

## Out of Scope

- 레지스트리 계약의 테넌트 범위 규칙 자체(IAM 소유) — (나) 가 그것을 바꿔야 하면 IAM 계약 먼저, 별도 티켓.
- `TASK-MONO-672` «Still open» 3 의 API 판정(닫혔다).

# Acceptance Criteria

- [x] **AC-0** — 위 ①②③ 을 file:line 으로 적는다. 소유자 결정(선택지 · 추천). → § AC-0 결과 · § 소유자 결정.
- [x] **AC-1** — 결정한 수선. 문구가 바뀌면 사용자 문안을 먼저 보여 주고 승인받는다. → § 구현 (문안 승인 2026-10-20 UTC 선택창 «이대로 승인»).
- [x] **AC-2** — 시험: 고친 화면마다 «테넌트 없음» 안내가 따를 수 있는 동작을 말한다(문구 시험 또는 스위처 항목 시험) · 기존 비회귀(일반 운영자 스위처 목록 불변). → § 구현.
- [ ] **AC-3** — 배포 상태(Vercel) · 라이브(`star-owner@demo.com` 으로 그 안내를 따라 화면에 들어간다 — 다음 데모 창).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.7 (assume-tenant · 활성 테넌트)
- `projects/iam-platform/specs/contracts/http/console-registry-api.md` § Tenant selection rule (있으면 — AC-0 에서 확인)

# Edge Cases

- 일반 운영자(단일 테넌트)는 스위처가 숨거나 라벨 하나다 — (나) 가 그들에게 `*` 를 보이면 안 된다.
- `*` 를 고른 세션에서 도메인 화면을 열면 도메인 서버가 `tenant_id='*'` 를 어떻게 받는지(ecommerce 는 GET 만 와일드카드 허용 — `TASK-MONO-795`).

# Failure Scenarios

1. 문구만 «아무 테넌트나 고르라» 로 바꾸고 닫는다 — 그룹 · 세트 화면에서 플랫폼(`*`) 범위를 볼 길이 없다는 기능 공백이 그대로 묻힌다(AC-0 ② 가 막는다).
2. 스위처에 `*` 를 모든 운영자에게 넣는다 — 서버가 거절하는 선택지를 보여 준다(`*` grant 없는 운영자).
3. 여섯 곳 중 일부만 고친다 — 같은 문장이 화면마다 다른 사실을 말하게 된다.

# AC-0 결과 (2026-10-20 UTC, origin/main `89dc24c70` · 코드 · 계약 · `git log -S` 판독, 라이브 측정 0)

## ① 이력 — `*` 는 스위처에 **한 번도** 없었다

- `selectableTenants()`(`apps/console-web/src/features/tenant/lib/tenant-options.ts:13-20`) 본문은 `debc68499`(2026-05-16, PC-FE-001) 이래 그대로다(그 뒤 `46ff64ef9` 이름 변경 · `9a5e4b120` 묶음 함수 추가뿐). 레지스트리 제품 `tenants` 의 합집합이고 `features/tenant` 에 `'*'` · `PLATFORM_SCOPE` 상수가 들어간 적 없다.
- 생산자도 `*` 를 안 낸다: `ConsoleRegistryUseCase.java:88,97-104` 는 `activeRegisteredTenantSlugs()` 로만 만들고, `*` 는 `tenants` 표의 행이 아니다(`V0009__create_tenants.sql:14-15`).
- 계약 `console-registry-api.md` § Tenant selection rule(`:274-313`) 은 `'*'` 범위 = «등록된 ACTIVE 테넌트 **전부**»(`:282` · § Authorization `:101-104`) — `*` 는 목록을 **넓히는** 범위이지 목록의 한 칸이 아니다. 7 커밋 모두 그대로.
- 잘못된 믿음이 코드 주석에도 있다: `features/tenants/api/tenants-client.ts:27-29`(«… offered by the tenant switcher») · `operator-groups-state.ts:17` · `org-nodes-state.ts:16` · `domain-permission-sets-client.ts:21` · `operator-groups/page.tsx:21`. 시험 고정값도 실제로 안 나오는 모양을 먹인다(`tests/unit/operators-page-parallel.test.tsx:91` `['wms','*']` · `OperatorGroupsScreen.test.tsx:162,230` `['*','acme-corp']`).
- 여섯 문구의 출생(`git log -S`): tenants 둘 `cb39eb45c`(2026-07-09, PC-FE-226) · org-hierarchy `9a5e4b120`(07-10, PC-FE-237) · operator-groups `62651ad70`(07-19, PC-FE-250) · security-settings `b7c389421`(10-09, MONO-771 S5) · domain-permission-sets `56a15be8b`(10-14, PC-FE-338a). **전부 스위처에 이미 `*` 가 없던 때** 쓰였다.

## ② 화면별 — 실제 테넌트(`demo-corp`) vs `*`

BFF 는 늘 `X-Tenant-Id` = 활성 테넌트 쿠키를 보내고, 없으면 `400 NO_ACTIVE_TENANT`(`shared/api/iam-gateway.ts:300-311`) — 여섯 화면이 «아무 테넌트» 를 요구하는 이유는 이것뿐이다. 요청 테넌트는 요청마다 결정된다(`admin-api.md:102-117` · `QueryTenantScopeGate.java:128-139` — 명시값 우선, `'*'` 범위는 어떤 값이든 통과).

| 화면 | 서버가 보는 것 | 실제 테넌트 vs `*` |
|---|---|---|
| tenants 목록 | 요청 테넌트 안 읽음(`admin-api.md:142`), 운영자 home 이 `*` 일 때만(`TenantAdminController.java:228-230`) | 같다 — 실제 게이트는 «아무 활성 테넌트»(`state.noTenant`) |
| tenants/[tenantId] | 경로의 테넌트 × `tenant.manage` 범위(`:143`) | 같다 |
| org-hierarchy | 헤더 무시, 운영자 범위(`OrgNodeAdminController.java:54-61`) | 같다 |
| operator-groups | `X-Tenant-Id` 무시, `'*'` 범위 = `findAll`(`GroupAdminUseCase.java:103-119`). 그룹은 플랫폼 단위가 될 수 없다(`:446-447` · `GroupForm.tsx:56` · `_proxy.ts:28`) | 같다 — `*` 소유자는 `demo-corp` 활성에서도 **모든** 그룹을 본다. 놓치는 «플랫폼 그룹» 은 없다 |
| domain-permission-sets | 프리셋 + T 의 세트, T=`*` 면 프리셋만(`DomainPermissionSetUseCase.java:76-91`), `*` 아래 생성 = 400(`:113-118`) | 실제 테넌트가 `*` 의 **상위집합**. 배지(`grantCount`/`groupCount`)만 테넌트별 — 사소 |
| security-settings | `tenant === '*'` 분기(`page.tsx:44-62`)는 실행될 수 없다 | 실제 테넌트 = 그 테넌트의 진입 정책 |

⇒ **(가) 문구만 고쳐도 여섯 화면에서 기능 공백이 묻히지 않는다**(Failure Scenario 1 의 우려는 측정으로 기각).

🔴 **옆 화면 `/operators` 에 진짜 공백 둘** — 여섯 곳 밖이라 `TASK-PC-FE-354` 로 기안:
1. `*` 운영자의 «모든 테넌트» 업무 권한 행을 콘솔에서 쓸 수 없다 — `OperatorDomainAccessUseCase.java:196-216` 이 요청 테넌트 T 아래에 쓰고, 행이 `'*'` 가 되는 것은 `X-Tenant-Id` 가 `*` 이거나 없을 때뿐인데 콘솔은 늘 활성 테넌트를 보낸다 ⇒ 부여는 `demo-corp` 행을 만들고, 기존 `'*'` 행의 회수는 `demo-corp` 아래에서 지우려다 0 건 → 404(`:174-179`). 읽기는 `'*'` 행을 «모든 테넌트» 로 보여 준다(`OperatorDomainAccessDialog.tsx:246-253`). 계약은 이것이 된다고 적는다(`console-integration-contract.md:267`).
2. «플랫폼 운영자 등록» 양식이 한 번도 안 그려졌다 — `operators/page.tsx:133` `isPlatformOperator = selectableTenants(...).includes('*')` 는 늘 false(`865c19168`, 2026-05-16 이래) ⇒ `OperatorsScreen.tsx:689-696` 양식 · `:419` «모든 회사에 적용» 정지 문구가 안 보인다.

## ③ 활성 테넌트 = `*` 이면 (코드 판독 · 일부 추론)

- 전환: `app/api/tenant/route.ts:104-139` 가 `selectableTenants` 로 검사(→ `*` 는 403) 후 assume, `console_active_tenant` · `console_assumed_token` 을 함께 둔다(`:198-207`). 로그인 기본값도 같은 집합(`active-tenant-default.ts:48-59,149-168`). 재-assume(`reassume/route.ts:51-58`) · 갱신(`session-refresh.ts:202-205`)은 쿠키 값을 그대로 다시 assume.
- `getDomainFacingToken`(`session.ts:310-321`) 은 `*` 토큰을 낸다. `DomainTenantGate` `tenantMismatch`(`DomainTenantGate.tsx:82-98,144`) — `*` 는 어떤 `product.tenants` 에도 없으므로 ecommerce(`ecommerce/layout.tsx:18`) · fan(`fan/layout.tsx:15`) 은 화면 대신 «다른 테넌트» 안내(795 의 와일드카드 GET 도 막힘). wms 는 `*` 호출자에게 0 건. scm · finance · erp 는 입구는 받지만 역할 0 이라 대부분 403(추론). 개요는 카드별 저하, 🔴 어느 출처든 401 을 내면 전체가 «세션 만료» 가 될 위험(추론 · 미측정). 알림은 역할 조회를 건너뛰고 모든 도메인에 물어 도메인별 403.
- 콘솔이 «이 운영자는 `*` 를 가졌다» 를 알 길: 레지스트리엔 표시 없음 · `GET /api/admin/me` `homeTenantId`(`iam-operators-types.ts:86`, `new-tenant/page.tsx:68` 이 이미 씀)는 활성 테넌트가 있어야 불린다 · 계약은 플랫폼 범위를 «grant 범위에 `*`» 로 정의(`admin-api.md:88`).
- (나) 의 최소 일: 출처(콘솔만 = `/me` 를 테넌트 없이 · 또는 IAM 계약 = 레지스트리 필드) · `/api/tenant` · 로그인 기본값에서 그 운영자에게만 `*` 허용 · 스위처 항목 · `DomainTenantGate` 의 `*` 처리 · 개요 401 위험 확인 · 시험. **지금은 쓸모가 거의 없다** — 여섯 화면은 실제 테넌트로 같거나 더 본다.

# 소유자 결정 (2026-10-20 UTC, 선택창)

- **(가) 문구만** + `/operators` 의 두 공백은 **별도 티켓 `TASK-PC-FE-354`**(스위처에 `*` 를 넣지 않는 좁은 수선). (나) 는 기안하지 않는다.
- AC-1 범위: 여섯 곳을 실제 게이트에 맞는 문장으로(security-settings 는 실행 불가 분기 삭제) · 잘못된 주석 다섯 곳 정정. `/operators` 의 시험 고정값(`['wms','*']`)은 354 가 고친다. 🔴 새 문안은 머지 전에 소유자 승인.

# 구현 (2026-10-20 UTC)

🔵 구현 중 일곱째 자리를 찾았다 — 가이드 `shared/guide/permission-map.ts:714` 의 security-settings «차이» 칸이 지운 `*` 분기를 설명했다. 문안 승인에 함께 넣었다(⑦). 잘못된 주석도 측정의 다섯이 아니라 **일곱**(`*-state.ts` 넷 · `tenants-client.ts` · `domain-permission-sets-client.ts` · 페이지 주석 둘).

| 자리 | 바뀐 것 (승인 문안) |
|---|---|
| ①② `tenants/page.tsx` · `tenants/[tenantId]/page.tsx` | «… 테넌트를 하나 선택한 뒤 다시 시도하세요. 어느 테넌트를 골라도 보이는 목록은 같습니다.» |
| ③ `org-hierarchy/page.tsx` | «… 어느 테넌트를 골라도 내 권한 범위의 조직 계층이 보입니다.» |
| ④ `operator-groups/page.tsx` | «… 어느 테넌트를 골라도 내 권한 범위의 그룹이 모두 보입니다.» |
| ⑤ `domain-permission-sets/page.tsx` | «… 테넌트를 선택한 뒤 다시 시도하세요. 기본 제공 세트와 고른 테넌트의 세트가 보입니다.» |
| ⑥ `security-settings/page.tsx` | `tenant === '*'` 분기 · `next/link` import 삭제, 머리 주석에 이유 |
| ⑦ `permission-map.ts:714` | «활성 테넌트의 정책을 보여 주고 바꾼다 — 데모 계정(SUPER_ADMIN)은 테넌트 상세에서도 같은 토글을 쓴다.» |

시험:
- 새 `tests/unit/no-platform-scope-in-switcher-copy.test.ts`(6) — 다섯 페이지가 승인 문장을 담고 «플랫폼 스코프(*)» 를 담지 않음 · security-settings 에 `*` 분기 없음 · 가이드 새 문장. 🔴 **bite**: `src` 변경만 stash 한 트리에서 rc=1(6/6 실패) → 되돌리면 통과.
- `security-settings-page.test.tsx` 의 `'*'` 시험 삭제(분기가 없다) · 쓰이지 않게 된 `next/link` mock 삭제.
- 비회귀: 스위처 코드(`tenant-options.ts` · `/api/tenant`)는 건드리지 않았다 ⇒ 일반 운영자 스위처 목록 불변.
- console-web `tsc --noEmit` rc=0 · `next lint` rc=0 · `vitest run` rc=0(**410 파일 · 4927 시험**). e2e 에 옛 문장 · `security-settings-platform-scope` 고정 0 건(grep).
