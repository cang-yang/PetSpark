$ErrorActionPreference = 'Stop'

$javaVersion = & cmd.exe /d /c "java -version 2>&1" | Out-String
if ($javaVersion -notmatch 'version "17\.') {
    throw "PetSpark requires the locally installed JDK 17. Current java -version is: $javaVersion"
}

$nodeVersion = (node --version).Trim()
if ($nodeVersion -notmatch '^v(2[2-9]|[3-9][0-9])\.') {
    throw "PetSpark requires a locally installed Node.js 22 or newer. Current node --version is: $nodeVersion"
}

$mysqlVersion = & mysql --version
if ($mysqlVersion -notmatch 'Ver 8\.') {
    throw "PetSpark requires the locally installed MySQL 8 client on PATH. Current mysql --version is: $mysqlVersion"
}

Write-Output "PetSpark environment verified: local Java 17, local Node $nodeVersion, local $mysqlVersion"
