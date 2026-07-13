#!/usr/bin/env bash
#
# check-service-map-drift.sh — TASK-MONO-345
#
# Guards `docs/project-overview.md` against `settings.gradle`, which is the only
# service inventory the build actually reads. A hand-maintained service map that
# silently diverges from the build is invisible to anyone who only reads the doc
# — the failure class already seen in TASK-MONO-339 (fed-e2e bring-up list vs
# compose declarations) and TASK-MONO-341 (demo wrapper map vs docker-compose).
#
# Two directions, both required:
#
#   forward (gradle -> doc)  every `projects:<p>:apps:<s>` include appears in
#                            project <p>'s service-map table. Catches services
#                            that shipped without being documented.
#
#   reverse (doc -> gradle)  every `<name>-service` in a service-map table is a
#                            gradle module, unless the row marks it as not
#                            current. Catches phantom services that only ever
#                            existed in prose.
#
# Deliberate limits (do not "fix" these without reading TASK-MONO-345):
#
#   * Scoped per project section. `notification-service` exists in wms, erp, fan
#     and ecommerce; `membership-service` in iam and fan. A whole-file grep would
#     let every project pass on another project's module.
#   * Reads `settings.gradle` includes, NOT a directory glob. Decommissioned
#     services keep their directory (ecommerce `apps/auth-service/` survives
#     TASK-BE-132) — a glob would resurrect them.
#   * Reverse direction only inspects tokens ending in `-service`. Frontends
#     (`console-web`, `web-store`, `fan-platform-web`) are not gradle modules and
#     are excluded by construction. `batch-worker` / `console-bff` are still
#     checked in the forward direction.
#   * A row is exempt from the reverse check when it is struck through (`~~`) or
#     says RETIRED / FROZEN. The marker means "not current", which may or may not
#     mean "no module" — the two are independent, and the exemption covers both:
#       - RETIRED with no module: iam's `community-service` / `membership-service`
#         (TASK-MONO-394) and `admin-web` (TASK-BE-299). Row kept for the history,
#         module gone. The reverse check must not demand a gradle include.
#       - Marked but still present: no current instance, but the exemption is
#         deliberately written to allow it (a service can be frozen before it is
#         removed), and such a row still satisfies the forward check anyway.
#
# Usage: bash scripts/check-service-map-drift.sh
# Exit:  0 = in sync, 1 = drift (offending names are printed)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SETTINGS="${SETTINGS_GRADLE:-$ROOT/settings.gradle}"
DOC="${OVERVIEW_DOC:-$ROOT/docs/project-overview.md}"

for f in "$SETTINGS" "$DOC"; do
  [ -r "$f" ] || { echo "FATAL: cannot read $f" >&2; exit 2; }
done

# --- gradle side ------------------------------------------------------------
# Strip line comments first: settings.gradle prose mentions module paths.
# `tests:e2e` and `libs:` are not services.
gradle_pairs="$(
  sed 's://.*::' "$SETTINGS" \
    | tr -d '\r' \
    | grep -oE "projects:[a-z0-9-]+:apps:[a-z0-9-]+" \
    | sed 's/^projects://; s/:apps:/ /' \
    | sort -u
)"

[ -n "$gradle_pairs" ] || { echo "FATAL: no 'projects:<p>:apps:<s>' includes found in $SETTINGS" >&2; exit 2; }

# --- doc side ---------------------------------------------------------------
# Emit "<project> <service> LIVE|MARKED" for every backticked *-service token
# inside a service-map table row, and "<project> <token> ROW" for every
# backticked token at all (used by the forward check).
doc_rows="$(
  tr -d '\r' < "$DOC" | awk '
    /^### 2\.[0-9]+ / {
      proj = ""
      if (match($0, /projects\/[a-z0-9-]+\/PROJECT\.md/)) {
        s = substr($0, RSTART, RLENGTH)
        sub(/^projects\//, "", s); sub(/\/PROJECT\.md$/, "", s)
        proj = s
      }
      next
    }
    proj != "" && /^\|/ {
      marked = (index($0, "~~") > 0 || index($0, "RETIRED") > 0 || index($0, "FROZEN") > 0)
      line = $0
      while (match(line, /`[a-z][a-z0-9._-]*`/)) {
        tok = substr(line, RSTART + 1, RLENGTH - 2)
        print proj, tok, "ROW"
        if (tok ~ /-service$/) print proj, tok, (marked ? "MARKED" : "LIVE")
        line = substr(line, RSTART + RLENGTH)
      }
    }
  '
)"

fail=0

# --- forward: gradle -> doc -------------------------------------------------
while read -r proj svc; do
  [ -n "$proj" ] || continue
  if ! printf '%s\n' "$doc_rows" | grep -qx "$proj $svc ROW"; then
    echo "MISSING  $proj:$svc — in settings.gradle, absent from its service-map table in docs/project-overview.md"
    fail=1
  fi
done <<< "$gradle_pairs"

# --- reverse: doc -> gradle -------------------------------------------------
while read -r proj tok kind; do
  [ "${kind:-}" = "LIVE" ] || continue
  if ! printf '%s\n' "$gradle_pairs" | grep -qx "$proj $tok"; then
    echo "PHANTOM  $proj:$tok — in the service-map table of docs/project-overview.md, absent from settings.gradle (mark the row RETIRED/FROZEN or ~~strike~~ it if that is intentional)"
    fail=1
  fi
done <<< "$doc_rows"

if [ "$fail" -ne 0 ]; then
  echo ""
  echo "docs/project-overview.md is out of sync with settings.gradle. See TASK-MONO-345."
  exit 1
fi

echo "OK: every settings.gradle app module is documented, and every documented service exists."
