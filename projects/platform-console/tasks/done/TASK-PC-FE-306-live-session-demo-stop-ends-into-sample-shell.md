# Task ID

TASK-PC-FE-306

# Title

로그인 쿠키가 아직 살아 있을 때 데모가 꺼져도 세션을 끝내고 **샘플 셸**로 보낸다 (PC-FE-305 경로 D)

# Status

done (2026-10-04 UTC — 로컬 게이트 전부 통과: lint/tsc/vitest 337파일·3799시험/next build rc=0, 클라이언트 그래프·해석기 사본 가드 rc=0, bite 확인. 소유자 결정 ①② 반영(두 번 연속 · 계약 먼저). AC-6 라이브 ⚪)

# Owner

frontend

# Task Tags

- code
- frontend
- console-web
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 인증 셸 가드에 데모 상태 판정을 더하되 «요청마다 컨트롤 플레인 왕복 없음» 을 지켜야 하고, 계약 § 2.6.1 의 문장 하나를 먼저 바꿔야 한다.

---

# Dependency Markers

- 선행: `TASK-PC-FE-305`(review — 판정 `sessionEndDestination` · 세션 종료 라우트 `GET /api/auth/demo-ended` · 샘플 셸 안내 `DemoSignedOutNotice` 를 만든 티켓). 🔴 305 가 머지된 뒤 착수한다 — 이 티켓은 그 셋을 **재사용**한다.
- 후속: 없음.

# Goal

소유자 결정(2026-10-04 UTC, 원문): «로그인 쿠키가 아직 살아 있을 때(Path D) 데모가 꺼져도 샘플 화면으로 보낸다 (Recommended)».

PC-FE-305 는 세션을 **갱신할 수 없게 된 뒤**(갱신 실패 / 401)의 착지만 바꿨다. 액세스·운영자 쿠키가 아직 살아 있는 동안(최대 30분 — 액세스 쿠키 `maxAge = expires_in` 1800s) 데모가 꺼지면 `(console)/layout.tsx` 의 `isAuthenticated()`(쿠키만 본다)가 참이라 갱신도 401 도 일어나지 않고, 방문자는 빈 데이터의 인증 셸 + `DemoBackendNotice`(「데모 서버가 꺼져 있어 로그인과 운영 데이터를 사용할 수 없습니다」)를 본다. 이 경우에도 305 와 **같은** 세션 종료 → 샘플 셸 + 같은 안내로 끝낸다.

# Scope

## In Scope

- `(console)/layout.tsx` 인증 분기: `isAuthenticated()` 참일 때 데모 상태 신호가 `sessionEndDestination(state) === 'sample'` 이면 `redirect('/api/auth/demo-ended?redirect=<x-pathname>')`(레이아웃은 쿠키를 못 지운다 — 305 의 라우트가 신호를 다시 읽고 지운다).
- 계약 § 2.6.1 / § 2.6.2 의 해당 문장을 **먼저** 고친다(아래 § 제약).
- 단위 테스트 + bite.

## Out of Scope

- 공유 해석기(`infra/demo/backend-resolver`) 변경 — TTL·타임아웃 포함.
- `unavailable` 의 「정지」/「`/status` 실패」 구분 — 305 소유자 결정 ①로 알려진 한계로 닫혔다(이 티켓도 같은 한계를 물려받는다 — § Edge Cases).
- 샘플 방문자 분기(쿠키 없음) — `DemoHeartbeat`/`DemoBackendNotice` 를 안 다는 ADR-MONO-074 의 결정은 그대로.

# 제약 — 레이아웃이 요청마다 컨트롤 플레인을 부르면 안 된다

