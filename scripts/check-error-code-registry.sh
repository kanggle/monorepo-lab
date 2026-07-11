#!/usr/bin/env bash
#
# Fails if a service can emit an HTTP error code that platform/error-handling.md
# does not register.
#
# The registry declares its own change protocol ("New domain-specific error codes ->
# add to this file"; "This document is the single authoritative registry"), but nothing
# enforced it — 38 live codes had drifted out of it by TASK-MONO-352. This is the
# enforcement.
#
# SOUND, NOT COMPLETE — on purpose.
#   It only collects codes that are *unambiguously* HTTP error codes: a string literal
#   passed as the `code` argument of an error-envelope factory, or carried by a domain
#   exception's `super("CODE", ...)` call. Both reach the `code` field of an HTTP error
#   response by construction.
#
#   It deliberately does NOT try to collect every SCREAMING_SNAKE literal. Doing so
#   produces false positives that are worse than a miss, because they pressure a
#   maintainer into registering things that are not HTTP error codes at all:
#     * event enums          — auth `CREDENTIALS_INVALID` is a LoginFailed failureReason;
#                              the HTTP code for that path is INVALID_CREDENTIALS
#     * bulk per-item codes  — `EMAIL_DUPLICATE` / `ALREADY_LOCKED` appear inside a 2xx
#                              bulk response body, never in an error envelope
#     * audit reasons        — ecommerce login passes `ACCOUNT_DEACTIVATED` as an audit /
#                              event reason and then throws InvalidCredentialsException,
#                              because the API must NOT reveal that the account is
#                              deactivated. Registering it would document a code the API
#                              deliberately never returns.
#     * plain enums          — `WEIGHTED_AVERAGE` is an FX costing method
#
#   A code carried in a field rather than a `super(...)` call can still slip past this
#   check. That is an accepted gap: a guard that never fires wrongly is one people keep,
#   and this catches the shape every drifted code so far actually had.
#
# Usage: scripts/check-error-code-registry.sh [--list]
set -euo pipefail

cd "$(dirname "$0")/.."

REGISTRY="platform/error-handling.md"
[[ -f "$REGISTRY" ]] || { echo "error: $REGISTRY not found" >&2; exit 2; }

# Codes registered in the registry's markdown tables: `| CODE | 4xx | ... |`
# (the code cell is optionally backticked).
registered="$(grep -oE '^\| *`?[A-Z][A-Z0-9_]{3,}`? *\|' "$REGISTRY" \
  | tr -d '|` ' | sort -u)"

# Codes a service can put in an error envelope.
#   ErrorResponse.of("CODE"  /  ApiErrorBody.of("CODE"   — handler-synthesised
#   super("CODE",                                        — domain exception carrying a code
emitted="$(grep -rhoE '(ErrorResponse|ApiErrorBody)\.of\("[A-Z][A-Z0-9_]{3,}"|super\("[A-Z][A-Z0-9_]{3,}"' \
    --include='*.java' \
    projects/*/apps/*/src/main libs/*/src/main 2>/dev/null \
  | grep -oE '"[A-Z][A-Z0-9_]{3,}"' | tr -d '"' | sort -u)"

if [[ "${1:-}" == "--list" ]]; then
  echo "registered: $(wc -l <<<"$registered")"
  echo "emitted:    $(wc -l <<<"$emitted")"
fi

missing="$(comm -23 <(echo "$emitted") <(echo "$registered") || true)"

if [[ -n "$missing" ]]; then
  echo "ERROR: these HTTP error codes are emitted but not registered in $REGISTRY:" >&2
  echo >&2
  while read -r code; do
    [[ -z "$code" ]] && continue
    site="$(grep -rlE "(ErrorResponse|ApiErrorBody)\.of\(\"$code\"|super\(\"$code\"" \
        --include='*.java' projects/*/apps/*/src/main libs/*/src/main 2>/dev/null | head -1)"
    printf '  %-38s %s\n' "$code" "${site:-?}" >&2
  done <<<"$missing"
  echo >&2
  echo "Register each one in $REGISTRY (and cross-reference it from the matching" >&2
  echo "rules/domains/<domain>.md, per that file's Change protocol) — or, if it is not" >&2
  echo "an HTTP error code at all, do not route it through an error envelope." >&2
  exit 1
fi

echo "OK: every emitted HTTP error code is registered in $REGISTRY"
