#!/usr/bin/env bash
# =============================================================================
# check-outside-module-input-filter-coverage.sh
# TASK-MONO-695
# =============================================================================
# WHAT THIS GUARDS
#
# ANY `build.gradle` under `projects/<p>/**` — not just `projects/<p>/apps/<m>/`
# — can declare `inputs.(file|dir|files)(rootProject.file('...'))` on a path
# OUTSIDE `projects/<p>/**` — most often a repo-root `infra/demo/seed/*.sh`
# demo seed script a test reads by path — to fix Gradle's `UP-TO-DATE` /
# `FROM-CACHE` blind spot: without the declaration, editing the seed leaves
# the test task cached and green over exactly the drift it exists to catch
# (TASK-MONO-683 measured this: a seed revert produced rc=0 with `:test`
# UP-TO-DATE). Besides `apps/<m>/build.gradle`, this repo has three other
# shapes that carry the same risk and none of them are hypothetical:
#   * `projects/<p>/build.gradle` — the project-level file whose `subprojects
#     {}` block (see e.g. `projects/wms-platform/build.gradle`'s commented
#     template) applies to EVERY module in the project at once, making it the
#     single likeliest future spot for exactly this declaration.
#   * `projects/<p>/libs/<m>/build.gradle` — project-scoped shared modules
#     (CLAUDE.md § "Project-scoped shared modules"), e.g.
#     `projects/finance-platform/libs/finance-common/build.gradle`.
#   * `projects/<p>/tests/e2e/build.gradle` — the project's e2e module.
# A review pass on this guard's first version (TASK-MONO-695) found it scanned
# only `apps/*/build.gradle` while claiming (here, in AC-3, and in the ci.yml
# filter gating this job) to cover the whole project — exactly the "population
# hole" shape this ticket exists to close, self-inflicted. Fixed by scanning
# `projects/**/build.gradle` instead — see PREDICATE below.
#
# That Gradle-side fix does nothing for `.github/workflows/ci.yml`'s PR path
# filter, which wakes `Build & Test` on `projects/<p>/**` only. A PR touching
# ONLY the outside file leaves that flag false, `Build & Test` SKIPPED, the PR
# merges green, and the seed-coupled test only runs — and can only go red — on
# the `main` push after merge (measured: PR #3246, `Build & Test` SKIPPED on
# the PR / success on the merge commit's `main` run; at the time nothing read
# the seed, so it was harmless — TASK-MONO-683 later added a test that does).
#
# `projects/iam-platform/apps/auth-service/build.gradle` had already recorded
# this exact gap in prose (`infra/demo/seed/seed-fan.sh`) as "UNOWNED", and
# `docs/adr/ADR-MONO-063.md:187` recorded it the same way. TASK-MONO-683 then
# created a second, identical gap in wms (`infra/demo/seed/seed-scm.sh`)
# without knowing about the first, because the "unowned" record was prose with
# no gate behind it. This guard is the gate.
#
# WHAT THIS DOES *NOT* GUARD
#   * That an outside-project input is a GOOD idea, or that the declaring test
#     is correct. It only asserts the filter can see what the test reads.
#   * Inputs whose declared path is inside the declaring module's OWN project
#     (`projects/<p>/**`) — the project-level PR filter already covers those by
#     construction; that is population B's negative control (6 iam entries).
#   * Repo-root `libs/**` and the repo-root `build.gradle` — deliberately OUT
#     of population A, not an oversight: those two paths are not behind any
#     `projects/<p>/**` project filter at all, so "which project's filter
#     should cover this" does not have an answer for them. An outside-project
#     input declared there would need the `libs` output flag instead — a
#     separate axis this guard does not police (recorded as a known exclusion
#     in TASK-MONO-695's task file, not silently dropped).
#   * `nightly-e2e.yml`'s own trigger conditions — population A DOES scan
#     `projects/<p>/tests/e2e/build.gradle` (widened by TASK-MONO-695's review
#     fix, below), so an outside-project input declared there is still checked
#     against ci.yml's PR filter for that project. That check is conservative
#     rather than exact for this one shape: it does not verify that ci.yml's
#     `Build & Test` job actually reaches the e2e module's task (it may not,
#     since nightly e2e suites are normally invoked by a different workflow) —
#     only that the filter WOULD wake `changes` if the input path changed.
#     Catching an uncovered filter early is strictly safer than the alternative
#     of not scanning the file at all, which is what TASK-MONO-695's review
#     fix corrected.
#   * Undeclared outside-module reads (a test that reads a path outside its
#     module WITHOUT an `inputs.*` declaration at all) — TASK-MONO-695's AC-1 ②
#     census found zero of those on the tree this guard was written against;
#     fixing that class is `Gradle input declaration`, a different guard's job.
#
# PREDICATE (deliberately structural, not a proxy)
#   population A = every `inputs.(file|dir|files)(rootProject.file('X'))` in a
#                  `projects/**/build.gradle` (every module shape: `apps/<m>`,
#                  the project-level file, `libs/<m>`, `tests/e2e` — anything
#                  under `projects/<p>/`, not `apps/` alone), where X does NOT
#                  start with `projects/<owning-project>/` — i.e. the declared
#                  input is OUTSIDE the project that owns the declaring
#                  build.gradle. Repo-root `libs/**` and the repo-root
#                  `build.gradle` are excluded (see above).
#   population B = ci.yml's `changes` job `filters:` block, parsed structurally
#                  (12-space-indented `key:` lines start a filter, 14-space
#                  `- 'pattern'` lines belong to the CURRENT filter) — never a
#                  hand-copied path list, so this guard cannot drift from the
#                  filter it is reading.
#   FAIL if some (project, path) in A is not covered by project's pattern list
#   in B, where "covered" is an exact match OR a `dir/**` prefix match (handles
#   `inputs.dir` — Edge Cases table row 2).
#
# Comments at filter-key indent (12 spaces, `#...`) are explicitly skipped —
# not merely failed to match "key:" syntax by luck — because this guard's own
# ci.yml comments sit at that indent and a naive scanner would either misread
# them as a filter key or silently swallow the real key below them.
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
    cat <<'EOF'
