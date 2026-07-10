param(
    [string]$WorkspacePath = ''
)

$ErrorActionPreference = 'Stop'

$workspace = if ($WorkspacePath) {
    (Resolve-Path -LiteralPath $WorkspacePath).Path
} else {
    (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
}

$baseEnvFile = Join-Path $workspace '.env.local'
$sparkEnvFile = Join-Path $workspace '.env.spark.local'

if (-not (Test-Path -LiteralPath $baseEnvFile)) {
    throw "Local environment file not found: $baseEnvFile"
}

function Import-PetSparkEnvFile {
    param([Parameter(Mandatory = $true)][string]$Path)

    $names = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    Get-Content -LiteralPath $Path | ForEach-Object {
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
        $isDoubleQuoted = $value.StartsWith('"') -and $value.EndsWith('"')
        $isSingleQuoted = $value.StartsWith("'") -and $value.EndsWith("'")
        if ($value.Length -ge 2 -and ($isDoubleQuoted -or $isSingleQuoted)) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        [void]$names.Add($name)
    }
    return ,$names
}

$baseNames = Import-PetSparkEnvFile -Path $baseEnvFile
$loadedFiles = [System.Collections.Generic.List[string]]::new()
[void]$loadedFiles.Add('.env.local')

$sparkNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
if (Test-Path -LiteralPath $sparkEnvFile) {
    $sparkNames = Import-PetSparkEnvFile -Path $sparkEnvFile
    [void]$loadedFiles.Add('.env.spark.local')
}

# A local Spark secret file is an explicit local opt-in. When it does not contain
# its own switch, a non-empty APIPassword enables the gateway for this process.
if ((Test-Path -LiteralPath $sparkEnvFile) -and -not $sparkNames.Contains('SPARK_ENABLED')) {
    $hasSparkPassword = $sparkNames.Contains('SPARK_API_PASSWORD') -and
        -not [string]::IsNullOrWhiteSpace($env:SPARK_API_PASSWORD)
    [Environment]::SetEnvironmentVariable('SPARK_ENABLED', $hasSparkPassword.ToString().ToLowerInvariant(), 'Process')
} elseif (-not (Test-Path -LiteralPath $sparkEnvFile) -and -not $baseNames.Contains('SPARK_ENABLED')) {
    [Environment]::SetEnvironmentVariable('SPARK_ENABLED', 'false', 'Process')
}

$sparkState = if ($env:SPARK_ENABLED -ieq 'true') { 'enabled' } else { 'disabled' }
Write-Output ("PetSpark local environment loaded from {0}; Spark AI {1}." -f ($loadedFiles -join ', '), $sparkState)
