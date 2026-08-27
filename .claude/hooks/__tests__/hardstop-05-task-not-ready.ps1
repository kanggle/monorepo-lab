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
