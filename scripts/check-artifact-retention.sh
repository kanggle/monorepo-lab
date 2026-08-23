#!/usr/bin/env bash
# =============================================================================
# check-artifact-retention.sh — build artifacts expire in 1 day, not 7
# TASK-MONO-566
# =============================================================================
# WHAT THIS GUARDS
#
# This repo has a convention that nobody wrote down: `retention-days: 7` is for
# things a HUMAN opens later (test reports, playwright traces — three of them are
# `if: failure()` and only exist when something broke), and `retention-days: 1` is
# for BUILD OUTPUTS that a downstream job in the same run consumes and nobody ever
# looks at again.
#
# The convention was born on 2026-04-28 (`df67721d7`, #83) when the first sibling
# boot-jars job was added with `1`. Every boot-jars job since has followed it. One
# did not: `wms-boot-jars` was written nine days EARLIER, in this repo's very first
# CI commit (`8da4ddd0f`, 2026-04-19), by the same commit that wrote
# `test-reports: 7` — at a time when no sibling existed to be inconsistent with.
# It carried no task number and no ADR, because there was no decision in it. It sat
# unrevisited for four months holding 23.0 GB, 38% of all live artifact bytes.
#
# The failure mode is silent by construction. A too-long retention never turns
# anything red; it just quietly bills storage. Only a comparison notices.
#
# WHAT THIS DOES *NOT* GUARD
#   * Diagnostic artifacts. Their 7 days is the convention working as intended, and
#     this script deliberately does not look at them.
#   * Whether the artifact is USEFUL, or whether 1 day is long enough for some new
#     consumer. That is a design question; this only enforces the existing rule.
#   * Uploads whose `name:` is a `${{ }}` expression. Those cannot be classified by
#     reading this file — see § UNRESOLVED below, which is why they are COUNTED and
#     PINNED rather than skipped.
#   * Cache entries. Different axis, different expiry rules.
#
# PREDICATE
#   population = every `actions/upload-artifact` block in .github/workflows/*.yml
#   FAIL if a block whose resolved name contains `boot-jar` has retention-days != 1
#          (a MISSING retention-days counts as a violation: the repo default is 90,
#           which is worse than the 7 this task exists to fix)
#
# § POPULATION — derived, never enumerated
#   The artifact names are NOT hardcoded. A hardcoded list is the failure shape this
#   repo keeps re-finding: a new job is added, the list does not grow, and the guard
#   goes green on a tree it never looked at. Deriving from the files also caught a
#   real error while this was being written — TASK-MONO-566's own prose listed six
#   boot-jar uploads. There are eight. (`fan-platform-iam-boot-jar` and
#   `federation-hardening-e2e-boot-jars` were missing from it. Both already `1`, so
#   the conclusion held; the enumeration did not.)
#
# § POPULATION FLOOR
#   Fewer than MIN_UPLOADS extracted blocks is a FAILURE, not a pass. If the parser
#   breaks — a YAML reformat, a different action version, a quoting change — it
#   extracts nothing, finds no violations, and reports success. Zero findings from
#   zero population is not a verdict; it is a broken instrument.
#
# § UNRESOLVED — the share this guard cannot judge, stated out loud
#   Two uploads name their artifact with a `${{ inputs.* }}` expression (reusable
#   workflows called with the name as an argument). A substring test cannot see
#   through that. Today every caller passes a `*-test-reports*` name, so nothing is
#   being missed — but a future caller could route boot jars through one and this
#   guard would stay green.
#
#   "Contains `${{`" is NOT the test — that was the first predicate written here, and
#   it immediately red-flagged a healthy upload: `observability-footprint-${{
#   github.run_id }}` is templated, but its fixed stem names it, and no run id can
#   turn it into a boot-jars artifact. The predicate is whether ANY fixed stem
#   survives once the `${{ … }}` spans are removed. Nothing left ⇒ the caller picks
#   the whole name ⇒ this file genuinely cannot decide. Narrowing the predicate is
#   the fix; loosening the expectation would have shipped the defect.
#   So the unresolved COUNT is pinned. A new templated upload fails this script and
#   forces a human to check what flows through it. That keeps the blind spot from
#   growing silently, which is the only honest thing a guard can do about a case it
#   cannot decide.
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Floor for the extracted population. Measured 20 on 2026-08-23; set well below so
# that removing a job is not a failure, but a dead parser (which yields 0) is.
MIN_UPLOADS=12

# Uploads whose name is a `${{ }}` expression. Measured 2 on 2026-08-23. This is a
# ratchet on a blind spot, not a target — see § UNRESOLVED.
MAX_UNRESOLVED=2

usage() {
    cat <<'EOF'
usage: check-artifact-retention.sh [--self-test] [--workflow-dir DIR]

  (no args)         Check this repository.
  --self-test       Run the predicate against copies of the real workflows, mutated.
                    Asserts the mutation actually landed before reading the verdict.
  --workflow-dir D  Check the workflows in D instead (used by --self-test).
EOF
}

