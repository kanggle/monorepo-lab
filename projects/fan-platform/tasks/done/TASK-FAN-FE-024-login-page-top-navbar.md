# Task ID

TASK-FAN-FE-024

# Title

팬 로그인 페이지에 상단 헤더(내비게이션)가 없다 — 자기 Header 재사용, 스토어 방식 이식 금지

# Status

done (2026-09-24 UTC — AC-1~AC-5 닫힘)

# Owner

frontend

# Task Tags

- code
- frontend
- test

---

# Dependency Markers

- **선행 (prerequisite)**: `TASK-MONO-728` 이 같은 파일 `(auth)/login/page.tsx:161` 의 버튼 문구(「GAP 로 로그인」→「GAP로 로그인」)를 고친다. **728이 먼저 머지된 뒤 착수**하거나, 같은 worktree에서 728 → 024 순서로 직렬 진행한다(같은 파일 시리즈 규율).

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5.

---

# Goal

팬 로그인 페이지(`(auth)/login/page.tsx:82-179`)는 로고 + 카드만 있고 상단 내비게이션이 없다(2026-09-24 UTC 실측 확인, `Header` import 없음). 팬 자신의 `widgets/header/Header.tsx`(서버 컴포넌트 — 피드/아티스트/멤버십 내비 + 로그인 버튼)를 로그인 라우트에서도 렌더해, 로그인 화면에서도 사이트를 둘러볼 수 있게 한다. **스토어의 헤더 구현(클라이언트 컴포넌트, 햄버거 상태)은 이식하지 않는다** — 팬의 "익명 방문자는 게이트웨이 호출 0회" 불변식을 깬다.

# Scope

## In Scope

- 팬 로그인 라우트(`(auth)/login/page.tsx`, 필요하면 `(auth)/layout.tsx` 경유)에서 기존 `widgets/header/Header.tsx` 를 렌더한다 — 로고/홈/아티스트/피드/멤버십/로그인 링크가 로그인 화면에서도 보인다.
- 반응형 확인 — 헤더가 `flex-wrap` 방식(햄버거 없음, `Header.tsx:26-36` 확인)으로 이미 설계돼 있으므로 그 설계를 그대로 유지한다.

## Out of Scope

- 스토어(`ecommerce-microservices-platform/apps/web-store/src/widgets/header/Header.tsx`, 클라이언트 컴포넌트 + 햄버거 상태)를 참고하되 **그대로 이식하지 않는다** — 팬 헤더는 서버 컴포넌트이고 익명 방문자에 대해 게이트웨이 호출이 0회라는 문서화된 불변식(`Header.tsx:8-25,39-51`)이 있다. 클라이언트 컴포넌트로 바꾸면 이 불변식이 깨질 수 있다.
- 헤더 자체의 디자인/링크 구성 변경 — 기존 컴포넌트를 그대로 재사용한다, 새로 디자인하지 않는다.
- 로그인 폼/카드 내용 변경 — `TASK-MONO-728` 이 문구만 다룬다, 이 티켓은 구조(헤더 유무)만 다룬다.

# Acceptance Criteria

- [ ] **AC-1** — 로그인 페이지가 익명 방문자에게 헤더를 렌더하고, 그 요청에서 게이트웨이 호출이 0회임을 테스트로 확인한다(기존 `Header.tsx` 의 zero-gateway 불변식 테스트 패턴을 따른다).
- [ ] **AC-2** — 기존 로그인 e2e/유닛 테스트(`e2e-smoke/login.spec.ts`, `__tests__/login-page.test.tsx`)가 헤더 추가 후에도 통과한다(카드/폼 요소 셀렉터가 헤더 추가로 어긋나지 않는지 확인).
- [ ] **AC-3** — 모바일 폭(~400px)에서 헤더가 `flex-wrap` 으로 줄바꿈되고 겹침/잘림이 없다(브라우저 확인).
- [ ] **AC-4** — `pnpm build`(또는 해당 빌드 명령)가 로컬에서 통과한다.
- [ ] **AC-5** — `TASK-MONO-728` 의 버튼 문구 변경(「GAP로 로그인」)과 충돌 없이 병합된다 — 같은 파일을 건드리는 순서가 지켜졌음을 구현 기록에 남긴다.

