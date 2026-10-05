# Task ID

TASK-PC-FE-305

# Title

데모 서버가 꺼진 뒤 남은 세션은 로그인 벽이 아니라 **샘플 셸**로 끝난다

# Status

review (2026-10-04 UTC — 로컬 게이트 전부 통과: lint/tsc/vitest 335파일·3761시험/next build rc=0, bite 확인. AC-2 «판정 불가» 칸은 소유자 결정 ①(2026-10-04)으로 알려진 한계로 닫힘, AC-6 라이브 ⚪. 경로 D 는 `TASK-PC-FE-306`(ready))

# Owner

frontend

# Task Tags

- code
- frontend
- console-web
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 세션 종료 경로(갱신 라우트 · 401 지점 53곳 · 로그인 페이지)의 합류점을 찾아 판정 하나를 끼우고, PC-FE-299 의 보장(캐시 비움 · no-store · bfcache)을 깨지 않아야 한다.

---

# Dependency Markers

- 선행(done): `TASK-PC-FE-299`(데모 종료 뒤 세션 잔존 표면 — 이 티켓이 바꾸는 `unavailable` 문구 분기를 만든 티켓), `TASK-MONO-674`(유휴 만료 갱신 홉), `TASK-PC-FE-278`(강제 재로그인 마커), ADR-MONO-074(샘플 방문자).
- 후속: 없음. 소유자 결정 항목(아래 § Owner decisions)은 별도 티켓 후보.

# Goal

소유자 보고(2026-10-04): «콘솔 데모 서버가 켜지고 로그인을 한 뒤 로그인된 채로 데모 서버가 꺼지고 콘솔로 들어가면, 로그인 안 된 상태에서 콘솔을 볼 수 있어야 하는데 "데모 서버가 종료되어 다시 로그인해야 합니다. 데모 시작 페이지에서 서버를 켠 뒤(약 10분) 다시 로그인해주세요."라고 나옴.»

데모 백엔드가 꺼져 있을 때 남은(갱신 불가) 세션을 가진 방문자는, IdP 도 같이 꺼져 있어 로그인이 **성공할 수 없는** 로그인 화면이 아니라 ADR-MONO-074 의 로그인 없는 샘플 셸에 착지해야 한다 — 왜 로그아웃됐는지 짧게 알려 주면서.

# Scope

## In Scope

- 데모 상태 신호(`resolveDemoBackendState()`)가 `unavailable` 일 때 강제 재로그인 착지에서 세션 쿠키를 지우고 샘플 셸로 보내는 판정 하나 + 쿠키를 지우는 라우트 핸들러.
- 샘플 셸의 안내(`?signed_out=demo_stopped`) + Query 캐시 비움(PC-FE-299 AC-5 승계).
- 계약 `console-integration-contract.md` § 2.6.2 신설, `architecture.md` Auth Flow 5b.

## Out of Scope

- 공유 해석기(`infra/demo/backend-resolver`) 변경 — `unavailable` 을 「꺼짐」/「판정 불가」로 가르는 일(§ Owner decisions ①).
- 액세스·운영자 쿠키가 아직 살아 있는 동안 데모가 꺼진 경우(갱신도 401 도 일어나지 않는다 — § Owner decisions ②).
- 브라우저 API 클라이언트의 갱신 실패(`shared/api/client.ts:94` → 마커 없는 `/login?redirect=…`).
- ADR-MONO-074 본문 수정(A1 술어는 바뀌지 않는다 — 아래 AC-0).

# Acceptance Criteria

