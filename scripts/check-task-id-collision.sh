#!/usr/bin/env bash
#
# check-task-id-collision.sh — TASK-MONO-443
#
# Fails when two task files in the same ID namespace claim the same ID.
#
# `tasks/INDEX.md` § Task ID Allocation already describes this failure and
# prescribes the remedy (rule 4: the branch already merged to `main` keeps the
# number, the other renumbers before merge). Nothing enforced it. Rule 4 only
# works if somebody notices before the second branch merges, and twice nobody
# did:
#
#   TASK-MONO-415  two unrelated tasks, both in tasks/done/ — landed, still there
#   TASK-MONO-435  a new ticket filed onto an ID a merged task already held
#                  (PR #2731); renumbered to 442 afterwards
#   TASK-PC-FE-250 caught pre-merge and renumbered to 251 — rule 4 working
#
# Two landed, one near-miss. The gap is detection latency, not the rule, so this
# guard turns a silent landing into a red check and leaves rule 4 to handle it.
#
# ---------------------------------------------------------------------------
# Design decisions, each measured on the tree at 2026-07-20 (AC-0), not assumed.
# The ticket specified a different predicate; re-measuring refuted it. Both
# corrections are recorded here because the numbers are the reason.
#
#   (1) SCOPE THE KEY TO THE PROJECT.
#       `BE` is not one namespace — every project has its own. A global
#       (namespace, number) key reports 126 "collisions", almost all of which are
#       TASK-BE-001 legitimately existing in ecommerce, iam and wms at once.
#       Project-scoped, the real count is 18.
#
#   (2) PARSE THE FILENAME, NOT THE BODY.
#       The ticket said the in-body `# Task ID` block was authoritative "because
#       the filename is a slug that drifts". The tree says the opposite: 735 of
#       1991 task files carry no body ID at all, and in the *active* queues it is
#       10 of 17. A body-based predicate would inspect 7 files and report green
#       over the other 10 — a guard blind to 59% of what it guards is worse than
#       no guard, because its green is believed. Every filename parses (1991/1991).
#
#       The body form is still worth having as a cross-check; it is what caught
#       TASK-BE-127-fix-TASK-BE-119.md declaring `TASK-BE-125` in its body. That
#       is a separate defect class (body/filename disagreement) and not this
#       guard's job.
#
#   (3) TAKE THE FIRST ID TOKEN IN THE BASENAME.
#       101 files name another ticket inside their own filename
#       (`TASK-MONO-016-fix-TASK-MONO-015.md`). Anchoring the match at the start
#       of the basename yields 016, not a phantom 015 duplicate. Verified against
#       all 101.
#
#   (4) GUARD THE ACTIVE QUEUES ONLY; SAY SO OUT LOUD (§ G8).
#       All 18 project-scoped collisions are in `done/`. `ready/`, `review/` and
#       `in-progress/` are clean, so the guard starts GREEN — which matters,
#       because a guard that is RED on day one gets switched off, and a switched
#       off job's skip reports green (§ G2).
#
#       `done/` is deliberately NOT guarded. Renaming a closed task to satisfy a
#       new check edits history to please a linter and breaks the references
#       other documents hold to it. The alternative — an allowlist of 18 frozen
#       entries — would be decoration: an allowlist is for deviations that go
#       away, and these never will.
#
#       WHAT THIS DOES NOT COVER, stated plainly:
#         - collisions already frozen in `done/` (83 by filename, 18 by body);
#         - a claim that has not been pushed yet. Two sessions can still both
#           pick 446; the second now finds out at PR time instead of never. This
#           closes the post-merge hole only.
#         - body/filename ID disagreement (see (2)).
# ---------------------------------------------------------------------------

set -euo pipefail

cd "$(dirname "$0")/.."

fail=0

# Population derived from the tree, not enumerated here (§ G7): every task file
# in an active lifecycle directory, root and per-project alike.
mapfile -t files < <(
  git ls-files 'tasks/*' 'projects/*' \
    | grep -E '(^|/)tasks/(ready|review|in-progress)/[^/]+\.md$' \
    || true
)

if [[ ${#files[@]} -eq 0 ]]; then
  echo "check-task-id-collision: no task files found in ready/ review/ in-progress/."
  echo "  That is almost certainly a bug in this script's population query, not an"
  echo "  empty repository. Failing rather than reporting a vacuous pass."
  exit 1
fi

declare -A seen

for f in "${files[@]}"; do
  base="${f##*/}"

  # Project scope: projects/<name>/tasks/... vs the root tasks/... queue.
  if [[ "$f" == projects/* ]]; then
    scope="${f#projects/}"
    scope="${scope%%/*}"
  else
    scope='<root>'
  fi

  # First ID token, anchored at the start of the basename — so
  # TASK-MONO-016-fix-TASK-MONO-015.md keys on 016.
  if [[ "$base" =~ ^(TASK-[A-Z]+(-[A-Z]+)*-[0-9]+[a-z]?(-[0-9]+[a-z]?)?) ]]; then
    id="${BASH_REMATCH[1]}"
  else
    echo "FAIL: cannot parse a task ID from filename: $f"
    echo "      expected TASK-<NAMESPACE>-<number>[suffix]-<slug>.md"
    fail=1
    continue
  fi

  key="${scope}//${id}"
  if [[ -n "${seen[$key]:-}" ]]; then
    echo "FAIL: duplicate task ID '${id}' in scope '${scope}'"
    echo "        ${seen[$key]}"
    echo "        ${f}"
    echo "      Per tasks/INDEX.md rule 4, the branch already merged to main keeps"
    echo "      the number; renumber the other (file + branch + references) before"
    echo "      merging."
    fail=1
  else
    seen[$key]="$f"
  fi
done

if [[ $fail -eq 0 ]]; then
  echo "check-task-id-collision: OK — ${#files[@]} active task files, no duplicate IDs."
  echo "  (done/ is intentionally unguarded; see the header for what that means.)"
fi

exit $fail
