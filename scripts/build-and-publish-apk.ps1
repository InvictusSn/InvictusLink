param(
    [string]$ProjectRoot = "$PSScriptRoot\..\..",
    [string]$BaseUrl = "http://<your-pc-vpn-ip>:3003",
    [switch]$AutoBump
)

$ErrorActionPreference = "Stop"

$androidDir = Join-Path $ProjectRoot "android"
$appBuildDir = Join-Path $androidDir "app\build"
$localAppBuildDir = Join-Path (
    [Environment]::GetFolderPath("LocalApplicationData")
) "InvictusLinkBuild\android-app"
$bridgeDownloadDir = Join-Path $ProjectRoot "bridge\public\download"
$apkSource = Join-Path $androidDir "app\build\outputs\apk\debug\app-debug.apk"
$apkSourceLocal = Join-Path $localAppBuildDir "outputs\apk\debug\app-debug.apk"
$apkTarget = Join-Path $bridgeDownloadDir "InvictusLink.apk"
$latestJsonPath = Join-Path $bridgeDownloadDir "latest.json"
$gradlew = Join-Path $androidDir "gradlew.bat"
$buildGradle = Join-Path $androidDir "app\build.gradle.kts"

if (-not (Test-Path $gradlew)) {
    throw "Could not find gradle wrapper at: $gradlew"
}

# Ensure Java is available for gradlew even if PATH is missing it.
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $studioJbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path (Join-Path $studioJbr "bin\java.exe")) {
        $env:JAVA_HOME = $studioJbr
    } else {
        throw "JAVA_HOME is not valid and Android Studio JBR was not found. Please install Java 17+."
    }
}
$env:PATH = "$($env:JAVA_HOME)\bin;$($env:PATH)"

# Optionally auto-bump versionCode/versionName so in-app updates are detected.
$originalGradleText = $null
if ($AutoBump) {
    $gradleText = Get-Content -Raw -Path $buildGradle
    $originalGradleText = $gradleText
    $versionCodeMatch = [regex]::Match($gradleText, "versionCode\s*=\s*(\d+)")
    $versionNameMatch = [regex]::Match($gradleText, "versionName\s*=\s*""([^""]+)""")
    if (-not $versionCodeMatch.Success -or -not $versionNameMatch.Success) {
        throw "Failed to parse versionCode/versionName from $buildGradle"
    }

    $currentCode = [int]$versionCodeMatch.Groups[1].Value
    $newCode = $currentCode + 1
    $currentName = $versionNameMatch.Groups[1].Value
    $newName = $currentName
    if ($currentName -match "^\d+\.\d+$") {
        $parts = $currentName.Split(".")
        $newName = "$($parts[0]).$([int]$parts[1] + 1)"
    } elseif ($currentName -match "^\d+\.\d+\.\d+$") {
        $parts = $currentName.Split(".")
        $newName = "$($parts[0]).$($parts[1]).$([int]$parts[2] + 1)"
    } else {
        $newName = "$currentName.$newCode"
    }

    $gradleText = [regex]::Replace($gradleText, "versionCode\s*=\s*\d+", "versionCode = $newCode", 1)
    $gradleText = [regex]::Replace($gradleText, "versionName\s*=\s*""[^""]+""", "versionName = ""$newName""", 1)
    Set-Content -Path $buildGradle -Value $gradleText -Encoding UTF8
    Write-Host "Auto-bumped app version to versionCode=$newCode, versionName=$newName"
}

try {
    # Remove stale intermediates that sometimes break dex snapshotting on synced folders.
    if (Test-Path $appBuildDir) {
        Remove-Item -Recurse -Force $appBuildDir -ErrorAction SilentlyContinue
    }
    if (Test-Path $localAppBuildDir) {
        Remove-Item -Recurse -Force $localAppBuildDir -ErrorAction SilentlyContinue
    }

    Push-Location $androidDir
    try {
        Write-Host "Building debug APK..."
        & $gradlew clean assembleDebug
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
} catch {
    if ($AutoBump -and $null -ne $originalGradleText) {
        Set-Content -Path $buildGradle -Value $originalGradleText -Encoding UTF8
        Write-Host "Build failed; reverted auto-bumped version in build.gradle.kts"
    }
    throw
}

if (-not (Test-Path $apkSource)) {
    if (Test-Path $apkSourceLocal) {
        $apkSource = $apkSourceLocal
    } else {
        throw "APK not found after build: `n$apkSource`n$apkSourceLocal"
    }
}

New-Item -ItemType Directory -Force -Path $bridgeDownloadDir | Out-Null
Copy-Item -Force $apkSource $apkTarget

$gradleText = Get-Content -Raw -Path $buildGradle
$versionCodeMatch = [regex]::Match($gradleText, "versionCode\s*=\s*(\d+)")
$versionNameMatch = [regex]::Match($gradleText, "versionName\s*=\s*""([^""]+)""")

if (-not $versionCodeMatch.Success -or -not $versionNameMatch.Success) {
    throw "Failed to parse versionCode/versionName from $buildGradle"
}

$versionCode = [int]$versionCodeMatch.Groups[1].Value
$versionName = $versionNameMatch.Groups[1].Value
$apkUrl = "$($BaseUrl.TrimEnd('/'))/download/InvictusLink.apk"

$latest = @{
    versionCode = $versionCode
    versionName = $versionName
    apkUrl = $apkUrl
}

$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($latestJsonPath, ($latest | ConvertTo-Json -Compress), $utf8NoBom)

$apkInfo = Get-Item $apkTarget
Write-Host ""
Write-Host "Done."
Write-Host "APK: $($apkInfo.FullName)"
Write-Host "Size: $($apkInfo.Length) bytes"
Write-Host "Manifest: $latestJsonPath"
Write-Host "Update feed: $($BaseUrl.TrimEnd('/'))/download/latest.json"
Write-Host "Phone flow: Open app -> Check for Update -> Install Update"

