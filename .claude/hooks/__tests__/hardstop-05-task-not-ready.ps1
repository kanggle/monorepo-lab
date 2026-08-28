# HARDSTOP-05 fixture — body edit on a task file inside tasks/in-progress/, tasks/review/ or tasks/done/.
# Includes positive (body edit) and negative (Status-field lifecycle move) cases.
. (Join-Path $PSScriptRoot '_helpers.ps1')

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..') | Select-Object -ExpandProperty Path

# Positive case: multi-line body edit on a task file under tasks/review/
$reviewFile = Join-Path $repoRoot "tasks\review\TASK-MONO-EXAMPLE.md"
$positiveOutput = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Edit'
    tool_input = @{
        file_path  = $reviewFile
        old_string = "# Goal`n`nOriginal goal."
        new_string = "# Goal`n`nRevised goal — adding scope post-review."
    }
    cwd = $repoRoot
}
Assert-Stanza -Output $positiveOutput -ExpectedId 'HARDSTOP-05' -ExpectedDecision 'block'
"PASS: HARDSTOP-05 positive (body edit in review/)"

# Negative case: lifecycle Status-field single-token move (review -> done)
$negativeOutput = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Edit'
    tool_input = @{
        file_path  = $reviewFile
        old_string = "review"
        new_string = "done"
    }
    cwd = $repoRoot
}
Assert-Allowed -Output $negativeOutput
"PASS: HARDSTOP-05 negative (Status-field lifecycle move)"

# Negative case 2: multi-line contextual Status-field move (common Edit pattern with surrounding lines for uniqueness)
$negativeMultilineOutput = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Edit'
    tool_input = @{
        file_path  = $reviewFile
        old_string = "# Status`n`nready"
        new_string = "# Status`n`nreview"
    }
    cwd = $repoRoot
}
Assert-Allowed -Output $negativeMultilineOutput
"PASS: HARDSTOP-05 negative-2 (contextual Status-field move)"

# ===== TASK-MONO-402: tasks/done/ is frozen too =====
#
# The path regex used to cover only (in-progress|review) while this stanza's own
# [VIOLATION]/[WHY] text named `done` as frozen. The repo's record of what actually
# happened was the one lifecycle stage nothing guarded — an arbitrary edit to a
# done/ task file passed silently (measured, 2026-07-14).
$doneFile = Join-Path $repoRoot "tasks\done\TASK-MONO-EXAMPLE.md"

# Positive case: arbitrary body edit on a task file under tasks/done/ — must block.
# Before MONO-402 this was ALLOWED.
$donePositiveOutput = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Edit'
    tool_input = @{
        file_path  = $doneFile
        old_string = "# Goal`n`nOriginal goal."
        new_string = "# Goal`n`nRewriting history after the fact."
    }
    cwd = $repoRoot
}
Assert-Stanza -Output $donePositiveOutput -ExpectedId 'HARDSTOP-05' -ExpectedDecision 'block'
"PASS: HARDSTOP-05 positive-2 (body edit in done/) — MONO-402"

# Negative case 3 — THE ONE THAT MATTERS.
#
# The close chore (`/review-task` § Close Chore, introduced by TASK-MONO-396) does:
#   git mv tasks/review/X.md tasks/done/X.md   then   edit Status: review -> done
# That Status edit therefore happens INSIDE tasks/done/. If freezing done/ blocked it,
# this change would break every task close in the repo — fixing the guard by breaking
# the pipeline it guards. The lifecycle-move exception must keep clearing it.
$doneCloseChoreOutput = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Edit'
    tool_input = @{
        file_path  = $doneFile
        old_string = "# Status`n`nreview"
        new_string = "# Status`n`ndone"
    }
    cwd = $repoRoot
}
Assert-Allowed -Output $doneCloseChoreOutput
"PASS: HARDSTOP-05 negative-3 (close-chore Status move inside done/) — MONO-402"

