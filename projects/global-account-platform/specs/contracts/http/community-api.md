# HTTP Contract: community-service (Public API)

Base path: `/api/community`

---

## POST /api/community/posts

아티스트 포스트 발행. `type=ARTIST_POST`는 ARTIST 역할 계정만 허용.

**Auth required**: Yes (JWT Bearer)

**Request**:
```json
{
  "type": "ARTIST_POST | FAN_POST",
  "visibility": "PUBLIC | MEMBERS_ONLY",
  "title": "string (optional, max 200, ARTIST_POST만)",
  "body": "string (required, max 10000)",
  "mediaUrls": ["string (URL, optional)"]
}
```

**Response 201**:
```json
{
  "postId": "string (UUID)",
  "type": "ARTIST_POST",
  "visibility": "PUBLIC",
  "status": "PUBLISHED",
  "authorAccountId": "string",
  "authorDisplayName": "string | null",
  "title": "string | null",
  "body": "string",
  "publishedAt": "2026-04-13T12:00:00Z",
  "createdAt": "2026-04-13T12:00:00Z"
}
```

**Errors**:
| Status | Code | 조건 |
|---|---|---|
| 403 | PERMISSION_DENIED | FAN_POST 계정이 ARTIST_POST 시도 |
| 422 | VALIDATION_ERROR | body 없음, 길이 초과 |
| 429 | RATE_LIMITED | 분당 포스트 발행 한도 초과 |

**Side Effects**:
- `post_status_history`에 DRAFT→PUBLISHED 기록
- `community.post.published` 이벤트 발행 (outbox)

---

## GET /api/community/posts/{postId}

포스트 단건 조회. MEMBERS_ONLY 포스트는 FAN_CLUB 구독 확인 후 응답.

**Auth required**: Yes (JWT Bearer)

**Path Parameters**:
| 파라미터 | 타입 | 설명 |
|---|---|---|
| postId | string (UUID) | 포스트 식별자 |

**Response 200**:
```json
{
  "postId": "string",
  "type": "ARTIST_POST | FAN_POST",
  "visibility": "PUBLIC | MEMBERS_ONLY",
  "status": "PUBLISHED",
  "authorAccountId": "string",
  "authorDisplayName": "string | null",
  "title": "string | null",
  "body": "string",
  "commentCount": 42,
  "reactionCount": 100,
  "myReaction": "HEART | null",
  "publishedAt": "2026-04-13T12:00:00Z"
}
```

**Errors**:
| Status | Code | 조건 |
|---|---|---|
| 403 | MEMBERSHIP_REQUIRED | MEMBERS_ONLY 포스트, 구독 없음 |
| 404 | POST_NOT_FOUND | 미존재 또는 DELETED |

---

## PATCH /api/community/posts/{postId}

포스트 내용 수정. 작성자 본인만 가능.

**Auth required**: Yes (JWT Bearer)

**Request body** (모든 필드 optional — 전달된 필드만 업데이트):
```json
{
  "title": "string",
  "body": "string",
  "mediaUrls": ["string"]
}
```

**Response 200**:
```json
{
  "postId": "string",
  "title": "string",
  "body": "string",
  "mediaUrls": ["string"],
  "updatedAt": "2026-04-30T12:00:00Z"
}
```

**Errors**:
| Status | Code | 조건 |
|---|---|---|
| 404 | POST_NOT_FOUND | 미존재 또는 DELETED |
| 403 | PERMISSION_DENIED | 작성자 본인이 아닌 경우 |
| 422 | VALIDATION_ERROR | body 없음, 길이 초과 |

---

## PATCH /api/community/posts/{postId}/status

포스트 상태 전이. 아티스트가 자신의 포스트에 대해 호출.

허용 전이 (ARTIST): `DRAFT → PUBLISHED`, `PUBLISHED → HIDDEN`, `PUBLISHED → DELETED`

**Auth required**: Yes (JWT Bearer)

**Request body**:
```json
{
  "status": "PUBLISHED | HIDDEN | DELETED",
  "reason": "string (optional)"
}
```

**Response 204**: (본문 없음)

