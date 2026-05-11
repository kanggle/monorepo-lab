# HARDSTOP-05 fixture — body edit on a task file inside tasks/in-progress/ or tasks/review/.
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
