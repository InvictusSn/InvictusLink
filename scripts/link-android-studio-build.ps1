param(
    [string]$ProjectRoot = "$PSScriptRoot\..\.."
)

$ErrorActionPreference = "Stop"

$projectBuild = Join-Path $ProjectRoot "android\app\build"
$localBuild = Join-Path $env:LOCALAPPDATA "InvictusLinkBuild\android-app"

New-Item -ItemType Directory -Force -Path $localBuild | Out-Null

if (Test-Path $projectBuild) {
    $item = Get-Item $projectBuild -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        Write-Host "Build link already exists: $projectBuild"
        exit 0
    }
    throw "android\app\build exists as a real folder. Move or delete it, then run this script again."
}

cmd /c mklink /J "$projectBuild" "$localBuild" | Out-Null
Write-Host "Linked Android Studio build path:"
Write-Host "  $projectBuild"
Write-Host "    -> $localBuild"
Write-Host ""
Write-Host "In Android Studio: File > Sync Project with Gradle Files, then Run app again."