- [x] **AC-0** — 데모가 꺼진 상태에서 남은 세션 방문자가 탈 수 있는 경로 전부를 file:line 으로 기록하고(레이아웃 가드 → 갱신 라우트 → `clearFullSession`; 페이지 401 리다이렉트; 로그인 페이지의 `unavailable` 분기), 세션 종료·샘플 방문자 동작을 정의하는 스펙(ADR-MONO-074 · 계약 · PC-FE-299)을 확인해 로그인 페이지 동작을 적은 스펙을 **먼저** 고친다.
- [x] **AC-1** — 세션을 갱신할 수 없고 **그리고** 데모 상태 신호가 백엔드 정지(`resolveDemoBackendState()` 의 `unavailable` — 로그인 페이지가 쓰는 같은 함수, 갱신 에러만으로 추정 금지)를 말하면, 세션 쿠키가 지워지고 방문자는 샘플 셸(요청한 화면이 샘플로 닿으면 그 화면, 아니면 `/`)에 착지하며 데모 서버가 꺼져 로그아웃됐다는 짧은 한국어 안내(`DemoBackendNotice` 문구와 일관)를 본다. 로그인 벽 없음.
- [x] **AC-2 (대조군)** — 데모 백엔드가 켜져 있고 세션만 만료/무효면 오늘 그대로(`/login?error=session_expired`, 일반 문구). 데모 상태를 판정할 수 없으면(`/status` 실패) 역시 오늘 그대로 — 그 선택을 명시한다. — 🟢 `running`·`starting`·`not-demo` 칸 / ⚪ `/status` 실패 칸: 공유 해석기가 `unavailable` 로 뭉쳐 돌려주므로 **오늘 그대로가 아니라 샘플로 간다** — 선택과 근거는 Implementation Record § AC-2, 결정은 § Owner decisions ①. — ✅ **소유자 결정 ①(2026-10-04 UTC)로 닫힘**: «데모 상태 확인(/status) 자체가 실패해 꺼짐인지 알 수 없을 때도 지금처럼(샘플 화면으로) 둔다 (Recommended)». 알려진 한계 — 해석기가 실패한 `/status` 에도 `unavailable` 을 돌려주고, 영향은 갱신 실패 / 401 **뒤**로 한정된다.
- [x] **AC-3** — 페이지 단위 401 리다이렉트가 AC-1/AC-2 와 일관되게 동작한다(15곳을 각각 고치기보다 공유 헬퍼 하나).
- [x] **AC-4** — PC-FE-299 의 보장(클라 잔존 상태 비움 · no-store · bfcache)이 유지되고 기존 테스트가 초록이다.
- [x] **AC-5** — 판정 유닛 테스트(정지 → 샘플; 실행 → 로그인; 판정 불가 → 로그인) + bite(분기 뒤집기 → 정확히 그 칸들만 빨강). 299 흐름의 Playwright/e2e 스펙이 있으면 갱신한다.
- [ ] **AC-6 (라이브, ⚪)** — 다음 데모 창: 로그인 → 데모 정지 → 콘솔 새로고침 → 안내가 붙은 샘플 셸.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.6.1 · § 2.6.2(신설)
- `projects/platform-console/specs/services/console-web/architecture.md` § Auth Flow 5a · 5b(신설)
- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md` A1
- `projects/platform-console/tasks/done/TASK-PC-FE-299-session-end-after-demo-shutdown.md`
- `infra/demo/backend-resolver/README.md`(상태 표 — `unavailable` 의 정의)

# Related Contracts

- `console-integration-contract.md` § 2.6.2 (이 티켓이 신설 — 콘솔 내부 라우트 `GET /api/auth/demo-ended`, 외부 API 변경 없음)

# Target App

- `apps/console-web`

# Edge Cases

- `starting`(켜지는 중) — 꺼진 것이 아니므로 로그인(PC-FE-299 와 같은 판단).
- `not-demo`(로컬·CI·비데모 배포) — 물어볼 컨트롤 플레인이 없으므로 로그인.
- 로그인 페이지와 라우트가 신호를 다르게 읽는 순간(서버 인스턴스별 15초 캐시) — `demo_checked=1` 로 한 홉에서 끊는다.
- 요청한 화면이 `/onboarding…` — pre-operator 세션만 들이므로 샘플로 닿지 않는다 → `/`.
- 401 지점은 `redirect` 를 싣지 않는다 → 그 경로의 착지는 `/`(→ `/dashboards/overview`).

# Failure Scenarios

- 갱신 에러의 모양만으로 데모 종료를 추정하면 IAM 일시 장애가 「데모 종료」 로 번역된다 — 금지(AC-1).
- 쿠키를 지우지 않고 샘플로 보내면 레이아웃 가드가 다시 갱신 홉을 태워 루프가 된다 — 라우트가 반드시 지운다.
- 쿠키를 지우는 GET 이 호출자를 믿으면 링크 하나로 살아 있는 세션을 끝낼 수 있다 — 라우트가 신호를 다시 읽는다.

---

# AC-0 Trace (2026-10-04 UTC, 이 브랜치 기준 file:line — 모두 `apps/console-web/` 기준)

**경로 A — 레이아웃 가드(소유자 보고의 경로)**: 데모가 유휴로 꺼질 즈음엔 30분짜리 액세스 쿠키가 이미 없고 30일 리프레시 쿠키만 남는다.
1. `src/app/(console)/layout.tsx:146` `isSampleVisitor()` → `false`(리프레시 쿠키가 있다 — `src/shared/lib/session.ts:297-303`).
2. `layout.tsx:147-155` → `hasRefreshToken()` 참 → `GET /api/auth/refresh?redirect=<path>`.
3. `src/app/api/auth/refresh/route.ts:175` `refreshSessionCookies` — IdP 가 꺼져 있으므로 네트워크 실패 → `case 'error'`(`:211-212`) → `sessionExpired()` → `/login?error=session_expired&redirect=<path>`, **쿠키 유지**(계약 § 2.6.1 «IAM 5xx / network → cookies kept»). IAM 4xx 면 `:197` 에서 `clearFullSession` 후 같은 착지.
4. `src/app/(auth)/login/page.tsx`(변경 전 `:97-107`) — 마커 있음 → `resolveDemoBackendState()` → `unavailable` → `:48` 「데모 서버가 종료되어 다시 로그인해야 합니다…」. 소유자가 본 화면이 정확히 이것.

**경로 B — 페이지 단위 401**: `(console)` 아래 서버측 401 지점(계약 § 2.6.1 이 53곳으로 셈; `tests/unit/relogin-marker.test.ts` 가 마커를 강제)과 `layout.tsx:185`(카탈로그 401), `src/app/(onboarding)/layout.tsx:25` 가 전부 `/login?error=session_expired`(redirect 없음)로 간다. 데모가 꺼져 있으면 백엔드가 응답하지 않아 401 은 드물지만, 도달하면 경로 A 의 4번과 **같은 착지**다.

**경로 C — 브라우저 API 클라이언트**: `src/shared/api/client.ts:71-94` — 401 → `POST /api/auth/refresh` 실패 → `/login?redirect=<current>`(마커 없음). 마커가 없으므로 데모 상태를 묻지 않는다(Out of Scope).

**경로 D — 쿠키가 아직 살아 있음**: `isAuthenticated()`(`session.ts:249-251`) 참 → 인증 셸 + `DemoBackendNotice`. 갱신도 401 도 없다(Out of Scope, § Owner decisions ②).

**⇒ 합류점은 하나**: 경로 A·B 는 모두 `/login?error=session_expired` 에서 만난다. 판정은 그 자리에서 한 번 하면 53곳을 고치지 않아도 된다(AC-3).

**스펙 확인**:
- 계약 § 2.6.1 은 실패 착지를 `/login?error=session_expired` 로 적고 «Unchanged: … the 53 server-side 401 sites» 라고 적는다 → § 2.6.2 를 **먼저** 신설하고 § 2.6.1 끝에 «Superseded in one case by § 2.6.2» 를 달았다. 401 지점과 § 2.6.1 핸들러는 실제로 안 바뀌므로 그 문장들은 참으로 남는다.
- `architecture.md` Auth Flow 5a → 5b 추가.
- ADR-MONO-074 A1(«반쪽 세션·죽은 쿠키는 지금 경로 그대로 … 샘플이 되지 않는다») — `isSampleVisitor()` 술어는 **바꾸지 않았다**. 죽은 쿠키를 샘플로 재해석하는 것이 아니라, 데모 정지가 확인되면 세션 종료 라우트가 쿠키를 **지우고**, 쿠키가 없는 방문자가 A1 그대로 샘플이 된다. ADR 본문은 손대지 않았다(§ Owner decisions ③).
- PC-FE-299 AC-4 의 `unavailable` 문구는 `demo_checked=1` 폴백으로만 남는다.
- 🔴 신호의 한계: `infra/demo/backend-resolver/README.md:30` 표 — `unavailable` = «`/status` 실패 · `state≠running` · ip 없음». `src/index.ts:184-186` 에서 `/status` 실패는 `null` → `:246` `unavailable`. **콘솔은 공유 해석기로는 「정지」와 「판정 불가」를 가를 수 없다.**

# Implementation Record (2026-10-04 UTC)

## 판정이 사는 곳

- `src/shared/lib/session-end.ts` — `sessionEndDestination(state)` 가 **유일한 판정**(`unavailable` → `sample`, `running`·`starting`·`not-demo` → `login`). 목적지 소독 `sampleReturnPath`(갱신 라우트의 `resolveRefreshReturnPath` + `/onboarding…` 거절), 표식 상수, URL 빌더.
- `src/app/(auth)/login/page.tsx` — 마커가 있을 때만 신호를 묻고(변경 전과 같은 조건), `sample` 이면 `redirect('/api/auth/demo-ended?redirect=…')`. `demo_checked=1` 이면 다시 넘기지 않고 PC-FE-299 문구를 폴백으로 렌더.
- `src/app/api/auth/demo-ended/route.ts`(신규) — 신호를 **다시** 읽어(쿠키를 지우는 GET 은 호출자를 믿지 않는다) 같은 판정이 `sample` 이면 `clearFullSession` → `<target>?signed_out=demo_stopped`, 아니면 쿠키를 안 건드리고 `/login?error=session_expired&demo_checked=1[&redirect=…]`. 둘 다 `Cache-Control: no-store`.
- `src/widgets/sample-visitor/DemoSignedOutNotice.tsx`(신규) — 표식이 있을 때만 「데모 서버가 종료되어 로그아웃되었습니다. 지금은 샘플 데이터로 둘러보는 중입니다 — 실제 데이터는 데모 시작 페이지에서 서버를 켠 뒤(약 10분) 다시 로그인하면 볼 수 있습니다.」 + `ForcedReLoginCacheReset` 마운트(Query 캐시 비움).
- `src/app/(console)/layout.tsx` — 샘플 분기에 `<Suspense><DemoSignedOutNotice/></Suspense>`.

## AC-2 «판정 불가» 의 선택 — 명시

`resolveDemoBackendState()` 는 `/status` 실패를 `unavailable` 로 돌려준다(AC-0 § 신호의 한계). 그래서 이 티켓의 실제 동작은: **`/status` 가 실패하면 정지로 읽혀 샘플로 간다** — AC-2 문구(«판정 불가면 오늘 그대로»)와 **어긋난다.** 공유 해석기를 바꾸지 않고 콘솔 안에서 가를 방법은 컨트롤 플레인 `/status` 를 직접 한 번 더 부르는 것뿐인데, 그것은 `scripts/check-demo-resolver-copies.sh` 가 막으려는 «앱이 자기 구현을 갖는 것» 이다. 완화: 이 판정은 (a) 세션이 이미 갱신에 실패했거나 백엔드가 401 을 낸 **뒤**에만 돌고, (b) PC-FE-299 의 「데모 서버가 종료」 문구가 같은 오판을 이미 하고 있었다(같은 함수의 같은 값). 이 칸은 ⚪ — § Owner decisions ①.

## 테스트

- `tests/unit/session-end-demo-stopped.test.tsx`(신규, 23칸): ① 판정 4값 ② 로그인 페이지(정지 → 라우트로 넘김 · 대조군 3종 → 오늘 문구 · 마커 없음 → 신호 미호출 · `demo_checked=1` 루프 상한) ③ 라우트(정지 → 세션 쿠키 6종 삭제 · `console_last_tenant` 유지 · 표식 · no-store; 대조군 3종 → 쿠키 무변경 + `demo_checked=1`; 목적지 소독 6종) ④ 안내 위젯(표식 → 안내 + 캐시 비움; 표식 없음 → 무렌더 · 캐시 유지).
- `tests/unit/login-session-expired-demo-signal.test.tsx`(PC-FE-299): `unavailable` 칸을 `demo_checked=1` 폴백 칸으로 바꿨다(정상 경로는 이제 리다이렉트). 나머지 4칸 그대로.
- e2e: `e2e-smoke/`·`tests/e2e/` 에 `session_expired`·「데모 서버가 종료」 를 다루는 스펙 0건(grep) — 갱신할 것 없음. 로컬에 컨트롤 플레인이 없어 `unavailable` 을 e2e 로 만들 수 없다(PC-FE-299 와 같은 사유).

## Bite

`sessionEndDestination` 의 삼항을 뒤집음(`'login' : 'sample'`) → 두 파일 28칸 중 **22 빨강 · 6 초록**. 초록 6칸은 판정을 거치지 않는 칸 전부다: 마커 없는 `/login` 2칸, `demo_checked=1` 루프 상한 2칸, 안내 위젯 2칸. 판정을 거치는 칸은 하나도 초록으로 남지 않았다. 원복 후 28/28.

## 검증 (2026-10-04 UTC, `apps/console-web`)

- `pnpm install --frozen-lockfile` rc=0
- `npx tsc --noEmit` rc=0
- `pnpm lint` rc=0 («No ESLint warnings or errors»)
- `npx vitest run --maxWorkers=4 --minWorkers=1` rc=0 — 335 파일 / 3761 시험
- `npx next build` rc=0 (`ƒ /api/auth/demo-ended` 생성 확인)

## Owner decisions (이 티켓이 정하지 못한 것)

1. **`unavailable` 을 둘로 가를 것인가** — 공유 해석기에 「컨트롤 플레인이 정지라고 답함」과 「`/status` 실패」를 가르는 값(또는 함수)을 더하면 AC-2 의 «판정 불가 → 오늘 그대로» 가 문자 그대로 성립한다. 해석기는 세 앱 + auth-forwarder 가 쓰는 공유 패키지라 루트 티켓 감이다.
2. **쿠키가 아직 살아 있는 동안의 정지(경로 D)** — 인증 셸이 빈 데이터 + `DemoBackendNotice` 를 보여준다. 이것도 샘플로 보낼지는 소유자 보고의 범위(«로그인된 채로 … 꺼지고 들어가면»)에 걸칠 수 있다. 바꾸면 레이아웃 가드가 매 요청 컨트롤 플레인을 묻게 된다(지금은 «가드는 네트워크 호출 없음» — 계약 § 2.6.1).
3. **ADR-MONO-074 A1 에 이력 한 줄을 남길지** — 술어는 그대로지만 «죽은 쿠키는 재로그인» 의 한 경우가 이제 «쿠키를 지우고 샘플» 로 끝난다.

## Owner decisions — 답 (2026-10-04 UTC, 원문 그대로)

1. «데모 상태 확인(/status) 자체가 실패해 꺼짐인지 알 수 없을 때도 지금처럼(샘플 화면으로) 둔다 (Recommended)» ⇒ AC-2 의 «판정 불가» 칸은 **알려진 한계로 닫는다**(해석기가 실패한 `/status` 에도 `unavailable` 을 돌려준다; 영향은 갱신 실패 / 401 뒤로 한정). AC-2 `[x]`.
2. «로그인 쿠키가 아직 살아 있을 때(Path D) 데모가 꺼져도 샘플 화면으로 보낸다 (Recommended)» ⇒ **이 PR 이 아니라 후속 티켓** `TASK-PC-FE-306`(`tasks/ready/`, 같은 PR 에서 기안만 — 구현 안 함).
3. (묻지 않음) ADR-MONO-074 이력 한 줄 — ADR 은 손대지 않는다. 위 3번 메모를 그대로 남긴다.

## CI 수정 (2026-10-04 UTC) — 클라이언트 그래프가 서버 전용 주소에 닿았다

PR #4149 첫 CI: `Client graph backend origins (the browser must not know the address)` RED — `scripts/check-client-graph-backend-origins.mjs` 가 console-web `hits=1`: `src/shared/config/demo-backend.ts → http://console.local`(ADR-MONO-067 D1). 원인: 클라이언트 컴포넌트 `DemoSignedOutNotice` 가 표식 상수를 `shared/lib/session-end.ts` 에서 import 했고, 그 판정 모듈은 `DemoBackendState` 를 위해 `demo-backend.ts` 를 import 한다 — `import type` 이어도 가드의 그래프에 든다(번들에는 안 들어가지만 가드는 import 문을 따라간다). 처방은 가드 완화가 아니라 **모듈 분리**: 상수만 `src/shared/lib/session-end-params.ts`(import 0)로 빼고, 클라이언트는 그 파일만 import 한다. `session-end.ts` 는 서버 전용으로 남아 상수를 재수출한다. 가드 rc=1 → rc=0 (console-web `reached=423 hits=0`). 로컬 첫 검증에서 이 가드를 돌리지 않은 것이 누락이었다.