# Related Specs

- `projects/fan-platform/web/fan-platform-web/src/app/(auth)/login/page.tsx`
- `projects/fan-platform/web/fan-platform-web/src/widgets/header/Header.tsx`(서버 컴포넌트, zero-gateway 불변식 :8-25,39-51, 반응형 :26-36)
- 참고(이식 금지 대상): `projects/ecommerce-microservices-platform/apps/web-store/src/widgets/header/Header.tsx`(클라이언트 컴포넌트, 햄버거 상태)
- `TASK-MONO-728`(같은 로그인 페이지 파일을 먼저 건드림)
- 2026-09-24 UTC 포트폴리오 UX 전수조사(소유자 승인)

---

# Related Contracts

- 없음(프런트엔드 전용, 게이트웨이 호출 0회 불변식은 **유지**, 새 호출을 추가하지 않는다).

---

# Target App

- `apps/fan-platform-web`(경로: `web/fan-platform-web`)

---

# Edge Cases

- 로그인 화면에서 헤더의 "로그인" 링크/버튼이 이미 로그인 페이지에 있는 것과 중복되지 않는지 확인한다(헤더의 로그인 버튼과 페이지 본문 카드가 어색하게 겹치지 않도록).
- 로그인 상태에서 이 라우트에 진입하는 경우(보통 리다이렉트로 도달하지 않지만) 헤더가 로그인 상태를 잘못 표시하지 않는지 확인한다.

# Failure Scenarios

- 스토어 헤더를 그대로 복사해 클라이언트 컴포넌트로 만들면 zero-gateway 불변식이 깨지고, 익명 방문자의 로그인 페이지 방문이 원치 않는 게이트웨이 호출을 유발한다.
- `TASK-MONO-728` 보다 먼저 착수해 같은 파일에서 병합 충돌이 난다.

---

# 구현 기록 (2026-09-24 UTC · 분석=Opus 5.5 · 구현=Sonnet 5)

## 순서 — ✅ TASK-MONO-728 → TASK-FAN-FE-024 (같은 worktree, 직렬)

728 이 먼저 `(auth)/login/page.tsx:161` 의 버튼 문구(`GAP 로 로그인`→`GAP로 로그인`)를 커밋했고, 그 다음 이 티켓이 **다른 파일**(`(auth)/layout.tsx` 신설)로 헤더를 추가했다. 두 커밋이 같은 파일의 겹치지 않는 관심사를 건드려 병합 충돌 없음(AC-5).

## 구현

- 신설: `src/app/(auth)/layout.tsx` — `(main)/layout.tsx` 와 같은 패턴으로 기존 `widgets/header/Header.tsx` 서버 컴포넌트를 그대로 렌더하고 `{children}` 을 이어붙인다. `page.tsx` 자체는 무변경(스코프대로 헤더 유무만 다룸, 폼/카드 내용 무변경).
- 스토어 헤더(클라이언트 컴포넌트, 햄버거 상태)는 참고만 하고 이식하지 않았다 — `Header` import 는 기존 서버 컴포넌트 그대로.

## AC-1 — ✅ 헤더 렌더 + zero-gateway, 테스트로 확인

신규 `src/app/(auth)/__tests__/layout.test.tsx` (3 tests):

1. `Header()` 를 직접 호출·await 해 로고 + 내비(피드/아티스트/멤버십) + 익명 "로그인" 링크가 렌더됨을 확인.
2. 🔴🔴 익명 방문자 렌더에서 `fetch` 스파이 호출 0회를 단언(zero-gateway 불변식).
3. `(auth)/layout.tsx` 소스를 읽어 `Header` 가 실제로 import·렌더되는지 배선 확인(렌더 테스트만으로는 「배선이 있는가」를 못 잡는 이 저장소의 반복 함정 대조군 — `fan-post-compose.test.tsx:88` 와 같은 방식).

🔴 **jsdom 제약**: `Header` 는 async Server Component라 `<Header/>` 를 JSX 로 그대로 `render()` 하면 *"Only Server Components can be async at the moment"* 로 죽는다(react-dom client 렌더러가 RSC 를 모른다) — 형제 `login-page.test.tsx` 가 `<LoginPage/>` 대신 `await LoginPage(...)` 를 직접 호출하는 것과 같은 이유. `Header()` 를 직접 await 해 우회했다.

