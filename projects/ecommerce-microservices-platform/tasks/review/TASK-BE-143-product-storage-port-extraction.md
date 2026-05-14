# Task ID

TASK-BE-143

# Title

product-service `ProductImageService` 의 `infrastructure.storage.StorageProperties` 직접 import 제거 — `domain/port/ProductImageBucketResolver` 추출 + 더불어 `ProductImageRepository.saveAll` dead code 제거

# Status

review

# Owner

backend

# Task Tags

- code
- test
- refactor

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

`application/service/ProductImageService` 가 `com.example.product.infrastructure.storage.StorageProperties` 를 직접 import 하는 layer drift 를 해소한다.

[`specs/services/product-service/architecture.md`](../../specs/services/product-service/architecture.md) § Boundary Rules ("application 은 domain port 를 통해서만 infrastructure 와 통신") 위반 1건을 closure.

원인: `ProductImageService` 가 presigned upload URL 생성 + 객체 키 검증 + thumbnail 동기화 3개 사용 사례 안에서 `storageProperties.getBuckets().getProductImages()` 를 3번 호출 → application → infrastructure 경계 import 1건 (line 17). 본질은 "어떤 S3 bucket 을 쓰는가" 라는 도메인 정책 결정인데, 현재는 infrastructure 의 properties bean 을 application 이 직접 참조한다.

해결: `domain/port/ProductImageBucketResolver` 인터페이스 추출 (메서드 1개: `String resolveBucket()`). `infrastructure/storage/` 안에 thin adapter (`S3ProductImageBucketResolver`) 가 `StorageProperties.getBuckets().getProductImages()` 를 반환. `ProductImageService` 는 `ProductImageBucketResolver` 만 의존 → infrastructure import 0.

본 task 는 ecommerce/product-service sweep dry-run (2026-05-15, `/refactor-code --dry-run` 결과) 의 finding A1 single-PR closure 이며, B3 dead-code 제거 (trivial bundling) 를 같은 PR 에 포함한다. B/C/D 카테고리 polish 는 본 task scope 외 (별도 평가에서 DEFER 결정 — 메모리 `project_refactor_sweep_status.md` § ecommerce/product-service dry-run 결과 참조; sweep value verdict = LEAN DEFER + 1 cherry-pick + 1 trivial bundling).

---

# Scope

## In Scope

**CHERRY-1 (A1 layer leak 해소)**:

- 새 domain port: `apps/product-service/src/main/java/com/example/product/domain/port/ProductImageBucketResolver.java` (interface, 메서드 1개 `String resolveBucket()`)
- 새 adapter: `apps/product-service/src/main/java/com/example/product/infrastructure/storage/S3ProductImageBucketResolver.java` (`@Component`, `StorageProperties` 주입, `resolveBucket()` 이 `storageProperties.getBuckets().getProductImages()` 반환)
- `ProductImageService` 수정:
  - `import com.example.product.infrastructure.storage.StorageProperties;` 라인 제거 (현재 line 17)
  - 생성자 의존성 `StorageProperties storageProperties` → `ProductImageBucketResolver bucketResolver` 로 교체
  - 기존 3개 호출 (`storageProperties.getBuckets().getProductImages()`) → `bucketResolver.resolveBucket()` 으로 치환
- 기존 `ProductImageServiceTest` 의 mock setup signature 업데이트 (`StorageProperties` mock → `ProductImageBucketResolver` mock, 1줄 `when(...).thenReturn("product-images-test")` stub)
- 새 `ProductImageBucketResolver` adapter 단위 테스트 1건 추가 (`S3ProductImageBucketResolverTest`, 단순 delegation 검증)

**CHERRY-2 (B3 dead-code 제거, trivial bundling)**:

- `domain/repository/ProductImageRepository.java` 에서 `saveAll(List<ProductImage> images)` 메서드 선언 제거
- `infrastructure/persistence/ProductImageRepositoryAdapter.java` 에서 `saveAll(...)` 구현 메서드 제거 (현재 line 58-62)
- grep 검증: production code 안 호출 0건 (이미 dry-run 단계 확인됨)
- 테스트 영향 없음 (호출 testcase 0건 확인 필요)

## Out of Scope

- B1 `ProductImageService.updateImage/deleteImage` long-method polish (~15 LOC, internal helper 추출). 사용자 명시 시 별 task 후보.
- B2 `validateAdminRole` + `ROLE_ADMIN` 의 2-place 중복 (admin controller 2개). 3-place threshold 미달, 다음 admin controller 추가 시 자연 처리.
- B4 `Product.reconstitute()` 9-arg overload 사용 검증/제거 — JPA mapper 사용 여부 확인 비용 vs ROI 미달.
- C1 `ProductController.java:47-48` fully-qualified `java.util.List<ProductImage>` import 정리. 다음 feature PR 자연 처리.
- C2 `presentation/` → `interface/` package rename (architecture.md spec 와의 naming drift). 모든 import touch — high risk cosmetic only, 별 평가.
- C3 `InventoryRepositoryAdapter` + `CategoryRepositoryAdapter` 의 dead `@Transactional`. 다음 feature PR 자연 처리.
- C4 `Inventory.increase()/decrease()` inline 또는 private 화. trivial polish.
- D1/D2 ADR-MONO-005 + libs/java-messaging 정합성 (이미 align, retrofit 불필요).
- 다른 service 의 동일 pattern (StorageProperties 직접 의존) 검사. 별 평가.
- HTTP contract / S3 bucket 이름 / event payload 변경 0 — 기존 동작 byte-identical 유지.

---

# Acceptance Criteria

