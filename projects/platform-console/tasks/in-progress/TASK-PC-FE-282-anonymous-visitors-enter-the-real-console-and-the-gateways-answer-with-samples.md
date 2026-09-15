# Task ID

TASK-PC-FE-282

# Title

익명 방문자가 **실제 콘솔 셸**에 들어오고, 게이트웨이 코어가 백엔드 대신 **샘플로 답한다** — `ADR-MONO-074` 갈래 A 의 기반 + 대시보드 샘플

# Status

in-progress

# Owner

platform-console

# Task Tags

- code
- test
- security-boundary
- demo

---

# Goal

`ADR-MONO-074` **ACCEPTED — A · R1ⓐ · R2ⓐ · R3ⓐ** (소유자, 2026-09-15) 의 실행 1/8.

로그인하지 않은 방문자가 `/` 로 들어오면 `/demo` 가 아니라 **실제 `/dashboards/overview`** 를 보고, 그 화면의 값은
**합성 샘플**이며 사람이 읽는 값 끝에 «(샘플)» 이 붙는다. 화면·로더·파서·에러 매핑은 바꾸지 않는다 — 바뀌는 것은
**백엔드로 나가기 직전의 한 분기**와 **셸의 익명판**뿐이다.

🔴 이 티켓이 `(console)/layout.tsx:90` 가드의 의미를 «익명은 들어올 수 없다» 에서 «익명은 들어오지만 백엔드에 닿을 수
없다» 로 바꾼다. 그래서 그것을 지키는 **가드가 이 티켓의 AC 다**(나중 티켓으로 미루지 않는다 — ADR § Consequences).

---

# Scope

## In Scope

- 샘플 방문자 판정 (A1)
- 샘플 라우터 `src/shared/sample/**` + 대시보드 픽스처(운영자 개요 · 도메인 헬스 · 레지스트리/카탈로그 · 알림 인박스)
- 게이트웨이 코어 6 + 코어 밖 `fetch` 전부의 샘플 분기 (A2)
- 쓰기 거부 `SAMPLE_READ_ONLY` (R1ⓐ)
- 셸 익명판 · 상시 배너 · 루트 착지 (A7 · A8)
- 샘플 원장 + 인벤토리 대조 (A9)
- 가드 셋: 샘플 라우터 금지 임포트 · 코어 밖 `fetch` 금지 · «(샘플)» 표기 규칙
- 기대값이 뒤집히는 테스트 교체

## Out of Scope

- 도메인 픽스처(iam·ecommerce·erp·finance/ledger·wms·scm) — `TASK-PC-FE-283`~`288`
- `(demo)` 그룹·`features/demo-tour`·`console-sample` 데이터셋 은퇴 — `TASK-MONO-686` (🔴 `/demo` 는 이 티켓 뒤에도 **남는다**)
- 로그인한 운영자의 동작 — **한 글자도 바뀌면 안 된다**(대조군)

---

# Acceptance Criteria

- [ ] **AC-0 재인벤토리** — `apps/console-web/src` 에서 백엔드로 나가는 `fetch(` 를 **전수**로 세고 표를 이 파일에 적는다:
      코어별 GET/쓰기 수 · 코어 밖 `fetch` 위치 · 코어 밖에서 세션을 읽는 자리. 🔴 ADR 의 94/99 · «코어 밖 5» 는
      **코드 읽기 스윕 수**다 — 다르면 이 파일에 적고(ADR 본문은 안 고친다) 범위만 맞춘다.
      🔴 AC-0 이 «클라이언트가 백엔드를 직접 부르는 자리» 를 하나라도 찾으면 **구현하지 않고 되돌린다**(ADR 전제 붕괴).
- [ ] **AC-1 판정** — `shared/lib/session.ts` 에 `isSampleVisitor()` 하나: 액세스 쿠키 **와** 운영자 쿠키가 **둘 다 없음**.
      단위 테스트 4칸(없음·없음=true / 액세스만 / 운영자만 / 둘 다 = false). 🔴 판정은 이 함수 **하나**만 쓴다.
