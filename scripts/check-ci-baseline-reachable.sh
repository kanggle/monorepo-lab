#!/usr/bin/env bash
#
# check-ci-baseline-reachable.sh — TASK-MONO-374
#
# Guards the POST-MERGE BASELINE: the run a `push`-triggered workflow performs on
# main. It is the only run that can answer "is main green at this commit?" — a
# pull-request run only proves the PR was green against the main it was branched
# from, which is not the same claim.
#
# What went wrong (measured, 2026-07-13, `origin/main` a7d3a6862):
#
#   concurrency:
#     group: ci-${{ github.workflow }}-${{ github.ref }}   # <- every main push
#     cancel-in-progress: true                             #    shares ONE group
#
# For a push, `github.ref` is `refs/heads/main` for EVERY commit — so consecutive
# merges collapsed into one concurrency group and each merge cancelled the run
# that was still proving the previous one. This repo's own task lifecycle made
# that the norm rather than the exception: an impl PR merges, its integration
# suites start (~10 min), the close-chore PR for the same task merges minutes
# later, and the impl's run is killed. The chore run then skips every heavy job
# (it is markdown-only) and reports success.
#
#   over the last 20 main runs:   12 integration-job executions killed mid-flight
#   c4417b432 (TASK-MONO-371):     8 of 9 integration suites cancelled
#   bfa5d1c6b:                     cancelled before a single job materialised
#
# And a cancelled run is neither success nor failure. Nothing alerts, nothing goes
# red, no dashboard that counts failures counts it. The suites did not run and the
# repo could not tell you that they had not.
#
# Same failure class this repo keeps meeting — TASK-MONO-359 / TASK-MONO-360:
# A CHECK THAT DOES NOT RUN REPORTS GREEN. Learned there about guards; this is the
# same proposition about a workflow's own post-merge run.
#
# `nightly-e2e.yml` had the same bug with a second face: `schedule` also reports
# `github.ref` as `refs/heads/main`, so the ref-keyed group meant a merge to main
# could cancel the nightly cron run in flight.
#
# ---------------------------------------------------------------------------
# THE RULE
#
# A workflow that triggers on `push` and can cancel in-progress runs must make
# each pushed commit its own concurrency group. One of:
#
#   (a) `group:` contains `github.sha`     — every push gets a distinct group, so
#                                            no push can cancel another. Pull
#                                            requests keep the ref-keyed group and
#                                            keep cancelling superseded pushes,
#                                            which is what the setting is for.
#                                            This is the fix applied in MONO-374.
#
#   (b) `cancel-in-progress:` is `false`, or an expression on `github.event_name`
#       that is false for `push`           — e.g. `${{ github.event_name != 'push' }}`.
#
# ---------------------------------------------------------------------------
# WHAT THIS SCRIPT DOES NOT GUARD (known, deliberate — read before "fixing")
#
#   * Whether the baseline run actually PASSED. That is not a static property of
#     the workflow file and cannot be read from it. This guard only ensures the
#     run is allowed to finish and therefore able to report.
#
#   * Whether `cancel-in-progress` expressions other than the two shapes above
#     are correct. Deciding an arbitrary GitHub expression is undecidable here, so
#     form (b) is accepted whenever it references `github.event_name` — with the
#     one shape that is unambiguously wrong (`event_name == 'push'`) rejected
#     explicitly. Widen the predicate if a third correct shape appears; do NOT add
#     an allowlist. An allowlist here would hide exactly the regression this
#     guard exists to catch (TASK-MONO-368 § I4).
#
#   * Queueing. With cancellation off, GitHub keeps at most one PENDING run per
#     group; a third push can still evict a queued one. Form (a) sidesteps this
#     entirely (groups never collide), which is the other reason to prefer it.
#
#   * Reusable workflows (`on: workflow_call`) and cron-only workflows. Neither
#     runs on `push`, so neither can be cancelled by a merge.
#     `federation-hardening-e2e.yml` is cron-only and is correctly skipped.
#
# Usage: bash scripts/check-ci-baseline-reachable.sh
# Exit:  0 = every push-triggered workflow's baseline can finish
#        1 = a merge can cancel a baseline run (offending workflows printed)
#        2 = cannot run, or would pass vacuously
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WF_DIR="${WF_DIR:-$ROOT/.github/workflows}"

