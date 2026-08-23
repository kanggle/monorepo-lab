#!/usr/bin/env bash
#
# check-service-type-notation.sh — TASK-MONO-570
#
# Fails when a composite `Service Type` row in a service `architecture.md` is
# written with the types in SEPARATE backtick spans:
#
#   canonical   | Service Type | `rest-api + event-consumer` (…) |
#   violation   | Service Type | `rest-api` + `event-consumer` (…) |
#
# WHY THIS EXISTS
# ---------------
# `platform/service-types/INDEX.md` § Selection Rules 4 fixes the notation. Until
# 2026-08-23 that rule existed only as EXAMPLES in that file — all four composite
# examples used the single-span form, none used the split form — and thirteen of
# the twenty-three composite services had grown the split form anyway, without
# violating anything anyone had written down. Examples do not constrain.
#
# The cost is not cosmetic. This value is read by tooling that extracts one
# identifier per backtick span. With both notations in circulation, whichever
# form a checker assumes, the other group fails ENTIRELY: the /validate-rules
# sweep of 2026-08-23 reported all ten conforming services as Critical, and a
# checker written the other way round would have mis-reported the other thirteen.
# One notation is what makes one extractor correct.
#
# WHAT THIS DOES NOT COVER
# ------------------------
# - Whether the declared types are the RIGHT ones for the service, or whether the
#   leftmost really is the primary. That is a re-classification question and
#   `platform/service-types/INDEX.md` § Selection Rules 2 routes it to an ADR.
# - Single-type rows. They have no `+` and no second span; nothing to drift.
# - The prose in each service's `Service Type Composition` section.
#
set -euo pipefail

SELF_TEST=0
[ "${1:-}" = "--self-test" ] && SELF_TEST=1

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

# Catalog is DERIVED, never hardcoded — a new service type must not need an edit
# here to be recognised.
CATALOG="$(sed -n 's/^| `\([a-z0-9-]*\)`.*/\1/p' platform/service-types/INDEX.md)"
CATALOG_N="$(printf '%s\n' "$CATALOG" | grep -c . || true)"
if [ "$CATALOG_N" -lt 4 ]; then
  echo "check-service-type-notation: FAIL — service-type catalog extracted ${CATALOG_N} entries (< 4)." >&2
  echo "  That is not 'no violations', it is UNMEASURABLE: the catalog parser is broken." >&2
  exit 1
fi

# Population is DERIVED from the tree — a hardcoded file list passes silently
# when a new service is added.
check_tree() {
  local files n=0 violations=0 composite=0
  files="$(git ls-files -- 'projects/*/specs/services/*/architecture.md')"

  while IFS= read -r f; do
    [ -n "$f" ] || continue
    n=$((n + 1))
    local row
    row="$(grep -m1 -E '^\|[[:space:]]*Service Type[[:space:]]*\|' "$f" || true)"
    if [ -z "$row" ]; then
      echo "  VIOLATION  $f" >&2
      echo "             no '| Service Type |' row found (unmeasurable, not clean)" >&2
      violations=$((violations + 1))
      continue
    fi
    # Count backtick spans that hold a catalog value.
    local spans
    spans="$(printf '%s\n' "$row" | grep -oE '`[^`]+`' | tr -d '`' || true)"
    local type_spans=0
    while IFS= read -r s; do
      [ -n "$s" ] || continue
      local first="${s%% *}"
      if printf '%s\n' "$CATALOG" | grep -qx -- "$first"; then
        type_spans=$((type_spans + 1))
      fi
    done <<< "$spans"

    if printf '%s\n' "$row" | grep -q '+'; then composite=$((composite + 1)); fi

    if [ "$type_spans" -gt 1 ]; then
      echo "  VIOLATION  $f" >&2
      echo "             $(printf '%s\n' "$row" | sed 's/^[[:space:]]*//')" >&2
      echo "             composite types must share ONE backtick span:" >&2
      echo "             \`<primary> + <secondary>\`, annotations outside the span." >&2
      echo "             (platform/service-types/INDEX.md § Selection Rules 4)" >&2
      violations=$((violations + 1))
    fi
  done <<< "$files"

  # Population floor. Zero extracted rows is UNMEASURABLE, not clean — a broken
  # glob or a moved directory must go red, not green.
  if [ "$n" -lt 40 ]; then
    echo "check-service-type-notation: FAIL — only ${n} architecture.md found (< 40 floor)." >&2
    echo "  That is not 'no violations', it is UNMEASURABLE: the population query is broken." >&2
    return 1
  fi

  if [ "$violations" -gt 0 ]; then
    echo "check-service-type-notation: FAIL — ${violations} row(s) split a composite Service Type across backtick spans." >&2
    return 1
  fi

  echo "check-service-type-notation: OK — ${n} architecture.md, ${composite} composite, all in one backtick span."
  echo "  (Whether the declared types are correct is NOT checked; re-classification is an ADR"
  echo "   question per platform/service-types/INDEX.md § Selection Rules 2.)"
  return 0
}

