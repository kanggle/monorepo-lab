# Task ID

TASK-FAN-BE-003

# Title

fan-platform artist-service Spring Boot 부트스트랩 (artist 프로필 + fandom 메타데이터)

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- event
- deploy

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

# Goal

fan-platform 의 마스터 데이터 service 인 `artist-service` 를 부트스트랩한다. 이 service 는 **community-service 의 master data** — 아티스트 프로필 / fandom 메타데이터 / 디렉토리를 책임진다. community-service 의 `Follow` 가 가리키는 `artist_id` 는 본 service 가 발급한 식별자.

wms 의 [master-service](../../../wms-platform/apps/master-service/) 가 동일한 마스터 데이터 패턴(Hexagonal Architecture, ports/adapters) 의 reference — **본 task 는 그 패턴을 복제하고 fan-platform 의 multi-tenant + content-heavy + read-heavy 요구사항을 적용한다**.

이 태스크 완료 후:

- `projects/fan-platform/apps/artist-service/` 에 Spring Boot 3.4 + JPA + Hexagonal Architecture 기반 REST API 동작
- TASK-FAN-BE-001 gateway-service 의 placeholder route `artist` 가 활성화 (`gateway → artist-service:8080`)
- 핵심 엔티티: `Artist` (프로필), `ArtistGroup` (그룹↔멤버 N:M), `Fandom` (팬덤 메타데이터), `ArtistDirectory` (read model)
- 운영자(admin role) 만 artist 등록 / 수정 가능 — 일반 fan 은 read-only
- artist-service 가 발행하는 `artist_id` 가 community-service 의 `posts.author_account_id` (artist 작성 시) + `follows.artist_account_id` 의 참조 무결성 (logical FK, cross-service 라 DB FK 불가)
- `artist.created`, `artist.updated`, `artist.published` 이벤트 outbox 발행 — community-service / search-service 컨슈머 기반 (v2 search 인덱싱)
- 단위 + 슬라이스 + Testcontainers integration test
- `docker-compose.yml` 에 artist-service + DB schema 추가

---

# Scope

## In Scope

### 1. Project skeleton

- `projects/fan-platform/apps/artist-service/` 디렉토리 + `build.gradle` (Spring Boot starter, Spring Web, Spring Data JPA, Flyway, Spring Kafka, Spring Data Redis (캐시), OAuth2 Resource Server, Lombok, libs:java-common/web/security/observability/messaging)
- `Dockerfile` — multi-stage Eclipse Temurin 21 JRE
- `settings.gradle` 루트에 `'projects:fan-platform:apps:artist-service'` include 추가

### 2. Application configuration

- `ArtistServiceApplication.java` (main)
- `application.yml` (default + `local` + `test`):
  - `spring.application.name: fan-platform-artist-service`
  - `spring.datasource` (Postgres) — `jdbc:postgresql://${POSTGRES_HOST:postgres}:5432/${POSTGRES_DB:fanplatform_artist}` (community 와 별도 DB schema 권장 — bounded context 격리)
  - `spring.flyway.enabled: true`, locations `classpath:db/migration/artist`
  - OAuth2 RS server: GAP issuer + JWKS URI
  - Redis 캐시: `spring.cache.type: redis`, namespace `cache:fan-platform:artist:`
- `application-test.yml` — Testcontainers 가정

### 3. Domain layer (Hexagonal — wms master-service 패턴 따름)

`apps/artist-service/src/main/java/com/example/fanplatform/artist/`:

