# Task ID

TASK-FAN-FE-027

# Title

fan-platform-web 도 같은 모양의 refresh 경합 해저드를 갖고 있다 — `ecommerce web-store` 와 동일한 in-process-dedupe 가정

# Status

review

# Owner

fan-platform

# Task Tags

- auth
- bug

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — `TASK-FE-106` 과 같은 레이어 판단이 필요하다.

---

# Dependency Markers

- 출처: `TASK-MONO-764` 23차 창 — `ecommerce web-store` 의 라이브 결함(`TASK-FE-106`)을
  조사하는 중 **코드 형태 일치**로 발견했다. 🔴 **fan-platform-web 자체는 이번 창에서
  라이브로 재현되지 않았다(n=0)** — 이 티켓은 코드 비교에서 나왔다.
- **선행으로 읍을 것: `TASK-FE-106`.** 같은 근본 원인(NextAuth `jwt` 콜백의 in-process
  refresh dedupe 가 Vercel 서버리스 다중 인스턴스를 못 넘는다)이므로, `TASK-FE-106` 이
  고른 해법을 **먼저 보고** 이 저장소의 관례(형제 grep, `CLAUDE.md` 10단계)대로 같은
  모양을 복사하는 것이 우선이다 — 독립적으로 다른 해법을 발명하지 않는다.

---

# 배경 — 코드 대조 (2026-10-06 UTC)

`projects/fan-platform/web/fan-platform-web/src/shared/auth/auth-callbacks.ts:116-122`:

```ts
/**
 * The `jwt` callback body — persists tokens on sign-in and performs proactive
 * silent refresh on subsequent calls (Phase 4.5 F3). Pure: only `fetch`
 * (mockable) and `Date.now` as side inputs.
 *
 * NextAuth serialises the `jwt` callback per session token, providing
 * in-flight deduplication of refresh calls.
 */
export async function jwtCallback({ token, account, profile, user }: JwtCallbackArgs) {
```

이것은 `ecommerce web-store` 의 `auth-callbacks.ts:174` 주석(`TASK-FE-106` 배경 참조)과
**문구까지 거의 동일**하다 — 둘 다 "NextAuth 가 `jwt` 콜백을 세션 토큰당 직렬화하므로
그것이 dedupe 지점이다" 라고 적고 있고, 둘 다 그 직렬화가 **한 Node 프로세스 안에서만**
성립한다는 전제를 놓치고 있다. 두 앱 다 Vercel 에 배포되므로, `TASK-FE-106` 이 겪은
다중 인스턴스 경합(유휴 뒤 복귀 시 병렬 요청 → 각 인스턴스가 독립적으로 refresh 시도 →
iam 유예창 내 재사용 거절 → 세션 붕괴)이 fan-platform-web 에서도 같은 코드 모양으로
일어날 수 있다.

---

# Goal

fan-platform-web 의 세션 refresh 가 다중 서버리스 인스턴스 경합에서도 세션을 깨뜨리지
않는다 — `TASK-FE-106` 의 결론과 같은 보증을 fan 쪾에도 둔다.

---

# Scope

## In Scope

- 착수 시 **먼저 `TASK-FE-106` 의 상태/결론을 확인**한다. 그 티켓이 아직 `ready`/
  `in-progress` 라면 그 결과를 기다리거나 같은 조사를 병행하되, **같은 결론에
  수렴시킨다**(두 번 다른 해법을 발명하면 쌍둥이 코드가 서로 다른 모양으로 갈라진다).
- fan-platform-web 에서 같은 재현 절차(유휴 뒤 복귀 + 동시 요청)를 시도한다.
- 재현되면(또는 안 되더라도 방어적으로) `TASK-FE-106` 이 고른 패턴을 fan-platform-web 의
  `jwt` 콜백 구조에 맞게 적용한다.

## Out of Scope

- `TASK-FE-106` 과 다른 독자적인 해법 설계 — 같은 메커니즘이면 같은 해법을 복사한다
  (다르다고 판단되면 그 이유를 이 티켓에 적을 것).
- iam 유예창 정책 변경(`TASK-BE-606`/`608`) — `TASK-FE-106` 과 동일하게 범위 밖.

