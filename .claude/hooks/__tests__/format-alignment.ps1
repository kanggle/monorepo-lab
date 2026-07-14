# Format-alignment fixtures — spec-check.ps1 + rule-consistency-check.ps1 emit 4-block stanzas.
. (Join-Path $PSScriptRoot '_helpers.ps1')

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..') | Select-Object -ExpandProperty Path

# spec-check SPEC-CHECK-01 (contract edit)
$o1 = Invoke-Hook -HookName 'spec-check.ps1' -Payload @{
    tool_name = 'Edit'
    tool_input = @{
        file_path  = (Join-Path $repoRoot "projects\wms-platform\specs\contracts\http\example.md")
        old_string = 'a'
        new_string = 'b'
    }
    cwd = $repoRoot
}
Assert-Stanza -Output $o1 -ExpectedId 'SPEC-CHECK-01' -ExpectedDecision 'ask'
"PASS: SPEC-CHECK-01 (contract edit 4-block)"

# spec-check SPEC-CHECK-02 (platform edit)
$o2 = Invoke-Hook -HookName 'spec-check.ps1' -Payload @{
    tool_name = 'Edit'
    tool_input = @{
        file_path  = (Join-Path $repoRoot "platform\example.md")
        old_string = 'a'
        new_string = 'b'
    }
    cwd = $repoRoot
}
Assert-Stanza -Output $o2 -ExpectedId 'SPEC-CHECK-02' -ExpectedDecision 'ask'
"PASS: SPEC-CHECK-02 (platform edit 4-block)"

# rule-consistency RULE-CONSISTENCY-01 (skill missing spec ref)
$o3 = Invoke-Hook -HookName 'rule-consistency-check.ps1' -Payload @{
    tool_name = 'Write'
    tool_input = @{
        file_path = (Join-Path $repoRoot ".claude\skills\test-skill\SKILL.md")
        content   = "# Test skill`n`nThis skill has no spec reference at all."
    }
    cwd = $repoRoot
}
Assert-Stanza -Output $o3 -ExpectedId 'RULE-CONSISTENCY-01' -ExpectedDecision 'block'
"PASS: RULE-CONSISTENCY-01 (skill missing spec 4-block)"

# rule-consistency RULE-CONSISTENCY-02 (agent missing frontmatter)
$o4 = Invoke-Hook -HookName 'rule-consistency-check.ps1' -Payload @{
    tool_name = 'Write'
    tool_input = @{
        file_path = (Join-Path $repoRoot ".claude\agents\test\agent.md")
        content   = "---`ndescription: fake`n---`n# Test agent`n`nNo Does NOT section."
    }
    cwd = $repoRoot
}
Assert-Stanza -Output $o4 -ExpectedId 'RULE-CONSISTENCY-02' -ExpectedDecision 'block'
"PASS: RULE-CONSISTENCY-02 (agent missing fields 4-block)"

# rule-consistency RULE-CONSISTENCY-03 (command missing frontmatter)
$o5 = Invoke-Hook -HookName 'rule-consistency-check.ps1' -Payload @{
    tool_name = 'Write'
    tool_input = @{
        file_path = (Join-Path $repoRoot ".claude\commands\test-cmd.md")
        content   = "# Test command`n`nBody without frontmatter."
    }
    cwd = $repoRoot
}
Assert-Stanza -Output $o5 -ExpectedId 'RULE-CONSISTENCY-03' -ExpectedDecision 'block'
"PASS: RULE-CONSISTENCY-03 (command missing frontmatter 4-block)"

# rule-consistency RULE-CONSISTENCY-04 (skill referencing non-existent spec)
$o6 = Invoke-Hook -HookName 'rule-consistency-check.ps1' -Payload @{
    tool_name = 'Write'
    tool_input = @{
        file_path = (Join-Path $repoRoot ".claude\skills\test-skill\SKILL.md")
        content   = "# Test skill`n`nReference: specs/contracts/http/nonexistent-spec-xyzzy-12345.md"
    }
    cwd = $repoRoot
}
Assert-Stanza -Output $o6 -ExpectedId 'RULE-CONSISTENCY-04' -ExpectedDecision 'block'
"PASS: RULE-CONSISTENCY-04 (broken spec ref 4-block)"
