# TASK-PC-FE-183 — 도메인 구독 self-enablement UI (조직 owner가 자기 테넌트 도메인을 켠다)

**Status:** done
**Area:** platform-console / console-web · **Scope:** `(console)/subscriptions` 화면 + 구독 프록시 + operator-gated 변이 클라이언트 + 사이드바
**Type:** entitlement-plane operator-gated CRUD UI 슬라이스 (보안-민감 아님 — 표준 operator-token 경로)
**Implemented:** branch `pc-fe-183-domain-subscription-ui` → **PR (open)**. `tsc --noEmit` + `next lint` + 신규 `vitest` 28건 green(client 9 / proxy 9 / derive 5 / screen 5); nav 테스트 회귀 0.
**Depends on:** TASK-BE-343 (`POST/PATCH /api/admin/subscriptions`, `subscription.manage` 게이트 — 이미 존재), TASK-PC-FE-182 / ADR-MONO-044 §3.4 (온보딩 owner가 `TENANT_BILLING_ADMIN` 보유), ADR-MONO-023 (IAM↔entitlement 평면 분리)
**Analysis model:** Opus 4.8 · **Impl model:** Opus (기존 API 위 표준 operator-gated UI 슬라이스)

## Goal

셀프서비스 온보딩(PC-FE-182)으로 만든 새 테넌트는 ADR-044 D6에 따라 **구독 0개**로 태어난다. owner는 `TENANT_BILLING_ADMIN`(`subscription.manage`)을 받지만, **콘솔에 도메인 구독을 켜는 UI가 없어** owner가 콘솔에 들어와도 어떤 도메인(ecommerce/wms/…)도 쓸 수 없는 **dead-end**에 빠진다. 백엔드 구독 API(BE-343)는 이미 있으므로(신규 BE 0), 이 task는 그 위에 **owner가 자기 테넌트 도메인을 self-enable하는 화면**을 얹어 온보딩을 실제로 의미있게 만든다 — AWS/GCP "owner가 서비스를 켠다" 패리티의 완성.

## 설계 판정 — GET/list 엔드포인트 부재 → 카탈로그로 상태 파생

BE-343엔 subscribe POST + status PATCH만 있고 **list/GET이 없다**. 따라서 현재 구독 상태는 기존 카탈로그(registry) 읽기로 파생한다: 도메인이 **ACTIVE** ⟺ 해당 `available` product의 `tenants ∋ activeTenant`. ADR-023에 따라 SUSPENDED/CANCELLED는 카탈로그에서 빠지므로 **카탈로그 ≈ ACTIVE-only** → 콘솔은 ACTIVE vs NOT-IN-CATALOG만 신뢰성 있게 구분한다. NOT-IN-CATALOG는 "미구독" 또는 "중지/해지"일 수 있고, 후자는 **subscribe 409 `SUBSCRIPTION_ALREADY_EXISTS` → 재개(PATCH ACTIVE)** 리커버리로 처리한다.

## Scope

**IN:**

- `src/shared/config/env.ts` — `SUBSCRIPTIONS_TIMEOUT_MS`(5000) 추가.
- `src/shared/api/errors.ts` — `SubscriptionsUnavailableError`(5xx/timeout) + MESSAGES(`SUBSCRIPTION_MANAGE_REQUIRED`/`SUBSCRIPTION_ALREADY_EXISTS`/`SUBSCRIPTION_TRANSITION_INVALID`/`SUBSCRIPTION_NOT_FOUND`/`TENANT_NOT_FOUND`).
- `src/features/subscriptions/api/domains.ts` — `SUBSCRIBABLE_DOMAINS`(wms/scm/finance/erp/ecommerce, **iam 제외** — 정체성 평면이라 구독 대상 아님) + `SubscribableDomainKey`.
- `src/features/subscriptions/api/types.ts` — `SubscriptionStatus`/`SubscriptionResultSchema`/`DerivedSubscriptionState`.
- `src/features/subscriptions/lib/derive.ts` — `deriveDomainSubscriptions(products, activeTenant)` → 도메인별 ACTIVE/NOT_SUBSCRIBED 행.
- `src/features/subscriptions/api/subscriptions-client.ts` (신규) — hardened core(operators-client 미러, feature-격리 복제): operator token + X-Tenant-Id + `X-Operator-Reason`(percent-encode) + taxonomy(401→ApiError·403/404/409/400→ApiError·503/timeout→SubscriptionsUnavailableError). 토큰 미로깅.
- `src/features/subscriptions/api/subscriptions-api.ts` — `createSubscription`(POST)/`changeSubscriptionStatus`(PATCH). **tenantId는 항상 서버-측 activeTenant**(클라 미제공 → 다른 테넌트 타겟 불가).
- `src/app/api/subscriptions/route.ts`(POST) + `[domainKey]/status/route.ts`(PATCH) + `_proxy.ts`(zod body/param 검증 — iam 거부, `mapError`).
- `src/app/(console)/subscriptions/page.tsx` (신규) — 서버 컴포넌트, 테넌트 게이트, 카탈로그 파생, 401→`/login`, degraded 배너.
- `src/features/subscriptions/components/{SubscriptionsScreen,SubscriptionConfirmDialog}.tsx` — 도메인별 구독/일시중지/해지 + reason-capture 다이얼로그 + 409→재개 전환.
- `src/features/subscriptions/index.ts` — 배럴.
- `src/shared/ui/ConsoleSidebarNav.tsx` — 신규 **'조직 설정'** 그룹(ADR-023 평면 분리 존중 — IAM 정체성 평면과 분리) + leaf `nav-subscriptions`→`/subscriptions`.
- 테스트: subscriptions-client(taxonomy+reason 인코딩+게이트) · subscriptions-proxy(POST/PATCH+검증+매핑) · derive(파생) · SubscriptionsScreen(렌더+subscribe/suspend+409 재개).

