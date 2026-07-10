param(
    [ValidateRange(1, 65535)]
    [int]$Port = 8080
)

$ErrorActionPreference = 'Stop'

$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'load-local-env.ps1')

$mavenWrapper = Join-Path $workspace 'mvnw.cmd'
$arguments = @('-pl', 'petspark-server', 'spring-boot:run')
if ($Port -ne 8080) {
    $arguments += "-Dspring-boot.run.arguments=--server.port=$Port"
}

& $mavenWrapper @arguments
exit $LASTEXITCODE
