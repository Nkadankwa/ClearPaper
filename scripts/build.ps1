$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $taskRoot 'Frontend')
try {
    npm.cmd ci --no-fund --no-audit
    if($LASTEXITCODE -ne 0){throw 'Frontend clean install failed.'}
    npm.cmd run check
    if($LASTEXITCODE -ne 0){throw 'Frontend build failed.'}
} finally {Pop-Location}
Push-Location (Join-Path $taskRoot 'Backend')
try {
    mvn '-Pfrontend' clean verify
    if($LASTEXITCODE -ne 0){throw 'Backend verification failed.'}
} finally {Pop-Location}

