#!/usr/bin/env bash
#
# check-ls-files-guard-count.sh — TASK-MONO-646
#
# Fails when the "N of the M `scripts/` guards read `git ls-files`" figure that
# `CLAUDE.md` and `platform/git-workflow-policy.md` state has drifted from the
# actual counts.
#
# WHY THIS EXISTS
# ---------------
# The rule those two files carry — *stage before you run a guard locally* —
# argues from a measurement: most guards derive their population from the git
# INDEX, so a pre-stage run asks a different question than CI asks. The rule
# survives a wrong number, but the number is what makes a reader believe it, and
# this repository has a name for figures nothing can falsify: they drift. One new
# guard is enough to make both sentences false, and nothing else in the repo
# would notice.
#
# The counts are DERIVED here, never hardcoded — adding a guard must update this
# check's expectation automatically, and the only thing that can fail is the
# prose that claims to know them.
#
# WHAT THIS DOES NOT COVER
# ------------------------
# - Whether each of those guards is CORRECT to use `git ls-files`. It usually is:
#   a guard asks about what will be committed. The rule is about WHEN you run it.
# - The three required-check names quoted alongside the figure. Those are pinned
#   by `scripts/check-required-check-names.sh`, which is the authority for them.
# - Guards that read the index some other way (`git diff --cached`, plumbing).
#   Widening the predicate would change the number this file and the prose agree
#   on, so it is a deliberate, documented boundary rather than an oversight.
#
set -euo pipefail

SELF_TEST=0
[ "${1:-}" = "--self-test" ] && SELF_TEST=1

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

HOMES=(
  "CLAUDE.md"
  "platform/git-workflow-policy.md"
)

# POPULATION, stated once so it cannot drift into two meanings:
#   TOTAL   = regular files directly under scripts/ . The two subdirectories are
#             not guards, so they are not in the denominator.
#   READERS = those whose text contains `ls-files`, MINUS this file. This script
#             names the string in order to count it; it does not build a
#             population from it, so counting itself would inflate the figure it
#             is guarding by exactly one.
#
# The pattern is the loose `ls-files`, NOT the literal `git ls-files`, and that
# is measured rather than assumed: `check-fetch-resolution.mjs` reaches the same
# plumbing as `execFileSync('git', ['-C', root, 'ls-files'])`, where the verb and
# its subcommand are separate argv elements. Requiring the contiguous string drops
# it — a true member lost to a tighter predicate, which is the failure mode this
# repository keeps paying for. The loose pattern can over-count a script that only
# MENTIONS the string; that direction is the safe one (it fails toward more work,
# and toward a human reading the diff), and today it over-counts nothing.
SELF="scripts/check-ls-files-guard-count.sh"
TOTAL="$(find scripts -maxdepth 1 -type f | wc -l | tr -d ' ')"
READERS="$(grep -rl 'ls-files' scripts 2>/dev/null | grep -vx "$SELF" | wc -l | tr -d ' ')"
echo "measured: ${READERS} of ${TOTAL} scripts/ entries read git ls-files"

fail=0
for home in "${HOMES[@]}"; do
  [ -f "$home" ] || { echo "MISSING: $home"; fail=1; continue; }
  # The sentence shape both homes use: "<READERS> of the <TOTAL> `scripts/`"
  if ! grep -q "${READERS} of the ${TOTAL} " "$home"; then
    echo "DRIFT: $home does not state \"${READERS} of the ${TOTAL} …\"."
    echo "       It currently says:"
    grep -n -oE '[0-9]+ of the [0-9]+ (guards under )?`?scripts/`?' "$home" | sed 's/^/         /' || \
      echo "         (no \"N of the M \`scripts/\`\" figure found at all)"
    fail=1
  fi
done

if [ "$SELF_TEST" = "1" ]; then
  # The check must be able to FAIL. Prove it against a tree where the figure is
  # wrong, rather than trusting that a green run means the predicate bites.
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  mkdir -p "$tmp/scripts"
  cp CLAUDE.md "$tmp/CLAUDE.md"
  # A population of one, so no honest prose could say "20 of the 52".
  printf '#!/usr/bin/env bash\ngit ls-files\n' > "$tmp/scripts/only-one.sh"
  (
    cd "$tmp"
    TOTAL="$(find scripts -maxdepth 1 -type f | wc -l | tr -d ' ')"
    READERS="$(grep -rl 'ls-files' scripts 2>/dev/null | wc -l | tr -d ' ')"
    if grep -q "${READERS} of the ${TOTAL} " CLAUDE.md; then
      echo "SELF-TEST FAILED: predicate matched a tree it must reject"
      exit 1
    fi
  ) || exit 1
  echo "self-test: predicate rejects a tree whose counts do not match the prose — OK"
fi

if [ "$fail" -ne 0 ]; then
  echo
  echo "check-ls-files-guard-count: FAILED — the stated figure has drifted."
  echo "Fix the sentence in each home above, not this script: the counts are derived."
  exit 1
fi

echo "check-ls-files-guard-count: OK — ${#HOMES[@]} homes agree with the measured ${READERS}/${TOTAL}."
