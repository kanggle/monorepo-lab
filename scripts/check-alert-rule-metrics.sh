#!/usr/bin/env bash
# =============================================================================
# check-alert-rule-metrics.sh — every metric a shipped alert rule queries is one
# that something actually exports
# TASK-MONO-834
# =============================================================================
# WHAT THIS GUARDS
#
# projects/<p>/infra/prometheus/alert-rules.yml is loaded by that project's
# Prometheus (`rule_files`). An alert whose `expr` names a metric nobody exports
# evaluates to no data, forever: it cannot fire, and a broken alert looks exactly
# like a healthy system. Measured 2026-11-24 UTC: five iam alerts queried four
# metric names that no code registered (the spec required them — TASK-BE-662
# implemented them), and the wms rules file was a verbatim copy of iam's, alerting
# on services wms does not have while having no alert for any wms service.
#
# PREDICATE
#   population = metric names referenced by `expr:` in every tracked
#                projects/<p>/infra/prometheus/alert-rules.yml. Parsed by removing
#                string literals, label matchers `{…}`, ranges `[…]`, and the
#                parenthesised label lists of by / without / on / ignoring /
#                group_left / group_right; what remains is identifiers, minus
#                PromQL keywords and anything called like a function `name(`.
#   a name PASSES if
#     (a) after `.`→`_`, it equals a string literal in that project's
#         apps/*/src/main/**/*.java or in libs/*/src/main/**/*.java — directly, or
#         once one Prometheus suffix (_total _count _sum _bucket _max) and then an
#         optional `_seconds` are stripped; or it is a `record:` name in the same
#         project's rules; or
#     (b) it starts with one of FRAMEWORK_PREFIXES (Micrometer / Spring Boot /
#         client auto-instrumentation, and Prometheus's own `up`).
#   FAIL CLOSED when no rules file or no expr is found.
#
# DELIBERATELY LOOSE ON (a): any string literal counts, so a log message that
# happens to contain the name passes too. That direction misses rather than
# bites wrongly; constants count, because a constant's value is a literal.
#
# WHAT THIS DOES *NOT* GUARD
#   * Label selectors ({service="…"}) matching real series labels — names only.
#   * Names chosen by regex on __name__ — invisible to any name check. Rules here
#     spell names out instead.
#   * Runbook links in annotations.
#
# Population is read from the INDEX (git ls-files): stage before running locally.
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Exported by frameworks, not by repo code. Keep entries to whole name words so a
# typo of a framework metric still reads as unknown rather than as a prefix match.
FRAMEWORK_PREFIXES=(
    jvm_ process_ system_ http_server_requests_ http_client_requests_ hikaricp_
    kafka_consumer_ kafka_producer_ spring_ resilience4j_ tomcat_ logback_
    executor_ disk_ lettuce_
)
FRAMEWORK_EXACT=(up)

usage() {
    cat <<'EOF'
usage: check-alert-rule-metrics.sh [--self-test]

  (no args)    Check this repository.
  --self-test  Run the predicate against mutated copies of the real inputs.
EOF
}

rules_files() {  # tracked rules files, one per line
    git -C "$1" ls-files -- ':(glob)projects/*/infra/prometheus/alert-rules.yml' || true
}

