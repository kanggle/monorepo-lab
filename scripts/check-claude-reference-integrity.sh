#!/usr/bin/env bash
#
# check-claude-reference-integrity.sh — TASK-MONO-468 (commands), TASK-MONO-469
# (widened to agents)
#
# Fails when an author-facing doc under `.claude/` — a slash-command definition
# (`.claude/commands/*.md`) or an agent definition (`.claude/agents/**/*.md`) —
# points at a repo path that does not exist, in either of the two shapes these
# docs use to name a file:
#
#   markdown link   [text](../../platform/refactoring-policy.md)  — a clickable
#                   link. Resolved RELATIVE TO THE DOC FILE, exactly the way
#                   GitHub renders it. A link that resolves nowhere is a dead link
#                   whether or not the target once existed.
#
#   anchored path   `.claude/skills/INDEX.md` in inline code — a path mention that
#                   BEGINS WITH A REPO-ROOT-ANCHORED PREFIX. Resolved from the
#                   repository root. `platform/`, `rules/`, `tasks/`, `docs/`,
#                   `scripts/`, `libs/`, `projects/`, `.claude/`, `.github/` are
#                   real roots; `skills/`, `agents/`, `commands/`, `workflows/`
#                   name directories that exist ONLY under `.claude/`, so a bare
#                   `skills/INDEX.md` is by construction a dead reference — that is
#                   the exact defect this guard was born from (below). `config/`
#                   and `hooks/` are pointedly NOT in that list; see the ANCHOR_RE
#                   comment for why (MONO-469 measured them as false positives).
#
# WHY THIS EXISTS
# ---------------
# `/validate-rules` § 2-4/2-5 already require that "command procedures reference
# existing specs and skills" and that "all `.claude/skills/**/SKILL.md` paths
# referenced ... exist". The rule is written down. Nothing ran it. On 2026-07-22 a
# full manual scan (TASK-MONO-467) found `validate-rules.md` itself citing
# `skills/INDEX.md` three times — a path with no `.claude/` prefix, resolving to
# nothing, sitting inside the very command whose job is to catch dead references.
# It had been wrong long enough that every other command, agent, skill and hook in
# the tree had settled on the full `.claude/skills/INDEX.md` form around it.
#
# It survived because the command layer is a GUARD-LESS surface. INDEX drift,
# service-type drift, ADR-index drift, error-code drift, controller-slice naming —
# each has a CI job that bites on every PR. Command-reference integrity had only
# `/validate-rules`, which is read-only, manual, and by its own words "reports
# drift but never blocks via hooks". A rule that only a human remembering to run a
# scan can enforce is the same failure class this repo has paid for repeatedly
# (MONO-360 / MONO-363 / MONO-451): a hand-kept correspondence that nothing
# compares against the truth. This job compares it on every PR that can break it.
#
# WHAT THIS GUARD DOES NOT COVER (deliberate — read before "improving")
# --------------------------------------------------------------------
# This is a REFERENCE-INTEGRITY guard: does the path resolve. It does NOT police
# the three SEMANTIC drifts MONO-467 also fixed, and it must not pretend to:
#
#   * prioritisation order in `refactor-code.md` disagreeing with
#     `platform/refactoring-policy.md` § Prioritization,
#   * `process-tasks.md` summarising a dispatch rule more loosely than the
#     `implement-task.md` it defers to,
#   * a `§ "..."` anchor label quoted from CLAUDE.md not matching the heading
#     verbatim.
#
# Catching those means parsing English claims and comparing meanings across files.
# A checker that had to do that would be a false-positive generator, and a guard
# that is red on legitimate prose gets switched off — and a switched-off job's
# skip reports green (MONO-360). Those stay `/validate-rules` (manual) territory,
# by design. The heading-anchor slug of a markdown link (`file.md#heading`) is
# also NOT verified here for the same day-one-red reason: GitHub's slug algorithm
# has enough corners (Unicode, emoji, duplicate headings) that pinning it would
# start red on the Korean-and-emoji headings this repo's docs actually use.
#
# Scope is `.claude/commands/*.md` AND `.claude/agents/**/*.md` (excluding
# README.md, which documents folder conventions and carries no repo-path links).
#
# SKILLS ARE DELIBERATELY EXCLUDED — measured, not assumed (TASK-MONO-469).
# MONO-468 noted "agent and skill are a natural follow-up". Extending to agents was
# clean: 13 agent docs, zero findings, day-one green. Extending to
# `.claude/skills/**/SKILL.md` was NOT — the first run produced 11 findings and all
# 11 were false positives, because a skill is a TEACHING document, not an
# operational one:
#   * `config/` / `hooks/` in an ASCII layout diagram name a Spring package and a
#     React folder, not `.claude/config` / `.claude/hooks`;
#   * `.github/workflows/example-frontend.yml`, `.github/workflows/proto.yml` are
#     example CI files the skill tells the reader to CREATE — they are not meant to
#     exist yet;
#   * `projects/...` is a prose ellipsis, not a path.
# Distinguishing "a file the reader will create / a code dir I am describing" from
# "a repo file I am pointing at" inside a teaching doc is exactly the prose-parsing
# a reference guard must not attempt. Two of those FP classes sit under REAL roots
# (`.github/`, `projects/`), so no anchor-list tweak rescues skills — the genre
# does. Skill reference integrity stays with `/validate-rules` (manual). The
# `config/`+`hooks/` removal from ANCHOR_RE below is the one lesson from that
# measurement that DID generalise back to the agent corpus and is kept.
#
# WHAT IS AND IS NOT A CHECKABLE REFERENCE (the false-positive frontier)
# ---------------------------------------------------------------------
# A command/agent file is mostly prose and code fragments. Only two token shapes
# are treated as references; everything else is ignored ON PURPOSE:
#
#   ignored: placeholders   `specs/services/<service>/architecture.md`,
#            `apps/<service>/src/`, `projects/*/apps/*/src/**` — a `<`, `{`, `*`,
#            `$`, `?`, `(`, `|`, `%`, space or backtick means it is a template,
#            not a literal file. Checking it would be a false-positive generator.
#   ignored: non-anchored   `specs/...`, `PROJECT.md`, `build.gradle.kts` — a
#            command runs INSIDE many projects, so a project-relative path names
#            no single file at the repo root and is not this guard's business.
#   ignored: identifiers    `backend-engineer`, `model=opus`, `--dry-run` — no
#            slash, not a path.
#   ignored: URLs / bare anchors  `https://...`, `#section`.
#
# `--selftest` pins this frontier with known positives AND known negatives, so a
# future edit that quietly starts extracting `model=opus` as a path fails loudly
# instead of red-Xing the repo.
#
# USAGE
#   scripts/check-claude-reference-integrity.sh              # guard the repo
#   scripts/check-claude-reference-integrity.sh --selftest   # assert the extractor
# Exit: 0 = every reference resolves, 1 = a dead reference (printed), 2 = cannot run
# ---------------------------------------------------------------------------

