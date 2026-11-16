$ErrorActionPreference = 'Stop'
$logsPath = Join-Path $PSScriptRoot 'logs'
New-Item -ItemType Directory -Path $logsPath -Force | Out-Null
$pythonPath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\python'))
$pythonExe = Join-Path $pythonPath '.venv\Scripts\python.exe'
$jarPath = Join-Path $PSScriptRoot 'backend\target\enterprise-portal-1.0.0.jar'
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Build the enterprise module first: powershell -File build.ps1' }
try {
    $agentState = Invoke-RestMethod 'http://127.0.0.1:8000/health' -TimeoutSec 3
    if ($agentState.status -ne 'healthy') { throw 'Unhealthy agent service' }
    Write-Host 'Reusing the existing Agent service on port 8000.'
} catch {
    if (-not (Test-Path -LiteralPath $pythonExe)) { throw 'The original Python virtual environment is missing.' }
    Start-Process -FilePath $pythonExe -ArgumentList '-m','uvicorn','main:app','--host','127.0.0.1','--port','8000' -WorkingDirectory $pythonPath -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logsPath 'agents.stdout.log') -RedirectStandardError (Join-Path $logsPath 'agents.stderr.log') | Out-Null
    Write-Host 'Agent service is starting on port 8000.'
}
if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
    Write-Host 'Port 8080 already has a listener. Check http://127.0.0.1:8080 before starting another instance.'
} else {
    $javaCommand = Get-Command java.exe -ErrorAction Stop
    Start-Process -FilePath $javaCommand.Source -ArgumentList '-jar','target/enterprise-portal-1.0.0.jar' -WorkingDirectory (Join-Path $PSScriptRoot 'backend') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logsPath 'portal.stdout.log') -RedirectStandardError (Join-Path $logsPath 'portal.stderr.log') | Out-Null
    Write-Host 'Portal is starting at http://127.0.0.1:8080'
}
Write-Host 'Initial administrator credentials: enterprise/backend/data/initial-admin.txt'