# --- self-test -------------------------------------------------------------
# Each case asserts THE INJECTION LANDED before reading the bite. A harness that
# silently injected nothing reports "did not bite" and looks like a dead guard.
run_self_test() {
  local tmp rc fixture
  tmp="$(mktemp -d)"
  # shellcheck disable=SC2064
  trap "rm -rf '$tmp'" RETURN

  probe() {                       # probe <row-content> -> echoes type-span count
    local row="$1" spans first count=0
    spans="$(printf '%s\n' "$row" | grep -oE '`[^`]+`' | tr -d '`' || true)"
    while IFS= read -r s; do
      [ -n "$s" ] || continue
      first="${s%% *}"
      if printf '%s\n' "$CATALOG" | grep -qx -- "$first"; then count=$((count + 1)); fi
    done <<< "$spans"
    printf '%s' "$count"
  }

  # (a) split form must BITE
  fixture='| Service Type | `rest-api` + `event-consumer` (dual) |'
  printf '%s\n' "$fixture" | grep -q '`rest-api` + `event-consumer`' \
    || { echo "  self-test (a): INJECTION FAILED — fixture not as written" >&2; return 1; }
  [ "$(probe "$fixture")" -gt 1 ] \
    || { echo "  self-test (a): predicate did NOT bite the split form" >&2; return 1; }
  echo "  self-test (a) ok — split form bites (type spans = $(probe "$fixture"))"

  # (b) canonical two-part form must PASS
  fixture='| Service Type | `rest-api + event-consumer` (primary rest-api; …) |'
  [ "$(probe "$fixture")" -eq 1 ] \
    || { echo "  self-test (b): canonical form wrongly flagged" >&2; return 1; }
  echo "  self-test (b) ok — canonical form passes"

  # (c) canonical THREE-part form must PASS (scm/demand-planning is real)
  fixture='| Service Type | `event-consumer + batch-job + rest-api` |'
  printf '%s\n' "$fixture" | grep -q 'batch-job + rest-api' \
    || { echo "  self-test (c): INJECTION FAILED" >&2; return 1; }
  [ "$(probe "$fixture")" -eq 1 ] \
    || { echo "  self-test (c): three-part canonical form wrongly flagged" >&2; return 1; }
  echo "  self-test (c) ok — three-part canonical form passes"

  # (d) a parenthetical naming a type in PLAIN TEXT must not count as a span
  fixture='| Service Type | `rest-api + event-consumer` (event-consumer leg = CQRS read model) |'
  [ "$(probe "$fixture")" -eq 1 ] \
    || { echo "  self-test (d): plain-text type name in annotation counted as a span" >&2; return 1; }
  echo "  self-test (d) ok — plain-text annotation does not count"

  # (e) the current tree must be clean
  rc=0; check_tree >/dev/null 2>&1 || rc=$?
  [ "$rc" -eq 0 ] || { echo "  self-test (e): current tree is NOT clean" >&2; return 1; }
  echo "  self-test (e) ok — current tree clean"

  echo "check-service-type-notation --self-test: OK (5/5)"
  return 0
}

if [ "$SELF_TEST" -eq 1 ]; then
  run_self_test
else
  check_tree
fi