🔴 **`server-only` 해석 실패**: `Header` 가 transitively import 하는 `@/shared/auth/session`·`@/shared/auth/federated-logout`·`@/features/notification`(→`getNotifications.ts`) 가 각각 `import 'server-only'` 를 갖고 있어 jsdom vitest 환경에서 로드가 안 된다(`Failed to resolve import "server-only"`). 저장소 기존 관행(`public-pages.test.tsx`·`post-detail-back-link.test.tsx`) 그대로 그 세 모듈만 `vi.mock` 으로 교체했다 — `Header` 자신의 JSX/분기 로직은 실제 코드를 그대로 태운다.

## AC-2 — ✅ 기존 로그인 테스트 무영향

- `__tests__/login-page.test.tsx` 는 `page.tsx` 를 **직접 import·await** 하는 방식이라(Next.js 라우팅 트리를 통하지 않음) 애초에 `(auth)/layout.tsx` 를 거치지 않는다 — 헤더 추가로 셀렉터 충돌 없음. 36 files / 305 tests 전체 통과로 재확인.
- `e2e-smoke/login.spec.ts` 는 실제 `/login` 라우트를 열므로 레이아웃이 적용된다 — `page.getByRole('heading', {name:'로그인'})` 은 카드의 `<h2>`(헤더의 "로그인"은 `<a>` 링크라 role 이 다르다), `page.getByTestId('oidc-signin')` 은 카드 버튼 — 헤더 추가로도 셀렉터가 갈라지지 않음을 브라우저로 확인(아래 AC-3).

## AC-3 — ✅ 브라우저 확인(로컬 `next dev`, 데스크톱 1280px + 모바일 ~400px)

- 데스크톱: 로고 `fan-platform` + 피드/아티스트/멤버십 + 우측 "로그인" 한 줄, 로그인 카드와 겹침 없음(스크린샷).
- 모바일 400px: 헤더가 `flex-wrap` 으로 두 줄로 접힘(1행 로고+로그인, 2행 피드/아티스트/멤버십) — 글자 단위로 끊기지 않고(`whitespace-nowrap`), 카드와 겹침/잘림 없음(스크린샷). `TASK-FAN-FE-023` 이 이미 고정한 반응형 설계를 그대로 재사용했으므로 새 CSS 없음.
- Edge Case(헤더 "로그인"과 카드 버튼의 중복 확인) — 스크린샷 상 시각적으로 분리돼 있고 헤더 "로그인"은 `/login` 링크(현재 페이지로 이동, 실질 no-op), 카드 버튼은 `GAP로 로그인`(OIDC 개시)으로 레이블이 달라 혼동 소지가 낮다고 판단, 별도 변경 없음.

## AC-4 — ✅ 빌드

`pnpm run build` rc=0. 라우트 표에 `/login` 이 여전히 개별 라우트로 생성됨(레이아웃은 별도 산출물로 합쳐짐, Next.js App Router 표준 동작).

## AC-5 — ✅ 순서/충돌 기록

위 "순서" 절 참조. 두 티켓이 로그인 파일 트리에서 겹치지 않는 파일(`page.tsx` 문구 vs `layout.tsx` 신설)을 건드려 실제 병합 충돌 0건.

## 게이트 기록(파이프 없이 개별 statement, 종료 코드 직접 확인)

| 게이트 | 명령 | rc |
|---|---|---|
| typecheck | `pnpm exec tsc --noEmit` | 0 |
| lint | `pnpm run lint` | 0 |
| unit test(전체) | `pnpm run test` | 0 (36 files / 305 tests) |
| build | `pnpm run build` | 0 |

## 편차 없음

Out of Scope 로 명시된 항목(스토어 헤더 이식·헤더 자체 디자인 변경·로그인 폼/카드 내용 변경) 전부 무변경. `(auth)/layout.tsx` 신설은 Related Specs 가 명시한 "필요하면 `(auth)/layout.tsx` 경유" 선택지를 그대로 택한 것.