[ -d "$WF_DIR" ] || { echo "FATAL: cannot read $WF_DIR" >&2; exit 2; }

# Extract a top-level YAML block: everything indented under `^<key>:` up to the
# next top-level key. Used for `on:` and `concurrency:`.
block() {
  awk -v key="$1" '
    $0 ~ "^" key ":" { f = 1; next }
    f && /^[^[:space:]#]/ { f = 0 }
    f { print }
  '
}

fail=0
examined=0   # push-triggered workflows that CAN cancel — the population this
             # guard is about. Zero means it verified nothing.

for wf in "$WF_DIR"/*.yml "$WF_DIR"/*.yaml; do
  [ -e "$wf" ] || continue
  name="$(basename "$wf")"
  body="$(tr -d '\r' < "$wf")"

  # push-triggered? Read it out of the `on:` block, not the whole file — a job
  # step could legitimately mention "push:" and must not be mistaken for a trigger.
  printf '%s\n' "$body" | block on | grep -qE '^[[:space:]]+push:' || continue

  conc="$(printf '%s\n' "$body" | block concurrency)"
  # No concurrency block at all: nothing can cancel anything. Correct by default.
  [ -n "$conc" ] || continue

  group="$( printf '%s\n' "$conc" | grep -m1 -E '^[[:space:]]*group:'              | sed 's/^[^:]*:[[:space:]]*//' || true)"
  cancel="$(printf '%s\n' "$conc" | grep -m1 -E '^[[:space:]]*cancel-in-progress:' | sed 's/^[^:]*:[[:space:]]*//' || true)"

  # Cancellation off entirely, or absent: a push run can never be killed.
  case "$cancel" in
    ""|false|"false") continue ;;
  esac

  examined=$((examined + 1))

  # (a) per-commit group.
  case "$group" in
    *github.sha*) continue ;;
  esac

  # (b) cancel-in-progress gated on the event, and not gated the wrong way round.
  if printf '%s' "$cancel" | grep -q 'github\.event_name'; then
    if printf '%s' "$cancel" | grep -qE "event_name[[:space:]]*==[[:space:]]*'push'"; then
      echo "INVERTED  $name — cancel-in-progress is \`$cancel\`, which cancels on push and nothing else. That is the bug this guard exists for, spelled out."
      fail=1
    fi
    continue
  fi

  echo "BASELINE-CANCELLABLE  $name"
  echo "    group:              $group"
  echo "    cancel-in-progress: $cancel"
  echo "    Every push to a branch resolves this group to the same value (\`github.ref\` is"
  echo "    \`refs/heads/main\` for all of them), so the next merge cancels the run that is"
  echo "    still proving the last one — and a cancelled run reports neither success nor"
  echo "    failure. Put \`github.sha\` in the group for pushes:"
  echo ""
  echo "      group: ...-\${{ github.event_name == 'push' && github.sha || github.ref }}"
  echo ""
  echo "    Pull requests keep the ref-keyed group and keep cancelling superseded pushes."
  echo "    See TASK-MONO-374."
  fail=1
done

# Vacuity. If no push-triggered, cancellable workflow was found, every check above
# passed without examining anything — and this guard would report green for the
# rest of the repo's life while saying nothing. TASK-MONO-359's --require-coverage
# discipline: no signal is not a pass.
if [ "$examined" -eq 0 ]; then
  echo "FATAL: no push-triggered workflow with cancel-in-progress was found under $WF_DIR." >&2
  echo "       The guard would pass vacuously. Either the workflows moved, or the parser broke." >&2
  exit 2
fi

if [ "$fail" -ne 0 ]; then
  echo ""
  echo "A merge to main can cancel a post-merge baseline run. See TASK-MONO-374."
  exit 1
fi

echo "OK: all $examined push-triggered workflow(s) with cancel-in-progress give each pushed commit its own concurrency group — no merge can cancel another commit's baseline."
