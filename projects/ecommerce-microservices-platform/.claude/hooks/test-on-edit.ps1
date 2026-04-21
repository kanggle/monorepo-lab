$reader    = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)
$inputJson = $reader.ReadToEnd()

try {
    $data = $inputJson | ConvertFrom-Json
    $filePath = ""

    if ($data.tool_input -and $data.tool_input.file_path) {
        $filePath = $data.tool_input.file_path
    }

    # Track edited source files (not test files) for test reminder
    if ($filePath -match 'apps[\\/][\w-]+[\\/]src[\\/]main[\\/]' -and $filePath -match '\.(java|kt)$') {
        # Extract service name from path
        $serviceName = ""
        if ($filePath -match 'apps[\\/]([\w-]+)[\\/]') {
            $serviceName = $matches[1]
        }

        if ($serviceName) {
            $msg = "Source file edited in $serviceName - remember to run tests before completing the task."
            Write-Host "[test-on-edit] $msg" -ForegroundColor Cyan
        }
    }
}
catch {}
