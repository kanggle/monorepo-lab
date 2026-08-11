# artist-api (artist-service HTTP contract)

> All endpoints require an `Authorization: Bearer <RS256 JWT>` issued by IAM
> with `tenant_id ∈ { fan-platform, * }`. Tokens with any other tenant value
> get 403 `TENANT_FORBIDDEN`.
>
> All mutating endpoints (POST / PATCH / DELETE) additionally require an
> admin-tier role: `ADMIN`, `OPERATOR`, or `SUPER_ADMIN`. Non-admin callers
> receive 403 `FORBIDDEN`. Read endpoints accept any authenticated tenant
> member.
>
> All requests are routed through the fan-platform gateway. The gateway maps
> external paths to the service-internal paths:
>
> | External path (client-facing) | Internal path (service-level) |
> |---|---|
> | `/api/v1/artists/**` | `/api/artists/**` |
> | `/api/v1/artist-groups/**` | `/api/artist-groups/**` |
> | `/api/v1/fandoms/**` | `/api/fandoms/**` |
>
> The gateway applies a `RewritePath` filter to strip the `/v1/` prefix before
> forwarding (TASK-FAN-BE-005). Path examples below use the **service-internal**
> path (i.e., no `/v1/` prefix). Clients must use the external paths above.
>
> The **internal** endpoint `/internal/artists/exists` is NOT gateway-routed —
> it is reachable only on the internal docker network and is authenticated by an
> IAM `client_credentials` workload-identity JWT (ADR-MONO-005), NOT an end-user
> token. See § Internal artist-account existence check.

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

For paginated list endpoints, `meta` adds `page`, `size`, `totalElements`,
`totalPages`.

### Error (matches `platform/error-handling.md` flat shape)
```json
{
  "code": "ARTIST_NOT_FOUND",
  "message": "Artist not found: 0190f3e2-...",
  "details": { "...": "..." },
  "timestamp": "2026-05-03T00:00:00Z"
}
```

### Common error codes

| HTTP | code | When |
|---|---|---|
| 400 | VALIDATION_ERROR | path-variable type mismatch (e.g. non-UUID `{id}`) |
| 422 | VALIDATION_ERROR | malformed JSON / unknown enum value (request body) |
| 401 | UNAUTHORIZED | missing / expired / invalid signature |
| 403 | TENANT_FORBIDDEN | `tenant_id` claim does not match `fan-platform` (and is not `*`) |
| 403 | FORBIDDEN | authenticated but lacks admin role on a mutating endpoint |
| 404 | ARTIST_NOT_FOUND | missing OR cross-tenant OR DRAFT/ARCHIVED to non-admin (existence not leaked) |
| 404 | ARTIST_GROUP_NOT_FOUND | missing group OR cross-tenant |
| 404 | FANDOM_NOT_FOUND | no fandom for the given artist |
| 409 | STAGE_NAME_CONFLICT | `(tenant_id, stage_name)` collides |
| 409 | GROUP_NAME_CONFLICT | `(tenant_id, group_name)` collides |
| 409 | CONFLICT | optimistic-lock collision |
| 422 | VALIDATION_ERROR | constraint violation (`@Valid`) |
| 422 | STATE_TRANSITION_INVALID | rejected by Artist state machine; `details.from`, `details.to` |
| 422 | ALREADY_MEMBER | adding an already-active member to the group |
| 422 | FANDOM_ALREADY_EXISTS | second fandom for the same artist (1:1 invariant) |
| 422 | ARTIST_NOT_PUBLISHED | fandom create/update against DRAFT/ARCHIVED artist |
| 422 | ARTIST_ARCHIVED | adding an ARCHIVED artist as a new group member |
| 422 | ILLEGAL_STATE | state-machine guard at controller boundary |

---

## Artists

### `POST /api/artists` — Register

Auth: admin-tier role (`ADMIN` / `OPERATOR` / `SUPER_ADMIN`).

