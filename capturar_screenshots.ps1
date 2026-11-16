$dir = $PSScriptRoot
Set-Location $dir

$utf8SemBom = New-Object System.Text.UTF8Encoding $false

$buildDir = "$dir\target"
$usandoFallback = $false
try {
    if (!(Test-Path $buildDir)) { New-Item -ItemType Directory -Force -Path $buildDir | Out-Null }
    $probe = "$buildDir\.write_probe"
    [System.IO.File]::WriteAllText($probe, "ok", $utf8SemBom)
    Remove-Item $probe -Force -ErrorAction SilentlyContinue
} catch {
    $usandoFallback = $true
    $buildDir = Join-Path $env:TEMP "sazonalisei_build"
    New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
}

$cpRaw = (Get-Content "$dir\target\cp.txt" -Raw).Trim() -replace 'C:\\Users\\[^\\]+\\.m2\\repository', "$env:USERPROFILE\.m2\repository"
if ($cpRaw.StartsWith('-cp ')) {
    $cpRaw = $cpRaw.Substring(4).Trim().Trim('"')
}
$cp = ($cpRaw -split ';' | Where-Object { $_ -and $_ -ne 'target/classes' -and $_ -ne 'target\classes' }) -join ';'

$jfxDir = Join-Path $env:USERPROFILE ".m2\repository\org\openjfx"
if (Test-Path $jfxDir) {
    $jfxJars = Get-ChildItem -Path $jfxDir -Recurse -Filter "*-win.jar" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
    foreach ($jar in $jfxJars) {
        if (-not $cp.Contains($jar)) {
            $cp = "$cp;$jar"
        }
    }
}

if (!(Test-Path "$buildDir\classes")) { New-Item -ItemType Directory -Force -Path "$buildDir\classes" | Out-Null }
if (!(Test-Path "$buildDir\test-classes")) { New-Item -ItemType Directory -Force -Path "$buildDir\test-classes" | Out-Null }
if (Test-Path "$dir\src\main\resources") {
    Copy-Item -Path "$dir\src\main\resources\*" -Destination "$buildDir\classes" -Recurse -Force
}

$sources = Get-ChildItem -Path "$dir\src\main\java" -Filter "*.java" -Recurse | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
[System.IO.File]::WriteAllLines("$buildDir\sources.txt", $sources, $utf8SemBom)
$srcPath = (Get-Item "$buildDir\sources.txt").FullName

Write-Host "--- Compilando Projeto Java (src/main/java) ---" -ForegroundColor Cyan
& javac -encoding UTF-8 -cp "$cp;$buildDir\classes" -d "$buildDir\classes" "@$srcPath"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$testSources = Get-ChildItem -Path "$dir\src\test\java" -Filter "*.java" -Recurse | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
[System.IO.File]::WriteAllLines("$buildDir\test_sources.txt", $testSources, $utf8SemBom)
$tSrcPath = (Get-Item "$buildDir\test_sources.txt").FullName

Write-Host "--- Compilando Testes Java (src/test/java) ---" -ForegroundColor Cyan
& javac -encoding UTF-8 -cp "$buildDir\classes;$cp" -d "$buildDir\test-classes" "@$tSrcPath"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (!(Test-Path "$buildDir\jfx-cache")) { New-Item -ItemType Directory -Force -Path "$buildDir\jfx-cache" | Out-Null }

Write-Host "--- Executando CapturadorScreenshotsJavaFX ---" -ForegroundColor Green
& java "-Dfile.encoding=UTF-8" "-Djavafx.cachedir=$buildDir\jfx-cache" "-Dprism.order=sw" -cp "$buildDir\test-classes;$buildDir\classes;$cp" br.com.senac.application.CapturadorScreenshotsJavaFX
exit $LASTEXITCODE
