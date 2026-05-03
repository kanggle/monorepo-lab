# community-api (community-service HTTP contract)

> All endpoints require an `Authorization: Bearer <RS256 JWT>` issued by GAP
> with `tenant_id ∈ { fan-platform, * }`. Tokens with any other tenant value
> get 403 `TENANT_FORBIDDEN`.
>
> All requests are routed through the fan-platform gateway under the prefix
> `/api/v1/community/**`; the gateway forwards to the service which serves
> `/api/community/**`. Path examples below use the service-internal path.

## Envelope shapes

### Success
```json
{
  "data": { ... },
  "meta": {
    "timestamp": "2026-05-03T00:00:00Z"
  }
}
```

### Error (matches `platform/error-handling.md` flat shape)
```json
{
  "code": "POST_NOT_FOUND",
  "message": "Post not found: 0190f3e2-...",
  "details": { "...": "..." },
  "timestamp": "2026-05-03T00:00:00Z"
}
```

### Common error codes

| HTTP | code | When |
|---|---|---|
| 400 | VALIDATION_ERROR | malformed JSON / type mismatch |
| 401 | UNAUTHORIZED | missing / expired / invalid signature |
| 403 | TENANT_FORBIDDEN | `tenant_id` claim does not match `fan-platform` (and is not `*`) |
| 403 | PERMISSION_DENIED | authorized but not the author / operator |
| 403 | MEMBERSHIP_REQUIRED | gated visibility tier; details.requiredTier ∈ MEMBERS_ONLY/PREMIUM |
| 404 | POST_NOT_FOUND | missing OR cross-tenant; existence not leaked |
| 404 | COMMENT_NOT_FOUND | missing OR cross-tenant |
| 404 | NOT_FOLLOWING | unfollow without prior follow |
| 409 | ALREADY_FOLLOWING | duplicate follow |
| 409 | CONFLICT | optimistic-lock collision |
| 422 | VALIDATION_ERROR | constraint violation (`@Valid`) |
| 422 | POST_STATUS_TRANSITION_INVALID | rejected by `PostStatusMachine` |
| 422 | EDIT_WINDOW_EXPIRED | author edited PUBLISHED past 5min |
| 422 | SELF_FOLLOW_FORBIDDEN | actor tries to follow itself |

---

## Posts

### `POST /api/community/posts` — Publish

Auth: any authenticated actor (FAN role). `ARTIST_POST` requires `ARTIST` role
or `OPERATOR`/`ADMIN`/`SUPER_ADMIN`.

Request:
```json
{
  "postType": "ARTIST_POST | FAN_POST",
  "visibility": "PUBLIC | MEMBERS_ONLY | PREMIUM",
  "title": "string (max 200, optional)",
  "body": "string (1..10000)",
  "mediaRefs": ["s3://...", "..."]
}
```

Response 201:
```json
{
  "data": {
    "postId": "0190f3e2-...",
    "tenantId": "fan-platform",
    "postType": "ARTIST_POST",
    "visibility": "PUBLIC",
    "status": "PUBLISHED",
    "authorAccountId": "uuid",
    "title": "...",
    "body": "...",
    "commentCount": 0,
    "reactionCount": 0,
    "publishedAt": "2026-05-03T00:00:00Z",
    "createdAt": "2026-05-03T00:00:00Z",
    "updatedAt": "2026-05-03T00:00:00Z"
  },
  "meta": { "timestamp": "..." }
}
```

Errors: 401, 403 (PERMISSION_DENIED if non-artist publishes ARTIST_POST), 422 (VALIDATION_ERROR).

### `GET /api/community/posts/{id}` — Get

Auth: bearer. Visibility check: PUBLIC/MEMBERS_ONLY/PREMIUM gating per
`specs/services/community-service/architecture.md` § Visibility Tiers.

Response 200: same shape as Publish response.

Errors: 401, 403 (MEMBERSHIP_REQUIRED for gated posts), 404 (POST_NOT_FOUND).

### `PATCH /api/community/posts/{id}` — Update content

Auth: post author within 5-minute grace window after PUBLISHED, or any operator.

Request:
```json
{
  "title": "string?",
  "body": "string?",
  "mediaRefs": ["..."]
}
```

Response 200: full post payload.

