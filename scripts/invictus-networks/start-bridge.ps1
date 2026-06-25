# Start the Invictus Link bridge on your PC (listens on port 3003).
# Run: powershell -ExecutionPolicy Bypass -File .\scripts\invictus-networks\start-bridge.ps1

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (-not $projectRoot) { $projectRoot = "$PSScriptRoot\..\.." }
$bridgeDir = Join-Path $projectRoot "bridge"

Push-Location $bridgeDir
try {
    Write-Host "Building bridge..."
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "npm run build failed" }

    $existing = Get-NetTCPConnection -LocalPort 3003 -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($existing) {
        Stop-Process -Id $existing.OwningProcess -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 1
        Write-Host "Restarted existing bridge on port 3003."
    }

    Write-Host "Starting bridge on http://<your-pc-vpn-ip>:3003 ..."
    Write-Host "Keep this window open while using Invictus Link on your phone."
    Write-Host ""
    npm run start
} finally {
    Pop-Location
}
