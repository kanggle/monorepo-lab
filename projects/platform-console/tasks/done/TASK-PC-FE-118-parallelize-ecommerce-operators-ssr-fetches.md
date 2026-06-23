# Task ID

TASK-PC-FE-118

# Title

console-web 핫패스 SSR fetch 워터폴 병렬화 2탄: `/ecommerce`(catalog → public domain-health) + `/operators`(성공 분기 catalog ∥ self) — PC-FE-117의 검증된 패턴을 안전한(authz-pre-flight 비침해) 두 페이지에 확장

# Status

done

# Owner

frontend (Opus 4.8 분석·구현 — PC-FE-117 동형 behavior-preserving SSR 워터폴 제거; contract/spec/backend 무변경)

# Task Tags

- code
- test
- performance

---

# Dependency Markers

- **builds on**: TASK-PC-FE-117(`/console`+`/dashboards/overview` SSR fetch 병렬화 — 본 task는 동일 패턴을 두 페이지에 확장). TASK-PC-FE-061(domain-health = tenant-무관 public actuator liveness — 투기적 fetch 안전성의 근거). TASK-PC-FE-020(`getSelfOperatorIdOrNull` fail-graceful). TASK-PC-FE-002a 등(operators/ecommerce 페이지).
- **scope-bounding 판정(중요)**: 콘솔의 2-fetch server page 10개 중 병렬화 안전한 것만 대상. **제외 6개**(`finance`/`ledger`/`scm`/`scm/replenishment`/`wms/outbound`/`wms`): 패턴이 `getCatalog()` → `getXSectionState(eligible, …)`로, 두 번째 fetch가 **`eligible`을 인자로 받는 authz pre-flight 의존**이다. 이 적격성 게이트는 인가받지 않은 운영자에게 per-tenant **권한 데이터**를 fetch하지 않으려는 의도적 규율 — 투기적 병렬화 시 entitlement 확인 전 권한 데이터를 요청해 authz 규율 회귀. domain-health(public liveness)와 달리 안전하지 않으므로 **의도적으로 제외**.
- **note(후속 후보 처분)**: (a) **BFF overview+health 단일 합성** = PC-FE-117이 이미 병렬성을 확보, 두 distinct 계약(§2.4.9.1 overview ↔ §2.4.9.2 health)을 결합하는 큰 백엔드 변경 대비 한계효용 작음 → **불채택**. (b) **OTel lazy-load** = `instrumentation.ts`가 이미 nodejs 런타임 게이트 동적 import + `otel-node.ts`가 `OTEL_EXPORTER_OTLP_ENDPOINT` 미설정 시 SDK 미기동(no-op) → 프로덕션 비용 0, **이미 최적(phantom 갭)** → 무작업.

# Goal

PC-FE-117에서 적용한 "독립적인 server fetch를 동시 발사 → 지연 `a+b` → `max(a,b)`" 패턴을, authz-pre-flight를 침해하지 않는 두 페이지로 확장한다:

1. **`/ecommerce`** — `getCatalog()`(적격성 게이트) → `getDomainHealthState()` 순차를, health promise를 진입부에서 투기적 발사 + catalog로 게이트 판정(401 redirect / degraded / not-eligible) + 적격 분기에서만 health await. domain-health는 tenant-무관 public actuator liveness라 console/overview와 동일하게 안전.
2. **`/operators`** — 성공 분기(게이트 통과 후)의 `getCatalog()`(테넌트 옵션) → `getSelfOperatorIdOrNull()`(self row 비활성화) **3연속의 뒤 2개를 상호 병렬화**. 두 fetch는 서로 독립이고 둘 다 `getOperatorsListState` 게이트(noTenant/permissionError/degraded) 통과 후라 authz pre-flight 침해 없음. catalog의 기존 try/catch(레지스트리 실패 → 빈 옵션, 비차단) 시맨틱 보존.

behavior-preserving: 모든 게이트 분기·렌더 출력 불변. 변경은 **fetch 시작 시점**(순차 → 동시)뿐.

# Scope

## In Scope

- **`src/app/(console)/ecommerce/page.tsx`** — 함수 진입부에서 `const healthPromise = getDomainHealthState();` 투기 발사. 기존 적격성 pre-flight(`getCatalog()` + degraded/eligible 판정)와 게이트 분기(registryDegraded / not-eligible / 401 redirect)는 불변. 적격 분기의 `const healthState = await getDomainHealthState();`를 `await healthPromise`로 대체. 기존 주석에 PC-FE-118 트레이드오프(게이트 분기 투기 호출 1건 낭비, non-throw라 안전) 반영.
- **`src/app/(console)/operators/page.tsx`** — 성공 분기에서 `const catalogPromise = getCatalog(); const selfPromise = getSelfOperatorIdOrNull();`로 둘을 동시 발사한 뒤, 기존 try/catch로 `await catalogPromise`(레지스트리 실패 → 빈 옵션 보존), 이어 `await selfPromise`. 두 fetch 시작이 순차 → 동시. 게이트(noTenant/permissionError/degraded)는 `getOperatorsListState` 결과로만 판정(불변).
- **Tests** (`tests/unit/ecommerce-page-parallel.test.tsx`, `tests/unit/operators-page-parallel.test.tsx`, new) — PC-FE-117 테스트와 동형:
  - ecommerce: 적격 경로에서 catalog+health **동시 발사**(async 본문이 첫 await resolve 전 두 fetcher 모두 호출), 게이트 분기(registryDegraded / not-eligible / 401 redirect) 렌더 출력 불변 + 투기 health promise 무영향 + non-throw 안전.
  - operators: 성공 분기에서 catalog+self **동시 발사**, 게이트 분기(noTenant/permissionError/degraded)에서 catalog/self 미호출(게이트가 먼저), catalog reject 시 self 결과 보존(빈 옵션 degrade).