```
├── ArtistServiceApplication.java
├── adapter/
│   ├── in/
│   │   └── web/
│   │       ├── controller/
│   │       │   ├── ArtistController.java               ← /api/artists CRUD
│   │       │   ├── ArtistDirectoryController.java      ← /api/artists?q= 검색 + 페이지네이션
│   │       │   ├── ArtistGroupController.java          ← /api/artist-groups
│   │       │   └── FandomController.java               ← /api/fandoms
│   │       ├── dto/
│   │       ├── advice/
│   │       │   └── GlobalExceptionHandler.java
│   │       └── filter/
│   │           └── TenantClaimEnforcer.java            ← service 레벨 fail-closed
│   └── out/
│       ├── persistence/
│       │   ├── ArtistJpaEntity.java
│       │   ├── ArtistJpaRepository.java
│       │   ├── ArtistRepositoryAdapter.java            ← implements ArtistRepository (port)
│       │   ├── ArtistGroupJpaEntity.java
│       │   ├── ArtistGroupJpaRepository.java
│       │   ├── ArtistGroupRepositoryAdapter.java
│       │   ├── FandomJpaEntity.java
│       │   ├── FandomJpaRepository.java
│       │   ├── FandomRepositoryAdapter.java
│       │   └── outbox/
│       │       ├── OutboxEvent.java
│       │       └── OutboxRelay.java
│       ├── cache/
│       │   └── ArtistDirectoryCacheAdapter.java        ← Redis read-through
│       └── event/
│           └── ArtistEventPublisherAdapter.java        ← outbox 적재 어댑터
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── RegisterArtistUseCase.java              ← (admin only)
│   │   │   ├── UpdateArtistUseCase.java
│   │   │   ├── PublishArtistUseCase.java               ← DRAFT → PUBLISHED
│   │   │   ├── ArchiveArtistUseCase.java
│   │   │   ├── GetArtistUseCase.java
│   │   │   ├── SearchArtistDirectoryUseCase.java
│   │   │   ├── CreateArtistGroupUseCase.java
│   │   │   ├── AddGroupMemberUseCase.java
│   │   │   ├── RemoveGroupMemberUseCase.java
│   │   │   └── UpdateFandomUseCase.java
│   │   └── out/
│   │       ├── ArtistRepository.java                   ← port
│   │       ├── ArtistGroupRepository.java
│   │       ├── FandomRepository.java
│   │       ├── ArtistDirectoryCache.java
│   │       └── ArtistEventPublisher.java
│   └── service/                                         ← use-case 구현
│       ├── ArtistManagementService.java
│       ├── ArtistDirectoryService.java
│       ├── ArtistGroupService.java
│       └── FandomService.java
├── domain/
│   ├── artist/
│   │   ├── Artist.java                                 ← aggregate root
│   │   ├── ArtistId.java                               ← UUID v7
│   │   ├── ArtistProfile.java                          ← VO: stage_name, real_name?, debut_date, agency, bio
│   │   ├── ArtistStatus.java                           ← enum: DRAFT / PUBLISHED / ARCHIVED
│   │   └── ArtistType.java                             ← enum: SOLO / GROUP_MEMBER
│   ├── group/
│   │   ├── ArtistGroup.java
│   │   ├── ArtistGroupId.java
│   │   ├── GroupMembership.java                        ← (group_id, artist_id, role, joined_at, left_at?)
│   │   └── GroupRole.java                              ← enum: LEADER / MEMBER / FORMER_MEMBER
│   ├── fandom/
│   │   ├── Fandom.java                                 ← (artist_id, fandom_name, color_hex, founded_at)
│   │   └── FandomId.java
│   └── tenant/
│       └── TenantContext.java
└── config/
    ├── SecurityConfig.java                              ← OAuth2 RS + admin role 강제
    ├── WebConfig.java
    ├── RedisCacheConfig.java
    └── KafkaProducerConfig.java
```

### 4. Database schema (Flyway)

- `db/migration/artist/V1__init.sql`:
  - `artists` (id UUID, tenant_id, type, status, stage_name, real_name, debut_date, agency, bio, profile_image_ref, created_at, updated_at, published_at, INDEX (tenant_id, status, stage_name), INDEX (tenant_id, type))
  - `artist_groups` (id UUID, tenant_id, name, debut_date, agency, profile_image_ref, status, created_at, updated_at, INDEX (tenant_id, name))
  - `group_memberships` (group_id, artist_id, role, joined_at, left_at, PK (group_id, artist_id, joined_at), INDEX (artist_id))
  - `fandoms` (artist_id PK, tenant_id, fandom_name, color_hex, founded_at, slogan, created_at, updated_at)
  - `outbox_events` (community-service 와 동일 스키마, 별도 테이블)

