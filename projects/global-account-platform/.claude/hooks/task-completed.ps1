$reader    = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)
$inputJson = $reader.ReadToEnd()

# "작업이 완료되었습니다."
$message = [string][char[]]@(0xC791,0xC5C5,0xC774,0x20,0xC644,0xB8CC,0xB418,0xC5C8,0xC2B5,0xB2C8,0xB2E4,0x2E)

try {
    $data = $inputJson | ConvertFrom-Json

    if ($data.task) {
        $task = $data.task
        if ($task.Length -gt 60) {
            $task = $task.Substring(0, 60) + "..."
        }
        # "작업 완료: "
        $prefix  = [string][char[]]@(0xC791,0xC5C5,0x20,0xC644,0xB8CC,0x3A,0x20)
        $message = $prefix + $task
    }
}
catch {}

& "$PSScriptRoot\notify.ps1" -Title "Claude Task Done" -Message $message
