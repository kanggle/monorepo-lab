# Task ID

TASK-FE-095

# Title

ecommerce Toss 라이브 결제 검증 중 발견된 체크아웃 차단 결함 2건 — gateway CORS 오리진 stale값 + Toss 연동 키 타입 불일치

# Status

done

# Owner

backend, frontend

# Task Tags

- code
- bugfix
- config

# Goal

라이브 검증("ecommerce Toss 라이브 켜기") 중 실제 브라우저(Playwright, headless Chromium)로 로그인 → 상품선택 → 배송지 입력 → "결제하기" 전 구간을 구동해 **두 개의 독립적인, 서로 다른 원인의 체크아웃 차단 결함**을 발견·수정했다. 둘 다 `golden-flow.spec.ts`가 "Toss 결제 단계는 외부 SDK/PG 콜백이 필요하므로 E2E 범위 외"라는 이유로 정확히 결제 직전에서 멈추도록 설계돼 있어 어떤 CI 레인도 잡지 못했다.

## 결함 1 — 주문 생성 POST가 실제 브라우저에서 항상 403

`POST /api/orders`가 gateway-service의 Spring Cloud Gateway `globalcors` CORS 필터에서 거부됨. 원인은 `.env`의 `CORS_ALLOWED_ORIGINS=http://localhost:13000,http://localhost:13001` — **TASK-MONO-024로 완전 폐지된 레거시 `PORT_PREFIX=1` 시절 값**이 Local Network Convention(Traefik) 마이그레이션 이후 갱신되지 않고 남아 있었다. 실제 접근 오리진(`http://web.ecommerce.local`, 로컬 데모 오버라이드 `http://localhost:3001`)이 목록에 전혀 없어 모든 실제 브라우저 요청이 403.

**발견을 어렵게 만든 함정**: `curl`(Origin 헤더 미첨부)로 재현하면 CORS 필터를 아예 안 타서 정상 400(VALIDATION_ERROR)이 나온다 — 재현은 오직 `Origin` 헤더를 첨부한 요청(실제 브라우저는 same-origin POST에도 항상 첨부)에서만 발생. `docker exec ... curl -H "Origin: ..."`로 gateway를 직접 때려 403을 확정 재현, `Origin` 헤더 부재 시에만 400으로 실제 핸들러에 도달함을 대조 확인. (최초 진단은 Next.js `experimental.serverActions.allowedOrigins` 미설정을 의심했으나 — 이는 **오진**이었다. `docker exec ecommerce-web-store` 컨테이너 내부에 임시 `console.error` 계측을 넣어 `backendRes.status`를 직접 로깅해 gateway 자체가 403을 반환함을 실측 확정.)

## 결함 2 — 주문은 성공(201)하지만 결제창이 항상 "네트워크 오류"로 실패

결함 1을 고치자 주문 생성은 201 Created로 성공했지만, 이어지는 `requestPayment()`(Toss SDK)가 항상 실패. 원인: `.env`의 `NEXT_PUBLIC_TOSS_CLIENT_KEY`/`TOSS_PAYMENTS_SECRET_KEY`가 **결제위젯(Payment Widget) 전용 공개 테스트 키**(`test_gck_docs_.../test_gsk_docs_...`)였는데, `use-toss-payment.ts`의 실제 연동 코드는 `toss.payment({customerKey}).requestPayment()` — **API 개별연동(결제창) 패턴**을 사용한다. 두 패턴은 서로 다른 키 타입을 요구하며 혼용 시 Toss SDK가 `"결제위젯 연동 키는 지원하지 않습니다"`로 즉시 reject한다.

**발견을 어렵게 만든 함정**: SDK 자체는 정상 로드되고(`window.TossPayments` 존재 확인됨) reject가 `useTossPayment` 훅 내부 `error` state에만 저장되고 `CheckoutForm.tsx`는 `requestPayment`만 destructure — 이 `error`를 화면에 절대 노출하지 않는다. 그 결과 증상이 항상 "주문에 실패했습니다"/"네트워크 오류가 발생했습니다" 같은 generic catch-all 메시지로만 보여 진짜 원인(SDK reject)이 콘솔에도 화면에도 안 드러남. `page.evaluate()`로 `window.TossPayments(key).payment().requestPayment()`를 앱 코드와 동일한 패턴으로 직접 재현해 정확한 reject 메시지를 확보.

# Scope

## In Scope

- `.env`(ecommerce 프로젝트 루트) — 2개 값 교정:
  - `CORS_ALLOWED_ORIGINS` → `http://ecommerce.local,http://web.ecommerce.local,http://localhost:3001`(Traefik 프로덕션 라우트 + 로컬 데모 오버라이드 포트).
  - `NEXT_PUBLIC_TOSS_CLIENT_KEY`/`TOSS_PAYMENTS_SECRET_KEY` → API 개별연동용 공개 테스트 키 쌍(`test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq` / `test_sk_zXLkKEypNArWmo50nX3lmeaxYG5R`, 다수 공개 튜토리얼에 쓰이는 Toss 공식 문서 계열 공개 sandbox 키).
- `gateway-service`, `web-store`, `payment-service` 재기동(env 반영, 코드 변경 없음).

## Out of Scope

- `use-toss-payment.ts`가 훅의 `error`를 노출하지 않는 UX 결함 자체(발견됐지만 이번 태스크는 라이브 검증 블로커 해소가 목적 — 별도 후속 태스크 후보).
- 특정 카드사(신한 등)의 ISP/앱카드 실뱅킹 인증 완료까지의 전 구간 자동화(테스트 모드라도 은행 수준 인증 시뮬레이션이라 브라우저 자동화로 완결 불가 — fan-platform PortOne KG이니시스 건과 동일한 성격의 한계).
- `golden-flow.spec.ts` 확장(결제 완료까지 커버하는 CI 스펙 추가).

