# Task ID

TASK-PC-FE-306

# Title

로그인 쿠키가 아직 살아 있을 때 데모가 꺼져도 세션을 끝내고 **샘플 셸**로 보낸다 (PC-FE-305 경로 D)

# Status

ready (2026-10-04 UTC)

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

- [ ] **AC-0** — 착수 시점(305 머지 후) 레이아웃 가드·`DemoBackendNotice`·계약 § 2.6.1/§ 2.6.2 의 file:line 을 다시 재고, 위 § 제약의 TTL·호출 지점 사실이 여전히 참인지 확인한다. 계약 문장을 **먼저** 고친다.
- [ ] **AC-1** — 액세스·운영자 쿠키가 살아 있고 데모 상태 신호가 `unavailable` 이면, 방문자는 세션 쿠키가 지워진 채 샘플 셸에 착지하고 305 와 같은 안내(`data-testid="demo-signed-out-notice"`)를 본다. 판정은 305 의 `sessionEndDestination` 하나 — 두 번째 판정을 만들지 않는다.
- [ ] **AC-2 (대조군)** — `running`·`starting`·`not-demo` → 오늘 그대로 인증 셸(리다이렉트 없음, 쿠키 무변경). `starting` 은 `DemoBackendNotice` 의 「켜지는 중」 배너 그대로.
- [ ] **AC-3** — 레이아웃이 컨트롤 플레인을 요청마다 부르지 않는다: 같은 렌더에서 가드와 `DemoBackendNotice` 가 해석기 스냅샷을 공유함을(또는 TTL 안의 두 번째 요청이 `/status` 를 다시 부르지 않음을) 테스트로 고정하고, `not-demo` 에서 `/status` 호출 0 을 고정한다.
- [ ] **AC-4** — PC-FE-299/305 보장(Query 캐시 비움 · no-store · bfcache · `demo_checked` 루프 상한) 유지, 기존 테스트 초록. `scripts/check-client-graph-backend-origins.mjs` rc=0(클라이언트는 `session-end-params.ts` 만 import — 305 CI 수정 참조).
- [ ] **AC-5** — 판정 칸 단위 테스트 + bite(가드의 분기 뒤집기 → 정확히 그 칸들만 빨강).
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
