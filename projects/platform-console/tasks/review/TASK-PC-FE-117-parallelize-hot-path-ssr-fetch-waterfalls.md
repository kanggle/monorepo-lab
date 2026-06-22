# Task ID

TASK-PC-FE-117

# Title

console-web 핫패스 SSR fetch 워터폴 병렬화: `/console`(홈) + `/dashboards/overview`(개요) — 두 개의 독립적인 server fetch를 순차 await → 투기적 병렬(`Promise.all` 패턴)로 전환해 평상시 페이지 렌더 지연을 `a+b` → `max(a,b)`로 단축

# Status

review

# Owner

frontend (Opus 4.8 분석·구현 — SSR data-fetch 워터폴 제거, behavior-preserving 성능 최적화; contract/spec/backend 무변경)

# Task Tags

- code
- test
- performance

---

# Dependency Markers

- **builds on**: TASK-PC-FE-011(operator overview `getOperatorOverviewState`) + TASK-PC-FE-013/037(domain health `getDomainHealthState`, SSR cookie forward) + TASK-PC-FE-061(개요에 도메인 상태 요약 카드 합성) + TASK-PC-FE-063~065(`/console` 카탈로그 + health dot).
- **reverses (의도적)**: TASK-PC-FE-061 § Architecture는 "개요 성공 분기에서만 health fetch(게이트 통과 후 active tenant 보장; **낭비 호출 없음**)"을 명시했다. 본 task는 hot-path(성공 분기) 지연 단축을 위해 health fetch를 **게이트 판정 이전에 투기적으로 발사**한다 — 결과적으로 게이트가 걸리는 분기(noTenant/unauthorized/bffUnavailable)에서는 health 호출 1건이 낭비된다. 트레이드오프 판단: (a) 게이트 분기는 모두 degraded/rare 경로(특히 noTenant는 TASK-PC-FE-036의 active-tenant-default 이후 사실상 첫 진입 한정)이고, (b) hot-path는 매 로드마다 health 지연을 절감하므로 순이득. `getDomainHealthState()`는 내부에서 모든 에러를 catch하고 절대 throw하지 않으므로(`domain-health-api.ts:134`), 게이트 분기에서 await하지 않아도 unhandled rejection 위험이 없다.
- **note**: 두 페이지의 두 fetch는 서로 독립(catalog/operator-overview ↔ domain-health는 데이터 의존 없음). 동일 BFF지만 별도 proxy route(`/api/console/dashboards/operator-overview`, `/api/console/dashboards/domain-health`, `/api/console/catalog`)로 별도 라운드트립이라 순차 시 합산 지연.

# Goal

console-web에서 **로그인 직후 사용자가 매번 보는 두 랜딩/대시보드 페이지**의 SSR data-fetch 워터폴을 제거한다:

1. **`/console`(콘솔 홈)** — `getCatalog()` await 후 `getDomainHealthState()` 순차 → 두 promise를 진입 시 동시 발사, catalog를 먼저 await(게이트=401 redirect)한 뒤 이미 in-flight인 health를 await.
2. **`/dashboards/overview`(운영자 개요)** — `getOperatorOverviewState()` await + 게이트 후 `getDomainHealthState()` 순차 → health promise를 진입 시 투기적 발사, overview를 먼저 await해 게이트(redirect/noTenant/bffUnavailable) 판정, 성공 분기에서만 health를 await(나머지 분기는 promise 방치 — throw 없음이라 안전).

behavior-preserving: 모든 게이트 분기·렌더 출력·degrade 동작은 기존과 동일. 변경은 **fetch 시작 시점**(순차 → 동시)뿐이다.

# Scope

## In Scope

- **`src/app/(console)/console/page.tsx`** — `getCatalog()`와 `getDomainHealthState()`를 진입에서 동시 발사:
  ```
  const catalogPromise = getCatalog();
  const healthPromise = getDomainHealthState(); // never throws
  let catalog;
  try { catalog = await catalogPromise; }
  catch (err) { if (ApiError 401) redirect('/login'); throw err; }
  const healthState = await healthPromise;
  ```
  catalog가 throw(401 redirect / 기타 re-throw)하면 healthPromise는 방치되지만 `getDomainHealthState`는 throw하지 않으므로 unhandled rejection 없음. 이후 `healthByDomain` 매핑·렌더는 기존 그대로.
- **`src/app/(console)/dashboards/overview/page.tsx`** — `getDomainHealthState()`를 함수 진입부에서 투기적 발사(`const healthPromise = getDomainHealthState();`), `const state = await getOperatorOverviewState();`로 게이트 판정. unauthorized/noTenant/bffUnavailable 분기는 기존과 동일(healthPromise 미사용·미await — 방치). 성공 분기에서 `const healthState = await healthPromise;`(기존 `await getDomainHealthState()` 대체)로 렌더. 기존 TASK-PC-FE-061 주석(성공 분기에서만 fetch)을 본 task 트레이드오프로 갱신.
- **Tests** — 두 페이지의 기존 단위/통합 테스트가 무회귀로 통과해야 한다. 페이지가 server component(`async`)이고 `redirect`/`next/headers`를 쓰므로, 기존 테스트 형태를 따른다:
  - 기존 테스트가 존재하면: 동시-발사 후에도 (i) 성공 분기에서 두 fetch 결과가 모두 렌더되고, (ii) 게이트 분기(401 redirect / noTenant / bffUnavailable)가 기존과 동일하게 동작함을 단언하도록 보강/유지.
  - 핵심 회귀 가드: **게이트 분기에서 health 결과가 렌더 출력에 영향을 주지 않음**(개요 noTenant/bffUnavailable 게이트 출력 불변), **성공 분기 출력 불변**.

