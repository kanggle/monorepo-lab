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
        $result = @{
            decision = "ask"
            reason   = "Contract files (specs/contracts/) must be updated through the design-api or design-event workflow. Verify this change follows the contract-first rule."
        }
        $result | ConvertTo-Json -Compress
        exit 0
    }

    # Warn when editing platform specs
    if ($filePath -match 'specs[\\/]platform[\\/]') {
        $result = @{
            decision = "ask"
            reason   = "Platform specs (specs/platform/) are the highest priority source of truth. Confirm this change is intentional and not a task-driven modification."
        }
        $result | ConvertTo-Json -Compress
        exit 0
    }
}
catch {}