**Errors**:
| Status | Code | 조건 |
|---|---|---|
| 404 | POST_NOT_FOUND | 미존재 또는 DELETED |
| 403 | PERMISSION_DENIED | 작성자 본인이 아닌 경우 |
| 422 | POST_STATUS_TRANSITION_INVALID | 허용되지 않는 전이 (예: DELETED→*) |
| 422 | VALIDATION_ERROR | status 필드 없음 또는 유효하지 않은 값 |

---

## GET /api/community/feed

팔로잉 아티스트 기반 피드 조회. 최신순 페이지네이션.

**Auth required**: Yes (JWT Bearer)

**Query Parameters**:
| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| page | int | No | 0부터 시작, 기본 0 |
| size | int | No | 기본 20, 최대 50 |

**Response 200**:
```json
{
  "content": [
    {
      "postId": "string",
      "type": "ARTIST_POST",
      "visibility": "PUBLIC",
      "authorAccountId": "string",
      "authorDisplayName": "string | null",
      "title": "string | null",
      "bodyPreview": "string (최대 200자)",
      "commentCount": 42,
      "reactionCount": 100,
      "publishedAt": "2026-04-13T12:00:00Z",
      "locked": false
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "hasNext": true
}
```

**참고**: `locked: true` — MEMBERS_ONLY 포스트 중 구독 없는 경우. body/title은 null 반환.

---

## POST /api/community/posts/{postId}/comments

포스트에 댓글 작성.

**Auth required**: Yes (JWT Bearer)

**Request**:
```json
{
  "body": "string (required, max 2000)"
}
```

**Response 201**:
```json
{
  "commentId": "string (UUID)",
  "postId": "string",
  "authorAccountId": "string",
  "authorDisplayName": "string | null",
  "body": "string",
  "createdAt": "2026-04-13T12:05:00Z"
}
```

**Errors**:
| Status | Code | 조건 |
|---|---|---|
| 403 | MEMBERSHIP_REQUIRED | MEMBERS_ONLY 포스트, 구독 없음 |
| 404 | POST_NOT_FOUND | 미존재 또는 DELETED/HIDDEN |
| 422 | VALIDATION_ERROR | body 없음, 길이 초과 |

**Side Effects**:
- `community.comment.created` 이벤트 발행 (outbox)

---

## POST /api/community/posts/{postId}/reactions

포스트에 반응 추가 또는 변경 (upsert). 계정당 포스트당 1개.

**Auth required**: Yes (JWT Bearer)

**Request**:
```json
{
  "emojiCode": "HEART | FIRE | CLAP | WOW | SAD"
}
```

**Response 200**:
```json
{
  "postId": "string",
  "emojiCode": "HEART",
  "totalReactions": 101
}
```

**Errors**:
| Status | Code | 조건 |
|---|---|---|
| 403 | MEMBERSHIP_REQUIRED | MEMBERS_ONLY 포스트, 구독 없음 |
| 404 | POST_NOT_FOUND | 미존재 또는 DELETED/HIDDEN |
| 422 | VALIDATION_ERROR | 허용되지 않는 emojiCode |

**Side Effects**:
- `community.reaction.added` 이벤트 발행 (outbox, 신규 또는 변경 시)

---

## POST /api/community/subscriptions/artists/{artistAccountId}

아티스트 팔로잉 (피드 구독).

**Auth required**: Yes (JWT Bearer)

**Response 200**:
```json
{
  "fanAccountId": "string",
  "artistAccountId": "string",
  "followedAt": "2026-04-13T12:00:00Z"
}
```

**Errors**:
| Status | Code | 조건 |
|---|---|---|
| 409 | ALREADY_FOLLOWING | 이미 팔로잉 중 |
| 404 | ARTIST_NOT_FOUND | 아티스트 계정 미존재 |

---

## DELETE /api/community/subscriptions/artists/{artistAccountId}

아티스트 언팔로잉.

**Auth required**: Yes (JWT Bearer)

**Response 204**: (본문 없음)

**Errors**:
| Status | Code | 조건 |
|---|---|---|
| 404 | NOT_FOLLOWING | 팔로잉 상태 아님 |
