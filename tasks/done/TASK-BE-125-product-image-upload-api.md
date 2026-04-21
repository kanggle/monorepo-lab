# Task ID

TASK-BE-125

# Title

product-service 상품 이미지 업로드/삭제 API + ProductImagesUpdated 이벤트 — 다중 이미지, 정렬 순서, 대표 이미지 지정

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- event

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

product-service에 상품 이미지 관리 기능을 추가한다. 어드민이 상품별로 최대 10장의
이미지를 업로드·삭제·재정렬·대표 지정할 수 있어야 하며, 변경 시
`ProductImagesUpdated` 이벤트를 발행하여 search-service 인덱스에 반영한다.

업로드 방식은 presigned PUT URL(specs/platform/object-storage-policy.md)을 따른다:
1. 어드민이 presigned URL을 요청한다.
2. 어드민이 URL로 직접 S3/MinIO에 PUT한다.
3. 어드민이 등록 API를 호출해 메타데이터를 확정한다.

작업 완료 후:
- 기존 `thumbnailUrl` 필드는 `isPrimary=true` 이미지의 resolved URL로 자동 파생된다.
  이미지가 없으면 기존 수동 설정값이 유지된다.
- 상품 상세 응답(`GET /api/products/{productId}`)에 `images[]` 배열이 포함된다.
- search-service가 `ProductImagesUpdated` 이벤트를 소비하여 검색 결과에 최신
  `thumbnailUrl`을 반영한다.

---

# Scope

## In Scope

- `ProductImage` 엔티티 (id, productId, objectKey, sortOrder, isPrimary, uploadedAt)
- `product_images` 테이블 DDL (Flyway migration)
- AWS S3 SDK 기반 `StorageClient` 포트 + 어댑터 (presigned URL 발급, HEAD, DELETE)
- `MediaUrlResolver` — objectKey → 환경별 URL 변환
- `POST /api/admin/products/{productId}/images/upload-url` — presigned URL 발급
- `POST /api/admin/products/{productId}/images` — 이미지 등록 (HEAD 검증 포함)
- `PATCH /api/admin/products/{productId}/images/{imageId}` — sortOrder / isPrimary 변경
- `DELETE /api/admin/products/{productId}/images/{imageId}` — 이미지 삭제 (버킷 객체 포함)
- `GET /api/products/{productId}/images` — 이미지 목록 공개 조회
- `GET /api/products/{productId}` 응답에 `images[]` + 파생 `thumbnailUrl` 포함
- `ProductImagesUpdated` Kafka 이벤트 발행 (토픽: `product-images-updated`)
- search-service `ProductImagesUpdated` 컨슈머 — 인덱스 `thumbnailUrl` 갱신
- 단위 테스트 + 통합 테스트

## Out of Scope

- admin-dashboard UI (TASK-FE-066)
- 이미지 리사이즈/썸네일 생성 파이프라인
- CDN(CloudFront) 구성
- 바이러스 스캔
- `thumbnailUrl` 필드 자체의 제거 (하위 호환 유지)
- `RegisterProductRequest`/`UpdateProductRequest`에서 `thumbnailUrl` 필드 제거 (하위 호환)
- 상품 목록 API 변경 (목록에서는 기존 `thumbnailUrl` 필드로 충분)
- 상품 soft-delete 시 이미지 일괄 삭제 (lifecycle으로 만료 — policy에 정의됨)

---

# Acceptance Criteria

- [ ] `POST /api/admin/products/{productId}/images/upload-url`가 presigned PUT URL을 반환하고, 허용되지 않은 content-type/size는 400을 반환한다
- [ ] `POST /api/admin/products/{productId}/images`가 objectKey의 HEAD 검증 후 메타데이터를 저장한다
- [ ] isPrimary=true 등록 시 기존 primary 이미지가 자동으로 demote된다
- [ ] 상품당 이미지 10장 초과 시 `IMAGE_LIMIT_EXCEEDED` (422) 반환
- [ ] `PATCH .../images/{imageId}`로 sortOrder/isPrimary 변경이 가능하다
- [ ] `DELETE .../images/{imageId}`가 DB 행 삭제 + 버킷 객체 삭제를 수행한다
- [ ] primary 이미지 삭제 시 최소 sortOrder 이미지가 자동 promote된다
- [ ] `GET /api/products/{productId}` 응답에 `images[]`와 파생 `thumbnailUrl`이 포함된다
- [ ] `GET /api/products/{productId}/images`가 sortOrder 오름차순으로 이미지 목록을 반환한다
- [ ] 이미지 등록/수정/삭제 시 `ProductImagesUpdated` 이벤트가 발행된다
- [ ] search-service가 `ProductImagesUpdated` 이벤트를 소비하여 인덱스의 `thumbnailUrl`을 갱신한다
- [ ] objectKey가 존재하지 않으면 `MEDIA_NOT_FOUND` (404) 반환
- [ ] 스토리지 장애 시 `STORAGE_UNAVAILABLE` (503) 반환
- [ ] `StorageClient`가 포트 인터페이스이며, 어댑터는 `infrastructure` 레이어에 위치한다
- [ ] `application.yml`에 `storage.s3.*` 속성이 외부 주입 가능하다

---

# Related Specs

