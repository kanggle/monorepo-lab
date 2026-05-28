# Task ID

TASK-BE-144

# Title

user-service `RestProductInfoProvider` 의 `RestTemplate` → `RestClient` 마이그레이션

# Status

review

# Owner

backend

# Task Tags

- code, test, refactor

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

user-service 의 wishlist 상품정보 enrichment outbound HTTP 호출이 레거시 `RestTemplate`
(외부 prototype 이관 잔재) 을 사용한다. monorepo 내부 신규 코드(platform-console console-bff,
wms outbound TmsClient)는 모두 Spring 6 `RestClient` 를 사용하므로, 이 단일 잔존 사용처를
`RestClient` 로 정렬하여 outbound HTTP 클라이언트를 단일 표준으로 수렴한다.

순수 클라이언트 교체이며, HTTP wire 동작(요청 URL / 메서드 / 헤더 / 응답 매핑 / 에러 시
`DELETED` fallback)은 byte-identical 하게 보존한다.

---

# Scope

## In Scope

- `RestTemplateConfig` → `RestClientConfig` 로 교체 — `@Bean("wishlistRestTemplate") RestTemplate`
  를 `@Bean("wishlistRestClient") RestClient` 로 변경. 기존 connect 3s / read 5s 타임아웃 보존
  (Spring Boot 3.4 `ClientHttpRequestFactoryBuilder` + `ClientHttpRequestFactorySettings`).
- `RestProductInfoProvider` — `RestTemplate.getForObject(...)` 를
  `RestClient.get().uri(...).retrieve().body(...)` 로 교체. `@Qualifier` 를 `wishlistRestClient`
  로 갱신. 병렬 fan-out / `DELETED` fallback / null 처리 로직은 무변경.
- `RestProductInfoProviderUnitTest` — `RestTemplate` mock 을 `RestClient` fluent-chain mock 으로 교체.
  동일한 5개 시나리오(다중 조회 / 빈 입력 / 실패 / null 응답 / 부분 실패) 보존.
- `specs/services/user-service/dependencies.md` — "Allowed Service Interactions" 의
  `RestTemplate` 언급을 `RestClient` 로 갱신 (spec/code drift 방지).

## Out of Scope

- Resilience4j (circuit breaker / retry / bulkhead) 도입 — 현행 동작에 없음, 별건.
- product-service 호출 계약(`product-api.md`) 변경.
- 다른 프로젝트(ecommerce 외)의 HTTP 클라이언트.
- 타임아웃 값 자체의 튜닝.

---

# Acceptance Criteria

- [x] production 코드에 `RestTemplate` 타입 참조가 0 건 (user-service main).
- [x] `wishlistRestClient` `RestClient` bean 이 connect 3s / read 5s 타임아웃을 유지한다.
- [x] `RestProductInfoProvider` 가 동일 URL(`{base}/api/products/{productId}`) / GET / `ProductDetailResponse`
      매핑 / 에러·null 시 `DELETED` fallback 동작을 보존한다.
- [x] 단위 테스트 5개 시나리오 전부 통과한다.
- [x] `:user-service:test` (또는 `check`) 그린.
- [x] `dependencies.md` 가 `RestClient` 를 반영한다.

---

# Related Specs

- `specs/services/user-service/dependencies.md` (§ Allowed Service Interactions — 본 task 에서 갱신)
- `specs/services/user-service/architecture.md` (§ Domain Scope — wishlist product enrichment)

# Related Contracts

- `specs/contracts/http/product-api.md` — `GET /api/products/{productId}` (호출 계약, 변경 없음)

---

# Edge Cases

- 빈 productId 집합 → HTTP 호출 없이 빈 맵 반환 (보존).
- product-service 4xx/5xx 또는 연결 실패 → `RestClient.retrieve()` 가 예외 throw → catch → `DELETED`
  (RestTemplate `getForObject` 의 4xx/5xx throw 동작과 동치).
- 빈 응답 본문 → `body()` null → `DELETED` (보존).
- 일부만 실패 → 성공분 정상 + 실패분 `DELETED` (보존).

---

# Failure Scenarios

- `RestClient.Builder` autoconfigured bean 미주입 → bean 정의에서 명시 주입으로 해결 (web starter 존재 확인됨).
- 타임아웃 API 가 Boot 3.4 에서 deprecated 경로 사용 시 경고 → `org.springframework.boot.http.client`
  신 API 사용으로 회피.

---

# Test Requirements

- `RestProductInfoProviderUnitTest` 5 시나리오 통과.
- `:user-service:test` 전체 그린, 회귀 0.

---

# Verification

- 2026-05-29, `task/be-144-ecommerce-restclient-migration` 브랜치 (off main).
- 변경 파일:
  - `RestTemplateConfig.java` 삭제 → `RestClientConfig.java` 신규 (`@Bean("wishlistRestClient")`,
    Boot 3.4 `ClientHttpRequestFactoryBuilder.detect().build(settings)` + connect 3s / read 5s).
  - `RestProductInfoProvider` — `RestTemplate.getForObject` → `RestClient.get().uri(...).retrieve().body(...)`,
    `@Qualifier("wishlistRestClient")`. 병렬 fan-out / `DELETED` fallback 무변경.
  - `RestProductInfoProviderUnitTest` — `RestClient` fluent-chain mock (per-id `uri()` 분기 helper).
    5 시나리오 보존.
  - `specs/services/user-service/dependencies.md` — `RestTemplate` → `RestClient`.
- `:projects:ecommerce-microservices-platform:apps:user-service:test` **BUILD SUCCESSFUL** (Docker-free,
  `@Tag("integration")` 제외 = CI 동치). 회귀 0.
- user-service main 의 `RestTemplate` / `wishlistRestTemplate` grep = 0 건.
- 분석=Opus 4.7 / 구현=Opus 4.7 — small mechanical refactor (구현 권장은 Sonnet 4.6 이었으나 동일 세션 직접 처리).
