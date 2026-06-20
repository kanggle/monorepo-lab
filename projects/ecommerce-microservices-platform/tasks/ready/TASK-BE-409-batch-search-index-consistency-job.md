---
id: TASK-BE-409
title: batch-worker — searchIndexConsistencyCheckJob 구현 + 스케줄링 스캐폴딩(ShedLock)
status: ready
project: ecommerce-microservices-platform
service: batch-worker
type: feature
created: 2026-06-20
---

# TASK-BE-409 — batch-worker: searchIndexConsistencyCheckJob + 스케줄링 스캐폴딩

## Goal

ecommerce batch-worker 의 3개 명세 잡 중 **유일하게 컨트랙트가 갖춰진** `searchIndexConsistencyCheckJob`(ES 인덱스 정합성 점검)을 구현하고, 모든 잡이 공유할 스케줄링 스캐폴딩(@EnableScheduling 재활성 + ShedLock 단일리더 가드)을 함께 도입한다. Job1(staleOrderCancel)·Job2(dailySalesAgg)는 컨트랙트/스키마 미정으로 본 task 범위에서 제외하고 별도 spec-clarification task(TASK-BE-410/411)로 분리한다.

## Background

- batch-worker scaffolding(BE-049/064)은 `BatchJobExecution` 도메인·JPA·history 테이블·application.yml 까지 갖췄으나 `@EnableScheduling` 은 BE-064 가 제거(실 잡 부재), `scheduling/`·`application/` 은 `.gitkeep` 만.
- `dependencies.md` 는 batch-worker 의 HTTP 소비자로 **product-service·search-service 만** 인가 → Job3 만 컨트랙트 충족. (Job1=order-service, Job2=order/payment-service 는 미인가 → 차단, BE-410/411 로.)
- `platform/service-types/batch-job.md`: "주어진 잡은 동시 1 인스턴스만 실행 — 분산락(ShedLock 등) 필수." 참조구현=shipping-service(BE-360) ShedLock 패턴.
- Job3 명세: `overview.md` "product-service 와 search-service HTTP read-only 조회 후 drift 검출", `@Scheduled(cron="0 0 3 * * *")`, `observability.md` `batch_index_inconsistencies_detected_total` 카운터.
- **Job3 한계(명시)**: search-api 는 keyword 검색(`GET /api/search/products?q=`)만 노출, 전수 인덱스 enumeration 엔드포인트 부재 → 정합성 점검은 product-service 권위 카탈로그를 페이지네이션하며 각 상품을 search 로 spot-check(이름 질의 후 존재 확인)하는 **heuristic** 방식. 이 한계를 코드 주석 + spec 노트로 명시한다(silent 과신 금지).

## Scope

- **IN (공유 스캐폴딩)**: `build.gradle` ShedLock 의존성(shipping-service 와 동일 버전); `BatchWorkerApplication` 에 `@EnableScheduling` 재추가; `V2__create_shedlock_table.sql`; `SchedulerConfig`(@EnableSchedulerLock + JdbcTemplateLockProvider). 
- **IN (Job3)**: `infrastructure/client/ProductServiceClient`·`SearchServiceClient`(RestClient, review-service 패턴); `application/SearchIndexConsistencyJob`(페이지네이션+spot-check+drift 집계+BatchJobExecution history+`batch_index_inconsistencies_detected_total` 메트릭); `scheduling/SearchIndexConsistencyScheduler`(@Scheduled cron + @SchedulerLock); application.yml 에 product/search base-url + 잡 enable 플래그.
- **OUT**: Job1(staleOrderCancel)·Job2(dailySalesAgg) 구현(BE-410/411 차단해소 후). order/payment-service 호출. 신규 출력 테이블(Job3 는 메트릭+로그+history 만, 테이블 불요).

## Acceptance Criteria

