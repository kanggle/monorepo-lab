# MinIO (local object storage)

Local / dev S3-compatible backend used by services that store binary media.
Policy: [specs/platform/object-storage-policy.md](../../specs/platform/object-storage-policy.md).

## Endpoints

| Purpose | URL |
|---|---|
| S3 API | http://localhost:9000 |
| Web console | http://localhost:9001 |
| Default root user | `minioadmin` (override via `MINIO_ROOT_USER`) |
| Default root password | `minioadmin` (override via `MINIO_ROOT_PASSWORD`) |

## Default bucket

`firstproject-local-product-images` — created automatically by the
`minio-init` oneshot container (`infra/minio/init.sh`). Override with
`PRODUCT_IMAGES_BUCKET` in `.env`.

## Local startup

```sh
docker compose up -d minio minio-init
```

`minio-init` waits for MinIO health, runs the bootstrap script, then exits.
Re-runs are idempotent (`mc mb --ignore-existing`).

## Verifying

```sh
./scripts/verify-object-storage.sh
```

Round-trips a PUT → HEAD → GET → DELETE against the configured bucket.

## Port conflicts

If 9000 / 9001 are in use on the host, edit the `ports:` block of `minio`
in `docker-compose.yml` — **do not** change `MINIO_ROOT_PASSWORD` alone.

## Staging / production

Production does **not** run this container. Instead:

- S3 bucket provisioned via Terraform (separate PR, out of TASK-INT-022 scope)
- Credentials injected as `SealedSecret` named `storage-credentials`
- Reads served through CloudFront; buckets are private

The service code is environment-agnostic: it only sees `STORAGE_S3_ENDPOINT`,
`STORAGE_S3_REGION`, `STORAGE_S3_PATH_STYLE_ACCESS`, and the bucket names.

## Troubleshooting

| Symptom | Check |
|---|---|
| `minio-init` exits with mc alias error | Root credentials env var mismatch between `minio` and `minio-init` services |
| `verify-object-storage.sh` gets 403 | MinIO creds in `.env` differ from running container — restart `minio` after `.env` change |
| `verify-object-storage.sh` gets connection refused | MinIO not healthy yet — `docker compose ps minio` should show `healthy` |
| Bucket not created | Inspect `docker compose logs minio-init`; rerun `docker compose up minio-init` |
