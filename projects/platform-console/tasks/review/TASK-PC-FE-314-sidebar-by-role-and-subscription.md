# Task ID

TASK-PC-FE-314

# Title

사이드바가 **권한·구독과 무관하게 거의 전부를 보인다** — 새 관리자는 눌러도 «권한 필요» 만 나오는 메뉴를 다수 본다. 플랫폼 전용 메뉴는 **숨기고**, 구독 안 한 도메인 메뉴는 **보이되 «구독 필요»** 로 표시한다

# Status

review

# Owner

platform-console

# Task Tags

- console-web
- frontend
- navigation

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 권한 정보 출처(`GET /api/admin/me` · 레지스트리 · `permission-map.ts`)가 이미 있어 화면 계산 위주.
>
> 🔵 **소유자 결정(2026-10-07 UTC):** 대화에서 «콘솔에 처음 들어온 계정이 볼 수 있는 메뉴» → 섞은 방식 추천 → «이 방향으로 platform-console 티켓을 기안».

---

# Dependency Markers

- 선행: 없음. `TASK-PC-FE-313`(사이드바 «조직 계층»·«테넌트» 를 «조직 설정» 으로, #4214 머지 · review)은 **자리만** 옮겼다 — 이 티켓은 그 결과 위에서 노출 규칙을 바꾼다.
- 관련: `ADR-MONO-080` D9 = T1 → `TASK-MONO-773`(운영자 아닌 계정의 셸 = «가이드 · 테넌트 생성» 만). 773 이 그 상태를 더할 때 이 티켓의 계산 함수에 한 갈래를 더한다(이 티켓이 먼저 머지되면 773 이 재사용).

# Background (착수 전 측정, `origin/main` `9031eb4a8`)

| 사실 | 근거 |
|---|---|
| 사이드바가 숨기는 규칙은 **하나뿐** — 레지스트리 게이트(`productKey`)가 걸린 부모(팬 디렉터리)만 | `apps/console-web/src/shared/ui/console-nav-config.ts:91-104` (`visibleGroups`) · `:438` |
| 메뉴마다 필요한 권한이 **이미 표로 있다** — `gate: { kind: 'admin', permission }` 11 · `admin-per-card` 2 · `domain` 32 · `operator` 2 · `public` 3 | `apps/console-web/src/shared/guide/permission-map.ts` (`gate:` 전수) |
| 역할 × 권한 표가 **이미 있다** (rbac.md § Seed Matrix 사본, 드리프트 테스트 있음) | `permission-map.ts:46-54`(`RBAC_ROLES`) · `:70`(`RBAC_SEED_MATRIX`) · `tests/unit/permission-map-drift.test.ts` |
| «내 역할» 출처 — `GET /api/admin/me` 가 `roles[]` 를 준다. 콘솔이 이미 부른다 | `projects/iam-platform/specs/contracts/http/admin-api.md:898-917` · `apps/console-web/src/features/operators/api/operators-self-api.ts:72-87` |
| «활성 테넌트가 구독한 도메인» 출처 — 레이아웃이 매 화면 레지스트리(`getCatalog`)를 받는다(제품별 `tenants[]`) | `apps/console-web/src/app/(console)/layout.tsx:183-188` |
| 예: 새 B2B 관리자(`TENANT_ADMIN` · `TENANT_BILLING_ADMIN`)에게 `/tenants`(`tenant.manage`) · `/org-hierarchy`(`org.manage`) 가 보이고, 눌러도 «SUPER_ADMIN 전용» 안내 | `permission-map.ts` `/tenants` · `/org-hierarchy` 행 · `(console)/tenants/page.tsx:42,63` |

# Goal

| 메뉴 종류 | 처리 |
|---|---|
| `admin` 게이트 — 내 역할 중 **어느 것도** 그 권한을 갖지 않음(시드 표 기준) | **숨김** (예: 고객사 관리자에게 «테넌트» · «조직 계층») |
| `admin-per-card` 게이트 — 카드 권한 중 하나라도 가짐 | 보임 · 하나도 없으면 숨김 |
| `domain` 게이트 — 활성 테넌트가 그 도메인을 **구독하지 않음** | **보이되 «구독 필요» 배지**. 부모(예: WMS)에 한 번만 표시하고, 하위 화면은 지금의 안내 그대로 |
| `public` · `operator` | 지금 그대로 |

원칙: **숨김은 화면 편의일 뿐이고 권한 판정은 서버가 한다.** 숨긴 메뉴의 주소를 직접 쳐도 지금의 «권한 필요» 화면이 그대로 나온다.

# Scope

## In Scope

- `shared/ui/console-nav-config.ts` — `visibleGroups` 를 «레지스트리 게이트» 하나에서 **노출 계산 함수 하나**로 일반화. 입력 = 내 역할 · 활성 테넌트의 구독 도메인 · 레지스트리 제품 키. 순수 함수(프레임워크 import 없음, 기존 규율).
- 권한 판정은 `permission-map.ts` 의 `gate` 와 `RBAC_SEED_MATRIX` 를 **조인**해 쓴다 — 메뉴별 권한 목록을 새로 손으로 만들지 않는다(사본 금지).
- `(console)/layout.tsx` — `GET /api/admin/me` 의 `roles` 를 받아 사이드바에 넘긴다(레지스트리 호출처럼 동시 시작, 실패해도 셸은 안 깨짐).
- 사이드바 «구독 필요» 배지 + 그 부모를 눌렀을 때 «도메인 구독» 으로 가는 안내(배지 옆 링크 또는 툴팁 — AC-0 에서 정함).
- 단위 시험 + bite.

## Out of Scope

- 서버 권한 판정 · 각 화면의 «권한 필요» 안내(그대로)
- 샘플 방문자(ADR-MONO-074) — **지금처럼 전부 보인다**(샘플 셸은 역할이 없다 — 숨기면 포트폴리오 둘러보기가 망가진다)
- 운영자 아닌 계정의 셸(`TASK-MONO-773`)

# Acceptance Criteria

- [x] **AC-0** — 착수 시 위 Background 재측정(`origin/main` 이 움직였으면 숫자 갱신). 결정해 적는다: ① 시드 표에 **없는 역할**(커스텀 · 권한 세트)을 가진 운영자 — 기본은 **보임**(모르면 숨기지 않는다, 서버가 막는다) ② `/api/admin/me` 실패 시 — 기본은 **지금처럼 전부 보임**(실패가 메뉴를 지우면 장애가 «권한 없음» 으로 읽힌다) ③ 활성 테넌트가 없을 때 도메인 배지 — 붙이지 않음(테넌트를 고르라는 안내가 이미 있다) ④ 배지 → «도메인 구독» 안내 방식. **→ Implementation Notes 참조.**
- [x] **AC-1** — `TENANT_ADMIN` + `TENANT_BILLING_ADMIN`(새 B2B 관리자): 렌더된 사이드바에 «테넌트» · «조직 계층» **없음**, «도메인 구독» · «운영자 관리» · «파트너십» **있음**. `tests/unit/sidebar-role-subscription.test.tsx` describe 블록 "AC-1".
- [x] **AC-2 (대조군)** — `SUPER_ADMIN`: 지금과 같은 메뉴 전부(회귀) — **단 파트너십은 예외, Implementation Notes § AC-0/AC-2 충돌 참조**. `ORG_ADMIN`: «조직 계층» 있음. `tests/unit/sidebar-role-subscription.test.tsx` describe 블록 "AC-2".
- [x] **AC-3** — 구독 0 인 활성 테넌트: 도메인 부모 메뉴가 **보이고** «구독 필요» 배지가 있다 · 구독한 도메인엔 배지 없음(대조군). `tests/unit/sidebar-role-subscription.test.tsx` + `console-nav-exposure.test.ts` describe 블록 "AC-3".
- [x] **AC-4** — 숨긴 메뉴의 주소 직접 접근 → 지금의 «권한 필요» 화면 그대로(서버 판정 유지 — 기존 시험 재확인). 서버 측 권한 판정·페이지 코드 무변경(이 티켓은 사이드바 노출 계산만 건드린다); 기존 전체 vitest 스위트 재실행으로 회귀 없음 확인(Implementation Notes § 검증).
- [x] **AC-5** — AC-0 ①② 의 «모르면 보임» 이 시험으로 고정된다(커스텀 역할 · `/me` 실패). `tests/unit/sidebar-role-subscription.test.tsx` describe "AC-5" + `console-nav-exposure.test.ts`.
- [x] **AC-6** — bite: 노출 계산에서 역할 조건을 지우면 AC-1 칸만 빨강. **측정 결과는 AC-1 보다 넓다 — Implementation Notes § AC-6 참조** (AC-1·AC-2·admin-per-card 단위 시험도 함께 빨개진다; AC-3/AC-5 는 그대로 초록 — 둘 다 "숨기지 않는다"는 기대라 역할 조건을 지워도 우연히 참이기 때문). 복원 후 `cmp` 로 byte-identical 확인 완료.
- [x] **AC-7** — `tsc` · `lint` · `vitest` 전체 rc=0. 🔴 콘솔 e2e 디렉터리(`tests/e2e`, `e2e-smoke`, 루트 `tests/federation-hardening-e2e`)에서 `nav-` testid 를 쓰는 스펙을 grep 해 **어떤 계정으로 어떤 메뉴를 찾는지** 대조한다 — 데모 시드 계정의 역할로 숨겨지는 메뉴를 찾는 스펙이 있으면 고친다. Implementation Notes § e2e 감사. ⚪ 머지 뒤 첫 `nightly-e2e.yml` 콘솔 잡 확인은 머지 후 사람이 재확인(이 구현 세션에서는 측정 불가).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`
- `projects/iam-platform/specs/services/admin-service/rbac.md` § Seed Matrix · § Permission Keys

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § `GET /api/admin/me` (소비만, 변경 없음)

# Edge Cases

- 역할은 테넌트 범위로 붙는다(예: `TENANT_ADMIN @ A`) — 활성 테넌트가 B 이면 B 에선 그 권한이 없다. `/me` 의 `roles` 가 범위를 싣지 않으면 «어느 테넌트에서든 가진 권한» 으로 보고 보인다(서버가 막는다). AC-0 에서 응답 모양을 확인해 적는다.
- `RBAC_SEED_MATRIX` 는 rbac.md 의 사본이라 낡을 수 있다 — 드리프트 테스트가 키만 본다(파일 머리 주석 § 한계). 낡으면 «숨기지 말아야 할 것을 숨기는» 쪽으로 틀릴 수 있으므로 ①의 «모르면 보임» 과 함께 본다.
- 부모 아래 하위 항목이 전부 숨겨지면 부모도 숨긴다.

# Failure Scenarios

1. `/me` 실패 때 메뉴를 다 숨긴다 — 장애가 «권한 없음» 으로 보인다.
2. 구독 안 한 도메인을 숨긴다 — 새 관리자가 무엇을 켤 수 있는지 알 길이 없다(결정과 반대).
3. 메뉴별 권한 목록을 새로 손으로 만든다 — `permission-map.ts` 와 두 사본이 갈라진다.
4. 사이드바 testid 를 찾는 e2e 가 데모 계정 역할 때문에 조용히 깨진다 — nightly 전용이라 PR 에서 안 보인다.

# Implementation Notes (2026-10-07 UTC)

## AC-0 재측정 + 결정

Background 표의 사실관계는 `origin/main` 기준 여전히 유효(worktree 분기점 `9031eb4a8` 이후 `console-nav-config.ts`/`permission-map.ts` 변경 없음 — 단, 별도 세션의 PR #4217 로 추정되는 `console-nav-config.ts` IAM 드릴 자식 순서 변경이 진행 중이라는 지시를 받아 그 파일은 `visibleGroups` 함수(기존 `:91-104`)만 건드렸다).

- **① 시드 표에 없는 역할 → 보임.** `console-nav-exposure.ts` `hasPermission()`: `RBAC_ROLES` 에 없는 role 문자열은 그 권한을 "가진 것"으로 간주(합집합 중 하나라도 모르면 보임 쪽으로 fail-open). 단위 시험 `console-nav-exposure.test.ts` "AC-5: 커스텀 역할".
- **② `/api/admin/me` 실패 → 전부 보임.** `shared/api/iam-operators-read.ts` `getSelfRolesOrNull()` 이 모든 실패 모드(401/403/503/timeout/network/schema-parse)를 `null` 로 수렴(기존 `getSelfOperatorIdOrNull` 과 동일한 자세). **추가 발견**: 이 함수가 타는 공유 게이트웨이 코어(`callAdminGateway`/`prepareAdminHeaders`, `iam-gateway.ts:300-306`)는 **활성 테넌트가 없으면 fetch 자체를 하지 않고 `400 NO_ACTIVE_TENANT` 를 던진다** — 즉 "테넌트 미선택" 도 `/me` 실패와 똑같이 "전부 보임" 버킷으로 자연히 떨어진다(별도 분기 불필요, 다만 이 사실을 AC-0 기록으로 남긴다).
- **③ 활성 테넌트 없음 → 배지 없음.** `(console)/layout.tsx` 에서 `activeTenant` 가 falsy 면 `subscribedDomains = undefined`; `console-nav-exposure.ts` `subscriptionBadgeKeys()` 는 `undefined` 를 받으면 즉시 빈 Set 반환. **범위를 넘는 결정 하나 추가**: 레지스트리 호출 자체가 실패(502/timeout/circuit-open, 기존 `catch` 분기)해도 `subscribedDomains` 는 선언 시 기본값인 `undefined` 로 남는다(= 배지 없음) — "모르면 배지 안 붙인다" 는 같은 원칙을 레지스트리 장애까지 확장한 것으로, 티켓 AC 에는 명시돼 있지 않은 연장 결정이다. 레지스트리가 없 어지면 `availableProductKeys` 도 이미 `[]`(기존 동작, fan 디렉터리 등 숨김) 라서, 이 연장 결정이 기존 fail-closed 자세와 반대 방향(배지는 fail-open)이라는 점도 기록해 둔다 — 근거: 도메인 메뉴 자체는 이 티켓의 Goal 표에 의해 **절대 숨기지 않으므로**, 장애 상황에서 "구독 안 했다" 고 잘못 경고하는 것보다 "지금처럼 아무 경고 없음" 이 더 안전한 디폴트라고 판단했다.
- **④ 배지 → 안내 방식: 툴팁(네이티브 `title`) 을 선택, 별도 링크는 만들지 않았다.** 이유: 배지가 붙는 지점(드릴 토글 `<button>`) 안에 `<a>` 를 중첩할 수 없다(HTML 무효 + 접근성 문제). 부모를 클릭하면 어차피 드릴인되어 그 도메인의 기존 하위 화면 안내(Out of Scope, 무변경)로 이어진다. 툴팁 문구: "활성 테넌트가 이 도메인을 구독하지 않았습니다 — 조직 설정 ▸ 도메인 구독에서 켜세요."

## `GET /api/admin/me` 응답 모양 + 테넌트 스코프 (Edge Case 란 확인)

`admin-api.md:898-917` — `roles` 는 **평평한 문자열 배열**이고 테넌트 스코프를 싣지 않는다(`["SUPER_ADMIN"]` 형태, role-per-tenant 쌍이 아니다). Edge Case 란이 예견한 대로: "role 은 테넌트 범위로 붙는다 — `/me` 의 `roles` 가 범위를 싣지 않으면 «어느 테넌트에서든 가진 권한» 으로 보고 보인다(서버가 막는다)" 는 자세를 그대로 채택했다 — `isHrefHiddenFor()` 는 역할 이름만으로 RBAC_SEED_MATRIX 를 조인하고, 테넌트별 실제 적용 여부는 그대로 서버(각 `/api/admin/**` 엔드포인트의 `@RequiresPermission`)가 최종 판정한다(Goal 원칙 그대로 — 숨김은 화면 편의일 뿐).

## AC-0 ↔ AC-2 충돌 — SUPER_ADMIN 의 `partnership.manage` 결여

Goal 표를 기계적으로 적용(= `permission-map.ts` 의 기존 `gate` + `RBAC_SEED_MATRIX` 조인, 새 권한 목록을 손으로 만들지 않는다는 Scope 지시)하면 **SUPER_ADMIN 도 `/partnerships` 를 잃는다** — `RBAC_SEED_MATRIX['partnership.manage']` 는 `(SA=0, …, TA=1, …)` 이고, 이는 `permission-map.ts` 자신의 `mismatch` 필드가 이미 기록한 **의도된** 설계다("파트너십은 두 고객 테넌트 사이의 관계이고 플랫폼은 당사자가 아니다" — `rbac.md:112`). 즉 오늘도 SUPER_ADMIN 이 `/partnerships` 를 열면 403 을 받는다(현재 사이드바는 그 사실을 안 가리고 있을 뿐). AC-2 문구("SUPER_ADMIN: 지금과 같은 메뉴 전부")를 **문자 그대로** 읽으면 이 변화와 충돌한다.

**결정**: Goal 표의 기계적 조인을 그대로 따르고(SUPER_ADMIN 도 `/partnerships` 를 잃는다), AC-2 는 "이 티켓이 새로 만드는 숨김 때문에 SUPER_ADMIN 이 오늘 쓰던 메뉴를 잃지 않는다" 는 취지로 해석했다 — `/partnerships` 숨김은 이 티켓이 고치려는 바로 그 종류의 결함(권한 없는데 보이는 메뉴)이 SUPER_ADMIN 에게도 적용된 것이지, 새로 생긴 리그레션이 아니다. SUPER_ADMIN 전용 예외를 하드코딩하는 대안은 Scope 의 "기존 출처를 조인, 새 메뉴별 목록을 손으로 안 만든다" 지시에 반한다고 판단해 채택하지 않았다. 단위 시험에 이 결정과 근거를 그대로 남겼다(`sidebar-role-subscription.test.tsx` "SUPER_ADMIN: 조직 설정 그룹…").

## `GET /api/admin/me` 역할 소스 — promotion 위치

`features/operators/api/operators-self-api.ts` 에 이미 거의 같은 호출(`getSelfOperatorIdOrNull`, self-row UX 게이트용)이 있었지만, 소비자가 `(console)/layout.tsx`(= `app/`, feature 아님)이고 `shared/` 는 `features/*` 를 import 할 수 없어(architecture.md § Allowed/Forbidden Dependencies, `tests/unit/layer-dependency-rules.test.ts` 로 기계 단언) `features/operators` 에 두는 선택지는 없었다. 다행히 `callGapOperators`/`OPERATORS_PREFIX`(TASK-PC-FE-259)와 `OperatorSummarySchema`(TASK-PC-FE-271)가 **이미** `shared/api/iam-operators-read.ts`/`iam-operators-types.ts` 로 승격돼 있어서, `rbac-catalog.ts` 가 세운 선례 그대로 새 `getSelfRolesOrNull()` 하나만 그 모듈에 추가했다 — 새 HTTP surface·새 zod 스키마 없음, 기존 self-row 게이트(`getSelfOperatorIdOrNull`)는 손대지 않았다(같은 엔드포인트를 두 곳에서 각자 호출하는 기존 패턴 그대로 — Next.js fetch 메모이제이션이 같은 렌더 패스 안에서는 중복 호출을 합친다).

## 검증

- `npx tsc --noEmit` → rc=0.
- `pnpm lint` (`next lint`) → rc=0, "No ESLint warnings or errors".
- `npx vitest run`(전체) → **3846/3849 통과**, 실패 3건은 `ProductForm.test.tsx` 2건 + `OrgScopeDialog.test.tsx` 1건(5초 타임아웃) — 이 티켓이 건드린 어떤 파일과도 무관(사이드바/권한/구독 코드 0% 중첩); 두 파일만 단독 재실행 시 **14/14 전부 통과** → 이 호스트의 알려진 부하-유발 flake(CLAUDE.md 환경 메모 — 동시 스위트 실행 시 5초 타임아웃), 이 작업의 결함이 아님.
- 신규 단위 시험: `tests/unit/console-nav-exposure.test.ts`(13건) + `tests/unit/sidebar-role-subscription.test.tsx`(15건) = 28/28 통과.
- AC-6 bite: `console-nav-exposure.ts` `isHrefHiddenFor()` 맨 앞에 `return false;` 를 추가(역할 조건 무효화) → 신규 2개 파일에서 8/28 실패(AC-1 전부, AC-2 전부, admin-per-card 단위 시험) · AC-3/AC-5 는 그대로 초록(둘 다 "숨기지 않는다"가 기대값이라 역할 조건을 지워도 우연히 참). 원복 후 `cmp` 로 수정 전 백업과 byte-identical 확인, 재실행 28/28 통과.

## e2e 감사 (AC-7)

`tests/e2e`(console-web) + `e2e-smoke`(console-web) + 루트 `tests/federation-hardening-e2e` 전체에서 `nav-` testid 사용처를 grep. 매칭된 것은 `nav-dashboards`(operator 게이트, 이 티켓이 절대 숨기지 않는 종류) · `nav-erp`/`nav-erp-{masters,overview,guide,orgview,approval,delegation}`(전부 `domain` 게이트 — Goal 표에 의해 role 로는 절대 숨지 않는다, 구독 배지만 영향받는데 이 스펙들은 배지를 검사하지 않는다) · `nav-operator-overview`/`nav-domain-health`(둘 다 "존재하지 않아야 한다"는 음의 단언, 이 티켓과 무관) 뿐이다. `nav-tenants`/`nav-org-hierarchy`/`nav-partnerships`/`nav-audit`/`nav-permissions`/`nav-permission-sets`/`nav-operators`/`nav-iam-operator-groups` — 이 티켓으로 역할에 따라 새로 숨을 수 있는 testid 들 — 은 세 디렉터리 어디에도 등장하지 않는다. `overview-consolidation.spec.ts` 가 쓰는 SUPER_ADMIN 시드 계정(`tests/e2e/fixtures/seed.sql`, `admin_operator_roles` 에 SUPER_ADMIN 단일 바인딩만 확인)은 위 AC-0↔AC-2 결정에 따라 `/partnerships` 메뉴를 잃지만, 그 스펙은 `nav-partnerships` 를 전혀 참조하지 않는다. **고칠 스펙 없음.** 머지 뒤 `nightly-e2e.yml` 콘솔 잡 1회 확인은 사람이 해야 하는 라이브 단계라 이 세션에서는 측정 불가(⚪) — INDEX 리뷰 행에 남겨둔다.