- 신호는 `resolveDemoBackendState()`(`shared/config/demo-backend.ts` → `@demo/backend-resolver`). 해석기는 **프로세스(서버 인스턴스)마다** `/status` 스냅샷을 **15초** 캐시한다(`infra/demo/backend-resolver/src/index.ts:142` `CACHE_TTL_MS = 15_000`; 왕복 상한 `:132` `STATUS_TIMEOUT_MS = 2_000`). 즉 한 인스턴스가 컨트롤 플레인에 가는 횟수는 «요청 수» 가 아니라 «15초당 최대 1회» 다.
- 🔵 그리고 인증 셸은 **이미** 매 렌더 이 함수를 부른다: `(console)/layout.tsx:240` 의 `<DemoBackendNotice />` 가 `resolveDemoBackendState()` 를 호출한다. 가드가 같은 함수를 같은 요청에서 부르면 **같은 캐시 스냅샷**을 읽으므로 추가 왕복은 0 이다(캐시 미스 시에도 같은 렌더 안에서 1회). → «요청마다 네트워크 호출 없음» 의 **의도**(가드가 비용·지연을 더하지 않는다)는 지켜진다.
- 🔴 그러나 계약의 **문장**은 그대로 참이 아니게 된다: `console-integration-contract.md:3583` «The guard itself still makes **no network call**.» — 이 티켓 이후 가드는 인증 분기에서 캐시된 데모 상태(15초에 최대 1회 `/status`)를 읽는다. 구현 **전에** 이 문장을 «the guard makes no network call of its own, except reading the cached demo-state snapshot (§ 2.6.2; at most one control-plane round trip per server instance per 15 s, the same snapshot `DemoBackendNotice` already reads)» 류로 고치고 § 2.6.2 «Not covered (a)» 를 이 티켓 참조로 바꾼다. `:3553` 의 «with no network call» 은 리프레시 쿠키 **없는** 방문자 문장이라 바뀌지 않는다(그 분기는 인증 분기가 아니다) — 착수 시 다시 확인.
- 🔴 `not-demo`(로컬·CI·비데모 배포)에서는 해석기가 컨트롤 플레인을 아예 부르지 않는다(`index.ts:243-244`) — 이 경로의 비용은 0 이어야 하고 테스트로 고정한다.

# Acceptance Criteria

