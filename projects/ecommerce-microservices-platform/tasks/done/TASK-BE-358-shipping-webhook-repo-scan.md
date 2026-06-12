# Task ID

TASK-BE-358

# Title

shipping-service 부팅 실패 핫픽스 — `@EnableJpaRepositories` 스캔 범위가 `infrastructure.persistence` 로 한정되어 BE-294 가 `infrastructure.webhook` 에 추가한 `ProcessedCarrierWebhookJpaRepository` 빈이 미생성 → APPLICATION FAILED TO START → 풀스택에서 crash-loop/unhealthy → web-store nightly RED.

# Status

done

# Owner

backend (Opus 4.8 analysis + impl, 로컬 재현 검증). 프로젝트 내부(ecommerce). 1-라인 핫픽스.

# Task Tags

- code

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **선행/원인**: TASK-BE-294 (`shipping carrier webhook ingestion`, #1359, 2026-06-12 머지) — 멱등 store 의 `ProcessedCarrierWebhookJpaRepository` 를 `com.example.shipping.infrastructure.webhook` 패키지에 추가했으나, `ShippingServiceApplication` 의 `@EnableJpaRepositories(basePackages = "com.example.shipping.infrastructure.persistence")` 가 그 패키지를 스캔하지 않음.
- **노출 경로**: web-store nightly(frontend-e2e-fullstack) — 2026-06-11 20:30 GREEN → 06-12 전부 RED. shipping-service 컨테이너가 unhealthy → `docker compose up` 실패.

# Goal

`ShippingServiceApplication` 의 `@EnableJpaRepositories` basePackages 를 `com.example.shipping.infrastructure` 로 광역화하여 `infrastructure` 하위의 모든 Spring Data 리포지토리(`persistence.ShippingJpaRepository` + `webhook.ProcessedCarrierWebhookJpaRepository` + 향후 추가분)가 빈으로 생성되도록 한다. 이로써 `JpaWebhookDeliveryStore → ProcessCarrierWebhookService` 의존성이 충족되어 애플리케이션 컨텍스트가 정상 기동하고, 풀스택 compose 에서 healthy 가 된다.

# Scope

## In Scope

- **`ShippingServiceApplication.java`** — `@EnableJpaRepositories(basePackages = "com.example.shipping.infrastructure.persistence")` → `"com.example.shipping.infrastructure"`. 회귀 경위 주석 추가.

## Out of Scope

- BE-294 의 webhook 도메인/엔티티/마이그레이션(V5)·컨트롤러·verifier 로직 불변(전부 정상).
- `@EntityScan`(이미 `com.example.shipping` 광역) 불변.
- carrier-aggregator(ADR-007) 후속 기능 — 별건.
- web-store nightly 의 다른 잡(이미 별도 처리됨)·CI 통합잡 부재 보강(별도 검토).

# Acceptance Criteria

- [ ] `@EnableJpaRepositories` basePackages 가 `com.example.shipping.infrastructure`.
- [ ] shipping-service 가 풀스택 compose 에서 정상 기동: `Started ShippingServiceApplication` + `/actuator/health` = `{"status":"UP"}` + 컨테이너 healthy (crash-loop 없음, restarts=0).
- [ ] `ProcessedCarrierWebhookJpaRepository` / `ShippingJpaRepository` 양쪽 빈 정상 생성(`No qualifying bean` 미발생).
- [ ] shipping-service `test` GREEN(기존 단위/슬라이스 무회귀).
- [ ] 다음 nightly-e2e 의 web-store frontend-e2e-fullstack 잡에서 shipping-service 가 healthy → compose up 통과.

# Verification (이미 수행)

- 로컬 재현(plain `docker compose`, origin/main 코드): **수정 전** = `APPLICATION FAILED TO START: ProcessedCarrierWebhookJpaRepository 빈 없음`, restarts=8, unhealthy. **수정 후** = `Started ShippingServiceApplication in 98.7s`, `/actuator/health {"status":"UP"}`, healthy, restarts=0.

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/shipping-service/overview.md` (webhook ingestion — BE-294). 동작 변경 아님(빈 배선 버그 수정) → 스펙 변경 불요.

# Related Contracts

- 변경 없음.

# Target Service

- shipping-service (ecommerce). Service Type 변경 없음.

# Architecture

- Spring Boot 에서 `@EnableJpaRepositories(basePackages=...)` 를 명시하면 그 패키지 트리에서만 Spring Data 리포지토리 프록시가 생성된다. 본 서비스는 `infrastructure.persistence` 로 좁게 한정해 두었는데, BE-294 가 webhook 멱등 리포지토리를 `infrastructure.webhook` 에 신설하면서 스캔 범위 밖에 놓였다. 단위/슬라이스 테스트는 store/repo 를 mock 하고, ecommerce 는 실 부팅을 검증하는 full-context `@SpringBootTest` 가 CI 게이트(`-PrunIntegration` 미실행)가 아니라, 이 배선 결함은 nightly 풀스택 compose 에서만 드러났다. 스캔 범위를 `infrastructure` 로 올려 동일 클래스의 재발(향후 새 infra 서브패키지 리포지토리)을 예방한다.

# Edge Cases

- `infrastructure` 하위에 Spring Data 리포지토리가 아닌 인터페이스가 있어도 `@EnableJpaRepositories` 는 Spring Data `Repository` 상속분만 프록시화하므로 영향 없음.
- `@EntityScan` 은 이미 `com.example.shipping` 광역이라 엔티티 스캔은 무관(이번 변경 대상 아님).

# Failure Scenarios

- webhook 패키지만 추가하고 광역화 안 함(`{persistence, webhook}` 명시) → 동작은 동일하나 향후 새 서브패키지에서 재발. 본 task 는 `infrastructure` 광역으로 근본 예방.
- basePackages 오타 → 모든 리포지토리 미스캔으로 부팅 실패. AC 가 양쪽 빈 생성 + healthy 부팅으로 보증.

# Definition of Done

- [ ] `@EnableJpaRepositories` → `com.example.shipping.infrastructure` + 경위 주석
- [ ] 로컬 풀스택 기동 healthy 재현 검증(완료)
- [ ] shipping-service test GREEN
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
