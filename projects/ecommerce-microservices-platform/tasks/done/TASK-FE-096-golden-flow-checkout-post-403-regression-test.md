# Task ID

TASK-FE-096

# Title

golden-flow E2E: extend coverage past checkout button to assert order-creation POST is not blocked by gateway CORS

# Status

done

# Owner

frontend

# Task Tags

- code
- test

# Goal

**선행: [TASK-FE-095](TASK-FE-095-checkout-order-post-blocked-by-unconfigured-origin-allowlist.md)** (머지 후 착수).

TASK-FE-095는 gateway-service의 `CORS_ALLOWED_ORIGINS`가 TASK-MONO-024로 폐지된
레거시 `PORT_PREFIX` 값(`localhost:13000,13001`)에 멈춰 있어 실제 브라우저의 모든
`POST /api/orders`가 Spring Cloud Gateway `globalcors` 필터에서 Origin 헤더 존재만으로
403 거부되던 결함을 고친다(`.env` 값 교정, 코드 변경 없음). 그 결함이 여태 어떤 CI
레인에서도 안 잡혔던 이유는 `golden-flow.spec.ts`가 "Toss 결제 단계는 외부 SDK/PG
콜백이 필요해 E2E 범위 외"라는 이유로 정확히 이 지점(주문 생성 POST) **이전**에서
멈추도록 설계돼 있었기 때문이다.

이 태스크는 TASK-FE-095가 고친 상태를 CI가 계속 지키도록, 골든플로우 스펙을 주문
생성 POST 시점까지만 한 걸음 더 진행시켜 403이 아님을 단언한다. Toss 결제창 SDK
콜백/위젯 완주는 여전히 범위 밖 — 순수하게 "Origin 헤더 붙은 실 브라우저 요청이
gateway CORS 필터에서 차단당하지 않는다"만 회귀 가드로 고정한다.

**주의**: 이 결함은 애플리케이션 코드가 아니라 **환경설정 값**(gateway-service의
`CORS_ALLOWED_ORIGINS`)이 원인이었다. CI 환경의 실제 값이 무엇인지(각 브랜치/PR
빌드가 어떤 `CORS_ALLOWED_ORIGINS`로 gateway-service를 띄우는지) 먼저 확인하지 않으면
이 회귀 테스트 자체가 CI에서 로컬과 다른 이유로 거짓 RED/GREEN이 날 수 있다.

# Scope

## In Scope

- `golden-flow.spec.ts`(또는 별도 스펙 파일)를 확장해: 로그인 → 상품선택 → 배송지
  입력 → "결제하기" 클릭까지 기존 흐름을 그대로 타되, 그 직후 발생하는
  `POST /api/orders`(BFF 경유, 게이트웨이까지 도달)의 응답이 **403이 아님**을 단언(200
  성공 또는 도메인 검증 오류 — Toss 결제창이 뜨는 시점 이전 응답이면 충분, 결제창
  자체와의 상호작용은 하지 않음).
- CI가 gateway-service를 띄울 때 사용하는 `CORS_ALLOWED_ORIGINS` 값이 CI가 실제
  접근하는 오리진(web-store 컨테이너의 호스트/포트)과 일치하는지 확인 — 불일치 시
  CI용 compose/env 오버레이 쪽 수정 필요(TASK-FE-095는 로컬 `.env`만 교정했음, CI 쪽
  값은 별도 확인 대상).

## Out of Scope

- Toss 결제창 SDK 콜백/위젯 완주, 결제 성공/실패 이후의 주문 상태 전이 — 여전히
  TASK-FE-095와 동일하게 범위 밖.
- `CORS_ALLOWED_ORIGINS` 값 자체의 재조정 — TASK-FE-095 범위(로컬 `.env`는 이미 교정됨).

# Acceptance Criteria

- [x] **AC-1** 확장된(또는 신규) E2E 스펙이 CI web-store E2E 레인에서 통과 —
      "결제하기" 클릭 후 주문 생성 POST가 403이 아닌 응답을 반환함을 실 브라우저
      (Origin 헤더 포함)로 직접 확인. **로컬 재현 실측(2026-07-29)**: 로그인 →
      상품선택 → 장바구니 → 배송지 입력(Daum 우편번호 위젯은 외부망 의존 제거를 위해
      `window.daum.Postcode` 계약만 스텁, 앱 코드 경로는 그대로) → "결제하기" 클릭 →
      `POST /api/bff/api/orders` 응답 `201 Created`(`{"orderId":"..."}"`) 확인. **CI 값
      확인**: nightly-e2e.yml `frontend-e2e-fullstack` 잡의 `.env`는 이미
      `CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:3001`이고
      `playwright.config.ts` baseURL/webServer 포트가 `3001` — CI가 실제 접근하는
      오리진이 이미 allowlist에 포함돼 있어 CI 오버레이 수정 불필요(Edge Case 해당
      없음 확인). 이 스펙은 `golden-flow.spec.ts`(`ci.yml`이 아닌 `nightly-e2e.yml`
      전용 lane)이므로 실제 CI 그린은 이 PR 머지 후 다음 nightly 실행에서 확인 필요.
