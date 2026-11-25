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
# TASK-MONO-839 — DASHBOARDS TOO. Every `expr` in a tracked
# projects/<p>/infra/grafana/dashboards/**/*.json goes through the same parser and the
# same predicate (JSON read by node, so escaped label values arrive unescaped). Measured
# 2026-11-25 UTC: iam dashboards queried 20 names nothing exported, ecommerce 1. A panel on
# a missing metric renders empty, which reads as "nothing happened", not "broken".
#   The one exemption: projects/<p>/infra/grafana/pending-metrics.txt lists names the
#   project's SPEC declares whose implementation is a pending owner decision
#   (TASK-MONO-837). It applies to dashboards only — never to an alert rule — and an
#   entry FAILS the check once it is exported (retract it) or once no dashboard queries
#   it (stale). `-` in a registered name normalises to `_`, as Prometheus does.
#
# DELIBERATELY LOOSE ON (a): any string literal counts, so a log message that
# happens to contain the name passes too. That direction misses rather than
# bites wrongly; constants count, because a constant's value is a literal.
#
# WHAT THIS DOES *NOT* GUARD
#   * Label selectors ({service="…"}) matching real series labels — names only.
#   * Names chosen by regex on __name__ — invisible to any name check. Rules here
#     spell names out instead.
#   * Grafana datasource uids (scripts/check-grafana-datasource-uid.mjs, TASK-MONO-838).
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
# `ALERTS` / `ALERTS_FOR_STATE` are series Prometheus itself synthesises from its rules
# (the dashboards' alert overview panels read them — TASK-MONO-839).
FRAMEWORK_EXACT=(up ALERTS ALERTS_FOR_STATE)

usage() {
    cat <<'EOF'
usage: check-alert-rule-metrics.sh [--self-test | --unexported <project>]

  (no args)    Check this repository.
  --self-test  Run the predicate against mutated copies of the real inputs.
  --unexported <project>  Print the stdin names (one per line) that project does NOT export.
EOF
}

rules_files() {  # tracked rules files, one per line
    git -C "$1" ls-files -- ':(glob)projects/*/infra/prometheus/alert-rules.yml' || true
}

# project<TAB>alert<TAB>metric — from one rules file (CR stripped).
rule_metrics() {
    local root="$1" file="$2" project
    project="$(printf '%s' "$file" | cut -d/ -f2)"
    tr -d '\r' < "$root/$file" | parse_rules "$project"
}

# TASK-MONO-839 — tracked Grafana dashboards, one per line.
dashboard_files() {
    git -C "$1" ls-files -- ':(glob)projects/*/infra/grafana/dashboards/**/*.json' || true
}

# project<TAB>dashboard:<file><TAB>metric — every `expr` string in a project's dashboards, fed
# through the SAME parser as the rules files (one predicate, not two copies). JSON is read
# by node so an escaped label value (`{service=\"x\"}`) arrives unescaped — read raw, the
# backslashes would split the string literal and leak label values in as metric names.
# One node process per PROJECT (not per file): the self-test re-runs this ~25 times.
dashboard_metrics() {  # dashboard_metrics <root> <project> <file>...
    local root="$1" project="$2"
    shift 2
    node -e '
        const fs = require("fs");
        const [root, ...files] = process.argv.slice(1);
        const out = [];
        for (const f of files) {
            const tag = "dashboard:" + f.split("/").pop();
            const d = JSON.parse(fs.readFileSync(root + "/" + f, "utf8"));
            (function walk(o) {
                if (Array.isArray(o)) { o.forEach(walk); return; }
                if (o && typeof o === "object") {
                    if (typeof o.expr === "string" && o.expr.trim() !== "")
                        out.push("- alert: " + tag + "\n  expr: " + o.expr.replace(/[\r\n]+/g, " "));
                    Object.values(o).forEach(walk);
                }
            })(d);
        }
        process.stdout.write(out.join("\n") + (out.length ? "\n" : ""));
    ' "$root" "$@" | parse_rules "$project"
}

# TASK-MONO-839 — names a project's dashboards may query although nothing exports them YET:
# declared by that project's spec, decision pending (TASK-MONO-837 D1/D2). One per line,
# `#` comments. Never applies to alert rules — an alert on a pending metric is still a dead alert.
pending_names() {  # pending_names <root> <project>
    local f="$1/projects/$2/infra/grafana/pending-metrics.txt"
    git -C "$1" ls-files --error-unmatch -- "projects/$2/infra/grafana/pending-metrics.txt" >/dev/null 2>&1 || return 0
    { tr -d '\r' < "$f" | sed -E 's/#.*$//; s/^[[:space:]]+//; s/[[:space:]]+$//' | grep -v '^$' | sort -u; } || true
}