- [ ] **AC-2 코어 분기** — `callAdminGateway` · `fetchRegistry` · `callWmsGateway` · `callEcommerceGateway` ·
      `callFlatEnvelopeGateway`(scm 심 포함) + AC-0 이 찾은 코어 밖 `fetch` 전부가 **토큰을 읽기 전에** 판정을 묻고,
      샘플이면 샘플 라우터의 `Response` 를 **기존 응답 처리 코드에 그대로** 넘긴다. 코어마다 테스트:
      ① 샘플 방문자 → 전역 `fetch` 스파이 **0회** + 파싱된 값 반환 ② 인증 방문자 → **기존 테스트 무수정 초록**.
- [ ] **AC-3 쓰기 거부 (R1ⓐ)** — 샘플 라우터는 비-GET 에 `403 { code: "SAMPLE_READ_ONLY" }` 를 준다.
      🔴🔴 **문구를 메시지에 싣지 않는다** — 코어 넷이 403 메시지를 덮어쓴다(실측: `iam-gateway.ts:239-248` `'not permitted'`,
      `wms-gateway.ts:203-213`, `ecommerce-gateway.ts:222-231`, `flat-envelope-gateway.ts:327-337`). 코드는 보존된다
      (`e.code || …`). ⇒ 문구 «샘플 화면에서는 실행되지 않습니다. 로그인하면 실제로 실행됩니다» 는 **코드 → 문구 매핑 한
      곳**에서 나온다. 화면별 분기 금지. AC-0 에서 쓰기 에러를 그리는 렌더러가 몇 종인지 세고, 전부가 그 매핑을 거치는지
      테스트로 문다.
- [ ] **AC-4 샘플 라우터 격리 (A3)** — `shared/sample/**` 는 `@/shared/config/env` · `@/shared/lib/session` · 전역 `fetch` 를
      임포트/호출하지 않는다. 정적 테스트가 문다 + **bite**(금지 임포트 한 줄 넣으면 빨강, 되돌리면 초록 — 복원된 트리에서 재실행).
- [ ] **AC-5 코어 밖 `fetch` 금지 (A2 의 무게)** — `apps/console-web/src` 의 `fetch(` 는 AC-0 표에서 파생한 **허용 목록** 안에만
      있다. 새 `fetch(` 가 목록 밖이면 빨강 + bite. 🔵 루트 `scripts/check-fetch-resolution.mjs` 는 «해석을 지나는가» 를 재고
      이 가드는 «샘플 분기 뒤에 있는가» 를 잰다 — 축이 다르다, 대신하지 않는다(그 스크립트는 건드리지 않는다).
- [ ] **AC-6 셸 익명판 (A7)** — `(console)/layout.tsx` 는 샘플 방문자면 가드를 통과한다. 반쪽 세션·죽은 쿠키는
      **지금 경로 그대로**. 익명판: 계정 메뉴 자리에 «로그인»(`/login?redirect=<x-pathname>`) · 테넌트 스위처는 샘플 테넌트
      하나(읽기 전용) · 알림 벨은 샘플 인박스 · 🔴 `DemoHeartbeat` · `DemoBackendNotice` **미렌더**(`ADR-MONO-071` D8).
      상시 배너 «샘플 데이터로 보는 실제 콘솔 화면입니다 · 로그인하면 실제 데이터» 는 **레이아웃**이 그린다.
- [ ] **AC-7 «(샘플)» 표기 (R2ⓐ)** — 픽스처의 **사람이 읽는 문자열**(이름·제목·설명·메모) 끝에 ` (샘플)`. 🔴 id · 코드 · 금액 ·
      수량 · 날짜 · 상태 enum 에는 **안 붙인다**(파서·`StatusBadge` 가 해석한다). 규칙을 테스트가 문다 — 술어는 구현이 정하되
      **양쪽 bite**(표시 문자열에서 접미를 빼면 빨강 / enum 에 붙이면 빨강).