> **Before reading Related Specs**: Follow `specs/platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `specs/rules/common.md` plus any `specs/rules/domains/<domain>.md` and `specs/rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `specs/platform/object-storage-policy.md` (presigned URL 흐름, allow-list, failure modes)
- `specs/platform/error-handling.md` (Object Storage + Product 에러 코드)
- `specs/platform/event-driven-policy.md`
- `specs/platform/architecture.md`
- `specs/platform/coding-rules.md`
- `specs/platform/testing-strategy.md`
- `specs/services/product-service/architecture.md`
- `specs/services/product-service/overview.md`
- `specs/rules/traits/content-heavy.md` (미디어는 객체 스토리지, DB에는 URL/키만)

# Related Skills

- `.claude/skills/backend/spring-boot-service.md`
- `.claude/skills/messaging/kafka-producer.md`

---

# Related Contracts

- `specs/contracts/http/product-api.md` (본 태스크에서 이미 갱신 완료)
- `specs/contracts/events/product-events.md` (본 태스크에서 이미 갱신 완료)

---

# Target Service

- `product-service` (주), `search-service` (컨슈머 추가)

---

# Architecture

Follow:

- `specs/services/product-service/architecture.md`

새 컴포넌트:
- `domain/model/ProductImage.java` — 엔티티
- `domain/port/StorageClient.java` — 포트 인터페이스 (presigned URL, HEAD, DELETE)
- `domain/port/MediaUrlResolver.java` — objectKey → URL 변환 포트
- `infrastructure/storage/S3StorageClient.java` — S3 SDK 어댑터
- `infrastructure/storage/S3MediaUrlResolver.java` — CDN/endpoint + objectKey 결합
- `infrastructure/persistence/ProductImageRepository.java`
- `application/service/ProductImageService.java`
- `presentation/controller/AdminProductImageController.java`
- `presentation/controller/ProductImageController.java` (공개 조회)

---

# Implementation Notes

- AWS S3 SDK v2 사용 (`software.amazon.awssdk:s3`). MinIO는 S3 API 호환이므로
  별도 SDK 불필요.
- `S3StorageClient`는 endpoint-url 기반 `S3Client` Bean을 주입받는다.
  `path-style-access=true`이면 path-style addressing 사용.
- presigned URL 발급은 `S3Presigner`를 사용한다.
- `ProductImage`와 `Product`는 별도 aggregate로 분리한다 (이미지 변경이 Product
  optimistic lock을 건드리면 안 됨). `thumbnailUrl` 파생은 이미지 변경 시
  Product를 업데이트하는 application service 로직으로 처리.
- Flyway migration 파일: `V{next}__create_product_images_table.sql`
- search-service 컨슈머: 기존 `ProductUpdatedConsumer`와 유사한 패턴. 인덱스
  문서의 `thumbnailUrl` 필드만 갱신하면 충분 (images 배열은 검색 인덱스에
  저장하지 않음).

---

# Edge Cases

- presigned URL 발급 후 클라이언트가 실제 업로드하지 않음 → 등록 API 호출 시 HEAD 실패 → MEDIA_NOT_FOUND 반환. orphan 객체는 bucket lifecycle에 의해 자연 만료.
- 동일 상품에 동시에 여러 이미지 등록 → sortOrder 중복 가능 → DB unique 제약 없음 (sortOrder는 display hint), 응답에서 orderBy sortOrder + createdAt으로 안정 정렬.
- isPrimary 이미지가 없는 상태 (전체 삭제 후) → thumbnailUrl=null, images=[]
- 10장 꽉 찬 상태에서 등록 시도 → IMAGE_LIMIT_EXCEEDED 반환
- objectKey에 다른 상품 ID가 포함된 경우 → objectKey prefix 검증으로 거부
- 버킷 객체 삭제 실패 (네트워크) → 메타데이터 삭제는 진행, 객체는 orphan으로 lifecycle 처리. 로그 경고만.
- search-service 이벤트 소비 실패 → DLQ로 라우팅 (기존 패턴)

---

# Failure Scenarios

- S3/MinIO 완전 장애 → presigned URL 발급 실패 → STORAGE_UNAVAILABLE (503)
- HEAD 요청 시 S3 일시 오류 → 재시도 1회 후 실패 → MEDIA_NOT_FOUND (404) 반환, 클라이언트 재시도 유도
- Kafka 발행 실패 → Outbox 패턴 적용 (order-service 선례: TASK-BE-109)
  또는 ApplicationEventPublisher + TransactionalEventListener
- DB 트랜잭션 실패 → 이미지 행 미생성, 이벤트 미발행. 버킷 객체는 orphan → lifecycle
- search-service consumer 예외 → DLQ + 메트릭

---

# Test Requirements

- **단위 테스트**: ProductImageService, S3StorageClient(mocked), MediaUrlResolver
- **통합 테스트**: AdminProductImageController 엔드포인트 (H2 + embedded/mock S3)
- **이벤트 테스트**: ProductImagesUpdated 발행 검증
- **search-service 컨슈머 테스트**: ProductImagesUpdated 소비 → 인덱스 갱신 검증
- **에러 케이스 테스트**: MEDIA_NOT_FOUND, IMAGE_LIMIT_EXCEEDED, STORAGE_UNAVAILABLE

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed (이미 완료)
- [ ] Specs updated first if required (이미 완료)
- [ ] Ready for review
