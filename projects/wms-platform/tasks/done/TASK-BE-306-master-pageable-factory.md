# TASK-BE-306 — master-service PageableFactory utility extract (F-L6-1 dedup)

Status: done

## Goal

`master-service` 의 `/refactor-code` strategic-sampling dry-run 결과 식별된 F-L6-1 cross-class duplication 의 closure. 6/6 RepositoryImpl (`WarehouseRepositoryImpl` / `ZoneRepositoryImpl` / `LocationRepositoryImpl` / `SkuRepositoryImpl` / `PartnerRepositoryImpl` / `LotRepositoryImpl`) 가 공유하는 `DEFAULT_SORT_FIELD` 상수 + `toPageable(PageQuery)` + `resolveSort(String, String)` + `parseDirection(String)` 3 private method 패턴을 신규 utility class 로 추출.

dry-run 결과 base rate (master-service 214 main file scope — 6 aggregate × Hexagonal layers):
- L0 (layer-violation) = 0 (Hexagonal port/adapter 깨끗, 6 aggregate clean separation)
- L1 (auth) = 0 (`@PreAuthorize` 가 controller 가 아닌 application service 에 위치 = inventory architecture.md 와 다른 패턴)
- L2 (dead-code) = 0
- L3 (long-method ≥30 LOC) = 0 (max ~30 LOC `WarehouseService.deactivate`, 이미 명확한 phase)
- L4 (pattern-mismatch) = 0 (Layered도 아니고 정통 Hexagonal, 6 aggregate uniform)
- **L6 (duplication) = 1 (대규모)** — 6 cross-class instance × 3-method 패턴 (본 task scope)
- intentional preservation 인지: `AggregateVersionGuard` 이미 추출됨 (BE-018 추정 또는 audit 이력), `loadOrThrow` 6 services × different exception 으로 utility 화 어려움 (verify exemption).

## Scope

In:

### Finding 1: F-L6-1 — PageableFactory cross-class utility extract

6/6 RepositoryImpl 에 동일하게 존재:

```java
private static final String DEFAULT_SORT_FIELD = "updatedAt";
private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

private Pageable toPageable(PageQuery pageQuery) {
    Sort sort = resolveSort(pageQuery.sortBy(), pageQuery.sortDirection());
    return PageRequest.of(pageQuery.page(), pageQuery.size(), sort);
}

private Sort resolveSort(String sortBy, String sortDirection) {
    String field = (sortBy == null || sortBy.isBlank()) ? DEFAULT_SORT_FIELD : sortBy;
    Sort.Direction direction = parseDirection(sortDirection);
    return Sort.by(direction, field);
}

private Sort.Direction parseDirection(String sortDirection) {
    if (sortDirection == null || sortDirection.isBlank()) {
        return DEFAULT_SORT_DIRECTION;
    }
    return "asc".equalsIgnoreCase(sortDirection)
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
}
```

신규 utility class `com.wms.master.adapter.out.persistence.PageableFactory`:

```java
package com.wms.master.adapter.out.persistence;

import com.example.common.page.PageQuery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class PageableFactory {

    private static final String DEFAULT_SORT_FIELD = "updatedAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

    private PageableFactory() {}

    static Pageable from(PageQuery pageQuery) {
        Sort sort = resolveSort(pageQuery.sortBy(), pageQuery.sortDirection());
        return PageRequest.of(pageQuery.page(), pageQuery.size(), sort);
    }

    private static Sort resolveSort(String sortBy, String sortDirection) {
        String field = (sortBy == null || sortBy.isBlank()) ? DEFAULT_SORT_FIELD : sortBy;
        Sort.Direction direction = parseDirection(sortDirection);
        return Sort.by(direction, field);
    }

    private static Sort.Direction parseDirection(String sortDirection) {
        if (sortDirection == null || sortDirection.isBlank()) {
            return DEFAULT_SORT_DIRECTION;
        }
        return "asc".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
    }
}
```

