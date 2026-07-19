# Task ID

TASK-BE-529

# Title

ecommerce backend Tier A refactoring sweep — dead-code deletion (review/gateway/payment/settlement) + PageQuery.of lib alignment (user)

# Status

done

# Owner

backend

# Task Tags

- code
- refactor

# Goal

12-서비스 백엔드 리팩토링 전수 스캔의 **Phase 1 — Tier A**. 순수 dead-code 삭제 + lib 정렬만 묶은 최저위험 배치(파이프라인 검증용 선행). 동작 무변경(`platform/refactoring-policy.md` 준수). 스캔의 후속(Tier B 서비스별 추출 / Tier C 민감 항목)은 별도 task.

# Scope

## In Scope (category=dead-code, 서비스별 1커밋)

- **review-service** `ReviewJpaRepository`: 멀티테넌시(BE-403/ADR-MONO-030 facet c) 전 잔재인 non-tenant-scoped derived query **7개 삭제** — `findByIdAndStatus`, `existsByUserIdAndProductIdAndStatus`, `findByProductIdAndStatus`, `findByUserIdAndStatus`, `averageRatingByProductIdAndStatus`, `countByProductIdAndStatus`, `ratingDistributionByProductIdAndStatus`. `…AndTenantId` 형제가 대체(live). 콜러 0(main+test grep 검증, `name(` 로 tenant 형제와 구분). stale 주석 동반 제거.
- **gateway-service** `RateLimiterConfig.ipKeyResolver`: BE-405(`tenantRouteKeyResolver` `@Primary`)가 고아화한 bean. `application.yml` 미참조, prod 콜러 0(javadoc 언급+테스트만) → 클래스 + `RateLimiterConfigTest` 삭제.
- **gateway-service** `RouteService.isPublicRoute`: prod 콜러 0(실제 공개경로 판정은 `SecurityConfig.PUBLIC_PATHS`), 테스트만 호출 → 메서드 삭제 + 미사용 `PATH_MATCHER`/import 정리 + `RouteServiceTest`의 isPublicRoute 4 테스트 제거(`resolveTargetService` 테스트 보존).
- **payment-service** `Payment.isPending()/isCompleted()`: 콜사이트 0(main+test) → 삭제.
- **settlement-service** `QuerySettlementPeriodUseCase.getPeriod`: 콜러 0(엔드포인트 없음), 테스트 없음 → 삭제 + 미사용 import(`PeriodNotFoundException`, `SettlementPeriod`) + javadoc {@link} 정리.

## In Scope (category=duplication/lib-alignment, 별도 커밋)

- **user-service** `UserProfileService.listUsers` / `WishlistService.getWishlist`: 인라인 clamp(`Math.max(page,0)` / `Math.max(Math.min(size,100),1)` + `new PageQuery(...)`)를 `libs/java-common`의 `PageQuery.of(...)`로 교체(byte-동치 clamp, MAX_SIZE=100). straggler(settlement/notification은 이미 lib 사용). WishlistService는 다운스트림 `pageQuery.page()/size()` 재참조로 clamp 값 보존.

## Out of Scope

- Tier B 서비스 내 진짜 중복 추출(admin-role validator, TenantContext.runWithTenant, dedupe helper 등) — Phase 2 별도 task.
- Tier C 행위 민감 항목(payment confirm() extract, order cancel-tail, settlement entity-split, product clamp, gateway swagger) — Phase 3.
- admin-role 체크의 **크로스-서비스** 통합(libs 정책 HARDSTOP-03, 선례 R-12/R-20) — OUT.

# Acceptance Criteria

- [ ] **AC-1** review 7 non-tenant query 삭제, tenant-scoped 형제 전량 보존, 콜러 잔여 0.
- [ ] **AC-2** gateway `RateLimiterConfig`(+테스트) 삭제, `RouteService.isPublicRoute`(+테스트 4건) 삭제, `resolveTargetService` 경로 보존.
- [ ] **AC-3** payment `isPending/isCompleted` 삭제, settlement `getPeriod` 삭제(+import/javadoc 정리).
- [ ] **AC-4** user 두 서비스 `PageQuery.of` 정렬, clamp 동작·페이지 응답 값 불변.
- [ ] **AC-5** 5개 모듈 `compileTestJava` 0. 편집 관련 단위 테스트(RouteServiceTest·WishlistServiceTest·UserProfileServiceTest·PaymentTest) 무수정 통과. CI(Linux) 전 레인 GREEN(Testcontainers IT 포함).

# Related Specs

- `platform/refactoring-policy.md`
- `projects/ecommerce-microservices-platform/specs/services/{review,gateway,payment,settlement,user}-service/architecture.md`

# Related Contracts

- N/A — 삭제 대상은 전부 내부 구현(리포지토리 메서드/도메인 헬퍼/설정 bean); API·이벤트 계약 무변경.

# Edge Cases

- review 삭제 메서드명이 `…AndTenantId` 형제의 substring — grep은 `name(` 로 구분(tenant 형제는 name과 `(` 사이 `AndTenantId` 존재).
- gateway `RouteService`에서 `isPublicRoute` 제거 시 `PATH_MATCHER`/`AntPathMatcher`/`HttpMethod` 미사용화 → 동반 제거(컴파일 게이트).
- user `WishlistService`가 clamp 로컬을 pageQuery 이후 재사용 → `pageQuery.page()/size()`로 대체(값 동일). (초기 편집 누락을 로컬 컴파일이 포착·수정.)

# Failure Scenarios

- 삭제가 남긴 소비자 잔존 → `compileTestJava` 실패로 포착(WishlistService에서 실제 발생·수정).
- Testcontainers IT는 Windows 호스트 차단 → 로컬 컴파일+단위테스트로 검증, CI-Linux가 권위.

# Test Requirements

- 5개 모듈 compileTestJava GREEN.
- 편집 영향 단위 테스트 무수정 통과.
- CI-Linux 전 레인(unit + Testcontainers IT) GREEN.

# Definition of Done

- [ ] 5개 서비스 편집, category-separated 커밋(dead-code 4 + lib-alignment 1)
- [ ] 로컬 compile + 단위 테스트 GREEN
- [ ] CI GREEN, 머지, worktree 정리