Request:
```json
{
  "artistType": "SOLO | GROUP_MEMBER",
  "stageName": "string (1..120, unique per tenant)",
  "realName": "string (max 120, optional)",
  "debutDate": "YYYY-MM-DD (optional)",
  "agency": "string (max 120, optional)",
  "bio": "string (max 4000, optional)",
  "profileImageRef": "string (max 500, optional, e.g. s3://...)"
}
```

Response 201:
```json
{
  "data": {
    "id": "0190f3e2-...",
    "tenantId": "fan-platform",
    "artistType": "SOLO",
    "status": "DRAFT",
    "stageName": "STAR-A",
    "realName": null,
    "debutDate": null,
    "agency": null,
    "bio": null,
    "profileImageRef": null,
    "createdAt": "2026-05-03T00:00:00Z",
    "updatedAt": "2026-05-03T00:00:00Z",
    "publishedAt": null,
    "archivedAt": null
  },
  "meta": { "timestamp": "..." }
}
```

Failures: 401, 403 FORBIDDEN, 409 STAGE_NAME_CONFLICT, 422 VALIDATION_ERROR.

### `GET /api/artists/{id}` — Get one

Auth: any authenticated tenant member.

- PUBLISHED → 200 OK
- DRAFT / ARCHIVED → admin sees 200; non-admin sees 404 ARTIST_NOT_FOUND
- cross-tenant → 404 ARTIST_NOT_FOUND

### `GET /api/artists?q=&type=&page=&size=` — Directory search

Auth: any authenticated tenant member. Returns only PUBLISHED artists.
Read-through Redis cache, TTL 5 min, invalidated on publish/update/archive.

Query params:
- `q` — case-insensitive substring on `stageName` (optional)
- `type` — `SOLO` | `GROUP_MEMBER` (optional)
- `page` — 0-indexed, default 0
- `size` — 1..100, default 20

