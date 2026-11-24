#!/usr/bin/env bash
# =============================================================================
# check-metric-name-registry.sh — a metric a service registers under a literal
# name is documented somewhere in that project's specs/
# TASK-MONO-833
# =============================================================================
# WHAT THIS GUARDS
#
# platform/observability.md § Business Metrics: "Each service must define its own
# business metrics in specs/services/<service>/..."; § Change Rule: "New metrics …
# must be documented here before implementation." Nothing enforced either.
# Measured 2026-11-24 UTC: 112 literal metric names across 32 services, 20 of them
# (8 services) documented nowhere in the repository — including a stuck-order
# recovery-exhausted counter, an auto-lock failure counter and outbox publish
# failure counters, i.e. exactly the ones an operator would alert on, and could
# not know existed.
#
# SOUND, NOT COMPLETE — on purpose (same decision as check-error-code-registry.sh).
#   Only names passed as a STRING LITERAL to a meter registration are collected:
#     Counter|Timer|Gauge|DistributionSummary|LongTaskTimer|FunctionCounter|
#     FunctionTimer .builder("…")   and   .counter("…" / .timer("…" / .gauge("…" /
#     .summary("…"
#   A name held in a constant, a field, or built by concatenation is NOT seen.
#   That is a known gap, not an oversight: chasing it means resolving Java
#   expressions, and a guard that bites wrongly is one people switch off.
#
# PREDICATE
#   population = (project, service, name) from projects/<p>/apps/<s>/src/main/**/*.java
#                (tracked files; `//` line comments and `*` doc-comment lines skipped)
#   PASS for a name iff the name — or its Prometheus spelling with `.` → `_` —
#   appears as a fixed string in some tracked file under projects/<p>/specs/
#   (the SAME project). A mention in tasks/, another project's specs, or the code
#   itself is not documentation: this guard's own ticket lists twenty of these
#   names, and must not be what turns the check green.
#   FAIL CLOSED when the population is empty.
#
# Population is read from the INDEX (git ls-files), like the other repo guards:
# stage before running it locally (CLAUDE.md § Stage before you run a repo guard).
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
    cat <<'EOF'
usage: check-metric-name-registry.sh [--self-test]

  (no args)    Check this repository.
  --self-test  Run the predicate against a mutated copy of the real inputs.
EOF
}

