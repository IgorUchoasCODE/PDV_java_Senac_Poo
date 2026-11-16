$ErrorActionPreference = "Stop"

$utf8SemBom = New-Object System.Text.UTF8Encoding $false
[Console]::InputEncoding = $utf8SemBom
[Console]::OutputEncoding = $utf8SemBom
$OutputEncoding = $utf8SemBom

$dir = $PSScriptRoot
Set-Location $dir

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
$cpStr = ($cpRaw -split ';' | Where-Object { $_ -and $_ -ne 'target/classes' -and $_ -ne 'target\classes' }) -join ';'

$jfxDir = Join-Path $env:USERPROFILE ".m2\repository\org\openjfx"
if (Test-Path $jfxDir) {
    $jfxJars = Get-ChildItem -Path $jfxDir -Recurse -Filter "*-win.jar" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
    foreach ($jar in $jfxJars) {
        if (-not $cpStr.Contains($jar)) {
            $cpStr = "$cpStr;$jar"
        }
    }
}

if (!(Test-Path "$buildDir\classes")) {
    New-Item -ItemType Directory -Force -Path "$buildDir\classes" | Out-Null
}
if (Test-Path "$dir\src\main\resources") {
    Copy-Item -Path "$dir\src\main\resources\*" -Destination "$buildDir\classes" -Recurse -Force
}

if ($usandoFallback) {
    $dataDir = ($buildDir -replace '\\', '/') + "/data/sazonalisei"
    New-Item -ItemType Directory -Force -Path "$buildDir\data" | Out-Null
    foreach ($pXml in @("$buildDir\classes\META-INF\persistence.xml", "$buildDir\classes\META-INF\persistence-embarcado.xml")) {
        if (Test-Path $pXml) {
            $xmlTxt = Get-Content $pXml -Raw -Encoding UTF8
            $xmlTxt = $xmlTxt -replace 'jdbc:h2:\./data/sazonalisei', "jdbc:h2:$dataDir"
            $xmlTxt = $xmlTxt -replace ';AUTO_SERVER=TRUE', ''
            [System.IO.File]::WriteAllText($pXml, $xmlTxt, $utf8SemBom)
        }
    }
}

$sources = Get-ChildItem -Path "$dir\src\main\java" -Filter "*.java" -Recurse | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
[System.IO.File]::WriteAllLines("$buildDir\sources_gui.txt", $sources, $utf8SemBom)
$srcPath = (Get-Item "$buildDir\sources_gui.txt").FullName

Write-Host "--- Compilando Sazonalisei (JavaFX + Motor IA) ---" -ForegroundColor Cyan
& javac -encoding UTF-8 -cp "$cpStr;$buildDir\classes" -d "$buildDir\classes" "@$srcPath"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Erro na compilação!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "--- Iniciando Interface Gráfica Sazonalisei ---" -ForegroundColor Green
& java "-Dfile.encoding=UTF-8" -cp "$buildDir\classes;$cpStr" br.com.senac.view.javafx.SazonaliseiGuiLauncher
