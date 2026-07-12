#!/usr/bin/env bash
#
# check-adr-index-drift.sh — TASK-MONO-363
#
# Guards `docs/adr/INDEX.md` against the ADR files themselves, which are the only
# authority on what has been decided. The index is the single entry point anyone
# uses to answer "has this already been decided?" — so when it silently falls
# behind, the answer it gives is "no", and a settled decision gets made again.
# That is not hypothetical: the table died at ADR-MONO-012a (2026-05-15) and by
# the time this guard was written it was missing 38 of 53 ADRs, including
# 013 (console), 019 (tenant model), 022 (fulfillment), 043 (notification),
# 047 (org-node) and 048 (gateway library). TASK-MONO-347 sat on exactly that
# kind of misreading for months.
#
# Same failure class as TASK-MONO-339 (fed-e2e bring-up list), TASK-MONO-341
# (demo wrapper map), TASK-MONO-345 (service map) and TASK-MONO-360 (gateway
# declarations): a hand-kept list that nothing compares against the truth.
#
# Three checks:
#
#   forward (file -> row)   every docs/adr/ADR-MONO-*.md has a row. This is the
#                           direction that leaked 38 times.
#
#   reverse (row -> file)   every row points at a file that exists. Catches
#                           phantom rows and renamed/deleted ADRs.
#
#   status  (row == file)   the Status cell equals the ADR's own `**Status:**`.
#                           This is not cosmetic: in this repo PROPOSED -> ACCEPTED
#                           is a human gate (an agent may not self-ACCEPT), so a
#                           row that says ACCEPTED when the file says PROPOSED is
#                           the index forging a decision — and the reverse, which
#                           is what was actually found: ADR-MONO-008 was ACCEPTED
#                           on 2026-05-18 and the table still said PROPOSED.
#
# ---------------------------------------------------------------------------
# WHAT THIS SCRIPT DOES NOT GUARD (known, deliberate — read before "fixing")
#
#   * The Date column. The `**Date:**` header is not a comparable field: 12 ADRs
#     omit it entirely, and of those that have it, some record the PROPOSED date
#     (008, 018, 019) while the rest record the current-status date. Comparing it
#     would fail on day one for reasons that are not drift — and a guard that is
#     red on day one gets switched off, which is worse than no guard at all
#     (TASK-MONO-360). The root cause is that the Authoring Convention never said
#     what Date means, so authors picked both readings. TASK-MONO-369 settles the
#     semantics and then extends this script to cover the Date axis.
#
#   * The Title column. Index titles are curated summaries, not copies of the
#     ADR's H1 (several H1s are full sentences). Comparing them would be a
#     false-positive generator.
#
#   * Project-internal ADRs under `projects/<name>/docs/adr/`. The index's own
#     preamble scopes itself to monorepo-level ADRs; pulling them in would be
#     red on day one.
#
# Usage: bash scripts/check-adr-index-drift.sh
# Exit:  0 = in sync, 1 = drift (offending ADRs are printed), 2 = cannot run

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADR_DIR="${ADR_DIR:-$ROOT/docs/adr}"
INDEX="${ADR_INDEX:-$ADR_DIR/INDEX.md}"

[ -d "$ADR_DIR" ] || { echo "FATAL: cannot read $ADR_DIR" >&2; exit 2; }
[ -r "$INDEX" ]   || { echo "FATAL: cannot read $INDEX" >&2; exit 2; }

# --- file side --------------------------------------------------------------
# "<id> <status>" per ADR file. The id keeps its letter suffix: ADR-MONO-012 and
# ADR-MONO-012a are different decisions, and a parser that drops the suffix would
# let one of them vanish from the index unnoticed.
#
# Status is the first all-caps word after `**Status:**` — ADR-MONO-021's line
# continues into a paragraph of prose ("SUPERSEDED by [ADR-MONO-032] ... "), so
# the whole line is not the value.
file_rows="$(
  for f in "$ADR_DIR"/ADR-MONO-*.md; do
    [ -e "$f" ] || continue
    base="$(basename "$f")"
    id="$(printf '%s' "$base" | grep -oE '^ADR-MONO-[0-9]+[a-z]?')"
    status="$(tr -d '\r' < "$f" | grep -m1 -oE '^\*\*Status:\*\* *[A-Z]+' | sed 's/.*\*\* *//')"
    if [ -z "$status" ]; then
      echo "NO-STATUS  $id — $base has no '**Status:** <VALUE>' header. Every ADR must declare one (docs/adr/INDEX.md § Authoring Convention)." >&2
      echo "$id __MISSING__"
      continue
    fi
    echo "$id $status"
  done | sort -u
)"

# Vacuity: if the glob or the parse silently yields nothing, every forward check
# passes and the guard reports success having verified nothing.
[ -n "$file_rows" ] || { echo "FATAL: no ADR files parsed out of $ADR_DIR — the guard would pass vacuously" >&2; exit 2; }

# --- index side -------------------------------------------------------------
# "<id> <status>" per table row. Rows look like:
#   | [ADR-MONO-013](ADR-MONO-013-....md) | title | ACCEPTED | 2026-05-16 |
index_rows="$(
  tr -d '\r' < "$INDEX" | awk -F'|' '
    /^\| *\[ADR-MONO-/ {
      if (match($2, /ADR-MONO-[0-9]+[a-z]?/)) {
        id = substr($2, RSTART, RLENGTH)
        status = $4
        gsub(/^[ \t]+|[ \t]+$/, "", status)
        print id, status
      }
    }
  ' | sort -u
)"

[ -n "$index_rows" ] || { echo "FATAL: no ADR rows parsed out of $INDEX — the guard would pass vacuously" >&2; exit 2; }

fail=0

# --- forward: file -> row ---------------------------------------------------
while read -r id _; do
  [ -n "$id" ] || continue
  if ! printf '%s\n' "$index_rows" | grep -qE "^$id "; then
    echo "MISSING  $id — the ADR exists in docs/adr/ but has no row in INDEX.md. Anyone searching the index for this decision will conclude it was never made."
    fail=1
  fi
done <<< "$file_rows"

# --- reverse: row -> file ---------------------------------------------------
while read -r id _; do
  [ -n "$id" ] || continue
  if ! printf '%s\n' "$file_rows" | grep -qE "^$id "; then
    echo "PHANTOM  $id — INDEX.md has a row for it, but no docs/adr/$id-*.md exists."
    fail=1
  fi
done <<< "$index_rows"

# --- status: row == file ----------------------------------------------------
while read -r id idx_status; do
  [ -n "$id" ] || continue
  file_status="$(printf '%s\n' "$file_rows" | awk -v i="$id" '$1==i {print $2; exit}')"
  [ -n "$file_status" ] || continue          # already reported as PHANTOM
  [ "$file_status" = "__MISSING__" ] && { fail=1; continue; }
  if [ "$idx_status" != "$file_status" ]; then
    echo "STATUS   $id — INDEX.md says '$idx_status', the ADR says '$file_status'. The file is the authority; fix the row, never the ADR."
    fail=1
  fi
done <<< "$index_rows"

if [ "$fail" -ne 0 ]; then
  echo ""
  echo "docs/adr/INDEX.md is out of sync with docs/adr/. See TASK-MONO-363."
  exit 1
fi

echo "OK: all $(printf '%s\n' "$file_rows" | wc -l | tr -d ' ') monorepo ADRs are indexed, no phantom rows, and every Status matches its ADR."