## Out of Scope

- `getDomainHealthState` / `getOperatorOverviewState` / `getCatalog` 내부 로직 변경(fetch 시작 시점만 호출측에서 변경; fetcher 불변).
- `ecommerce/page.tsx`의 health fetch(선행 entitlement 게이트 뒤 단일 fetch — 병렬화 대상 아님), `dashboards/health/page.tsx`·`dashboards/page.tsx`(단일 fetch).
- BFF 측 합성(operator-overview+health를 한 호출로 묶기) — 별건(console-bff 변경, 더 큰 범위).
- OTel/instrumentation, 번들 코드 스플릿, 타임아웃 값 튜닝 — 별건.
- 백엔드/계약/스펙 변경.

# Acceptance Criteria

- [ ] `/console` 성공 시: 카탈로그 + 도메인 health dot이 기존과 동일하게 렌더되며, 두 fetch가 순차가 아닌 동시에 시작된다(워터폴 제거). catalog 401 → `redirect('/login')` 동작 불변.
- [ ] `/dashboards/overview` 성공 시: `<OperatorOverviewScreen>` + `<DomainHealthSummaryCard>`가 기존과 동일하게 렌더되며, health fetch가 overview fetch와 동시에 시작된다.
- [ ] `/dashboards/overview` 게이트 분기(unauthorized→redirect, noTenant→"테넌트 선택" 게이트, bffUnavailable→"일시적으로 불러올 수 없음")의 렌더 출력이 기존과 byte-동일(health 투기 발사가 게이트 출력에 영향 없음).
- [ ] 게이트 분기에서 investigate된 health promise가 unhandled rejection을 일으키지 않는다(`getDomainHealthState` non-throw 보장에 의존).
- [ ] `pnpm exec vitest run` green(무회귀), `npx tsc --noEmit` clean, `pnpm lint` clean(no-unused-vars 등 CI 두 프런트 잡 가드 — `env_console_web_local_verify_needs_lint`). scope = console-web only.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` § Server vs Client Components / § Performance Budget — 소비만, 변경 없음(server-first render 유지).
- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.9.1/§2.4.9.2 — read-only 소비, 변경 없음.

# Related Contracts

- 변경 없음. 호출측 fetch 시작 시점만 조정(동일 read 계약·동일 횟수·hot-path 동일).

# Target Service

- `platform-console` / `apps/console-web` — `app/(console)/console/page.tsx` + `app/(console)/dashboards/overview/page.tsx` 2개 server-component route. behavior-preserving 성능 최적화.

# Architecture

- Next.js App Router SSR data-fetch 워터폴 제거 패턴. 독립적인 두 server fetch를 `await A; await B`(순차, 지연=`a+b`) → promise를 동시 발사하고 게이트에 필요한 쪽을 먼저 await(지연=`max(a,b)`). 게이트 분기에서 투기 발사된 fetch는 non-throw fetcher라 방치 가능. RSC streaming/Suspense 도입 없이 호출측 await 순서만 조정하는 최소-침습 변경(렌더 트리·게이트 분기 구조 불변).

# Edge Cases

- `/console`: catalog 성공 + health null(bff 미가용) → dot 없이 카탈로그만(기존 degrade 동일).
- `/console`: catalog 401 → redirect, health promise 방치(throw 없음, 미사용).
- `/overview`: noTenant(첫 진입) → "테넌트 선택" 게이트 렌더, health promise 방치(1건 낭비 호출 발생하나 출력 무영향).
- `/overview`: bffUnavailable → 배너 렌더, health promise 방치.
- `/overview`: 성공 + health degraded/null → 요약 카드 compact note(기존 degrade 동일).
- 두 fetch 중 느린 쪽이 hot-path 지연을 지배(`max`); 둘 다 빠르면 체감 변화 미미하나 회귀 아님.

# Failure Scenarios

- 투기 발사된 health promise가 게이트 분기에서 reject되어 페이지 크래시 → `getDomainHealthState`가 모든 에러를 내부 catch(절대 throw 안 함)임을 코드로 확인 + AC가 게이트 분기 무회귀 단언으로 가드.
- 병렬화가 게이트 판정을 바꿔버림(예: health 결과로 분기) → 게이트는 오직 overview/catalog 결과로만 판정(health는 게이트 입력 아님)임을 유지; AC가 게이트 출력 불변 단언.
- catalog throw 경로에서 health unhandled rejection으로 Node warning/로그 오염 → non-throw 보장으로 발생 불가; 회귀 시 테스트가 잡도록 게이트 분기 테스트 유지.
- lint(no-unused-vars)로 CI 프런트 잡 RED → push 전 `pnpm lint` 필수(가드 AC).

# Definition of Done

- [ ] 두 페이지 SSR fetch 워터폴 제거(동시 발사), 게이트 분기·렌더 출력 behavior-preserving
- [ ] vitest + tsc + lint green, 무회귀; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