**OUT (의도적 비범위):**

- 신규 BE/contract 변경(전부 BE-343 소비만).
- **완전-충실 상태 표시(SUSPENDED/CANCELLED를 명시적으로 구분)** — list/GET subscriptions 엔드포인트가 필요(BE 후속). 현재는 카탈로그 파생(ACTIVE-only) + 409-재개로 커버. 이게 필요해지면 별 BE task.
- 도메인 auto-subscribe 번들(D6-B), 승인 큐, billing.

## Acceptance Criteria

- [ ] **AC-1** `/subscriptions`가 5개 subscribable 도메인(iam 제외)을 카탈로그 파생 상태로 렌더: ACTIVE=`구독 중`+[일시중지][해지], NOT_SUBSCRIBED=`미구독`+[구독].
- [ ] **AC-2** 구독/일시중지/해지 모두 reason-capture 다이얼로그 경유(빈 사유면 제출 비활성 — producer `X-Operator-Reason` 필수).
- [ ] **AC-3** subscribe → `POST /api/subscriptions {domainKey, reason}`; 성공 시 `router.refresh()`로 카탈로그 재파생. tenantId는 서버에서 activeTenant로 주입(클라 미제공).
- [ ] **AC-4** status 전이 → `PATCH /api/subscriptions/{domainKey}/status {status, reason}`; domainKey는 path, tenantId는 서버 activeTenant.
- [ ] **AC-5** subscribe 409 `SUBSCRIPTION_ALREADY_EXISTS` → 다이얼로그가 **재개**로 전환(PATCH ACTIVE) + actionable 메시지(중지/해지 이력).
- [ ] **AC-6** 클라이언트 core taxonomy: 401→ApiError(re-login)·403 PERMISSION_DENIED→ApiError·503/timeout→SubscriptionsUnavailableError·operator 토큰/사유 인코딩(비-Latin1 사유=`encodeURIComponent`). 토큰 미로깅.
- [ ] **AC-7** 프록시: iam/미지 domainKey → 422, 잘못된 status → 422, activeTenant 없음 → 400 NO_ACTIVE_TENANT, producer 에러 passthrough.
- [ ] **AC-8** 사이드바 '조직 설정' 그룹 + `nav-subscriptions`; 기존 nav 테스트 회귀 0.
- [ ] **AC-9** `pnpm lint` + `tsc --noEmit` + `vitest`(신규 스위트) green, 회귀 0.

## Related Specs

- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` — IAM↔entitlement 평면 분리(SUSPENDED/CANCELLED는 카탈로그·`entitled_domains`에서만 빠지고 RBAC 보존; 이 화면이 그 평면을 조작).
- `docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md` §3.4 — 이 task가 온보딩 owner의 dead-end(구독 0)를 해소.
- `console-web/architecture.md` § Forbidden Dependencies — 브라우저 직접 IAM 호출 금지(동일 출처 프록시 경유).

## Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § Subscription Management (BE-343 — 소비만): `POST /api/admin/subscriptions {tenantId, domainKey}`(201), `PATCH .../{tenantId}/{domainKey}/status {status}`(200), 401/403/400/404/409/503.
- `console-registry-api.md` / `console-integration-contract.md § 2.2` — 카탈로그 읽기(구독 상태 파생 소스).

## Edge Cases

- **GET 부재 → SUSPENDED 불가시**: 중지된 도메인이 "미구독"으로 보임 → [구독] 시 409 → 재개 경로로 복구. 완전-충실 표시는 BE GET 후속(OUT).
- **CANCELLED 재개 시도**: 재개 PATCH ACTIVE가 producer state-machine에서 `SUBSCRIPTION_TRANSITION_INVALID`(409) → inline 표시.
- **테넌트 미선택**: 게이트("테넌트를 먼저 선택하세요"), operators 화면 미러.
- **카탈로그 degraded(레지스트리 unreachable)**: 배너로 "상태 부정확 가능" 고지, 화면 유지(shell intact).
- **subscription.manage 없는 operator**(예: TENANT_ADMIN만) → producer 403 → inline "subscription.manage 필요".

## Failure Scenarios

- **구독 엔드포인트 5xx/timeout** → SubscriptionsUnavailableError → 다이얼로그 inline "일시적으로 처리 불가" + 재시도 가능(세션 유지).
- **사이드바 그룹 추가가 nav 회귀?** → dashboards/operators/audit-nav 테스트로 확증(그룹 추가는 기존 testid/href 불변 → 회귀 0).
- **클라가 다른 테넌트 타겟?** → 불가: tenantId는 서버에서 activeTenant로만 주입(POST body·PATCH path 모두), 클라 미제공.
- **비-ASCII 사유로 fetch throw?** → core가 `encodeURIComponent`로 헤더 ASCII화(TASK-MONO-176 패턴), producer 디코드.
