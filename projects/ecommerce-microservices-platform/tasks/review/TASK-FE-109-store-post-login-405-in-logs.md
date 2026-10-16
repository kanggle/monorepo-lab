# Task ID

TASK-FE-109

# Status

review

# Title

스토어 Vercel 로그에 `POST /login 405` 가 쌍으로 반복된다 — 누가 `/login` 에 POST 하는지 밝힌다

# Owner

ecommerce-microservices-platform

# Task Tags

- web-store
- observability

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 원인 추적이 대부분(AC-0), 고칠 것은 작을 가능성.

---

# Dependency Markers

- 출처: 26차 데모 창(2026-10-11 UTC) — `TASK-FE-108` 재현 중 소유자가 복사한 Vercel 요청 로그.
- 관련: `TASK-FE-108`(같은 로그 창).

# Goal

26차 창 Vercel 요청 로그(KST): `POST /login 405` 가 **두 줄씩** 여러 번 — 18:53:30.85 · .91, 18:55:14.62 · .63, 18:55:15.33 · .34, 18:55:16.82 · .82, 18:57:50.04 · .04, 18:57:54.98 · .99, 18:57:56.05 · .08. 같은 시각에 `GET /login 200/304` · `POST|GET /api/vitals 307` 도 찍힌다. 사용자 화면에는 증상이 없었고(FE-108 의 실패와는 무관 — 그 원인은 state 경합으로 확정), 정체를 모른다.

가설(미검증): Next.js 라우터 프리페치/서버 액션이 `/login` 에 POST 를 보내거나, `/api/vitals` 307 리다이렉트가 POST 를 `/login` 으로 넘긴다(307 은 메서드를 보존한다 — 미들웨어가 `/api/vitals` 를 인증 필요로 보고 `/login` 으로 307 하면 POST 가 그대로 `/login` 에 도착해 405).

# Scope

## In Scope

- **AC-0** — 로컬 또는 데모에서 재현해 `POST /login` 의 출처를 판정(브라우저 네트워크 탭 initiator 또는 Vercel 요청 상세). 위 가설이면 `middleware.ts` · `auth.ts` `authorized` 의 공개 경로 목록(`/api/vitals` 포함 여부)을 file:line 으로.
- 원인 지점 수정(예: `/api/vitals` 를 공개 경로로 · 또는 vitals 전송을 인증된 경우로) + 시험.

## Out of Scope

- vitals 수집 자체의 설계 변경.

# Acceptance Criteria

