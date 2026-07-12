#!/usr/bin/env bash
#
# check-jwt-claims-registry.sh — TASK-MONO-371
#
# platform/contracts/jwt-standard-claims.md declares itself the authority on what
# every access token carries: "All access tokens issued by the identity-platform
# service MUST include the following claims". Nothing checked that against the
# service that actually mints them — and by 2026-07-12 the document was missing
# FOUR of them, including `tenant_id`, the claim every gateway in the fleet
# rejects tokens for lacking.
#
# The runtime was never wrong. Tokens carried the claims; gateways enforced them.
# Only the document was wrong — and a document does not fail. The cost lands on
# whoever writes the next service from this contract and ships it with no tenant
# isolation, believing the contract was complete.
#
# Same failure class as TASK-MONO-345 (service map), -352 (error registry),
# -360 (gateway declarations) and -363 (ADR index): a hand-kept declaration that
# nothing compares against the machine truth.
#
# FORWARD ONLY: every claim the identity-platform mints must have a row in the
# Standard Claims table.
#
# ---------------------------------------------------------------------------
# WHAT THIS SCRIPT DOES NOT GUARD (deliberate — read before "fixing")
#
#   * The reverse direction (table -> code). `iss`, `iat`, `exp`, `jti`, `aud`
#     and `kid` are emitted by Spring Authorization Server as framework defaults
#     — there is no `.claim(...)` call to find. `sub` comes from the principal.
#     A reverse check would be red on day one for six rows that are perfectly
#     correct, and a guard that is red on day one gets switched off, which is
#     worse than no guard at all: a skipped job reports green (TASK-MONO-360).
#
#   * Whether a claim's Required / Type / Description cells are ACCURATE. Those
#     are prose. This guards presence, which is what was actually lost.
#
#   * Claims minted by other services. Only the identity-platform issues access
#     tokens; that is the scope the contract's own sentence claims.
#
# Usage: bash scripts/check-jwt-claims-registry.sh
# Exit:  0 = in sync, 1 = a minted claim is unregistered, 2 = cannot run

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IDP_SRC="${IDP_SRC:-$ROOT/projects/iam-platform/apps/auth-service/src/main}"
CONTRACT="${JWT_CONTRACT:-$ROOT/platform/contracts/jwt-standard-claims.md}"

[ -d "$IDP_SRC" ]   || { echo "FATAL: cannot read $IDP_SRC" >&2; exit 2; }
[ -r "$CONTRACT" ]  || { echo "FATAL: cannot read $CONTRACT" >&2; exit 2; }

# --- code side: what the identity-platform actually mints --------------------
#
# Two shapes, and the second one is the whole difficulty:
#
#   .claim("tenant_id", ...)          <- string literal
#   .claim(CLAIM_ORG_SCOPE, ...)      <- constant reference
#
# `entitled_domains` and `org_scope` are minted ONLY through constants. A parser
# that reads literals alone finds two of the four and reports success — a guard
# that silently checks half of what it claims to. So constants are resolved
# through their `private static final String X = "value";` declaration.
minted="$(
  {
    # literal form
    grep -rhoE '\.claim\("[a-z_]+"' "$IDP_SRC" --include=*.java \
      | sed -E 's/^\.claim\("//; s/"$//'

    # constant form: collect the identifiers, then resolve each to its value
    for const in $(grep -rhoE '\.claim\([A-Z][A-Z0-9_]*' "$IDP_SRC" --include=*.java \
                     | sed -E 's/^\.claim\(//' | sort -u); do
      val="$(grep -rhoE "String +$const *= *\"[a-z_]+\"" "$IDP_SRC" --include=*.java \
               | head -1 | sed -E 's/.*"([a-z_]+)".*/\1/')"
      if [ -z "$val" ]; then
        echo "UNRESOLVED-CONSTANT  $const — minted via .claim($const, ...) but its String declaration was not found in $IDP_SRC. The guard cannot tell which claim this is, so it cannot tell whether the contract carries it. Declare it as 'private static final String $const = \"...\";' beside its use." >&2
        echo "__UNRESOLVED__"
      else
        echo "$val"
      fi
    done
  } | sort -u
)"

# Vacuity: if the parse yields nothing, every check below passes and the guard
# reports success having verified nothing.
[ -n "$minted" ] || { echo "FATAL: no .claim(...) call parsed out of $IDP_SRC — the guard would pass vacuously" >&2; exit 2; }

# --- doc side: what the Standard Claims table registers ----------------------
# Rows look like:  | `tenant_id` | string | Yes | ... | ... |
# The struck-through `account_id` row (~~`account_id`~~) is a REMOVED marker, not
# a registration, and must not count as one.
registered="$(
  tr -d '\r' < "$CONTRACT" \
    | grep -E '^\| *`[a-z_]+` *\|' \
    | sed -E 's/^\| *`([a-z_]+)` *\|.*/\1/' \
    | sort -u
)"

[ -n "$registered" ] || { echo "FATAL: no claim rows parsed out of $CONTRACT — the guard would pass vacuously" >&2; exit 2; }

fail=0

while read -r claim; do
  [ -n "$claim" ] || continue
  if [ "$claim" = "__UNRESOLVED__" ]; then fail=1; continue; fi   # already reported
  if ! printf '%s\n' "$registered" | grep -qx "$claim"; then
    echo "UNREGISTERED  $claim — the identity-platform mints this claim, but platform/contracts/jwt-standard-claims.md has no row for it."
    echo "              That contract says every access token MUST include the claims it lists, so anyone building a resource server"
    echo "              from it will not know this claim exists — and if it is an isolation axis, they will ship without isolation."
    echo "              Add a row to § Standard Claims. The code is the authority; document it, do not delete the claim."
    fail=1
  fi
done <<< "$minted"

if [ "$fail" -ne 0 ]; then
  echo ""
  echo "platform/contracts/jwt-standard-claims.md is out of sync with the identity-platform. See TASK-MONO-371."
  exit 1
fi

echo "OK: all $(printf '%s\n' "$minted" | wc -l | tr -d ' ') claims the identity-platform mints are registered in the JWT contract."
