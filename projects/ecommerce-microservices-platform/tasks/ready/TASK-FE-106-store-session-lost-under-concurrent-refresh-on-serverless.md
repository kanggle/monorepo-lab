# Task ID

TASK-FE-106

# Status

ready

# Title

유휴 뒤 복귀 시 동시 요청이 refresh 토큰을 중복 전송 — Vercel 서버리스 인스턴스 경합으로 스토어 세션이 로그아웃된다

# Owner

ecommerce-microservices-platform

# Task Tags

- web-store
- auth
- bug
- demo

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — web-store/iam 레이어 경계를 가르는 설계 판단이 필요한 결함.

---

# Dependency Markers

- 출처: `TASK-MONO-764` 23차 창(데모 기능 점검표).
- 관련: `TASK-BE-606`·`TASK-BE-608`(iam, refresh 토큰 재사용 탐지 — 30초 유예창, 유예 내
  재사용은 거절하되 아무것도 회수하지 않는다) — **이 정책을 읍어야 이 티켓의 원인을
  이해할 수 있다.**
- 참고(패턴): `TASK-PC-FE-300`(console-web 이 같은 클래스의 경합을 307 재시도로 해소한
  선행 티켓).

---

# 배경 — 23차 창 라이브 실측 (2026-10-06 UTC, 07:10Z, n=1 — 1회 관측, 재현 절차는 AC-0)

스토어 세션의 액세스 토큰이 만료(유휴 ~35분)된 상태에서 스토어 페이지를 로드하자 **여러
병렬 서버 요청**이 동시에 나갔다. 같은 시각 auth-service 로그:

```
SAS_REFRESH: replay of a refresh token rotated PT1.0…3.6S ago, within the 30s grace
window — refused, nothing revoked. account=65947474-…
```

**5회** 반복 기록. 그 직후 스토어의 `/api/auth/session` 이 `accountId` 없이(로그아웃)
반환됐다.

## 원인 후보 — in-process dedupe 는 인스턴스를 못 넘는다

web-store 는 `src/shared/auth/auth-callbacks.ts:174` 에서 이렇게 적어 두고 있다
(NextAuth `jwt` 콜백 안):

```ts
// Phase 4.5 F3 — proactive silent refresh. On subsequent calls (no
// `account`), refresh if the access token is at/near expiry and a refresh
// token is held. NextAuth serializes the `jwt` callback per session token, so
// this is the in-flight-dedupe point (a single refresh per session JWT).
if (!account) {
  ...
  if (!stillValid && refreshToken && !token.error) {
    const refreshed = await refreshAccessToken(refreshToken);
```

이 가정(`NextAuth serializes the jwt callback`)은 **한 Node 프로세스 안에서만** 참이다.
Vercel 서버리스는 같은 브라우저의 병렬 요청(여러 리소스가 동시에 같은 페이지에서 로드될
때)을 **여러 인스턴스**로 분산할 수 있다 — 각 인스턴스가 독립적으로 "만료됐다, refresh
하자"고 판단하면, 먼저 끝난 인스턴스가 토큰을 회전시키고, 늦은 인스턴스는 **이미 회전된
구 토큰**으로 refresh 를 재시도한다. iam 의 30초 유예창(`TASK-BE-606`/`608`)이 그 재시도를
"거절하되 회수하지 않음"으로 처리하는 것은 **옳은 동작**이다 — 문제는 web-store 쪽이 그
거절을 받은 뒤 **먼저 성공한 인스턴스의 결과로 수렴하지 못하고** 세션을 깨뜨리는 것으로
보인다(정확한 실패 지점은 착수 시 재현해 확인 — AC-0).

## 참고가 될 선행 해결 — console-web

`projects/platform-console/apps/console-web/src/shared/lib/session-refresh.ts`
(`TASK-PC-FE-300`): refresh POST 가 `grant_rejected` + 회전 의심 상황을 만나면, 대기 후
**307 `?retry=1`** 로 같은 요청을 다시 돌려 GET 이 **그 사이 회전된 쿠키를 새로
읍게** 한다. 판정 함수 `hasCompleteSession` 을 GET/POST 가 공유한다. 🔴 **그대로 복사할
수는 없다** — console 은 쿠키 기반 Route Handler 라 307 로 리다이렉트할 자리가 있지만,
web-store 는 NextAuth JWT 전략(`jwt` 콜백이 메모리의 토큰 객체를 갚는 구조)이라 같은
모양의 리다이렉트 지점이 없다. **착수 시 이 패턴에서 무엇이 web-store 에 옮겨오는지부터
판단할 것**(AC-1).

## 별개 관측 — 결함이 아님 (Edge Case 로만 기록)

같은 측정 세션에서, 오래된 세션 쿠키를 재생(replay)하는 자동화가 한 번
`reuse detected — revoking the refresh-token family` 를 발생시켰다 — 이것은 **올바른
동작**이다(진짜 재사용 탐지). 이 티켓의 결함(유예창 내 "무해한" 동시 재시도)과 혼동하지
말 것.

