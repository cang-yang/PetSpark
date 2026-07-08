$ErrorActionPreference = 'Stop'

$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$launcher = Join-Path $workspace 'scripts\petspark-env.ps1'

$requiredFiles = @(
    $launcher
)

foreach ($requiredFile in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $requiredFile)) {
        throw "Missing required project toolchain file: $requiredFile"
    }
}

$originalJavaHome = $env:JAVA_HOME
$originalPath = $env:PATH
try {
    . $launcher
    $selectedJavaVersion = & cmd.exe /d /c "java -version 2>&1" | Out-String
    if ($selectedJavaVersion -notmatch 'version "17\.') {
        throw "Launcher did not select JDK 17: $selectedJavaVersion"
    }
    $nodeVersion = (node --version).Trim()
    if ($nodeVersion -notmatch '^v(2[2-9]|[3-9][0-9])\.') {
        throw "Expected local Node.js 22 or newer, got: $nodeVersion"
    }
    $mysqlVersion = & mysql --version
    if ($mysqlVersion -notmatch 'Ver 8\.') {
        throw "Expected local MySQL 8 client, got: $mysqlVersion"
    }
    if ($env:PATH -ne $originalPath) {
        throw "Launcher should validate local tools without rewriting PATH."
    }
}
finally {
    $env:JAVA_HOME = $originalJavaHome
    $env:PATH = $originalPath
}

Write-Output 'TOOLCHAIN_TEST=PASS'
