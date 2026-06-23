# TASK-BE-427 — notification-service 템플릿 목록이 정렬 없이 조회돼 신규 등록 템플릿이 1페이지에서 누락되는 버그

- **Status**: ready
- **Project**: ecommerce-microservices-platform
- **Service**: notification-service
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (영속성 쿼리 버그픽스)

## Goal

콘솔 E-Commerce > 알림 템플릿(`GET /api/notifications/templates`) 목록이, **새로 등록한 템플릿을 목록에서 보여주지 못하는** 버그를 고친다. 등록 직후 1페이지 최상단에 신규 템플릿이 나타나야 한다.

근본 원인: 목록 쿼리 `NotificationTemplateJpaRepository.findByTenantId(tenantId, pageable)` 에 **ORDER BY 절이 없다**. `templateId` 는 `UUID.randomUUID()` 로 생성되는 랜덤 PK 이므로, 정렬이 없으면 DB(Postgres)는 반환 순서를 보장하지 않고 신규 행이 결과 집합의 **무작위 위치**에 들어간다. 템플릿이 페이지 크기(기본 20)를 넘으면 새 템플릿이 2페이지 이후로 밀려 1페이지에서 사라진다. 또한 정렬이 없어 페이지네이션 자체가 호출마다 행이 달라질 수 있는 불안정 상태였다. 같은 서비스의 알림 목록은 이미 `findByTenantIdAndUserIdOrderByCreatedAtDesc` 로 최신순 정렬을 적용하고 있어, 템플릿 목록만 이 패턴이 누락된 케이스.

## Scope

**In scope** (notification-service only):

1. `src/main/java/.../adapter/out/persistence/repository/NotificationTemplateJpaRepository.java` — 목록 파생 쿼리를 `findByTenantIdOrderByCreatedAtDescTemplateIdAsc` 로 변경(최신순 + 동률 시 `templateId` 보조 정렬로 결정적 페이지네이션).
2. `src/main/java/.../adapter/out/persistence/repository/TemplateRepositoryImpl.java#findAll` — 변경된 메서드 호출.
3. `src/test/java/.../TemplateRepositoryImplTest.java` — mock stub 메서드명 동기화.

**Out of scope**: 프런트(콘솔 `NotificationsScreen` 은 force-dynamic 서버 컴포넌트라 등록 후 복귀 시 page 0 재조회 → 변경 불필요), 다른 목록 쿼리, 정렬 기준의 API 파라미터화.

## Acceptance Criteria

- **AC-1 — 최신순.** `GET /api/notifications/templates` 결과가 `createdAt` 내림차순으로 정렬된다. 직전 등록 템플릿이 page 0 최상단에 위치한다.
- **AC-2 — 결정적 페이지네이션.** `createdAt` 이 같은 행이 둘 이상이어도 `templateId` ASC 보조키로 호출 간 순서가 안정적이다(행 누락/중복 없음).
- **AC-3 — 테넌트 격리 보존.** 정렬 추가가 기존 `tenantId` 스코프 필터를 변경하지 않는다(타 테넌트 행 미노출).
- **AC-4 — 게이트.** notification-service `:test` GREEN(기존 + 동기화된 stub). 파생 쿼리명(`...OrderByCreatedAtDescTemplateIdAsc`) 의 Spring Data 컨텍스트 검증은 Testcontainers IT 영역 → CI Linux 권위(로컬 Windows 는 Testcontainers 차단).

## Related Specs

- TASK-BE-372 (M3) — admin 템플릿 목록 surface(테넌트 스코프 조회).
- TASK-PC-FE-089 (ADR-031 Phase 5b) — 콘솔 알림 템플릿 운영 화면(목록 소비 측).

## Related Contracts

- `GET /api/notifications/templates` — 페이지네이션 응답(`content`, `page`, `size`, `totalElements`, `totalPages`). 본 변경은 `content` 의 정렬을 최신순으로 고정(추가 계약 필드 없음).

## Edge Cases

- 템플릿 0건 → 빈 페이지(기존 동작 보존).
- 동일 `createdAt` 다건(테스트/일괄 시드) → `templateId` ASC 로 결정적.
- 테넌트당 (type, channel) 유니크 제약상 템플릿 수가 적은 테넌트는 1페이지에 다 들어와 정렬 차이가 비가시 — 그래도 최신순 보장.

## Failure Scenarios

- 파생 쿼리 메서드명 오타 시 Spring Data 가 부팅 시점에 query derivation 실패 → 컨텍스트 로드 IT 에서 적발(단위 테스트는 jpaRepository mock 이라 미적발). 메서드명은 엔티티 속성(`tenantId`/`createdAt`/`templateId`)과 정확히 일치하도록 작성.
- 보조 정렬을 누락하면 동일 `createdAt` 다건에서 페이지 경계가 흔들려 행 누락/중복 가능 → `templateId` ASC 로 전순서 확보.
