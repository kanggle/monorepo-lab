# Object Storage Policy

Defines how services store and serve binary media (images, videos, attachments).

This policy is activated for services flagged with the `content-heavy` trait (see
[`rules/traits/content-heavy.md`](../rules/traits/content-heavy.md) rule C2 —
"미디어는 원본 ↔ 파생본 분리"). Other services storing media may adopt it voluntarily.

---

# Storage Backend

| Environment | Backend | Reason |
|---|---|---|
| `local` | MinIO (single node, docker-compose) | S3-compatible API, runs on dev laptop without cloud credentials |
| `dev` | MinIO (k8s, single replica) | Same image used in CI; identical SDK call paths to production |
| `staging` | AWS S3 | Same backend as production, separate bucket, lifecycle-cleared weekly |
| `production` | AWS S3 | Managed durability (11 nines), CDN integration, IAM-scoped credentials |

All services MUST access the backend through the AWS S3 SDK against an
`endpoint-url`-overridable client. No service may import a MinIO-specific SDK.

A project MAY substitute a different S3-compatible backend (GCS, R2, Blob Storage)
as long as it keeps the "same SDK / endpoint override" discipline.

---

# Bucket Naming

Format: `{project}-{env}-{domain}-{purpose}`

Rules:
- One bucket per `(env, domain, purpose)`. Do not share buckets across services.
- Bucket name is configured per service via env var
  `STORAGE_BUCKETS_<PURPOSE>` (e.g. `STORAGE_BUCKETS_PRODUCT_IMAGES`,
  `STORAGE_BUCKETS_INVOICE_PDFS`). Never hard-code.
- `{project}` is the project short code declared in `PROJECT.md`.

---

# Object Key Layout

Format: `{entity-type}/{entity-id}/{sort-order}-{uuid}.{ext}`

Rules:
- `entity-id` MUST be the canonical UUID of the owning aggregate.
- `sort-order` is a zero-padded integer; the lowest-numbered key is the primary
  asset. Projects that do not need ordering MAY use a fixed `0`.
- The `uuid` segment is a fresh v4 (or v7) generated at upload time — never derived
  from the filename, to prevent overwrite collisions.
- Original client filename is stored as object metadata
  (`x-amz-meta-original-filename`), not in the key.

---

# Upload Flow — Presigned URL

Direct multipart uploads through the gateway are forbidden (memory pressure,
timeout cliffs, rate-limit interactions). All client uploads use presigned PUT
URLs.

Sequence:

1. Client calls `POST /<resource>/{id}/<media>/upload-url` with
   `{ contentType, contentLength }`.
2. Service validates content-type and length against the allow-list, generates a
   presigned PUT URL with TTL ≤ 5 minutes, and returns
   `{ uploadUrl, objectKey, expiresAt }`.
3. Client PUTs the bytes directly to S3/MinIO using `uploadUrl`.
4. Client calls `POST /<resource>/{id}/<media>` with `{ objectKey, sortOrder,
   isPrimary }` to register the upload.
5. Service verifies the object exists and matches the announced size/type via
   HEAD before persisting the metadata row and emitting a domain event
   (e.g. `<Entity>MediaUpdated`).

---

# Allow-list

Each purpose MUST declare its allow-list in the project's spec:

| Field | Example values |
|---|---|
| Allowed content types | `image/jpeg`, `image/png`, `image/webp`, `application/pdf` |
| Max object size | `5 MB`, `50 MB` |
| Max count per entity | `10`, `unlimited` |

Rules:
- The allow-list is enforced both at presigned-URL issuance and at the registration
  step. Server-side validation is mandatory; client-side checks are advisory only.
- Per-purpose allow-lists live in the owning service's spec
  (e.g. `specs/services/<service>/<purpose>-allow-list.md`).

---

# Read Path

- Production buckets MUST sit behind a CDN (CloudFront, Cloudflare, Fastly). The
  CDN URL pattern is `https://cdn.{project}.<tld>/{bucket-name}/{object-key}`.
