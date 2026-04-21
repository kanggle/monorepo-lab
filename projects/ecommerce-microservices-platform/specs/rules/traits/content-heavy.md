# Trait: content-heavy

> **Activated when**: `PROJECT.md` includes `content-heavy` in `traits:`.

---

## Scope

콘텐츠(상품 정보, 기사, 미디어, 리뷰, 프로모션 배너 등)가 제품 가치의 핵심 자산이고, 그 생산·큐레이션·배포·검색이 시스템 설계를 좌우하는 서비스에 적용된다.

ecommerce-microservices-platform 기준 적용 범위:

- 필수: [apps/product-service/](../../../apps/product-service/) (상품 카탈로그), [apps/review-service/](../../../apps/review-service/) (리뷰 콘텐츠), [apps/search-service/](../../../apps/search-service/) (검색 인덱스), [apps/promotion-service/](../../../apps/promotion-service/) (배너·캠페인 콘텐츠)
- 조건부: 프론트엔드 렌더러([apps/web-store/](../../../apps/web-store/), [apps/admin-dashboard/](../../../apps/admin-dashboard/))
- 제외: 순수 상태 전이 서비스([apps/order-service/](../../../apps/order-service/), [apps/payment-service/](../../../apps/payment-service/))

---

## Mandatory Rules

### C1. 콘텐츠는 구조화 스키마로 저장
모든 콘텐츠는 사전 정의된 스키마(JSON schema, DB schema, protobuf 등)를 따라야 하며, 자유 형식 텍스트에 메타데이터를 섞어 저장하는 것은 금지. 콘텐츠 모델 변경은 [../../platform/versioning-policy.md](../../platform/versioning-policy.md)의 스키마 버전 관리 규칙을 따른다.

### C2. 발행(Publish) 상태 기계
콘텐츠는 최소 다음 상태를 가진다: `DRAFT` → `REVIEW` → `PUBLISHED` → `ARCHIVED`. 스킵 가능한 전이가 있을 수 있으나(예: 자동 발행), 모든 발행은 명시적 트리거(API 호출 또는 이벤트)를 통해야 하며 암묵적 자동 발행은 금지.

### C3. 콘텐츠 버전 이력 보존
PUBLISHED된 콘텐츠의 변경은 새 버전을 생성하는 방식으로 처리하며, 이전 버전은 최소 30일간 조회 가능해야 한다. 복구 가능성이 목적.

### C4. 미디어는 별도 스토리지에, URL로 참조
이미지·비디오·첨부 파일은 관계형 DB가 아니라 **객체 스토리지(S3/GCS/OSS)** 에 저장하고, 콘텐츠 레코드는 URL 또는 객체 키만 보유한다. 미디어 URL은 CDN 뒤에 위치해야 한다.

### C5. 검색 인덱싱은 이벤트 기반 비동기
콘텐츠 변경 시 검색 인덱스 업데이트는 **이벤트 기반**으로 처리한다. 동기적 인덱스 업데이트는 레이턴시·결합 때문에 금지.

### C6. 전문(Full-text) 검색은 전용 엔진에 위임
상품명·설명·리뷰 본문에 대한 검색은 DB LIKE 쿼리가 아닌 전용 엔진(Elasticsearch/OpenSearch/Algolia 등)을 사용한다. DB 기반 LIKE '%...%' 검색은 초기 PoC 외 금지.

### C7. 콘텐츠 캐시 레이어 필수
읽기 트래픽이 많은 콘텐츠 엔드포인트는 다계층 캐시(CDN, 애플리케이션 캐시, DB 캐시)를 가진다. 캐시 무효화는 변경 이벤트로 트리거. (이 규칙은 `read-heavy` trait과 중첩됨)

### C8. 콘텐츠 국제화(i18n)를 모델 레벨에서 지원
다국어 콘텐츠는 (entityId, locale) 복합 키로 별도 row/document에 저장한다. 단일 row에 `title_ko`, `title_en` 같은 칼럼 추가는 금지.

### C9. 민감 콘텐츠 모더레이션 훅
사용자 생성 콘텐츠(리뷰 등)는 발행 전 **모더레이션 훅**을 통과해야 한다. 자동 필터(금칙어, AI 분류기) 또는 수동 검수 큐. 어느 쪽이든 파이프라인에 명시적으로 존재.

---

## Forbidden Patterns

- ❌ **DB에 BLOB으로 이미지/동영상 저장**
- ❌ **`LIKE '%keyword%'` 기반 검색** (대용량 콘텐츠 테이블 대상)
- ❌ **동기 검색 인덱스 업데이트** (API 응답 경로에 인덱싱 호출)
- ❌ **다국어를 칼럼 suffix로 표현** (`title_ko`, `title_en`)
- ❌ **콘텐츠 직접 삭제** (soft delete 또는 archive 상태 사용)
- ❌ **모더레이션 없는 사용자 콘텐츠 즉시 발행**

---

## Required Artifacts

1. **콘텐츠 모델 스키마 문서** — 각 콘텐츠 타입별 필드·타입·제약. 위치: `specs/services/<service>/content-model.md`
2. **발행 상태 기계 다이어그램** — 콘텐츠 타입별. 위치: `specs/services/<service>/state-machines/`
3. **검색 인덱스 매핑 정의** — 엔진별 매핑. 위치: `specs/services/search-service/index-mappings/` 또는 동등
4. **캐시 전략 문서** — 어떤 엔드포인트가 어느 계층에서 캐시되는지, TTL, 무효화 트리거
5. **모더레이션 파이프라인 문서** — 사용자 콘텐츠가 거치는 검증 단계

---

## Interaction with Common Rules

- [../../platform/error-handling.md](../../platform/error-handling.md)의 `CONTENT_NOT_PUBLISHED`, `CONTENT_VERSION_CONFLICT`, `MEDIA_NOT_FOUND` 등 콘텐츠 관련 에러 코드를 사용한다 (없으면 common 파일에 추가).
- [../../platform/versioning-policy.md](../../platform/versioning-policy.md)의 API 버전과 별개로, **콘텐츠 스키마 버전**을 추적한다.
- [../../platform/observability.md](../../platform/observability.md)에 다음 메트릭을 추가: 캐시 hit rate, 검색 인덱스 lag, 미디어 스토리지 사용량, 모더레이션 큐 깊이.

---

## Checklist (Review Gate)

- [ ] 콘텐츠가 구조화 스키마로 저장되고 버전 관리되는가? (C1, C3)
- [ ] 발행 상태가 상태 기계로 명시되어 있고 자동 발행이 없는가? (C2)
- [ ] 미디어가 객체 스토리지 + CDN에 분리 저장되고 DB에는 URL만 있는가? (C4)
- [ ] 검색 인덱스 업데이트가 이벤트 기반 비동기인가? (C5)
- [ ] 전문 검색이 DB LIKE가 아닌 전용 엔진을 사용하는가? (C6)
- [ ] 다계층 캐시와 무효화 전략이 명시되어 있는가? (C7)
- [ ] 다국어가 (id, locale) 모델로 표현되는가? (C8)
- [ ] 사용자 생성 콘텐츠가 모더레이션 훅을 통과하는가? (C9)
- [ ] 금지 패턴이 코드베이스에 존재하지 않는가?
