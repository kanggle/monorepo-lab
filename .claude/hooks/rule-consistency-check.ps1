$reader    = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)
$inputJson = $reader.ReadToEnd()

function Write-Block {
    param([string]$Stanza)
    $result = @{
        decision = "block"
        reason   = $Stanza
    }
    $result | ConvertTo-Json -Compress
    exit 0
}

try {
    $data = $inputJson | ConvertFrom-Json
    $filePath = ""

    if ($data.tool_input -and $data.tool_input.file_path) {
        $filePath = $data.tool_input.file_path
    }

    # Only check rule-related files
    $isRuleFile = $false
    $fileType = ""

    if ($filePath -match 'CLAUDE\.md$') {
        $isRuleFile = $true
        $fileType = "claude-md"
    }
    elseif ($filePath -match '\.claude[\\/]skills[\\/]' -and $filePath -notmatch 'README\.md$') {
        $isRuleFile = $true
        $fileType = "skill"
    }
    elseif ($filePath -match '\.claude[\\/]agents[\\/]' -and $filePath -notmatch 'README\.md$') {
        $isRuleFile = $true
        $fileType = "agent"
    }
    elseif ($filePath -match '\.claude[\\/]commands[\\/]' -and $filePath -notmatch 'README\.md$') {
        $isRuleFile = $true
        $fileType = "command"
    }
    elseif ($filePath -match 'specs[\\/]services[\\/]') {
        $isRuleFile = $true
        $fileType = "service-spec"
    }

    if (-not $isRuleFile) {
        # Not a rule file — allow silently
        exit 0
    }

    # Validate the RESULTING file, not the edit fragment.
    # Write supplies the full file in `content`; Edit supplies only old/new_string
    # fragments, so reconstruct the post-edit file = on-disk content with
    # old_string replaced by new_string (literal). Falling back to on-disk content
    # when the fragment can't be located keeps the whole-file frontmatter checks
    # correct (a body edit never removes frontmatter). Validating the fragment
    # alone falsely flags every partial edit as "missing frontmatter".
    $content = ""
    if ($null -ne $data.tool_input.content) {
        $content = [string]$data.tool_input.content
    }
    elseif ($null -ne $data.tool_input.old_string) {
        if ($filePath -and (Test-Path -LiteralPath $filePath)) {
            $existing = Get-Content -Raw -LiteralPath $filePath
            $content = $existing.Replace([string]$data.tool_input.old_string, [string]$data.tool_input.new_string)
        }
        else {
            $content = [string]$data.tool_input.new_string
        }
    }
    elseif ($null -ne $data.tool_input.new_string) {
        $content = [string]$data.tool_input.new_string
    }

    # Check 1: Skill files must reference a spec
    if ($fileType -eq "skill" -and $content -and $content -notmatch 'specs/') {
        $stanza = @"
[VIOLATION] RULE-CONSISTENCY-01: Skill file at ``$filePath`` does not reference any spec (no ``specs/...`` path in body).
[WHY] Skills must point at the source-of-truth spec they automate so future readers can trace skill behaviour back to the rule it enforces.
[REMEDIATION] Choose one:
  1. Add a reference to the source spec inside the skill body (e.g. ``Reference: specs/contracts/http/<name>.md``).
  2. If the skill is genuinely cross-cutting (no single spec source), document the rationale inline (``# No single spec — operates across <areas>``).
  3. If this is an early draft and the spec is not yet authored, file the spec first under ``<project>/specs/`` and return.
[REFERENCE] .claude/skills/INDEX.md (authoring convention)
"@
        Write-Block $stanza
    }

    # Check 2: Agent files must have required frontmatter fields
    if ($fileType -eq "agent" -and $content) {
        $gaps = @()
        if ($content -notmatch 'name:')   { $gaps += "missing 'name' frontmatter field" }
        if ($content -notmatch 'tools:')  { $gaps += "missing 'tools' frontmatter field" }
        if ($content -notmatch 'Does NOT') { $gaps += "missing 'Does NOT' constraints section" }
        if ($gaps.Count -gt 0) {
            $gapList = ($gaps | ForEach-Object { "  - $_" }) -join "`n"
            $stanza = @"
[VIOLATION] RULE-CONSISTENCY-02: Agent file at ``$filePath`` is missing required frontmatter / constraints:
$gapList
[WHY] Agents are dispatchable units that future sessions look up by name and capability; missing frontmatter fields break the dispatch catalog and missing constraints leak behaviour into other agents' scope.
[REMEDIATION] Choose one:
  1. Add the missing fields per ``.claude/agents/<group>/README.md`` template (``name:`` + ``tools:`` + ``Does NOT`` constraints block).
  2. If this is a template / partial, mark it with ``# DRAFT — do not register`` at the top and the hook will continue to warn (acceptable for intermediate state).
[REFERENCE] .claude/agents/<group>/README.md (agent authoring template)
"@
            Write-Block $stanza
        }
    }

    # Check 3: Command files must have frontmatter
    if ($fileType -eq "command" -and $content) {
        $gaps = @()
        if ($content -notmatch '^---')          { $gaps += "missing YAML frontmatter block (---name/description---)" }
        if ($content -notmatch 'name:')         { $gaps += "missing 'name' field" }
        if ($content -notmatch 'description:')  { $gaps += "missing 'description' field" }
        if ($gaps.Count -gt 0) {
            $gapList = ($gaps | ForEach-Object { "  - $_" }) -join "`n"
            $stanza = @"
[VIOLATION] RULE-CONSISTENCY-03: Command file at ``$filePath`` is missing required frontmatter:
$gapList
[WHY] Commands are slash-invocable from any session; without ``name`` / ``description`` the harness cannot list them in the user-invocable catalog.
[REMEDIATION] Choose one:
  1. Add the missing fields per ``.claude/commands/README.md`` template.
  2. If this is documentation about a command (not the command itself), move the file out of ``.claude/commands/`` to a docs path.
[REFERENCE] .claude/commands/README.md (command authoring template)
"@
            Write-Block $stanza
        }
    }

    # Check 4: Skill/Agent referencing non-existent spec files
    if (($fileType -eq "skill" -or $fileType -eq "agent") -and $content) {
        $specRefs = [regex]::Matches($content, 'specs/[a-zA-Z0-9/_-]+\.md')
        $broken = @()
        foreach ($ref in $specRefs) {
            $specPath = $ref.Value
            if (-not (Test-Path $specPath)) { $broken += $specPath }
        }
        if ($broken.Count -gt 0) {
            $brokenList = ($broken | ForEach-Object { "  - $_" }) -join "`n"
            $stanza = @"
[VIOLATION] RULE-CONSISTENCY-04: $fileType file at ``$filePath`` references non-existent spec files:
$brokenList
[WHY] Skills / agents must point at real specs — a broken reference is a silent dead branch in the rule graph that future readers cannot follow.
[REMEDIATION] Choose one:
  1. Fix the path — the spec may have moved (check ``git log --follow`` on the referenced file).
  2. Author the missing spec under ``<project>/specs/`` first, then return.
  3. Remove the broken reference if it was an obsolete pointer to deleted content.
[REFERENCE] .claude/skills/INDEX.md + .claude/agents/<group>/README.md
"@
            Write-Block $stanza
        }
    }
}
catch {}
