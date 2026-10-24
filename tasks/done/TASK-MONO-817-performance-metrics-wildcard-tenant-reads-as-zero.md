# Task ID

TASK-MONO-817

# Title

A performance-metrics call made with a wildcard tenant (`tenant_id='*'`) answers all zeros — and the console shows «0», which reads as «nothing happened»

# Status

done

# Owner

backend / platform-console

# Task Tags

- code
- docs
- api

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (a cross-domain rule for the shared metrics contract, applied to each domain endpoint)
> Drafted 2026-10-24 UTC from `TASK-MONO-812` AC-4 (🔵). A shared-contract question across the ADR-MONO-084 series, not one domain's bug.

# Dependency Markers

- 출처: `TASK-MONO-812` AC-4 · `ADR-MONO-084` · `platform/contracts/performance-metrics-contract.md` § 4 («A failed strip is never shown as zero»).
- 관련: `TASK-MONO-807`~`812` (the six domain endpoints) · `TASK-PC-FE-368` (the home cards read the same endpoints).

# Goal

Every domain's metrics endpoint does one defined thing for a wildcard tenant, and the console never shows a wildcard's empty result as a real «0».

# Scope

## In Scope

- **AC-0 measurement, per domain endpoint** (ecommerce order · wms admin · erp approval · scm procurement · iam admin · finance account): what a `*` tenant gets today. The options are zeros, the sum over all tenants, 403, or 400.
  - Finance: zeros, the same as its other reads (TASK-MONO-812 contract note).
  - IAM: `tenantId=*` = all tenants for SUPER_ADMIN (TASK-MONO-811).
  - The rest: measure.
  - Also: which console operators can actually call with `*` (platform super-admin, console session tenant `*`)? If none can, the defect is latent, and the ticket says so instead of changing code.
- **Decision (owner selection window after AC-0):**
  - (a) `*` = sum over all tenants, where the domain can do that safely.
  - (b) `*` = 400 / 403, which the console strip shows as «권한 없음» or «불러올 수 없음».
  - (c) Per-domain, documented.

  The rule goes into `platform/contracts/performance-metrics-contract.md` (shared, project-agnostic wording).
- Implementation in one atomic PR if more than one domain changes.

## Out of Scope

- Per-tenant time zones (ADR-MONO-084 D3 placeholder).

# Acceptance Criteria

- [x] **AC-0** — the per-domain table above, file:line, plus who can reach it with `*`.
  - → **닫힘 2026-10-24 UTC** (origin/main `60d4d96bd`). 읽기 측정은 Explore 에이전트(opus)가 했고, 핵심 셋은 원문으로 직접 확인했다.

    | 도메인 | `*` 일 때 (수정 전) | 근거 |
    |---|---|---|
    | ecommerce order | **200, 전부 `"0"`** | `OrderOperatorAccess.java:31` 이 역할 없는 `*` 읽기를 허용한다. `OrderJpaRepository.java:209` `WHERE o.tenantId = :tenantId` 는 0행이다 |
    | finance account | **200, 전부 `"0"`** (계약상 의도) | 변환기가 `ROLE_FINANCE_SUPERADMIN_READ` 를 준다(`ActorContextJwtAuthenticationConverter.java:103`). `TransactionJpaRepository.java:45` 는 0행이다 |
    | iam admin | 전 테넌트 합계 (명시) | `QueryTenantScopeGate.java:128-139` 를 거쳐 하위 서비스의 전 테넌트 분기로 간다. `admin-api.md:557` 이 SUPER_ADMIN 만이라고 적고 있다 |
    | wms admin | 403 `TENANT_FORBIDDEN` | 입구 게이트에 와일드카드 허용이 없다(admin `OAuth2ResourceServerConfig.java:60,78-81`). 지표는 원래 창고 전역이다 |
    | erp approval | 403 `PERMISSION_DENIED` | 역할 없는 `*` 는 `canReadErp()` 가 false 다(`ActorContext.java:72-76`) |
    | scm procurement | 403 | URL 게이트가 `hasAnyRole(SCM_PROCUREMENT_READ, WRITE)` 다(`SecurityConfig.java:75-76`) |

  - **누가 `*` 로 닿나 — 콘솔에서는 아무도 닿지 못한다(잠재 결함).**
    - 홈 합성은 활성 테넌트가 없으면 400 `NO_ACTIVE_TENANT` 를 내고 아무 다리도 부르지 않는다(`operator-overview-composition.ts:70`).
    - 활성 테넌트가 있으면 assume 한 구체 테넌트 토큰을 쓴다. 그 토큰이 만료되면 null(401)이다(`session.ts:310-320`).
    - 도메인 화면은 `DomainTenantGate` 로 막혀 있다.
    - 콘솔 밖에서 `*` 토큰으로 직접 부르는 경우만 남는다.
    - ⚪ 테넌트를 고르지 않은 SUPER_ADMIN 의 기본 토큰이 지금도 `*` 인지는 확인하지 않았다. 다만 도메인 화면 게이트가 어느 쪽이든 막는다.
