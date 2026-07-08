$ErrorActionPreference = 'Stop'

$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $workspace '.env.local'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Local environment file not found: $envFile"
}

Get-Content -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith('#')) {
        return
    }

    $separatorIndex = $line.IndexOf('=')
    if ($separatorIndex -lt 1) {
        return
    }

    $name = $line.Substring(0, $separatorIndex).Trim()
    $value = $line.Substring($separatorIndex + 1).Trim()
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

Write-Output 'PetSpark local environment loaded for this PowerShell process.'
