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
#   passed as the `code` argument of an error-envelope factory, carried by a domain
#   exception's `super("CODE", ...)` call, or returned as a bare literal by an
#   `errorCode()` / `getErrorCode()` override. All three reach the `code` field of an
#   HTTP error response by construction — the override shape is the wms domain-exception
#   standard, and each wms GlobalExceptionHandler writes `e.errorCode()` into the body
#   (TASK-MONO-733: before it was collected, 32 of the 38 codes returned this way were
#   invisible to this check).
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
#   check — including an `errorCode()` override that returns a field instead of a literal
#   (`return errorCode;`, as the iam SignupNotPossible / AccountStatus /
#   NonRetryableDownstream exceptions and wms MasterRefInactiveException do). That is an
#   accepted gap: a guard that never fires wrongly is one people keep, and this catches
#   the shape every drifted code so far actually had.
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
#   errorCode() { return "CODE"; }                       — domain exception overriding the accessor
SRC_DIRS=(projects/*/apps/*/src/main libs/*/src/main)

# The override almost always puts `return` on the next line, so a line-by-line grep sees
# nothing (that is how this shape stayed invisible). Flatten each override-bearing file
# to one line first; the pattern is anchored on the method name, so joining lines cannot
# pair a literal with a different method.
OVERRIDE_RE='(get)?[eE]rrorCode\(\)[[:space:]]*\{[[:space:]]*return[[:space:]]+"[A-Z][A-Z0-9_]{3,}"[[:space:]]*;'
override_files="$(grep -rlE '(get)?[eE]rrorCode\(\)[[:space:]]*\{' --include='*.java' \
    "${SRC_DIRS[@]}" 2>/dev/null || true)"
override_emitted="$(if [[ -n "$override_files" ]]; then
    while read -r f; do tr '\r\n' '  ' < "$f"; echo; done <<<"$override_files" \
      | { grep -oE "$OVERRIDE_RE" || true; } | grep -oE '"[A-Z][A-Z0-9_]{3,}"' | tr -d '"' || true
  fi)"

emitted="$({ grep -rhoE '(ErrorResponse|ApiErrorBody)\.of\("[A-Z][A-Z0-9_]{3,}"|super\("[A-Z][A-Z0-9_]{3,}"' \
    --include='*.java' \
    "${SRC_DIRS[@]}" 2>/dev/null \
  | grep -oE '"[A-Z][A-Z0-9_]{3,}"' | tr -d '"'
  if [[ -n "$override_emitted" ]]; then echo "$override_emitted"; fi
  } | sort -u)"

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
        --include='*.java' "${SRC_DIRS[@]}" 2>/dev/null | head -1 || true)"
    if [[ -z "$site" && -n "$override_files" ]]; then
      # An override-only code: the literal sits on the line after the method name.
      while read -r f; do
        if tr '\r\n' '  ' < "$f" | grep -qE "(get)?[eE]rrorCode\(\)[[:space:]]*\{[[:space:]]*return[[:space:]]+\"$code\"[[:space:]]*;"; then
          site="$f"; break
        fi
      done <<<"$override_files"
    fi
    printf '  %-38s %s\n' "$code" "${site:-?}" >&2
  done <<<"$missing"
  echo >&2
  echo "Register each one in $REGISTRY (and cross-reference it from the matching" >&2
  echo "rules/domains/<domain>.md, per that file's Change protocol) — or, if it is not" >&2
  echo "an HTTP error code at all, do not route it through an error envelope." >&2
  exit 1
fi

echo "OK: every emitted HTTP error code is registered in $REGISTRY"
