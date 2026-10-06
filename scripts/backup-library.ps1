param(
    [string]$RestorePath
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$h2Repository = Join-Path $env:USERPROFILE ".m2\repository\com\h2database\h2"
$h2Jar = Get-ChildItem -Path $h2Repository -Filter "h2-*.jar" -Recurse -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $h2Jar) {
    throw "H2 tools are not available in the local Maven cache. Run 'mvn package' first."
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java is not on PATH. Install JDK 17 and configure JAVA_HOME."
}

$databaseUrl = "jdbc:h2:file:./data/librarydb;AUTO_SERVER=TRUE"
if ($RestorePath) {
    $restoreFile = Resolve-Path -LiteralPath $RestorePath
    & java -cp $h2Jar.FullName org.h2.tools.RunScript -url $databaseUrl -user sa -script $restoreFile.Path
    if ($LASTEXITCODE -ne 0) {
        throw "H2 restore failed with exit code $LASTEXITCODE."
    }
    Write-Host "Restored H2 data from $($restoreFile.Path)"
    exit 0
}

$backupDirectory = Join-Path $projectRoot "backups"
New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
$backupFile = Join-Path $backupDirectory ("library-{0}.sql" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
& java -cp $h2Jar.FullName org.h2.tools.Script -url $databaseUrl -user sa -script $backupFile
if ($LASTEXITCODE -ne 0) {
    throw "H2 backup failed with exit code $LASTEXITCODE."
}
Write-Host "Created H2 backup: $backupFile"