Package-private 로 한정 (`final class` + package-private; adapter/out/persistence 내부만 사용). master-service 내부 추출 (other service / libs 영향 0).

각 RepositoryImpl 의 변경:
- 6 file 의 `DEFAULT_SORT_FIELD` 상수 삭제
- 6 file 의 `DEFAULT_SORT_DIRECTION` 상수 삭제
- 6 file 의 `toPageable`, `resolveSort`, `parseDirection` 3 private method 삭제
- 6 file 의 `findPage` body 안 `Pageable pageable = toPageable(pageQuery);` → `Pageable pageable = PageableFactory.from(pageQuery);` 1-line 치환
- 6 file 의 `org.springframework.data.domain.PageRequest` + `org.springframework.data.domain.Sort` import 정리 (사용처 없으면 제거)

Out:

- `PageResult<>(stream + 4 page fields)` 6-line ctor pattern in 6 RepositoryImpl 의 `findPage` body 는 **libs/common 영역 (`com.example.common.page.PageResult`) 변경 필요** = zero-retrofit invariant 위반 가능. 별 task (BE-307 libs v2 후보) 로 분리.
- `loadOrThrow` 6 services × different exception 패턴 = generics + exception supplier 도입 시 over-abstraction. 본 task scope 외 (verify exemption).
- `AGGREGATE_TYPE` 상수 6 services × 다른 값 = per-aggregate string. 추출 불가.
- `collectChangedFields` 6 services × 다른 fields = per-aggregate logic. 추출 불가.
- 다른 cluster / service / libs 변경 0.

## Acceptance Criteria

AC-1. 신규 `com.wms.master.adapter.out.persistence.PageableFactory.java`:
- `final class PageableFactory` (package-private 접근, 외부 누출 0)
- `private PageableFactory()` constructor
- `private static final String DEFAULT_SORT_FIELD = "updatedAt";` (byte-identical 보존)
- `private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;` (byte-identical 보존)
- `static Pageable from(PageQuery)` — `toPageable` 의 4-line body 그대로
- `private static Sort resolveSort(String, String)` — `resolveSort` 4-line body 그대로
- `private static Sort.Direction parseDirection(String)` — `parseDirection` 5-line body 그대로
- short javadoc cross-ref (사용처: 6 RepositoryImpl)

AC-2. 6 RepositoryImpl 각각:
- 2 상수 (DEFAULT_SORT_FIELD + DEFAULT_SORT_DIRECTION) 제거
- 3 private method (toPageable + resolveSort + parseDirection) 제거
- `findPage` body 의 `Pageable pageable = toPageable(pageQuery);` → `Pageable pageable = PageableFactory.from(pageQuery);` 1-line 치환
- 불필요한 import (`PageRequest`, `Sort`) 제거 (사용처 없을 시)

AC-3. `findPage` body 의 `PageResult<>(stream + 4 page fields)` 6-line pattern + page.getContent stream + mapper::toDomain 호출 + 4 page field 추출 모두 byte-identical 보존 (별 task BE-307 후보).

AC-4. 6 service 의 application/service layer (WarehouseService / ZoneService / LocationService / SkuService / PartnerService / LotService) 변경 0.

AC-5. `./gradlew :projects:wms-platform:apps:master-service:check --rerun-tasks` 로컬 BUILD SUCCESSFUL (gradlew main 디렉토리 absent 시 CI authoritative substitute).

AC-6. CI 19/20 GREEN authoritative (`Integration (master-service + notification-service, Testcontainers)` + `E2E (gateway-master live-pair smoke, Testcontainers)` + GAP IT + scm/finance/erp/console-bff IT + 4 E2E job 포함; FAILURE = 0).

AC-7. cross-service drift 없음 — `projects/wms-platform/apps/` 의 master-service 외 다른 6 service + `projects/{scm,global-account,erp,fan,ecommerce-microservices,finance,platform-console}-platform/` + `libs/` 변경 0. (**41회째 zero-retrofit invariant** 검증.)

