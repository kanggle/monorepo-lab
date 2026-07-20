#!/usr/bin/env bash
#
# check-index-queue-drift.sh — TASK-MONO-451
#
# Fails when a `tasks/INDEX.md` queue listing disagrees with the queue
# DIRECTORY it describes, in either direction:
#
#   listing-only  a row sits under `## ready` but no file is in `tasks/ready/`
#                 (the file already moved on — the row is stale)
#   disk-only     a file sits in `tasks/ready/` but no row lists it
#                 (the move happened, the INDEX edit did not)
#
# WHY THIS EXISTS
# ---------------
# `tasks/INDEX.md` § PR Separation Rule already requires a close chore to do
# three things in ONE commit — ① `git mv` ② edit the file's `Status` ③ move the
# INDEX row — and already warns, citing PR #375, that "skipping step 2 or 3
# produces silent drift". The rule exists, the violation is documented, and it
# still happened three times in one day (2026-07-20):
#
#   TASK-BE-535  in done/, row still under `## ready`     caught by hand (#2766)
#   TASK-BE-536  same                                      caught by hand (#2766)
#   TASK-BE-539  row body rewritten to "**DONE (#2770)**"  caught by hand (#2777)
#                but the row never left `## ready`
#
# All three merged green. The neighbouring `Task ID collision` job did not fire,
# and could not have: its predicate is "does an ID appear twice in the active
# queues", not "does the listing match the directory". A guard existed; it was
# not looking at this axis. Same day, two concurrent sessions moved rows in the
# root and ecommerce INDEX files and a merge conflict in the root `## review`
# section nearly dropped another session's TASK-MONO-452 row — again green.
#
# Three repeats of a rule that is written down is not a people problem. Writing
# the warning in bold a fourth time does not stop the fourth occurrence.
#
# THE PREDICATE IS SET EQUALITY, AND POSITION IS THE TRUTH
# --------------------------------------------------------
# A row's text is a CLAIM; the section it sits under is the FACT. TASK-BE-539's
# row said "DONE" in bold while sitting under `## ready`. So this script never
# reads a row body for words like "DONE" — that implementation catches BE-539 by
# accident and then false-positives on every legitimate row in `## done`
# (ticket § F2). It compares, per section, the SET of task IDs listed against
# the SET of task IDs on disk in the matching directory. Nothing else.
#
# WHAT IS COMPARED, AND WHAT IS NOT
# ---------------------------------
#   compared   ready, in-progress, review — the ACTIVE queues.
#
#   backlog    EXCLUDED, deliberately. A backlog entry is an idea with no file
#              yet; having no file is its normal state, not drift. Including it
#              would put every project permanently RED (ecommerce alone lists 5
#              fileless backlog rows), and a permanently red guard gets switched
#              off — and a switched-off job's skip reports green.
#
#   archive    EXCLUDED: some projects have an `## archive` section and no
#              `tasks/archive/` directory at all.
#
#   done       EXCLUDED, and this one was measured rather than assumed. `## done`
#              is an append-only changelog, and a large share of its rows are
#              GROUPED — one bullet closing several tickets ("TASK-MONO-437 +
#              TASK-MONO-439", "TASK-MONO-430/431 (+ scm SCM-BE-034/035/038)").
#              Set equality over one-ID-per-row reports every co-closed ticket as
#              disk-only. Measured on the tree at 2026-07-20, not estimated —
#              re-run it with INDEX_DRIFT_SECTIONS="ready in-progress review done":
#              848 findings, 833 of them on the `done` axis, against 15 on the
#              active queues. That is not a guard, it is noise with a red X on
#              it. The three defects this ticket exists for were all
#              ACTIVE-queue drift, which is what active-queue coverage is for:
#              a task that leaves `ready/` is caught leaving, whatever its
#              destination. Drift confined to `## done` (a closed task listed
#              twice, or not at all) is not covered — stated plainly, per § G8.
#
# FORMAT TOLERANCE (ticket AC-4)
# ------------------------------
# The nine INDEX files do not share a format, and a parser tuned to one silently
# returns zero findings on the others — a green that means "did not look", not
# "no drift" (ticket § F3). Measured, today:
#
#   * root `tasks/INDEX.md` has NO `## ready` HEADING AT ALL. Its ready queue is
#     the unlabelled region between `# Task List` and the first `##`. A parser
#     that keys on `## ready` reads the root file — the busiest queue in the
#     repo — as zero rows and passes. Handled: the region opened by `# Task List`
#     is treated as `ready` until a `##` renames it, and if a file has BOTH a
#     `## ready` heading and rows in that unlabelled region, that is reported as
#     a parse error rather than guessed at.
#   * rows are BULLETS (`- \`TASK-X-1-slug.md\` — text`) in the root, fan and
#     platform-console files, and TABLE rows (`| TASK-BE-543 | ... |`) in
#     ecommerce. Both are parsed; table header and `|---|` separator rows are
#     skipped by shape.
#   * empty sections are written `(empty)`, `_(없음)_`, or left blank.
#   * `## done` occurs TWICE in platform-console; sections are merged by name.
#   * `## ready → in-progress` is a heading in every file's Move Rules. Sections
#     are only read AFTER `# Task List`, so that transition heading is not
#     mistaken for the ready queue.
#   * IDs have three segments as well as two (TASK-FIN-BE-059, TASK-PC-FE-252).
#     The ID pattern below takes them all; self-check `--selftest` asserts it
#     against known positives instead of trusting an empty result.
#
# A section that contains prose but yields no parsable row and carries no empty
# marker is reported as a PARSE ERROR and fails the run. An unreadable section
# must never be indistinguishable from a clean one.
#
# Prose lines are ignored on purpose: both the root ready region and the
# platform-console ready section carry `>` notes and `_(직전 완료)_` paragraphs
# that mention other tickets. Only bullets and table rows are rows.
#
# TWO PARSER BUGS THAT THE LIVE TREE FOUND, recorded because each one produced a
# confident wrong answer that looked exactly like a real finding:
#
#   * The empty-marker test was a SUBSTRING test. The row for TASK-MONO-451
#     itself — whose title quotes "`(empty)`/`_(없음)_`" while describing this
#     parser — was therefore read as an empty marker and dropped, and the guard
#     reported that live, correctly-listed row as disk-only. Markers are now
#     matched anchored to the whole line: the question is whether the line IS
#     the marker, not whether it mentions one.
#   * A bullet's row ID was "the first task ID anywhere in the line". The root
#     ready region carries note bullets whose subject is an ADR and which name a
#     closed task in passing, so TASK-MONO-392 was reported as a stale ready row
#     that does not exist. A bullet is a row only when its FIRST inline-code span
#     names a task; otherwise it is a note.
#
# Both were caught by checking a known answer against the detector rather than
# believing its output. `--selftest` pins both.
#
# STATE OF THE TREE WHEN THIS LANDED (AC-0 — 15 findings, 0 parse errors,
# across three INDEX files):
#
#   iam    § ready lists BE-508/509/510/511/512/513/519/520, all eight in done/
#          — the BE-539 shape, eight times over, oldest since 2026-07-15
#   iam    ready/ holds BE-398, named only in a `>` note, never a row
#   wms    § ready says "(empty)"; ready/ holds BE-523 and BE-529
#   root   § ready lists MONO-428/430/432/448, all four in done/
#
# Nobody had ever compared these. That list is the ticket's largest yield.
#
# USAGE
#   scripts/check-index-queue-drift.sh              # guard the repo
#   scripts/check-index-queue-drift.sh --selftest   # assert regex + parser
#   INDEX_DRIFT_SECTIONS="ready review in-progress done" ...  # widen (measuring)
# ---------------------------------------------------------------------------