# ===== TASK-MONO-589: tasks/in-progress/ is NOT frozen =====
#
# The path regex used to cover (in-progress|review|done) while every tasks/INDEX.md in
# the repo — all 9 of them, measured — named only `review/` and `done/` as frozen, AND
# defined `ready -> in-progress` as "Allowed only when implementation starts" with the
# impl PR "moving the task file through `in-progress/` to `review/`". A task therefore
# sits in in-progress/ for the entire implementation with nowhere to record what that
# implementation found. Practice had already picked the INDEX side: 51c0cff53 (#3452)
# edits an in-progress/ body and is an ancestor of main.
#
# 🔴 The two block cases above (review/, done/) are the NEGATIVE CONTROLS for these two.
# Without them, deleting the path regex outright would still read as green here.

# Positive-allow 1: arbitrary body edit on a ROOT task file under tasks/in-progress/.
# Before MONO-589 this was BLOCKED.
$inProgFile = Join-Path $repoRoot "tasks\in-progress\TASK-MONO-EXAMPLE.md"
$inProgOutput = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Edit'
    tool_input = @{
        file_path  = $inProgFile
        old_string = "## AC-2`n`nMeasure the thing."
        new_string = "## AC-2`n`nMeasure the thing.`n`nMeasured 2026-08-27: 9 files, not 1."
    }
    cwd = $repoRoot
}
Assert-Allowed -Output $inProgOutput
"PASS: HARDSTOP-05 allow-1 (body edit in root in-progress/) — MONO-589"

# Positive-allow 2: the same edit under a PROJECT task queue.
#
# 🔴 This case is not redundant. The regex anchors on `(?:^|/)tasks/`, so it bites
# projects/<name>/tasks/ identically — fixing only the root path would have left the
# same defect in all 8 projects. The root-only version of this fixture could not see that.
$projInProgFile = Join-Path $repoRoot "projects\fan-platform\tasks\in-progress\TASK-FAN-EXAMPLE.md"
$projInProgOutput = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Edit'
    tool_input = @{
        file_path  = $projInProgFile
        old_string = "## AC-2`n`nMeasure the thing."
        new_string = "## AC-2`n`nMeasure the thing.`n`nMeasured 2026-08-27: 9 files, not 1."
    }
    cwd = $repoRoot
}
Assert-Allowed -Output $projInProgOutput
"PASS: HARDSTOP-05 allow-2 (body edit in project in-progress/) — MONO-589"

# Positive control for the two allows above: the SAME edit shape under review/ must
# still block. Without this, an allow that fires because the payload was malformed
# (and the hook never reached the path check) would look identical to a real pass.
$sameShapeUnderReview = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Edit'
    tool_input = @{
        file_path  = $reviewFile
        old_string = "## AC-2`n`nMeasure the thing."
        new_string = "## AC-2`n`nMeasure the thing.`n`nMeasured 2026-08-27: 9 files, not 1."
    }
    cwd = $repoRoot
}
Assert-Stanza -Output $sameShapeUnderReview -ExpectedId 'HARDSTOP-05' -ExpectedDecision 'block'
"PASS: HARDSTOP-05 positive-3 (same edit shape under review/ still blocks) — MONO-589"

# ===== TASK-MONO-591: two narrow repairs on a frozen file =====
#
# 🔴 The danger this ticket was warned about is that a correction exception quietly
# widens into arbitrary editing and undoes TASK-MONO-402. Every cell below therefore
# comes in pairs: the permitted shape, and the SAME shape mutated just past the rule.

# --- (R1) conflict-marker repair -------------------------------------------------
# Real shape, measured: two ecommerce done/ files carry an unresolved conflict where
# both sides are lifecycle tokens, committed verbatim from a worktree-agent-* branch.
$conflictOld = "<<<<<<<< HEAD:tasks/done/TASK-BE-080-FIX.md`ndone`n========`nreview`n>>>>>>>> worktree-agent-a250ba6d:tasks/review/TASK-BE-080-FIX.md"

