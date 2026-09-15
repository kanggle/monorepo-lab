# Task ID

TASK-FE-100

# Title

로그인 안 해도 보여야 할 상품 리뷰가 `/login` 으로 튕긴다 — 낡은 세션 토큰이 공개 조회까지 401 로 만든다

# Status

in-progress

# Owner

frontend

# Task Tags

- code
- test

---

# 배경

상품 리뷰 목록·요약은 **공개 조회**다. 세 층이 모두 그렇게 말하고 있고 실제로 열려 있다:

- 계약 `specs/contracts/http/review-api.md` — *"Product review listing and summary are public endpoints (no auth required)."*
- 게이트웨이 `SecurityConfig` — `GET /api/reviews/products/**` → `permitAll()`
- web-store `middleware.ts` — `/products`, `/api/bff` 는 게이팅하지 않는다

그런데 web-store 는 클라이언트 컴포넌트의 모든 백엔드 호출을 같은 오리진 BFF 프록시
(`/api/bff/[...path]`)로 보내고, 그 프록시는 **세션 쿠키에 토큰이 있으면 무조건 Bearer 를 붙인다.**

## 기전 (코드로 확인)

1. `getWebStoreSession()` → `decodeServerJwt()` 는 NextAuth 쿠키를 **디코드만** 한다. 갱신은
   `jwt` 콜백에서만 일어나므로, 만료된 access token 이 그대로 나올 수 있다(데모처럼 IAM 서명키가
   부팅마다 바뀌면 **서명이 무효한** 토큰도 나온다).
2. Spring Security 리소스 서버는 Authorization 헤더가 **있으면** 인가 판단 전에 먼저 검증하고,
   무효하면 `permitAll` 경로에서도 **401** 을 낸다.
3. BFF 는 백엔드 401 을 무조건 `401 X-Reauth: 1` 로 바꾼다.
4. 클라이언트 `ApiClient` 는 `/api/reviews` 가 `publicPaths` 에 없으므로 `onAuthError` →
   `window.location.href = '/login?from=…'`.

⇒ **한 번 로그인했다가 토큰이 낡은 방문자**가 상품 상세를 열면 리뷰를 보는 대신 로그인 화면으로
끌려간다. 순수 익명(쿠키 없음)은 1단계에서 토큰이 없어 통과하므로 재현이 조건부다.

⚪ **라이브 재현은 못 했다** — 이 호스트의 Docker 데몬이 꺼져 있어 스택을 띄울 수 없었다. 기전은
코드 경로로 특정했고, 판정은 BFF 단위 테스트(CI)로 한다.

## 왜 공개 경로 목록을 BFF 에 복제하지 않는가

「어느 경로가 공개인가」는 게이트웨이 `SecurityConfig` 가 권위다. BFF·`ApiClient.publicPaths`
에 사본을 두면 게이트웨이가 경로를 열거나 닫을 때 한쪽만 고쳐진다. 그래서 BFF 는 **경로를 몰라도
되는 규칙**을 쓴다: *안전한 메서드(GET/HEAD)가 토큰을 붙인 채 401 을 받으면, 토큰 없이 한 번 더
묻는다.* 공개면 게이트웨이가 답을 주고, 보호 경로면 다시 401 이 나서 기존 재인증 흐름으로 간다.

---

# Goal

세션 토큰이 만료·무효인 방문자도 로그인 없이 상품 리뷰 목록과 평점 요약을 볼 수 있다.

---

# Scope

## In Scope

- BFF 프록시: GET/HEAD 가 Bearer 를 붙인 채 401 을 받으면 Bearer 없이 1회 재시도
- 재시도 결과가 401 이 아니면 그대로 전달, 여전히 401 이면 기존 `REAUTH_REQUIRED` 신호
- BFF 단위 테스트 추가

## Out of Scope

- 게이트웨이 보안 설정 변경 (이미 공개 — 변경 불필요)
- `ApiClient.publicPaths` 기본값 변경 (공유 패키지, 경로 목록 사본을 늘리는 방향이라 채택 안 함)
- NextAuth 쿠키 디코드 시점의 선제 토큰 갱신 (별개 설계 — 세션 전반의 동작을 바꾼다)

---

# Acceptance Criteria

- [x] **AC-0 (재측정)** — 계약·게이트웨이·미들웨어가 리뷰 조회를 이미 공개로 두고 있음을 확인하고,
      401 이 어느 층에서 생기는지 코드로 특정한다 (배경 § 기전)
- [x] **AC-1** — GET 이 Bearer 를 붙인 채 401 을 받으면 Bearer **없이** 한 번 더 요청하고, 그 응답
      (200)을 status·본문 그대로 돌려준다
- [x] **AC-2** — 재시도도 401 이면 기존과 같이 `401` + `X-Reauth: 1` + `REAUTH_REQUIRED`
- [x] **AC-3** — POST/PUT/PATCH/DELETE 는 재시도하지 않는다 (업스트림 호출 1회)
- [x] **AC-4** — 세션 토큰이 없어 Bearer 를 안 붙인 요청은 401 이어도 재시도하지 않는다 (호출 1회)
- [x] **AC-5** — 기존 `bff-proxy.test.ts` 케이스가 **수정 없이** 통과한다
- [ ] **AC-6** — `tsc --noEmit` · `next lint` 로컬 통과, 단위 테스트는 CI `frontend-unit-tests`(Node 20) 초록

---

# Related Specs

- `projects/ecommerce-microservices-platform/specs/contracts/http/review-api.md` (공개 조회 선언)
- `projects/ecommerce-microservices-platform/apps/gateway-service/src/main/java/com/example/gateway/config/SecurityConfig.java`
- `projects/ecommerce-microservices-platform/apps/web-store/src/middleware.ts`
- `projects/ecommerce-microservices-platform/apps/web-store/src/app/api/bff/[...path]/route.ts`

# Related Contracts

- 없음 (계약은 이미 공개 조회를 선언하고 있다 — 구현이 계약에 맞춰지는 방향)

---

# Edge Cases

- 보호된 GET(`/api/orders` 등) + 낡은 토큰 — 재시도도 401 → 재인증 흐름 그대로
- 공개 GET + 유효 토큰 — 첫 요청이 200 이라 재시도 없음 (추가 비용 0)
- 재시도 중 업스트림 네트워크 실패 — 502 (첫 요청 실패와 동일 처리)
- 재시도 응답이 403/404 등 — 그대로 전달

# Failure Scenarios

- **POST 를 재시도한다** → 쓰기가 익명으로 한 번 더 나가 부작용이 중복될 수 있다. AC-3 이 잡는다
- **토큰 없는 요청까지 재시도한다** → 같은 요청이 두 번 나가 401 응답 비용만 2배. AC-4 가 잡는다
- **공개 경로 목록을 BFF 에 하드코딩한다** → 게이트웨이와 목록이 갈라진다. 설계에서 배제

# Test Requirements

- `bff-proxy.test.ts` 에 AC-1~AC-4 케이스 추가

# Definition of Done

- [x] 수정 + 테스트
- [ ] 게이트 통과
- [x] Ready for review
