# community-service — Overview

> 1-pager: responsibilities, public API surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `community-service` |
| Project | `fan-platform` |
| Service Type | `rest-api` |
| Architecture Style | **Layered + 명시적 상태 기계** |
| Stack | Java 21, Spring Boot 3.4, Postgres 16, Redis 7, Kafka 3.7 |
| Deployable unit | `apps/community-service/` |
| Bounded Context | `community` (post / comment / reaction / follow) |
| Persistent stores | Postgres (`fanplatform_community` DB), Redis (feed cache only) |
| Event publication | `community.post.published.v1`, `community.post.status_changed.v1`, `community.comment.added.v1`, `community.reaction.added.v1` |

## Responsibilities

- **포스트 라이프사이클** — DRAFT/PUBLISHED/HIDDEN/DELETED 상태 기계 + 작성자 + OPERATOR 전이 매트릭스 + append-only `post_status_history`.
- **3-tier visibility** — `PUBLIC` / `MEMBERS_ONLY` / `PREMIUM`. v1 PREMIUM 는 membership-service 미존재로 항상 통과 + WARN.
- **댓글** — 포스트 단위 1-depth, 작성자 또는 OPERATOR 만 삭제 가능 (soft-delete `deleted_at`).
- **반응** — `(post, reactor)` PK 기반 멱등 upsert. `LIKE/LOVE/FIRE/SAD`.
- **팔로우** — 비대칭 `(fan, artist)`. self-follow 거부, 중복 거부.
- **팬 피드** — 팔로우한 아티스트의 PUBLISHED 포스트 페이지네이션, Redis 캐시 (5분 TTL, fail-open).
- **이벤트 발행** — outbox 패턴 (libs:java-messaging), 4개 토픽.
- **테넌트 격리** — 모든 테이블 `tenant_id`, 모든 쿼리 `WHERE tenant_id = ?`. 서비스 레벨에서 fail-closed 재검증.

## Public API surface (요약)

자세한 스펙은 `specs/contracts/http/community-api.md` 참조.

| Method | Path | 설명 | Auth |
|---|---|---|---|
| POST | `/api/community/posts` | 포스트 발행 (PUBLISHED) | bearer |
| GET | `/api/community/posts/{id}` | 단건 조회 (visibility 검사) | bearer |
| PATCH | `/api/community/posts/{id}` | 본문/미디어 수정 (5분 grace window) | bearer |
| PATCH | `/api/community/posts/{id}/status` | 상태 전이 (HIDDEN/DELETED) | bearer |
| DELETE | `/api/community/posts/{id}` | 삭제 (= status DELETED) | bearer |
| GET | `/api/community/feed` | 팔로우 기반 피드 (paginated) | bearer |
| POST | `/api/community/posts/{id}/comments` | 댓글 추가 | bearer |
| DELETE | `/api/community/posts/{id}/comments/{cid}` | 댓글 삭제 | bearer (작성자 or OPERATOR) |
| PUT | `/api/community/posts/{id}/reactions` | 반응 추가/변경 (멱등) | bearer |
| DELETE | `/api/community/posts/{id}/reactions` | 반응 제거 | bearer |
| POST | `/api/community/follows` | 아티스트 팔로우 | bearer |
| DELETE | `/api/community/follows/{artistId}` | 언팔로우 | bearer |

`/actuator/health`, `/actuator/info`, `/actuator/prometheus` 는 인증 없이 접근.

## Key invariants

1. **Tenant isolation (multi-tenant.md M2)** — 모든 row 의 `tenant_id` 컬럼이 가드되며, 서비스 토큰의 `tenant_id` 와 일치해야 한다. SUPER_ADMIN 의 `*` wildcard 만 예외.
2. **Status machine (transactional.md T4)** — `PostStatusMachine.ensureTransitionAllowed` 통과 없이는 어떤 status 전이도 일어나지 않는다. `PUBLISHED → DRAFT` 는 항상 422.
3. **Audit append-only** — `post_status_history` 는 INSERT 만. 모든 status 변경은 1 row 추가.
4. **Outbox at-least-once** — 비즈니스 트랜잭션과 outbox 적재가 한 트랜잭션. 컨슈머는 `event_id` 기반 멱등 처리.
5. **Cross-tenant non-disclosure** — 다른 테넌트의 post id 로 조회해도 404 (403 아님 — 존재 누설 방지).

## Out of scope (v1)

- artist-service (artist profile + fandom) — TASK-FAN-BE-003
- 미디어 업로드 (S3/MinIO 통합) — v2
- membership-service 통합 — v2 (현재는 PREMIUM 항상 통과 + TODO + WARN)
- notification-service / search-service — v2
- 모더레이션 워크플로 — v2 admin-service