- [x] **AC-1** — owner decision recorded. The shared contract states the rule.
  - → **소유자 결정 2026-10-24 UTC (선택창): «조용한 0 금지».**
    - `*` 는 명시적 합계이거나 403 이다. 0 으로 답하지 않는다.
    - ecommerce · finance 지표만 403 으로 바꾼다. IAM 합계는 유지한다. wms · erp · scm 은 이미 403 이다.
    - 콘솔 변경은 없다.
  - 공용 계약 `performance-metrics-contract.md` § 4 «Wildcard tenant» 에 적었다(프로젝트 이름 없음).
    - 합계는 도메인 계약이 «누가 · 전 테넌트» 를 적은 곳에서만 허용한다. IAM `admin-api.md:557` 이 이미 그렇다.
    - 그 밖은 403 이다.
    - 시험 두 가지(`*` → 403 이고 쿼리 없음 · 구체 테넌트 대조군)를 의무로 정했다.
- [x] **AC-2** — each endpoint follows the rule, with a test per domain. Control group: a concrete tenant's values are unchanged.
  - → **ecommerce:** `OrderOperatorAccess.requireConcreteTenant` 를 `getMetrics` 에서 `requireRead` 다음에 부른다. 역할이 있어도 `*` 면 403 `ACCESS_DENIED` 다. 다른 운영자 읽기 4경로는 `*` 를 그대로 받는다.
    - 시험: `AdminOrderMetricsControllerSliceTest` 에 `*`(역할 없음 · 역할 있음) → 403 이고 서비스 호출 0, 구체 테넌트 → 200 이고 값은 그대로.
    - 페르소나 표(`AdminOrderBusinessRoleGateSliceTest`)는 `*` × metrics 를 403 으로 바꾸고, «`*` + 주문 읽기 역할» 페르소나를 더했다.
  - **finance:** `TransactionMetricsController` 가 `actor.tenantId()=='*'` 면 `PermissionDeniedException` 을 던진다 → 403 `PERMISSION_DENIED`. URL 게이트는 그대로 `*` 를 들인다(`AccountBusinessRoleGateSliceTest` 는 게이트만 재는 프로브라 불변이고, 주석을 달았다).
    - 시험: `TransactionMetricsControllerSliceTest.wildcardTenant403`. 대조군은 기존 `weekBareShape`(테넌트 `finance` → 200)다.
  - **iam · wms · erp · scm:** 코드 변경 없음. 이미 규칙대로다(AC-0 표).
  - **무는가:** main 코드만 되돌리면(`git stash -- src/main`) 새로 넣거나 바꾼 시험 **4건만** 빨강이다(order 3 · finance 1, rc=1). 나머지 30건은 초록이고, 복구 뒤 34/34 초록이다.
  - 계약: ecommerce `order-api.md` § metrics(행 · 오류 표) · finance `account-api.md` § metrics Tenancy.
- [x] **AC-3** — console: a wildcard response never renders as «0» in a strip or a home card.
  - → 이제 어떤 지표 엔드포인트도 `*` 에 0 을 돌려주지 않는다.
    - 403 은 `PerformanceStrip.tsx:121-127` 의 `status !== 'ok'` 분기에서 «권한 없음» 으로 그려진다.
    - IAM 합계는 실제 숫자다.
    - 게다가 콘솔은 `*` 로 부르지도 않는다(AC-0).
    - 그래서 콘솔 코드 변경은 없다(소유자 결정).
  - 기록만: ecommerce `/summary` · `/insights` 와 finance 의 다른 읽기는 `*` 에 여전히 빈 결과를 준다. 공용 실적 계약 밖이라 범위 밖이다.

# Related Specs

- `platform/contracts/performance-metrics-contract.md`
- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md`

# Related Contracts

- Each domain's `…/metrics` section (ecommerce `order-api.md` · wms `admin-service-api.md` · erp approval · scm `procurement-api.md` · iam `admin-api.md` · finance `account-api.md`).

# Edge Cases

- (a) for IAM already includes `consumer-pool` (TASK-MONO-811). A «sum» elsewhere may need the same note.

# Failure Scenarios

1. Only the shared contract text changes and an endpoint keeps returning zeros. AC-2 is per domain for this reason.