---

# Acceptance Criteria

- [x] **AC-0 (재측정 + 선행 확인)** — `auth-callbacks.ts:116-122` 주석은 착수 시점에도
      그대로 「NextAuth serialises the `jwt` callback per session token, providing in-flight
      deduplication」 이었다(수정함). `TASK-FE-106` = PR #4183(`origin/task-fe-106`, 착수 시
      미머지 · review) — 해법은 아래 § 구현에 옮겨 적었다.
- [ ] **AC-1** — 재현. **로컬(단위 하네스) = 재현됨**: `session-route-refresh-race.test.ts`
      의 🔵 대조군 2칸이 **수정 전 코드에서** 초록(= 결함을 보인다) — ① bare 세션 액션에서
      패자의 `RefreshAccessTokenError` 쿠키가 승자를 덮는다, ② 요청 없는 `auth()` 가 IAM 에서
      `rt-0` 을 회전시키고 쿠키를 버려 다음 읽기가 `rt-0` 을 재전송한다(`['rt-0','rt-0']`).
      **라이브 = ⚪ 오케스트레이터가 다음 창에서 측정** (IAM 스택 필요 — 로그인 → 액세스
      토큰 만료까지 유휴 → 아무 페이지 로드 → auth-service 의 `SAS_REFRESH: replay …
      within the 30s grace window` 또는 60초 뒤 `reuse detected` 유무. 수정 전 배포본에서는
      헤더의 `isAuthenticated()` 와 heartbeat 이 매 요청 refresh 하므로 탭 하나로도 재현될
      것으로 예상 — § 착수 조사 2).
- [x] **AC-2** — `TASK-FE-106` 의 패턴 적용 + fan 구조에 맞춘 차이 1건(`SessionKeeper`) —
      § 구현 · § web-store 와 다른 점.
- [x] **AC-3 (회귀)** — fan vitest 전체 45 files / 401 tests 통과(착수 전 기준 42 / 367,
      새 3파일 + 기존 파일 셀 추가). 팔로우·반응·세션·FE-031 redirect 콜백
      (`auth-redirect-callback.test.ts` 4칸) 전부 초록.

---

# Related Specs

- `projects/iam-platform/tasks/done/TASK-BE-606-sas-reuse-detection-misses-rotated-token-replay.md`
- `projects/iam-platform/tasks/done/TASK-BE-608-reuse-detection-hardening-followups.md`
- `projects/ecommerce-microservices-platform/tasks/ready/TASK-FE-106-store-session-lost-under-concurrent-refresh-on-serverless.md`
  (쌍둥이 티켓 — 먼저 읍을 것)

# Related Contracts

- iam SAS refresh-token 유예창 시맨틱(위 두 티켓이 정의)

---

# Edge Cases

- `TASK-FE-106` 이 이 티켓보다 먼저 닫히면, 이 티켓의 구현은 "복사 + fan 구조로 번역"
  작업이 된다 — 새로운 설계를 하지 않는다.
- 라이브 재현이 이번 창에서 안 될 수 있다(n=0 출발) — AC-1 이 그 경우도 허용한다.

# Failure Scenarios

- **`TASK-FE-106` 의 결론을 안 읍고 독립적으로 고친다** — 같은 메커니즘에 대해 두 코드베이스가
  서로 다른 해법을 갖게 되고, 다음 사람이 "fan 은 왜 다르게 짰나"를 또 조사해야 한다
  (`CLAUDE.md` 10단계가 경고하는 바로 그 패턴).
- **재현을 시도하지 않고 "같은 결함일 것"이라고만 적고 코드를 바꾼다** — AC-1 이 그
  확인을 요구한다.

---

# 구현 · 결정 (2026-10-07 UTC, 브랜치 `task-fan-fe-027`)

## 착수 조사 — fan 코드에서 찾은 기전 (AC-1 의 코드 쪽 절반)

