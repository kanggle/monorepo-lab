# Task ID

TASK-FE-106

# Status

review

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
      ⚪ **오케스트레이터가 다음 창에서 측정** (라이브 스택 필요). 코드 쪽 기전은 아래
      § 착수 조사 — 재현 시 **보호 경로(`/my/*`·`/checkout`) 진입 또는 그 링크의 prefetch**
      를 넣으면 미들웨어 경로가 섞인다. 수정 전 배포본에서 재야 한다.
- [x] **AC-1** — web-store 의 refresh 처리가 다중 인스턴스에 안전한 전략으로 바뀐다(구체적
      기전은 착수 시 결정 — console-web 패턴을 인용만, 강제하지 않음).
      → § 결정 (가)(나). 인스턴스 상태를 공유하지 않으므로 인스턴스 수와 무관.
- [ ] **AC-2** — AC-0 재현 절차를 수정 후 다시 돌리면 세션이 깨지지 않는다(유효한
      `accountId` 유지). ⚪ **오케스트레이터가 다음 창에서 측정** (머지·배포 뒤).
- [ ] **AC-3 (회귀)** — 진짜 재사용 탐지 경로(Edge Case 의 "결함 아님" 사례)는 여전히
      정상 동작한다 — `TASK-BE-606`/`608` 의 의도된 동작을 깨뜨리지 않는다.
      ⚪ **오케스트레이터가 다음 창에서 측정.** iam 코드는 건드리지 않았다. 클라이언트 쪽
      단위 증거: 경합 패자·진짜 실패 어느 경로도 같은 refresh 토큰을 두 번 보내지 않는다
      (`session-route-refresh-race.test.ts` 의 IAM 호출 수 단언 — 재시도 홉은 refresh 0회).
- [x] **AC-4** — 만료된 같은 JWT 에 대한 동시 refresh 호출을 재현하는 단위/통합 테스트.
      → `apps/web-store/src/__tests__/session-route-refresh-race.test.ts` (8셀) +
      `auth-callbacks.test.ts` § TASK-FE-106 (9셀) + `middleware.test.ts` § decode-only (4셀).
      bite 는 CI 로 확인 — 아래 § 검증.

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

---

# 구현 · 결정 (2026-10-06 UTC, 브랜치 `task-fe-106`)

## 착수 조사 — 코드에서 찾은 기전 (AC-0 의 코드 쪽 절반 · 라이브 재현은 아직)

1. **「NextAuth 가 `jwt` 콜백을 직렬화한다」는 가정은 어디서도 참이 아니다** — 한 프로세스
   안에서도 동시 요청 각각이 자기 쿠키 스냅숏으로 `jwt` 콜백을 돌린다. 서버리스 인스턴스는
   그 위에 얹힌 한 가지 경우일 뿐이다.
2. 🔴 **미들웨어가 refresh 를 하고 결과를 버리고 있었다.** `src/middleware.ts` 의 `await auth()`
   (요청 인자 없는 RSC 형태)는 `jwt` 콜백 = silent refresh 를 실제로 돌려 IAM 에서 refresh
   토큰을 **회전시킨 뒤**, 회전된 쿠키(Set-Cookie)를 **버린다** —
   `next-auth@5.0.0-beta.25/lib/index.js:91` `getSession(h, config).then((r) => r.json())`.
   그래서 액세스 토큰 만료 뒤의 보호 경로 네비게이션·**링크 prefetch** 하나하나가 「승자」가
   되고 그 승자의 결과는 아무에게도 전달되지 않는다. 브라우저는 옛 refresh 토큰을 그대로
   들고 있고, 다음 `/api/auth/session` 이 그것을 다시 보내 → 30초 안이면 유예 거절(관측된
   `SAS_REFRESH: replay … within the 30s grace window` 5회와 같은 모양), 30초 밖이면
   **재사용 탐지 → 패밀리 폐기 + 보안 이벤트**. 이 기전은 **인스턴스가 하나여도** 성립한다
   — n=1 관측을 설명하는 데 서버리스 다중 인스턴스가 필요 없다(둘 중 무엇이 실제였는지는
   AC-0 라이브 재현으로만 가릴 수 있다).
