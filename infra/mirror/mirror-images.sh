#!/usr/bin/env bash
#
# Copy each image in infra/mirror/images.txt to GHCR, keeping its digest, and fail if
# the copy's digest differs from the source digest (TASK-MONO-734).
#
# Why: the ecommerce MinIO images now come from docker.io/bitnamilegacy, which is
# "no longer updated" and can disappear; every upstream MinIO distribution point is
# already closed (Docker Hub minio/* - BE-591, quay.io/minio - BE-598, dl.min.io 410).
# Once the source is gone, the GHCR copy may be the only one left, so it has to be
# byte-identical: a copy that re-wrote a multi-arch index into a single manifest would
# still "pull", but it would not be the image compose pinned.
#
# Usage:
#   bash infra/mirror/mirror-images.sh                   # copy + verify (needs crane, logged in)
#   bash infra/mirror/mirror-images.sh --visibility      # report anonymous pull per destination
#   bash infra/mirror/mirror-images.sh --require-public  # same, but fail if any is not public
#   bash infra/mirror/mirror-images.sh --self-test       # prove the digest check bites (no network)
set -euo pipefail

SELF="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
cd "$(dirname "$0")/../.."
LIST="${MIRROR_LIST:-infra/mirror/images.txt}"
CRANE="${CRANE:-crane}"

entries() { grep -vE '^[[:space:]]*(#|$)' "$LIST"; }

copy_and_verify() {
  local src dst want got bad=0
  while read -r src dst; do
    want="${src##*@}"
    case "$want" in
      sha256:*) ;;
      *) echo "✗ $src — source must be pinned by @sha256 digest"; bad=1; continue ;;
    esac
    echo "== $src -> $dst"
    if ! "$CRANE" copy "$src" "$dst"; then
      echo "✗ copy failed: $src"; bad=1; continue
    fi
    got="$("$CRANE" digest "$dst")"
    if [[ "$got" == "$want" ]]; then
      echo "✓ digest preserved: $got"
    else
      echo "✗ digest changed: source $want, copy $got"; bad=1
    fi
  done < <(entries)
  return "$bad"
}

# Anonymous pull check: ask GHCR for an anonymous pull token and HEAD the manifest.
# A package is private by default on first publish; making it public is a web-UI-only step.
visibility() {
  local require="$1" dst repo tag token code bad=0
  while read -r _ dst; do
    repo="${dst#ghcr.io/}"; tag="${repo##*:}"; repo="${repo%:*}"
    token="$(curl -fsS "https://ghcr.io/token?scope=repository:${repo}:pull" 2>/dev/null \
      | sed -nE 's/.*"token":"([^"]+)".*/\1/p' || true)"
    code="$(curl -s -o /dev/null -w '%{http_code}' -I \
      -H "Authorization: Bearer ${token:-none}" \
      -H 'Accept: application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json' \
      "https://ghcr.io/v2/${repo}/manifests/${tag}")"
    if [[ "$code" == "200" ]]; then
      echo "✓ public (anonymous manifest HEAD 200): $dst"
    else
      echo "· not anonymously pullable (HTTP $code): $dst"; bad=1
    fi
  done < <(entries)
  if [[ "$require" == "1" && "$bad" -ne 0 ]]; then
    echo "✗ at least one destination is not public — switch it in the package settings first"
    return 1
  fi
  return 0
}

self_test() {
  local dir rc
  dir="$(mktemp -d)"
  trap 'rm -rf "$dir"' RETURN
  # A fake crane: `copy` succeeds, `digest` prints whatever FAKE_DIGEST says.
  printf '%s\n' '#!/usr/bin/env bash' \
    'case "$1" in copy) exit 0 ;; digest) echo "$FAKE_DIGEST" ;; *) exit 2 ;; esac' > "$dir/crane"
  chmod +x "$dir/crane"
  local d="sha256:$(printf 'a%.0s' {1..64})" other="sha256:$(printf 'b%.0s' {1..64})"
  printf 'docker.io/x/y:1@%s ghcr.io/z/y:1\n' "$d" > "$dir/list"

  set +e
  MIRROR_LIST="$dir/list" CRANE="$dir/crane" FAKE_DIGEST="$d" bash "$SELF" > "$dir/ok.log" 2>&1; rc=$?
  set -e
  [[ $rc -eq 0 ]] || { echo "self-test FAIL: matching digest should pass (rc=$rc)"; cat "$dir/ok.log"; return 1; }

  set +e
  MIRROR_LIST="$dir/list" CRANE="$dir/crane" FAKE_DIGEST="$other" bash "$SELF" > "$dir/bad.log" 2>&1; rc=$?
  set -e
  [[ $rc -ne 0 ]] || { echo "self-test FAIL: changed digest should fail"; cat "$dir/bad.log"; return 1; }
  grep -q 'digest changed' "$dir/bad.log" || { echo "self-test FAIL: wrong failure reason"; cat "$dir/bad.log"; return 1; }

  printf 'docker.io/x/y:1 ghcr.io/z/y:1\n' > "$dir/list"
  set +e
  MIRROR_LIST="$dir/list" CRANE="$dir/crane" FAKE_DIGEST="$d" bash "$SELF" > "$dir/unpinned.log" 2>&1; rc=$?
  set -e
  [[ $rc -ne 0 ]] || { echo "self-test FAIL: an unpinned source should fail"; return 1; }

  echo "self-test OK: match → rc=0 · changed digest → rc≠0 ('digest changed') · unpinned source → rc≠0"
}

case "${1:-}" in
  --self-test)      self_test ;;
  --visibility)     visibility 0 ;;
  --require-public) visibility 1 ;;
  "")               copy_and_verify ;;
  *) echo "usage: $0 [--self-test|--visibility|--require-public]" >&2; exit 2 ;;
esac