set -euo pipefail

cd "$(dirname "$0")/.."

# Three segments as well as two: TASK-BE-542, TASK-FIN-BE-059, TASK-PC-FE-252.
ID_RE='TASK-[A-Z]+(-[A-Z]+)*-[0-9]+[a-z]?'

# --------------------------------------------------------------------------
# --selftest: never trust an empty detector output (§ the empty-output rule).
# Asserts the ID pattern AND the section/row parser against known positives and
# known negatives before the guard is believed. Run by CI ahead of the guard
# itself, so a parser that silently stops matching fails loudly instead of
# reporting a clean repo.
# --------------------------------------------------------------------------
run_selftest() {
  local st_fail=0
  check_id() { # <input> <expected first id, or "" for none>
    local got
    if [[ "$1" =~ ($ID_RE) ]]; then got="${BASH_REMATCH[1]}"; else got=""; fi
    if [[ "$got" != "$2" ]]; then
      echo "SELFTEST FAIL: '$1' -> '${got:-<none>}' (expected '${2:-<none>}')"
      st_fail=1
    fi
  }
  check_id 'TASK-BE-542-map-unique-violations.md'          'TASK-BE-542'
  check_id 'TASK-FIN-BE-059-some-slug.md'                  'TASK-FIN-BE-059'
  check_id 'TASK-PC-FE-252-idempotency-key.md'             'TASK-PC-FE-252'
  check_id 'TASK-MONO-451-index-queue-tables-drift.md'     'TASK-MONO-451'
  check_id '| TASK-BE-543 | **READY** | user-service |'    'TASK-BE-543'
  check_id '- ✅ `TASK-MONO-448-adr-record.md` — **DONE**' 'TASK-MONO-448'
  # first token wins, so a ticket naming another ticket keys on itself
  check_id 'TASK-MONO-016-fix-TASK-MONO-015.md'            'TASK-MONO-016'
  check_id 'TASK-MONO-430/431 (+ scm SCM-BE-034)'          'TASK-MONO-430'
  check_id '- **📎 배경 (ADR-MONO-049 범위 A)**'           ''
  check_id '_(없음)_'                                      ''

  # ---- parser fixture -----------------------------------------------------
  # Exercises every shape the nine live INDEX files use, including the two that
  # produced wrong answers during development (a note bullet whose subject is an
  # ADR; a row whose title QUOTES the "(empty)" marker).
  local fx expected got
  fx="$(mktemp)"
  cat > "$fx" <<'FIXTURE'
# Move Rules

## ready → in-progress

This heading must NOT be read as the ready queue.

# Task List

> a blockquote note mentioning `TASK-BE-001` in passing

- **📎 배경 (`ADR-MONO-049` — D5 완결 (`TASK-MONO-392`, 2026-07-13))**
- `TASK-MONO-451-index-queue-tables-drift.md` — 일부는 `(empty)`/`_(없음)_` 로 형식이 다르다
- ✅ `TASK-MONO-430/431` (+ scm SCM-BE-034) — **DONE**

## in-progress

(empty)

## review

| ID | Title | Service | Tags |
|---|---|---|---|
| TASK-FIN-BE-059 | something | account-service | code |

## done

_(없음)_

## odd-section

prose only, no row, no marker
FIXTURE
  expected='PARSE	odd-section	1 non-blank line(s), no parsable row and no (empty)/_(없음)_ marker
ROW	ready	TASK-MONO-451
ROW	ready	TASK-MONO-430
ROW	review	TASK-FIN-BE-059'
  got="$(extract_rows "$fx" | LC_ALL=C sort)"
  rm -f "$fx"
  if [[ "$got" != "$(printf '%s' "$expected" | LC_ALL=C sort)" ]]; then
    echo "SELFTEST FAIL: parser fixture mismatch."
    echo "--- expected ---"; printf '%s\n' "$expected"
    echo "--- got ---";      printf '%s\n' "$got"
    st_fail=1
  fi

  if [[ $st_fail -eq 0 ]]; then
    echo "check-index-queue-drift --selftest: OK (10 ID assertions + parser fixture)."
    echo "  fixture asserts: unlabelled root-style ready region parsed as 'ready';"
    echo "  '## ready → in-progress' in Move Rules NOT parsed as a section;"
    echo "  note bullet (ADR subject) not a row; row quoting '(empty)' still a row;"
    echo "  bullet + table + placeholder forms; prose-only section = PARSE error."
  fi
  return $st_fail
}