# project<TAB>alert<TAB>metric from rules text on stdin.
parse_rules() {
    awk -v proj="$1" '
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
        | (cd "$root" && xargs -0 -r grep -ohE '"[A-Za-z][A-Za-z0-9_.-]*"') \
        | tr -d '"' | tr '.-' '__' | sort -u; } || true
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
        printf '%s\n' "$files" | (cd "$root" && tr '\n' '\0' | xargs -0 -r grep -ohE "${reg}\"[A-Za-z][A-Za-z0-9_.-]*\"") \
            | grep -oE '"[^"]+"' || true
        if [[ -n "$idents" ]]; then
            printf '%s\n' "$files" | (cd "$root" && tr '\n' '\0' | xargs -0 -r grep -ohE \
                "\\b($(printf '%s' "$idents" | paste -sd'|' -))[[:space:]]*=[[:space:]]*\"[A-Za-z][A-Za-z0-9_.-]*\"") \
                | grep -oE '"[^"]+"' || true
        fi
    } | tr -d '"' | tr '.-' '__' | sort -u
}

is_framework() {
    local m="$1" p
    for p in "${FRAMEWORK_EXACT[@]}"; do [[ "$m" == "$p" ]] && return 0; done
    for p in "${FRAMEWORK_PREFIXES[@]}"; do [[ "$m" == "$p"* ]] && return 0; done
    return 1
}

# exported <name> — THE predicate (alerts, dashboards, pending entries, and — TASK-MONO-841 —
# spec rows via --unexported). Reads the caller's `defined`, `registered` and `nl` (bash dynamic
# scope): run_check and unexported_mode each set them for one project before calling it.
exported() {
    local m="$1" base
    is_framework "$m" && return 0
    # exact name: any string literal (a constant's value is a literal)
    case "${nl}${defined}${nl}" in *"${nl}${m}${nl}"*) return 0 ;; esac
    # suffix-stripped name: only a name handed to a meter registration (TASK-MONO-835)
    for base in "$(printf '%s' "$m" | sed -E 's/_(total|count|sum|bucket|max)$//')" \
                "$(printf '%s' "$m" | sed -E 's/_(total|count|sum|bucket|max)$//; s/_seconds$//')"; do
        case "${nl}${registered}${nl}" in *"${nl}${base}${nl}"*) return 0 ;; esac
    done
    return 1
}

