#!/bin/sh
# ============================================================
# MinIO bootstrap for local docker-compose
# ------------------------------------------------------------
# Creates the product-images bucket declared in
# specs/platform/object-storage-policy.md and grants anonymous
# read access (local-only convenience — dev/staging/prod must
# use signed URLs or CloudFront origin access).
# ============================================================
set -eu

MC_ALIAS="local"
ENDPOINT="http://minio:9000"
BUCKET="${PRODUCT_IMAGES_BUCKET:-firstproject-local-product-images}"

echo "[minio-init] configuring mc alias -> ${ENDPOINT}"
mc alias set "${MC_ALIAS}" "${ENDPOINT}" "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}"

echo "[minio-init] ensuring bucket ${BUCKET} exists"
mc mb --ignore-existing "${MC_ALIAS}/${BUCKET}"

echo "[minio-init] granting anonymous download on ${BUCKET} (local only)"
mc anonymous set download "${MC_ALIAS}/${BUCKET}"

# CORS: MinIO allows all origins by default in local; no mc command needed.
# For S3 production, configure CORS via Terraform or AWS console.

echo "[minio-init] done."