# ---------------------------------------------------------------------------
# extraction
# ---------------------------------------------------------------------------
# Emits one TSV row per upload block: file <TAB> line <TAB> name <TAB> retention
# where retention is the literal `<omitted>` when the key is absent.
#
# A block ends at the next `uses:` or the next list item at the step level. Parsing
# YAML with awk is only defensible because the shape here is narrow and the
# population floor plus the self-test both fail loudly if it stops matching.
extract_uploads() {
    local dir="$1" f
    for f in "$dir"/*.yml; do
        [ -e "$f" ] || continue
        awk -v F="$(basename "$f")" '
            function flush() {
                if (inblk) { printf "%s\t%d\t%s\t%s\n", F, ln, (nm=="" ? "<noname>" : nm), ret; inblk=0 }
            }
            /uses:[[:space:]]*actions\/upload-artifact/ { flush(); inblk=1; nm=""; ret="<omitted>"; ln=NR; next }
            inblk && /^[[:space:]]*name:[[:space:]]/ && nm=="" { sub(/^[[:space:]]*name:[[:space:]]*/,""); sub(/[[:space:]]*$/,""); nm=$0; next }
            inblk && /^[[:space:]]*retention-days:[[:space:]]/ { sub(/^[[:space:]]*retention-days:[[:space:]]*/,""); sub(/[[:space:]]*$/,""); ret=$0; next }
            inblk && /^[[:space:]]*-[[:space:]]*name:[[:space:]]/ && NR>ln+1 { flush(); next }
            END { flush() }
        ' "$f"
    done
}

# ---------------------------------------------------------------------------
# predicate
# ---------------------------------------------------------------------------
run_check() {
    local dir="$1"
    local rows total=0 unresolved=0 violations=0

    rows="$(extract_uploads "$dir")"
    [ -n "$rows" ] && total="$(printf '%s\n' "$rows" | wc -l | tr -d ' ')"

    if [ "$total" -lt "$MIN_UPLOADS" ]; then
        echo "check-artifact-retention: FAILED — extracted only ${total} upload block(s)," >&2
        echo "  below the floor of ${MIN_UPLOADS}. This is a parser failure, not a clean tree:" >&2
        echo "  zero findings drawn from zero population is not a verdict." >&2
        return 1
    fi

    while IFS=$'\t' read -r file line name ret; do
        [ -n "${file:-}" ] || continue
        # Strip `${{ … }}` spans. What remains is the part THIS file fixes.
        local static
        static="$(printf '%s' "$name" | sed -e 's/\${{[^}]*}}//g' -e 's/[-_[:space:]]//g')"
        case "$name" in
            *'${{'*)
                # A name with a fixed identifying stem is named here — `observability-
                # footprint-${{ github.run_id }}` cannot become a boot-jars artifact by
                # substituting a run id. A name that is ENTIRELY an argument is chosen by
                # the caller, and this file cannot see what flows through it.
                if [ -z "$static" ]; then
                    unresolved=$((unresolved + 1))
                    echo "  UNRESOLVED  ${file}:${line}  name=${name}  retention=${ret}"
                    continue
                fi
                ;;
        esac
        case "$name" in
            *boot-jar*)
                if [ "$ret" != "1" ]; then
                    violations=$((violations + 1))
                    if [ "$ret" = "<omitted>" ]; then
                        echo "VIOLATION: ${file}:${line}  ${name} has NO retention-days" >&2
                        echo "           → inherits the repository default (90 days), which is worse" >&2
                        echo "             than the 7 this guard exists to fix. Set it to 1." >&2
                    else
                        echo "VIOLATION: ${file}:${line}  ${name} has retention-days: ${ret}, expected 1" >&2
                        echo "           → build outputs are consumed inside their own run; 7 days is" >&2
                        echo "             the convention for diagnostics a human opens later." >&2
                    fi
                fi
                ;;
        esac
    done <<< "$rows"

    if [ "$unresolved" -gt "$MAX_UNRESOLVED" ]; then
        echo "check-artifact-retention: FAILED — ${unresolved} upload(s) name their artifact with a" >&2
        echo "  \${{ }} expression, above the pinned ${MAX_UNRESOLVED}. This guard cannot classify those" >&2
        echo "  by reading the file. Check what the callers pass: if any routes boot jars through it," >&2
        echo "  that upload needs retention-days: 1. Then raise MAX_UNRESOLVED with a note saying why." >&2
        return 1
    fi

    if [ "$violations" -gt 0 ]; then
        echo "" >&2
        echo "check-artifact-retention: FAILED — ${violations} violation(s) over ${total} upload block(s)." >&2
        return 1
    fi

    echo "check-artifact-retention: OK — ${total} upload blocks, ${unresolved} unresolved (pinned ≤ ${MAX_UNRESOLVED})."
    echo "  Every upload whose name contains 'boot-jar' declares retention-days: 1."
    return 0
}