- [ ] `apps/product-service/src/main/java/com/example/product/application/service/ProductImageService.java` 에 `com.example.product.infrastructure.**` import 라인 0개 (grep 검증).
- [ ] `ProductImageBucketResolver` interface 가 `domain/port/` 패키지에 있고 메서드 1개 (`String resolveBucket()`).
- [ ] `S3ProductImageBucketResolver` adapter 가 `infrastructure/storage/` 패키지에 있고 `@Component` annotated + `StorageProperties` 주입.
- [ ] `ProductImageService` 가 `ProductImageBucketResolver` 만 의존 (생성자 시그너처 변경, 기존 3개 호출 모두 port 경유).
- [ ] `ProductImageRepository.saveAll(...)` interface 메서드 선언 제거, `ProductImageRepositoryAdapter.saveAll(...)` 구현 제거. production code 호출 0 (grep `\.saveAll\(` in `apps/product-service/src/main/java/**`).
- [ ] 기존 `ProductImageServiceTest` 가 새 signature 로 PASS (Bucket 이름 stub 1줄 추가).
- [ ] 새 `S3ProductImageBucketResolverTest` 1 case PASS (delegation 확인).
- [ ] `./gradlew :product-service:test` PASS, `./gradlew :product-service:check` 회귀 없음.
- [ ] HTTP API + Kafka event payload + S3 bucket 이름 변경 0 (byte-identical 외부 동작).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- [`specs/services/product-service/architecture.md`](../../specs/services/product-service/architecture.md) — Boundary Rules + Allowed Dependencies
- [`platform/service-types/rest-api.md`](../../../../platform/service-types/rest-api.md)
- `rules/common.md`
- `rules/domains/ecommerce.md` (있으면)
- `rules/traits/transactional.md` / `rules/traits/content-heavy.md` (있으면)

# Related Skills

- `.claude/skills/backend/implement-task` (구현)
- `.claude/skills/backend/refactoring` (참고 — 본 task 는 sweep 가 아닌 targeted spec-drift fix + 1 trivial dead-code bundling)

---

# Related Contracts

- HTTP API 변경 없음 — presigned URL / image registration / list endpoint 모두 응답 shape + S3 bucket 동일.
- Kafka event payload 변경 없음.
- `specs/contracts/http/` 변경 없음.

---

# Target Service

- `product-service` (`projects/ecommerce-microservices-platform/apps/product-service`)

---

# Architecture

Follow:

- [`specs/services/product-service/architecture.md`](../../specs/services/product-service/architecture.md) — DDD 4-layer with domain/port (Hexagonal류), 본 task 는 application→infrastructure direct import → domain port 경유 패턴으로 정상화.

---

# Implementation Notes

- 새 port 의 위치는 `domain/port/` (기존 `MediaUrlResolver.java`, `StorageClient.java` 가 이미 같은 패키지에 있음 — sibling pattern 답습).
- adapter 의 위치는 `infrastructure/storage/` (기존 `S3MediaUrlResolver.java`, `S3StorageClient.java` 와 sibling — `S3` 접두사 통일).
- `ProductImageBucketResolver` 메서드 명은 `resolveBucket()` (한 글자 더 짧은 `bucket()` 도 후보지만 `MediaUrlResolver.resolveUrl(...)` 의 명명 패턴 답습).
- Spring 의 `@Component` 자동 등록만 사용 — 별도 `@Configuration` 정의 불필요.
- `S3ProductImageBucketResolver` 가 `StorageProperties` 의 nested `Buckets.getProductImages()` 단일 호출 — null safety 는 기존 `StorageProperties` validation 에 위임.
- B3 dead `saveAll` 제거 시 import 정리도 함께 (예: `java.util.List` import 가 다른 메서드에서도 쓰이면 유지).

---

# Edge Cases

- `StorageProperties.getBuckets().getProductImages()` 가 null 반환 케이스 — 기존 동작 그대로 (validate 는 `StorageProperties` 의 책임). `S3ProductImageBucketResolver` 는 pass-through.
- presigned URL 생성 + image registration + thumbnail 동기화 3개 사용 사례 모두 same bucket 사용 — port 메서드 1개로 충분.
- 향후 multi-bucket 정책 도입 시 (예: variant 별 bucket) port 메서드 추가는 본 task 범위 외.

---

# Failure Scenarios

- adapter 가 null bucket 반환 → 기존 `S3StorageClient` 의 NPE 동일 (변경 없음).
- 새 port 가 미구현된 채로 deploy → Spring DI 실패 (즉시 boot fail, 기존과 동일 fail-fast).

---

# Test Requirements

- unit: `ProductImageServiceTest` 가 새 signature (`ProductImageBucketResolver` mock) 로 PASS, assertion 데이터 동일.
- adapter unit: `S3ProductImageBucketResolverTest` 1 case 신규 추가 (mock `StorageProperties` 에서 bucket 이름 stub, `resolveBucket()` 반환 확인).
- IT: 신규 추가 없음. Testcontainers npipe blocker (메모리 `project_testcontainers_docker_desktop_blocker`) 영향 없음.

---

# Definition of Done

- [ ] Implementation completed (3 file 신규 + 2 file 수정 + 1 test 신규)
- [ ] Tests added (1 adapter unit test)
- [ ] Tests passing (`./gradlew :product-service:check`)
- [ ] application service 에서 `com.example.product.infrastructure.**` import 0 (grep)
- [ ] `ProductImageRepository.saveAll` declaration 0 (grep)
- [ ] HTTP / event / bucket name 무 변경 (byte-identical 외부 동작)
- [ ] Specs 변경 없음 (architecture.md 의 § Boundary Rules 위반 해소만)
- [ ] Ready for review
