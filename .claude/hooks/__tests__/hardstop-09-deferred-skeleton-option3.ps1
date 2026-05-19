# HARDSTOP-09 fixture — rule-sanctioned deferred-skeleton (Option 3) recognition.
#
# Provenance: TASK-MONO-120 (erp PR-B (g)(1) over-fire; finance PR-B was the
# same target). platform/hardstop-rules.md HARDSTOP-09 Remediation Option 3
# allows a reversible+local deferred skeleton: an inline citation of the choice
# + a follow-up tasks/ready/ task to backfill architecture.md. The hook must
# recognise that case (allow) but still fire when either signal is absent
# (fail-closed — not a blanket bypass).
#
# Two assertions:
#   positive — skeleton config WITH inline Option-3 citation + follow-up
#              tasks/ready/TASK-*-BE-* task referencing architecture.md → allow
#   negative — same skeleton path WITHOUT the citation → still HARDSTOP-09 block

. (Join-Path $PSScriptRoot '_helpers.ps1')

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..') | Select-Object -ExpandProperty Path

# ---- Positive: cited deferred skeleton + follow-up backfill task → allow ----
$posProj = "projects\test-erp-fixture-" + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$posDir  = Join-Path $repoRoot $posProj
$posRes  = Join-Path $posDir "apps\synth-md-svc\src\main\resources"
$posReady = Join-Path $posDir "tasks\ready"
New-Item -Path $posRes   -ItemType Directory -Force | Out-Null
New-Item -Path $posReady -ItemType Directory -Force | Out-Null
New-Item -Path (Join-Path $posDir "PROJECT.md") -ItemType File -Force | Out-Null
Set-Content -Path (Join-Path $posDir "PROJECT.md") -Value "---`ndomain: test`n---" -Encoding UTF8
# Follow-up task whose body references architecture.md (ADR-008/016 §D6.2 PR-B
# pattern: first BE task AC-1 owns architecture.md). Filename matches TASK-*-BE-*.
Set-Content -Path (Join-Path $posReady "TASK-SYNTH-BE-001-bootstrap.md") `
    -Value "# Task ID`n`nTASK-SYNTH-BE-001`n`n# Acceptance Criteria`n`n- [ ] AC-1: projects/<p>/specs/services/synth-md-svc/architecture.md MUST exist before domain impl." `
    -Encoding UTF8

# Realistic inline citation — mirrors the actual erp masterdata-service
# application.yml header form ("HARDSTOP-09 note (remediation option 3 — ...").
$citedYaml = @"
# -----------------------------------------------------------------------------
# synth deferred skeleton config.
#
# HARDSTOP-09 note (remediation option 3 — reversible + local skeleton, NO
# architecture decision is made in this file): the service architecture
# decision is owned by the ALREADY-FILED follow-up task
# tasks/ready/TASK-SYNTH-BE-001-bootstrap.md (AC-1: architecture.md MUST exist
# before any domain implementation).
# -----------------------------------------------------------------------------
spring:
  application:
    name: synth-md-svc
"@

try {
    $output = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
        tool_name  = 'Write'
        tool_input = @{
            file_path = (Join-Path $posRes "application.yml")
            content   = $citedYaml
        }
        cwd = $repoRoot
    }
    Assert-Allowed -Output $output
    "PASS: HARDSTOP-09 deferred-skeleton Option-3 cited + follow-up task -> allow"
}
finally {
    Remove-Item -Recurse -Force $posDir -ErrorAction SilentlyContinue
}

# ---- Negative: same skeleton path WITHOUT citation → still fires ----
$negProj = "projects\test-erp-fixture-" + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$negDir  = Join-Path $repoRoot $negProj
$negRes  = Join-Path $negDir "apps\synth-md-svc\src\main\resources"
New-Item -Path $negRes -ItemType Directory -Force | Out-Null
New-Item -Path (Join-Path $negDir "PROJECT.md") -ItemType File -Force | Out-Null
Set-Content -Path (Join-Path $negDir "PROJECT.md") -Value "---`ndomain: test`n---" -Encoding UTF8

$plainYaml = @"
spring:
  application:
    name: synth-md-svc
  datasource:
    url: jdbc:mysql://localhost/synth
"@

try {
    $output = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
        tool_name  = 'Write'
        tool_input = @{
            file_path = (Join-Path $negRes "application.yml")
            content   = $plainYaml
        }
        cwd = $repoRoot
    }
    Assert-Stanza -Output $output -ExpectedId 'HARDSTOP-09' -ExpectedDecision 'block'
    "PASS: HARDSTOP-09 deferred-skeleton WITHOUT citation -> still fires (fail-closed)"
}
finally {
    Remove-Item -Recurse -Force $negDir -ErrorAction SilentlyContinue
}
