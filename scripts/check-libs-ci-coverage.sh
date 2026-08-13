#!/usr/bin/env bash
# =============================================================================
# check-libs-ci-coverage.sh — every shared-library module runs its own :check in CI
# TASK-MONO-527
# =============================================================================
# WHAT THIS GUARDS
#
# A Gradle module reached only as a COMPILE DEPENDENCY never runs its own tests.
# CI's jobs invoke explicit task lists (`./gradlew :a:check :b:check …`), never a
# bare root `:check`, so a libs module is verified if and only if its own `:check`
# is spelled out on one of those lists. TASK-MONO-521 measured the consequence:
# `:projects:fan-platform:apps:artist-service:check --dry-run` reached
# `:libs:java-security-servlet` at `compileJava → jar` and stopped — its tests and
# its `assertClasspathNeutrality` guard had never run. Six more modules were in the
# same state, holding 206 test cases between them, plus
# `assertNoServletOnReactiveEdge` (ADR-MONO-048 § D1) and the root
# `assertNoApiOnSharedLibs` (ADR-MONO-049 § D2) — the latter carrying a comment
# claiming it was "reachable on every code change".
#
# The failure mode is silent by construction: nothing goes red when a module drops
# off the list, because the module still compiles. Only this comparison notices.
#
# WHAT THIS DOES *NOT* GUARD
#   * That the tests inside those modules are any good, or that they cover anything.
#   * That a task list entry is spelled correctly — a typo'd task path makes Gradle
#     fail loudly at run time, which is a different (and self-announcing) failure.
#   * `projects/*/apps/*` modules. Those are named per project in each job and have
#     their own drift guards; this one is about the shared layer.
#
# PREDICATE (deliberately structural, not a proxy)
#   population A = module paths containing a `libs:` segment, included by settings.gradle
#   population B = module paths whose own `:check` appears in .github/workflows/*.yml
#   FAIL if A \ B is non-empty.
#
# Comments are stripped from settings.gradle before parsing: its long `//` blocks
# contain prose apostrophes, and a naive quote scan reads them as string delimiters
# and invents module names out of sentence fragments (measured, TASK-MONO-527).
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
    cat <<'EOF'
usage: check-libs-ci-coverage.sh [--self-test]

  (no args)    Check this repository.
  --self-test  Run the predicate against copies of the real tree, mutated. Verifies
               that it bites, that it passes when it should, and that it fails closed
               when an input is empty.
EOF
}

# ---------------------------------------------------------------------------
# populations
# ---------------------------------------------------------------------------

# Included module paths that contain a `libs:` segment, one per line.
#
# The trailing `|| true` is deliberate and does NOT weaken anything: under `pipefail`
# a `grep` that matches nothing returns 1, which errexit turns into a silent abort.
# "Matched nothing" must become EMPTY OUTPUT so that run_check's explicit
# empty-population check can reject it — an abort here would exit 1 with no message,
# which reads as a guard failure rather than the fail-closed verdict it should be.
included_libs_modules() {
    local settings="$1"
    { sed -e 's://.*::' "$settings" \
        | grep -oE "'(([a-z0-9-]+:)*libs:[a-z0-9-]+)'" \
        | tr -d "'" \
        | sort -u; } || true
}