Errors: 401, 403 (PERMISSION_DENIED), 404, 422 (EDIT_WINDOW_EXPIRED), 409.

### `PATCH /api/community/posts/{id}/status` — Status transition

Auth: post author or operator. Allowed transitions per `PostStatusMachine`.

Request:
```json
{ "status": "HIDDEN | DELETED | PUBLISHED", "reason": "string?" }
```

Response: 204.

Errors: 401, 403, 404, 409, 422 (POST_STATUS_TRANSITION_INVALID with details `{ from, to, actor }`).

### `DELETE /api/community/posts/{id}` — Delete (status DELETED shortcut)

Auth: author or operator.

Response: 204.

Errors: 401, 403, 404, 422.

---

## Feed

### `GET /api/community/feed?page=0&size=20`

Auth: bearer. Returns the actor's follow-based feed (artists they follow). Page size capped at 50.

Response 200:
```json
{
  "data": {
    "content": [
      {
        "postId": "...",
        "postType": "ARTIST_POST",
        "visibility": "PUBLIC",
        "authorAccountId": "...",
        "title": "...",
        "bodyPreview": "...first 200 chars...",
        "commentCount": 4,
        "reactionCount": 12,
        "publishedAt": "...",
        "locked": false
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 123,
    "totalPages": 7,
    "hasNext": true
  },
  "meta": { "timestamp": "..." }
}
```

When `locked=true`, `title` and `bodyPreview` are `null` (UI uses this to render a "Subscribe" gate).

---

## Comments

### `POST /api/community/posts/{postId}/comments`

Auth: bearer; post must be PUBLISHED + visibility-accessible.

Request:
```json
{ "body": "string (1..2000)" }
```

Response 201:
```json
{
  "data": {
    "commentId": "...",
    "postId": "...",
    "tenantId": "fan-platform",
    "authorAccountId": "...",
    "body": "...",
    "createdAt": "..."
  },
  "meta": { "timestamp": "..." }
}
```

Errors: 401, 403 (MEMBERSHIP_REQUIRED for gated posts), 404, 422.

### `DELETE /api/community/posts/{postId}/comments/{commentId}`

Auth: comment author or operator.

Response: 204.

Errors: 401, 403 (PERMISSION_DENIED), 404 (COMMENT_NOT_FOUND).

---

## Reactions

### `PUT /api/community/posts/{postId}/reactions`

Auth: bearer; post must be PUBLISHED + visibility-accessible. Idempotent
upsert on `(post_id, reactor_account_id)`.

Request:
```json
{ "reactionType": "LIKE | LOVE | FIRE | SAD" }
```

Response 200:
```json
{
  "data": {
    "postId": "...",
    "reactionType": "LIKE",
    "totalReactions": 42
  },
  "meta": { "timestamp": "..." }
}
```

### `DELETE /api/community/posts/{postId}/reactions`

Auth: bearer. Removes the actor's own reaction (no-op if none exists).

Response: 204.

---

## Follows

### `POST /api/community/follows`

Request:
```json
{ "artistAccountId": "uuid (length ≤ 36)" }
```

Response 201:
```json
{
  "data": {
    "fanAccountId": "...",
    "artistAccountId": "...",
    "tenantId": "fan-platform",
    "followedAt": "..."
  },
  "meta": { "timestamp": "..." }
}
```

Errors: 401, 403, 409 (ALREADY_FOLLOWING), 422 (SELF_FOLLOW_FORBIDDEN).

### `DELETE /api/community/follows/{artistAccountId}`

Response: 204.

Errors: 401, 404 (NOT_FOLLOWING).

---

## Health / metrics

| Path | Auth | Response |
|---|---|---|
| `GET /actuator/health` | none | 200 (composite of DB/Redis/Kafka) |
| `GET /actuator/info` | none | 200 |
| `GET /actuator/prometheus` | none (network-restricted; rate-limit gap — see TASK-FAN-BE-004) | text/plain Prometheus format |

---

## Versioning

This is `v1`. The HTTP path is unversioned — the gateway maps `/api/v1/community/**`
to community-service's `/api/community/**`. Breaking changes will branch the
controller path (`/api/community/v2/...`) AND bump the event topic suffix
(`community.*.v2`).
