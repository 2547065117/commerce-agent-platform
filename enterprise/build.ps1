$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    $mavenPath = if ($mavenCommand) { $mavenCommand.Source } else { 'C:\Program Files\JetBrains\IntelliJ IDEA 2024.1.6\plugins\maven\lib\maven3\bin\mvn.cmd' }
    if (-not (Test-Path -LiteralPath $mavenPath)) { throw 'Install Maven 3.6.3+ and add mvn.cmd to PATH.' }
    Push-Location frontend
    try {
        & npm.cmd ci --no-audit --no-fund --cache=../.tools/npm-cache
        if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    } finally { Pop-Location }
    Push-Location backend
    try {
        & $mavenPath "-Dmaven.repo.local=$PSScriptRoot/.tools/m2" --batch-mode --no-transfer-progress package
        if ($LASTEXITCODE -ne 0) { throw 'Backend build or tests failed.' }
    } finally { Pop-Location }
} finally { Pop-Location }
