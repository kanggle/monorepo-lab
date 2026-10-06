# Task ID

TASK-MONO-767

# Title

web-store · fan-platform-web 빌드가 `next/font/google` 의 **빌드 시점 Google Fonts 요청** 때문에 간헐적으로 실패한다 — `TypeError: Cannot read properties of null (reading '1')`

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- frontend
- cross-project

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (두 앱의 글꼴 로더 교체 + 빌드 시험)

---

# 왜 루트 티켓인가

같은 원인이 두 프로젝트(`ecommerce-microservices-platform/apps/web-store` · `fan-platform/web/fan-platform-web`)에 있고, 증상은 공용 CI 잡(`Frontend lint & build` · `Frontend E2E smoke` · Nightly `Web-store GAP logout e2e` 등)을 빨갛게 만든다. 한 PR 로 같이 고쳐야 한쪽만 남지 않는다.

# Dependency Markers

- 출처: 2026-10-06~07 UTC 세션 — 코드와 무관한 PR 들의 CI 가 같은 메시지로 세 번 빨갛게 됐다(아래 실측). 지나가듯 적힌 선례: `TASK-FE-103`(done) 마감 기록 «첫 런 lint·build 의 next/font 외부 fetch 오류».

# 실측 (2026-10-06~07 UTC)

| 커밋 / PR | 잡 | 바뀐 경로 | 같은 코드 재실행 |
|---|---|---|---|
| `ddf39df00` (#4167) | CI `Frontend lint & build` | infra/demo (프런트 무관) | 다음 커밋 `b11dbc13a` 에서 통과 |
| `3e39a3df5` (#4189) | Nightly `Web-store GAP logout e2e` | wms 시드 (프런트 무관) | 다음 커밋 `fed949d5c` 전 잡 통과 |
| #4195 head | CI `Frontend E2E smoke` | community-service (프런트 무관) | 실패 잡 재실행 → 통과 |

로그(#4195, fan-platform-web `next build`):

```
src/app/layout.tsx
An error occurred in `next/font`.
TypeError: Cannot read properties of null (reading '1')
    at … next/dist/compiled/… 
    at async nextFontGoogleFontLoader (…)
> Build failed because of webpack errors
```

`next/font/google` 을 쓰는 곳(2026-10-07, `git grep`): `apps/web-store/src/app/layout.tsx` · `fan-platform-web/src/app/layout.tsx` — 둘뿐.

# 원인 (가설 — AC-0 에서 확정)

`next/font/google` 은 **빌드 때** Google Fonts CSS 를 내려받아 정규식으로 파싱한다. 응답이 예상 모양이 아니면(속도 제한 · 일시 오류 · 다른 본문) 매치가 `null` 이 되어 위 TypeError 로 빌드 전체가 죽는다. 코드가 같아도 재실행하면 통과하는 이유.

# Goal

프런트 빌드가 외부 글꼴 서버 상태와 무관하게 결정적으로 성공한다.

# Scope

## In Scope

- 두 앱의 글꼴을 **빌드 시점 네트워크 없이** 쓰도록 바꾼다 — 1안 `next/font/local` + 저장소에 woff2 커밋(라이선스 확인 — OFL 이면 동봉), 2안 시스템 글꼴 스택. 화면 차이를 비교해 고른다.
- 같은 글꼴 이름·weight·`display`·CSS 변수명 유지(화면 무변경이 목표).

## Out of Scope

- console-web(이미 `next/font/google` 미사용 — 확인만).
- 디자인 변경.

# Acceptance Criteria

- [ ] **AC-0** — 원인 확정: 같은 빌드를 네트워크 차단(또는 Google Fonts 호스트 차단) 상태에서 돌려 같은 TypeError 가 나는지(재현) · 수정 후 같은 조건에서 빌드 성공(대조).
- [ ] **AC-1** — 두 앱 `next build` 가 Google Fonts 호스트 없이 rc=0.
- [ ] **AC-2** — 렌더된 글꼴 동일: 수정 전·후 같은 페이지 스크린숏(또는 computed `font-family`) 대조.
- [ ] **AC-3** — `git grep "next/font/google"` 0건, 재도입 방지 가드(정적 검사 한 칸) — 가드가 문다는 bite 포함.
- [ ] **AC-4** — CI `Frontend lint & build` · `Frontend E2E smoke` 초록.

# Related Specs

- 각 앱 `specs/services/<app>/architecture.md` 의 빌드·자산 절(있으면)

# Related Contracts

- 없음.

# Edge Cases

- 글꼴 파일 라이선스 — OFL 이 아니면 2안(시스템 글꼴)으로.
- 한글 글꼴은 용량이 크다 — 서브셋/weight 수를 현재 사용분으로 한정.

# Failure Scenarios

1. **재실행으로 계속 넘긴다** — 머지 시점 빨강이 «flake» 로 정상화되고, 진짜 빨강을 같은 눈으로 본다.
2. **한 앱만 고친다** — 다른 앱이 같은 이유로 CI 를 계속 깬다.
3. **글꼴 이름·변수를 바꾼다** — 화면이 조용히 바뀐다(AC-2 가 막는다).
