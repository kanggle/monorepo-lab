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