- [ ] **AC-8 루트 (A8)** — `/` 는 누구든 `/dashboards/overview`. `app/page.tsx` 의 분기 제거.
- [ ] **AC-9 원장 (A9)** — `shared/sample/coverage` 에 AC-0 의 GET 전부가 `ready | pending` 으로 등재. 인벤토리에 있는데 원장에 없으면
      빨강. `pending` GET 은 그 섹션을 기존 degrade 상태로 떨어뜨리고 «이 화면의 샘플 데이터는 준비 중입니다» 를 보인다
      (🔴 코어의 503 매핑이 profile 고정 문구를 쓰므로 AC-3 과 같은 이유로 **코드 `SAMPLE_NOT_READY` → 문구**).
- [ ] **AC-10 대시보드 샘플 (R3ⓐ)** — 운영자 개요 · 도메인 헬스 · 카탈로그 · 알림 인박스 GET 이 `ready`. `e2e-smoke`(백엔드 전부
      loopback)에서 익명 `/` → `/dashboards/overview` 에 배너 + 개요 지표가 선다.
- [ ] **AC-11 뒤집히는 기대값** — 세 파일의 기대값을 바꾸고 **헤더에 `ADR-MONO-074` 를 인용**한다(«빨개져서 고쳤다» 가 아니라
      «결정이 기대값을 바꿨다»):
      `tests/unit/demo-tour-console-guard-regression.test.tsx`(익명 = 셸 렌더 + `fetch` 0 · 반쪽 세션 = `/login`) ·
      `e2e-smoke/root-redirect.spec.ts`(`/` → `/dashboards/overview`) ·
      `e2e-smoke/demo-tour.spec.ts` 의 «`/ecommerce/orders` 직접 진입 → 로그인» 칸(나머지 `/demo` 칸은 `TASK-MONO-686` 까지 유지).
- [ ] **AC-12 샘플 운영자의 권한** — 샘플 방문자에게 **모든 화면이 열리는** 레지스트리/권한 집합을 준다(권한 거부 화면은 샘플로
      재현하지 않는다). 🔵 이것은 구현자 선택이다 — 소유자가 한 줄로 뒤집을 수 있게 이 파일에 «내 선택» 으로 적는다.
- [ ] **AC-13 대조군** — 로그인한 운영자 경로의 기존 단위 테스트 **무수정 초록**. 전후 `pnpm test` 통과 수를 둘 다 적는다.
- [ ] **AC-14 캐시/전환** — «익명으로 연 뒤 같은 브라우저로 로그인하면 샘플 문자열 0». 🔴 smoke 는 세션을 못 만든다
      (`root-redirect.spec.ts` 헤더) ⇒ 콘솔 full-stack(`nightly-e2e.yml`)에 싣거나, 못 실으면 **⚪ 로 이유와 함께** 기록한다.