- [x] **AC-0** — 착수 시점(305 머지 후) 레이아웃 가드·`DemoBackendNotice`·계약 § 2.6.1/§ 2.6.2 의 file:line 을 다시 재고, 위 § 제약의 TTL·호출 지점 사실이 여전히 참인지 확인한다. 계약 문장을 **먼저** 고친다.
- [x] **AC-1** — 액세스·운영자 쿠키가 살아 있고 데모 상태 신호가 `unavailable` 이면, 방문자는 세션 쿠키가 지워진 채 샘플 셸에 착지하고 305 와 같은 안내(`data-testid="demo-signed-out-notice"`)를 본다. 판정은 305 의 `sessionEndDestination` 하나 — 두 번째 판정을 만들지 않는다. — 🟢 소유자 결정 ①로 «서로 다른 두 번의 연속 `unavailable`» 뒤에 (§ Design · § Implementation Record).
- [x] **AC-2 (대조군)** — `running`·`starting`·`not-demo` → 오늘 그대로 인증 셸(리다이렉트 없음, 쿠키 무변경). `starting` 은 `DemoBackendNotice` 의 「켜지는 중」 배너 그대로.
- [x] **AC-3** — 레이아웃이 컨트롤 플레인을 요청마다 부르지 않는다: 같은 렌더에서 가드와 `DemoBackendNotice` 가 해석기 스냅샷을 공유함을(또는 TTL 안의 두 번째 요청이 `/status` 를 다시 부르지 않음을) 테스트로 고정하고, `not-demo` 에서 `/status` 호출 0 을 고정한다.
- [x] **AC-4** — PC-FE-299/305 보장(Query 캐시 비움 · no-store · bfcache · `demo_checked` 루프 상한) 유지, 기존 테스트 초록. `scripts/check-client-graph-backend-origins.mjs` rc=0(클라이언트는 `session-end-params.ts` 만 import — 305 CI 수정 참조).
- [x] **AC-5** — 판정 칸 단위 테스트 + bite(가드의 분기 뒤집기 → 정확히 그 칸들만 빨강).
- [ ] **AC-6 (라이브, ⚪)** — 다음 데모 창: 로그인 → 30분 안에 데모 정지 → 콘솔 새로고침 → 안내가 붙은 샘플 셸.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.6.1(`:3583`) · § 2.6.2
- `projects/platform-console/specs/services/console-web/architecture.md` § Auth Flow 5a · 5b
- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md` A1 · A7
- `projects/platform-console/tasks/review/TASK-PC-FE-305-demo-stop-ends-session-into-sample-shell.md`(머지 후 `done/`)
- `infra/demo/backend-resolver/src/index.ts`(캐시 TTL · `not-demo` 단락)

# Related Contracts

- `console-integration-contract.md` § 2.6.1 · § 2.6.2 (문장 수정 — 외부 API 변경 없음)

# Target App

- `apps/console-web`

# Edge Cases

- `/status` 실패 → 해석기가 `unavailable` → 살아 있는 세션도 샘플로 끝난다. 305 결정 ①과 같은 한계지만 **여기서는 영향이 더 크다**: 305 는 갱신 실패/401 뒤에만 돌았고, 이 티켓은 **멀쩡한 세션**에도 돈다. 컨트롤 플레인 일시 장애(2초 타임아웃) 한 번이 로그인한 운영자를 로그아웃시킬 수 있다 — 착수 시 이 위험을 소유자에게 다시 확인하거나(예: 같은 판정 2회 연속일 때만), 해석기 분리(305 Owner decisions ① 원안)를 선행으로 둘지 정한다.
- 라우트(`/api/auth/demo-ended`)가 다시 읽은 신호가 `unavailable` 이 아니면 `/login?error=session_expired&demo_checked=1` 로 간다 — 쿠키가 살아 있는 방문자에게는 이 착지가 틀리다(세션은 유효하다). 이 경로에서는 라우트가 「불일치 → 원래 화면」으로 돌려보내야 하는지 설계에서 정한다(인증 셸 ↔ 라우트 핑퐁 상한 포함).
- 샘플 방문자 분기에는 가드를 걸지 않는다(쿠키가 없으므로 끝낼 세션이 없다).

# Failure Scenarios

- 가드가 해석기를 우회해 `/status` 를 직접 부르면 `check-demo-resolver-copies.sh` 의 취지(앱이 자기 구현을 갖지 않는다)를 깨고, 캐시도 못 탄다.
- 가드가 `DemoBackendNotice` 와 다른 캐시 인스턴스를 쓰면 같은 렌더에서 「꺼짐」 리다이렉트와 「켜짐」 화면이 갈린다.
- 계약 문장을 안 고치고 구현하면 § 2.6.1 이 거짓이 된다(스펙이 코드보다 우선).

---

# Owner decisions (2026-10-04 UTC, 원문 그대로)

1. «두 번 연속 '꺼짐'일 때만 (Recommended)» ⇒ 아직 작동하는 세션은 **서로 다른 두 번의 연속 `unavailable`**(해석기 15초 캐시 창보다 멀리 떨어져 같은 캐시 스냅샷일 수 없는)일 때만 샘플 셸로 끝낸다. 판독 하나(실패한 `/status` 하나 포함)는 작동하는 세션을 절대 로그아웃시키지 않는다. 공유 해석기(`infra/demo/backend-resolver`)는 바꾸지 않고 console-web 안에서 처리한다.
2. «바꾼다 (Recommended)» ⇒ 계약 문장을 **먼저**(코드보다 앞선 별도 커밋) 고친다: 세션 가드는 셸이 렌더마다 이미 읽는 같은 15초 데모 상태 스냅샷을, 데모 배포에서만 읽는다. 비데모 배포에서는 컨트롤 플레인 호출 0. «비데모 `/status` 호출 0» 과 «셸의 기존 판독 외 추가 왕복 없음» 을 테스트로 고정. § 2.6.2 «Not covered (a)» 를 그에 맞게 고친다.

# Design (2026-10-04 UTC)

## AC-0 재측정 (305 머지 후, origin/main `f0927bcd0`)

- 가드: `apps/console-web/src/app/(console)/layout.tsx:146-155`(샘플 → 인증 판정 → 갱신/로그인 홉), `DemoBackendNotice` 마운트 `:240`(인증 분기에서만). 가드 자체는 네트워크 호출 없음 — 이 티켓 전까지 참.
- 해석기: `infra/demo/backend-resolver/src/index.ts:132` `STATUS_TIMEOUT_MS = 2_000`, `:142` `CACHE_TTL_MS = 15_000`, `:210` 캐시 판정 `now - cache.at < CACHE_TTL_MS`, `:231` **실패한 `/status` 도 캐시된다**(`value: null`), `:243-244` `DEMO_API_BASE` 없으면 `not-demo` 를 네트워크 없이 반환, `:246` 값 없음 → `unavailable`. § 제약의 사실 전부 여전히 참.
- 계약: § 2.6.1 «The guard itself still makes **no network call**.»(`:3583`), § 2.6.2 «Not covered (a)»(`:3614`). `:3553` «with no network call» 은 리프레시 쿠키 **없는** 비인증 분기 문장 — 이 티켓의 가드는 인증 분기에만 서므로 그대로 참(안 고침).
- 305 의 라우트 `GET /api/auth/demo-ended` 는 신호 한 번(`unavailable`)으로 세션을 지운다. 이 티켓은 그 경로(로그인 착지 → 라우트)를 **바꾸지 않는다**.

## 왜 표식 쿠키인가 — 그리고 왜 라우트가 쓰는가

- 콘솔은 서버리스(Vercel)라 인스턴스 메모리는 요청 사이에 믿을 수 없다 → «첫 `unavailable` 판독 시각»은 방문자의 브라우저에 둔다: `console_demo_stop_check = <firstUnavailableAt>.<checkedAt>`(epoch ms, `0` = 대기 중인 판독 없음, ~27바이트).
- 레이아웃(서버 컴포넌트)은 쿠키를 못 쓴다(§ 2.6.1 의 같은 이유) ⇒ 쓰는 자리는 305 의 라우트 하나. 레이아웃은 «라우트에 가야 하는가» 만 정한다.
- 속성: `HttpOnly` · `SameSite=Lax` · `Secure`(세션 쿠키와 같은 `tokenCookieOpts`) · `Path=/`(읽는 쪽이 모든 콘솔 경로의 레이아웃이라 경로를 좁힐 수 없다) · `Max-Age=600`(10분 지난 첫 판독은 «연속»의 증거가 아니다 — 판정에서도 10분 초과는 버린다).
- **위조**: 표식은 자격이 아니다. 위조·재생된 표식이 할 수 있는 최대치는 (가) 방문자 **자신의** 세션을 진짜 `unavailable` 판독 **한 번** 뒤에 끝내게 하는 것(라우트는 여전히 신호를 다시 읽는다 — 신호가 다른 말을 하면 아무것도 안 지운다), (나) 그 끝을 미루는 것(오늘의 동작). 다른 방문자에게는 닿지 않는다(쿠키는 그 방문자의 항아리에 있다). 서명은 붙이지 않았다 — 지킬 자산이 «자기 로그아웃» 뿐이다.

## «서로 다른» 의 정의

해석기는 인스턴스마다 스냅샷 하나를 15초 캐시한다(실패도 캐시). 라우트는 판독을 **시작한 시각**과 **끝난 시각**을 따로 잰다. 첫 판독의 표식 = 끝난 시각 `f`(그 스냅샷은 `f` 이전에 받아졌다). 두 번째 판독의 시작 `s` 가 `s − f > 15 000ms` 이면, 그 판독이 읽는 스냅샷(어느 인스턴스든)은 해석기의 캐시 술어상 `s − 15 000 > f` 이후에 받아진 것이다 ⇒ 첫 판독과 **다른 왕복**이다. 콘솔 쪽 상수 `DEMO_STATE_SNAPSHOT_TTL_MS = 15_000` 은 해석기 상수의 사본이므로, 테스트가 **실제 해석기**로 «그 상수만큼 떨어지면 다시 가져온다» 를 고정한다(해석기 TTL 이 늘면 빨강).

## 흐름

- 가드(인증 분기, 데모 배포에서만 — `not-demo` 면 쿠키도 안 읽고 끝): 판독이 `sessionEndDestination(state) === 'sample'`(305 의 유일한 판정 — 두 번째 판정 없음) →
  - 라우트가 15초 안에 답했다(표식 `checkedAt`; 표식이 없으면 URL 의 `demo_checked=1`) → 그대로 셸(홉 상한);
  - 아니면 `GET /api/auth/demo-ended?live=check&redirect=<path>`.
  - 다른 판독 + 표식에 대기 중인 첫 판독 → `?live=clear`(연속 깨짐). 그 밖 → 오늘 그대로.
- 라우트 live 모드(`live` 파라미터 **그리고** 액세스·운영자 쿠키 있음 — 아니면 305 핸들러 그대로): `clear` → 첫 판독 0(신호 안 읽음). `check` → 신호 재판독: 꺼짐 아님 → 첫 판독 0; 꺼짐 + 첫 판독이 15초 초과·10분 이내 → `clearFullSession` + 표식 삭제 → `<target>?signed_out=demo_stopped`(305 의 같은 착지·안내); 꺼짐 + 그 밖 → 첫 판독 기록(대기 중이면 유지). 끝내지 않는 모든 결과는 `checkedAt` 을 적고 **요청한 화면**으로 `demo_checked=1` 을 달아 돌려보낸다 — `/login` 이 아니다(세션은 작동한다, Edge Case 2).
- 홉 상한: 돌아온 화면은 15초 안에 다시 `check` 하지 않고, `clear` 는 대기 판독을 항상 비우므로, 한 내비게이션이 라우트에 닿는 횟수는 최대 2(check → clear). 브라우저가 표식을 거부해도(쿠키 `Secure` 불일치 등) URL 의 `demo_checked=1` 이 한 홉에서 끊는다 — 그때는 두 판독 규칙이 성립할 수 없으므로 오늘의 동작(셸 + `DemoBackendNotice`)으로 남는다.
- 왜 `live` 파라미터가 모드를 고르는가(쿠키만으로가 아니라): 305 의 경로 B(백엔드 401 → `/login` → 라우트)는 쿠키가 살아 있는 채로 라우트에 온다. 그 방문자를 «돌려보내기» 로 다루면 401 화면 ↔ 라우트가 핑퐁한다. 305 경로는 그대로 두고 레이아웃이 보낸 홉만 live 모드로 다룬다. 남는 노출은 305 와 같다(§ Owner follow-ups).

## 왕복 비용

- 가드는 `DemoBackendNotice` 와 **같은 모듈 인스턴스**의 `resolveDemoBackendState` 를 같은 요청에서 부른다 ⇒ 같은 캐시 스냅샷, 추가 왕복 0(캐시 미스여도 그 렌더에서 1회 — 테스트로 고정). 카탈로그 호출(`resolveBackendUrl`)도 같은 스냅샷을 기다리므로 지연도 더하지 않는다.
- 비데모: 해석기가 `/status` 를 안 부르고, 가드는 쿠키도 안 읽는다(테스트로 고정).

# Implementation Record (2026-10-04 UTC)

## 커밋 순서

1. `docs(platform-console)` — 계약 § 2.6.1 문장(«no network call» → «no network call of its own; 데모 배포에서만 같은 15초 스냅샷; 비데모 호출 0»), § 2.6.2 «Live session» 블록(«Not covered (a)» 대체), `architecture.md` Auth Flow 5c. **코드보다 먼저**(소유자 결정 ②).
2. `fix(platform-console)` — 코드 + 테스트.

## 코드 (모두 `apps/console-web/`)

- `src/shared/lib/live-session-demo-stop.ts`(신규, 서버 전용) — 표식 형식·속성, 순수 판정 둘(`liveShellDemoStopAction` 레이아웃 홉 · `liveRouteDemoStopOutcome` 두 번 연속 규칙), 레이아웃용 `liveSessionDemoStopRedirect`. 「꺼짐인가」 는 305 의 `sessionEndDestination` 그대로 호출.
- `src/app/(console)/layout.tsx` — 인증 분기(샘플 방문자 제외)에서 `liveSessionDemoStopRedirect(x-pathname)` → 홉이면 `redirect`. `not-demo` 면 쿠키도 안 읽고 `null`.
- `src/app/api/auth/demo-ended/route.ts` — live 모드(`live=check|clear` + 살아 있는 쿠키) 추가. 그 밖은 305 핸들러 **무변경**(끝낼 때 표식이 있으면 함께 지우는 한 줄만 추가).
- 클라이언트 컴포넌트 변경 0 — 새 모듈은 서버 전용(레이아웃·라우트만 import). 클라이언트 그래프 가드 `reached=424 hits=0`.

## 테스트

- `tests/unit/live-session-demo-stop.test.tsx`(신규, 32칸) — ① 판정표(첫 꺼짐 → 머묾·표식 기록 / 15초 넘게 떨어진 두 번째 → 쿠키 6종·표식 삭제 + 샘플 안내 / 캐시 창 안 두 번째 → 홉 없음 / 정확히 15초째 라우트 직접 호출 → 안 끝남 / 꺼짐→켜짐 → 표식 비움, 다음 꺼짐은 다시 «첫» / 꺼짐→starting 도 깸 / 10분 넘은 첫 판독 → 다시 셈 / 순수 함수 4값) ② 대조군 `running`·`starting`·`not-demo`(홉 0 · 쿠키 무변경 · `not-demo` 는 쿠키 읽기 0) ③ 홉 상한(엇갈리는 신호 3패턴 × 6스텝 → 라우트 방문 ≤ 2 · 표식 거부 → 한 홉 · 같은 URL 새로고침이 두 번째 판독이 됨 · 불일치 착지 = 요청 화면, `/login` 아님 · 전 응답 no-store) ④ 305 보존(`live` 없음 → 한 번에 끝 · 죽은 쿠키 + `live` → 305 · 305 불일치 착지 그대로) ⑤ 표식 속성·형식 오류·위조 범위 ⑥ **실제 레이아웃**(꺼짐 → live 홉 · 대조군 3종 → 가드 통과 · 샘플 방문자 → 신호 미호출).
- `tests/unit/live-session-demo-stop-resolver.test.ts`(신규, 6칸, **실제 해석기**) — 비데모 `/status` 0·쿠키 읽기 0 / 가드 + `DemoBackendNotice` 한 렌더 = `/status` 1회 / TTL 안 다음 요청 재호출 없음 / 콘솔 상수만큼 떨어지면 새 왕복(해석기 TTL ≤ 콘솔 상수 — 해석기 TTL 이 늘면 빨강) / **실패한 `/status` 하나 → 머묾, 다음 켜짐 → 표식 비움** / 대조군: 두 번 연속 실패 → 샘플.
- 기존 305·299·레이아웃 스위트 무수정 초록.

## Bite

`liveRouteDemoStopOutcome` 의 두 판독 요구를 끔(`if (true || (pending && age > TTL)) return { end: true }` — 꺼짐 판독 하나로 끝냄) → 새 두 파일 + 305 스위트 61칸 중 **13 빨강 · 48 초록**. 빨강: 첫 꺼짐 → 머묾, **실패한 `/status` 하나 → 머묾**, 캐시 창 안 두 번째, 정확히 15초째, 꺼짐→켜짐/starting, 10분 초과, 형식 오류 표식, 표식 거부, 두 번째 → 샘플(첫 판독에서 이미 끝나 착지 경로가 달라짐), no-store 체인, 표식 속성(첫 판독에서 이미 끝나 표식이 안 써짐), 두 번 연속 실패 대조군(같은 이유). 초록으로 남은 것: 305 스위트 전부(판정 무관 — 의도), 대조군 3종(꺼짐을 안 거침), 순수 함수 4값 칸(만료된 표식을 넣으므로 어느 쪽이든 end), 305 보존 칸, 위조 칸(어차피 끝남을 기대). 원복 후 61/61.

## 검증 (2026-10-04 UTC, `apps/console-web`, 각 명령 단독 실행 · rc 직접 읽음)

- `pnpm install --frozen-lockfile` rc=0
- `pnpm lint` rc=0 («No ESLint warnings or errors»)
- `npx tsc --noEmit` rc=0
- `npx vitest run --maxWorkers=4 --minWorkers=1` rc=0 — 337 파일 / 3799 시험
- `npx next build` rc=0 (`ƒ /api/auth/demo-ended` 생성; 경고는 기존 OpenTelemetry `winston-transport` 미해결 import 뿐)
- 리포 루트: `node scripts/check-client-graph-backend-origins.mjs` rc=0(console-web `reached=424 hits=0`), `bash scripts/check-demo-resolver-copies.sh` rc=0

## Owner follow-ups (이 티켓이 정하지 않은 것)

1. **305 경로는 여전히 판독 하나로 끝낸다** — `live` 없이 라우트에 오는 홉(`/login?error=session_expired` 착지 = 갱신 실패·백엔드 401 뒤, 또는 손으로 만든 링크)은 305 핸들러 그대로다. 쿠키가 살아 있는 채 그 길로 오는 경우(경로 B — 백엔드 401)와 교차 사이트 최상위 GET(쿠키 `SameSite=Lax` 라 실린다)으로 라우트를 직접 여는 경우, `/status` 실패 **한 번**이 그 세션을 끝낼 수 있다(305 이후 존재하던 노출 — 라우트가 신호를 다시 읽으므로 신호가 `unavailable` 일 때만). 좁히려면 «살아 있는 쿠키면 `live` 와 무관하게 두 번 규칙» 으로 바꾸면 되지만, 그러면 경로 B 의 첫 착지가 PC-FE-299 문구(로그인 벽)가 된다 — 그래서 이 티켓은 305 를 그대로 뒀다.
2. **자동 재확인 없음** — 첫 판독 뒤 방문자는 인증 셸 + `DemoBackendNotice` 를 보고, 15초 넘게 지난 **하드 내비게이션/새로고침**에서 두 번째 판독이 일어난다(App Router 의 소프트 내비게이션은 레이아웃을 다시 렌더하지 않는다). 배너가 떠 있는 동안 16초 뒤 `router.refresh()` 하는 클라이언트 위젯을 달면 자동이 된다 — 범위 밖으로 뒀다.
3. **콘솔의 TTL 사본** — `DEMO_STATE_SNAPSHOT_TTL_MS` 는 해석기 `CACHE_TTL_MS` 의 사본이다(해석기 변경 금지). 실제 해석기로 고정하는 칸이 있다(해석기 TTL 이 늘면 빨강); 해석기가 TTL 을 export 하면 사본을 지울 수 있다(루트 티켓 감).
4. AC-6 라이브 ⚪.

---

## CORRECTION (2026-10-05 UTC) — 20차 창 판정 (2026-10-04 UTC · i-0c4859442f56d70e0 · ami-0d78d476824493d77 · f0927bcd0)

> 위 AC-6 의 `[ ]` 와 Owner follow-ups 4 «AC-6 라이브 ⚪» 는 **이제 사실이 아니다.** 동결 파일이라 체크박스는 고치지 않고 여기서 닫는다. 이 절이 현재 상태다. 분석=Opus 5.5.

**배포 확인.** 콘솔(Vercel) 변경이다. 창 시점 Vercel 콘솔 = main ≥ `60b97d178` = 이 티켓의 머지 커밋(#4150) ⇒ live 모드(`live=check|clear`)가 배포돼 있었다.

### AC-6 — 로그인 → 30분 안에 데모 정지 → 콘솔 새로고침 → 안내가 붙은 샘플 셸

| 시각 (UTC) | 무엇 | 관측 |
|---|---|---|
| (창 중) | 소유자 콘솔 로그인 | 세션 살아 있음(액세스 쿠키 30분 안) |
| 15:46:15 | 데모 정지 | `POST /stop` |
| 15:48:08 | EC2 stopped · `/status` = `stopped` | |
| 15:49:16.91 | **첫 새로고침** — Vercel `GET /api/auth/demo-ended` | `demo_ended_live_session_kept` · `intent=check` · `state=unavailable` · `pending=true` ⇒ 첫 판독은 **세션을 끝내지 않았다**(소유자 결정 ①) |
| 15:49:20 | 첫 새로고침의 화면 | 로그인된 셸 + 데모 꺼짐 배너 · 카드 전부 `DOWNSTREAM_ERROR` |
| 15:49:40 | **두 번째 새로고침**(~24 s 뒤 — 해석기 15 s 캐시 창 밖) — Vercel `GET /api/auth/demo-ended` | `demo_ended_live_session_cleared` · `state=unavailable` |
| 같은 때 | 두 번째 새로고침의 화면 | **샘플 셸** — 테넌트 `sample` · 기준 시각 2026-09-15T11:00:00Z 샘플 데이터 · 안내 «데모 서버가 종료되어 로그아웃되었습니다. 지금은 샘플 데이터로 둘러보는 중입니다 — …»(305 의 `DemoSignedOutNotice`) |

⇒ **AC-6 ✅.** AC 문장의 순서 그대로 끝났고, 끝나는 방식이 이 티켓의 설계(«서로 다른 두 번의 연속 `unavailable`» — § Design)와 정확히 맞는다: 첫 판독 `kept` + 표식 기록(`pending=true`) → 15 s 넘게 떨어진 두 번째 판독 `cleared` → 305 의 같은 착지·안내. 두 판독 간격 ~24 s 는 `DEMO_STATE_SNAPSHOT_TTL_MS`(15 s)보다 크고 표식 `Max-Age`(600 s)보다 작다.
- 🔵 Owner follow-up 2(«자동 재확인 없음 — 두 번째 판독은 하드 새로고침에서») 도 라이브로 그대로 보였다: 첫 새로고침 뒤 방문자는 꺼짐 배너가 붙은 인증 셸에 머물렀고, 두 번째 **새로고침**에서야 끝났다.
- ⚪ 이 창은 `/status` **실패**(컨트롤 플레인 장애) 판독을 만들지 않았다 — 두 판독 모두 진짜 정지에서 나온 `unavailable` 이다. «실패 한 번은 세션을 끝내지 않는다» 는 단위 시험(`live-session-demo-stop-resolver.test.ts`)이 고정한 그대로다.

### 4차원 (close chore)

| 차원 | 결과 |
|---|---|
| (a) `gh pr view 4150` | `state=MERGED` · mergedAt 2026-10-04T14:09:05Z · mergeCommit `60b97d178` |
| (b) origin/main 조상 | 참(origin/main `92a6320eb`) |
| (c) 머지 시점 실패 체크 | `statusCheckRollup` 65건 = SUCCESS 15 · SKIPPED 50 · **FAILURE 0** |
| (d) `# Acceptance Criteria` | AC-0 ~ AC-5 `[x]`(본문) · **AC-6 = 이 절에서 닫힘** — 동사 «로그인 → 데모 정지 → 새로고침 → 안내가 붙은 샘플 셸» 을 위 표로 확인 |

⇒ **`review/` → `done/`.**
