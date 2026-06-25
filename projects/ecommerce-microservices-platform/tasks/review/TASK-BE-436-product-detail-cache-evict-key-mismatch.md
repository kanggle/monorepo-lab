# TASK-BE-436 — 상품 상세 캐시가 재고 조정·수정·삭제 후 무효화되지 않는 버그 (product-detail @CacheEvict 키 불일치)

- **Status**: review
- **Project**: ecommerce-microservices-platform
- **Service**: product-service
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (캐시 무효화 키 정합 — 체계적 버그 클래스)

## Goal

product-service 의 `product-detail` Redis 캐시가 **재고 조정(stock adjust)·상품 수정·삭제·변형(variant) 변경 후 무효화되지 않아**, 쓰기가 DB 에 반영됐는데도 상품 상세 조회(`GET /api/products/{id}`)가 최대 TTL(60초) 동안 stale 데이터를 서빙하는 버그를 고친다. 원인은 `@Cacheable` 읽기 키와 `@CacheEvict` 무효화 키의 **세그먼트 불일치**다.

근본 원인 (런타임 진단 2026-06-26):

- 사용자 신고 증상: 콘솔 이커머스 상품 상세에서 재고를 증가시켜도 화면에 반영되지 않음(에러 없음, 새로고침해도 그대로). DB 확인 결과 `product_variants.stock` 은 정상 증가(쓰기 성공) — 읽기만 stale.
- `QueryProductService.findById` 의 `@Cacheable("product-detail")` 키는 **3 세그먼트**:
  `tenant : sellerScope(또는 'all') : productId`
  (seller-scope 격리/귀속 AC-6 도입 시 `SellerScopeContext` 세그먼트가 추가됨)
- 그러나 targeted `@CacheEvict("product-detail")` 키들은 **2 세그먼트**로 남아 있음:
  `tenant : productId` (sellerScope 세그먼트 누락)
- 두 키가 절대 일치하지 않아 **무효화가 100% 미스** → 캐시 엔트리가 TTL(60초)까지 잔존 → stale read. `product-list` 는 `allEntries = true` 라 정상 무효화되므로 목록은 갱신되지만 **상세만** 깨진다.

체계적 버그 클래스 (같은 seller-scope 키 변경에서 함께 누락된 모든 targeted product-detail evict):

| 서비스 | 위치 | 현재 evict 키 |
|---|---|---|
| `AdjustStockService` | line 38 | `tenant:productId` (재고 조정 — 신고 증상) |
| `UpdateProductService` | line 30 | `tenant:productId` (상품 수정) |
| `DeleteProductService` | line 27 | `tenant:productId` (삭제 — 삭제된 상품 상세가 60초간 노출) |
| `VariantManagementService` | line 29,46,62 | `tenant:productId` (변형 추가/수정/삭제) |

이미 올바른(영향 없음) — `allEntries = true` 사용:
- `RegisterProductService` line 39, `ProductImageService` line 61/104/140.

## Scope

**In scope** (product-service only):

1. 깨진 4개 서비스의 `@CacheEvict(value = "product-detail", key = "…tenant:productId…")` 를 **`@CacheEvict(value = "product-detail", allEntries = true)`** 로 변경 — 이미 정상인 `RegisterProductService`/`ProductImageService` 와 동일 패턴으로 통일:
   - `AdjustStockService.java` (line 38)
   - `UpdateProductService.java` (line 30)
   - `DeleteProductService.java` (line 27)
   - `VariantManagementService.java` (line 29, 46, 62 — 3곳)
2. 회귀 IT 추가: `ProductDetailCacheEvictionIntegrationTest` (`@Tag("integration")`, Testcontainers postgres + `spring.cache.type=simple` 로 ConcurrentMapCacheManager 활성화). 재고 조정/상품 수정/변형 추가 각각에 대해 "findById(캐시 채움) → 쓰기 → findById 가 새 값 반영" 을 단언(수정 전 코드에선 stale 값으로 실패하도록).

**Out of scope**:

- **targeted 키 정합 대안**(evict 키에 sellerScope 세그먼트 추가)은 채택하지 않음 — 쓰기(operator/seller-scoped) 경로의 `SellerScopeContext` 가 읽기(public storefront, 보통 'all') 경로와 다를 수 있어 targeted evict 가 여전히 미스할 수 있다. `allEntries = true` 가 컨텍스트 무관하게 정확하며, 서비스 내 절반(Register/ProductImage)이 이미 쓰는 검증된 패턴이다. (효율 비용: 상세 캐시는 쓰기 시 전량 flush — 재고 조정/수정은 read 대비 저빈도라 무시 가능.)
- 캐시 TTL(60초) 자체 변경 없음 — 무효화가 즉시 동작하면 TTL 은 보조 안전망으로 충분.
- `@Cacheable` 읽기 키 변경 없음(정상).
- console-web/프록시 변경 없음(TASK-PC-FE-132 로 detail GET 프록시 405 는 이미 해결; 본 task 는 그 다음 단계인 백엔드 캐시 staleness).

## Acceptance Criteria

- **AC-1 — 재고 조정 즉시 반영.** 상품 상세를 한 번 조회해 캐시를 채운 뒤 재고를 +N 조정하면, 직후 상세 조회가 **새 재고**를 반환한다(stale 아님).
- **AC-2 — 수정/변형도 즉시 반영.** 상품 수정(이름/가격 등)·변형 추가/수정/삭제 후 상세 조회가 변경을 즉시 반영한다.
- **AC-3 — 삭제 즉시 반영.** 삭제 후 상세 캐시가 무효화돼 삭제된 상품 상세가 잔존 서빙되지 않는다.
- **AC-4 — 회귀 테스트.** `ProductDetailCacheEvictionIntegrationTest` 가 수정 후 GREEN, 수정 전(키 불일치) 코드에선 stale read 로 FAIL(버그를 핀).
- **AC-5 — 게이트.** `./gradlew :projects:ecommerce-microservices-platform:apps:product-service:check` GREEN(Docker-free; `integration` 태그는 CI 제외 — 신규 IT 는 nightly/`-PrunIntegration` 에서 실행, compile 은 `:check` 가 검증).

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/product-service/architecture.md` — read-heavy 서비스의 캐시 전략(`@Cacheable` product-list/product-detail, Redis, TTL). 쓰기 시 무효화 일관성.
- seller-scope 격리/귀속(AC-6, `SellerScopeContext`) — `@Cacheable` 키에 scope 세그먼트를 추가한 변경(읽기 키 3세그먼트화)의 출처.

## Related Contracts

- ecommerce `product-service` `GET /api/products/{id}`(public 상세) — `ProductDetailResponse`(variants[].stock 포함). 쓰기 후 즉시 일관성 기대.
- `PATCH /api/admin/products/{id}/stock`(admin 재고 조정) — DB 커밋 + `product.product.stock-changed` 발행 + 캐시 무효화(본 버그 지점).

## Edge Cases

- **seller-scoped operator 가 자기 상품 재고 조정 + public storefront 가 'all' 스코프로 상세 조회** — targeted 키였다면 evict 미스(쓰기 scope ≠ 읽기 scope). `allEntries = true` 는 이 경우도 정확히 무효화.
- **멀티테넌트** — `allEntries = true` 는 product-detail 캐시 전체를 비우지만 테넌트 간 데이터 유출 위험은 없음(다음 읽기가 tenant-prefixed 키로 재적재). 잠깐의 캐시 미스만 발생.
- **TTL 60초** — 무효화가 깨져 있어도 60초 후엔 self-heal 됐던 것이 신고 증상이 간헐적으로 보인 이유(빠른 새로고침=stale, 60초 후=정상). 수정 후엔 즉시 정상.

## Failure Scenarios

- evict 키를 sellerScope 세그먼트만 추가해 "정합"시키면, 쓰기/읽기 scope 컨텍스트가 다른 경로에서 여전히 stale — 그래서 `allEntries = true` 로 확정(컨텍스트 무관 정확성).
- 회귀 IT 가 `spring.cache.type` 을 명시적으로 활성화(`simple`)하지 않으면 기존 IT 처럼 캐시 no-op 으로 도망쳐 버그를 못 핀다 → `@DynamicPropertySource` 로 `spring.cache.type=simple` 강제.
- 4개 서비스 중 하나라도 빠뜨리면 해당 쓰기 경로의 상세 staleness 가 잔존 → 본 task 는 깨진 4개 전부(6개 어노테이션)를 한 번에 수정.