---

## CORRECTION (2026-10-05 UTC) — 20차 창 판정 (2026-10-04 UTC · i-0c4859442f56d70e0 · ami-0d78d476824493d77 · f0927bcd0) — AC-6 은 이 창에서 **재지 않았다** (review 유지)

> 분석=Opus 5.5. 덧붙이기만 한다. AC-6 은 여전히 `[ ]` 이고 그것이 현재 상태다.

- 이 창의 데모 종료(15:46:15 `POST /stop` → 15:48:08 EC2 stopped)는 **`TASK-PC-FE-306` 의 경로**로 끝났다: 소유자의 액세스 쿠키가 아직 살아 있었으므로(로그인 30분 안) 레이아웃 가드가 `live=check` 홉을 보냈고, 15:49:16 `demo_ended_live_session_kept`(pending) → 15:49:40 `demo_ended_live_session_cleared` → 샘플 셸 + 안내. 판정은 `TASK-PC-FE-306` 20차 절.
- 🔴 그러므로 **이 티켓의 경로(갱신 실패 / 백엔드 401 뒤 — 액세스 쿠키가 만료됐거나 갱신이 실패한 세션이 `/login?error=session_expired` 착지 → `live` 없는 `/api/auth/demo-ended` 로 가는 길)는 라이브로 한 번도 타지 않았다.** AC-6 의 글자(«로그인 → 데모 정지 → 콘솔 새로고침 → 안내가 붙은 샘플 셸»)는 이번 창의 순서와 같아 보이지만, 그 순서는 30분 안에서는 306 의 live 모드로 끝나므로 305 핸들러의 판정 분기(단일 판독 · 갱신 실패 후 착지)를 재지 않는다.
- 공유된 것과 아닌 것:
  - ✅ 재사용 부품은 라이브로 보였다 — 305 가 만든 착지 `<target>?signed_out=demo_stopped`, 안내 `DemoSignedOutNotice`(«데모 서버가 종료되어 로그아웃되었습니다 …»), 세션 쿠키 지우기(`clearFullSession`, 306 의 live 모드가 같은 함수를 부른다).
  - ⚪ 305 고유 칸은 미측정 — 갱신 라우트 실패 → 로그인 착지 → `live` 없는 라우트의 단일 판독 판정, 그리고 페이지 단위 401 공유 헬퍼(AC-3) 경로.
