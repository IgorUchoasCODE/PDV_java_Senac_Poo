$ErrorActionPreference = "Stop"
Write-Host "Compilando e iniciando interface JavaFX Sazonalisei..." -ForegroundColor Cyan

if (-not (Test-Path "target\cp.txt")) {
    mvn dependency:build-classpath "-Dmdep.outputFile=target/cp.txt"
}

$cp = (Get-Content target\cp.txt -Raw).Trim() -replace '^-cp\s+', '' -replace '"', ''
$cp = $cp -replace 'C:\\Users\\[^\\]+\\.m2\\repository', "$env:USERPROFILE\.m2\repository"

New-Item -ItemType Directory -Force -Path "target\classes" | Out-Null
Copy-Item -Path "src\main\resources\*" -Destination "target\classes" -Recurse -Force

$files = @(Get-ChildItem -Recurse -Filter *.java src\main\java | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' })
[System.IO.File]::WriteAllLines("target\sources_fx.txt", $files, (New-Object System.Text.UTF8Encoding $false))
javac -encoding UTF-8 -d target\classes -cp "$cp" "@target\sources_fx.txt"

java '-Dfile.encoding=UTF-8' -cp "$cp;target\classes" br.com.senac.application.SazonaliseiJavaFXLauncher
