$reader    = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)
$inputJson = $reader.ReadToEnd()

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

    # Collect warnings
    $warnings = @()

    # Check 1: Skill files must reference a spec
    if ($fileType -eq "skill") {
        $content = ""
        if ($data.tool_input.content) {
            $content = $data.tool_input.content
        }
        elseif ($data.tool_input.new_string) {
            $content = $data.tool_input.new_string
        }

        if ($content -and $content -notmatch 'specs/') {
            $warnings += "Skill file does not reference any spec. Skills should reference their source-of-truth spec."
        }
    }

    # Check 2: Agent files must have required frontmatter fields
    if ($fileType -eq "agent") {
        $content = ""
        if ($data.tool_input.content) {
            $content = $data.tool_input.content
        }

        if ($content) {
            if ($content -notmatch 'name:') {
                $warnings += "Agent file missing 'name' in frontmatter."
            }
            if ($content -notmatch 'tools:') {
                $warnings += "Agent file missing 'tools' in frontmatter."
            }
            if ($content -notmatch 'Does NOT') {
                $warnings += "Agent file missing 'Does NOT' section (constraints)."
            }
        }
    }

    # Check 3: Command files must have frontmatter
    if ($fileType -eq "command") {
        $content = ""
        if ($data.tool_input.content) {
            $content = $data.tool_input.content
        }

        if ($content) {
            if ($content -notmatch '^---') {
                $warnings += "Command file missing YAML frontmatter (---name/description---)."
            }
            if ($content -notmatch 'name:') {
                $warnings += "Command file missing 'name' in frontmatter."
            }
            if ($content -notmatch 'description:') {
                $warnings += "Command file missing 'description' in frontmatter."
            }
        }
    }

    # Check 4: Skill/Agent referencing non-existent spec files
    if ($fileType -eq "skill" -or $fileType -eq "agent") {
        $content = ""
        if ($data.tool_input.content) {
            $content = $data.tool_input.content
        }

        if ($content) {
            $specRefs = [regex]::Matches($content, 'specs/[a-zA-Z0-9/_-]+\.md')
            foreach ($ref in $specRefs) {
                $specPath = $ref.Value
                if (-not (Test-Path $specPath)) {
                    $warnings += "References '$specPath' but file does not exist."
                }
            }
        }
    }

    if ($warnings.Count -gt 0) {
        $warningText = $warnings -join "`n- "
        $result = @{
            decision = "block"
            reason   = "Rule consistency warnings for $($fileType):`n- $warningText`nFix these issues or confirm they are intentional."
        }
        $result | ConvertTo-Json -Compress
        exit 0
    }
}
catch {}