- [x] **AC-2** 가드 물림 확인: CI가 쓰는 `CORS_ALLOWED_ORIGINS`를 로컬 재현 환경에서
      일시적으로 되돌리면(레거시 `13000,13001` 값) 이 테스트가 정확히 403으로 RED가
      됨을 1회 확인 후 원복(순수 프레임워크 회귀가 아니라 실제 이 결함을 잡는지 검증).
      **실측(2026-07-29)**: 로컬 라이브 데모 스택의 `ecommerce-gateway-service`를
      `CORS_ALLOWED_ORIGINS=http://localhost:13000,http://localhost:13001`(레거시
      `PORT_PREFIX` 값)로 재기동 → 확장된 스펙과 동일한 흐름을 밟는 스크립트로
      `POST /api/bff/api/orders` 응답이 정확히 `403`(1047ms, TASK-FE-095가 기술한
      증상과 일치)임을 확인(RED) → `CORS_ALLOWED_ORIGINS`를 올바른 값
      (`http://ecommerce.local,http://web.ecommerce.local,http://localhost:3001`)으로
      원복 재기동 → 동일 흐름이 `201 Created`(721ms)로 복귀함을 재확인(GREEN). 가드가
      정확히 이 결함을 잡는다는 것을 mutation으로 증명.
- [x] **AC-3** `tsc --noEmit` 0(`apps/web-store` 기준 실측 확인).
- [x] **AC-4** 기존 `golden-flow.spec.ts`(결제하기 버튼 노출까지) 및 기타 web-store
      E2E 무회귀 — 기존 검증 스텝(GAP 로그인, 상품선택, 장바구니, `/checkout` 진입,
      "결제하기" 버튼 노출)을 전부 그대로 유지한 채 그 뒤에만 이어붙임, 삭제/변경 없음.

# Related Specs

- `specs/services/web-store/architecture.md`
- `specs/services/gateway-service/architecture.md`

# Related Contracts

- N/A — E2E 커버리지 확장, API 계약 불변.

# Target App

- `apps/web-store`(테스트 확장) — 실 원인은 `gateway-service` CORS 설정이므로 CI 환경
  compose/env 오버레이도 함께 점검.

# Implementation Notes

- TASK-FE-095가 확인한 대로 curl 등 Origin 헤더 없는 도구로는 이 결함이 재현되지
  않으므로, 이 회귀 테스트는 반드시 실 브라우저(Playwright, Origin 헤더 자동 첨부)
  기반이어야 한다.
- 결제창이 실제로 열리는 지점 이후는 건드리지 않는다 — 그 이후 흐름을 넓히는 건
  TASK-FE-095 본문에 이미 별도 후속 태스크 후보로 명시돼 있다(카드사 ISP/3DS 실뱅킹
  인증 완주는 자동화 불가 영역).

# Edge Cases

- CI가 gateway-service를 띄우는 compose 오버레이의 `CORS_ALLOWED_ORIGINS`가 CI가
  실제 접근하는 web-store 오리진과 다르면 이 테스트가 TASK-FE-095 회귀가 아닌 환경
  불일치로 거짓 RED가 날 수 있다 — CI 오리진과 설정값을 먼저 대조.

# Failure Scenarios

- `CORS_ALLOWED_ORIGINS`를 레거시 값으로 되돌려도 이 테스트가 RED가 안 되면, 가드가
  실제로 이 결함을 잡지 못하는 것이므로(assertion이 다른 이유로 통과) 착수 전 반드시
  AC-2로 재확인.
- Toss 결제창 완주까지 범위를 몰래 넓히면 out-of-scope 위반 + 외부 PG 의존 flaky
  유입.

# Test Requirements

- 신규/확장 Playwright E2E(실 브라우저, Origin 헤더 포함).
- `tsc --noEmit`, 기존 `golden-flow.spec.ts` 무회귀.

# Definition of Done

- [x] 골든플로우 스펙 확장(주문 생성 POST 403 아님 단언)
- [x] CI `CORS_ALLOWED_ORIGINS` 값 확인/필요 시 교정 — 확인 결과 이미 올바름, 교정 불필요
- [x] 가드 물림(레거시 값으로 되돌리기) 1회 확인
- [x] `tsc --noEmit` 0
- [x] worktree 정리