모든 테이블에 `tenant_id` + multi-tenant 인덱스.

### 5. API contract (신규)

- `projects/fan-platform/specs/contracts/http/artist-api.md` (신규):
  - `POST /api/artists` — 아티스트 등록 (admin only, DRAFT)
  - `GET /api/artists/{id}` — 단건 조회 (PUBLISHED 만 일반 사용자 가능, admin 은 모든 status)
  - `GET /api/artists?q=&type=&page=&size=` — 디렉토리 검색 (PUBLISHED 만, Redis 캐시)
  - `PATCH /api/artists/{id}` — 프로필 수정 (admin)
  - `PATCH /api/artists/{id}/status` — 상태 전이 (DRAFT → PUBLISHED, PUBLISHED → ARCHIVED)
  - `POST /api/artist-groups` — 그룹 생성 (admin)
  - `GET /api/artist-groups/{id}` — 그룹 조회 + 멤버 리스트
  - `POST /api/artist-groups/{id}/members` — 멤버 추가 (admin)
  - `DELETE /api/artist-groups/{id}/members/{artistId}` — 멤버 제거 / 탈퇴 (admin)
  - `GET /api/fandoms/{artistId}` — 팬덤 메타데이터
  - `PUT /api/fandoms/{artistId}` — 팬덤 업데이트 (admin)
  - 응답 envelope `{ data, meta }` + 에러 `{ code, message, details? }` (community-api 와 일관)

### 6. Event contracts (신규)

- `projects/fan-platform/specs/contracts/events/artist-events.md` (신규):
  - `artist.registered` — { artist_id, tenant_id, type, stage_name, registered_by, occurred_at }
  - `artist.published` — { artist_id, tenant_id, published_at }
  - `artist.updated` — { artist_id, tenant_id, changed_fields[], occurred_at }
  - `artist.archived` — { artist_id, tenant_id, archived_at, reason? }
  - `artist.group_created` — { group_id, tenant_id, name }
  - `artist.group_member_changed` — { group_id, artist_id, role, action: ADDED/REMOVED, occurred_at }
  - 이벤트 envelope: community-events 와 동일 (`event_id`, `event_type`, `occurred_at`, `payload`, `tenant_id`)
- outbox 패턴 — `libs:java-messaging` 활용

### 7. spec 작성

- `projects/fan-platform/specs/services/artist-service/architecture.md` — Service Type=`rest-api`, Architecture Style=Hexagonal, wms master-service 의 동일 파일 패턴 따름
- `projects/fan-platform/specs/services/artist-service/data-model.md`
- `projects/fan-platform/specs/services/artist-service/dependencies.md` — postgres, redis, kafka, GAP IdP
- `projects/fan-platform/specs/services/artist-service/observability.md`
- `projects/fan-platform/specs/services/artist-service/overview.md`

### 8. Tests

- 단위:
  - `ArtistTest`, `ArtistGroupTest`, `FandomTest` (도메인 invariant)
  - 각 use-case (`*Service`) 단위 테스트
  - `TenantClaimEnforcerTest`
- 슬라이스:
  - 4 controller `@WebMvcTest`
- 통합 (`@Tag("integration")`):
  - `ArtistServiceIntegrationTest` — happy path (등록 → 발행 → 검색 → 업데이트)
  - `AdminRoleEnforcementIntegrationTest` — 일반 fan 토큰으로 POST/PATCH 시도 → 403 `FORBIDDEN`
  - `MultiTenantIsolationTest` — cross-tenant 조회 시 404
  - `OutboxRelayIntegrationTest` — `artist.published` Kafka 발행 검증
  - `ArtistDirectoryCacheIntegrationTest` — Redis 캐시 + invalidation