# (project<TAB>service<TAB>name) per line, sorted, possibly empty.
# One awk process over all files (no per-file subshell — msys fork exhaustion).
literal_metrics() {
    local root="$1"
    { git -C "$root" ls-files -z -- ':(glob)projects/*/apps/*/src/main/**/*.java' \
        | (cd "$root" && xargs -0 -r awk '
            {
              line = $0
              sub(/\r$/, "", line)
              if (line ~ /^[ \t]*\*/) next          # javadoc / block-comment body
              sub(/\/\/.*/, "", line)               # line comment
              re = "((Counter|Timer|Gauge|DistributionSummary|LongTaskTimer|FunctionCounter|FunctionTimer)[.]builder|[.](counter|timer|gauge|summary))[(][ \t]*\"[A-Za-z][A-Za-z0-9_.-]*\""
              while (match(line, re)) {
                m = substr(line, RSTART, RLENGTH)
                line = substr(line, RSTART + RLENGTH)
                q = index(m, "\"")
                name = substr(m, q + 1)
                sub(/".*/, "", name)
                n = split(FILENAME, a, "/")
                print a[2] "\t" a[4] "\t" name
              }
            }') \
        | sort -u; } || true
}

# Names (one per line) from $2 that are documented under projects/$1/specs in $3.
documented_names() {
    local project="$1" names="$2" root="$3" pat
    pat="$(mktemp)"
    # both spellings of every name, as fixed strings
    printf '%s\n' "$names" | awk 'NF { print; g = $0; gsub(/[.]/, "_", g); if (g != $0) print g }' > "$pat"
    { git -C "$root" ls-files -z -- "projects/$project/specs" \
        | (cd "$root" && xargs -0 -r grep -ohF -f "$pat") \
        | sort -u; } || true
    rm -f "$pat"
}

run_check() {
    local root="$1" quiet="${2:-}"
    local rows
    rows="$(literal_metrics "$root")"
    if [[ -z "$rows" ]]; then
        echo "FAIL: found ZERO literal metric registrations under projects/*/apps/*/src/main." >&2
        echo "      Either the registration shape changed or the file population is empty;" >&2
        echo "      the check would pass having verified nothing." >&2
        return 1
    fi

    local missing="" projects p names found nl=$'\n' line proj svc name g
    projects="$(printf '%s\n' "$rows" | cut -f1 | sort -u)"
    while IFS= read -r p; do
        [[ -z "$p" ]] && continue
        names="$(printf '%s\n' "$rows" | awk -F'\t' -v p="$p" '$1 == p { print $3 }' | sort -u)"
        found="$(documented_names "$p" "$names" "$root")"
        while IFS=$'\t' read -r proj svc name; do
            [[ "$proj" != "$p" ]] && continue
            g="${name//./_}"
            case "${nl}${found}${nl}" in
                *"${nl}${name}${nl}"*|*"${nl}${g}${nl}"*) ;;
                *) missing="${missing}  projects/${proj}  ${svc}  ${name}${nl}" ;;
            esac
        done <<< "$rows"
    done <<< "$projects"

    if [[ -n "$missing" ]]; then
        echo "FAIL: metric(s) registered under a literal name but documented nowhere in that project's specs/:" >&2
        printf '%s' "$missing" >&2
        echo >&2
        echo "  Add each to the service's spec (observability.md if it has one): name, type, tags, meaning." >&2
        echo "  platform/observability.md § Business Metrics / § Change Rule — document BEFORE implementing." >&2
        return 1
    fi

    if [[ "$quiet" != "quiet" ]]; then
        local n s
        n="$(printf '%s\n' "$rows" | wc -l | tr -d ' ')"
        s="$(printf '%s\n' "$rows" | cut -f1,2 | sort -u | wc -l | tr -d ' ')"
        echo "check-metric-name-registry: OK — ${n} literal metric name(s) across ${s} service(s), each documented in its project's specs/."
    fi
    return 0
}

# ---------------------------------------------------------------------------
# self-test — a git repo built from COPIES of the real files the predicate reads
# (the meter-registering sources + every projects/*/specs file), then mutated.
# ---------------------------------------------------------------------------
self_test() {
    local pass=0 fail=0 tmp
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' RETURN

    local base="$tmp/base"
    mkdir -p "$base"
    # the inputs: every java file under src/main that registers a literal meter, and all project specs
    # `cp --parents` in one batch, not a per-file loop: a mkdir+cp per file forks
    # hundreds of processes, which msys (the dev host) does not survive reliably.
    (cd "$ROOT" && {
        git ls-files -z -- ':(glob)projects/*/apps/*/src/main/**/*.java' \
            | xargs -0 -r grep -lZE '(builder|counter|timer|gauge|summary)\("' || true
        git ls-files -z -- ':(glob)projects/*/specs/**'
    } | xargs -0 -r cp --parents -t "$base")
    git -C "$base" init -q
    git -C "$base" add -A

    clone() {  # clone <name> -> dir
        local d="$tmp/$1"; cp -r "$base" "$d"; echo "$d"
    }
    restage() { git -C "$1" add -A >/dev/null 2>&1; }
    expect() {  # expect <label> <want> <dir>
        local got=0
        run_check "$3" quiet >/dev/null 2>&1 || got=$?
        if [[ "$got" == "$2" ]]; then echo "  PASS  $1 (rc=$got)"; pass=$((pass + 1))
        else echo "  FAIL  $1 (want rc=$2, got rc=$got)"; fail=$((fail + 1)); fi
    }

    echo "self-test: predicate against a mutated copy of the real inputs"

    local rc0=0
    bash "$ROOT/scripts/check-metric-name-registry.sh" >/dev/null 2>&1 || rc0=$?
    if [[ "$rc0" == 0 ]]; then echo "  PASS  real entry point, separate process (rc=0)"; pass=$((pass + 1))
    else echo "  FAIL  real entry point, separate process (rc=$rc0)"; fail=$((fail + 1)); fi

    expect "unmutated copy passes" 0 "$base"

    # pick one real (project, service, name) to mutate around
    local row p s n svcdir doc d
    row="$(literal_metrics "$base" | head -1)"
    IFS=$'\t' read -r p s n <<< "$row"
    svcdir="projects/$p/apps/$s/src/main/java"
    doc="$(cd "$base" && git ls-files -- "projects/$p/specs" | xargs grep -lF -e "$n" -e "${n//./_}" | head -1)"
    if [[ -z "$doc" ]]; then
        echo "  FAIL  setup: could not find the doc file for $p/$s $n"; fail=$((fail + 1))
    fi

    # 1. the documenting mention removed → bites
    d="$(clone undocumented)"
    sed -i -e "s/${n//./\\.}/REMOVED_NAME/g" -e "s/${n//./_}/REMOVED_NAME/g" "$d/$doc"
    restage "$d"
    if grep -qF -e "$n" -e "${n//./_}" "$d/$doc"; then
        echo "  FAIL  injection check: name still present in $doc"; fail=$((fail + 1))
    fi
    expect "name removed from its project specs bites" 1 "$d"

    # 2. a new literal metric nobody documented → bites
    d="$(clone new_metric)"
    mkdir -p "$d/$svcdir/selftest"
    printf 'class SelfTestMeter { void f(io.micrometer.core.instrument.MeterRegistry r) { r.counter("selftest_brand_new_total"); } }\n' > "$d/$svcdir/selftest/SelfTestMeter.java"
    restage "$d"
    expect "new undocumented literal metric bites" 1 "$d"

    # 3. same, documented in the SAME project's specs → passes
    d="$(clone new_metric_documented)"
    mkdir -p "$d/$svcdir/selftest"
    printf 'class SelfTestMeter { void f(io.micrometer.core.instrument.MeterRegistry r) { r.counter("selftest_brand_new_total"); } }\n' > "$d/$svcdir/selftest/SelfTestMeter.java"
    printf '\n| `selftest_brand_new_total` | Counter | - | self-test |\n' >> "$d/$doc"
    restage "$d"
    expect "new metric documented in its project passes" 0 "$d"

    # 4. documented only in ANOTHER project's specs → still bites
    local other
    other="$(cd "$d" && git ls-files -- ':(glob)projects/*/specs/**' | grep -v "^projects/$p/" | head -1)"
    d="$(clone other_project)"
    mkdir -p "$d/$svcdir/selftest"
    printf 'class SelfTestMeter { void f(io.micrometer.core.instrument.MeterRegistry r) { r.counter("selftest_brand_new_total"); } }\n' > "$d/$svcdir/selftest/SelfTestMeter.java"
    printf '\nselftest_brand_new_total\n' >> "$d/$other"
    restage "$d"
    expect "documented only in another project bites" 1 "$d"

    # 5. mentioned only in tasks/ → still bites
    d="$(clone tasks_only)"
    mkdir -p "$d/$svcdir/selftest" "$d/tasks/ready"
    printf 'class SelfTestMeter { void f(io.micrometer.core.instrument.MeterRegistry r) { r.counter("selftest_brand_new_total"); } }\n' > "$d/$svcdir/selftest/SelfTestMeter.java"
    printf 'selftest_brand_new_total\n' > "$d/tasks/ready/TASK-X.md"
    restage "$d"
    expect "mentioned only in tasks/ bites" 1 "$d"

    # 6. Prometheus spelling (dots → underscores) counts as documented
    d="$(clone prom_spelling)"
    mkdir -p "$d/$svcdir/selftest"
    printf 'class SelfTestMeter { void f(io.micrometer.core.instrument.MeterRegistry r) { r.counter("selftest.dotted.total"); } }\n' > "$d/$svcdir/selftest/SelfTestMeter.java"
    printf '\n`selftest_dotted_total`\n' >> "$d/$doc"
    restage "$d"
    expect "dotted name documented in its _ spelling passes" 0 "$d"

    # 7. a commented-out registration is not a registration
    d="$(clone commented)"
    mkdir -p "$d/$svcdir/selftest"
    printf 'class SelfTestMeter {\n  // r.counter("selftest_commented_total");\n  /**\n   * r.counter("selftest_javadoc_total");\n   */\n}\n' > "$d/$svcdir/selftest/SelfTestMeter.java"
    restage "$d"
    expect "registrations in // and javadoc comments are ignored" 0 "$d"

    # 8. an unstaged file is invisible (index population) — documents the contract
    d="$(clone unstaged)"
    mkdir -p "$d/$svcdir/selftest"
    printf 'class SelfTestMeter { void f(io.micrometer.core.instrument.MeterRegistry r) { r.counter("selftest_unstaged_total"); } }\n' > "$d/$svcdir/selftest/SelfTestMeter.java"
    expect "an unstaged source is not in the population (stage first)" 0 "$d"

    # 9. empty population fails closed
    d="$tmp/empty"; mkdir -p "$d"; git -C "$d" init -q
    expect "zero registrations fails closed" 1 "$d"

    echo "self-test: ${pass} passed, ${fail} failed"
    [[ "$fail" -eq 0 ]]
}

case "${1:-}" in
    --self-test) self_test ;;
    -h|--help)   usage ;;
    "")          run_check "$ROOT" ;;
    *)           usage >&2; exit 2 ;;
esac
