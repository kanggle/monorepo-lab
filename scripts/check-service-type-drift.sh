#!/usr/bin/env bash
#
# check-service-type-drift.sh — TASK-MONO-372
#
# Each service declares one cell in specs/services/<svc>/architecture.md:
#
#     | Service Type | `rest-api` (primary) + `event-consumer` ... |
#
# That cell is not documentation. platform/entrypoint.md § "Service-Type-Specific"
# says: "Read EXACTLY ONE file matching the target service's declared Service
# Type." The cell chooses which platform/service-types/*.md rule set is loaded for
# any task touching the service — which MUSTs apply to it at all.
#
# HARDSTOP-10 enforces that the cell is present and names a known type. Nothing
# enforced that it is TRUE. By 2026-07-12 three of the 51 declarations were false:
#
#   ecommerce product-service      declared `rest-api` (single) — runs 6 @KafkaListeners
#                                  across 3 consumer groups. event-consumer.md had
#                                  never been loaded for it; its spec still claimed
#                                  "all three consumers dedupe on event_id" (six).
#   ecommerce notification-service declared `event-consumer` (single) — serves 13
#                                  endpoints across 3 @RestControllers, and the repo
#                                  publishes an HTTP contract for it. One hand-kept
#                                  surface published an API for a service whose other
#                                  hand-kept surface said it had none.
#   wms inbound-service            cell said `rest-api` (primary) and stopped, while its
#                                  own Composition prose honestly named the consumer
#                                  path. Prose is not what the rule-loader reads.
#
# Same failure class as MONO-345 (service map), -352 (error registry), -360 (gateway
# declarations), -363 (ADR index), -371 (JWT claims): a hand-kept declaration that
# nothing compares against the machine truth. The runtime is never wrong — the
# consumers consume, the controllers respond. Only the rule set is wrong, and a rule
# set that is never loaded fails nothing.
#
# Two directions, and ONLY two:
#
#   @KafkaListener present  =>  the cell must contain `event-consumer`
#   @RestController present =>  the cell must contain `rest-api` (or `identity-platform`)
#
# ---------------------------------------------------------------------------
# WHAT THIS SCRIPT DELIBERATELY DOES NOT CHECK
# (measured, not guessed — adding any of these makes the guard red on day one,
#  and a guard that is red on day one gets switched off, which is worse than no
#  guard at all because a skipped job reports green — TASK-MONO-360)
#
#   * @Scheduled => `batch-job`.  MEASURED: 29 services carry @Scheduled (outbox
#     relay, cleanup sweeps) and not one declares batch-job — correctly so. This
#     axis is pure noise: 29 red on day one.
#
#   * The reverse of rest-api: "declares rest-api, therefore must have a
#     @RestController". MEASURED: all 7 gateway-service modules declare `rest-api`
#     and have ZERO controllers — they route with Spring Cloud Gateway. 7 red on
#     day one.
#
#   * The reverse of event-consumer, for the same reason: a declaration is allowed
#     to be aspirational about a path not yet built. Only the code's presence is
#     evidence; its absence is not.
#
#   * Frontends (web-store, console-web, admin-web, fan-platform-web) and any
#     service without src/main — no Java, nothing to compare.
#
# Usage: bash scripts/check-service-type-drift.sh
# Exit:  0 = declarations match the code, 1 = drift, 2 = cannot run

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECTS="${PROJECTS_DIR:-$ROOT/projects}"

[ -d "$PROJECTS" ] || { echo "FATAL: cannot read $PROJECTS" >&2; exit 2; }

# @RestControllerAdvice is NOT a controller. A plain `grep @RestController` matches
# it as a prefix, so a service whose only web class is a global exception handler
# would be reported as a rest-api. Require a non-identifier char after the name.
RC_PATTERN='@RestController([^A-Za-z0-9_]|$)'
KL_PATTERN='@KafkaListener'

checked=0
fail=0

for arch in "$PROJECTS"/*/specs/services/*/architecture.md; do
  [ -e "$arch" ] || continue

  # <projects>/<proj>/specs/services/<svc>/architecture.md
  svc="$(basename "$(dirname "$arch")")"
  proj="$(basename "$(dirname "$(dirname "$(dirname "$(dirname "$arch")")")")")"
  src="$PROJECTS/$proj/apps/$svc/src/main"

  # No Java module (frontends, or a spec written before the module exists).
  [ -d "$src" ] || continue

  cell="$(tr -d '\r' < "$arch" | grep -m1 -E '^\| *Service Type *\|' || true)"
  if [ -z "$cell" ]; then
    echo "NO-TYPE  $proj/$svc — architecture.md has no '| Service Type | ... |' row. HARDSTOP-10 requires one; platform/entrypoint.md loads the service's rule file from it."
    fail=1
    continue
  fi

  checked=$((checked + 1))

  # `grep -l` exits 1 when nothing matches, which is the normal case for most
  # services. Under `set -e` + `pipefail` that would kill the script silently, so
  # each count is taken with an explicit fallback rather than letting the pipeline
  # decide. (It did kill it, once, before this was written.)
  kl="$( { grep -rlE "$KL_PATTERN" "$src" --include=*.java 2>/dev/null || true; } | wc -l | tr -d ' ')"
  rc="$( { grep -rlE "$RC_PATTERN" "$src" --include=*.java 2>/dev/null || true; } | wc -l | tr -d ' ')"

  if [ "$kl" -gt 0 ] && ! printf '%s' "$cell" | grep -q 'event-consumer'; then
    echo "DRIFT    $proj/$svc — $kl class(es) carry @KafkaListener, but the Service Type cell does not name 'event-consumer'."
    echo "         platform/service-types/event-consumer.md is therefore never loaded for this service, so its MUSTs"
    echo "         (idempotency, retry/DLQ, eventVersion branching, declared subscriptions) have never been applied to"
    echo "         those consumers. Add 'event-consumer' to the cell and to the Service Type Composition section."
    fail=1
  fi

  if [ "$rc" -gt 0 ] && ! printf '%s' "$cell" | grep -qE 'rest-api|identity-platform'; then
    echo "DRIFT    $proj/$svc — $rc class(es) carry @RestController, but the Service Type cell does not name 'rest-api'"
    echo "         (or 'identity-platform'). platform/service-types/rest-api.md is therefore never loaded for this"
    echo "         service. Add it to the cell and to the Service Type Composition section."
    fail=1
  fi
done

# Vacuity: a glob that matches nothing, or a tree layout change, would let every
# check above pass having compared nothing at all.
[ "$checked" -gt 0 ] || { echo "FATAL: no architecture.md with a Service Type row was compared against a Java module — the guard would pass vacuously" >&2; exit 2; }

if [ "$fail" -ne 0 ]; then
  echo ""
  echo "A Service Type declaration does not match the code. The code is the authority."
  echo "See TASK-MONO-372 and platform/service-types/INDEX.md."
  exit 1
fi

echo "OK: all $checked Service Type declarations match the annotations in their service's code."