## Out of Scope

- **6개 섹션 페이지**(finance/ledger/scm/scm-replenishment/wms-outbound/wms) — authz pre-flight 의존(`eligible` 인자), 병렬화 부적합(Dependency Markers 참조).
- BFF overview+health 단일 합성(불채택), OTel lazy-load(이미 최적) — 무작업.
- fetcher 내부 로직 변경(시작 시점만 호출측 조정), 백엔드/계약/스펙 변경.

# Acceptance Criteria

- [ ] `/ecommerce` 적격 시: ecommerce domain-health 카드가 기존과 동일하게 렌더되며, health fetch가 catalog fetch와 동시에 시작된다(워터폴 제거). 401 → `redirect('/login')`, registryDegraded, not-eligible 게이트 출력 불변.
- [ ] `/ecommerce` 게이트 분기에서 투기 발사된 health promise가 렌더 출력에 영향을 주지 않고 unhandled rejection을 일으키지 않는다(`getDomainHealthState` non-throw 보장).
- [ ] `/operators` 성공 시: tenant 옵션 + selfOperatorId 기반 렌더가 기존과 동일하며, catalog와 self fetch가 동시에 시작된다. catalog 레지스트리 실패 → 빈 옵션 degrade(self 결과는 보존), noTenant/permissionError/degraded 게이트 출력 불변.
- [ ] 6개 섹션 페이지 무변경(authz pre-flight 보존).
- [ ] `pnpm exec vitest run` green(무회귀), `npx tsc --noEmit` clean, `pnpm lint` clean. scope = console-web only.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` § Server vs Client Components / § Performance Budget — 소비만, 변경 없음.
- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.2(eligibility)/§2.4.9.2(domain health) — read-only 소비, 변경 없음.

# Related Contracts

- 변경 없음. 호출측 fetch 시작 시점만 조정(동일 read 계약·동일 횟수·hot-path 동일).

# Target Service

- `platform-console` / `apps/console-web` — `app/(console)/ecommerce/page.tsx` + `app/(console)/operators/page.tsx` 2개 server-component route. behavior-preserving 성능 최적화.

# Architecture

- PC-FE-117과 동일한 Next.js App Router SSR 워터폴 제거 패턴. ecommerce = 투기적 병렬(domain-health = public liveness, 게이트 분기 낭비 호출 허용). operators = post-gate 독립 fetch 상호 병렬(authz pre-flight 통과 후라 투기 아님 — 순수 안전). 6개 섹션 페이지는 authz pre-flight(`eligible` 의존)라 동일 패턴 적용 불가 — 의도적 제외로 authz 규율 보존.

# Edge Cases

- `/ecommerce` not-eligible/registryDegraded/401 → 게이트 렌더, health promise 방치(1건 낭비, 출력 무영향).
- `/ecommerce` 적격 + health null/degraded → ecommerce 카드 degrade(기존 동일).
- `/operators` catalog reject(레지스트리 다운) → 빈 tenant 옵션 + selfOperatorId 보존(self는 별도 promise, fail-graceful).
- `/operators` noTenant/permissionError/degraded → 게이트 렌더, catalog/self 미호출(게이트가 state await 직후라 도달 전).
- 두 fetch 중 느린 쪽이 hot-path 지연 지배(`max`); 둘 다 빠르면 체감 미미하나 회귀 아님.

# Failure Scenarios

- 투기 health promise가 게이트 분기에서 reject → 페이지 크래시: `getDomainHealthState` non-throw + AC 게이트 무회귀 단언으로 가드.
- operators에서 `Promise.all` 식으로 묶어 catalog reject가 self까지 실패시킴 → 본 task는 두 promise를 **개별 await**(catalog는 try/catch, self는 독립)로 구현해 회피; AC가 catalog-실패-시-self-보존 단언.
- 병렬화가 게이트 판정을 바꿈(health/catalog 결과로 분기) → 게이트는 오직 게이트-결정 fetch(ecommerce=catalog, operators=operatorsListState)로만 판정; AC 게이트 출력 불변 단언.
- 범위 오해로 6개 섹션 페이지까지 병렬화 → authz pre-flight 회귀: Out of Scope 명시 + AC 섹션 페이지 무변경.
- lint(no-unused-vars) → CI 프런트 잡 RED: push 전 `pnpm lint` 필수.

# Definition of Done

- [ ] ecommerce + operators SSR fetch 워터폴 제거(동시 발사), 게이트 분기·렌더 출력 behavior-preserving
- [ ] 6개 섹션 페이지 무변경(authz pre-flight 보존)
- [ ] vitest + tsc + lint green, 무회귀; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
