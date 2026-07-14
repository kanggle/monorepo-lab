# HARDSTOP-10 fixture — architecture.md Write without a recognised Service Type declaration.
. (Join-Path $PSScriptRoot '_helpers.ps1')

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..') | Select-Object -ExpandProperty Path

# Synthetic project: projects/test-svctype-fixture/{PROJECT.md, specs/services/synth-svc/architecture.md}
$tempProj = "projects\test-svctype-fixture-" + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$projDir = Join-Path $repoRoot $tempProj
$archDir = Join-Path $projDir "specs\services\synth-svc"
New-Item -Path $archDir -ItemType Directory -Force | Out-Null
New-Item -Path (Join-Path $projDir "PROJECT.md") -ItemType File -Force | Out-Null
Set-Content -Path (Join-Path $projDir "PROJECT.md") -Value "---`ndomain: test`n---" -Encoding UTF8

try {
    $archFile = Join-Path $archDir "architecture.md"

    # Positive case: Write without Service Type
    $positiveContent = @"
# synth-svc Architecture

## Style

Hexagonal.

## Notes

No service type declared here intentionally for the test.
"@
    $positiveOutput = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
        tool_name  = 'Write'
        tool_input = @{
            file_path = $archFile
            content   = $positiveContent
        }
        cwd = $repoRoot
    }
    Assert-Stanza -Output $positiveOutput -ExpectedId 'HARDSTOP-10' -ExpectedDecision 'block'
    "PASS: HARDSTOP-10 positive (no Service Type header)"

    # Negative case 1 (legacy form): Write WITH valid Service Type via `## Service Type` H2 + body
    # Preserves historical form coverage — pre-ADR-MONO-012 architecture.md baseline.
    $negativeLegacyContent = @"
# synth-svc Architecture

## Service Type

``rest-api``

## Style

Hexagonal.
"@
    $negativeLegacyOutput = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
        tool_name  = 'Write'
        tool_input = @{
            file_path = $archFile
            content   = $negativeLegacyContent
        }
        cwd = $repoRoot
    }
    Assert-Allowed -Output $negativeLegacyOutput
    "PASS: HARDSTOP-10 negative legacy form (## Service Type H2 + body)"

    # Negative case 2 (canonical form per ADR-MONO-012 D1): Identity table + `### Service Type Composition` H3.
    # Validates that ADR-MONO-012 ACCEPTED form (WMS Identity-table canonical, MONO-095 SCM batch baseline)
    # passes the hook. Regression guard for next migration batches (MONO-097 GAP / MONO-098 ecommerce).
    $negativeCanonicalContent = @"
# synth-svc — Architecture

This document declares the internal architecture of ``synth-svc``.

---

## Identity

| Field | Value |
|---|---|
| Service Name | ``synth-svc`` |
| Service Type | ``rest-api`` (single — see Service Type Composition below) |
| Architecture Style | **Hexagonal** (Ports & Adapters) |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | Synthetic test fixture context |
| Deployable unit | ``apps/synth-svc/`` |
| Data store | PostgreSQL (owned) |
| Event publication | none |

### Service Type Composition

``synth-svc`` is a single-type ``rest-api`` service per
``platform/service-types/INDEX.md``. No secondary composition — synchronous
HTTP request/response surface only.

---

## Style

Hexagonal.
"@
    $negativeCanonicalOutput = Invoke-Hook -HookName 'hardstop-detect.ps1' -Payload @{
        tool_name  = 'Write'
        tool_input = @{
            file_path = $archFile
            content   = $negativeCanonicalContent
        }
        cwd = $repoRoot
    }
    Assert-Allowed -Output $negativeCanonicalOutput
    "PASS: HARDSTOP-10 negative canonical form (Identity table + ### Service Type Composition H3)"
}
finally {
    Remove-Item -Recurse -Force $projDir -ErrorAction SilentlyContinue
}
