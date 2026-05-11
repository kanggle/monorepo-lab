$reader    = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)
$inputJson = $reader.ReadToEnd()

try {
    $data = $inputJson | ConvertFrom-Json
    $filePath = ""

    if ($data.tool_input -and $data.tool_input.file_path) {
        $filePath = $data.tool_input.file_path
    }

    # Warn when editing contract files (http/event contracts)
    if ($filePath -match 'specs[\\/]contracts[\\/]') {
        $stanza = @"
[VIOLATION] SPEC-CHECK-01: Contract file edit at ``$filePath`` — contract changes must precede implementation.
[WHY] HTTP and event contracts MUST be updated *before* implementation per CLAUDE.md § Layer Rules; implementing first and "documenting later" produces contract-vs-code drift that consumers cannot rely on.
[REMEDIATION] Choose one:
  1. Confirm this contract edit is part of a contract-first PR (spec lands before — or together with — the impl commit). Proceed with the edit.
  2. If this edit was triggered by impl work that started before contract update, PAUSE, revert the impl, land the contract change first, then resume.
  3. If the change is non-substantive (typo / formatting), confirm intent and proceed.
[REFERENCE] CLAUDE.md § Layer Rules + .claude/skills/INDEX.md (design-api / design-event)
"@
        $result = @{
            decision = "ask"
            reason   = $stanza
        }
        $result | ConvertTo-Json -Compress
        exit 0
    }

    # Warn when editing platform specs
    if ($filePath -match '[\\/]platform[\\/]' -or $filePath -match '^platform[\\/]') {
        $stanza = @"
[VIOLATION] SPEC-CHECK-02: Platform spec edit at ``$filePath`` — highest-priority source of truth.
[WHY] ``platform/`` files are the highest-priority source of truth across the monorepo (see CLAUDE.md § Source of Truth Priority layer 5); a casual edit can ripple into every project's interpretation of common rules.
[REMEDIATION] Choose one:
  1. Confirm this platform edit is task-driven (the task spec lives in ``tasks/ready/`` and lists this file under Related Specs). Proceed.
  2. If the edit is exploratory and no task references it yet, PAUSE, file a ``tasks/ready/TASK-MONO-XXX-<slug>.md`` first, then return.
  3. If the change is documentation-only (typo / formatting / link fix), confirm intent and proceed.
[REFERENCE] CLAUDE.md § Source of Truth Priority + tasks/INDEX.md § Move Rules
"@
        $result = @{
            decision = "ask"
            reason   = $stanza
        }
        $result | ConvertTo-Json -Compress
        exit 0
    }
}
catch {}