1. 주석의 「NextAuth 가 `jwt` 콜백을 직렬화」 가정은 web-store 와 같이 어디서도 참이 아니다.
2. 🔴 **fan 은 web-store 보다 나쁘다 — refresh 가 일어나는 자리가 «버리는 자리» 뿐이었다.**
   요청 없는 `auth()`(`next-auth@5.0.0-beta.25/lib/index.js:91` — `getSession(h, config)
   .then((r) => r.json())`, Set-Cookie 를 버림)가 세 군데: `middleware.ts:85`,
   `shared/auth/session.ts:72`(`getFanSession`), `:109`(`isAuthenticated` — **헤더가 공개
   페이지 포함 매 렌더**, 그리고 `app/api/demo/heartbeat/route.ts` 가 **60초마다**). 반면
   fan 에는 `/api/auth/session` 을 읽는 클라이언트가 **하나도 없다**(`next-auth/react`·
   `SessionProvider`·`useSession` grep 0건 — web-store 에는 `features/auth/model/
   auth-context.tsx` 의 `SessionProvider` 가 있다). ⇒ 액세스 토큰 만료 뒤 첫 렌더가 `rt-0`
   을 회전시키고 결과를 버리며, 같은 렌더의 다음 `auth()` 가 `rt-0` 을 다시 보내 유예 거절
   → 익명. 탭이 열려 있으면 60초 뒤 heartbeat 이 다시 `rt-0` → **재사용 탐지 → 패밀리 폐기
   + 보안 이벤트**. 다중 인스턴스도 다중 탭도 필요 없다(n=1 로 성립).
   또 `getFanSession()` 은 bearer 를 쿠키에서 직접 읽었으므로, «성공한» refresh 조차 게이트웨이
   호출에 닿은 적이 없다 — fan 의 silent refresh 는 사실상 한 번도 작동하지 않았다.

## 구현 — FE-106 의 ①~④ + fan 에만 필요한 ⑤

| # | 무엇 | 파일 |
|---|---|---|
| ① | 서버 쪽 세션 읽기 셋 전부 decode-only. 판정 = 복호 JWT → `publicSessionFromToken`(`@auth/core` 의 기본 `{ user: { name, email, image } }` + `sessionCallback`) → `hasAuthenticatedUser` **하나**. `auth()` 호출 0 | `src/middleware.ts`, `src/shared/auth/session.ts`, `src/shared/auth/session-token.ts`(신규, web-store 사본) |
| ② | `refreshTokenGrant` 가 `rotation_suspect`(IAM `400 invalid_grant`) / `failed` 분류. 패자는 `error` + 한 호출짜리 `refreshRaceLost` 를 같이 단다. `session` 콜백이 그 표지를 익명 세션에 boolean 으로만 싣는다 | `src/shared/auth/auth-callbacks.ts`, `types.d.ts` |
| ③ | `GET /api/auth/session` 래퍼 — 패자 응답을 Set-Cookie 째 버림 → 2초 → `307 ?refresh_retry=1` → 재시도 홉은 refresh 안 함, 미해결이면 쿠키 삭제 | `src/shared/auth/session-route.ts`(신규, web-store 사본), `src/app/api/auth/[...nextauth]/route.ts` |
| ④ | 대조군(결함 재현) 먼저 초록 → 수정 셀 | `src/__tests__/session-route-refresh-race.test.ts` |
| ⑤ | **`SessionKeeper`** — 인증 헤더에만 마운트되는 클라이언트 컴포넌트. `/api/auth/session` 을 탭 복귀 시·가시 상태 50초마다 읽고, 서버 렌더가 만료 bearer 를 썼으면(`FanSession.accessTokenStale`) 즉시 읽은 뒤 `router.refresh()` | `src/shared/auth/SessionKeeper.tsx`(신규), `src/widgets/header/Header.tsx` |

## web-store 와 다른 점 (Out of Scope 「다르면 이유를 적을 것」)

- **⑤ 만 다르다, 그리고 그것은 해법이 아니라 전제의 차이다.** web-store 는 ①을 해도
  `SessionProvider` 가 `/api/auth/session` 을 읽어 refresh 가 계속 일어난다. fan 은 ①을 하면
  **refresh 를 일으키는 것이 아무것도 없다** — 액세스 토큰이 만료된 채 모든 게이트웨이 호출이
  401. 그래서 FE-106 이 «refresh 는 한 곳» 이라고 정한 바로 그 한 곳(`/api/auth/session`)을
  부르는 최소 클라이언트를 붙였다. 새 refresh 경로를 만든 것이 아니다.
