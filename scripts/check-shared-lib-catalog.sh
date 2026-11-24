#!/usr/bin/env bash
# =============================================================================
# check-shared-lib-catalog.sh — every repo-root shared library is in the catalog,
# and every catalog row is a library the build actually includes
# TASK-MONO-832
# =============================================================================
# WHAT THIS GUARDS
#
# `platform/shared-library-policy.md` § Change Rule says the catalog entry list
# "must be documented in this file BEFORE any `libs/` touch". Nothing enforced it.
# Measured 2026-11-24 UTC: settings.gradle included 12 repo-root `libs:*` modules
# and the catalog table listed 8. Four modules had arrived over four months
# (`java-security-servlet`, `payment-core`, `payment-portone`, `payment-toss`),
# each through a reviewed PR, and none of them added its row. The same day a PR
# edited that very table twice — removing a retired module — and did not notice
# the four that were missing. A list that nothing can fail on drifts; this is the
# thing that can fail.
#
# The reverse direction matters as much: a row for a module that is no longer
# built tells readers a standard exists that does not (the retired module's row
# said "notification producers" for months while it had zero consumers).
#
# WHAT THIS DOES *NOT* GUARD
#   * Whether a row's Contents / Consumers text is TRUE. Consumers is an
#     operational fact that changes with every service; checking it would need a
#     dependency graph, not a table. Only membership is checked.
#   * Project-scoped shared modules (`projects:<p>:libs:<m>`). They are project
#     content by design (CLAUDE.md § Project-scoped shared modules) and do not
#     belong in the repo-root catalog — they are excluded, and the self-test
#     asserts the exclusion.
#   * A directory under `libs/` that settings.gradle does not include. It is not
#     part of the build, which is a different defect.
#
# PREDICATE
#   A = repo-root modules settings.gradle includes: the quoted path is exactly
#       'libs:<name>' (a quote immediately before `libs`, so a project-scoped
#       'projects:<p>:libs:<m>' never matches). `//` comments stripped first.
#   B = table rows of the policy file's `# Catalog` section only, shaped
#       | `libs/<name>` | … — prose elsewhere in the file mentions `libs/<x>`
#       constantly, and counting a mention as an entry would let a deleted row
#       pass.
#   FAIL if A \ B or B \ A is non-empty. FAIL CLOSED if A or B is empty.
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POLICY_REL="platform/shared-library-policy.md"

usage() {
    cat <<'EOF'
usage: check-shared-lib-catalog.sh [--self-test]

  (no args)    Check this repository.
  --self-test  Run the predicate against mutated copies of the real inputs.
               Verifies that it bites in both directions, passes when it should,
               ignores project-scoped modules and prose, and fails closed.
EOF
}

# ---------------------------------------------------------------------------
# populations — each prints one name per line, sorted, possibly empty.
# `|| true`: under pipefail a grep that matches nothing returns 1 and errexit
# would abort silently; "nothing" must be EMPTY OUTPUT so run_check's explicit
# fail-closed branch can name it (the check-libs-ci-coverage.sh lesson).
# ---------------------------------------------------------------------------
included_root_libs() {
    local settings="$1"
    # CR stripped first: the dev host checks text out CRLF, CI checks it out LF.
    { tr -d '\r' < "$settings" \
        | sed -e 's://.*::' \
        | grep -oE "'libs:[a-z0-9-]+'" \
        | sed -e "s/^'libs://" -e "s/'\$//" \
        | sort -u; } || true
}

catalog_rows() {
    local policy="$1"
    # Only the lines between the `# Catalog` heading and the next top-level
    # heading, and only table rows whose FIRST cell is a backticked libs/ path.
    # CR stripped first, or `$0 == "# Catalog"` never holds on a CRLF checkout.
    { tr -d '\r' < "$policy" \
        | awk '
            /^# / { in_cat = ($0 == "# Catalog"); next }
            in_cat { print }
          ' \
        | grep -oE '^\| `libs/[a-z0-9-]+` \|' \
        | sed -e 's/^| `libs\///' -e 's/` |$//' \
        | sort -u; } || true
}

# Lines of $1 that are not lines of $2 (pure bash — `comm` with process
# substitution has killed the shell under msys before).
minus() {
    local a="$1" b="$2" out="" x nl=$'\n'
    while IFS= read -r x; do
        if [[ -z "$x" ]]; then continue; fi
        case "${nl}${b}${nl}" in
            *"${nl}${x}${nl}"*) ;;
            *) out="${out}${x}${nl}" ;;
        esac
    done <<< "$a"
    printf '%s' "${out%"$nl"}"
}

