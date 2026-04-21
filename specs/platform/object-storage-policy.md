# Object Storage Policy

Defines how services store and serve binary media (images, videos, attachments).

This policy applies to all services flagged with the `content-heavy` trait
(see `specs/rules/traits/content-heavy.md` — "media must be stored in object
storage, not in the relational database").

---

# Storage Backend

| Environment | Backend | Reason |
|---|---|---|
| `local` | MinIO (single node, docker-compose) | S3-compatible API, runs on dev laptop without cloud credentials |
| `dev` | MinIO (k8s, single replica) | Same image used in CI; identical SDK call paths to prod |
| `staging` | AWS S3 | Same backend as production, separate bucket, lifecycle-cleared weekly |
| `production` | AWS S3 | Managed durability (11 nines), CDN integration, IAM-scoped credentials |

All services MUST access the backend through the AWS S3 SDK against an
`endpoint-url`-overridable client. No service may import a MinIO-specific SDK.

---

# Bucket Naming

Format: `{project}-{env}-{domain}-{purpose}`

Examples:
- `firstproject-prod-product-images`
- `firstproject-staging-product-images`
- `firstproject-local-product-images` (MinIO)

Rules:
- One bucket per (env, domain, purpose). Do not share buckets across services.
- Bucket name is configured per service via env var `STORAGE_BUCKETS_<PURPOSE>`
  (e.g. `STORAGE_BUCKETS_PRODUCT_IMAGES`). Never hard-code.

---

# Object Key Layout

Format: `{entity-type}/{entity-id}/{sort-order}-{uuid}.{ext}`

Examples:
- `products/3f2a.../0-9b8c....jpg` (primary image of a product)
- `products/3f2a.../1-7e1d....png`

Rules:
- `entity-id` MUST be the canonical UUID of the owning aggregate.
- `sort-order` is a zero-padded integer; the lowest-numbered key is the primary image.
- The `uuid` segment is a fresh v4 generated at upload time — never derived from
  filename, to prevent overwrite collisions.
- Original client filename is stored as object metadata
  (`x-amz-meta-original-filename`), not in the key.

---

# Upload Flow — Presigned URL

Direct multipart uploads through the gateway are forbidden (memory pressure,
timeout cliffs, rate-limit interactions). All client uploads use presigned PUT
URLs.

Sequence:

1. Client calls `POST /api/admin/products/{id}/images/upload-url` with
   `{ contentType, contentLength }`.
2. Service validates content-type and length against allow-list, generates a
   presigned PUT URL with TTL ≤ 5 minutes, and returns
   `{ uploadUrl, objectKey, expiresAt }`.
3. Client PUTs the bytes directly to S3/MinIO using `uploadUrl`.
4. Client calls `POST /api/admin/products/{id}/images` with `{ objectKey,
   sortOrder, isPrimary }` to register the upload.
5. Service verifies the object exists and matches the announced size/type via
   HEAD before persisting the metadata row and emitting `ProductImagesUpdated`.

---

# Allow-list

| Purpose | Allowed Content Types | Max Size | Max Count Per Entity |
|---|---|---|---|
| `product-images` | `image/jpeg`, `image/png`, `image/webp` | 5 MB | 10 |

The allow-list is enforced both at presigned-URL issuance and at the registration
step. Server-side validation is mandatory; client-side checks are advisory only.

---

# Read Path

- Production buckets MUST sit behind a CDN (CloudFront). The CDN URL pattern is
  `https://cdn.firstproject.io/{bucket-name}/{object-key}`.
- Local/dev MinIO is exposed as `http://localhost:9000/{bucket-name}/{object-key}`
  (inside the cluster: `http://minio.infra.svc.cluster.local:9000/...`).
- Services persist only the **object key** in the database, never the resolved
  URL. URL resolution happens at response serialization time using an injected
  `MediaUrlResolver` so the same DB row works across environments.

---

# Bucket Permissions

- Production buckets: **private** (no public read). All reads go through the CDN
  with origin access identity.
- Dev/staging buckets: **private**, accessed via signed URLs.
- Local MinIO: bucket created with anonymous read for developer convenience.

Write access:
- Service principals (IAM role / MinIO access key) scoped to a single bucket.
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
lifecycle rule expires noncurrent versions; immediate purge is out of scope.

---

# Failure Modes

| Failure | Required Behavior |
|---|---|
| Presigned URL issuance fails (S3 unreachable) | Return `STORAGE_UNAVAILABLE` (503), client retries |
| Client PUTs to expired URL | S3 returns 403; client must request a new URL |
| Registration step finds object missing | Return `MEDIA_NOT_FOUND` (404), do not persist row |
| Registration step finds size/type mismatch | Return `MEDIA_VALIDATION_FAILED` (400), delete the orphan object |
| Bucket misconfigured (404 NoSuchBucket) | Service fails health check; ops alert |

Error codes follow `specs/platform/error-handling.md` — see the
`Object Storage` section.

---

# Configuration

Each service consuming object storage exposes:

```
storage.s3.endpoint           # http://minio:9000 (local), https://s3.<region>.amazonaws.com (prod)
storage.s3.region             # us-east-1 by default; required by SDK even for MinIO
storage.s3.access-key         # IAM role in prod, env var locally
storage.s3.secret-key         # IAM role in prod, env var locally
storage.s3.path-style-access  # true for MinIO, false for S3
storage.cdn.base-url          # CDN origin (prod) or storage origin (dev/local)
storage.buckets.<purpose>     # bucket name per purpose
```

Hard-coded credentials are forbidden (`specs/platform/security-rules.md`).

In docker-compose, the backing MinIO service is defined in `docker-compose.yml`
(`minio`, `minio-init`) and bootstrapped by `infra/minio/init.sh`. In k8s, see
`k8s/base/storage-minio.yaml` (StatefulSet, Service, bucket-init Job) and the
`storage-credentials` SealedSecret in `k8s/base/secrets.yaml`. The shared
STORAGE_* ConfigMap keys live in `k8s/external/infrastructure.yaml`. Round-trip
health is verified via `scripts/verify-object-storage.sh`.

---

# Out of Scope (Future)

- Image transformation pipeline (thumbnail generation, format conversion)
- Video transcoding
- Virus scanning of uploaded objects
- Cross-region replication
- Signed read URLs for time-limited private content (currently all reads go
  through public CDN)

These will be tracked as separate platform changes when first needed.