# Module paths whose own `:check` is invoked by some workflow, one per line.
#
# YAML comments are stripped first. A workflow comment that *mentions* a task path —
# and this repo writes long explanatory comments right next to these task lists — is
# documentation, not coverage. Counting it would let a module be "covered" by the
# sentence explaining why it was removed. The self-test asserts this directly.
ci_checked_modules() {
    local wfdir="$1"

    # Collect only paths that EXIST. The obvious spelling —
    # `cat "$wfdir"/*.yml "$wfdir"/*.yaml 2>/dev/null` — carried a comment saying it was
    # chosen so an unmatched glob "must yield empty output, not a non-zero exit that -e
    # would turn into a stop". It does not do that: this repo has no `.yaml` workflow, so
    # the glob stays literal, `cat` exits 1, `pipefail` propagates it, and the script died
    # with rc=1 and NO output. The comment stated the intent; the code never met it.
    local files=() f
    for f in "$wfdir"/*.yml "$wfdir"/*.yaml; do
        if [[ -f "$f" ]]; then files+=("$f"); fi
    done
    if [[ ${#files[@]} -eq 0 ]]; then
        return 0   # empty output; run_check's fail-closed check rejects it
    fi

    # `|| true` for the same reason as included_libs_modules: no match must be empty
    # output, not an abort.
    { cat "${files[@]}" \
        | sed -e 's/#.*$//' \
        | grep -oE ':(([a-z0-9-]+:)*libs:[a-z0-9-]+):check' \
        | sed -e 's/^://' -e 's/:check$//' \
        | sort -u; } || true
}

# ---------------------------------------------------------------------------
# the check
# ---------------------------------------------------------------------------
run_check() {
    local root="$1" quiet="${2:-}"
    local settings="$root/settings.gradle"
    local wfdir="$root/.github/workflows"

    if [[ ! -f "$settings" ]]; then
        echo "FAIL: settings.gradle not found at $settings" >&2
        return 1
    fi
    if [[ ! -d "$wfdir" ]]; then
        echo "FAIL: workflow directory not found at $wfdir" >&2
        return 1
    fi

    local included checked
    included="$(included_libs_modules "$settings")"
    checked="$(ci_checked_modules "$wfdir")"

    # Fail closed on an empty population. No signal is not a pass (TASK-MONO-359):
    # an empty `included` makes the difference vacuously empty, and an empty
    # `checked` would mean CI runs no libs check at all — which is the very state
    # this guard exists to prevent, not a reason to report OK.
    if [[ -z "$included" ]]; then
        echo "FAIL: parsed ZERO libs modules from settings.gradle." >&2
        echo "      The comparison would pass having verified nothing." >&2
        return 1
    fi
    if [[ -z "$checked" ]]; then
        echo "FAIL: no ':libs:*:check' invocation found in any workflow." >&2
        echo "      Every shared-library module is therefore unverified in CI." >&2
        return 1
    fi

    # Set difference in pure bash. `comm -23 <(…) <(…)` is the obvious spelling and was
    # the first one written; under msys (this repo's dev host) the process substitutions
    # killed the shell outright — no output, exit 1, no trace line. The self-test did not
    # catch it because every case there invokes run_check inside `|| got=$?`, which is
    # exactly the `set -e` suppression trap MONO-4xx recorded. No external command and no
    # /dev/fd here, so the guard behaves identically on the runner and on the dev host.
    local missing="" m
    local nl=$'\n'
    while IFS= read -r m; do
        if [[ -z "$m" ]]; then continue; fi
        case "${nl}${checked}${nl}" in
            *"${nl}${m}${nl}"*) ;;                  # covered
            *) missing="${missing}${m}${nl}" ;;      # not on any CI task list
        esac
    done <<< "$included"
    missing="${missing%"$nl"}"

    if [[ -n "$missing" ]]; then
        echo "FAIL: shared-library module(s) never run their own \`:check\` in CI." >&2
        echo >&2
        while IFS= read -r m; do
            if [[ -z "$m" ]]; then continue; fi
            echo "  :$m" >&2
        done <<< "$missing"
        echo >&2
        echo "  A module reached only as a compile dependency is built, not tested —" >&2
        echo "  its \`test\` task and any guard wired to its \`check\` never enter the" >&2
        echo "  task graph. Add \`:<module>:check\` to the libs task list in" >&2
        echo "  .github/workflows/ci.yml (TASK-MONO-521 / TASK-MONO-527)." >&2
        echo >&2
        echo "  Do NOT resolve this by removing the module from settings.gradle." >&2
        return 1
    fi

    if [[ "$quiet" != "quiet" ]]; then
        local n_inc n_chk
        n_inc="$(printf '%s\n' "$included" | wc -l | tr -d ' ')"
        n_chk="$(printf '%s\n' "$checked" | wc -l | tr -d ' ')"
        echo "check-libs-ci-coverage: OK — ${n_inc} shared-library module(s) included, all run their own :check (${n_chk} :check invocation(s) found)."
    fi
    return 0
}

# ---------------------------------------------------------------------------
# self-test — mutates COPIES of the real tree, never a hand-built fixture.
# A fixture is more forgiving than reality and proves less (TASK-MONO-526).
# ---------------------------------------------------------------------------
self_test() {
    local pass=0 fail=0
    local tmp
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' RETURN

    expect() {  # expect <label> <expected-rc> <dir>
        local label="$1" want="$2" dir="$3" got=0
        run_check "$dir" quiet >/dev/null 2>&1 || got=$?
        if [[ "$got" == "$want" ]]; then
            echo "  PASS  $label (rc=$got)"
            pass=$((pass + 1))
        else
            echo "  FAIL  $label (want rc=$want, got rc=$got)"
            fail=$((fail + 1))
        fi
    }

    make_case() {  # make_case <name> -> echoes dir
        local d="$tmp/$1"
        mkdir -p "$d/.github/workflows"
        cp "$ROOT/settings.gradle" "$d/settings.gradle"
        cp "$ROOT"/.github/workflows/*.yml "$d/.github/workflows/" 2>/dev/null || true
        echo "$d"
    }

    echo "self-test: predicate against mutated copies of the real tree"

    local d
    # 0. THE REAL ENTRY POINT, as a separate process.
    #
    # 🔴 Every `expect` below calls run_check in-process behind `|| got=$?`, and that
    # suppresses `set -e` for the whole call. A command that aborts the script under
    # errexit therefore reports PASS here while the real invocation dies silently with
    # rc=1 and no output. That happened during this guard's own development — twice, from
    # two different causes (a `comm` process substitution crashing under msys, then a
    # `[[ … ]] && continue` returning non-zero on the common path). The in-process
    # baseline said PASS both times.
    #
    # So the first case runs the script the way CI runs it. If this fails, nothing below
    # it means anything.
    local rc0=0
    bash "$ROOT/scripts/check-libs-ci-coverage.sh" >/dev/null 2>&1 || rc0=$?
    if [[ "$rc0" == 0 ]]; then
        echo "  PASS  real entry point, separate process, errexit active (rc=0)"
        pass=$((pass + 1))
    else
        echo "  FAIL  real entry point, separate process, errexit active (rc=$rc0)"
        echo "        The in-process cases below run with set -e suppressed and cannot see this."
        fail=$((fail + 1))
    fi

    # 1. Unmutated real tree — must pass. Without this, an always-FAIL guard would
    #    look identical to a working one.
    d="$(make_case baseline)"
    expect "unmutated real tree passes" 0 "$d"

    # 2. A module dropped from the CI list — the actual defect.
    d="$(make_case dropped)"
    sed -i 's/:libs:java-security:check//' "$d/.github/workflows/ci.yml"
    expect "module dropped from the CI task list bites" 1 "$d"

    # 3. A NEW module included but never added to CI — the drift this prevents.
    d="$(make_case newmodule)"
    sed -i "s/'libs:java-common',/'libs:java-common',\n    'libs:java-brandnew',/" "$d/settings.gradle"
    expect "newly included module with no CI entry bites" 1 "$d"

    # 4. A new module that IS added to CI — must pass, or the guard just bans growth.
    d="$(make_case newmodule_wired)"
    sed -i "s/'libs:java-common',/'libs:java-common',\n    'libs:java-brandnew',/" "$d/settings.gradle"
    sed -i 's/:libs:java-common:check/:libs:java-common:check\n          :libs:java-brandnew:check/' "$d/.github/workflows/ci.yml"
    expect "newly included module WITH a CI entry passes" 0 "$d"

    # 5/6. Fail-closed on an empty side. An empty difference must not read as OK.
    #
    # The mutation must strip EVERY libs path, project-scoped ones included. A first
    # version matched only `'libs:…'` and left
    # `'projects:finance-platform:libs:finance-common'` standing, so the population was
    # 1 rather than 0 and the case reported the guard as broken when the injection was.
    # Verified below by asserting the mutated file really parses to zero.
    d="$(make_case no_libs)"
    sed -i "s/'[a-z0-9:-]*libs:[a-z0-9-]*'/'nothing'/g" "$d/settings.gradle"
    if [[ -n "$(included_libs_modules "$d/settings.gradle")" ]]; then
        echo "  FAIL  injection check: no_libs still parses modules — the case below would be meaningless"
        fail=$((fail + 1))
    else
        echo "  PASS  injection check: no_libs really parses to zero modules"
        pass=$((pass + 1))
    fi
    expect "zero parsed modules fails closed" 1 "$d"

    d="$(make_case no_ci)"
    sed -i 's/:libs:[a-z0-9-]*:check//g' "$d"/.github/workflows/*.yml
    expect "zero CI check invocations fails closed" 1 "$d"

    # 7. A comment mentioning a module must NOT count as coverage. This is the
    #    mistake the guard's own author made first (prose parsed as data).
    d="$(make_case comment_only)"
    sed -i 's/:libs:java-security:check/# :libs:java-security:check/' "$d/.github/workflows/ci.yml"
    expect "a COMMENTED-OUT entry does not count as coverage" 1 "$d"

    # 8. Missing inputs are a stop, not a pass.
    d="$(make_case no_settings)"
    rm -f "$d/settings.gradle"
    expect "absent settings.gradle fails closed" 1 "$d"

    echo "self-test: ${pass} passed, ${fail} failed"
    [[ "$fail" -eq 0 ]]
}

case "${1:-}" in
    --self-test) self_test ;;
    -h|--help)   usage ;;
    "")          run_check "$ROOT" ;;
    *)           usage >&2; exit 2 ;;
esac
