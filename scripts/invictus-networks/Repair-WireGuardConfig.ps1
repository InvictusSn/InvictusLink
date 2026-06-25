function Repair-WireGuardConfig {
    param([string]$Raw)
    $s = ($Raw -replace "`r`n", " " -replace "`n", " ").Trim()
    function Get-Field([string]$Name) {
        if ($s -match "(?i)${Name}\s*=\s*(\S+)") { return $matches[1] }
        throw "Missing field: $Name"
    }
    $lines = @(
        "[Interface]"
        "PrivateKey = $(Get-Field 'PrivateKey')"
        "Address = $(Get-Field 'Address')"
        ""
        "[Peer]"
        "PublicKey = $(Get-Field 'PublicKey')"
        "Endpoint = $(Get-Field 'Endpoint')"
        "AllowedIPs = $(Get-Field 'AllowedIPs')"
        "PersistentKeepalive = $(Get-Field 'PersistentKeepalive')"
    )
    return ($lines -join "`n")
}

function Save-WireGuardConfig {
    param([string]$Path, [string]$Content)
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($Path, $Content.Trim() + "`n", $utf8NoBom)
}