Response 200:
```json
{
  "data": [
    { "id": "...", "stageName": "...", "artistType": "SOLO", "status": "PUBLISHED", ... }
  ],
  "meta": {
    "timestamp": "...",
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### `PATCH /api/artists/{id}` — Update profile

Auth: admin role. PATCH semantics: every field is optional; `null` means
"do not change".

Request:
```json
{
  "stageName": "string (max 120) | null",
  "realName": "string (max 120) | null",
  "debutDate": "YYYY-MM-DD | null",
  "agency": "string (max 120) | null",
  "bio": "string (max 4000) | null",
  "profileImageRef": "string (max 500) | null"
}
```

Response 200: same shape as the GET response (envelope `{ data, meta }`).
Failures: 401, 403 FORBIDDEN, 404 ARTIST_NOT_FOUND, 409 STAGE_NAME_CONFLICT, 422.

### `PATCH /api/artists/{id}/status` — Status transition

Auth: admin role.

Request:
```json
{
  "status": "PUBLISHED | ARCHIVED",
  "reason": "string (max 200, optional, archive only)"
}
```

Allowed transitions: DRAFT → PUBLISHED, DRAFT → ARCHIVED, PUBLISHED → ARCHIVED.
Sending `status: DRAFT` returns 422.

Response 200:
```json
{
  "data": { "id": "...", "status": "PUBLISHED", "publishedAt": "...", ... },
  "meta": { "timestamp": "..." }
}
```

Failures: 401, 403 FORBIDDEN, 404 ARTIST_NOT_FOUND,
422 STATE_TRANSITION_INVALID (`details.from`, `details.to`).

---

## Artist groups

### `POST /api/artist-groups` — Create group

Auth: admin role.

Request:
```json
{
  "name": "string (1..120, unique per tenant)",
  "debutDate": "YYYY-MM-DD (optional)",
  "agency": "string (max 120, optional)",
  "profileImageRef": "string (max 500, optional)"
}
```

Response 201:
```json
{
  "data": {
    "id": "0190f3e2-...",
    "tenantId": "fan-platform",
    "name": "Group X",
    "debutDate": null,
    "agency": null,
    "profileImageRef": null,
    "status": "ACTIVE",
    "createdAt": "...",
    "updatedAt": "...",
    "members": []
  },
  "meta": { "timestamp": "..." }
}
```

Failures: 401, 403 FORBIDDEN, 409 GROUP_NAME_CONFLICT, 422 VALIDATION_ERROR.

### `GET /api/artist-groups/{id}` — Get group + members

Auth: any authenticated tenant member.

Response 200:
```json
{
  "data": {
    "id": "...",
    "name": "Group X",
    "members": [
      { "artistId": "...", "role": "LEADER | MEMBER | FORMER_MEMBER",
        "joinedAt": "...", "leftAt": null }
    ]
    // ... other group fields
  },
  "meta": { "timestamp": "..." }
}
```

### `POST /api/artist-groups/{id}/members` — Add member

Auth: admin role.

Request:
```json
{
  "artistId": "0190f3e2-...",
  "role": "LEADER | MEMBER"
}
```

Member status policy:

- The member artist may be in `DRAFT` or `PUBLISHED` status. DRAFT lets
  admins pre-stage a group's roster ahead of debut.
- An `ARCHIVED` artist cannot start a new membership: the call returns
  422 `ARTIST_ARCHIVED`.
- A missing or cross-tenant artist returns 404 `ARTIST_NOT_FOUND`.

Response 200: returns the updated group with the new member appended.

Failures: 401, 403 FORBIDDEN, 404 ARTIST_GROUP_NOT_FOUND,
404 ARTIST_NOT_FOUND, 422 ARTIST_ARCHIVED, 422 ALREADY_MEMBER,
422 VALIDATION_ERROR (`role: FORMER_MEMBER` rejected on add).

### `DELETE /api/artist-groups/{id}/members/{artistId}` — Remove member

Auth: admin role.

Sets `left_at = now()` and flips role to `FORMER_MEMBER` (soft remove —
preserves history). Idempotent on already-former members would still 404
because there's no active membership to flip; v1 returns 404 ARTIST_NOT_FOUND
for that case.

Response 204 No Content.

Failures: 401, 403 FORBIDDEN, 404 ARTIST_GROUP_NOT_FOUND, 404 ARTIST_NOT_FOUND.

---

## Fandoms

### `GET /api/fandoms/{artistId}` — Get fandom

Auth: any authenticated tenant member.

Response 200:
```json
{
  "data": {
    "artistId": "...",
    "tenantId": "fan-platform",
    "fandomName": "Hearts",
    "colorHex": "#FFAA00",
    "foundedAt": "2020-01-01",
    "slogan": "Forever",
    "createdAt": "...",
    "updatedAt": "..."
  },
  "meta": { "timestamp": "..." }
}
```

Failures: 401, 404 FANDOM_NOT_FOUND.

### `POST /api/fandoms/{artistId}` — Create fandom

Auth: admin role. Creates the single fandom for the artist. The artist must
already be PUBLISHED — 422 `ARTIST_NOT_PUBLISHED` otherwise. If a fandom
already exists for that artist, 422 `FANDOM_ALREADY_EXISTS` (artist:fandom
is 1:1 — subsequent edits go through PATCH).

Request:
```json
{
  "fandomName": "string (1..120)",
  "colorHex": "#RRGGBB (optional)",
  "foundedAt": "YYYY-MM-DD (optional)",
  "slogan": "string (max 200, optional)"
}
```

Response 201: same shape as GET.

Failures: 401, 403 FORBIDDEN, 404 ARTIST_NOT_FOUND,
422 ARTIST_NOT_PUBLISHED, 422 FANDOM_ALREADY_EXISTS, 422 VALIDATION_ERROR.

### `PATCH /api/fandoms/{artistId}` — Update fandom

Auth: admin role. Updates the existing fandom for the artist. If no fandom
exists, 404 `FANDOM_NOT_FOUND` (use POST first).

Request:
```json
{
  "fandomName": "string (1..120)",
  "colorHex": "#RRGGBB (optional)",
  "foundedAt": "YYYY-MM-DD (optional)",
  "slogan": "string (max 200, optional)"
}
```

Response 200: same shape as GET.

Failures: 401, 403 FORBIDDEN, 404 ARTIST_NOT_FOUND, 404 FANDOM_NOT_FOUND,
422 ARTIST_NOT_PUBLISHED, 422 VALIDATION_ERROR.

---

## Internal artist-account existence check (workload identity — NOT gateway-routed)

`TASK-FAN-BE-045` AC-6 · `ADR-004` (ACCEPTED — A) · `ADR-MONO-059` (ACCEPTED — A).

### `GET /internal/artists/exists?accountId={accountId}&tenantId={tenantId}`

The **remote counterpart** of community-service's port
`ArtistAccountChecker.isArtistAccount(String accountId, String tenantId) → boolean`,
called before `FollowArtistUseCase` persists a `follows` row.

**Why this endpoint exists.** `follows.artist_account_id` lives in
`fanplatform_community` and `artists.account_id` lives in `fanplatform_artist` —
**separate databases**, so the reference cannot be a foreign key, and
`specs/services/community-service/architecture.md` § Forbidden dependencies bars a
DB-level reach-in. `ADR-004` chose the synchronous seam over an event projection
because the projection would change community-service's declared single-type
`rest-api` composition.

Auth: **IAM `client_credentials` workload-identity JWT** (ADR-MONO-005). NOT an
end-user access token. The internal security chain validates issuer + signature +
a recognized internal client identity; an end-user token → 403 `FORBIDDEN`, no
token → 401.

Query parameters (1:1 with the port signature):

| Param | Required | Maps to port param | Meaning |
|---|---|---|---|
| `accountId` | YES | `accountId` | the account claimed to be an artist |
| `tenantId` | YES | `tenantId` | tenant scope |

Response 200:
```json
{ "exists": true }
```

`exists` maps **1:1** to the port's `boolean` return value. `exists=true` iff a row
in `artists` has `account_id = accountId` AND `tenant_id = tenantId`.

**Fail-closed.** Any infrastructure error (DB unavailable, query failure) returns
`{ "exists": false }` — never `true` on error. The calling adapter is ALSO
fail-closed: timeout / non-2xx / malformed body → `false`. An unknown account, an
account belonging to another tenant, and an account that is simply not an artist
all return `{ "exists": false }` (deny), never leaked as a different status.

> 🔴 **Fail-closed here refuses the follow.** That is deliberate and is what
> `ADR-004` § Drivers 3 requires: a validation that opens on error is
> indistinguishable from having no validation. `TASK-FAN-BE-045` AC-6 asserts
> both halves — a bad `accountId` is refused, **and** taking artist-service down
> does not open follow.

**Deliberately NOT in this contract** (stated so the omission is a decision, not a
gap): the artist's `status` (`DRAFT`/`PUBLISHED`/`ARCHIVED`) is **not** exposed and
**not** consulted. AC-6 requires existence, and gating follow on publication state
is a product rule nobody has decided. Adding it later is an additive field on this
response, not a breaking change.

Errors: 401 (no token), 403 (non-workload-identity token), 400 (missing required
param). Note: a domain "does not exist" is NOT an error — it returns 200 with
`exists=false`.

---

## Authentication non-leak

Tokens that fail signature / issuer / `tenant_id` validation produce 401
UNAUTHORIZED with the canonical envelope. A wrong `tenant_id` claim (anything
other than `fan-platform` or `*`) produces 403 TENANT_FORBIDDEN — distinct
from generic auth failure so operators can see cross-tenant probes in their
gateway logs.

Cross-tenant artist IDs and DRAFT/ARCHIVED artists viewed by non-admins
return 404 — never 403 — so the service does not leak the existence of
private records.