- **다음 창에서 이 AC 를 재려면:** 로그인 → **30분 이상**(액세스 쿠키 `maxAge` 1800 s 만료) 기다리거나 액세스 쿠키만 지운 뒤 → 데모 정지 → 새로고침. 그러면 레이아웃 가드는 인증 분기가 아니라 갱신 홉을 타고, 갱신이 실패한 뒤 305 의 라우트에 닿는다. Vercel 로그에서 `live` 모드 이벤트(`demo_ended_live_session_*`)가 **아닌** 305 핸들러의 종료 이벤트가 찍히는지로 경로를 가른다.

---

## CORRECTION (2026-10-05 UTC) — 21차 창 (i-0aa3180ae21de4445 · ami-0a7b20c97325be01d · 678b6d003) — AC-6 은 이번에도 **재지 못했다** (review 유지)

> 분석=Opus 5.5. 덧붙이기만 한다. AC-6 은 여전히 `[ ]` 다.

**한 것** — 소유자가 콘솔(`demo@demo.com`)에서 개발자 도구로 `console_access_token` 을 삭제했다고 알린 뒤 데모를 정지했다(`/stop` 08:36:53Z → EC2 stopped 08:38:11Z · `/status` stopped · 스토어 탐침 `unavailable`). 그 뒤 새로고침 두 번.