usage: check-outside-module-input-filter-coverage.sh [--self-test]

  (no args)    Check this repository.
  --self-test  Run the predicate against copies of the real tree, mutated. Verifies
               that it bites (AC-3's revert-one-filter-line case), that it passes
               when it should, that the in-project negative control is excluded
               from population A, and that it fails closed when an input is empty.
EOF
}

# EVERY build.gradle under projects/**, repo-relative — not just apps/*/.
#
# TASK-MONO-695 review fix: the first version of this guard scanned only
# `projects/*/apps/*/build.gradle`, which left an outside-project `inputs.*`
# declared in a project-level `projects/<p>/build.gradle` (whose `subprojects
# {}` applies to every module at once — the likeliest future spot), a
# `projects/<p>/libs/<m>/build.gradle` (CLAUDE.md project-scoped shared
# module), or a `projects/<p>/tests/e2e/build.gradle` completely invisible —
# exactly the population hole this ticket exists to close, in the guard meant
# to close it. `projects/**/build.gradle` deliberately does NOT include
# repo-root `libs/**` or the repo-root `build.gradle` — see the header's
# "WHAT THIS DOES NOT GUARD".
#
# `git ls-files` when `$root` is a real git work tree — this repo's guards read
# the committed/staged index (CLAUDE.md § "Stage before you run a repo guard
# locally"); a file not yet `git add`ed must not silently vanish from either
# population. Falls back to `find` for a self-test fixture dir, which is a
# purpose-built minimal copy (not a git repo at all) rather than a real
# checkout — see self_test()'s make_case.
list_build_gradle_files() {
    local root="$1"
    if git -C "$root" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
        git -C "$root" ls-files -- 'projects/**/build.gradle' | sort
    else
        (cd "$root" && find projects -name build.gradle 2>/dev/null | sed 's#^\./##' | sort)
    fi
}

# ---------------------------------------------------------------------------
# population A — outside-project declared Gradle inputs
#   one line per hit: "<project>\t<kind>\t<declaredPath>\t<buildGradleRelPath>:<lineNo>"
# ---------------------------------------------------------------------------
outside_declared_inputs() {
    local root="$1"
    local f rel project lineno match kind path

    while IFS= read -r rel; do
        [[ -z "$rel" ]] && continue
        f="$root/$rel"
        [[ -f "$f" ]] || continue

        project="$(printf '%s\n' "$rel" | sed -nE 's#^projects/([a-zA-Z0-9_-]+)/.*#\1#p')"
        [[ -z "$project" ]] && continue

        while IFS=: read -r lineno match; do
            [[ -z "$lineno" ]] && continue
            kind="$(printf '%s' "$match" | sed -E "s/^inputs\.([a-z]+)\(.*/\1/")"
            path="$(printf '%s' "$match" | sed -E "s/.*rootProject\.file\('([^']+)'\).*/\1/")"
            case "$path" in
                "projects/$project/"*) ;;  # inside its OWN project — not population A
                *) printf '%s\t%s\t%s\t%s:%s\n' "$project" "$kind" "$path" "$rel" "$lineno" ;;
            esac
        done < <(grep -noE "inputs\.(file|dir|files)\(rootProject\.file\('[^']+'\)\)" "$f" || true)
    done < <(list_build_gradle_files "$root")
}

