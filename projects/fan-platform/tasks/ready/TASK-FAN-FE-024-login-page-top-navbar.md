# Task ID

TASK-FAN-FE-024

# Title

팬 로그인 페이지에 상단 헤더(내비게이션)가 없다 — 자기 Header 재사용, 스토어 방식 이식 금지

# Status

ready

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