$r1Allow = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Edit'
    tool_input = @{ file_path = $doneFile; old_string = $conflictOld; new_string = "done" }
    cwd = $repoRoot
}
Assert-Allowed -Output $r1Allow
"PASS: HARDSTOP-05 allow-3 (conflict block collapsed to a side it already held) — MONO-591"

# 🔴 new_string must be a BARE token. A token plus anything else is a body edit wearing
# a repair's clothes — this is the cell that keeps R1 from becoming a hole.
$r1Block = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Edit'
    tool_input = @{ file_path = $doneFile; old_string = $conflictOld; new_string = "done`n`n# Goal`n`nRewritten." }
    cwd = $repoRoot
}
Assert-Stanza -Output $r1Block -ExpectedId 'HARDSTOP-05' -ExpectedDecision 'block'
"PASS: HARDSTOP-05 positive-4 (conflict repair carrying extra body still blocks) — MONO-591"

# 🔴 The token must already be a SIDE of the conflict. Otherwise the 'repair' invents a
# status the file never held.
$r1Invent = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Edit'
    tool_input = @{ file_path = $doneFile; old_string = $conflictOld; new_string = "backlog" }
    cwd = $repoRoot
}
Assert-Stanza -Output $r1Invent -ExpectedId 'HARDSTOP-05' -ExpectedDecision 'block'
"PASS: HARDSTOP-05 positive-5 (conflict repair to a value not in the conflict blocks) — MONO-591"

# --- (R2) append-only correction block --------------------------------------------
$tailOld = "## AC-3`n`nOriginal closing text."

$r2Allow = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Edit'
    tool_input = @{
        file_path  = $doneFile
        old_string = $tailOld
        new_string = "$tailOld`n`n## CORRECTION (post-close, 2026-08-28)`n`nThe header said AC-0/2 were pending; they were decided on 08-25."
    }
    cwd = $repoRoot
}
Assert-Allowed -Output $r2Allow
"PASS: HARDSTOP-05 allow-4 (append-only correction block) — MONO-591"

# 🔴🔴 THE CELL THAT MATTERS. Same correction heading, but one character of the original
# is altered. If this passes, the exception is a rewrite channel and the freeze is gone.
$r2Mutate = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Edit'
    tool_input = @{
        file_path  = $doneFile
        old_string = $tailOld
        new_string = "## AC-3`n`nOriginal closing text REWRITTEN.`n`n## CORRECTION (post-close)`n`nappended too."
    }
    cwd = $repoRoot
}
Assert-Stanza -Output $r2Mutate -ExpectedId 'HARDSTOP-05' -ExpectedDecision 'block'
"PASS: HARDSTOP-05 positive-6 (correction that alters the original still blocks) — MONO-591"

# 🔴 An append whose first content line is NOT the correction heading is just an append.
# Without this cell, R2 would allow appending anything at all to a frozen file.
$r2Unlabelled = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Edit'
    tool_input = @{
        file_path  = $doneFile
        old_string = $tailOld
        new_string = "$tailOld`n`n## Extra Findings`n`nSmuggled in without the label."
    }
    cwd = $repoRoot
}
Assert-Stanza -Output $r2Unlabelled -ExpectedId 'HARDSTOP-05' -ExpectedDecision 'block'
"PASS: HARDSTOP-05 positive-7 (unlabelled append still blocks) — MONO-591"

# 🔵 The close chore must still work. This is the AC-2 cell whose failure would break
# every close chore in the repo, so it is asserted last and explicitly.
$closeChoreStillWorks = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
    tool_name  = 'Edit'
    tool_input = @{ file_path = $doneFile; old_string = "# Status`n`nreview"; new_string = "# Status`n`ndone" }
    cwd = $repoRoot
}
Assert-Allowed -Output $closeChoreStillWorks
"PASS: HARDSTOP-05 allow-5 (close chore Status move survives both new exceptions) — MONO-591"