SECTIONS="${INDEX_DRIFT_SECTIONS:-ready in-progress review}"

# extract_rows <index-file>
#   emits: "ROW<TAB><section><TAB><id>"      one per parsed row
#          "PARSE<TAB><section><TAB><reason>" one per unreadable section
extract_rows() {
  awk -v id_re="$ID_RE" '
    function emit_section_state(   _) {
      if (section == "") return
      # A section with prose but no row and no empty-marker is unreadable.
      if (rows[section] + 0 == 0 && empty_marker[section] + 0 == 0 && \
          content[section] + 0 > 0)
        printf "PARSE\t%s\t%d non-blank line(s), no parsable row and no (empty)/_(없음)_ marker\n", \
               section, content[section]
    }
    # Everything before "# Task List" is prose (Lifecycle, Move Rules, ...).
    # Move Rules contains headings like "## ready → in-progress" which a naive
    # "^## ready" matcher would happily accept as the queue section.
    !in_list && /^# Task List[[:space:]]*$/ {
      in_list = 1
      # Root tasks/INDEX.md has no "## ready" heading: its ready queue is this
      # unlabelled region. Project files open with "## backlog" immediately, so
      # this provisional name is overwritten before any row appears.
      section = "ready"; implicit = 1
      next
    }
    !in_list { next }
    /^## / {
      seen_heading[substr($0, 4)] = 1
      name = $0
      sub(/^##[[:space:]]*/, "", name)
      sub(/[[:space:]]*$/, "", name)
      section = name
      implicit = 0
      next
    }
    /^# / { in_list = 0; next }        # a new level-1 heading ends the Task List
    {
      line = $0
      if (line ~ /^[[:space:]]*$/) next
      content[section]++
      if (implicit) implicit_content++

      # Empty markers are matched ANCHORED to the whole line. Unanchored, the
      # very row for TASK-MONO-451 — whose title quotes "`(empty)`/`_(없음)_`"
      # while describing this parser — was read as an empty marker and silently
      # dropped, and the guard then reported that live row as disk-only. A
      # substring test asks "does this line mention the marker"; the question is
      # "IS this line the marker".
      if (line ~ /^[[:space:]]*\(empty[^)]*\)[[:space:]]*$/ ||
          line ~ /^[[:space:]]*_\(없음\)_[[:space:]]*$/) { empty_marker[section]++; next }

      is_row = 0
      if (line ~ /^\|/) {
        # table: skip the "|---|---|" separator and the "| ID | Title |" header
        if (line ~ /^\|[[:space:]]*:?-{3,}/) next
        if (line ~ /^\|[[:space:]]*ID[[:space:]]*\|/) next
        is_row = 1
        sub(/^\|/, "", line)
        sub(/\|.*$/, "", line)         # first cell only — the ID column
      } else if (line ~ /^[-*][[:space:]]/) {
        # A bullet is a ROW when its FIRST inline-code span names a task; it is a
        # NOTE when that span names something else, or when there is none. The
        # root ready region carries note bullets like
        #   - **📎 배경 (`ADR-MONO-049` ... (`TASK-MONO-392`, 2026-07-13) ...
        # whose subject is an ADR and which merely mention a closed task in
        # passing. Keying on "first task ID anywhere in the bullet" reported
        # TASK-MONO-392 as a stale ready row — a listing entry that does not
        # exist. The subject of the row is the first code span, so that is what
        # is read.
        if (line !~ /`[^`]*`/) next
        sub(/^[^`]*`/, "", line)       # drop everything up to the first backtick
        sub(/`.*$/, "", line)          # keep only that first code span
        is_row = 1
      }
      if (!is_row) next                # prose: ">" notes, "_(직전 완료)_", bold paras

      if (match(line, id_re)) {
        printf "ROW\t%s\t%s\n", section, substr(line, RSTART, RLENGTH)
        rows[section]++
        if (implicit) implicit_rows++
      }
      # A bullet with no task ID is a note bullet, not a row. It still counted
      # toward content[], so a section made only of such bullets is reported as
      # a parse error rather than silently read as empty.
    }
    END {
      for (s in content) {
        section = s
        emit_section_state()
      }
      if (implicit_rows > 0 && seen_heading["ready"])
        printf "PARSE\t<unlabelled>\t%d row(s) sit between \"# Task List\" and the first \"##\" in a file that ALSO has a \"## ready\" heading — cannot tell which is the ready queue\n", implicit_rows
    }
  ' "$1"
}