- 컨트랙트: `ArtistApiContractTest`

### 9. gateway-service 라우트 활성화

- TASK-FAN-BE-001 의 `application.yml` 의 `artist` 라우트가 이미 `http://artist-service:8080` 으로 향함. 본 task 에서 추가 변경 불필요.

### 10. docker-compose 통합

- `projects/fan-platform/docker-compose.yml` 에 추가:
  - `artist-service`: `expose: ["8080"]`, `depends_on: [postgres, kafka, redis]`, networks: `fan-platform-net`
  - 환경변수 community-service 와 동일 (DB schema 만 다름)

## Out of Scope

- community-service 부트스트랩 — TASK-FAN-BE-002 (별도 task)
- 미디어 업로드 (S3/MinIO) — v2
- 검색 인덱싱 (Elasticsearch) — v2 search-service
- 아티스트 ↔ 팬 메시지 / DM — 도메인 자체가 아님 (out of fan-platform scope)
- artist 인증 (artist 본인이 자기 프로필 수정) — v1 은 admin only, artist self-service 는 v2
- 통계 / 대시보드 (팔로워 수 집계 등) — v2

---

# Acceptance Criteria

- [ ] `./gradlew :projects:fan-platform:apps:artist-service:build` 통과
- [ ] `./gradlew :projects:fan-platform:apps:artist-service:check` 통과
- [ ] `./gradlew :projects:fan-platform:apps:artist-service:integrationTest` 통과
- [ ] `pnpm fan-platform:up` 후 `curl http://fan-platform.local/api/artists` 가 401 (인증 없음)
- [ ] admin role JWT 로 `POST /api/artists` → 201 + outbox 행 + Kafka `artist.registered` 발행
- [ ] 일반 fan role JWT 로 `POST /api/artists` → 403 `FORBIDDEN`
- [ ] DRAFT 상태 artist 는 일반 사용자 GET 시 404 (admin 만 조회 가능)
- [ ] PUBLISHED 상태 artist 는 누구나 GET 가능
- [ ] `tenant_id=wms` JWT → gateway 가 403 차단
- [ ] 디렉토리 검색 (`GET /api/artists?q=...`) 두 번째 호출 시 Redis 캐시 hit (메트릭 검증)
- [ ] artist 업데이트 시 캐시 invalidation 동작
- [ ] Flyway 멱등 + 모든 테이블 `tenant_id` + 인덱스
- [ ] `specs/contracts/http/artist-api.md` + `specs/contracts/events/artist-events.md` 작성
- [ ] `specs/services/artist-service/architecture.md` 가 [platform/architecture-decision-rule.md](../../../../platform/architecture-decision-rule.md) 형식

---

# Related Specs

- `projects/fan-platform/PROJECT.md` § Service Map (v1) — artist-service 책임
- `projects/fan-platform/PROJECT.md` § GAP IdP Integration
- `projects/fan-platform/specs/integration/gap-integration.md` (TASK-FAN-BE-001)
- `rules/domains/fan-platform.md` § F1 (asymmetric content), F2 (fail-closed), F7 (multi-tenant)
- `rules/traits/transactional.md`
- `rules/traits/content-heavy.md`
- `rules/traits/read-heavy.md`
- `rules/traits/multi-tenant.md` § M2
- `rules/traits/integration-heavy.md`
- `platform/event-driven-policy.md`
- `projects/wms-platform/apps/master-service/` (Hexagonal master-data reference)
- `projects/wms-platform/specs/services/master-service/architecture.md` (architecture template)

# Related Skills