# ---------------------------------------------------------------------------
# the check
# ---------------------------------------------------------------------------
run_check() {
    local root="$1" quiet="${2:-}"
    local settings="$root/settings.gradle"
    local policy="$root/$POLICY_REL"

    if [[ ! -f "$settings" ]]; then
        echo "FAIL: settings.gradle not found at $settings" >&2
        return 1
    fi
    if [[ ! -f "$policy" ]]; then
        echo "FAIL: $POLICY_REL not found at $policy" >&2
        return 1
    fi

    local included catalog
    included="$(included_root_libs "$settings")"
    catalog="$(catalog_rows "$policy")"

    if [[ -z "$included" ]]; then
        echo "FAIL: parsed ZERO repo-root libs modules from settings.gradle." >&2
        echo "      The comparison would pass having verified nothing." >&2
        return 1
    fi
    if [[ -z "$catalog" ]]; then
        echo "FAIL: parsed ZERO catalog rows from $POLICY_REL § Catalog." >&2
        echo "      Either the heading or the row shape (| \`libs/<name>\` |) changed;" >&2
        echo "      fix the parser together with the file, not by deleting the check." >&2
        return 1
    fi

    local not_listed stale m rc=0
    not_listed="$(minus "$included" "$catalog")"
    stale="$(minus "$catalog" "$included")"

    if [[ -n "$not_listed" ]]; then
        echo "FAIL: shared-library module(s) built but missing from the catalog:" >&2
        while IFS= read -r m; do echo "  libs/$m" >&2; done <<< "$not_listed"
        echo "  Add a row to $POLICY_REL § Catalog (Module | Contents | Consumers)." >&2
        echo "  § Change Rule: the catalog is updated BEFORE the libs/ change lands." >&2
        rc=1
    fi
    if [[ -n "$stale" ]]; then
        if [[ "$rc" == 1 ]]; then echo >&2; fi
        echo "FAIL: catalog row(s) for module(s) settings.gradle does not include:" >&2
        while IFS= read -r m; do echo "  libs/$m" >&2; done <<< "$stale"
        echo "  Remove the row, or — if the module should exist — include it in settings.gradle." >&2
        rc=1
    fi
    if [[ "$rc" != 0 ]]; then return 1; fi

    if [[ "$quiet" != "quiet" ]]; then
        local n
        n="$(printf '%s\n' "$included" | wc -l | tr -d ' ')"
        echo "check-shared-lib-catalog: OK — ${n} repo-root shared-library module(s), all in the catalog, no catalog row without a module."
    fi
    return 0
}

