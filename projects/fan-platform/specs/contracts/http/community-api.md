# community-api (community-service HTTP contract)

> All endpoints require an `Authorization: Bearer <RS256 JWT>` issued by IAM
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
| 422 | UNKNOWN_ARTIST_ACCOUNT | follow target is not a live `artists.account_id` in this tenant — including when artist-service is unreachable (fail-closed) |

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

### `GET /api/community/posts/mine?page=0&size=20` — My posts (TASK-FAN-FE-016)

Auth: bearer. Returns the caller's own posts, newest first.

Scoped to `author_account_id = <caller>` AND the caller's tenant. Because the caller is
always the author, **no visibility gating applies** — an author can always read their own
post (this mirrors the `actor.owns(...)` short-circuit in `GET /{id}`). DELETED posts are
excluded; HIDDEN and DRAFT are included, since the author is the one person entitled to see
them and hiding them here would make a post look lost.

**Why this endpoint exists**: the feed is follow-based, so a fan's own post never appears in
their own feed, and `GET /{id}` requires already knowing the id. Without a listing there was
no path in the product from "I wrote a post" back to that post.

`mine` is a literal segment on the same template as `GET /{id}`; Spring resolves the literal
ahead of the path variable, and `PostControllerSliceTest` pins that so a future refactor
cannot silently turn it into a lookup for a post whose id is `"mine"`.

Query params: `page` (default 0, clamped ≥0), `size` (default 20, clamped 1..50 — same bounds
as the feed).

Response 200:
```json
{
  "data": {
    "content": [ { "...": "same item shape as the Publish response" } ],
    "page": 0,
    "size": 20,
    "totalElements": 3,
    "totalPages": 1,
    "hasNext": false
  },
  "meta": { "timestamp": "..." }
}
```

Errors: 401.

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

Errors: 401, 403, 409 (ALREADY_FOLLOWING), 422 (SELF_FOLLOW_FORBIDDEN),
422 (UNKNOWN_ARTIST_ACCOUNT).

#### `artistAccountId` is validated against artist-service

`TASK-FAN-BE-045` AC-6 · `ADR-004` (ACCEPTED — A).

`artistAccountId` must be a live `artists.account_id` in the caller's tenant.
community-service confirms it synchronously against artist-service's
`GET /internal/artists/exists` (see `artist-api.md`) before persisting the row.
Anything else — an unknown account, an account in another tenant, an account that
is simply not an artist — is 422 `UNKNOWN_ARTIST_ACCOUNT`.

> 🔴 **Fail-closed, and the outage answer is deliberately indistinguishable.**
> If artist-service cannot be reached the follow is **refused** with the same 422
> `UNKNOWN_ARTIST_ACCOUNT`. Two reasons: a validation that opens on error is
> indistinguishable from having no validation (`ADR-004` § Drivers 3), and a
> distinct "validator down" code would be an oracle for probing which accounts
> exist. `TASK-FAN-BE-045` AC-6 asserts both halves — a bad id is refused, **and**
> taking artist-service down does not open follow.

Until this landed the field was stored verbatim with no existence check, so the
feed join (`posts.author_account_id ⋈ follows.artist_account_id`) held only
because the web app happened to send a value that matched.

### `DELETE /api/community/follows/{artistAccountId}`

Response: 204.

Errors: 401, 404 (NOT_FOLLOWING).

---

## Health / metrics

| Path | Auth | Exposure | Response |
|---|---|---|---|
| `GET /actuator/health` | none | gateway-routed (`/actuator/health`) | 200 (composite of DB/Redis/Kafka) |
| `GET /actuator/info` | none | gateway-routed (`/actuator/info`) | 200 |
| `GET /actuator/prometheus` | none | **internal docker network only — NOT gateway-routed** | text/plain Prometheus format |

`/actuator/prometheus` is intentionally excluded from the gateway route table.
It is scraped by Prometheus directly within the `fan-platform-net` docker network
(`http://community-service:8080/actuator/prometheus`), never exposed through the
external gateway. This is the network-isolation approach (TASK-FAN-BE-004 option c):
a gateway route would create a path-collision between the gateway's own
`/actuator/prometheus` and community-service's endpoint, and would require an
additional auth bypass rule. Network isolation is simpler and equally safe since
the service container binds only to the internal network.

See `projects/fan-platform/docs/operations/prometheus-scrape.md` for Prometheus
job configuration and scrape interval guidance.

---

## Versioning

This is `v1`. The HTTP path is unversioned — the gateway maps `/api/v1/community/**`
to community-service's `/api/community/**`. Breaking changes will branch the
controller path (`/api/community/v2/...`) AND bump the event topic suffix
(`community.*.v2`).