# ---------------------------------------------------------------------------
# population B — ci.yml's own `changes` job filter block, key -> patterns
#   one line per pattern: "<key>\t<pattern>"
# ---------------------------------------------------------------------------
ci_yml_filters() {
    local ciyml="$1"
    awk '
        /^          filters: \|$/ { inblock=1; next }
        inblock && /^  [A-Za-z][A-Za-z0-9_-]*:$/ { inblock=0 }
        !inblock { next }
        /^            #/ { next }
        /^            [A-Za-z][A-Za-z0-9_-]*:$/ {
            key=$0
            sub(/^            /, "", key)
            sub(/:$/, "", key)
            next
        }
        /^              - / {
            if (key == "") next
            pat=$0
            sub(/^              - /, "", pat)
            print key "\t" pat
        }
    ' "$ciyml" \
        | sed -E "s/\t'/\t/; s/'\$//"
}

# project directory name (e.g. `ecommerce-microservices-platform`) -> ci.yml
# filter key (e.g. `ecommerce`). Edge Cases table: "가드는 «프로젝트 = 이름» 이
# 아니라 실제 필터 목록으로 판정" — the directory name is not a reliable
# transform of the key (ecommerce-microservices-platform -> ecommerce, not
# "ecommerce-platform"), so this derives the mapping from ci.yml's OWN
# bare-project-root pattern (`- 'projects/<dir>/**'`) rather than guessing a
# naming rule. A project whose filter has no such bare-root entry (e.g.
# `platform-console`, which lists specific sub-paths instead) is deliberately
# NOT mapped here — that ambiguity is a fail-closed "no filter for this
# project" below, not a guess, matching the same table row's warning.
# Single awk pass, deliberately — a per-line `sed` fork here (one per pattern
# in the WHOLE filters block, a hundred-plus lines) measured slow enough on
# this host's msys fork overhead to blow past a 120s budget in self-test,
# which calls this once per mutated case (env_msys_fork_exhaustion class).
project_key_map() {
    local filtermap="$1"
    printf '%s\n' "$filtermap" | awk -F'\t' '
        {
            if ($2 ~ /^projects\/[^/]+\/\*\*$/) {
                dir = $2
                sub(/^projects\//, "", dir)
                sub(/\/\*\*$/, "", dir)
                print dir "\t" $1
            }
        }
    '
}

# "covered" — exact match, or a `dir/**` prefix match (Edge Cases: dir inputs
# need prefix matching, since the filter that catches them is `dir/**`).
path_covered() {
    local path="$1" patterns="$2" p prefix
    while IFS= read -r p; do
        [[ -z "$p" ]] && continue
        if [[ "$p" == "$path" ]]; then
            return 0
        fi
        case "$p" in
            */'**')
                # Quoting '**' forces a LITERAL trailing "/**" match in both the case
                # pattern above and the %-trim below — unquoted, bash's parameter-
                # expansion glob would treat `**` as "any string" and strip from the
                # first '/', not the last.
                prefix="${p%/'**'}"
                if [[ "$path" == "$prefix" || "$path" == "$prefix"/* ]]; then
                    return 0
                fi
                ;;
        esac
    done <<< "$patterns"
    return 1
}

# ---------------------------------------------------------------------------
# the check
# ---------------------------------------------------------------------------
run_check() {
    local root="$1" quiet="${2:-}"
    local ciyml="$root/.github/workflows/ci.yml"

    if [[ ! -f "$ciyml" ]]; then
        echo "FAIL: ci.yml not found at $ciyml" >&2
        return 1
    fi

    local outside filtermap
    outside="$(outside_declared_inputs "$root")"
    filtermap="$(ci_yml_filters "$ciyml")"

    # Fail closed on an empty population (TASK-MONO-359 class): no signal is
    # not a pass. An empty `outside` would make the comparison vacuously
    # empty; an empty `filtermap` means the parser cannot see ci.yml's own
    # filters at all, which would make every project look "uncovered" for the
    # wrong reason (or, worse, never get compared).
    if [[ -z "$outside" ]]; then
        echo "FAIL: parsed ZERO outside-project Gradle test inputs from projects/*/apps/*/build.gradle." >&2
        echo "      The comparison would pass having verified nothing." >&2
        return 1
    fi
    if [[ -z "$filtermap" ]]; then
        echo "FAIL: parsed ZERO filter patterns from ci.yml's changes job \`filters: |\` block." >&2
        echo "      Either the block moved/reformatted, or this guard's parser broke." >&2
        return 1
    fi

    local keymap
    keymap="$(project_key_map "$filtermap")"

    local missing="" project kind path buildfile key patterns
    while IFS=$'\t' read -r project kind path buildfile; do
        [[ -z "$project" ]] && continue
        key="$(printf '%s\n' "$keymap" | awk -F'\t' -v d="$project" '$1==d{print $2; exit}')"
        if [[ -z "$key" ]]; then
            missing="${missing}${buildfile}: no ci.yml filter maps project directory '${project}' to a key at all (input ${path})"$'\n'
            continue
        fi
        patterns="$(printf '%s\n' "$filtermap" | awk -F'\t' -v k="$key" '$1==k{print $2}')"
        if [[ -z "$patterns" ]]; then
            missing="${missing}${buildfile}: no ci.yml filter named '${key}' exists at all (input ${path})"$'\n'
            continue
        fi
        if ! path_covered "$path" "$patterns"; then
            missing="${missing}${buildfile}: '${key}' filter does not cover ${path} (declared as inputs.${kind})"$'\n'
        fi
    done <<< "$outside"

    if [[ -n "$missing" ]]; then
        echo "FAIL: outside-project Gradle test input(s) not covered by their project's ci.yml PR filter." >&2
        echo >&2
        printf '%s' "$missing" | while IFS= read -r line; do
            [[ -z "$line" ]] && continue
            echo "  $line" >&2
        done
        echo >&2
        echo "  A PR touching ONLY that file wakes no project flag -> \`Build & Test\` is SKIPPED on" >&2
        echo "  the PR, and the test the Gradle declaration protects only runs (and can only go red)" >&2
        echo "  after the merge lands on main (TASK-MONO-695)." >&2
        echo "  Fix: add the EXACT file path to that project's filter block in" >&2
        echo "  .github/workflows/ci.yml — pure-positive, no directory wildcard wider than" >&2
        echo "  necessary (CLAUDE.md § CI path-filter; TASK-MONO-695 Failure Scenario 1)." >&2
        return 1
    fi

    if [[ "$quiet" != "quiet" ]]; then
        local n
        n="$(printf '%s\n' "$outside" | grep -c . || true)"
        echo "check-outside-module-input-filter-coverage: OK — ${n} outside-project Gradle input(s), all covered by their project's ci.yml filter."
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

    # A minimal COPY of only the files this guard reads — every
    # `projects/**/build.gradle` (from the actual on-disk working tree, not a
    # git object, so it sees this session's OWN uncommitted fixes) and
    # `.github/workflows/ci.yml`. Not a git repo: `list_build_gradle_files`
    # falls back to `find` when `$root` has no `.git`, matching a check-list
    # sibling (check-libs-ci-coverage.sh) that copies only settings.gradle +
    # the workflow ymls rather than the whole repository — full-tree `cp -r`
    # or `git clone` of this monorepo is both slow and beside the point.
    make_case() {  # make_case <name> -> echoes dir
        local d="$tmp/$1" rel
        mkdir -p "$d/.github/workflows"
        cp "$ROOT/.github/workflows/ci.yml" "$d/.github/workflows/ci.yml"
        while IFS= read -r rel; do
            [[ -z "$rel" ]] && continue
            mkdir -p "$d/$(dirname "$rel")"
            cp "$ROOT/$rel" "$d/$rel"
        done < <(git -C "$ROOT" ls-files -- 'projects/**/build.gradle')
        echo "$d"
    }

    echo "self-test: predicate against mutated copies of the real tree"

    local d

    # 0. THE REAL ENTRY POINT, as a separate process (same rationale as
    #    check-libs-ci-coverage.sh: in-process `|| got=$?` suppresses `set -e`
    #    for the whole call, so a script that aborts under errexit can still
    #    report PASS below if only tested in-process).
    local rc0=0
    bash "$ROOT/scripts/check-outside-module-input-filter-coverage.sh" >/dev/null 2>&1 || rc0=$?
    if [[ "$rc0" == 0 ]]; then
        echo "  PASS  real entry point, separate process, errexit active (rc=0)"
        pass=$((pass + 1))
    else
        echo "  FAIL  real entry point, separate process, errexit active (rc=$rc0)"
        echo "        The in-process cases below run with set -e suppressed and cannot see this."
        fail=$((fail + 1))
    fi

    # 1. Unmutated real tree — must pass (AC-2's fix is already landed).
    d="$(make_case baseline)"
    expect "unmutated real tree passes" 0 "$d"

    # 2. Negative control (Edge Cases table row 3): the 6 in-project iam
    #    inputs must be EXCLUDED from population A, not merely "covered" —
    #    asserted directly, because a guard that happened to cover them by
    #    accident would still pass `expect` above without proving the
    #    project-boundary check works.
    d="$(make_case negctl)"
    local negctl_out negctl_leak
    negctl_out="$(outside_declared_inputs "$d")"
    # Field 3 (declared path) specifically — NOT the whole line, which always
    # contains the substring "projects/iam-platform/" in field 4 (the
    # build.gradle's own path) regardless of whether the DECLARED INPUT leaked.
    negctl_leak="$(printf '%s\n' "$negctl_out" | awk -F'\t' '$1=="iam-platform" && $3 ~ /^projects\/iam-platform\//')"
    if [[ -n "$negctl_leak" ]]; then
        echo "  FAIL  in-project iam inputs leaked into population A (outside-project)"
        printf '%s\n' "$negctl_leak" | sed 's/^/        /'
        fail=$((fail + 1))
    else
        echo "  PASS  in-project iam inputs excluded from population A (negative control)"
        pass=$((pass + 1))
    fi

    # 3. Positive control (AC-1): the already-declared, already-covered wms
    #    seed-scm.sh input must appear in population A at all (proves the
    #    scan itself fires on a known outside-module read, declared or not —
    #    AC-1's requirement that ① 's positive control also trips ②'s
    #    predicate).
    d="$(make_case posctl)"
    local posctl_out
    posctl_out="$(outside_declared_inputs "$d")"
    if printf '%s\n' "$posctl_out" | grep -q "^wms-platform"$'\t'"file"$'\t'"infra/demo/seed/seed-scm.sh"; then
        echo "  PASS  wms seed-scm.sh outside-project input detected (positive control)"
        pass=$((pass + 1))
    else
        echo "  FAIL  wms seed-scm.sh outside-project input NOT detected — scan predicate is broken"
        fail=$((fail + 1))
    fi

    # 4. BITE — revert one AC-2 filter line. Must fail, naming module:line.
    d="$(make_case reverted)"
    sed -i "/^            wms:\$/,/^            ecommerce:\$/ { /- 'infra\/demo\/seed\/seed-scm.sh'/d }" \
        "$d/.github/workflows/ci.yml"
    local out
    out="$(run_check "$d" quiet 2>&1 || true)"
    # Line number NOT hardcoded — inbound-service/build.gradle's own header
    # comment (AC-5) has already moved this once (75 -> 85); pin the file, not
    # the line, or this case silently rots the next time that file gains a
    # comment line above the declaration.
    if printf '%s\n' "$out" | grep -qE "inbound-service/build\.gradle:[0-9]+: 'wms' filter does not cover infra/demo/seed/seed-scm\.sh"; then
        echo "  PASS  reverted wms filter line bites, names inbound-service/build.gradle:<line>"
        pass=$((pass + 1))
    else
        echo "  FAIL  reverted wms filter line did not bite / did not name the module:line"
        echo "$out" | sed 's/^/        /'
        fail=$((fail + 1))
    fi
    expect "reverted wms filter line: rc != 0" 1 "$d"

    # 5. RESTORE (manual edit — never `git checkout --`, CLAUDE.md § B1). Remove
    #    the line the same way case 4 did, then re-add it, then check pass again
    #    — proves the guard is not just "always fails on a fresh clone" or some
    #    other artifact of `make_case`.
    d="$(make_case restored)"
    sed -i "/^            wms:\$/,/^            ecommerce:\$/ { /- 'infra\/demo\/seed\/seed-scm.sh'/d }" \
        "$d/.github/workflows/ci.yml"
    sed -i "s/^            wms:\$/            wms:\n              - 'infra\/demo\/seed\/seed-scm.sh'/" \
        "$d/.github/workflows/ci.yml"
    expect "manually restored wms filter line passes again" 0 "$d"

    # 6. A NEW outside-project input added without a matching filter line — the
    #    drift AC-3 exists to prevent from recurring.
    d="$(make_case newgap)"
    sed -i "s#inputs.file(rootProject.file('infra/demo/seed/seed-fan.sh'))#inputs.file(rootProject.file('infra/demo/seed/seed-fan.sh'))\n    inputs.file(rootProject.file('infra/demo/seed/seed-finance.sh'))\n            .withPropertyName('newGapProbe')\n            .withPathSensitivity(PathSensitivity.RELATIVE)#" \
        "$d/projects/iam-platform/apps/auth-service/build.gradle"
    expect "new undeclared-in-filter outside input bites" 1 "$d"

    # 6b. TASK-MONO-695 REVIEW FIX — the population hole this fix closes: an
    #     outside-project input declared in a PROJECT-LEVEL `projects/<p>/
    #     build.gradle` (the `subprojects {}` shape, not `apps/<m>/`). Appended
    #     to scm-platform's project-level file, a project untouched by any
    #     other case here, so this case cannot pass by accident of some other
    #     case's state. Must bite, naming that exact file:line.
    d="$(make_case projectlevelgap)"
    cat >> "$d/projects/scm-platform/build.gradle" <<'GRADLE'

subprojects {
    tasks.matching { it.name == 'test' }.configureEach {
        inputs.file(rootProject.file('infra/demo/seed/seed-project-level-probe.sh'))
                .withPropertyName('projectLevelGapProbe')
                .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
GRADLE
    out="$(run_check "$d" quiet 2>&1 || true)"
    if printf '%s\n' "$out" | grep -q "scm-platform/build.gradle:[0-9]*: 'scm' filter does not cover infra/demo/seed/seed-project-level-probe.sh"; then
        echo "  PASS  project-level (subprojects {}) outside input bites, names scm-platform/build.gradle:<line>"
        pass=$((pass + 1))
    else
        echo "  FAIL  project-level outside input did not bite / did not name the file:line"
        echo "$out" | sed 's/^/        /'
        fail=$((fail + 1))
    fi
    expect "project-level outside input: rc != 0" 1 "$d"

    # 6c. Same population hole, the `projects/<p>/tests/e2e/build.gradle`
    #     shape. Appended to fan-platform's e2e module, independent of the
    #     other cases. Must bite, naming that exact file:line.
    d="$(make_case e2elevelgap)"
    cat >> "$d/projects/fan-platform/tests/e2e/build.gradle" <<'GRADLE'

tasks.matching { it.name == 'test' }.configureEach {
    inputs.file(rootProject.file('infra/demo/seed/seed-e2e-level-probe.sh'))
            .withPropertyName('e2eLevelGapProbe')
            .withPathSensitivity(PathSensitivity.RELATIVE)
}
GRADLE
    out="$(run_check "$d" quiet 2>&1 || true)"
    if printf '%s\n' "$out" | grep -q "fan-platform/tests/e2e/build.gradle:[0-9]*: 'fan' filter does not cover infra/demo/seed/seed-e2e-level-probe.sh"; then
        echo "  PASS  tests/e2e outside input bites, names fan-platform/tests/e2e/build.gradle:<line>"
        pass=$((pass + 1))
    else
        echo "  FAIL  tests/e2e outside input did not bite / did not name the file:line"
        echo "$out" | sed 's/^/        /'
        fail=$((fail + 1))
    fi
    expect "tests/e2e outside input: rc != 0" 1 "$d"

    # 6d. Positive control for 6b/6c: the existing wms (apps/*) and iam
    #     (apps/*) cases from ①/② above must STILL pass after the population
    #     widened — the fix must not have narrowed anything it already caught.
    d="$(make_case posctl_after_widen)"
    expect "wms/iam apps/* cases still pass after population widened" 0 "$d"

    # 7. Fail-closed: no build.gradle files at all.
    d="$(make_case no_gradle)"
    find "$d/projects" -name build.gradle -delete
    expect "zero build.gradle files fails closed" 1 "$d"

    # 8. Fail-closed: ci.yml has no filters block the parser can see.
    d="$(make_case no_filters)"
    sed -i "s/^          filters: |\$/          filters_renamed: |/" "$d/.github/workflows/ci.yml"
    expect "unparseable filters block fails closed" 1 "$d"

    echo "self-test: ${pass} passed, ${fail} failed"
    [[ "$fail" -eq 0 ]]
}

case "${1:-}" in
    --self-test) self_test ;;
    -h|--help)   usage ;;
    "")          run_check "$ROOT" ;;
    *)           usage >&2; exit 2 ;;
esac