set -euo pipefail

cd "$(dirname "$0")/.."

# A token is a CHECKABLE anchored path when it begins with one of these prefixes.
# The first nine are real repo roots; the last four are `.claude/` subdirectories
# that exist nowhere else, so writing one bare (e.g. `skills/INDEX.md`) is itself
# the drift. `config/` and `hooks/` are deliberately NOT in that second group:
# they are also ubiquitous CODE directory names (a Spring `config/` package, a
# React `hooks/` folder), and TASK-MONO-469 measured them producing false
# positives the moment the population widened past command files. They remain
# checkable only in explicit `.claude/config/...` / `.claude/hooks/...` form, via
# the `.claude` root prefix above.
ANCHOR_RE='^(platform|rules|libs|tasks|docs|scripts|projects|\.claude|\.github|skills|agents|commands|workflows)/'

# Any of these characters makes a token a placeholder/glob/expression, not a
# literal file. (Backtick can appear if inline-code extraction ever misfires.)
PLACEHOLDER_RE='[<>{}*?$()|% `]'

# extract_refs <command-file>
#   emits: "LINK<TAB><target>"    one per local markdown-link target
#          "PATH<TAB><token>"     one per anchored inline-code path token
# The FP frontier lives here; --selftest pins it.
extract_refs() {
  local f="$1"
  # strip CR so Windows-authored files parse identically to CI's Linux checkout
  local body links spans
  body="$(tr -d '\r' < "$f")"

  # `grep` exits 1 when a file has no links / no inline code. Under this script's
  # `set -euo pipefail` that non-zero would propagate through the pipe and abort
  # the run — the trap MONO-442 hit in CI while a pipefail-less local run passed.
  # Capture with `|| true` so "no match" is data (empty), not a failure.

  # --- markdown links: [text](target) --------------------------------------
  # Extracted from the body with FENCED CODE BLOCKS REMOVED: GitHub does not
  # render a link inside ```...```, so `Agent[R-1](worktree-1)` in an ASCII
  # architecture diagram is not a link — extracting it produced 10 false
  # positives on the first run. A checkable target must also CONTAIN A '/':
  # real intra-repo doc links here are always pathful (`../../platform/x.md`),
  # whereas a bare `[Title](file.md)` or `[x](...)` is an illustrative format
  # example, not a live link. Drop URLs and same-file "#anchor" links; strip a
  # trailing "#anchor" so the file half is what gets resolved.
  links="$(printf '%s\n' "$body" \
    | awk '/^[[:space:]]*```/{fence=!fence; next} !fence' \
    | grep -oE '\]\([^)]+\)' || true)"
  if [ -n "$links" ]; then
    printf '%s\n' "$links" \
      | sed -E 's/^\]\(//; s/\)$//' \
      | while IFS= read -r target; do
          [ -n "$target" ] || continue
          case "$target" in
            http://*|https://*|mailto:*|'#'*) continue ;;
          esac
          target="${target%%#*}"          # drop #anchor
          [ -n "$target" ] || continue    # was a pure #anchor
          case "$target" in */*) ;; *) continue ;; esac   # must be pathful
          printf 'LINK\t%s\n' "$target"
        done
  fi

  # --- anchored inline-code paths: `...` -----------------------------------
  # One inline-code span per output line. A span is a checkable path only when it
  # is anchored AND carries no placeholder metacharacter.
  spans="$(printf '%s\n' "$body" | grep -oE '`[^`]+`' || true)"
  if [ -n "$spans" ]; then
    printf '%s\n' "$spans" \
      | sed -E 's/^`//; s/`$//' \
      | while IFS= read -r tok; do
          [ -n "$tok" ] || continue
          [[ "$tok" =~ $ANCHOR_RE ]] || continue
          [[ "$tok" =~ $PLACEHOLDER_RE ]] && continue
          printf 'PATH\t%s\n' "$tok"
        done
  fi
}

# ---------------------------------------------------------------------------
# --selftest: never trust an empty detector output. Assert the extractor's
# classification against known positives and negatives before the guard runs.
# ---------------------------------------------------------------------------
run_selftest() {
  local st_fail=0 fx got expected
  fx="$(mktemp)"
  cat > "$fx" <<'FIXTURE'
See [`platform/refactoring-policy.md`](../../platform/refactoring-policy.md) — authoritative.
Read `.claude/skills/INDEX.md` and the matched skill.
Older text said read `skills/INDEX.md` (bare — the bug this guard exists for).
Reference `platform/does-not-exist.md` for nothing.
Placeholder: `specs/services/<service>/architecture.md` must be IGNORED.
Glob: `projects/*/apps/*/src/**` must be IGNORED.
Project-relative `specs/contracts/http/` must be IGNORED (not anchored).
Bare word `backend-engineer` and `model=opus` are not paths.
External [docs](https://example.com/x) and a [jump](#section) are not files.
A directory ref `.claude/config/` is anchored (explicit dot-claude prefix).
Code dirs `config/` and `hooks/query-keys.ts` are NOT anchored — IGNORE (MONO-469).
Illustrative [Title](file.md) and [ellipsis](...) links are NOT pathful — IGNORE.
```
Agent[R-1](worktree-1) in a fenced diagram is NOT a link — IGNORE.
```
FIXTURE
  expected='LINK	../../platform/refactoring-policy.md
PATH	.claude/config/
PATH	.claude/skills/INDEX.md
PATH	platform/does-not-exist.md
PATH	platform/refactoring-policy.md
PATH	skills/INDEX.md'
  got="$(extract_refs "$fx" | LC_ALL=C sort)"
  rm -f "$fx"
  if [[ "$got" != "$(printf '%s' "$expected" | LC_ALL=C sort)" ]]; then
    echo "SELFTEST FAIL: extractor classification mismatch."
    echo "--- expected ---"; printf '%s\n' "$expected"
    echo "--- got ---";      printf '%s\n' "$got"
    st_fail=1
  fi

  # No-match file: a prose-only command with no links and no inline code must
  # yield empty output and exit 0 — NOT abort under `set -euo pipefail` when the
  # internal greps find nothing (the MONO-442 trap). This pins the `|| true`.
  local nfx nout nrc
  nfx="$(mktemp)"
  printf 'Just prose. No links, no code spans at all.\n' > "$nfx"
  set +e
  nout="$(extract_refs "$nfx")"; nrc=$?
  set -e
  rm -f "$nfx"
  if [[ $nrc -ne 0 || -n "$nout" ]]; then
    echo "SELFTEST FAIL: no-match file gave rc=$nrc out='$nout' (want rc=0, empty) — pipefail trap."
    st_fail=1
  fi

  if [[ $st_fail -eq 0 ]]; then
    echo "check-claude-reference-integrity --selftest: OK (extractor frontier pinned)."
    echo "  asserts: markdown link extracted; inline path inside a link extracted;"
    echo "  bare '.claude'-subdir path ('skills/INDEX.md') extracted (the MONO-467 bug);"
    echo "  code dirs 'config/'/'hooks/' NOT anchored (MONO-469 measured FP);"
    echo "  placeholder <service>, glob '*', project-relative 'specs/', bare word,"
    echo "  URL and '#anchor' all IGNORED."
  fi
  return $st_fail
}

if [[ "${1:-}" == "--selftest" ]]; then
  run_selftest
  exit $?
fi

# ---------------------------------------------------------------------------
# Population from the tree, not enumerated here: every author-facing markdown
# under `.claude/` that points at repo files — command definitions, agent
# definitions, and skill bodies. README.md files document folder conventions and
# are not author instructions; they are excluded (and carry no repo-path links).
# ---------------------------------------------------------------------------
mapfile -t docs < <(
  git ls-files '.claude/commands/*.md' '.claude/agents/**/*.md' \
    | grep -v '/README\.md$' \
    | LC_ALL=C sort -u
)

