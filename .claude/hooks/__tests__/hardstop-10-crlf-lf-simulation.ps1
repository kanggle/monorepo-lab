# HARDSTOP-10 CRLF/LF simulation fixture (TASK-MONO-102).
#
# Regression guard for the 4-instance Edit hook CRLF/LF simulation mismatch
# (MONO-083 / MONO-093 / MONO-095 / MONO-101). Before this fix, an Edit whose
# oldString was LF-normalized while the on-disk file was CRLF caused
# $existing.Contains($oldString) to return false → simContent silently fell back
# to $existing → newly-added Service Type Composition H3 / catalog value was
# invisible to HARDSTOP-10 detection, and the hook fired a false-positive block.
#
# Synth scenario:
# - architecture.md written with explicit CRLF line endings, NO Service Type
#   header (would normally fire HARDSTOP-10).
# - Edit invocation supplies LF-normalized oldString matching the table tail,
#   newString adding a `### Service Type Composition` H3 with a valid catalog
#   value (`rest-api`) within the 600-char detection window.
# - Post-fix: hook's normalize fallback path produces simContent containing the
#   new H3 + catalog value → HARDSTOP-10 allows (no block).
# - Pre-fix (verified by stashing only the hook change): fixture would FAIL with
#   the hook emitting the HARDSTOP-10 stanza instead of silently allowing.

. (Join-Path $PSScriptRoot '_helpers.ps1')

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..') | Select-Object -ExpandProperty Path

$tempProj = "projects\test-crlf-lf-" + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$projDir  = Join-Path $repoRoot $tempProj
$archDir  = Join-Path $projDir "specs\services\synth-svc"
New-Item -Path $archDir -ItemType Directory -Force | Out-Null
New-Item -Path (Join-Path $projDir "PROJECT.md") -ItemType File -Force | Out-Null
Set-Content -Path (Join-Path $projDir "PROJECT.md") -Value "---`ndomain: test`n---" -Encoding UTF8

try {
    $archFile = Join-Path $archDir "architecture.md"

    # Write the file with explicit CRLF line endings + NO Service Type header.
    $crlfContent = "# synth-svc — Architecture`r`n`r`n## Identity`r`n`r`n| Field | Value |`r`n|---|---|`r`n| Service Name | ``synth-svc`` |`r`n`r`n---`r`n`r`n## Notes`r`n`r`nSynth fixture — pre-Composition-H3 state.`r`n"
    [System.IO.File]::WriteAllText($archFile, $crlfContent, [System.Text.UTF8Encoding]::new($false))

    # Edit invocation: LF-normalized oldString (the trailing Identity-table row +
    # the `---` separator + `## Notes` heading), newString adds the
    # `### Service Type Composition` H3 + `rest-api` catalog value before `---`.
    $oldLf = "| Service Name | ``synth-svc`` |`n`n---`n`n## Notes"
    $newLf = "| Service Name | ``synth-svc`` |`n`n### Service Type Composition`n`n``synth-svc`` is a single-type ``rest-api`` service per ``platform/service-types/INDEX.md``.`n`n---`n`n## Notes"

    $output = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
        tool_name  = 'Edit'
        tool_input = @{
            file_path  = $archFile
            old_string = $oldLf
            new_string = $newLf
        }
        cwd = $repoRoot
    }
    Assert-Allowed -Output $output
    "PASS: HARDSTOP-10 CRLF/LF simulation fallback (Edit LF oldString against CRLF file allows when H3 + catalog value added)"
}
finally {
    Remove-Item -Recurse -Force $projDir -ErrorAction SilentlyContinue
}