AC-8. Domain method / port interface / API contract / event payload / DB schema 변경 0.

AC-9. `PageRequest.of(page, size, sort)` + `Sort.by(direction, field)` 호출 시 page + size + sort 의 3 argument 가 byte-identical 보존. PageResult 생성 시 totalElements + totalPages 도 그대로.

## Related Specs

- `projects/wms-platform/specs/services/master-service/architecture.md` (Hexagonal port/adapter)
- `projects/wms-platform/specs/contracts/http/master-service-api.md` (변경 없음)
- `projects/wms-platform/specs/contracts/events/master-events.md` (변경 없음)
- `platform/refactoring-policy.md` § Allowed Refactoring Categories: Reduce Duplication (Medium risk; 본 task = Low risk because 6 instance × 동일 byte-identical pattern, paging/sort 의 well-known utility)
- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/refactoring/SKILL.md`

## Related Contracts

- 본 task 는 contract 변경 0. API / event schema unchanged.
- Pagination behavior byte-identical (page/size/sort 결과 unchanged).

## Edge Cases

- **`PageQuery` 정의 위치 (libs/common)**: `com.example.common.page.PageQuery` 는 libs 영역 — 본 task 가 import 만 사용, 정의 변경 0. zero-retrofit invariant 보존.
- **각 RepositoryImpl 의 `sortBy` validation**: 일부 (예: SkuRepositoryImpl 의 listViews-style WHERE builder) 에서 별도 sortBy whitelist 가능성 → 확인 후 해당 file 만 별도 처리. main session verify 의무.
- **`@Repository` annotation + DI**: utility 가 Spring bean 아님 (final class + private ctor + static method). 각 RepositoryImpl 은 그대로 `@Repository` 보존.
- **Package-private 접근**: utility 가 `package-private final class` (no `public`), 외부 service / controller / config 에서 import 불가. adapter/out/persistence 내부만 사용 — single-purpose 의도성 명시.

## Failure Scenarios

- **`DEFAULT_SORT_FIELD` 값 변경 시**: 6 RepositoryImpl 의 default sort 동작 byte-identical 보존 의무. utility 의 상수가 "updatedAt" byte-identical. 새 default 도입 0.
- **`Sort.Direction.DESC` 변경 시**: 동일. utility 의 상수가 byte-identical.
- **`parseDirection` 의 case-insensitive 비교**: `"asc".equalsIgnoreCase(sortDirection)` byte-identical 보존. ASC 외 모든 input (null / blank / "desc" / "DESC" / 임의 string) 이 DESC fallback 유지.
- **새 RepositoryImpl 추가 시**: 미래 task 가 새 aggregate 추가 시 동일 utility 사용 가능 (의도된 확장성).

## Approach Notes

- Refactoring policy § Allowed Refactoring Categories: Reduce Duplication (Medium risk per policy, 본 task = Low risk because: 6 byte-identical instance + paging/sort utility 의 well-known mature pattern + Spring Data API 안정성).
- BE-300 ProjectionConsumerSupport + BE-301 JwtHelper + BE-304 ReactiveJwtAccess utility 패턴 답습. **wms cluster 5번째 helper class extraction** (admin → inventory → gateway → master). cross-class utility 카테고리 4번째 instance (BE-300/301/304 + BE-306).
- BE-305 의 same-class private method 카테고리와 다른 점: 본 task = cross-class utility (6 RepositoryImpl × 1 utility).
- 대규모 file 변경 (6 RepositoryImpl + 1 신규 utility = 7 file) 이지만 mechanical (byte-identical body) — agent dispatch 가 자연 적합.

## 분석/구현 권장 모델

- 분석=Opus 4.7 (verification + 6 byte-identical 검증)
- 구현 권장=Sonnet 4.6 (utility extract + 6 RepositoryImpl 치환, mechanical)
