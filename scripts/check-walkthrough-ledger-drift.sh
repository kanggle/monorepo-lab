#!/usr/bin/env bash
#
# check-walkthrough-ledger-drift.sh — TASK-MONO-518
#
# Guards `docs/guides/interview-demo-walkthrough.md` § 6 ("알려진 한계") against the
# task queues. A row there cites a ticket; when that ticket moves to `done/`, the row
# has to say so. When it does not, the document tells an interviewer that a defect we
# fixed is still live — and nothing else in the repo notices, because no test depends
# on prose.
#
# That is not hypothetical. TASK-MONO-517 audited the table on 2026-08-08 and found
# **7 of 26 ticket-citing rows stale**. Five of those carried no status emoji at all,
# so neither a human skim nor a "grep for 🔴" heuristic could see them — only a
# mechanical queue lookup did. This script is that lookup, run on every PR.
#
# ---------------------------------------------------------------------------
# THE PREDICATE
# ---------------------------------------------------------------------------
#   for each § 6 table row:
#     ids := every TASK-… token in the row's TRACKING column  (from the SOURCE, never
#            a hand-kept list)
#     for each id:
#       loc := the queue directory that ticket file lives in
#       loc missing          -> FAIL   (typo / deleted ticket is drift too)
#       loc == done/ and the row carries NO resolution marker
#                            -> FAIL   unless the id is an accepted exception
#
# A "resolution marker" is the row's LEADING status emoji being ✅ or 🔵 (or a row
# opening with ~~strikethrough~~) — the forms TASK-MONO-517's convention established.
# 🔴 LEADING, not "anywhere in the row" — see the note at the `resolved` assignment.
#
# 🔴 EXTRACTING ZERO IDS IS A FAILURE, not a pass. If the table's formatting changes
# and the regex stops matching, a count-based guard silently becomes vacuous and
# reports green forever over exactly the drift it exists to catch. This repository
# has shipped that shape before; the floor below is the fix.
#
# 🔴 WHY NOT `done ⇔ ✅`. That correspondence is wrong, and getting it wrong would
# make this guard fire on correct rows until someone deleted it. TASK-PC-FE-273 was
# closed as "investigation concluded, not a defect" — its row's 🟡 is ACCURATE, and
# demanding a ✅ there would be demanding a lie. So there is an exception list, and
# every entry must carry a reason (see § EXCEPTIONS). An exception without a reason
# is itself a failure: a list of bare ids reads as "things we wave through", and the
# next real stale row gets parked in it.
#
# 🔴 ONLY THE TRACKING COLUMN COUNTS, and that was measured, not assumed. The first
# version of this guard read TASK- ids out of the WHOLE row and immediately produced a
# false positive: the `/erp/delegation` row tracks TASK-ERP-BE-043 (open) and merely
# NARRATES a closed one — "TASK-ERP-BE-041 이 닫히자 예측이 실측으로 확인됐다". That
# sentence is the row being correct, and a guard that fires on it teaches people to
# stop writing the history down. The tracking column is what a row is ABOUT; prose
# citations are context.
#
# 🔵 Within the tracking column, several ids are checked as ANY-done. A row tracking
# one closed and one open ticket while saying nothing about the closed one is the
# failure mode this guard exists for.
#
# ---------------------------------------------------------------------------
# NOTE ON `git ls-files`
# ---------------------------------------------------------------------------
# Ticket locations are resolved with `git ls-files`, so a ticket file that has been
# moved between queues but NOT yet staged still reports its old location and this
# script reports a drift that CI will not see (or misses one CI will). Stage first.
# `check-index-queue-drift.sh` has the same property and the same note.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# TASK-MONO-549 — overridable so the self-test can run the predicate against a MUTATED
# COPY of the real ledger. Only the ledger moves: the ticket index still comes from
# this repo's real `git ls-files`, because a hand-built world is more forgiving than
# the real one and would prove less.
LEDGER="${WALKTHROUGH_LEDGER_FILE:-docs/guides/interview-demo-walkthrough.md}"
EXCEPTIONS="scripts/walkthrough-ledger-exceptions.txt"
SECTION_HEADING='^## 6[.] '