if [[ "${1:-}" == "--selftest" ]]; then
  run_selftest
  exit $?
fi

# --------------------------------------------------------------------------
# Population from the tree, not enumerated here: every task INDEX in the repo.
# --------------------------------------------------------------------------
mapfile -t indexes < <(
  git ls-files 'tasks/INDEX.md' 'projects/*/tasks/INDEX.md' | LC_ALL=C sort
)

if [[ ${#indexes[@]} -eq 0 ]]; then
  echo "check-index-queue-drift: no tasks/INDEX.md found."
  echo "  That is a bug in this script's population query, not an empty repo."
  echo "  Failing rather than reporting a vacuous pass."
  exit 1
fi

fail=0
findings=0
parse_errors=0

# id_of_file <basename> -> first ID token anchored at the start
id_of_file() {
  if [[ "$1" =~ ^($ID_RE) ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
  else
    printf ''
  fi
}

for idx in "${indexes[@]}"; do
  base_dir="${idx%/INDEX.md}"          # tasks | projects/<p>/tasks

  if ! grep -q '^# Task List[[:space:]]*$' "$idx"; then
    echo "PARSE ERROR: ${idx} has no '# Task List' heading — cannot locate the queue"
    echo "             listings. Refusing to report zero findings for a file this"
    echo "             script could not read."
    parse_errors=$((parse_errors + 1))
    fail=1
    continue
  fi

  parsed="$(extract_rows "$idx")"

  while IFS=$'\t' read -r kind sect detail; do
    [[ "$kind" == "PARSE" ]] || continue
    # Only guarded sections matter; an unreadable backlog is not this job.
    if [[ " $SECTIONS " == *" $sect "* || "$sect" == "<unlabelled>" ]]; then
      echo "PARSE ERROR: ${idx} § ${sect} — ${detail}"
      parse_errors=$((parse_errors + 1))
      fail=1
    fi
  done <<< "$parsed"

  for sect in $SECTIONS; do
    # --- listed set -----------------------------------------------------
    listed="$(
      printf '%s\n' "$parsed" \
        | awk -F'\t' -v s="$sect" '$1=="ROW" && $2==s {print $3}' \
        | LC_ALL=C sort -u
    )"

    # --- on-disk set ----------------------------------------------------
    disk=""
    while IFS= read -r f; do
      [[ -n "$f" ]] || continue
      id="$(id_of_file "${f##*/}")"
      if [[ -z "$id" ]]; then
        echo "PARSE ERROR: cannot parse a task ID from filename: $f"
        parse_errors=$((parse_errors + 1))
        fail=1
        continue
      fi
      disk+="${id}"$'\n'
    done < <(git ls-files "${base_dir}/${sect}/*.md" 2>/dev/null || true)
    disk="$(printf '%s' "$disk" | sed '/^$/d' | LC_ALL=C sort -u)"

    # --- set difference, both directions --------------------------------
    while IFS= read -r id; do
      [[ -n "$id" ]] || continue
      actual="$(git ls-files "${base_dir}/*/${id}-*.md" "${base_dir}/*/${id}.md" 2>/dev/null | head -1)"
      echo "DRIFT: ${idx} § ${sect} lists ${id}, but no such file in ${base_dir}/${sect}/"
      if [[ -n "$actual" ]]; then
        actual_sect="${actual#"${base_dir}"/}"; actual_sect="${actual_sect%%/*}"
        echo "       the file is at ${actual} — move the row to '## ${actual_sect}'"
        echo "       (section, not row text, is what readers act on: a row rewritten"
        echo "        to say DONE while it sits under ready is still a ready entry)"
      else
        echo "       and no file with that ID exists anywhere under ${base_dir}/"
      fi
      findings=$((findings + 1))
      fail=1
    done < <(comm -23 <(printf '%s\n' "$listed" | sed '/^$/d') <(printf '%s\n' "$disk" | sed '/^$/d'))

    while IFS= read -r id; do
      [[ -n "$id" ]] || continue
      echo "DRIFT: ${base_dir}/${sect}/ holds ${id}, but ${idx} § ${sect} does not list it"
      echo "       add the row (close chores move file AND row in one commit —"
      echo "       tasks/INDEX.md § PR Separation Rule, step 3)"
      findings=$((findings + 1))
      fail=1
    done < <(comm -13 <(printf '%s\n' "$listed" | sed '/^$/d') <(printf '%s\n' "$disk" | sed '/^$/d'))
  done
done

echo
if [[ $fail -eq 0 ]]; then
  echo "check-index-queue-drift: OK — ${#indexes[@]} INDEX files, sections [${SECTIONS}]"
  echo "  listing and directory agree in both directions."
  echo "  (backlog/archive/done intentionally unguarded; see the header for what"
  echo "   that leaves uncovered.)"
else
  echo "check-index-queue-drift: FAILED — ${findings} drift finding(s), ${parse_errors} parse error(s)."
fi

exit $fail
