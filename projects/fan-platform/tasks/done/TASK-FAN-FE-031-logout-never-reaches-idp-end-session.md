# Task ID

TASK-FAN-FE-031

# Title

팬 «로그아웃» 이 IdP `end_session` 에 닿지 않는다 — NextAuth 기본 `redirect` 콜백이 다른 origin 주소를 앱 루트로 바꿔, IdP 세션이 남고 다음 «IAM 로그인» 이 비밀번호 없이 다시 로그인된다

# Status

done

# Owner

fan-platform

# Task Tags

- frontend
- auth
- demo

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (콜백 하나 + 단위 시험)
>
> 출처: `TASK-MONO-764` 23차 창(2026-10-06 UTC) — 소유자 보고 «구글로 로그인했다가 로그아웃한 상태에서 IAM 로그인 누르면 로그인하는 창이 떠야 하는데 기존 구글로 자동 로그인돼». 소유자 결정 «창 안에서 고치기».

---

# Dependency Markers

- 출처: `TASK-MONO-764` 23차 창.
- 형제(정상): web-store `features/auth/model/auth-context.tsx:63-73` — `/api/auth/end-session-url` 로 받은 주소로 `window.location` 직접 이동.

# 실측 (2026-10-06 UTC, 인스턴스 `i-036521b58c68566f2`)

같은 브라우저 · 같은 이메일 계정(소셜과 무관함을 보이려고 이메일 계정으로 재현):

| | 로그아웃 직후 이동 | 다시 로그인 |
|---|---|---|
| 팬(수정 전) | 외부 이동 0 — `fan.hubwang.com/` 에서 끝 | `authorize → callback → /` — **비밀번호 폼 0** |
| 스토어(대조군) | `end-session-url` 200 → `auth.hubwang.com/connect/logout` → 스토어 | **비밀번호 폼 1** |

auth-service 로그에 팬 쪽 로그아웃 흔적 0.

# 원인

`widgets/header/Header.tsx:116` 은 `signOut({ redirectTo: endSession ?? '/login' })` 로 IdP `end_session`(`<issuer>/connect/logout?id_token_hint=…`)을 넘긴다. 그러나 `shared/auth/auth.ts` 에 `redirect` 콜백이 없어 NextAuth 기본 콜백이 쓰이고, 기본 콜백은 **`baseUrl` 과 origin 이 다른 URL 을 `baseUrl` 로 바꾼다.** 배포에서 issuer(`auth.hubwang.com`)와 앱(`fan.hubwang.com`)은 origin 이 다르다 ⇒ 브라우저는 IdP 에 가지 못하고 `/` 로 간다. (id_token 이 없을 때의 대체 경로였다면 `/login` 으로 갔을 것 — 실측은 `/` 라 그 경로가 아니다.)

# Goal

로그아웃이 IdP 세션까지 끝내서, 다음 로그인에 로그인 화면이 뜬다.

# Scope

## In Scope

- `shared/auth/auth-callbacks.ts` 에 `redirectCallback` — 상대 경로·같은 origin 은 기본과 동일, 그 밖에는 **issuer 의 `/connect/logout` 정확히 하나만** 허용, 나머지는 `baseUrl`(오픈 리디렉트 아님).
- `shared/auth/auth.ts` 의 `callbacks.redirect` 에 연결.
- 단위 시험 `src/__tests__/auth-redirect-callback.test.ts`.

## Out of Scope

- 헤더 로그아웃 방식을 스토어처럼 클라이언트 이동으로 바꾸기 — 서버 액션 + 콜백 한 곳으로 충분하다.
- IdP(auth-service) 쪽 변경 없음.

# Acceptance Criteria

- [x] **AC-1** — IdP `end_session` URL 은 그대로 통과, 다른 외부 주소(다른 호스트의 `/connect/logout` · issuer 의 다른 경로 · `..` 우회 · 파싱 불가)는 `baseUrl`. 상대·같은 origin 은 기본 동작 유지(대조군).
- [x] **AC-2** — bite: 콜백을 기본 동작으로 되돌리면 수정을 재는 2건만 실패, 대조군·거부 4건은 통과. 복원 후 `cmp` 동일 · 재통과.
- [x] **AC-3** — `tsc --noEmit` rc=0 · `next lint` rc=0 · `auth-callbacks.test.ts` 와 함께 26/26.
- [x] **AC-4 (라이브, 23차 창)** — 배포 후 같은 브라우저에서 팬 로그인 → 로그아웃 시 `auth.hubwang.com/connect/logout` 을 거치고, 다시 «IAM 로그인» 에 **비밀번호 폼이 뜬다**.

# Related Specs

- `projects/fan-platform/specs/services/fan-platform-web/` (인증 · 로그아웃)

# Related Contracts

- 없음(클라이언트 리디렉트 정책).

# Edge Cases

- issuer 에 경로 접두사가 있는 경우(`https://idp/realm`) — `realm/connect/logout` 로 비교(시험 있음).
- `post_logout_redirect_uri` 는 IdP 에 등록된 값과 정확히 같아야 한다(기존 `buildGapEndSessionUrl` 그대로 — 무변경).

# Failure Scenarios

1. **issuer origin 전체를 허용** — 그 호스트의 아무 경로로나 보낼 수 있는 리디렉트가 된다. 경로까지 고정한다.
2. **id_token 이 없는 세션** — 기존대로 `/login` 로컬 로그아웃(무변경).

---

## 라이브 AC-4 ✅ + done 이관 (2026-10-06 UTC)

- 배포 반영 13:43:54Z(`fan.hubwang.com/build-info.json` commit = `99c1332c7`).
- 같은 재현 스크립트 · 같은 이메일 계정 · 한 브라우저: 로그아웃 → `GET auth.hubwang.com/connect/logout [q:id_token_hint,post_logout_redirect_uri,client_id]` → `302 → fan.hubwang.com/` · 다시 «IAM 로그인» → `authorize → auth.hubwang.com/login` **비밀번호 폼 1**(수정 전 0, 13:21Z).
- 4-dim: (a) #4177 MERGED (b) `99c1332c7` ∈ origin/main (c) 머지 시점 실패 0 (SUCCESS 16) (d) AC-1~4 전부 닫힘.