3. 「별개 관측」(옛 세션 쿠키 재생 → `reuse detected — revoking the family`)도 2번과 같은
   경로로 생길 수 있다 — 미들웨어가 버린 회전 뒤 30초가 지나 같은 토큰이 다시 나가면 그
   로그가 나온다. 결함 아님 판정 자체(iam 쪽은 옳게 동작)는 그대로다.

## 결정 — 두 갈래

**(가) 미들웨어 게이트를 decode-only 로** (`src/middleware.ts`). refresh 는 **결과가 브라우저에
쓰이는 곳에서만** 돈다: `GET /api/auth/session`. 게이트는 세션 쿠키를 복호만 하고
(`shared/auth/session-token.ts`) `session` 콜백과 같은 규칙(역할 · `error`)으로 판정한다.
만료됐지만 refresh 가능한 세션은 통과 — 클라이언트의 세션 조회가 갱신하고, BFF 는 낡은
bearer 를 스스로 거절한다(기존과 같음 — 미들웨어의 refresh 결과는 원래도 버려졌으므로
RSC·BFF 가 보던 토큰은 변하지 않는다).

**(나) 경합 패자는 쿠키를 쓰지 않고, 한 번 더 묻는다** (console-web `TASK-PC-FE-300` 의
기전을 세션 조회 자리로 옮김):

- `refreshTokenGrant`(`auth-callbacks.ts`)가 실패를 `rotation_suspect`(IAM `400
  invalid_grant` — BE-606 유예 거절의 모양) / `failed` 로 나눈다. 판정 기준은 console 의
  `rotationSuspect` 와 같다.
- 패자의 `jwt` 콜백은 `error`(종결 — 쿠키가 어디로든 새어 나가도 결과는 오늘의 로그아웃이지
  옛 refresh 토큰의 재전송이 아니다) **와** `refreshRaceLost` 표지를 단다. 표지는 그 호출
  한 번에만 유효(다음 호출 시작에서 지움).
- `GET /api/auth/session` 래퍼(`shared/auth/session-route.ts`)가 표지를 보면 그 응답을
  **Set-Cookie 째 버리고**, 2초(`REFRESH_RACE_GRACE_MS`, console 과 같은 값) 기다린 뒤
  `307 ?refresh_retry=1` 을 준다. `next-auth/react` 의 `fetch` 가 투명하게 따라가고, 그
  요청은 **그 시점의** 브라우저 쿠키 — 승자가 착지했다면 승자의 것 — 를 싣는다.
- **재시도 홉은 절대 refresh 하지 않는다**(루프 상한 · 옛 토큰 재전송 금지). 액세스 토큰이
  신선하면(`hasFreshAccessToken` — `jwt` 콜백과 **같은 판정 함수 하나**) 정상 응답 →
  로그인 유지. 여전히 만료면 세션 쿠키를 지우고 익명 → 로그아웃(진짜 실패, 또는 승자가
  2초 안에 못 온 경합).
- 경합 모양이 아닌 실패(5xx · 네트워크 · `invalid_client` 등)는 기존대로 즉시 `error` → 로그아웃.

## 버린 대안

- **공유 저장소(Redis/KV)로 승자의 토큰을 인스턴스 간 전달** — 새 인프라 = 아키텍처 결정
  (HARDSTOP-09 영역)이고, 그래도 패자의 Set-Cookie 덮어쓰기는 따로 막아야 한다.
- **패자가 `error` 없이 옛 토큰을 그대로 둔다** — 패자 쿠키가 승자 뒤에 착지하면 브라우저에
  옛 refresh 토큰이 남고, 30초 뒤 다음 refresh 가 **재사용 → 패밀리 폐기 + 보안 점수**
  (같은 계정 1시간 2회면 AUTO_LOCK, BE-606 결정 2). 로그아웃보다 나쁘다.