---

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md` (A1~A10 · R1ⓐ · R2ⓐ · R3ⓐ)
- `docs/adr/ADR-MONO-070-public-browsing-served-from-a-versioned-vercel-snapshot.md` D3 · D6 (축 유지)
- `docs/adr/ADR-MONO-071-boot-the-bundle-the-visitor-chose.md` D8
- `projects/platform-console/PROJECT.md` · `specs/services/console-web/architecture.md` · `platform/service-types/frontend-app.md`
- `projects/platform-console/docs/conventions/frontend-ui.md` § 5 (로컬 게이트)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.1 · § 2.5 · § 2.6 — 🔴 인증 방문자에 대해서는 **불변**

---

# Edge Cases

- 액세스 쿠키만 있음(pre-operator) → 샘플 아님, 기존 온보딩/로그인 경로.
- 쿠키는 있으나 백엔드가 401(죽은 세션) → 샘플 아님, 기존 강제 재로그인.
- 샘플 방문자가 `/api/**` 를 직접 POST → 라우트 핸들러가 코어를 거치므로 `SAMPLE_READ_ONLY`.
- 샘플 방문자가 테넌트 전환 → 전환 라우트가 샘플 분기(토큰 교환 **호출 0**).
- 픽스처 id 로 상세 경로 진입 → 목록과 같은 id 로 찾아져야 한다(도메인 티켓에서도 반복 확인).
- 존재하지 않는 id → 실제와 같은 404 모양.

# Failure Scenarios

- 코어 밖 `fetch` 가 분기를 빠뜨림 → 토큰이 없어 백엔드 401 → 익명이 재로그인 루프. 🔴 데이터 유출은 아니지만 AC-5 가 막는다.
- 샘플 분기가 **인증 방문자**에게 켜짐 → 운영자가 샘플을 실데이터로 오인. AC-1 의 4칸 + AC-13 대조군이 막는다.
- 픽스처가 파서 스키마를 어김 → 섹션 degrade 로 보여 «고장» 과 구별 안 됨 ⇒ 픽스처마다 스키마 파싱 테스트.
- 빌드가 샘플 응답을 정적으로 굽는다 → `(console)` 은 `force-dynamic` 이지만 AC-14 로 확인.

---

# Test Requirements

- 로컬 게이트(각각 독립 statement + `rc=$?`): `pnpm lint` · `npx tsc --noEmit` · `pnpm test` · `pnpm test:e2e:smoke`(가능 시)
- 가드 셋(AC-4 · AC-5 · AC-7)은 bite 로 발화 증명 후 복원 트리에서 재실행
- 🔴 헤딩·testid·경로를 바꾸므로 `nightly-e2e.yml` 의 콘솔 스펙 디렉터리를 grep 하고, 머지 후 첫 nightly 를 1회 확인

---

# Definition of Done

- [ ] AC-0~14 닫힘(⚪ 는 이유와 함께)
- [ ] 로그인 운영자 경로 무변화 증명
- [ ] 익명 `/` → 실제 개요 + 배너

분석=Opus 5 / 구현 권장=**Opus 5** (보안 경계 이동 · 코어 6 동시 변경).

---

# Implementation notes (구현 에이전트, 2026-09-15 UTC)

## AC-0 재인벤토리 — 측정값 (코드 스윕, 런타임 측정 아님)

🔴 **클라이언트가 백엔드를 직접 부르는 자리: 0 (ADR 전제 유지).** 유일한 브라우저→비-오리진 호출은
`features/ecommerce-ops/hooks/use-ecommerce-images.ts` 의 presigned S3 PUT(XHR) 인데, 그 URL 은
`POST …/images/upload-url`(**쓰기**, 코어 경유)의 응답으로만 생긴다 → 샘플 방문자는 그 쓰기를 거부당하므로 도달 불가.
자격증명도 싣지 않는다. 그래서 되돌리지 않고 진행했다.

### 네트워크 호출 자리 (`src/**` 의 `fetch(` · `XMLHttpRequest` · `sendBeacon`, 주석 제외) — 24 파일 / 26 호출

| 분류 | 파일 (호출 수) |
|---|---|
| **gated** (샘플 분기 뒤, 9) | `shared/api/{iam-gateway, registry-client, wms-gateway, ecommerce-gateway, flat-envelope-gateway}.ts` (각 1) · `app/api/console/dashboards/{operator-overview, domain-health}/route.ts` · `app/api/console/notifications/{inbox, [sourceDomain]/[id]/read}/route.ts` (각 1) — `api/tenant/route.ts` 는 직접 fetch 가 없고(레지스트리·교환 헬퍼 경유) 역시 gated |
| same-origin (7) | `shared/api/client.ts` (2) · `features/operator-overview/api/operator-overview-api.ts` · `features/domain-health/api/domain-health-api.ts` · `features/operators/api/account-existence.ts` · `shared/lib/logout.ts` · `shared/observability/web-vitals.tsx` (2: sendBeacon+fetch) · `widgets/demo-heartbeat/DemoHeartbeat.tsx` (샘플 방문자에게 미마운트) |
| auth-flow (5) — 익명에서 **벗어나는** 경로라 분기하면 로그인이 깨진다 | `app/api/auth/{callback, refresh, logout}/route.ts` · `shared/lib/{operator-token-exchange, assume-tenant-exchange}.ts` |
| unreachable-for-sample (3) | `features/onboarding/api/onboarding-client.ts` · `features/onboarding/components/CreateOrganizationForm.tsx` (pre-operator 전용) · `use-ecommerce-images.ts` (위 XHR) |

🔵 **ADR 의 «코어 밖 구멍 5» 는 맞았다** (console-bff 4 + tenant 1). 코드에서 더 나온 것은 전부 same-origin/auth-flow/도달불가 분류다.
🔵 `src/` 밖: `@demo/backend-resolver` 가 `/status` 를 fetch 하지만 `resolveBackendUrl()` 은 **샘플 분기 뒤**에서만 불린다.
`(demo)` 그룹의 `@demo/public-data` 는 `(console)` 에서 도달하지 않는다.

### 코어별 GET / 쓰기 — `method: '<VERB>'` 리터럴 스윕

| 코어 | GET 리터럴 | 쓰기 리터럴 | 비고 |
|---|---:|---:|---|
| `callAdminGateway` (iam) | 20 | 35 | 9 surface: accounts · audit · operators · rbac · subscriptions · partnerships · tenants · org_nodes · groups |
| `fetchRegistry` | 1 | 0 | |
| `callWmsGateway` | ~19–21 | 7 | 래퍼 2곳의 리터럴이 GET 인지 기본값인지 불확실 — 3 surface |
| `callEcommerceGateway` | 27 | 26 | 9 surface (`ecommerce` + `ecommerce_<event>` 8) |
| `callFlatEnvelopeGateway` (+scm 심) | ≥6 | ~30 | ⚪ scm-ops · finance · ledger · erp 읽기 호출은 `method` 를 **생략**(기본 GET)해서 리터럴 스윕에 안 잡힌다 — 8 surface |
| console-bff 프록시 | 3 | 1 | |

⚪ ADR 의 94/99 와 **직접 비교하지 않는다** — 단위가 다르다(ADR=호출 함수 이름 스윕, 여기=메서드 리터럴). 차이의 원인이 위
«method 생략» 과 래퍼 리터럴이다. 🔴 그래서 원장 granularity 를 **엔드포인트가 아니라 surface(프로필 `logPrefix`)** 로 잡았다(아래 편차 D1).

### 코어 밖에서 세션을 읽는 자리

`(console)/layout.tsx` · `(console)/account/page.tsx` · `(console)/{operators, subscriptions, partnerships}/page.tsx` ·
`features/*/api/*-state.ts` 의 `getActiveTenant()` 선확인 9곳(audit · accounts · dashboards · iam-overview · operators ·
partnerships · tenants · org-nodes · operator-groups) · `shared/api/{rbac-catalog, iam-*-read}.ts` · `app/page.tsx` ·
`(auth)/login/page.tsx` · `api/demo/heartbeat` · auth 라우트들. → 테넌트 선확인은 `getActiveTenant()` 가 샘플 방문자에게
샘플 테넌트를 돌려주는 것으로 **한 곳에서** 해결했다(편차 D3).

### 쓰기 에러 렌더러 — 3 종

| 종 | 규모 | 샘플 문구 도달 경로 |
|---|---|---|
| K1 `messageForCode(code, …)` 조회 | 71 파일 (errors.ts 제외) | 매핑에 `SAMPLE_READ_ONLY` 가 있다 |
| K2 `err.message` 출력 (도메인 매퍼의 `default:` 등) | 4 매퍼 (`approval-error` · `use-master-write` · `use-department-write` · `discrepancy-error`) | 단일 클라이언트 입구 `shared/api/client.ts` 가 샘플 코드의 message 를 **같은 매핑**으로 다시 쓴다 |
| K3 에러를 안 읽는 고정 문구 | ⚪ 개별 미집계 (`useMutation(` 83곳 · 컴포넌트 `isError` 59곳 중 일부) | 같은 입구가 거부 신호를 발행 → 레이아웃 배너의 `SampleRefusalNotice` 가 문구를 말한다 |

테스트: `tests/unit/sample-read-only-copy.test.tsx` (K1·K2·K3 각 1칸 + 대조군).

## AC-12 — 내 선택 (소유자가 한 줄로 뒤집을 수 있다)

샘플 레지스트리(`shared/sample/fixtures/registry.ts`)는 **6 제품 전부 `available: true`, 테넌트 = `['sample']`** 이다 →
모든 화면의 레지스트리 eligibility 선확인이 통과한다. 권한 거부 화면은 샘플로 재현하지 않는다.
뒤집으려면: 그 파일의 `available`/`tenants` 를 바꾸면 된다(다른 코드 변경 없음).

## 편차 / 구현자 선택 (작은 것까지)

- **D1 원장 granularity = surface.** `shared/sample/coverage.ts` 의 행은 엔드포인트가 아니라 게이트웨이 프로필
  `logPrefix` (37행: ready 4 · pending 33) + 화면 원장(64행: ready 3 · static 6 · pending 55). 가드
  (`sample-coverage-ledger.test.ts`)는 코드에서 `logPrefix` 리터럴 · ecommerce 슬라이스 라벨 · console-bff GET 라우트 ·
  `(console)/**/page.tsx` 를 **다시 세어** 양방향 대조한다. ⚪ 한계: 이미 `ready` 인 surface 안에 새 GET 이 생기면 가드는
  모른다 — 라우터가 그 경로를 `SAMPLE_NOT_READY` 로 답하므로 조용한 200 은 아니다.
- **D2 «준비 중» 문구는 섹션이 아니라 셸이 말한다.** 섹션 degrade 상태는 `degraded: true` 만 저장하고 코드를 버린다(wms-ops
  state 5곳 등 실측). 그래서 레이아웃의 클라이언트 컴포넌트 `SampleScreenNotice` 가 `usePathname()` + 화면 원장으로 판정하고
  문구는 `messageForCode('SAMPLE_NOT_READY')` 에서 가져온다. 섹션 자체는 코어의 503 매핑으로 기존 degrade 상태가 된다.
  CSR 쿼리의 `ApiError.message` 도 입구에서 같은 문구로 바뀐다.
- **D3 `getActiveTenant()` 가 샘플 방문자에게 `'sample'` 을 돌려준다** (판정은 `isSampleVisitor()` 하나). 이유: 테넌트
  선확인이 코어 밖 9+곳에 흩어져 있어, 안 그러면 pending 화면이 «테넌트를 선택하세요» 게이트로 떨어진다.
- **D4 `sampleGate()` 는 판정 함수가 던지면 «샘플 아님»** 으로 간다(기존 경로 = 같은 쿠키 저장소를 읽고 같은 실패를 낸다).
  반대 방향(던지면 샘플)은 인증 운영자에게 샘플을 보여 줄 수 있는 쪽이라 택하지 않았다. 부수 효과: `@/shared/lib/session` 을
  토큰 getter 만으로 목킹한 기존 테스트 3개(partnerships/subscriptions/tenants client)가 **무수정**으로 실경로를 계속 잰다.
- **D5 코어 리팩터링의 모양.** 각 코어의 «토큰 → 헤더 매트릭스» 블록을 `prepare*Headers()` 로 **글자 그대로** 옮기고 그 호출을
  `sample ? {} : …` 뒤에 뒀다. wms·flat 은 `profile.resolveDefaults(env)`(순수 함수)를 분기 앞으로 올렸다(샘플 경로 이름 계산용).
  샘플 방문자의 쓰기는 사유/멱등키 사전검증 **전에** `SAMPLE_READ_ONLY` 가 된다.
- **D6 mark-read(POST) 거부는 라우트의 기존 매핑 그대로 `502`** 가 된다(403 을 통과시키면 인증 운영자의 BFF-403 동작이
  바뀐다). 벨은 mark-read 실패를 설계상 무시하므로 이 쓰기에는 문구가 렌더되지 않는다 — 성공으로 보이지도 않는다.
- **D7 `api/tenant` 는 upstream 응답 매핑이 없어** 샘플 403 을 그대로 반환한다(레지스트리 조회 · 교환 호출 0).
  분기 위치는 **본문 파싱 · `tenant === ''` 해제 분기 뒤, `fetchRegistry` · 토큰 교환 앞**이다 — 해제 분기는 쿠키만 지우고
  백엔드에 닿지 않으므로 그대로 뒀다(그 결과 `tenant-switch.test.ts` 의 해제 칸은 무수정 초록). 순서 가드
  (`sample-fetch-allowlist.test.ts`)는 «`sampleGate(` 가 모든 백엔드 도달보다 앞» 을 이 파일에서도 잰다.
- **D8 AC-11 목록 밖에서 기대값이 더 뒤집힌 파일** — 전부 헤더에 ADR-MONO-074 인용:
  - `tests/unit/demo-tour-root-redirect.test.ts` (AC-8 의 직접 결과) · `e2e-smoke/console-guard.spec.ts` (익명 `/operators` 가
    더 이상 `/login` 으로 튕기지 않는다).
  - 🔴 **51 파일 / 70 셀 (실패 테스트 72개)** — 도메인 토큰 코어(wms · ecommerce · flat: scm/erp/finance/ledger)의 api·proxy
    테스트와 `domain-health-proxy` · `tenant-switch` 의 «no IAM session → 401, fetch 0» 칸. 이 칸들은 **빈 쿠키 병**으로
    «세션 없음» 을 모델링했는데, 빈 병은 A1 에 따라 이제 **샘플 방문자**다(여전히 fetch 0 이지만 답이 401 이 아니라 샘플).
    그래서 각 칸이 **반쪽 세션(운영자 쿠키만, IAM 액세스 쿠키 없음)** 을 심도록 바꿨다 — 401 경로가 **여전히 존재하는** 상태이고,
    칸의 단언(401 · fetch 0)은 한 글자도 안 바꿨다. 기계적 변환(스크래치 스크립트)이라 파일마다 같은 모양이다.
    🔵 이것은 «인증된 운영자 경로» 테스트가 아니다(AC-13 의 대조군은 이 칸들을 포함하지 않는다) — 인증 칸은 전부 무수정이다.
  - `tests/unit/layout-login-redirect.test.ts` · `relogin-loop.test.tsx` · `domain-health-nav.test.tsx` 는 **무수정 초록**.
- **D9 배너 색은 시맨틱 토큰**(`bg-muted`/`border-border`) — 상태칩이 아니므로 `StatusBadge` 대상이 아니고, 손 팔레트도 쓰지 않았다.
- **D10 `app/page.tsx` 는 세션을 더 이상 읽지 않는다** (유닛 테스트가 «읽기 0» 을 잰다). `force-dynamic` 은 유지.

## 측정 (이 워크트리 · Windows 호스트 · 각 게이트 독립 실행 + 명시 rc)

| 게이트 | 결과 |
|---|---|
| `pnpm lint` | rc=0 · «No ESLint warnings or errors» |
| `npx tsc --noEmit` | rc=0 |
| vitest **변경 전** (손대지 않은 트리, 맨 먼저) | rc=0 · **292 files / 3031 tests passed** |
| vitest **변경 후** (최종 트리) | rc=0 · **301 files / 3161 tests passed** (신규 9 파일 · +130 tests · 실패 0) |
| `pnpm build` | rc=0 |
| `pnpm e2e:smoke` (백엔드 전부 loopback 127.0.0.1:1) | rc=0 · **18 passed** — `sample-visitor` 3 · `root-redirect` 3 · `console-guard` 2 · `demo-tour` 5 · `login-page` 5 |

🔵 AC-13 대조군: 중간 실행(변환 전)에서 72 테스트가 빨개졌고 **전부** «빈 쿠키 병 = 세션 없음 → 401» 칸이었다(D8). 인증 운영자 칸은
한 개도 빨개지지 않았다. 알려진 Windows flake(`OperatorsScreen.test.tsx`)는 이번 두 실행 모두 초록.

### Bite (금지 코드를 넣어 빨강 → 되돌려 복원 트리에서 초록)

| # | 가드 | 주입 | 빨강 (rc) | 복원 후 |
|---|---|---|---|---|
| B1 | AC-4 `sample-router-isolation` | `shared/sample/codes.ts` 에 `import { getServerEnv } from '@/shared/config/env'` | rc=1 · `non-relative import '@/shared/config/env'` | 초록 |
| B2 | AC-5 개수 `sample-fetch-allowlist` | 새 파일 `shared/lib/bite-probe.ts` 에 `fetch('/bite')` | rc=1 · `+ "shared/lib/bite-probe.ts": 1` | 초록 (파일 삭제) |
| B3 | AC-5 순서 (A2) | `api/console/dashboards/domain-health/route.ts` 의 `sampleGate(` 앞에 `await getAccessToken()` | rc=1 · `getAccessToken … comes before sampleGate(` | 초록 |
| B4 | AC-7 방향 ① | 레지스트리 `displayName: 'IAM (샘플)'` → `'IAM'` | rc=1 · `$.products[0].displayName` `missing-suffix` | 초록 |
| B5 | AC-7 방향 ② | 도메인 헬스 `status: 'UP'` → `'UP (샘플)'` (**단독 재주입**) | rc=1 · `$.cards[0].data.status` `suffix-on-machine-value` (+ 스키마 테스트도 enum 거부) | 초록 |
| B6 | AC-9 원장 | `SURFACE_COVERAGE` 에서 `groups` 행 삭제 | rc=1 · `+ "groups"` (원장에 없는 surface) | 초록 |

B1–B4·B6 는 한 번에 주입해 가드 파일별로 귀속했다(5 files / 7 tests 빨강). 복원 트리 재실행: rc=0 · 5 files / 76 tests.
🔴 B5 는 합동 실행에서 **증거가 되지 못했다** — vitest 가 같은 `it.each` 줄의 두 실패를 한 에러 블록으로 묶어 레지스트리 diff 만
출력했고, 눈에 띈 `UP (샘플)` 은 스키마 테스트의 zod enum 거부였다. 그래서 단독으로 다시 주입해 위 문구를 직접 확인했다.

## ⚪ 측정하지 못한 것

- **AC-14 캐시/전환** — `tests/e2e/sample-visitor-transition.spec.ts` 를 **실었다**(익명으로 개요 → 샘플 문자열 존재 확인 →
  같은 컨텍스트로 실제 OIDC PKCE 로그인 → 같은 주소에 배너 0 · `(샘플)` 0). 🔴 **로컬에서 한 번도 안 돌렸다** — full-stack
  (`docker-compose.e2e.yml` + seed) 이 필요하고 `nightly-e2e.yml` 의 `platform-console-e2e-fullstack` 잡이
  `pnpm exec playwright test --project=chromium` 으로 `tests/e2e/**` 전부를 돈다. ⇒ 머지 후 첫 nightly 가 이 스펙의 첫 측정이다.
- **nightly 영향 grep** — `tests/e2e/**` 의 기존 3 스펙(operators-admin-profile · operators-profile · overview-consolidation)은
  전역 storageState(로그인됨)로 돈다. 이 티켓이 바꾼 헤딩·testid 는 **샘플 셸에만** 생겼고(`sample-visitor-*`,
  `sample-screen-not-ready`) 인증 셸의 testid(`account-menu-*` · `logout-button` · `tenant-*` · `nav-*`)는 그대로다.
  `/demo`·`/login?redirect` 를 기대하는 nightly 스펙은 0건.
- **K3 렌더러(에러를 안 읽는 고정 문구) 개별 수** — 셸 알림이 전부를 덮으므로 개별로 세지 않았다(위 표).
- **실제 Vercel 배포에서 샘플 방문자** — 로컬 production build + smoke 까지만 쟀다.
