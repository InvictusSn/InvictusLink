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

$wgProfile = Get-NetConnectionProfile -InterfaceAlias -like "WireGuard*" -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($wgProfile -and $wgProfile.NetworkCategory -ne "Private") {
    Set-NetConnectionProfile -InterfaceIndex $wgProfile.InterfaceIndex -NetworkCategory Private
    Write-Host "Set WireGuard tunnel network profile to Private (was Public)."
} elseif ($wgProfile) {
    Write-Host "WireGuard tunnel is already Private."
}

Write-Host "Done. With WireGuard on, phone should reach http://<your-pc-vpn-ip>:3003/health"