- **미들웨어도 refresh 하되 쿠키를 저장**(`auth(handler)` 래퍼 형태) — prefetch 하나하나가
  refresh 주체가 되고, 그 경로(`lib/index.js:168`)는 세션 Set-Cookie 를 무조건 붙이므로 패자
  쿠키를 막을 자리가 없다.
- iam 유예창 확대 · 클라이언트 동시 요청 합치기 — Out of Scope(Failure Scenarios).

## 남는 위험 (알고 둔다)

- 승자의 응답이 2초 넘게 걸리면 패자의 재시도 홉이 쿠키를 지운다 → 그 뒤 착지 순서가
  결과를 정한다(console 과 같은 절충; 비용은 실패 경로에서만).
- `POST /api/auth/session`(`useSession().update()`)은 래핑하지 않았다 — web-store 는 쓰지
  않는다. 쓰게 되더라도 `error` 가 같이 붙으므로 결과는 로그아웃이지 재전송이 아니다.
- RSC 는 refresh 하지 않는다(그 사실은 수정 전에도 같았다 — 미들웨어의 refresh 결과는
  버려졌다).

## 스펙

- `specs/services/web-store/architecture.md` § Authentication — 「refresh 는 한 곳」 ·
  「경합 처리」 두 줄 추가(Change Rule: 코드보다 먼저).

## 검증 (2026-10-06 UTC)

| 무엇 | rc / 결과 |
|---|---|
| `npx tsc --noEmit` (web-store) | 0 |
| `npx next lint` (web-store) | 0 — No ESLint warnings or errors |
| 로컬 vitest | 기동 불가(Node 24 `#module-evaluator`, 알려진 호스트 한계) → CI 가 권위 |
| **bite** — 커밋 `5464e7b84`(패자 표지 한 줄만 끈 트리), PR #4183 CI `Frontend unit tests` 런 37479099169 | **fail, 정확히 예상한 4셀**: `auth-callbacks` «경합 패자: error + refreshRaceLost» (`expected undefined to be true`) · `session-route-refresh-race` «loser converges» (`expected 200 to be 307`) · «winner not landed → logged out» · «dead refresh token → one retry hop». 🔵 대조군 «bare Auth.js session action → 패자가 승자를 덮는다» 는 **통과** = 하네스가 결함을 재현한다. 나머지 129 파일 통과 |
| 수정 커밋 CI | PR #4183 의 다음 런 (리뷰어가 `Frontend unit tests` 의 `session-route-refresh-race.test.ts (8 tests)` 줄 확인) |

미들웨어의 bite 는 재지 않았다: 수정 전 미들웨어는 `@/shared/auth/auth`(NextAuth 팩토리)를
불러 시험 환경에서 목 없이는 로드되지 않는다 — «IAM 에 refresh 를 보내지 않는다» 셀은 수정
후 성질의 고정이다.

## FAN-FE-027 로 옮길 것 (같은 코드 모양)

fan-platform-web 은 `auth()` 호출처가 **더 많다** — `middleware.ts:85`, `shared/auth/session.ts:72`
(`getFanSession`, RSC), `:109`(`isAuthenticated` — 헤더가 매 페이지에서 부름). 셋 다 요청 없는
`auth()` 라 refresh 를 하고 회전된 쿠키를 버린다(공개 페이지 조회마다). 옮길 것: ① 세 곳
전부 decode-only 로(판정은 `session` 콜백 규칙 재사용), ② `jwt` 콜백의 `rotation_suspect` 분류
+ `error`·`refreshRaceLost` 동시 표지, ③ `GET /api/auth/session` 래퍼(버림 → 2초 → `307
?refresh_retry=1` → 재시도 홉은 refresh 안 함, 미해결이면 쿠키 삭제), ④ 같은 대조군 셀
(bare 세션 액션이 패자로 승자를 덮는 것)을 먼저 초록으로 세운 뒤 수정 셀.
