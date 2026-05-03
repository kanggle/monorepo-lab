# artist-service — Overview

> 1-pager: responsibilities, public API surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `artist-service` |
| Project | `fan-platform` |
| Service Type | `rest-api` |
| Architecture Style | **Hexagonal (ports/adapters)** |
| Stack | Java 21, Spring Boot 3.4, Postgres 16, Redis 7, Kafka 3.7 |
| Deployable unit | `apps/artist-service/` |
| Bounded Context | `artist` (artist profile / group / fandom — community-service의 master data) |
| Persistent stores | Postgres (`fanplatform_artist` DB), Redis (directory search cache only) |
| Event publication | `artist.registered.v1`, `artist.published.v1`, `artist.updated.v1`, `artist.archived.v1`, `artist.group_created.v1`, `artist.group_member_changed.v1` |

## Responsibilities

- **아티스트 프로필 관리** — 등록 / 수정 / 발행 / 아카이브. DRAFT → PUBLISHED → ARCHIVED 라이프사이클. 운영자 (admin role) 만 쓰기 가능.
- **그룹 / 멤버십** — 그룹 생성, 멤버 추가 / 제거 (활성/탈퇴). 같은 (group, artist) 활성 멤버십 중복 거부.
- **팬덤 메타데이터** — artist 1:1 fandom (이름, 색상, 슬로건, 창립일). 발행된 아티스트만 fandom 생성 가능.
- **디렉토리 검색** — `GET /api/artists?q=...` PUBLISHED 아티스트만 read-through Redis 캐시 (TTL 5분).
- **이벤트 발행** — outbox 패턴 (libs:java-messaging), 6개 토픽. community-service 와 search-service (v2) 가 컨슈머.
- **테넌트 격리** — 모든 테이블 `tenant_id`, 모든 쿼리 `WHERE tenant_id = ?`. 서비스 레벨에서 fail-closed 재검증.

## Public API surface (요약)

자세한 스펙은 `specs/contracts/http/artist-api.md` 참조.

| Method | Path | 설명 | Auth |
|---|---|---|---|
| POST | `/api/artists` | 아티스트 등록 (DRAFT) | bearer + ADMIN |
| GET | `/api/artists/{id}` | 단건 조회 (DRAFT/ARCHIVED는 admin만) | bearer |
| GET | `/api/artists?q=&type=&page=&size=` | 디렉토리 검색 (PUBLISHED만, Redis cache) | bearer |
| PATCH | `/api/artists/{id}` | 프로필 수정 | bearer + ADMIN |
| PATCH | `/api/artists/{id}/status` | 상태 전이 (PUBLISHED / ARCHIVED) | bearer + ADMIN |
| POST | `/api/artist-groups` | 그룹 생성 | bearer + ADMIN |
| GET | `/api/artist-groups/{id}` | 그룹 + 멤버 리스트 | bearer |
| POST | `/api/artist-groups/{id}/members` | 멤버 추가 | bearer + ADMIN |
| DELETE | `/api/artist-groups/{id}/members/{artistId}` | 멤버 탈퇴 (left_at + FORMER_MEMBER) | bearer + ADMIN |
| GET | `/api/fandoms/{artistId}` | 팬덤 조회 | bearer |
| POST | `/api/fandoms/{artistId}` | 팬덤 생성 (artist must be PUBLISHED, 1:1) | bearer + ADMIN |
| PATCH | `/api/fandoms/{artistId}` | 팬덤 수정 (404 if no fandom yet) | bearer + ADMIN |

`/actuator/health`, `/actuator/info`, `/actuator/prometheus` 는 인증 없이 접근.

## Key invariants

1. **Tenant isolation (multi-tenant.md M2)** — 모든 row 의 `tenant_id` 컬럼이 가드되며, 서비스 토큰의 `tenant_id` 와 일치해야 한다. SUPER_ADMIN 의 `*` wildcard 만 예외.
2. **Stage name uniqueness** — `(tenant_id, stage_name)` UNIQUE 제약. 동시 등록 race 는 DB 가 한쪽 reject → 409 STAGE_NAME_CONFLICT.
3. **Status machine (transactional.md T4)** — DRAFT → PUBLISHED → ARCHIVED 만 허용. PUBLISHED → DRAFT 는 422 STATE_TRANSITION_INVALID.
4. **DRAFT/ARCHIVED visibility = admin-only** — 일반 사용자가 DRAFT/ARCHIVED 단건 조회 시 404 (do not leak existence).
5. **Outbox at-least-once** — 비즈니스 트랜잭션과 outbox 적재가 한 트랜잭션. 컨슈머는 `eventId` 기반 멱등 처리.
6. **Cross-tenant non-disclosure** — 다른 테넌트의 artist id 로 조회해도 404.
7. **Fandom 1:1 + ARTIST_PUBLISHED prerequisite** — DRAFT 아티스트의 fandom 생성 시 422 ARTIST_NOT_PUBLISHED.

## Out of scope (v1)

- 미디어 업로드 (S3/MinIO) — 프로필 이미지 reference URL 만 저장
- 검색 인덱싱 (Elasticsearch) — v2 search-service
- 아티스트 self-service (artist 본인이 자기 프로필 수정) — v1 admin only
- 통계 / 대시보드 (팔로워 수 집계 등) — v2 admin-service
- artist 인증 (artist 본인 로그인) — out of scope; GAP 통합 admin 계정 모델로 처리
