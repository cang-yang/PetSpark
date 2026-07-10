$ErrorActionPreference = 'Stop'

$loader = Join-Path $PSScriptRoot 'load-local-env.ps1'
$root = Join-Path ([System.IO.Path]::GetTempPath()) ("petspark-env-test-{0}" -f [Guid]::NewGuid())

function New-TestWorkspace {
    param(
        [string]$Name,
        [string[]]$SparkLines = @()
    )

    $path = Join-Path $root $Name
    [void](New-Item -ItemType Directory -Path $path -Force)
    Set-Content -LiteralPath (Join-Path $path '.env.local') -Encoding utf8 -Value @(
        'DB_URL=jdbc:mysql://localhost:3306/petspark_test',
        'DB_USERNAME=tester',
        'DB_PASSWORD=fake-test-password'
    )
    if ($SparkLines.Count -gt 0) {
        Set-Content -LiteralPath (Join-Path $path '.env.spark.local') -Encoding utf8 -Value $SparkLines
    }
    return $path
}

try {
    [Environment]::SetEnvironmentVariable('SPARK_API_PASSWORD', '', 'Process')
    [Environment]::SetEnvironmentVariable('SPARK_ENABLED', '', 'Process')
    $withoutSpark = New-TestWorkspace -Name 'without-spark'
    $message = . $loader -WorkspacePath $withoutSpark
    if ($env:SPARK_ENABLED -ne 'false') { throw 'Missing Spark file must keep AI disabled.' }

    [Environment]::SetEnvironmentVariable('SPARK_API_PASSWORD', '', 'Process')
    [Environment]::SetEnvironmentVariable('SPARK_ENABLED', '', 'Process')
    $withSpark = New-TestWorkspace -Name 'with-spark' -SparkLines @(
        'SPARK_API_PASSWORD=fake-spark-test-password',
        'SPARK_BASE_URL=https://example.invalid/v2',
        'SPARK_MODEL=spark-x'
    )
    $message = . $loader -WorkspacePath $withSpark
    if ($env:SPARK_ENABLED -ne 'true') { throw 'A non-empty local Spark password must enable AI.' }
    if (($message -join ' ') -match 'fake-spark-test-password') { throw 'Loader output exposed the Spark password.' }

    [Environment]::SetEnvironmentVariable('SPARK_API_PASSWORD', 'inherited-password-must-not-enable', 'Process')
    [Environment]::SetEnvironmentVariable('SPARK_ENABLED', '', 'Process')
    $withoutSparkPassword = New-TestWorkspace -Name 'spark-without-password' -SparkLines @(
        'SPARK_BASE_URL=https://example.invalid/v2',
        'SPARK_MODEL=spark-x'
    )
    $message = . $loader -WorkspacePath $withoutSparkPassword
    if ($env:SPARK_ENABLED -ne 'false') { throw 'A Spark file without its own password must stay disabled.' }

    [Environment]::SetEnvironmentVariable('SPARK_API_PASSWORD', '', 'Process')
    [Environment]::SetEnvironmentVariable('SPARK_ENABLED', '', 'Process')
    $explicitOff = New-TestWorkspace -Name 'explicit-off' -SparkLines @(
        'SPARK_ENABLED=false',
        'SPARK_API_PASSWORD=fake-spark-test-password'
    )
    $message = . $loader -WorkspacePath $explicitOff
    if ($env:SPARK_ENABLED -ne 'false') { throw 'An explicit local off switch must be honored.' }

    Write-Output 'Local environment loader tests passed.'
} finally {
    if (Test-Path -LiteralPath $root) {
        Remove-Item -LiteralPath $root -Recurse -Force
    }
}