if [[ ${#docs[@]} -eq 0 ]]; then
  echo "check-claude-reference-integrity: no .claude/ command/agent/skill docs found."
  echo "  That is a bug in this script's population query, not an empty repo."
  echo "  Failing rather than reporting a vacuous pass."
  exit 2
fi

fail=0
findings=0
checked=0

for doc in "${docs[@]}"; do
  doc_dir="$(dirname "$doc")"

  while IFS=$'\t' read -r kind ref; do
    [ -n "$kind" ] || continue
    checked=$((checked + 1))
    case "$kind" in
      LINK)
        # Resolve relative to the doc file, the way GitHub renders the link.
        # `[ -e ]` collapses ../ at the filesystem level, so any depth works.
        target="${ref%/}"
        if [ ! -e "$doc_dir/$target" ]; then
          echo "DEAD LINK  $doc"
          echo "           [..]($ref) resolves to '$doc_dir/$ref', which does not exist."
          findings=$((findings + 1))
          fail=1
        fi
        ;;
      PATH)
        token="${ref%/}"                 # tolerate a trailing slash on dir refs
        if [ ! -e "$token" ]; then
          echo "DEAD PATH  $doc"
          echo -n "           \`$ref\` does not exist at the repo root."
          # A bare '.claude'-subdir path is almost always a missing '.claude/' prefix.
          if [ -e ".claude/$token" ]; then
            echo " Did you mean '.claude/$ref'?"
          else
            echo ""
          fi
          findings=$((findings + 1))
          fail=1
        fi
        ;;
    esac
  done < <(extract_refs "$doc")
done

echo
if [[ $fail -eq 0 ]]; then
  echo "check-claude-reference-integrity: OK — ${#docs[@]} .claude/ doc(s), ${checked} reference(s) checked, all resolve."
  echo "  (Semantic drift — prioritisation order, dispatch wording, anchor labels —"
  echo "   is intentionally NOT guarded here; see the header and /validate-rules.)"
else
  echo "check-claude-reference-integrity: FAILED — ${findings} dead reference(s) across ${#docs[@]} .claude/ doc(s)."
  echo "  Fix the reference in the doc; never invent a file to satisfy it."
fi

exit $fail
