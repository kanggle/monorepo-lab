# Task ID

TASK-FE-109

# Status

ready

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

- [ ] **AC-0** — 출처 판정(file:line · 재현 근거).
- [ ] **AC-1** — 수정 뒤 같은 동작에서 `POST /login 405` 0줄(로컬 재현 또는 다음 창 Vercel 로그) · vitals 가 의도대로 수집된다(대조군).

# Related Specs

- `projects/ecommerce-microservices-platform/apps/web-store/src/middleware.ts` · `src/shared/auth/auth.ts`(`authorized` 공개 경로)

# Related Contracts

- 없음

# Edge Cases

- 로그인 상태와 비로그인 상태에서 다르게 나올 수 있다 — 둘 다 잰다.

# Failure Scenarios

1. 405 를 막으려고 `/login` 에 POST 핸들러를 추가한다 — 증상만 지우고 vitals 는 계속 버려진다.