cd "$ROOT"

# ---------------------------------------------------------------------------
# self-test — TASK-MONO-549. Mutates COPIES of the real ledger, never a fixture.
# ---------------------------------------------------------------------------
# A hand-built table is more forgiving than the real one: it has the shape whoever
# wrote it expected, which is the shape the parser already handles. Everything below
# starts from `docs/guides/interview-demo-walkthrough.md` as it stands.
self_test() {
    local pass=0 fail_n=0 tmp
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' RETURN

    # Rewrites the leading marker of the § 6 row containing <needle>. `new` may be empty,
    # which removes the marker entirely — the evasion path this rule has to close.
    mutate() {  # mutate <src> <dst> <needle> <new-marker>
        awk -v needle="$3" -v new="$4" '
            index($0, needle) && /^\|/ && !done {
                sub(/^\|[[:space:]]*(🔴|🟡|🔵|✅)[[:space:]]*/, "| " (new == "" ? "" : new " "))
                done = 1
            }
            { print }
        ' "$1" > "$2"
    }
    # Blanks the tracking column of the row containing <needle>.
    uncite() {  # uncite <src> <dst> <needle>
        awk -v needle="$3" '
            index($0, needle) && /^\|/ && !done {
                sub(/\|[[:space:]]*`?TASK-[^|]*\|[[:space:]]*$/, "| — |"); done = 1
            }
            { print }
        ' "$1" > "$2"
    }

    run() { WALKTHROUGH_LEDGER_FILE="$1" bash "$ROOT/scripts/check-walkthrough-ledger-drift.sh" >/dev/null 2>&1; }
    expect() {  # expect <label> <want-rc> <ledger>
        local got=0; run "$3" || got=$?
        if [ "$got" = "$2" ]; then echo "  PASS  $1 (rc=$got)"; pass=$((pass + 1))
        else echo "  FAIL  $1 (want $2, got $got)"; fail_n=$((fail_n + 1)); fi
    }

    echo "self-test: predicate against mutated copies of the real ledger"

    local real="$ROOT/docs/guides/interview-demo-walkthrough.md"
    local base="$tmp/base.md"
    cp "$real" "$base"

    # 0. The real entry point, own process, errexit active. Every case below runs behind
    #    `|| got=$?`, which suppresses errexit — a script that aborts would report PASS
    #    there while the real invocation dies with no output.
    local rc0=0
    bash "$ROOT/scripts/check-walkthrough-ledger-drift.sh" >/dev/null 2>&1 || rc0=$?
    if [ "$rc0" = 0 ]; then echo "  PASS  real entry point, separate process (rc=0)"; pass=$((pass + 1))
    else echo "  FAIL  real entry point, separate process (rc=$rc0)"; fail_n=$((fail_n + 1)); fi

    # 1. Unmutated copy passes. Without this an always-FAIL guard looks identical.
    expect "unmutated ledger passes" 0 "$base"

    # 2. 🔴 That control is only meaningful if uncited rows EXIST in it — otherwise case 1
    #    passes because the new rule has nothing to look at, and every case below would be
    #    testing a rule that never fires on real data. The 🔴-only version of this rule had
    #    exactly that problem (0 red rows), which is why the rule keys on "unresolved".
    #    🔵 Counted on the TRACKING COLUMN, the same field the guard judges — not "a
    #    TASK id appears anywhere in the row". Those differ by one on the real ledger:
    #    the ERP row narrates TASK-ERP-BE-042 in its prose while tracking `—`. Using the
    #    looser predicate here reported 5 where the guard reports 6, and two numbers that
    #    disagree for an unexplained reason are how a reader stops trusting either.
    local uncited_n
    uncited_n="$(awk -F'|' '
        /^## 6[.] / { inside = 1; next }
        inside && /^## / { inside = 0 }
        inside && /^\|/ && $0 !~ /^\|[[:space:]]*(항목|---)/ {
            if ($(NF-1) !~ /TASK-[A-Z]+(-[A-Z]+)*-[0-9]+/) n++
        }
        END { print n + 0 }
    ' "$base")"
    if [ "$uncited_n" -gt 0 ]; then
        echo "  PASS  reachability: the control ledger holds $uncited_n uncited row(s) for the rule to see"
        pass=$((pass + 1))
    else
        echo "  FAIL  reachability: no uncited rows — cases below would prove nothing"
        fail_n=$((fail_n + 1))
    fi

    # 3/4/5. An uncited row that is NOT marked resolved must fail, however it got there.
    local needle='장바구니는 시드할 수 없다'
    mutate "$base" "$tmp/red.md"  "$needle" '🔴'
    expect "uncited row marked 🔴 fails"                1 "$tmp/red.md"
    mutate "$base" "$tmp/amber.md" "$needle" '🟡'
    expect "uncited row marked 🟡 fails (the live population)" 1 "$tmp/amber.md"
    mutate "$base" "$tmp/bare.md" "$needle" ''
    expect "uncited row with NO marker fails (deleting the marker is not an exit)" 1 "$tmp/bare.md"

    # 6. The original rule must still bite: a row citing a done ticket, with its
    #    resolution marker stripped. Guards get replaced by their successors' bugs.
    mutate "$base" "$tmp/wasdone.md" '/wms/operations' '🟡'
    expect "cited done ticket without a resolution marker still fails (original rule)" 1 "$tmp/wasdone.md"

    # 7. And a resolved row that loses its citation must stay silent — this is the case
    #    that stops the new rule from demanding tickets for statements of fact.
    uncite "$base" "$tmp/uncited-ok.md" '/wms/operations'
    expect "resolved row with its citation removed passes (fact, not open work)" 0 "$tmp/uncited-ok.md"

    echo "self-test: ${pass} passed, ${fail_n} failed"
    [ "$fail_n" -eq 0 ]
}

case "${1:-}" in
    --self-test) self_test; exit $? ;;
    -h|--help)   printf 'usage: check-walkthrough-ledger-drift.sh [--self-test]\n'; exit 0 ;;
    "")          ;;
    *)           printf 'unknown argument: %s\n' "$1" >&2; exit 2 ;;
esac

fail_count=0
fail() { printf 'DRIFT: %s\n' "$*" >&2; fail_count=$((fail_count + 1)); }

[ -f "$LEDGER" ] || { printf 'FATAL: %s not found\n' "$LEDGER" >&2; exit 2; }

# --- § 6 rows -----------------------------------------------------------------
# From the § 6 heading to the next `## ` heading, keep pipe-table rows only, and drop
# the header/separator rows. Blockquote lines (the convention block) never start with
# `|`, so they fall out here rather than needing a second exclusion.
rows="$(awk -v start="$SECTION_HEADING" '
    $0 ~ start { inside = 1; next }
    inside && /^## / { inside = 0 }
    inside && /^\|/ { print }
' "$LEDGER" | grep -vE '^\|[[:space:]]*(항목|---)' || true)"

row_count="$(printf '%s' "$rows" | grep -c '^|' || true)"
if [ "${row_count:-0}" -eq 0 ]; then
    printf 'FATAL: parsed 0 table rows out of %s § 6 — the section heading or table\n' "$LEDGER" >&2
    printf '       format changed and this guard is now vacuous. Fix the parser, do\n' >&2
    printf '       NOT lower this floor.\n' >&2
    exit 2
fi

# --- exceptions ---------------------------------------------------------------
# Format: `TASK-ID<space>#<space>reason`. A line without a reason is rejected, so the
# file cannot decay into a bare allow-list.
declare -A exception_reason=()
if [ -f "$EXCEPTIONS" ]; then
    while IFS= read -r line; do
        # 🔴 TASK-MONO-524 (incidental): strip a trailing CR before anything else.
        # This repo's .gitattributes forces LF for *.sh and *.sql but not *.txt, so on
        # a Windows clone with core.autocrlf=true this file checks out CRLF. A blank
        # line then arrives as "\r" — it does not match the '' case below, survives to
        # the reason check, and the guard reports `exception '' carries no reason`.
        # CI never sees it (Linux checkout = LF), so the failure is local-only: the
        # guard is RED on the machine of anyone who runs it before pushing, which is
        # exactly the audience it was written for.
        line="${line%$'\r'}"
        case "$line" in ''|'#'*) continue ;; esac
        id="$(printf '%s' "$line" | awk '{print $1}')"
        reason="$(printf '%s' "$line" | sed -E 's/^[^#]*#[[:space:]]*//')"
        if [ -z "$reason" ] || [ "$reason" = "$line" ]; then
            fail "exception '$id' in $EXCEPTIONS carries no reason. A bare id reads as "\
"'wave this through' and is where the next genuinely stale row gets parked."
            continue
        fi
        exception_reason["$id"]="$reason"
    done < "$EXCEPTIONS"
fi

# --- ticket location index ----------------------------------------------------
# One `git ls-files` for every task file, then a lookup per id — rather than one
# invocation per id.
#
# 🔴 The body of this loop spawns NO subprocesses, and that is not premature
# optimisation. Measured on this repo's Windows/msys host: 2238 task files, and the
# obvious `basename`/`dirname`/`grep -oE` version cost four process spawns each —
# ~9000 spawns, which ran past a five-minute timeout and read as a hang rather than
# as slowness. Parameter expansion and `[[ =~ ]]` are in-process.
declare -A ticket_queue=()
while IFS= read -r path; do
    [ -n "$path" ] || continue
    base="${path##*/}"
    dir="${path%/*}"
    queue="${dir##*/}"
    if [[ $base =~ ^(TASK-[A-Z]+(-[A-Z]+)*-[0-9]+) ]]; then
        ticket_queue["${BASH_REMATCH[1]}"]="$queue"
    fi
done < <(git ls-files 'tasks/*/TASK-*.md' 'projects/*/tasks/*/TASK-*.md')

if [ "${#ticket_queue[@]}" -eq 0 ]; then
    printf 'FATAL: indexed 0 ticket files — `git ls-files` returned nothing, so every\n' >&2
    printf '       row below would be judged against an empty world.\n' >&2
    exit 2
fi

# --- the check ----------------------------------------------------------------
checked_rows=0
cited_ids=0
unowned_rows=0     # unresolved marker AND no citation — TASK-MONO-549's rule
informational=0    # resolved marker and no citation — legitimately outside the predicate

while IFS= read -r row; do
    [ -n "$row" ] || continue
    # `$(NF-1)` is the tracking column: a markdown row is `| a | b | c |`, so awk sees
    # a trailing empty field after the final pipe and the last real column is NF-1.
    tracking="$(printf '%s' "$row" | awk -F'|' '{print $(NF-1)}')"
    ids="$(printf '%s' "$tracking" | grep -oE 'TASK-[A-Z]+(-[A-Z]+)*-[0-9]+' | sort -u || true)"

    # The row's own label, trimmed, for readable failure output.
    label="$(printf '%s' "$row" | awk -F'|' '{print $2}' | cut -c1-90)"

    # 🔴 The marker must be the row's LEADING status emoji, not "appears somewhere in
    # the row". Measured: the first version matched anywhere, and the `/ledger` row —
    # whose status is 🟡 — writes "시산표 **3원소** ✅" inside a measurement list. That
    # decorative tick made the row read as resolved, which in turn made the
    # TASK-PC-FE-273 exception look unnecessary: removing it from the list changed
    # nothing, because the guard was never going to flag that row anyway. A guard that
    # passes for the wrong reason and an exception that is never exercised are the
    # same defect seen from two sides.
    #
    # TASK-MONO-517's convention (a) is what makes the narrow form possible: every row
    # starts with its status emoji. ✅ = 해소, 🔵 = 사실 기록 (both count as "the row
    # says something about the closed ticket"); 🔴 / 🟡 do not.
    first_col="$(printf '%s' "$row" | awk -F'|' '{print $2}' | sed -E 's/^[[:space:]]+//')"
    resolved=0
    case "$first_col" in
        "✅"*|"🔵"*|"~~"*) resolved=1 ;;
    esac

    # --- TASK-MONO-549: an unresolved row must have an owner ------------------
    # The original predicate reads "for each CITED ticket, is it done while the row
    # still says otherwise" — so a row with `—` in its tracking column was dropped
    # before any judgement, and the summary line reported `OK` beside a count that
    # meant "and N rows were not looked at". Both TASK-MONO-547 and TASK-MONO-548 sat
    # in exactly that gap: live 🔴 defects, in no queue, while this guard was green.
    # One had its citation deleted when the ticket it named turned out to be about
    # something else; the other never had an owner. A person reading the eight uncited
    # rows by hand is what found them.
    #
    # 🔵 The rule reuses `resolved` rather than testing for 🔴 — deliberately, and the
    # numbers are why. Closing 547/548 took the 🔴 count to ZERO, so a 🔴-only rule
    # would examine nothing today, pass, and have no way to build a bite out of real
    # data. Inverting the existing predicate gives it a live population (4 rows carry
    # 🟡, all of them already citing) and closes the marker-deletion path in the same
    # move: a row with NO marker is not resolved either, so it must cite too.
    #
    # 🔵 A row that IS resolved (✅ / 🔵 / ~~) needs no ticket. All 6 uncited rows are
    # of that kind — which screens fill, that `demo.env` must be sourced, that the cart
    # is localStorage-backed. Demanding tickets there manufactures empty ones.
    if [ -z "$ids" ]; then
        if [ "$resolved" -eq 1 ]; then
            informational=$((informational + 1))
        else
            unowned_rows=$((unowned_rows + 1))
            fail "row [$label ] carries an unresolved marker but cites no ticket. Nothing "\
"schedules it and — until TASK-MONO-549 — nothing checked it either: the tracking "\
"column is what puts a row inside this guard's predicate. File a ticket and cite it, "\
"or if the row is a statement of fact rather than an open defect, mark it 🔵."
        fi
        continue
    fi
    checked_rows=$((checked_rows + 1))

    while IFS= read -r id; do
        [ -n "$id" ] || continue
        cited_ids=$((cited_ids + 1))
        queue="${ticket_queue[$id]:-}"

        if [ -z "$queue" ]; then
            fail "row [$label ] cites $id, which exists in no task queue. A citation that "\
"resolves to nothing is drift too — the ticket was renamed, deleted, or mistyped."
            continue
        fi

        if [ "$queue" = "done" ] && [ "$resolved" -eq 0 ]; then
            if [ -n "${exception_reason[$id]:-}" ]; then
                continue
            fi
            fail "row [$label ] cites $id, which is in tasks/…/done/, but the row carries no "\
"resolution marker (✅ / ~~strikethrough~~ / 🔵). Either mark it, or add $id to "\
"$EXCEPTIONS WITH A REASON if the row is correct as it stands."
        fi
    done <<< "$ids"
done <<< "$rows"

if [ "$cited_ids" -eq 0 ]; then
    printf 'FATAL: %s § 6 has %s table rows but NOT ONE cites a TASK- id.\n' "$LEDGER" "$row_count" >&2
    printf '       Either the citation column changed shape or the id regex broke; in\n' >&2
    printf '       both cases this guard is checking nothing. 0 is not a pass.\n' >&2
    exit 2
fi

if [ "$fail_count" -gt 0 ]; then
    printf '\ncheck-walkthrough-ledger-drift: FAILED — %s drift finding(s).\n' "$fail_count" >&2
    exit 1
fi

# 🔴 TASK-MONO-549 — the summary says what was NOT judged, not just what was. The
# previous wording ("42 of 48 rows cite a ticket") already carried that number and
# nobody read it as "6 rows were not looked at"; it sat next to the word OK. Now every
# row is accounted for by one of the three counts below, and the informational tally
# names the reason it is safe rather than leaving a remainder to be inferred.
printf 'check-walkthrough-ledger-drift: OK — %s of %s § 6 rows judged by citation (%s citations); '\
'%s row(s) cite nothing and are marked resolved (✅/🔵/~~), so they are statements of '\
'fact rather than open work; %s unowned row(s). Every cited ticket resolves to a queue, '\
'every done one is marked resolved (%s documented exception(s)).\n' \
"$checked_rows" "$row_count" "$cited_ids" "$informational" "$unowned_rows" "${#exception_reason[@]}"
