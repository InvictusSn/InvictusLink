# Allow inbound TCP 3003 for Invictus Link bridge (WireGuard subnet).
# Run as Administrator:
#   powershell -ExecutionPolicy Bypass -File .\scripts\invictus-networks\allow-bridge-firewall.ps1

$ErrorActionPreference = "Stop"

$ruleName = "Invictus Link Bridge 3003"
$existing = netsh advfirewall firewall show rule name="$ruleName" 2>&1
if ($LASTEXITCODE -ne 0) {
    netsh advfirewall firewall add rule `
        name="$ruleName" `
        dir=in `
        action=allow `
        protocol=TCP `
        localport=3003 `
        remoteip=10.66.66.0/24 `
        profile=any
    Write-Host "Added firewall rule: $ruleName (TCP 3003 from 10.66.66.0/24)"
} else {
    Write-Host "Firewall rule already exists: $ruleName"
}

$wg = Get-NetConnectionProfile -InterfaceAlias -like "WireGuard*" -ErrorAction SilentlyContinue
if ($wg -and $wg.NetworkCategory -ne "Private") {
    Set-NetConnectionProfile -InterfaceAlias -like "WireGuard*" -NetworkCategory Private
    Write-Host "Set WireGuard tunnel network profile to Private (was Public)."
} elseif ($wg) {
    Write-Host "WireGuard tunnel is already Private."
}

Write-Host "Done. Phone should reach http://<your-pc-vpn-ip>:3003/health with WireGuard on."
