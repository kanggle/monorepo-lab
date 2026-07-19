# Task ID

TASK-BE-530

# Title

ecommerce backend Tier B refactoring sweep — genuine duplication extractions (behavior-preserving)

# Status

in-progress

# Owner

backend

# Task Tags

- code
- refactor

# Goal

12-서비스 백엔드 리팩토링 전수 스캔의 **Phase 2 — Tier B**. 발굴 에이전트가 회의적 검증(false-dup 배제)을 거쳐 남긴 **진짜 수렴 중복만** 서비스별 헬퍼로 추출. 동작 무변경(`platform/refactoring-policy.md`). Tier A(BE-529, dead-code)는 완료, Tier C(민감 항목)는 Phase 3 별도 task.

ecommerce IT 레인이 단일 체크(서비스 무관 전체 실행)라 위험대역별로 묶어 PR화한다(서비스별 category-separated 커밋):

- **PR-A** (low-risk mechanical): batch-worker · settlement · review · promotion
- **PR-B** (consumer/tail): shipping · notification
- **PR-C** (tenant): order · product — `TenantContext.runWithTenant` (tenant 격리 IT 게이트)

# Scope

## In Scope (category=duplication, 서비스별 커밋)

- **batch-worker**: 4개 client(`ProductServiceClient`/`SearchServiceClient`/`OrderServiceClient`/`IamClientCredentialsTokenProvider`)의 동일 `SimpleClientHttpRequestFactory`+timeout+`RestClient.builder()` 블록 → 패키지 내부 `RestClients.timed(connect, read)` 헬퍼(Builder 반환, baseUrl 유무 흡수).
- **settlement**: `SettlementController`/`SettlementPeriodController`의 byte-identical `ROLE_ADMIN`+`validateAdminRole`+`hasAdminRole` → `OperatorRoleGuard.requireOperator`.
- **review**: `ReviewController`/`ReviewRepositoryImpl` 두 층의 `ALLOWED_SORT_FIELDS`+validate 중복 → application-layer `ReviewSortFields.requireValid` 단일 출처.
- **promotion**: `CouponCommandService`/`PromotionCommandService`/`PromotionQueryService` 3개 동일 admin-role 체크 → `OperatorRoleGuard.requireOperator`; `GlobalExceptionHandler.handleMissingRequestHeader`의 inert if/else(양분기 byte-동일) collapse.
- **shipping** (PR-B): `ShippingCommandService`의 transition→save→publish tail 2메서드 → private 헬퍼; admin-role 체크 3클래스 → 공유 validator.
- **notification** (PR-B): 3 consumer(`OrderPlaced`/`PaymentCompleted`/`ShippingStatusChanged`)의 null-check→build-command 형태 → 공유 헬퍼/base(로그 텍스트 byte 보존; `AccountCreated`는 형태 달라 제외).
- **order** (PR-C): `TenantContext.set/finally clear` wrapper 3 consumer → `TenantContext.runWithTenant(tenantId, Runnable)`.
- **product** (PR-C): `TenantContext.runWithTenant` 4 consumer; `ReservationEventDedupe`↔`WmsReconciliationDedupe` `isDuplicate` 공유 알고리즘(테이블 분리 유지, MANDATORY 전파·DIV 분기 byte 보존); UUID-parse 헬퍼 통합; `ProductImageService` tail.

## Out of Scope

- admin-role 체크의 **크로스-서비스** 통합(libs 정책 HARDSTOP-03, 선례 R-12/R-20) — 각 서비스 내부 추출로만.
- Tier C(Phase 3): payment `confirm()` extract, order cancel-tail, settlement domain-vs-JPA-entity split, product WmsReconciliation clamp, gateway swagger param collapse — 개별 안전망(durability IT/행위 diff/test-first) 선행.
- borderline field-copy dedup(promotion `Promotion.hydrate`/`PromotionJpaEntity.copyFieldsFrom`) — churn 대비 이득 낮아 미실행.
- GlobalExceptionHandler per-type dedup(FIN-BE-058 WONTFIX 선례).

# Acceptance Criteria

- [ ] **AC-1** 각 추출은 동작 무변경 — 예외 타입/메시지/HTTP 상태/이벤트 발행 순서·timing 불변.
- [ ] **AC-2** 추출 후 콜사이트 전량 갱신, 원본 중복 메서드/상수 잔여 0.
- [ ] **AC-3** 영향 모듈 `compileTestJava` 0, 편집 인접 단위 테스트 무수정 통과.
- [ ] **AC-4** tenant 관련(order/product runWithTenant)은 set→run→clear-in-finally 순서·예외 시 clear 보존 — `MultiTenantIsolationIntegrationTest`가 CI 게이트.
- [ ] **AC-5** CI(Linux) 전 레인 GREEN(Testcontainers IT 포함).

# Related Specs

- `platform/refactoring-policy.md`
- 각 서비스 `specs/services/<service>/architecture.md`

# Related Contracts

- N/A — 전부 내부 구현(헬퍼 추출/dead-branch); API·이벤트 계약 무변경.

# Edge Cases

- review `ReviewSortFields.requireValid`: null/blank 경로는 리포지토리에서 호출 전 default 분기로 가드(controller는 @RequestParam default라 null 불가) — 기존 동작 동일.
- tenant runWithTenant: 예외 발생 시에도 `finally`로 clear, 풀 스레드 컨텍스트 누수 없음(기존과 동일).
- product dedupe 헬퍼: `Propagation.MANDATORY` + DIV→duplicate 분기 verbatim 보존(같은 트랜잭션 내 실행).

# Failure Scenarios

- 삭제/추출이 남긴 소비자 잔존 → `compileTestJava` 실패로 포착.
- authz 헬퍼 시맨틱 변화 → 컨트롤러 403 테스트로 포착.
- tenant 누수 → CI `MultiTenantIsolationIntegrationTest`.

# Test Requirements

- 영향 모듈 compileTestJava GREEN + 편집 인접 단위 테스트 통과.
- CI-Linux 전 레인 GREEN.

# Definition of Done

- [ ] PR-A/B/C 순차 머지, 서비스별 category-separated 커밋
- [ ] 각 PR CI GREEN, 3-dim 검증
- [ ] worktree 정리, 일괄 close chore