# Acceptance Criteria

- [x] **AC-1** `CORS_ALLOWED_ORIGINS` 교정 — 실제 브라우저(Origin 헤더 첨부)로 `POST /api/orders`가 403이 아닌 실제 핸들러 응답(201 Created)을 반환. 실측: gateway 로그 `POST /api/orders 201 660ms`(및 그 외 여러 회).
- [x] **AC-2** Toss 키 교정 — 실제 체크아웃 플로우에서 `결제하기` 클릭 시 실제 Toss Payments sandbox 결제창(`payment-gateway-sandbox.tosspayments.com`)이 열리고 "실제 결제가 안되는 테스트입니다" 배지가 표시됨. 실측: 카드사 선택 → 필수 약관 동의 → 카드사별 인증(ISP/3DS) 리다이렉트까지 정상 진행.
- [x] **AC-3** 두 수정 모두 `.env`(로컬 전용, git-ignored) 값 교정만 — 애플리케이션 코드 변경 없음, `tsc`/`golden-flow.spec.ts` 영향 없음.
- [ ] **AC-4**(후속) — 실제 결제 완료(카드사 ISP 인증까지 포함)까지의 서버측 confirm 기록 확인은 별도 세션에서 특정 카드사의 테스트-모드 자동승인 경로를 찾거나 수동 완료 필요.

# Related Specs

- `specs/services/web-store/architecture.md`
- `specs/services/gateway-service/architecture.md`
- `TEMPLATE.md § Local Network Convention`

# Related Contracts

- N/A — 환경설정 값 교정, API 계약/코드 불변.

# Target App

- 프로젝트 루트 `.env`(공유 설정) → `gateway-service`, `web-store`, `payment-service` 전부에 영향.

# Implementation Notes

- **결함 1 실측 절차**: `docker exec ecommerce-gateway-service curl -H "Origin: http://localhost:3001" http://localhost:8080/api/products` → 403(Origin 헤더 있음) vs 헤더 없이 → 200. `route.ts`(web-store BFF 프록시)에 임시 `console.error`로 `backendRes.status`를 찍어 gateway가 403을 반환함을 확정한 뒤 되돌림(운영 코드 변경 없음 — `git diff` 로 원본과 동일함 확인).
- **결함 2 실측 절차**: `page.evaluate(() => window.TossPayments(key).payment({customerKey}).requestPayment({...}))`로 앱과 동일 패턴을 직접 재현 → `"결제위젯 연동 키는 지원하지 않습니다"` 정확한 reject 메시지 확보. 공개 테스트 키 쌍은 Toss 공식 문서 기반 다수 커뮤니티 튜토리얼(velog 등)에서 재확인.
- 두 결함 모두 원인이 서로 완전히 무관하고(하나는 gateway CORS 설정, 하나는 PG 연동 키 타입), 우연히 같은 라이브 검증 세션에서 순차로 발견됨 — 결함 1을 고치고 나서야 결함 2가 드러났다(결함 1이 막고 있는 동안은 주문 생성 자체가 안 돼 Toss SDK 호출까지 도달하지 못했음).

# Edge Cases

- `CORS_ALLOWED_ORIGINS`는 로컬 데모 포트(`3001`)와 프로덕션 Traefik 호스트(`web.ecommerce.local`, 포트 없음)를 모두 커버해야 두 환경 모두에서 회귀 없음.
- Toss 키는 **공개 docs 키**(전세계 공유, 민감정보 아님) — 데모 전용, 실서비스 절대 금지(기존 주석 방침 유지).

# Failure Scenarios

- `.env`가 git-ignored 로컬 전용 파일이라 다른 세션/머신에서 동일 stale 값이 재발할 수 있음 — `.env.example`은 이미 올바른 CORS 값을 갖고 있었으나 실제 `.env`가 그와 별개로 방치돼 있었다는 점이 핵심 교훈(TASK-MONO-024 마이그레이션 시 `.env.example`만 갱신되고 로컬 `.env`는 안 건드려짐).
- Toss 키 타입 재교정 후에도 특정 카드사가 여전히 reject하면 카드사별(신한/우리 등) 개별 정책 차이일 수 있음 — 이번 세션에서 신한/우리 둘 다 ISP 인증 리다이렉트까지는 정상 진행함을 확인.

# Test Requirements

- 실제 브라우저 기반 스크립트 검증(Playwright, Origin 헤더 포함) — 두 결함 모두 Origin-less 도구(curl 등)로는 재현 안 됨에 유의.
- 서버측 로그 대조(`docker logs ecommerce-gateway-service`)로 실제 201 Created 확인 — 클라이언트측 "성공/실패" 표시만으로 판단 금지(호스트 부하로 인한 응답 지연이 클라이언트 타임아웃을 유발해 실제로는 성공한 주문도 "네트워크 오류"로 보일 수 있었음 — 서버 로그가 권위).

# Definition of Done

- [x] `.env` `CORS_ALLOWED_ORIGINS` 교정, gateway-service 재기동, 201 Created 실측 확인
- [x] `.env` Toss 키 쌍 교정, web-store/payment-service 재기동, 실제 Toss sandbox 결제창 오픈 실측 확인
- [x] 애플리케이션 코드 변경 없음(진단용 임시 계측 전부 원복, `git diff` 로 확인)
- [ ] worktree 정리(해당 없음 — 메인 체크아웃에서 직접 작업, `.env`만 변경)