# TASK-MONO-841 — `--unexported <project>`: names on stdin (one per line, `.`/`-` spellings
# allowed); prints the ones `exported` rejects for that project, in their original spelling.
# Lets scripts/check-spec-metrics-implemented.mjs judge spec rows with this exact predicate
# instead of a second copy of it.
unexported_mode() {
    local root="$1" p="$2" nl=$'\n' defined registered recs rules name norm
    [[ -n "$p" && -d "$root/projects/$p" ]] || { echo "usage: --unexported <project>  (names on stdin)" >&2; return 2; }
    defined="$(defined_names "$root" "$p")"
    registered="$(registered_names "$root" "$p")"
    rules="projects/$p/infra/prometheus/alert-rules.yml"
    recs=""
    if git -C "$root" ls-files --error-unmatch -- "$rules" >/dev/null 2>&1; then
        recs="$(rule_metrics "$root" "$rules" | awk -F'\t' '$2 == "#record" { print $3 }')"
    fi
    defined="${defined}${nl}${recs}"
    while IFS= read -r name; do
        name="${name%$'\r'}"
        [[ -z "$name" ]] && continue
        norm="$(printf '%s' "$name" | tr '.-' '__')"
        # A spec row may name the Micrometer METER (`http.server.requests`), whose exported
        # series are `<name>_<suffix>` — so the bare name also passes as the base of a framework series.
        exported "$norm" || is_framework "${norm}_" || printf '%s\n' "$name"
    done
    return 0
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

    # TASK-MONO-839 — Grafana dashboards go through the same parser and the same predicate.
    local dfiles
    dfiles="$(dashboard_files "$root")"
    if [[ -n "$dfiles" ]]; then
        if ! command -v node >/dev/null 2>&1; then
            echo "FAIL: node is required to read the tracked Grafana dashboards (JSON) and is not on PATH." >&2
            return 1
        fi
        local dp
        for dp in $(printf '%s\n' "$dfiles" | cut -d/ -f2 | sort -u); do
            # shellcheck disable=SC2046 — tracked paths here never contain whitespace
            rows="${rows}$(dashboard_metrics "$root" "$dp" $(printf '%s\n' "$dfiles" | awk -F/ -v p="$dp" '$2 == p'))"$'\n'
        done
    fi

    local missing="" dmissing="" pend_bad="" nl=$'\n' p defined registered recs pending used proj alert m n_refs=0 n_drefs=0 n_pend=0
    local projects
    projects="$( { printf '%s' "$rows" | cut -f1; git -C "$root" ls-files -- ':(glob)projects/*/infra/grafana/pending-metrics.txt' | cut -d/ -f2; } | grep -v '^$' | sort -u || true)"
    for p in $projects; do
        defined="$(defined_names "$root" "$p")"
        registered="$(registered_names "$root" "$p")"
        recs="$(printf '%s' "$rows" | awk -F'\t' -v p="$p" '$1 == p && $2 == "#record" { print $3 }')"
        defined="${defined}${nl}${recs}"
        pending="$(pending_names "$root" "$p")"
        used=""
        while IFS=$'\t' read -r proj alert m; do
            [[ "$proj" != "$p" || "$alert" == "#record" || "$alert" == "#runbook" || -z "$m" ]] && continue
            if [[ "$alert" == dashboard:* ]]; then n_drefs=$((n_drefs + 1)); else n_refs=$((n_refs + 1)); fi
            exported "$m" && continue
            if [[ "$alert" == dashboard:* ]]; then
                case "${nl}${pending}${nl}" in *"${nl}${m}${nl}"*) used="${used}${m}${nl}"; continue ;; esac
                dmissing="${dmissing}  projects/${proj}  ${alert#dashboard:}  ${m}${nl}"
            else
                missing="${missing}  projects/${proj}  ${alert}  ${m}${nl}"
            fi
        done <<< "$rows"
        # A pending entry must keep earning its place: still unexported, still queried.
        while IFS= read -r m; do
            [[ -z "$m" ]] && continue
            if exported "$m"; then
                pend_bad="${pend_bad}  projects/${p}  ${m}  — now EXPORTED: delete the entry (an exemption must not outlive the defect)${nl}"
            else
                case "${nl}${used}${nl}" in
                    *"${nl}${m}${nl}"*) n_pend=$((n_pend + 1)) ;;
                    *) pend_bad="${pend_bad}  projects/${p}  ${m}  — no dashboard queries it: delete the stale entry${nl}" ;;
                esac
            fi
        done <<< "$pending"
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
    if [[ -n "$dmissing" ]]; then
        echo "FAIL: Grafana dashboard panel(s) query a metric nothing exports — they render empty:" >&2
        printf '%s' "$(printf '%s' "$dmissing" | sort -u)" >&2; echo >&2
        echo >&2
        echo "  Query a metric that exists, or delete the panel. Only if the project's spec declares the" >&2
        echo "  metric and its implementation is a pending decision, list it in" >&2
        echo "  projects/<p>/infra/grafana/pending-metrics.txt with the spec location (TASK-MONO-839)." >&2
        return 1
    fi
    if [[ -n "$pend_bad" ]]; then
        echo "FAIL: pending-metrics.txt entr(y/ies) no longer justified:" >&2
        printf '%s' "$pend_bad" >&2
        return 1
    fi
    if [[ "$quiet" != "quiet" ]]; then
        local nf nd
        nf="$(printf '%s\n' "$files" | grep -c .)"
        nd="$(printf '%s\n' "$dfiles" | grep -c . || true)"
        echo "check-alert-rule-metrics: OK — ${n_refs} metric reference(s) in ${nf} rules file(s), all exported by code, libs, a record rule, or a framework; ${n_rb} runbook link(s), all to tracked files; ${n_drefs} reference(s) in ${nd} dashboard(s), all exported except ${n_pend} listed as pending."
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
            ':(glob)projects/*/infra/grafana/dashboards/**' ':(glob)projects/*/infra/grafana/pending-metrics.txt' \
        | xargs -0 -r cp --parents -t "$base")
    for p in $(cd "$base" && ls projects); do
        mkdir -p "$base/projects/$p/apps/real/src/main/java"
        { (cd "$ROOT" && git ls-files -z -- ":(glob)projects/$p/apps/*/src/main/**/*.java" \
              | xargs -0 -r grep -ohE '"[A-Za-z][A-Za-z0-9_.-]*"') || true; } | sort -u \
          | awk 'BEGIN { print "class RealLiterals {" } { print "  String s" NR " = " $0 ";" } END { print "}" }' \
          > "$base/projects/$p/apps/real/src/main/java/RealLiterals.java"
        # TASK-MONO-839: the names the real tree hands to meter registrations, re-registered,
        # so a suffix-stripped name (`_seconds_sum`, `_total`) resolves in the copy as it does live.
        registered_names "$ROOT" "$p" \
          | awk 'BEGIN { print "class RealRegistrations {" } NF { print "  Object r" NR " = io.micrometer.core.instrument.Counter.builder(\"" $0 "\");" } END { print "}" }' \
          > "$base/projects/$p/apps/real/src/main/java/RealRegistrations.java"
    done
    mkdir -p "$base/libs/real/src/main/java"
    { (cd "$ROOT" && git ls-files -z -- ':(glob)libs/*/src/main/**/*.java' \
          | xargs -0 -r grep -ohE '"[A-Za-z][A-Za-z0-9_.-]*"') || true; } | sort -u \
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

    # TASK-MONO-839 — dashboards. A dashboard file the case writes, then the predicate.
    add_dash() {  # add_dash <dir> <project> <expr-as-JSON-string-body>
        mkdir -p "$1/projects/$2/infra/grafana/dashboards"
        printf '{ "uid": "selftest", "panels": [ { "id": 1, "targets": [ { "expr": "%s", "refId": "A" } ] } ] }\n' "$3" \
            > "$1/projects/$2/infra/grafana/dashboards/selftest.json"
    }
    add_pending() {  # add_pending <dir> <project> <name>
        mkdir -p "$1/projects/$2/infra/grafana"
        printf '%s   # selftest\n' "$3" >> "$1/projects/$2/infra/grafana/pending-metrics.txt"
    }

    d="$(clone dash_unknown)"; add_dash "$d" "$P1" 'rate(selftest_dash_nobody_total[5m])'; restage "$d"
    expect "a dashboard metric nothing exports bites" 1 "$d"

    d="$(clone dash_escaped_label)"; add_dash "$d" "$P1" 'sum(rate(http_server_requests_seconds_count{service=\"selftest_label_value_total\"}[5m]))'; restage "$d"
    expect "an escaped label value inside a dashboard expr is not a metric" 0 "$d"

    d="$(clone dash_pending)"; add_dash "$d" "$P1" 'rate(selftest_dash_pending_total[5m])'; add_pending "$d" "$P1" selftest_dash_pending_total; restage "$d"
    expect "a dashboard metric listed in pending-metrics.txt passes" 0 "$d"

    d="$(clone pending_now_exported)"
    mkdir -p "$d/projects/$P1/apps/x/src/main/java"
    printf 'class M { Object c = io.micrometer.core.instrument.Counter.builder("selftest.now.exported"); }\n' > "$d/projects/$P1/apps/x/src/main/java/M.java"
    add_dash "$d" "$P1" 'rate(selftest_now_exported_total[5m])'; add_pending "$d" "$P1" selftest_now_exported_total; restage "$d"
    expect "a pending entry that is now exported bites (retract the exemption)" 1 "$d"

    d="$(clone pending_stale)"; add_pending "$d" "$P1" selftest_nobody_asks_total; restage "$d"
    expect "a pending entry no dashboard queries bites (stale)" 1 "$d"

    d="$(clone pending_not_for_alerts)"; add_pending "$d" "$P1" selftest_alert_pending_total
    add_dash "$d" "$P1" 'rate(selftest_alert_pending_total[5m])'
    add_rule "$d" "$P1" 'rate(selftest_alert_pending_total[5m]) > 0'; restage "$d"
    expect "pending-metrics.txt never exempts an alert rule" 1 "$d"

    d="$(clone hyphen_name)"
    mkdir -p "$d/projects/$P1/apps/x/src/main/java"
    printf 'class M { Object c = io.micrometer.core.instrument.Counter.builder("selftest.with-hyphen.count"); }\n' > "$d/projects/$P1/apps/x/src/main/java/M.java"
    add_dash "$d" "$P1" 'rate(selftest_with_hyphen_count_total[5m])'; restage "$d"
    expect "a registered name with '-' matches its '_' Prometheus spelling" 0 "$d"

    d="$tmp/empty"; mkdir -p "$d"; git -C "$d" init -q
    expect "no rules file fails closed" 1 "$d"

    echo "self-test: ${pass} passed, ${fail} failed"
    [[ "$fail" -eq 0 ]]
}

case "${1:-}" in
    --self-test) self_test ;;
    --unexported) unexported_mode "$ROOT" "${2:-}" ;;
    -h|--help)   usage ;;
    "")          run_check "$ROOT" ;;
    *)           usage >&2; exit 2 ;;
esac
