param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [string]$ConfigPath = ".release/publish-config.json",
    [string]$OutputDir = "artifacts",
    [string]$ArtifactPathOutputFile = "",
    [bool]$SkipTests = $false
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $ConfigPath)) {
    throw "Release config '$ConfigPath' was not found."
}

$config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
$normalizedVersion = (($Version.Trim()) -replace "^v", "")
if ([string]::IsNullOrWhiteSpace($normalizedVersion)) {
    throw "Version cannot be empty."
}

$sourceVersion = (Get-Content -LiteralPath "gradle.properties" |
    Where-Object { $_ -match "^mod_version=" } |
    Select-Object -First 1) -replace "^mod_version=", ""
if ($sourceVersion.Trim() -ne $normalizedVersion) {
    throw "Version mismatch: source '$sourceVersion' does not match requested '$normalizedVersion'."
}

$gradleArguments = @("--no-daemon", "clean", "build")
if ($SkipTests) {
    $gradleArguments = @("--no-daemon", "clean", "shadowJar", "-x", "test")
}

& .\gradlew.bat @gradleArguments
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed with exit code $LASTEXITCODE."
}

$builtJars = @(Get-ChildItem -LiteralPath "build\libs" -Filter "*.jar")
if ($builtJars.Count -ne 1) {
    throw "Expected one built jar in build/libs, but found $($builtJars.Count)."
}

New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
$artifactName = $config.artifactNameTemplate.Replace("{version}", $normalizedVersion)
$artifactPath = Join-Path $OutputDir $artifactName
Copy-Item -LiteralPath $builtJars[0].FullName -Destination $artifactPath -Force

$resolvedArtifactPath = (Resolve-Path -LiteralPath $artifactPath).Path
if (-not [string]::IsNullOrWhiteSpace($ArtifactPathOutputFile)) {
    Set-Content -LiteralPath $ArtifactPathOutputFile -Value $resolvedArtifactPath
}

Write-Host "Built artifact: $resolvedArtifactPath"
Write-Output $resolvedArtifactPath