---

# Goal

유휴 뒤 복귀 시 여러 병렬 요청이 동시에 refresh 를 시도해도, 스토어 세션이 깨지지 않고
먼저 성공한 회전 결과로 수렴한다.

---

# Scope

## In Scope

- `auth-callbacks.ts` 의 `jwt` 콜백이 동시 refresh 경합에서 **인스턴스를 넘어** 안전하게
  동작하도록 수정 — 정확한 기전(예: 실패한 refresh 를 즉시 세션 오류로 승격하지 않고, 짝이
  맞는 재시도/재조회 경로를 둔다)은 착수 시 조사해 정한다. console-web 의 307 패턴을
  **참고**하되 NextAuth JWT 전략에 맞는 대응책을 고를 것 — 그대로 베끼는 것을 요구하지
  않는다.
- 재현 테스트(AC-4).

## Out of Scope

- iam 의 30초 유예창 정책(`TASK-BE-606`/`608`) 변경 — 그 설계는 단일 클라이언트 경합을
  위한 것으로 이미 올바르고, 이 티켓은 클라이언트 측(web-store)의 다중 인스턴스 문제다.
- 클라이언트에서 병렬 요청 자체를 금지/합치는 것(밴드에이드) — 진짜 레이스가 다른 방식
  (예: 서로 다른 탭)으로도 재발할 수 있다.
- `fan-platform-web` 의 동일 결함 — 같은 창에서 코드 형태 일치를 발견해 별도 티켓
  (`TASK-FAN-FE-027`)으로 기안했다. 두 프로젝트의 수정은 각자 PR.

---

# Acceptance Criteria

- [ ] **AC-0 (재현)** — 세션을 ~35분 유휴시킨 뒤 스토어 페이지 로드(또는 동시에 여러
      요청을 유발하는 조작)로 위 auth-service 로그(`SAS_REFRESH: replay … within the 30s
      grace window`)와 `/api/auth/session` 의 로그아웃을 재현한다. n=1 관측을 n≥2 로
      확인하거나, 재현 안 되면 조건을 더 좁혀 다시 시도하고 그 결과를 기록한다.
- [ ] **AC-1** — web-store 의 refresh 처리가 다중 인스턴스에 안전한 전략으로 바뀐다(구체적
      기전은 착수 시 결정 — console-web 패턴을 인용만, 강제하지 않음).
- [ ] **AC-2** — AC-0 재현 절차를 수정 후 다시 돌리면 세션이 깨지지 않는다(유효한
      `accountId` 유지).
- [ ] **AC-3 (회귀)** — 진짜 재사용 탐지 경로(Edge Case 의 "결함 아님" 사례)는 여전히
      정상 동작한다 — `TASK-BE-606`/`608` 의 의도된 동작을 깨뜨리지 않는다.
- [ ] **AC-4** — 만료된 같은 JWT 에 대한 동시 refresh 호출을 재현하는 단위/통합 테스트.

---

# Related Specs

- `projects/iam-platform/tasks/done/TASK-BE-606-sas-reuse-detection-misses-rotated-token-replay.md`
- `projects/iam-platform/tasks/done/TASK-BE-608-reuse-detection-hardening-followups.md`

# Related Contracts

- iam SAS refresh-token 엔드포인트의 유예창 시맨틱(위 두 티켓이 정의)

---

# Edge Cases

- **결함 아님** — 오래된 세션 쿠키 재생 시 "reuse detected — revoking the family" 가
  뜨는 것은 올바른 동작이다. 이 티켓의 수정이 그 경로를 약화시키면 안 된다(AC-3).
- 여러 **탭**이 동시에 refresh 를 시도하는 경우와 여러 **서버리스 인스턴스**가 하나의
  페이지 로드에서 동시에 refresh 를 시도하는 경우는 서로 다른 레이스일 수 있다 — 착수 시
  어느 쪽(또는 둘 다)이 실제 기전인지 확인할 것.

# Failure Scenarios

- **iam 의 유예창을 늘려서 "고친다"** — 잘못된 레이어. 유예창은 이미 의도대로 동작하고
  있고(재사용 거절 + 무회수), 깨지는 것은 클라이언트가 그 거절을 받은 뒤의 처리다.
- **클라이언트에서 동시 요청 자체를 막는 것만으로 끝낸다** — 진짜 원인(인스턴스 경합)이
  다른 경로(다른 페이지 동시 로드 등)로 재발할 수 있다.
- **console-web 의 307 패턴을 기계적으로 복사한다** — web-store 는 Route Handler 가 아닌
  NextAuth JWT 콜백 구조라 같은 리다이렉트 지점이 없다; 형태가 다르면 다른 해법이 필요하다.
