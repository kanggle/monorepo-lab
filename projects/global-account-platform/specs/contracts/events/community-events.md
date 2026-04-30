# Event Contract: community-service

발행 방식: Outbox 패턴 ([libs/java-messaging](../../../libs/java-messaging))
파티션 키: `post_id` (포스트 관련 이벤트), `account_id` (구독 관련 이벤트)

---

## community.post.published

아티스트/팬 포스트가 PUBLISHED 상태로 전이될 때 발행.

**Topic**: `community.post.published`

**Payload**:
```json
{
  "postId": "string (UUID)",
  "authorAccountId": "string (UUID)",
  "type": "ARTIST_POST | FAN_POST",
  "visibility": "PUBLIC | MEMBERS_ONLY",
  "publishedAt": "2026-04-13T12:00:00Z"
}
```

**Consumers**: notification-service (향후), search-service (향후)

---

## community.comment.created

팬이 포스트에 댓글을 작성했을 때 발행.

**Topic**: `community.comment.created`

**Payload**:
```json
{
  "commentId": "string (UUID)",
  "postId": "string (UUID)",
  "postAuthorAccountId": "string (UUID)",
  "commenterAccountId": "string (UUID)",
  "createdAt": "2026-04-13T12:05:00Z"
}
```

**참고**: 댓글 본문은 PII 유출 방지를 위해 페이로드에 포함하지 않음. 소비자가 필요 시 API 조회.

**Consumers**: notification-service (향후 — 아티스트에게 댓글 알림)

---

## community.reaction.added

팬이 포스트에 반응을 추가하거나 변경했을 때 발행.

**Topic**: `community.reaction.added`

**Payload**:
```json
{
  "postId": "string (UUID)",
  "reactorAccountId": "string (UUID)",
  "emojiCode": "HEART | FIRE | CLAP | WOW | SAD",
  "isNew": true,
  "occurredAt": "2026-04-13T12:06:00Z"
}
```

**Consumers**: 향후 반응 집계 서비스

---

## Consumer Rules

- 멱등 처리 (`eventId` dedup, Redis 또는 DB unique index)
- 스키마 포워드 호환 (신규 필드는 무시)
- DLQ: `community.post.published.dlq`, `community.comment.created.dlq`, `community.reaction.added.dlq` (3회 재시도)
- W3C Trace Context 헤더 전파
