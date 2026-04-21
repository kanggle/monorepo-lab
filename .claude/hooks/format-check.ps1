$reader    = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)
$inputJson = $reader.ReadToEnd()

try {
    $data = $inputJson | ConvertFrom-Json
    $filePath = ""

    if ($data.tool_input -and $data.tool_input.file_path) {
        $filePath = $data.tool_input.file_path
    }

    # Check Java files for common format issues after edit
    if ($filePath -match '\.java$') {
        if (Test-Path $filePath) {
            $content = Get-Content $filePath -Raw -ErrorAction Stop

            $issues = @()

            # Check for wildcard imports
            if ($content -match 'import\s+[\w.]+\.\*;') {
                $issues += "Wildcard import detected (use explicit imports)"
            }

            # Check for System.out.println
            if ($content -match 'System\.(out|err)\.(print|println)') {
                $issues += "System.out/err usage detected (use SLF4J logger)"
            }

            # Check for empty catch blocks
            if ($content -match 'catch\s*\([^)]+\)\s*\{\s*\}') {
                $issues += "Empty catch block detected"
            }

            if ($issues.Count -gt 0) {
                $msg = "Format issues: " + ($issues -join "; ")
                Write-Host "[format-check] $msg" -ForegroundColor Yellow
            }
        }
    }
}
catch {}