- 주기 50초 = `REFRESH_MARGIN_SECONDS`(60초)보다 짧게 — 보이는 탭에서는 액세스 토큰이 만료
  되기 전에 반드시 한 번 margin 창 안에서 읽는다(`session-keeper.test.tsx` 가 부등식을 고정).
  숨은 탭은 안 읽고, 복귀 시 1회.
- `next-auth/react` `SessionProvider` 를 쓰지 않은 이유: 소비자가 없는 세션 컨텍스트를
  공개 세션째 클라이언트에 올리고, «서버 렌더가 낡았으면 다시 그린다» 를 못 한다.
- 「익명 방문은 요청 0」(Header 머리 주석) 유지 — keeper 는 `authed` 분기 안, heartbeat 옆.
- `session.ts` 의 쿠키 이름 판정이 `NEXTAUTH_URL` 의 https 여부 → **쿠키 존재 여부**로 바뀜
  (web-store·`federated-logout.ts` 와 같은 방식). `federated-logout.ts` 는 건드리지 않았다.
- 미들웨어 테스트의 「auth.js 설정 오류 500 본문」 칸: 복호 전용 판정에는 그 본문이 올 수
  없다. 같은 설정 오류(`NEXTAUTH_SECRET` 부재)는 `getToken` 의 MissingSecret **throw** 로
  오므로 그 칸들을 throw 로 재현하도록 바꿨다 — 기대값(꺾인다 / 공개 경로는 열린다) 불변.

## 공유하지 않은 것

`session-token.ts` · `session-route.ts` 는 web-store 파일의 **프로젝트별 사본**이다(머리
주석에 명시). repo-root `libs/` 는 프로젝트 무관이어야 하고, 두 프론트 앱이 한 프로젝트가
아니므로 `projects/<name>/libs/` 대상도 아니다. 한쪽을 고치면 형제를 grep 할 것.

## 남는 위험 (알고 둔다)

- 유휴 뒤 **첫 하드 로드**는 만료 bearer 로 렌더된다 → keeper 가 읽고 `router.refresh()`
  로 다시 그린다(한 번 깜빡임). JS 가 없으면 갱신되지 않는다.
- 가시 탭당 50초마다 `/api/auth/session` 1회(서버리스 호출). IAM 호출은 margin 창에서만.
- FE-106 과 같은 절충: 승자 응답이 2초 넘게 걸리면 패자의 재시도 홉이 쿠키를 지운다.

## 스펙

- `specs/services/fan-platform-web/architecture.md` § Authentication — 「Silent refresh —
  exactly one place」 절 추가, § Failure Modes 1행 분리(코드보다 먼저).

## 검증 (2026-10-07 UTC, Node 24.14.0 로컬)

| 무엇 | rc / 결과 |
|---|---|
| 대조군만, 수정 전 코드 | rc=0 — 2/2 초록 (= 하네스가 결함 두 모양을 재현) |
| fan vitest 전체 | rc=0 — 45 files / 401 tests (착수 전 42 / 367) |
| `npx tsc --noEmit` | rc=0 |
| `npx next lint` | rc=0 — No ESLint warnings or errors |
| `next build` (`NEXTAUTH_SECRET` 더미) | rc=0 — Middleware 45.5 kB |
| `check-client-graph-backend-origins` · `check-client-graph-server-only` · `check-fetch-resolution` | rc=0 · 0 · 0 |
| **bite** — 스크래치 사본으로 ② 패자 표지 1줄 끔 + `session.ts`·`middleware.ts` 를 HEAD 로 + keeper 주기 60s | rc=1 — **정확히 9셀 실패**: auth-callbacks «경합 패자 표지» · race «loser converges» · «winner not landed» · «dead refresh token» · middleware «만료·refresh 가능 통과, IAM·auth() 0» · «로그인 상태 통과(음성 대조군)» · decode-only 2칸 · keeper 부등식. 🔵 대조군 2칸은 그대로 초록. 복원 후 `cmp` 4파일 일치, 전체 재실행 초록 |