- [x] **AC-0** — 출처 판정(file:line · 재현 근거). 가설(미들웨어가 `/api/vitals` 를 공개 경로에서 빼먹어 307→`/login`, 메서드 보존→405)이 **맞다**.
  - `src/app/web-vitals.tsx:32-36` — 프로덕션에서 `useReportWebVitals` 콜백이 메트릭(LCP/CLS/INP/FCP/TTFB)마다 하나씩 `navigator.sendBeacon('/api/vitals', body)`(실패 시 `fetch POST keepalive`)를 쏜다 — 한 페이지에서 메트릭이 거의 동시에 보고되므로 로그에 쌍으로 찍힌다.
  - `src/middleware.ts:97-108`(수정 전) — `config.matcher` 는 `api/auth|_next/static|_next/image|favicon.ico|robots.txt|sitemap.xml|sw.js` 만 빼므로 `/api/vitals` 에도 미들웨어가 돈다.
  - `src/middleware.ts:22-65`(수정 전) — public 경로 목록에 `/api/vitals` 가 없어, 비로그인 요청이 87-91행의 인증 검사로 떨어져 `NextResponse.redirect(loginUrl)`(307, `loginUrl.pathname='/login'`) 을 받는다. 307 은 메서드를 보존(RFC 7231 §6.4.7)하므로 POST 가 그대로 `/login` 에 도착 → Next.js 가 `/login` 라우트에 POST 핸들러가 없어 405.
  - `src/shared/auth/auth.ts:120-138` — 같은 표를 미러링하는 `authorized` 콜백에도 `/api/vitals` 가 없었다(이쪽은 현재 런타임에서 실제로 평가되지 않는 것으로 보이나, `middleware.ts` 의 기존 주석이 "Mirrors the documented intent of authConfig.callbacks.authorized" 라고 명시해 둔 짝이라 함께 고쳤다).
  - 재현 근거: `pnpm --filter web-store exec vitest`(CI, Node 20)로 수정 전 커밧(`67c3aa01b`)에 추가한 두 테스트(미인증 POST·GET `/api/vitals`)가 **307/`/login` 로 떨어져 실패**(CI run [38412464400](https://github.com/kanggle/monorepo-lab/actions/runs/38412464400), job `Frontend unit tests`, `src/__tests__/middleware.test.ts` 17 중 2 실패) — 로컬 vitest 는 이 호스트의 Node 24 `#module-evaluator` 트랩(vitest4×Node24)으로 기동 자체가 안 돼 CI 를 재현 증거로 썼다. `next build`/`next start` 로컬 재현은 `output:'standalone'` 의 trace-copy 단계가 Windows 심링크 `EPERM` 으로 막혀 못 했다(타입체크까지는 통과: `pnpm exec tsc --noEmit` rc=0).
- [x] **AC-1** — 수정 뒤 같은 동작에서 `POST /login 405` 0줄 · vitals 가 의도대로 수집된다(대조군).
  - 수정 커밋 `57fc4a47a`: `middleware.ts`/`auth.ts` 의 public 목록에 `pathname === '/api/vitals'` 추가. 같은 CI run 재실행([38413114273](https://github.com/kanggle/monorepo-lab/actions/runs/38413114273), job `Frontend unit tests`) 에서 `src/__tests__/middleware.test.ts` **17/17 통과** — 미인증 POST·GET `/api/vitals` 모두 200·`Location` 없음.
  - 대조군(같은 커밋, 같은 테스트 파일): 로그인한 방문자도 `/api/vitals` 에서 막히지 않음(이 경로가 "인증되면 통과"로 바뀐 게 아니라 무조건 공개임을 확인) · `/api/demo/heartbeat` 는 여전히 막혀 있음(목록 확장이 좁게 들어갔다는 회귀 대조) · `/api/vitals/route.ts` 자체는 변경하지 않아 204 응답·`console.log(JSON.stringify({type:'web-vitals',…}))` 로깅 로직은 그대로 — vitals 수집 라우트에 도달하면 전과 동일하게 기록된다.
  - ⚪ **다음 창** — "다음 창 Vercel 로그에서 `POST /login 405` 0줄"은 라이브 배포 뒤가 아니면 잴 수 없다. 로컬/CI 로 판정한 것은 미들웨어의 라우팅 결정이고, Vercel 프로덕션 로그 재확인은 다음 데모 창에서.

# Related Specs

- `projects/ecommerce-microservices-platform/apps/web-store/src/middleware.ts` · `src/shared/auth/auth.ts`(`authorized` 공개 경로)

# Related Contracts

- 없음

# Edge Cases

- 로그인 상태와 비로그인 상태에서 다르게 나올 수 있다 — 둘 다 잰다.

# Failure Scenarios

1. 405 를 막으려고 `/login` 에 POST 핸들러를 추가한다 — 증상만 지우고 vitals 는 계속 버려진다.

---

# 28차 창 메모 (2026-10-16 UTC) — 닫지 않음

- AC-1 의 라이브 칸(«다음 창 Vercel 로그에서 `POST /login 405` 0줄» + vitals 대조군)은 이번 창에서 **재지 않았다** ⚪ — 스토어 Vercel 요청 로그를 읽지 않았다. 이 칸에는 AMI 가 아니라 Vercel 로그가 필요하다.
- 그래서 `review/` 에 그대로 둔다. 다음 창의 할 일 목록: `TASK-MONO-672` § Order «Still open» 8.