- Local / dev MinIO is exposed as `http://localhost:9000/{bucket-name}/{object-key}`
  (inside the cluster: `http://minio.infra.svc.cluster.local:9000/...`).
- Services persist only the **object key** in the database, never the resolved
  URL. URL resolution happens at response serialization time using an injected
  `MediaUrlResolver` (or equivalent) so the same DB row works across environments.

---

# Bucket Permissions

- Production buckets: **private** (no public read). All reads go through the CDN
  with origin access identity.
- Dev / staging buckets: **private**, accessed via signed URLs.
- Local MinIO: bucket MAY be created with anonymous read for developer convenience.

Write access:
- Service principals (IAM role or MinIO access key) scoped to a single bucket.
- No cross-bucket write access.

---

# Lifecycle

| Bucket Type | Versioning | Lifecycle Rule |
|---|---|---|
| Production | Enabled | Noncurrent versions deleted after 30 days |
| Staging | Disabled | All objects deleted after 7 days |
| Dev | Disabled | All objects deleted after 1 day |
| Local (MinIO) | Disabled | None — developer responsibility |

Soft-deleted entities (e.g. soft-deleted products) keep their objects until the
lifecycle rule expires noncurrent versions; immediate purge is out of scope by
default. A project MAY add a purge pipeline when regulatory needs require it.

---

# Failure Modes

| Failure | Required Behavior |
|---|---|
| Presigned URL issuance fails (S3 unreachable) | Return `STORAGE_UNAVAILABLE` (503); client retries |
| Client PUTs to expired URL | S3 returns 403; client must request a new URL |
| Registration step finds object missing | Return `MEDIA_NOT_FOUND` (404); do not persist row |
| Registration step finds size/type mismatch | Return `MEDIA_VALIDATION_FAILED` (400); delete the orphan object |
| Bucket misconfigured (404 NoSuchBucket) | Service fails health check; ops alert |

Error codes are registered as platform-common in
[`error-handling.md`](error-handling.md) under the **Content-Heavy Trait** subsection.

---

# Configuration

Each service consuming object storage exposes:

```
storage.s3.endpoint           # http://minio:9000 (local), https://s3.<region>.amazonaws.com (prod)
storage.s3.region             # required by SDK even for MinIO (e.g. us-east-1)
storage.s3.access-key         # IAM role in prod; env var locally
storage.s3.secret-key         # IAM role in prod; env var locally
storage.s3.path-style-access  # true for MinIO, false for S3
storage.cdn.base-url          # CDN origin (prod) or storage origin (dev/local)
storage.buckets.<purpose>     # bucket name per purpose
```

Hard-coded credentials are forbidden — see
[`security-rules.md`](security-rules.md). Credential rotation is the project's
infrastructure concern (SealedSecret, Parameter Store, etc.).

A project integrating object storage SHOULD document its concrete infrastructure
wiring (docker-compose services, k8s manifests, init scripts, round-trip smoke
test) in its `projects/<project>/infra/` or `infra/minio/` directory.

---

# Out of Scope (Future)

- Image transformation pipeline (thumbnail generation, format conversion)
- Video transcoding
- Virus scanning of uploaded objects
- Cross-region replication
- Signed read URLs for time-limited private content (default: CDN handles public read,
  private reads are a project extension)

These will be tracked as separate platform changes when first needed by a project.

---

# Cross-references

- [`rules/traits/content-heavy.md`](../rules/traits/content-heavy.md) — C2 (원본 ↔ 파생본 분리)
  is the rule that activates this policy.
- [`error-handling.md`](error-handling.md) — authoritative registry for
  `STORAGE_UNAVAILABLE`, `MEDIA_NOT_FOUND`, `MEDIA_VALIDATION_FAILED`.
- [`security-rules.md`](security-rules.md) — credential handling.
