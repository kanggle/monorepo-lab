#!/usr/bin/env bash
#
# check-domain-error-code-registry.sh — TASK-MONO-472
#
# Fails when a `rules/domains/<domain>.md` file lists an error code in its
# `## Standard Error Codes` section that is NOT registered in the platform
# registry `platform/error-handling.md`.
#
# WHY THIS EXISTS
# ---------------
# Every domain rule file's `## Standard Error Codes` section opens with the same
# instruction — "이 도메인의 코드는 platform/error-handling.md 의 전역 카탈로그에
# 등록되어야 한다" — and then lists domain codes. Nothing enforced the "must be
# registered" half. On 2026-07-23 (TASK-MONO-471) a manual `/refactor-spec` audit
# found `rules/domains/erp.md` listing `OPERATION_NOT_PERMITTED`: zero Java
# emitters anywhere, registered in no table, present nowhere else in the repo — a
# phantom code that violated the file's own rule and had drifted from the
# registered `PERMISSION_DENIED` for the same concept.
#
# It survived because the domain error-code lists are a GUARD-LESS surface. The
# neighbouring `check-error-code-registry.sh` guards the OTHER direction — codes
# EMITTED by Java against the registry — and could not have caught this: a
# markdown list is not an emission. This guards the doc side: every code a domain
# file DECLARES must resolve to a registered row. Same hand-kept-correspondence
# failure class as MONO-360 / MONO-363 / MONO-451 / MONO-468.
#
# NOTE ON DUPLICATION (why this is a guard, not a dedup — TASK-MONO-471 decision)
# ------------------------------------------------------------------------------
# The domain files RESTATE a subset of registry codes (with short domain-local
# descriptions) rather than pointing at the registry. `rules/README.md`'s own
# "Index File Rule ⚠️" says that is not itself a defect while the copies have not
# diverged — the fix for un-diverged duplication is a DECLARATION plus a guard,
# not deletion. This is that guard: it lets the domain lists stay (they carry
# per-domain rationale) while making a drift from the registry fail loudly the
# moment it appears, instead of a manual audit months later.
#
# SCOPE (measured, not assumed — TASK-MONO-472)
# ---------------------------------------------
# The first live run flagged 26 codes; classified, that was 6 false positives
# (description-embedded ABAC attributes / roles / event enums) and a genuine
# PRE-EXISTING gap of 20 codes across erp / fan-platform / scm — domain codes
# never registered. Registering those needs an HTTP-status decision per code, so
# those three domains are EXCLUDED here (see UNRECONCILED below) and reconciled in
# TASK-MONO-473. This guard covers the four already-clean domains (wms, ecommerce,
# fintech, saas) day-one green, and each excluded domain joins as it is reconciled.
#
# WHAT IS AND IS NOT CHECKED (the false-positive frontier)
# -------------------------------------------------------
#   checked   the FIRST backticked UPPER_SNAKE token on each `- \`CODE\` — desc`
#             bullet in a domain file's `## Standard Error Codes` section — the
#             code the bullet declares.
#   ignored   OTHER backticked tokens in a bullet's description (an `ACCESS_
#             CONDITION_UNMET` row naming `SOURCE_IP`/`TIME_WINDOW` — those are
#             ABAC attributes, not codes; grabbing every backtick flagged them as
#             phantoms on the first run); anything outside the section; non-code
#             tokens (must be all-caps with an underscore).
#   registered   the SAME extraction `check-error-code-registry.sh` uses: the
#             first cell of a `| CODE | ... |` registry table row. "Registered"
#             means the exact thing in both guards.
#
# `--selftest` pins the extractor (incl. the description-token FP class) and the
# membership check with known positives and known negatives.
#
# USAGE
#   scripts/check-domain-error-code-registry.sh              # guard the repo
#   scripts/check-domain-error-code-registry.sh --selftest   # assert the logic
# Exit: 0 = every declared domain code is registered, 1 = an unregistered code
#       (printed), 2 = cannot run
# ---------------------------------------------------------------------------

set -euo pipefail

cd "$(dirname "$0")/.."

REGISTRY="${ERROR_REGISTRY:-platform/error-handling.md}"