# ---------------------------------------------------------------------------
# self-test — mutates COPIES of the real inputs, never a hand-built fixture.
# ---------------------------------------------------------------------------
self_test() {
    local pass=0 fail=0 tmp
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' RETURN

    expect() {  # expect <label> <want-rc> <dir>
        local label="$1" want="$2" dir="$3" got=0
        run_check "$dir" quiet >/dev/null 2>&1 || got=$?
        if [[ "$got" == "$want" ]]; then
            echo "  PASS  $label (rc=$got)"; pass=$((pass + 1))
        else
            echo "  FAIL  $label (want rc=$want, got rc=$got)"; fail=$((fail + 1))
        fi
    }
    make_case() {
        local d="$tmp/$1"
        mkdir -p "$d/platform"
        cp "$ROOT/settings.gradle" "$d/settings.gradle"
        cp "$ROOT/$POLICY_REL" "$d/$POLICY_REL"
        echo "$d"
    }
    # A mutation that silently matched nothing would make its case meaningless;
    # every injection is verified before the verdict is read.
    injected() {  # injected <label> <before> <after>
        if [[ "$2" == "$3" ]]; then
            echo "  FAIL  injection check: $1 changed nothing — the case below proves nothing"
            fail=$((fail + 1))
        fi
    }

    echo "self-test: predicate against mutated copies of the real inputs"
    local d before

    # 0. The real entry point as a separate process — in-process cases run with
    #    set -e suppressed behind `|| got=$?` and cannot see an errexit abort.
    local rc0=0
    bash "$ROOT/scripts/check-shared-lib-catalog.sh" >/dev/null 2>&1 || rc0=$?
    if [[ "$rc0" == 0 ]]; then
        echo "  PASS  real entry point, separate process (rc=0)"; pass=$((pass + 1))
    else
        echo "  FAIL  real entry point, separate process (rc=$rc0)"; fail=$((fail + 1))
    fi

    # 1. Unmutated — must pass, or an always-FAIL guard looks like a working one.
    d="$(make_case baseline)"
    expect "unmutated real inputs pass" 0 "$d"

    # 2. A catalog row deleted — the drift this exists for (module built, no row).
    d="$(make_case row_deleted)"
    before="$(cat "$d/$POLICY_REL")"
    sed -i '/^| `libs\/java-common` |/d' "$d/$POLICY_REL"
    injected row_deleted "$before" "$(cat "$d/$POLICY_REL")"
    expect "deleted catalog row bites" 1 "$d"

    # 3. A new repo-root module with no row.
    d="$(make_case new_module)"
    before="$(cat "$d/settings.gradle")"
    sed -i "s/'libs:java-common',/'libs:java-common',\n    'libs:java-brandnew',/" "$d/settings.gradle"
    injected new_module "$before" "$(cat "$d/settings.gradle")"
    expect "new module without a row bites" 1 "$d"

    # 4. A new module WITH its row — must pass, or the guard just bans growth.
    d="$(make_case new_module_listed)"
    sed -i "s/'libs:java-common',/'libs:java-common',\n    'libs:java-brandnew',/" "$d/settings.gradle"
    sed -i 's/^\(| `libs\/java-common` |.*\)$/\1\n| `libs\/java-brandnew` | test | test |/' "$d/$POLICY_REL"
    expect "new module with its row passes" 0 "$d"

    # 5. A stale row for a module the build no longer includes (reverse direction).
    d="$(make_case stale_row)"
    before="$(cat "$d/$POLICY_REL")"
    sed -i 's/^\(| `libs\/java-common` |.*\)$/\1\n| `libs\/java-retired` | gone | none |/' "$d/$POLICY_REL"
    injected stale_row "$before" "$(cat "$d/$POLICY_REL")"
    expect "row for an unbuilt module bites" 1 "$d"

    # 6. A project-scoped module is NOT a catalog member — adding one must pass.
    d="$(make_case project_scoped)"
    before="$(cat "$d/settings.gradle")"
    sed -i "s/'libs:java-common',/'libs:java-common',\n    'projects:some-project:libs:some-module',/" "$d/settings.gradle"
    injected project_scoped "$before" "$(cat "$d/settings.gradle")"
    expect "project-scoped module is ignored" 0 "$d"

    # 7. A module named only in a settings.gradle comment is not included.
    d="$(make_case comment_only)"
    printf "\n// 'libs:java-commented-out' was removed in some task\n" >> "$d/settings.gradle"
    expect "module named only in a // comment is ignored" 0 "$d"

    # 8. Prose outside § Catalog mentioning libs/<x> is not a row: delete a row and
    #    add a backticked mention in a later section — must still bite.
    d="$(make_case prose_mention)"
    sed -i '/^| `libs\/java-common` |/d' "$d/$POLICY_REL"
    printf '\n# Appendix\n\n| `libs/java-common` | mentioned outside the catalog |\n' >> "$d/$POLICY_REL"
    expect "a row-shaped line outside § Catalog does not count" 1 "$d"

    # 9/10. Fail closed on empty populations.
    d="$(make_case no_libs)"
    sed -i "s/'libs:[a-z0-9-]*'/'nothing'/g" "$d/settings.gradle"
    if [[ -n "$(included_root_libs "$d/settings.gradle")" ]]; then
        echo "  FAIL  injection check: no_libs still parses modules"; fail=$((fail + 1))
    fi
    expect "zero included modules fails closed" 1 "$d"

    d="$(make_case no_catalog)"
    sed -i 's/^# Catalog$/# Catalogue (renamed)/' "$d/$POLICY_REL"
    if [[ -n "$(catalog_rows "$d/$POLICY_REL")" ]]; then
        echo "  FAIL  injection check: no_catalog still parses rows"; fail=$((fail + 1))
    fi
    expect "renamed catalog heading fails closed" 1 "$d"

    # 11. Missing input is a stop, not a pass.
    d="$(make_case no_policy)"
    rm -f "$d/$POLICY_REL"
    expect "absent policy file fails closed" 1 "$d"

    echo "self-test: ${pass} passed, ${fail} failed"
    [[ "$fail" -eq 0 ]]
}

case "${1:-}" in
    --self-test) self_test ;;
    -h|--help)   usage ;;
    "")          run_check "$ROOT" ;;
    *)           usage >&2; exit 2 ;;
esac