- [ ] **AC-1 (스캐폴딩)**: `build.gradle` 에 `shedlock-spring`+`shedlock-provider-jdbc-template`(shipping-service 와 동일 버전); `BatchWorkerApplication` `@EnableScheduling` 재추가; `V2__create_shedlock_table.sql`(name/lock_until/locked_at/locked_by, PK name); `infrastructure/config/SchedulerConfig`(`@EnableSchedulerLock(defaultLockAtMostFor=...)` + `JdbcTemplateLockProvider` `.usingDbTime()`).
- [ ] **AC-2 (HTTP 클라이언트)**: `ProductServiceClient.listOnSale(page,size)` → `GET /api/products?page=&size=&status=ON_SALE`(public, 무인증); `SearchServiceClient.searchByName(name)` → `GET /api/search/products?q={name}`. review-service `OrderServiceClient` 의 RestClient 패턴 미러. 호출 실패는 잡 실패로 격리(전파 안 함).
- [ ] **AC-3 (잡 로직)**: `application/SearchIndexConsistencyJob.execute()` — product-service 를 페이지네이션하며 각 ON_SALE 상품을 search spot-check(이름 질의 결과에 해당 상품 부재=inconsistency). drift 카운트를 `batch_index_inconsistencies_detected_total`(Micrometer Counter) 에 기록 + `BatchJobExecution.start/complete/fail` 라이프사이클로 history 저장. read-only(무변경) → idempotent. heuristic 한계 주석 명시.
- [ ] **AC-4 (스케줄러+락)**: `scheduling/SearchIndexConsistencyScheduler` `@Scheduled(cron="0 0 3 * * *")` + `@SchedulerLock(name="batch-search-index-consistency-check", lockAtMostFor="PT30M", lockAtLeastFor="PT5S")`, 얇게 `SearchIndexConsistencyJob.execute()` 위임.
- [ ] **AC-5 (테스트)**: 단위 `SearchIndexConsistencyJobTest`(client/repo/MeterRegistry mock — 빈 카탈로그→0 inconsistency·COMPLETED; 3상품 중 1 미발견→메트릭+1·COMPLETED; search 예외→FAILED history·예외 비전파). 통합(`@Tag("integration")`, `AbstractIntegrationTest` 확장, WireMock/MockWebServer 로 product/search stub)에서 `execute()` 직접 호출(ShedLock 우회=shipping-service 패턴), history COMPLETED + 메트릭 검증.
- [ ] **AC-6 (Job3 한계 spec 노트)**: `specs/services/batch-worker/overview.md`(또는 architecture.md)의 Job3 항목에 "search-api 전수조회 부재 → product 권위 카탈로그 기준 spot-check heuristic" 1줄 노트 추가.
- [ ] **AC-7**: `:batch-worker:test` GREEN(단위+컴파일). Testcontainers IT 는 CI Linux 권위.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/batch-worker/overview.md` / `architecture.md` / `dependencies.md` / `observability.md`
- `projects/ecommerce-microservices-platform/specs/contracts/http/product-api.md` / `search-api.md`

## Related Contracts

- product-service `GET /api/products` (기존), search-service `GET /api/search/products?q=` (기존). 둘 다 dependencies.md 인가됨.

## Edge Cases

- product-service/search-service 다운 → 잡 FAILED history 기록, 예외 비전파(다음 cron 재시도). 
- ES drift 0 → 정상 COMPLETED, 메트릭 0.
- 멀티 replica → ShedLock 으로 1 인스턴스만 실행(AC-1/4).
- heuristic 위양성(이름 검색 relevance 로 존재하나 매칭 실패) → 노트로 한계 명시, 메트릭은 "의심 drift" 로 해석.

## Failure Scenarios

- @EnableScheduling 누락 → 잡 미실행. 
- ShedLock 누락 → 멀티 replica 중복 실행(배치잡 불변식 위반).
- search 전수조회 부재를 무시하고 "완전 정합 보장"으로 기술 → 오신뢰. AC-6 노트 필수.
- 테스트가 ShedLock 경유 스케줄러를 호출 → 로컬 락 트랩. `execute()` 직접 호출 필수.
