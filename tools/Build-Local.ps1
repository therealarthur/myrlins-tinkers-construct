param(
    [ValidateSet('compileJava', 'jar', 'craftingRegression', 'returningFixtureJar')]
    [string[]]$Tasks = @('jar', 'craftingRegression', 'returningFixtureJar'),
    [ValidatePattern('^[A-Za-z0-9_.-]+$')]
    [string]$Version = '3.12.2-arthur.4'
)
$ErrorActionPreference = 'Stop'
$checkout = Split-Path -Parent $PSScriptRoot
$taskRoot = [IO.Path]::GetFullPath((Join-Path $checkout '../..'))
$tempPath = Join-Path $taskRoot 'tmp/continuum'
New-Item -ItemType Directory -Path $tempPath -Force | Out-Null
$tempDirectory = Get-Item -LiteralPath (Resolve-Path -LiteralPath $tempPath).Path
if ($tempDirectory.FullName -notlike 'D:\*' -or ($tempDirectory.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw 'Continuum TEMP must be an existing non-reparse directory on D:.'
}
$env:TEMP = $tempDirectory.FullName
$env:TMP = $tempDirectory.FullName
$env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$($tempDirectory.FullName)"
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-25'
$env:GRADLE_USER_HOME = Join-Path $taskRoot 'cache/gradle-target'
$assetRoot = Join-Path $env:GRADLE_USER_HOME 'caches/minecraft-assets'
if (-not (Test-Path -LiteralPath (Join-Path $assetRoot 'indexes/30.json'))) {
    throw 'The pinned shared Minecraft 26.1.2 asset index 30 is missing. Ask the coordinator to prepare the shared assets.'
}
$moddevDirectory = Join-Path $checkout 'build/moddev'
New-Item -ItemType Directory -Path $moddevDirectory -Force | Out-Null
# Headless FML tests need the asset reference. Reuse the existing index/objects;
# ModDev's default download task otherwise creates a second asset root.
$assetProperties = "asset_index=30`nassets_root=$($assetRoot.Replace('\', '/'))`n"
[IO.File]::WriteAllText((Join-Path $moddevDirectory 'minecraft_assets.properties'), $assetProperties, [Text.UTF8Encoding]::new($false))
$logDirectory = Join-Path $checkout 'build/local-evidence'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$logPath = Join-Path $logDirectory ('build-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
Push-Location $checkout
try {
    & ./gradlew.bat --no-daemon --no-configuration-cache --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx1G' '-Dorg.gradle.java.installations.auto-download=false' "-PARTIFACT_VERSION=$Version" -x downloadAssets @Tasks 2>&1 | Tee-Object -FilePath $logPath
    $buildExit = $LASTEXITCODE
    Write-Output "Build log: $logPath"
    exit $buildExit
} finally {
    Pop-Location
}