# An error code here is an all-caps token with at least one underscore, ≥4 chars.
CODE_RE='[A-Z][A-Z0-9]*_[A-Z0-9_]+'

# registered_codes <registry-file>
#   The first cell of every `| CODE | ... |` row. Matches check-error-code-registry.sh.
registered_codes() {
  grep -oE '^\| *`?[A-Z][A-Z0-9_]{3,}`? *\|' "$1" 2>/dev/null \
    | tr -d '|` ' | LC_ALL=C sort -u || true
}

# domain_codes <domain-file>
#   The declared code of each bullet in the `## Standard Error Codes` section:
#   the FIRST backticked UPPER_SNAKE token on a `- \`CODE\` — desc` line. Only the
#   first — a description may name OTHER UPPER_SNAKE tokens (ABAC attributes like
#   `SOURCE_IP`/`TIME_WINDOW`, roles like `ORG_ADMIN`, event enums like
#   `CREDENTIALS_INVALID`) that are NOT the code being declared. Grabbing every
#   backtick in the section produced exactly those false positives on the first
#   measurement (TASK-MONO-472). One per line.
domain_codes() {
  local body section
  body="$(tr -d '\r' < "$1")"
  # Slice the Standard Error Codes section. A domain file that pointer-izes
  # (ecommerce) simply yields few/no codes here — not an error.
  section="$(printf '%s\n' "$body" \
    | awk '/^## Standard Error Codes/{f=1; next} /^## /{f=0} f' || true)"
  [ -n "$section" ] || return 0
  printf '%s\n' "$section" \
    | grep -oE "^[[:space:]]*[-*] \`$CODE_RE\`" 2>/dev/null \
    | grep -oE "$CODE_RE" | LC_ALL=C sort -u || true
}

# ---------------------------------------------------------------------------
# --selftest: never trust an empty detector output.
# ---------------------------------------------------------------------------
run_selftest() {
  local st_fail=0 rfx dfx got_reg got_dom
  rfx="$(mktemp)"; dfx="$(mktemp)"
  cat > "$rfx" <<'REG'
| Code | Status | Description |
|---|---|---|
| PERMISSION_DENIED | 403 | registered |
| ASN_NOT_FOUND | 404 | registered |
REG
  cat > "$dfx" <<'DOM'
## Ubiquitous Language
- `NOT_A_CODE_SECTION` — outside Standard Error Codes, must be IGNORED

## Standard Error Codes
이 도메인 코드는 [../../platform/error-handling.md](../../platform/error-handling.md) 에 등록.
### Sub
- `ASN_NOT_FOUND` — registered, OK
- `OPERATION_NOT_PERMITTED` — phantom, must be FLAGGED
- `ACCESS_CONDITION_UNMET` — 조건 미충족 (`SOURCE_IP`·`TIME_WINDOW` AND 결합) — desc tokens must NOT be extracted
- lowercase `not_upper` and single `WORD` are not codes

## Forbidden Patterns
- `ALSO_IGNORED_OUTSIDE` — after the section ends
DOM
  got_reg="$(registered_codes "$rfx" | tr '\n' ' ')"
  got_dom="$(domain_codes "$dfx" | tr '\n' ' ')"
  rm -f "$rfx" "$dfx"

  if [[ "$got_reg" != "ASN_NOT_FOUND PERMISSION_DENIED " ]]; then
    echo "SELFTEST FAIL: registered extraction = '$got_reg' (want 'ASN_NOT_FOUND PERMISSION_DENIED ')"; st_fail=1
  fi
  # domain_codes must include the two in-section codes and NEITHER the
  # out-of-section tokens NOR the non-code words.
  if [[ "$got_dom" != "ACCESS_CONDITION_UNMET ASN_NOT_FOUND OPERATION_NOT_PERMITTED " ]]; then
    echo "SELFTEST FAIL: domain extraction = '$got_dom' (want 'ACCESS_CONDITION_UNMET ASN_NOT_FOUND OPERATION_NOT_PERMITTED ')"; st_fail=1
  fi
  if [[ $st_fail -eq 0 ]]; then
    echo "check-domain-error-code-registry --selftest: OK."
    echo "  registered = table-row first cells; domain = FIRST backticked UPPER_SNAKE"
    echo "  on each bullet in '## Standard Error Codes' only. Asserts description"
    echo "  tokens (SOURCE_IP/TIME_WINDOW), out-of-section, and non-code are IGNORED."
  fi
  return $st_fail
}