# ---------------------------------------------------------------------------
# self-test — mutates COPIES of the real workflows, never a hand-built fixture
# ---------------------------------------------------------------------------
# A fixture written by hand only proves the predicate handles inputs its author
# already imagined. These cases start from the tree that actually ships.
self_test() {
    local tmp pass=0 fail=0
    tmp="$(mktemp -d)"
    # shellcheck disable=SC2064
    trap "rm -rf '$tmp'" RETURN

    echo "self-test: predicate against mutated copies of the real workflows"

    _case() { # name expected_rc dir
        local nm="$1" want="$2" dir="$3" got=0
        run_check "$dir" >/dev/null 2>&1 || got=$?
        if [ "$got" = "$want" ]; then
            echo "  ok    ${nm} (rc=${got})"; pass=$((pass + 1))
        else
            echo "  FAIL  ${nm} (rc=${got}, expected ${want})"; fail=$((fail + 1))
        fi
    }

    # Assert an injection landed by matching against a CAPTURED string, never through
    # a pipe. `extract_uploads … | grep -q …` looks correct and is not: grep -q exits
    # on the first match, the upstream dies of SIGPIPE, and `pipefail` reports the
    # pipeline as failed — so a mutation that DID land reads as "injection did not
    # land". That misdirection cost a debugging round here; keep the capture.
    _landed() { # description dir pattern
        local nm="$1" dir="$2" pat="$3" rows
        rows="$(extract_uploads "$dir")"
        case "$rows" in
            *"$pat"*) return 0 ;;
            *) echo "  FAIL  ${nm} — injection did not land (no row matching '${pat}')"
               fail=$((fail + 1)); return 1 ;;
        esac
    }

    # (b) the real tree passes -----------------------------------------------
    local clean="$tmp/clean"
    mkdir -p "$clean" && cp "$ROOT"/.github/workflows/*.yml "$clean/"
    _case "clean tree passes" 0 "$clean"

    # (a) a 7-day boot-jars upload is caught ----------------------------------
    local inj7="$tmp/inj7"
    mkdir -p "$inj7" && cp "$ROOT"/.github/workflows/*.yml "$inj7/"
    perl -0pi -e 's/(name: wms-boot-jars.*?retention-days: )1/${1}7/s' "$inj7/ci.yml"
    # Assert the mutation LANDED. A silent no-op edit reads as "the guard did not
    # bite" when the truth is that nothing was ever injected — the failure this
    # repo has walked into more than once.
    if _landed "7-day boot-jars is caught" "$inj7" "$(printf 'wms-boot-jars\t7')"; then
        _case "7-day boot-jars is caught" 1 "$inj7"
    fi

    # (c) an OMITTED retention-days is caught ---------------------------------
    # 90 by default, worse than the 7 this task fixes.
    local injo="$tmp/injo"
    mkdir -p "$injo" && cp "$ROOT"/.github/workflows/*.yml "$injo/"
    # `\r?` is load-bearing: these files are CRLF in a Windows checkout, so a pattern
    # ending in `\n` matches nothing and the mutation silently no-ops.
    perl -0pi -e 's/(name: wms-boot-jars.*?)^[ \t]*retention-days: 1\r?\n/${1}/ms' "$injo/ci.yml"
    if _landed "omitted retention-days is caught" "$injo" "$(printf 'wms-boot-jars\t<omitted>')"; then
        _case "omitted retention-days is caught" 1 "$injo"
    fi

    # floor: a dead parser must fail, not pass --------------------------------
    local empty="$tmp/empty"
    mkdir -p "$empty" && cp "$ROOT/.github/workflows/ci.yml" "$empty/"
    perl -0pi -e 's/actions\/upload-artifact/actions\/UPLOAD-artifact-RENAMED/g' "$empty/ci.yml"
    _case "population floor fails closed" 1 "$empty"

    # control: a 7-day DIAGNOSTIC upload must NOT be caught --------------------
    # Without this cell, "flag every upload that is not 1" passes every other case
    # and this guard would demand 1 day for reports a human opens after a failure.
    local diag="$tmp/diag"
    mkdir -p "$diag" && cp "$ROOT"/.github/workflows/*.yml "$diag/"
    perl -0pi -e 's/(name: test-reports.*?retention-days: )7/${1}5/s' "$diag/ci.yml"
    if _landed "diagnostic retention is not policed (control)" "$diag" "$(printf 'test-reports\t5')"; then
        _case "diagnostic retention is not policed (control)" 0 "$diag"
    fi

    # control: an unresolved-name upload above the pin fails --------------------
    local unres="$tmp/unres"
    mkdir -p "$unres" && cp "$ROOT"/.github/workflows/*.yml "$unres/"
    perl -0pi -e 's/(name: wms-boot-jars)/name: \$\{\{ inputs.sneaky \}\}/s' "$unres/ci.yml"
    _case "a new templated name breaks the pin" 1 "$unres"

    echo "self-test: ${pass} passed, ${fail} failed"
    [ "$fail" -eq 0 ]
}

# ---------------------------------------------------------------------------
main() {
    local dir="$ROOT/.github/workflows"
    while [ $# -gt 0 ]; do
        case "$1" in
            --self-test) self_test; return $? ;;
            --workflow-dir) dir="$2"; shift 2 ;;
            -h|--help) usage; return 0 ;;
            *) echo "unknown argument: $1" >&2; usage >&2; return 2 ;;
        esac
    done
    run_check "$dir"
}

main "$@"