# project<TAB>alert<TAB>metric — from one rules file (CR stripped).
rule_metrics() {
    local root="$1" file="$2" project
    project="$(printf '%s' "$file" | cut -d/ -f2)"
    tr -d '\r' < "$root/$file" | awk -v proj="$project" '
        function flush(   e, n, i, t, rest, ch) {
            if (expr == "") return
            e = expr
            gsub(/"([^"\\]|\\.)*"/, " ", e)          # string literals
            gsub(/\x27([^\x27\\]|\\.)*\x27/, " ", e)
            while (gsub(/\{[^{}]*\}/, " ", e)) {}       # label matchers
            while (gsub(/\[[^][]*\]/, " ", e)) {}       # ranges / subqueries
            # label lists: by (...), without (...), on (...), ignoring (...), group_left (...), group_right (...)
            while (match(e, /(by|without|on|ignoring|group_left|group_right)[ \t]*\([^()]*\)/)) {
                e = substr(e, 1, RSTART - 1) " " substr(e, RSTART + RLENGTH)
            }
            rest = e
            while (match(rest, /[A-Za-z_:][A-Za-z0-9_:]*/)) {
                t = substr(rest, RSTART, RLENGTH)
                after = substr(rest, RSTART + RLENGTH)
                rest = after
                sub(/^[ \t]*/, "", after)
                if (substr(after, 1, 1) == "(") continue        # function call
                if (t ~ /^(and|or|unless|bool|offset|by|without|on|ignoring|group_left|group_right|inf|nan|Inf|NaN)$/) continue
                print proj "\t" alert "\t" t
            }
            expr = ""
        }
        {
            line = $0
            if (inblock) {
                ind = match(line, /[^ ]/) ? RSTART - 1 : 999
                if (line ~ /^[ \t]*$/ || ind > blockind) { expr = expr " " line; next }
                inblock = 0; flush()
            }
            if (match(line, /^[ \t]*-?[ \t]*(alert|record):[ \t]*/)) {
                kind = line; sub(/^[ \t]*-?[ \t]*/, "", kind); sub(/:.*/, "", kind)
                name = substr(line, RSTART + RLENGTH); gsub(/["\x27 \t]/, "", name)
                if (kind == "alert") alert = name; else { alert = "(record " name ")"; print proj "\t#record\t" name }
                next
            }
            # TASK-MONO-835: runbook annotations — emitted as "#runbook <alert>|<path>".
            if (match(line, /^[ \t]*runbook:[ \t]*/)) {
                rb = substr(line, RSTART + RLENGTH); gsub(/["\x27]/, "", rb); gsub(/^[ \t]+|[ \t]+$/, "", rb)
                if (rb != "") print proj "\t#runbook\t" alert "|" rb
                next
            }
            if (match(line, /^[ \t]*expr:[ \t]*/)) {
                rest = substr(line, RSTART + RLENGTH)
                if (rest ~ /^[|>][-+]?[ \t]*$/) {
                    inblock = 1; blockind = match(line, /[^ ]/) ? RSTART - 1 : 0; expr = ""
                } else { expr = rest; flush() }
            }
        }
        END { if (inblock) flush() }'
}

# Normalised string literals (one per line) from a project's sources + libs.
defined_names() {
    local root="$1" project="$2"
    { git -C "$root" ls-files -z -- \
          ":(glob)projects/$project/apps/*/src/main/**/*.java" \
          ':(glob)libs/*/src/main/**/*.java' \
        | (cd "$root" && xargs -0 -r grep -ohE '"[A-Za-z][A-Za-z0-9_.]*"') \
        | tr -d '"' | tr '.' '_' | sort -u; } || true
}

# Normalised names that are actually HANDED TO A METER REGISTRATION — either as a literal
# (`Counter.builder("x")`, `registry.counter("x"…)`) or as a constant whose `= "x"` value is
# then looked up. TASK-MONO-835: a suffix-stripped match (`foo_total` → `foo`) is only
# trusted against THIS set. Against every string literal it was wrong: the Kafka topic
# "auth.token.reuse.detected" normalised to the base of `auth_token_reuse_detected_total`, so
# two alerts on metrics nothing exported passed as "exported".
registered_names() {
    local root="$1" project="$2" files reg='((Counter|Timer|Gauge|DistributionSummary|LongTaskTimer|FunctionCounter|FunctionTimer)[.]builder|[.](counter|timer|gauge|summary))[(][[:space:]]*'
    files="$(git -C "$root" ls-files -- \
          ":(glob)projects/$project/apps/*/src/main/**/*.java" \
          ':(glob)libs/*/src/main/**/*.java' 2>/dev/null || true)"
    [[ -z "$files" ]] && return 0
    local idents
    idents="$( { printf '%s\n' "$files" | (cd "$root" && tr '\n' '\0' | xargs -0 -r grep -ohE "${reg}[A-Z][A-Z0-9_]*[[:space:]]*[,)]") \
                 | sed -E "s/.*[(][[:space:]]*//; s/[[:space:]]*[,)]\$//" | sort -u; } || true)"
    {
        printf '%s\n' "$files" | (cd "$root" && tr '\n' '\0' | xargs -0 -r grep -ohE "${reg}\"[A-Za-z][A-Za-z0-9_.]*\"") \
            | grep -oE '"[^"]+"' || true
        if [[ -n "$idents" ]]; then
            printf '%s\n' "$files" | (cd "$root" && tr '\n' '\0' | xargs -0 -r grep -ohE \
                "\\b($(printf '%s' "$idents" | paste -sd'|' -))[[:space:]]*=[[:space:]]*\"[A-Za-z][A-Za-z0-9_.]*\"") \
                | grep -oE '"[^"]+"' || true
        fi
    } | tr -d '"' | tr '.' '_' | sort -u
}

is_framework() {
    local m="$1" p
    for p in "${FRAMEWORK_EXACT[@]}"; do [[ "$m" == "$p" ]] && return 0; done
    for p in "${FRAMEWORK_PREFIXES[@]}"; do [[ "$m" == "$p"* ]] && return 0; done
    return 1
}

run_check() {
    local root="$1" quiet="${2:-}"
    local files rows="" f
    files="$(rules_files "$root")"
    if [[ -z "$files" ]]; then
        echo "FAIL: no tracked projects/*/infra/prometheus/alert-rules.yml — nothing to verify." >&2
        return 1
    fi
    while IFS= read -r f; do
        [[ -z "$f" ]] && continue
        rows="${rows}$(rule_metrics "$root" "$f")"$'\n'
    done <<< "$files"
    if [[ -z "$(printf '%s' "$rows" | awk -F'\t' '$2 != "#record" && $2 != "#runbook"' | tr -d '[:space:]')" ]]; then
        echo "FAIL: parsed ZERO metric references from the alert rules — the parser or the files changed." >&2
        return 1
    fi

    local missing="" nl=$'\n' p defined registered recs proj alert m base hit n_refs=0
    for p in $(printf '%s' "$rows" | cut -f1 | sort -u); do
        defined="$(defined_names "$root" "$p")"
        registered="$(registered_names "$root" "$p")"
        recs="$(printf '%s' "$rows" | awk -F'\t' -v p="$p" '$1 == p && $2 == "#record" { print $3 }')"
        defined="${defined}${nl}${recs}"
        while IFS=$'\t' read -r proj alert m; do
            [[ "$proj" != "$p" || "$alert" == "#record" || "$alert" == "#runbook" || -z "$m" ]] && continue
            n_refs=$((n_refs + 1))
            if is_framework "$m"; then continue; fi
            hit=0
            # exact name: any string literal (a constant's value is a literal)
            case "${nl}${defined}${nl}" in *"${nl}${m}${nl}"*) hit=1 ;; esac
            # suffix-stripped name: only a name handed to a meter registration (TASK-MONO-835)
            if [[ "$hit" == 0 ]]; then
                for base in "$(printf '%s' "$m" | sed -E 's/_(total|count|sum|bucket|max)$//')" \
                            "$(printf '%s' "$m" | sed -E 's/_(total|count|sum|bucket|max)$//; s/_seconds$//')"; do
                    case "${nl}${registered}${nl}" in *"${nl}${base}${nl}"*) hit=1; break ;; esac
                done
            fi
            if [[ "$hit" == 0 ]]; then
                missing="${missing}  projects/${proj}  ${alert}  ${m}${nl}"
            fi
        done <<< "$rows"
    done

    # TASK-MONO-835: a runbook link that leads nowhere is the next thing an on-call reader
    # hits after the alert fires. Paths are relative to the project root; the file must be
    # tracked (index population, like everything else here).
    local tracked dead="" n_rb=0 rest rb_alert rb_path
    tracked="$(git -C "$root" ls-files -- ':(glob)projects/*/**' 2>/dev/null || true)"
    while IFS=$'\t' read -r proj m rest; do
        [[ "$m" != "#runbook" ]] && continue
        n_rb=$((n_rb + 1))
        rb_alert="${rest%%|*}"; rb_path="${rest#*|}"
        case "${nl}${tracked}${nl}" in
            *"${nl}projects/${proj}/${rb_path}${nl}"*) ;;
            *) dead="${dead}  projects/${proj}  ${rb_alert}  ${rb_path}${nl}" ;;
        esac
    done <<< "$rows"

    if [[ -n "$dead" ]]; then
        echo "FAIL: alert runbook link(s) point at a file that does not exist:" >&2
        printf '%s' "$dead" >&2
        echo >&2
        echo "  The path is relative to the project root (projects/<p>/). Write the runbook" >&2
        echo "  (knowledge/runbooks/, see that project's README there) or fix the link." >&2
        return 1
    fi

    if [[ -n "$missing" ]]; then
        echo "FAIL: alert rule(s) query a metric nothing exports — they can never fire:" >&2
        printf '%s' "$(printf '%s' "$missing" | sort -u)" >&2; echo >&2
        echo >&2
        echo "  Either the code must register it (with the name the spec gives), or the rule must" >&2
        echo "  query a metric that exists. A framework metric not covered here belongs in" >&2
        echo "  FRAMEWORK_PREFIXES at the top of this script — say which library exports it." >&2
        return 1
    fi
    if [[ "$quiet" != "quiet" ]]; then
        local nf
        nf="$(printf '%s\n' "$files" | grep -c .)"
        echo "check-alert-rule-metrics: OK — ${n_refs} metric reference(s) in ${nf} rules file(s), all exported by code, libs, a record rule, or a framework; ${n_rb} runbook link(s), all to tracked files."
    fi
    return 0
}

# ---------------------------------------------------------------------------
# self-test — a git repo holding COPIES of the real rules files, plus, per
# project, one source file carrying every string literal the real sources and
# libs contain (extracted from the real tree, not invented), then mutated.
# ---------------------------------------------------------------------------
self_test() {
    local pass=0 fail=0 tmp
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' RETURN
    local base="$tmp/base" f p
    mkdir -p "$base"
    (cd "$ROOT" && git ls-files -z -- ':(glob)projects/*/infra/prometheus/alert-rules.yml' \
            ':(glob)projects/*/knowledge/runbooks/**' \
        | xargs -0 -r cp --parents -t "$base")
    for p in $(cd "$base" && ls projects); do
        mkdir -p "$base/projects/$p/apps/real/src/main/java"
        { (cd "$ROOT" && git ls-files -z -- ":(glob)projects/$p/apps/*/src/main/**/*.java" \
              | xargs -0 -r grep -ohE '"[A-Za-z][A-Za-z0-9_.]*"') || true; } | sort -u \
          | awk 'BEGIN { print "class RealLiterals {" } { print "  String s" NR " = " $0 ";" } END { print "}" }' \
          > "$base/projects/$p/apps/real/src/main/java/RealLiterals.java"
    done
    mkdir -p "$base/libs/real/src/main/java"
    { (cd "$ROOT" && git ls-files -z -- ':(glob)libs/*/src/main/**/*.java' \
          | xargs -0 -r grep -ohE '"[A-Za-z][A-Za-z0-9_.]*"') || true; } | sort -u \
      | awk 'BEGIN { print "class LibLiterals {" } { print "  String s" NR " = " $0 ";" } END { print "}" }' \
      > "$base/libs/real/src/main/java/LibLiterals.java"
    git -C "$base" init -q; git -C "$base" config core.autocrlf false; git -C "$base" add -A

    clone() { local d="$tmp/$1"; cp -r "$base" "$d"; echo "$d"; }
    restage() { git -C "$1" add -A >/dev/null 2>&1; }
    expect() {
        local got=0
        run_check "$3" quiet >/dev/null 2>&1 || got=$?
        if [[ "$got" == "$2" ]]; then echo "  PASS  $1 (rc=$got)"; pass=$((pass + 1))
        else echo "  FAIL  $1 (want rc=$2, got rc=$got)"; fail=$((fail + 1)); fi
    }
    # append one rule to a project's rules file (first group)
    add_rule() {  # add_rule <dir> <project> <expr>
        printf '\n  - name: selftest.rules\n    rules:\n      - alert: SelfTestAlert\n        expr: %s\n' "$3" \
            >> "$1/projects/$2/infra/prometheus/alert-rules.yml"
    }

    echo "self-test: predicate against mutated copies of the real inputs"
    local rc0=0
    bash "$ROOT/scripts/check-alert-rule-metrics.sh" >/dev/null 2>&1 || rc0=$?
    if [[ "$rc0" == 0 ]]; then echo "  PASS  real entry point, separate process (rc=0)"; pass=$((pass + 1))
    else echo "  FAIL  real entry point, separate process (rc=$rc0)"; fail=$((fail + 1)); fi

    expect "unmutated copy passes" 0 "$base"

    local P1 P2 d
    P1="$(cd "$base" && ls projects | head -1)"
    P2="$(cd "$base" && ls projects | sed -n 2p)"

    d="$(clone unknown)"; add_rule "$d" "$P1" 'rate(selftest_nobody_exports_total[5m]) > 0'; restage "$d"
    expect "unknown metric bites" 1 "$d"

    d="$(clone labels)"; add_rule "$d" "$P1" 'sum(rate(http_server_requests_seconds_count{service="x", job="y"}[5m])) by (service, job, le) > 1'; restage "$d"
    expect "label names in {…} and by (…) are not metrics" 0 "$d"

    d="$(clone framework)"; add_rule "$d" "$P1" 'jvm_memory_used_bytes > 1 and up == 0'; restage "$d"
    expect "framework prefix and up pass" 0 "$d"

    d="$(clone typo_framework)"; add_rule "$d" "$P1" 'http_servr_requests_seconds_count > 1'; restage "$d"
    expect "misspelled framework metric bites" 1 "$d"

    d="$(clone code_dotted)"
    mkdir -p "$d/projects/$P1/apps/x/src/main/java"
    printf 'class M { Object c = io.micrometer.core.instrument.Counter.builder("selftest.dotted.total"); }\n' > "$d/projects/$P1/apps/x/src/main/java/M.java"
    add_rule "$d" "$P1" 'increase(selftest_dotted_total[10m]) > 0'; restage "$d"
    expect "dotted code name matches its _ spelling" 0 "$d"

    d="$(clone timer_suffix)"
    mkdir -p "$d/projects/$P1/apps/x/src/main/java"
    printf 'class M { Object t = io.micrometer.core.instrument.Timer.builder("selftest.op"); }\n' > "$d/projects/$P1/apps/x/src/main/java/M.java"
    add_rule "$d" "$P1" 'histogram_quantile(0.99, sum(rate(selftest_op_seconds_bucket[5m])) by (le)) > 1'; restage "$d"
    expect "timer exported as _seconds_bucket matches its base name" 0 "$d"

    d="$(clone libs_name)"
    mkdir -p "$d/libs/x/src/main/java"
    printf 'class L { String n = "selftest_lib_metric_total"; }\n' > "$d/libs/x/src/main/java/L.java"
    add_rule "$d" "$P1" 'increase(selftest_lib_metric_total[5m]) > 0'; restage "$d"
    expect "a name exported from libs/ passes" 0 "$d"

    if [[ -n "$P2" ]]; then
        d="$(clone other_project)"
        mkdir -p "$d/projects/$P2/apps/x/src/main/java"
        printf 'class M { String n = "selftest_other_project_total"; }\n' > "$d/projects/$P2/apps/x/src/main/java/M.java"
        add_rule "$d" "$P1" 'increase(selftest_other_project_total[5m]) > 0'; restage "$d"
        expect "a name only another project exports bites" 1 "$d"
    fi

    d="$(clone record)"
    printf '\n  - name: selftest.records\n    rules:\n      - record: selftest:derived:rate5m\n        expr: rate(http_server_requests_seconds_count[5m])\n      - alert: SelfTestOnRecord\n        expr: selftest:derived:rate5m > 1\n' \
        >> "$d/projects/$P1/infra/prometheus/alert-rules.yml"; restage "$d"
    expect "a record: rule name counts as defined" 0 "$d"

    d="$(clone block)"
    printf '\n  - name: selftest.block\n    rules:\n      - alert: SelfTestBlock\n        expr: |\n          sum(rate(selftest_block_only_total[5m]))\n            > 0\n        for: 5m\n' \
        >> "$d/projects/$P1/infra/prometheus/alert-rules.yml"; restage "$d"
    expect "a name inside an expr: | block bites" 1 "$d"

    # TASK-MONO-835 — a plain string (a Kafka topic) must not satisfy a suffix-stripped match;
    # the same base handed to Counter.builder must.
    d="$(clone topic_not_metric)"
    mkdir -p "$d/projects/$P1/apps/x/src/main/java"
    printf 'class T { static final String TOPIC = "selftest.thing.detected"; }\n' > "$d/projects/$P1/apps/x/src/main/java/T.java"
    add_rule "$d" "$P1" 'increase(selftest_thing_detected_total[5m]) > 0'; restage "$d"
    expect "a topic-name literal does not count as the metric selftest_thing_detected_total" 1 "$d"

    d="$(clone counter_constant)"
    mkdir -p "$d/projects/$P1/apps/x/src/main/java"
    printf 'class C { static final String NAME = "selftest.thing.counted"; Object c = io.micrometer.core.instrument.Counter.builder(NAME); }\n' > "$d/projects/$P1/apps/x/src/main/java/C.java"
    add_rule "$d" "$P1" 'increase(selftest_thing_counted_total[5m]) > 0'; restage "$d"
    expect "a Counter registered via a constant matches its _total spelling" 0 "$d"

    # TASK-MONO-835 — runbook links
    d="$(clone runbook_dead)"
    printf '\n  - name: selftest.runbook\n    rules:\n      - alert: SelfTestRunbook\n        expr: up == 0\n        annotations:\n          runbook: "knowledge/runbooks/selftest-does-not-exist.md"\n' \
        >> "$d/projects/$P1/infra/prometheus/alert-rules.yml"; restage "$d"
    expect "a runbook link to a missing file bites" 1 "$d"

    d="$(clone runbook_ok)"
    mkdir -p "$d/projects/$P1/knowledge/runbooks"
    printf '# selftest\n' > "$d/projects/$P1/knowledge/runbooks/selftest-exists.md"
    printf '\n  - name: selftest.runbook\n    rules:\n      - alert: SelfTestRunbook\n        expr: up == 0\n        annotations:\n          runbook: "knowledge/runbooks/selftest-exists.md"  \n' \
        >> "$d/projects/$P1/infra/prometheus/alert-rules.yml"; restage "$d"
    expect "a quoted runbook link to a tracked file passes (quotes/trailing space stripped)" 0 "$d"

    d="$(clone runbook_unstaged)"
    mkdir -p "$d/projects/$P1/knowledge/runbooks"
    printf '# selftest\n' > "$d/projects/$P1/knowledge/runbooks/selftest-unstaged.md"
    printf '\n  - name: selftest.runbook\n    rules:\n      - alert: SelfTestRunbook\n        expr: up == 0\n        annotations:\n          runbook: knowledge/runbooks/selftest-unstaged.md\n' \
        >> "$d/projects/$P1/infra/prometheus/alert-rules.yml"
    git -C "$d" add -- "projects/$P1/infra/prometheus/alert-rules.yml" >/dev/null 2>&1
    expect "a runbook that exists on disk but is not tracked bites" 1 "$d"

    d="$tmp/empty"; mkdir -p "$d"; git -C "$d" init -q
    expect "no rules file fails closed" 1 "$d"

    echo "self-test: ${pass} passed, ${fail} failed"
    [[ "$fail" -eq 0 ]]
}

case "${1:-}" in
    --self-test) self_test ;;
    -h|--help)   usage ;;
    "")          run_check "$ROOT" ;;
    *)           usage >&2; exit 2 ;;
esac
