#!/usr/bin/env bash
# =============================================================
# Object Storage Round-Trip Verification
# -------------------------------------------------------------
# Confirms that MinIO (or any S3-compatible endpoint) is reachable
# and that the product-images bucket accepts PUT / HEAD / GET / DELETE.
#
# Intended for local docker-compose. Override env vars to target
# a remote endpoint.
# =============================================================
set -euo pipefail

ENDPOINT="${STORAGE_S3_ENDPOINT:-http://localhost:9000}"
ACCESS_KEY="${STORAGE_S3_ACCESS_KEY:-${MINIO_ROOT_USER:-minioadmin}}"
SECRET_KEY="${STORAGE_S3_SECRET_KEY:-${MINIO_ROOT_PASSWORD:-minioadmin}}"
BUCKET="${PRODUCT_IMAGES_BUCKET:-firstproject-local-product-images}"
REGION="${STORAGE_S3_REGION:-us-east-1}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

log_info() { echo -e "  ${YELLOW}[INFO]${NC} $*"; }
log_pass() { echo -e "  ${GREEN}[PASS]${NC} $*"; }
log_fail() { echo -e "  ${RED}[FAIL]${NC} $*"; exit 1; }

command -v aws >/dev/null 2>&1 || log_fail "aws CLI is required. Install via: pip install awscli"

export AWS_ACCESS_KEY_ID="${ACCESS_KEY}"
export AWS_SECRET_ACCESS_KEY="${SECRET_KEY}"
export AWS_DEFAULT_REGION="${REGION}"

S3="aws --endpoint-url=${ENDPOINT} s3api"

log_info "endpoint=${ENDPOINT}  bucket=${BUCKET}  region=${REGION}"

# 1. head-bucket
log_info "step 1/4: head-bucket"
if ! ${S3} head-bucket --bucket "${BUCKET}" >/dev/null 2>&1; then
  log_fail "bucket ${BUCKET} not reachable. Is MinIO running? Did minio-init finish?"
fi
log_pass "bucket reachable"

# 2. put-object
TMP_FILE="$(mktemp)"
trap 'rm -f "${TMP_FILE}"' EXIT
printf 'object-storage-verify %s' "$(date -u +%FT%TZ)" > "${TMP_FILE}"
KEY="verify/roundtrip-$(date +%s)-$$.txt"

log_info "step 2/4: put-object key=${KEY}"
${S3} put-object \
  --bucket "${BUCKET}" \
  --key "${KEY}" \
  --body "${TMP_FILE}" \
  --content-type "text/plain" >/dev/null \
  || log_fail "put-object failed"
log_pass "uploaded"

# 3. head-object + get-object
log_info "step 3/4: head-object + get-object"
${S3} head-object --bucket "${BUCKET}" --key "${KEY}" >/dev/null \
  || log_fail "head-object failed"

DOWNLOADED="$(mktemp)"
trap 'rm -f "${TMP_FILE}" "${DOWNLOADED}"' EXIT
${S3} get-object --bucket "${BUCKET}" --key "${KEY}" "${DOWNLOADED}" >/dev/null \
  || log_fail "get-object failed"
cmp -s "${TMP_FILE}" "${DOWNLOADED}" \
  || log_fail "downloaded content does not match uploaded content"
log_pass "round-trip byte-equal"

# 4. delete-object
log_info "step 4/4: delete-object"
${S3} delete-object --bucket "${BUCKET}" --key "${KEY}" >/dev/null \
  || log_fail "delete-object failed"
log_pass "cleaned up"

echo
echo -e "${GREEN}Object storage verification succeeded.${NC}"