**Vercel 로그(소유자 제공, UTC)**

| 시각 | 이벤트 |
|---|---|
| 08:39 무렵 | 첫 새로고침 → `/scm/inventory?demo_checked=1` (306 의 첫 판독 «kept») |
| 08:40:02 | `/dashboards/overview` → 307 → `/api/auth/demo-ended` |
| 08:40:03 | **`demo_ended_live_session_cleared`** (`state=unavailable`) → 샘플 셸(`org-sample-0001`) |
| 08:40:40 · 08:41:05 | 샘플 셸의 «로그인» → `oidc_login_initiated` ×2 → `/login?redirect=/dashboards/overview` |

- 이 구간에 **`/api/auth/refresh` 요청이 0건**이다 ⇒ 두 새로고침 모두 접근 쿠키가 살아 있었다(인증 분기 = 306 의 live 경로). 삭제가 실제로 적용되지 않았거나, 지운 뒤 정지가 끝나기까지 ~2분 동안 데모가 켜져 있어 갱신으로 다시 받은 것이다(어느 쪽인지는 못 가른다).
- ⇒ 306 의 «서로 다른 두 번의 꺼짐 → 샘플 셸» 은 라이브로 한 번 더 확인됐지만, **305 고유 경로(갱신 실패 → `/login?error=session_expired` → `live` 없는 라우트 → `demo_ended_session_cleared`)는 타지 않았다.**

**다음 창의 순서 — 바꾼다** (위 20차 절의 순서는 쿠키가 다시 발급될 틈을 남긴다)
1. 콘솔 로그인 → 2. **데모를 먼저 정지**(EC2 stopped + `/status` stopped 확인) → 3. 화면을 건드리지 않은 채 `console_access_token` **행 삭제**(Application → Cookies → 행 선택 → 도구 막대 `✕`, 표에서 사라졌는지 확인 · `console_refresh_token` 은 남긴다) → 4. 새로고침.
- 데모가 이미 꺼진 뒤라 갱신이 반드시 실패한다. 판정 = Vercel 로그의 `idle_refresh_*` 실패 이벤트 → `demo_ended_session_cleared` · URL `signed_out=demo_stopped` · 샘플 셸 안내.
