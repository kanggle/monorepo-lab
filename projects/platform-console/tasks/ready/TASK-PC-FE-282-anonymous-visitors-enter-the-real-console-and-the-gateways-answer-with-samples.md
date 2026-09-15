# Task ID

TASK-PC-FE-282

# Title

익명 방문자가 **실제 콘솔 셸**에 들어오고, 게이트웨이 코어가 백엔드 대신 **샘플로 답한다** — `ADR-MONO-074` 갈래 A 의 기반 + 대시보드 샘플

# Status

ready

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