- `.claude/skills/backend/springboot-api/SKILL.md`
- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/exception-handling/SKILL.md`
- `.claude/skills/backend/validation/SKILL.md`
- `.claude/skills/backend/dto-mapping/SKILL.md`
- `.claude/skills/backend/transaction-handling/SKILL.md`
- `.claude/skills/backend/pagination/SKILL.md`
- `.claude/skills/backend/observability-metrics/SKILL.md`
- `.claude/skills/messaging/event-implementation/SKILL.md`
- `.claude/skills/messaging/outbox-pattern/SKILL.md`
- `.claude/skills/database/schema-change-workflow/SKILL.md`
- `.claude/skills/database/indexing/SKILL.md`
- `.claude/skills/cross-cutting/caching/SKILL.md`
- `.claude/skills/testing/testcontainers/SKILL.md`
- `.claude/skills/testing/contract-test/SKILL.md`
- `.claude/skills/service-types/rest-api-setup/SKILL.md`

---

# Related Contracts

- `projects/fan-platform/specs/contracts/http/artist-api.md` (신규)
- `projects/fan-platform/specs/contracts/events/artist-events.md` (신규)
- `projects/fan-platform/specs/contracts/http/community-api.md` — community-service 의 follow / post.author_account_id 가 본 service 의 artist_id 참조

---

# Target Service / Component

- `projects/fan-platform/apps/artist-service/` (신규)
- `projects/fan-platform/specs/services/artist-service/{architecture,data-model,dependencies,observability,overview}.md` (신규)
- `projects/fan-platform/specs/contracts/http/artist-api.md` (신규)
- `projects/fan-platform/specs/contracts/events/artist-events.md` (신규)
- `projects/fan-platform/docker-compose.yml` (수정 — artist-service 추가)
- `projects/fan-platform/.env.example` (수정)
- `settings.gradle` (루트, artist-service include)

---

# Architecture

`platform/architecture-decision-rule.md` 따름. Service Type = `rest-api`. Architecture Style = **Hexagonal (ports/adapters)** — wms master-service 와 동일.

본 service 는 community-service 와 다르게 Hexagonal 을 선택하는 이유:
- 마스터 데이터 service 는 외부 호출 / 통합이 단순 (read-heavy + 이벤트 발행) 이므로 ports/adapters 격리가 명확
- 향후 search 인덱싱 어댑터 / 외부 음악 메타데이터 API (Spotify) 어댑터 등 추가 시 `application/port/out/` 만 확장하면 됨
- wms master-service 의 검증된 패턴 재사용

---

# Implementation Notes

- **wms master-service 를 첫 reference 로 복제** + 다음 변경:
  - 패키지: `com.wms.master` → `com.example.fanplatform.artist`
  - 도메인 엔티티: Warehouse/Zone/Location/Sku/Lot → Artist/ArtistGroup/Fandom
  - tenant 값 fan-platform 적용
  - outbox + Kafka 발행 추가 (wms master-service 가 이미 outbox 패턴이면 그대로, 아니면 community-service 와 동일 패턴 추가)
- artist-service 가 발행하는 `artist_id` 는 **community-service 의 `posts.author_account_id` (artist 작성 시) 와 `follows.artist_account_id` 의 logical FK**. cross-service 라 DB FK 는 불가 — 대신 community-service 가 follow / post 생성 시 artist-service 에 동기 HTTP 검증 (또는 캐시된 artist 디렉토리 활용). v1 은 단순 검증 생략 (eventually consistent), v2 에서 강화.
- admin role 강제: `SecurityConfig` 에서 `POST/PATCH/DELETE` 는 `hasAuthority('ROLE_ADMIN')` 요구. role 은 GAP JWT 의 `roles` claim 에서 추출.
- DRAFT vs PUBLISHED visibility:
  - DRAFT: admin 만 GET 가능
  - PUBLISHED: 인증된 모든 사용자 GET 가능 (tenant_id 일치 시)
  - ARCHIVED: admin 만 GET 가능
- 디렉토리 검색은 read-heavy — Redis 캐시 read-through. 캐시 키: `cache:fan-platform:artist:directory:<query-hash>`. TTL 5분. PUBLISH/UPDATE/ARCHIVE 시 캐시 invalidation (eventually consistent — 5분 안에 stale 가능, 명시적 acceptable).
- v1 은 검색이 단순 `LIKE '%query%'` (Postgres `pg_trgm` 인덱스). v2 에서 search-service (Elasticsearch) 로 대체.

---

# Edge Cases

- **stage_name 충돌**: 같은 tenant 내 같은 stage_name 으로 두 artist 등록 시 — `409 CONFLICT` (unique constraint per tenant_id + stage_name).
- **그룹 멤버 중복 추가**: 같은 (group_id, artist_id) 활성 멤버십 추가 시 422 `ALREADY_MEMBER`.
- **그룹 해체**: 그룹 ARCHIVE 시 멤버는 `left_at` 기록 (cascade soft-delete).
- **fandom 단일성**: artist:fandom = 1:1 — 두 번째 fandom 생성 시 422 `FANDOM_ALREADY_EXISTS`.
- **DRAFT artist 의 fandom 생성**: PUBLISHED 이후만 허용. DRAFT 상태에서는 422 `ARTIST_NOT_PUBLISHED`.
- **archived artist 참조**: community-service 의 follow / post 가 archived artist 를 참조 — community 는 별도 처리 (조회 시 "이 아티스트는 더 이상 활동하지 않습니다" 표시).
- **cross-tenant artist_id 추측**: 임의 UUID 로 다른 tenant artist 조회 시 404 (존재 자체 누설 방지).

---

# Failure Scenarios

- **Postgres 다운**: 5xx — gateway 503. circuit breaker.
- **Kafka 다운**: outbox 적재 성공, relay 만 실패. 재시도 backoff. metric `artist_outbox_publish_failures_total`.
- **Redis 캐시 다운**: 디렉토리 검색 직접 DB query (fail-open). metric `artist_directory_cache_unavailable_total`.
- **graph 일관성 위배 (community 의 follow → artist_id 가 archive 된 경우)**: community-service 가 자체 처리. artist-service 는 archive 시 이벤트만 발행.
- **outbox dead letter**: community-service 와 동일 — 1시간 + retry > 10 → DLQ + 알림 (v2).
- **stage_name unique 동시성 race**: 두 클라이언트가 같은 이름 동시 POST — DB unique constraint 가 한 쪽 reject. 422 envelope 반환.

---

# Test Requirements

- 단위: 도메인 invariant + use-case + tenant enforcer
- 슬라이스: 4 controller `@WebMvcTest`
- 통합 (`@Tag("integration")`):
  - `ArtistServiceIntegrationTest` — happy path
  - `AdminRoleEnforcementIntegrationTest` — fan / admin role 차이
  - `MultiTenantIsolationTest` — cross-tenant 격리
  - `OutboxRelayIntegrationTest` — Kafka 발행
  - `ArtistDirectoryCacheIntegrationTest` — Redis 캐시 + invalidation
  - `StageNameUniquenessIntegrationTest` — unique constraint per tenant
- 컨트랙트: `ArtistApiContractTest`

---

# Definition of Done

- [ ] artist-service 코드 작성 완료 (adapter / application / domain / config)
- [ ] Flyway V1 마이그레이션 + `tenant_id` + 인덱스 + unique constraint
- [ ] 단위 + 슬라이스 + 통합 + 컨트랙트 테스트 작성 + 통과
- [ ] `specs/services/artist-service/{architecture,data-model,dependencies,observability,overview}.md` 작성
- [ ] `specs/contracts/http/artist-api.md` + `specs/contracts/events/artist-events.md` 작성
- [ ] `docker-compose.yml` + `.env.example` 갱신
- [ ] `settings.gradle` 갱신
- [ ] gateway-service `artist` 라우트로 end-to-end 동작 (`pnpm fan-platform:up` + curl)
- [ ] outbox → Kafka 발행 통합 테스트 통과
- [ ] admin role 강제 통합 테스트 통과
- [ ] Ready for review
