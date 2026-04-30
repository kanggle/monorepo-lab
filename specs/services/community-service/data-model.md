# community-service — Data Model

## Design Decision

포스트 상태는 `UPDATE posts SET status = ?` 금지. 모든 전이는 `PostStatusMachine`을 통해 `post_status_history`에 append-only 기록 ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A3).

`reactions`는 계정당 포스트당 1개 제한 — upsert (`INSERT ... ON DUPLICATE KEY UPDATE`) 사용.

## Tables

### posts

| 컬럼 | 타입 | 제약 | PII 등급 | 설명 |
|---|---|---|---|---|
| id | VARCHAR(36) | PK (UUID) | internal | 포스트 식별자 |
| author_account_id | VARCHAR(36) | NOT NULL, INDEX | internal | 작성자 계정 ID (account-service 참조) |
| type | VARCHAR(20) | NOT NULL | internal | ARTIST_POST / FAN_POST |
| visibility | VARCHAR(20) | NOT NULL, DEFAULT 'PUBLIC' | internal | PUBLIC / MEMBERS_ONLY |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'DRAFT' | internal | DRAFT / PUBLISHED / HIDDEN / DELETED |
| title | VARCHAR(200) | NULL | internal | 포스트 제목 (아티스트 포스트만) |
| body | TEXT | NULL | internal | 포스트 본문 |
| media_urls | JSON | NULL | internal | 첨부 미디어 URL 목록 |
| published_at | DATETIME(6) | NULL | internal | 최초 발행 일시 |
| created_at | DATETIME(6) | NOT NULL | internal | 생성 일시 |
| updated_at | DATETIME(6) | NOT NULL | internal | 최종 수정 일시 |
| deleted_at | DATETIME(6) | NULL | internal | 소프트 삭제 일시 |
| version | INT | NOT NULL, DEFAULT 0 | internal | 낙관적 락 (T5) |

**인덱스**: idx_posts_author (author_account_id), idx_posts_status_published (status, published_at DESC), idx_posts_type_visibility (type, visibility)

### post_status_history (append-only)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK AUTO_INCREMENT | — |
| post_id | VARCHAR(36) | NOT NULL, INDEX | 포스트 참조 |
| from_status | VARCHAR(20) | NOT NULL | 이전 상태 |
| to_status | VARCHAR(20) | NOT NULL | 후 상태 |
| actor_type | VARCHAR(20) | NOT NULL | author / operator / system |
| actor_id | VARCHAR(36) | NULL | 처리 주체 ID |
| reason | VARCHAR(100) | NULL | 전이 사유 |
| occurred_at | DATETIME(6) | NOT NULL | UTC |

**불변성**: DB 트리거로 UPDATE/DELETE 차단 (A3)

### comments

| 컬럼 | 타입 | 제약 | PII 등급 | 설명 |
|---|---|---|---|---|
| id | VARCHAR(36) | PK (UUID) | internal | 댓글 식별자 |
| post_id | VARCHAR(36) | NOT NULL, INDEX | internal | 포스트 참조 |
| author_account_id | VARCHAR(36) | NOT NULL, INDEX | internal | 작성자 계정 ID |
| body | TEXT | NOT NULL | internal | 댓글 본문 |
| created_at | DATETIME(6) | NOT NULL | internal | 생성 일시 |
| deleted_at | DATETIME(6) | NULL | internal | 소프트 삭제 일시 |

**인덱스**: idx_comments_post (post_id, created_at)

### reactions

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| post_id | VARCHAR(36) | PK (composite) | 포스트 참조 |
| account_id | VARCHAR(36) | PK (composite) | 반응한 계정 |
| emoji_code | VARCHAR(20) | NOT NULL | 반응 이모지 코드 (e.g. HEART, FIRE) |
| created_at | DATETIME(6) | NOT NULL | 최초 반응 일시 |
| updated_at | DATETIME(6) | NOT NULL | 마지막 반응 변경 일시 |

**UNIQUE KEY**: (post_id, account_id) — 계정당 포스트당 1개 보장

### feed_subscriptions

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| fan_account_id | VARCHAR(36) | PK (composite) | 팬 계정 ID |
| artist_account_id | VARCHAR(36) | PK (composite) | 팔로잉할 아티스트 계정 ID |
| followed_at | DATETIME(6) | NOT NULL | 팔로우 일시 |

**인덱스**: idx_feed_fan (fan_account_id, followed_at DESC)

### outbox_events

표준 outbox 스키마 ([libs/java-messaging](../../../libs/java-messaging) `OutboxJpaEntity` 참조).

## Migration Strategy

| 버전 | 파일 | 내용 |
|---|---|---|
| V0001 | `V0001__create_posts_and_feed.sql` | posts, feed_subscriptions 테이블 |
| V0002 | `V0002__create_post_status_history.sql` | post_status_history + append-only 트리거 |
| V0003 | `V0003__create_comments_and_reactions.sql` | comments, reactions 테이블 |
| V0004 | `V0004__create_outbox_events.sql` | outbox_events 테이블 |

## PII 분류

- `posts.body`, `comments.body` — internal (콘텐츠, 공개 가능)
- `posts.author_account_id`, `comments.author_account_id` — internal (계정 ID, PII 아님)
- 실제 사용자 이름·이메일은 이 서비스에 저장하지 않음 — account-service에서 조회