if [[ "${1:-}" == "--selftest" ]]; then
  run_selftest
  exit $?
fi

[ -r "$REGISTRY" ] || { echo "FATAL: cannot read $REGISTRY" >&2; exit 2; }

registered="$(registered_codes "$REGISTRY")"
[ -n "$registered" ] || { echo "FATAL: parsed 0 codes out of $REGISTRY — the guard would pass vacuously" >&2; exit 2; }

# Domains whose Standard Error Codes section declares codes NOT YET in the
# registry — a PRE-EXISTING drift measured on 2026-07-23 (TASK-MONO-472),
# reconciled separately in TASK-MONO-473. They are EXCLUDED here, not fixed by
# this guard, because bringing them green means REGISTERING those codes (an HTTP
# status + description per code = a platform-contract decision, not a refactor).
# A guard red on day one gets switched off (testing-strategy.md § G2); the honest
# move is to guard the domains that are already clean and state the gap (§ G8).
# As each domain is reconciled in MONO-473, delete it from this list.
#   erp          1 code : READ_MODEL_SOURCE_UNAVAILABLE
#   fan-platform 7 codes: ARTIST_INACTIVE, FOLLOW_SELF_FORBIDDEN, REACTION_INVALID_EMOJI,
#                         MEMBERSHIP_EXPIRED, MEMBERSHIP_DOWNGRADE_BLOCKED,
#                         POST_REPORTED_PENDING_REVIEW, MODERATION_DECISION_REQUIRED
#   scm          12 codes: SUPPLIER_CONTRACT_EXPIRED, SLA_VIOLATION, CATALOG_SYNC_TIMEOUT,
#                         FORECAST_PERIOD_INVALID, REORDER_POINT_NEGATIVE,
#                         FORECAST_DATA_INSUFFICIENT, SAFETY_STOCK_BELOW_MINIMUM,
#                         CARRIER_TIMEOUT, ROUTE_UNAVAILABLE, ETA_EXPIRED,
#                         INVOICE_AMOUNT_MISMATCH, SETTLEMENT_NOT_READY
UNRECONCILED="${DOMAIN_ERRCODE_EXCLUDE:-erp fan-platform scm}"

mapfile -t domain_files < <(git ls-files 'rules/domains/*.md' | LC_ALL=C sort)
if [[ ${#domain_files[@]} -eq 0 ]]; then
  echo "check-domain-error-code-registry: no rules/domains/*.md found."
  echo "  That is a bug in this script's population query, not an empty repo."
  exit 2
fi

fail=0
findings=0
checked=0
skipped=0

for df in "${domain_files[@]}"; do
  dname="$(basename "$df" .md)"
  if [[ " $UNRECONCILED " == *" $dname "* ]]; then
    skipped=$((skipped + 1))
    continue
  fi
  while IFS= read -r code; do
    [ -n "$code" ] || continue
    checked=$((checked + 1))
    if ! printf '%s\n' "$registered" | grep -qxF "$code"; then
      echo "UNREGISTERED  $df"
      echo "              \`$code\` is listed in § Standard Error Codes but has no"
      echo "              \`| $code | ... |\` row in $REGISTRY."
      echo "              Register it there first (the file's own rule), or fix the"
      echo "              code to the registered name. (Phantom-code drift — MONO-471.)"
      findings=$((findings + 1))
      fail=1
    fi
  done < <(domain_codes "$df")
done

echo
if [[ $fail -eq 0 ]]; then
  echo "check-domain-error-code-registry: OK — $(( ${#domain_files[@]} - skipped )) reconciled domain file(s), ${checked} declared code(s), all registered."
  echo "  ${skipped} domain(s) excluded pending TASK-MONO-473 registration (see UNRECONCILED)."
else
  echo "check-domain-error-code-registry: FAILED — ${findings} unregistered domain code(s)."
  echo "  Register in ${REGISTRY} first, or fix to the registered name."
fi

exit $fail
