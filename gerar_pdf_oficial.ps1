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

$testSources = Get-ChildItem -Path "$dir\src\test\java" -Filter "*.java" -Recurse | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
[System.IO.File]::WriteAllLines("$buildDir\test_sources.txt", $testSources, $utf8SemBom)
$tSrcPath = (Get-Item "$buildDir\test_sources.txt").FullName

Write-Host "--- Compilando GeradorDocumentacaoPdfHtml ---" -ForegroundColor Cyan
& javac -encoding UTF-8 -cp "$buildDir\classes;$cp" -d "$buildDir\test-classes" "@$tSrcPath"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "--- Executando GeradorDocumentacaoPdfHtml ---" -ForegroundColor Cyan
& java "-Dfile.encoding=UTF-8" -cp "$buildDir\test-classes;$buildDir\classes;$cp" br.com.senac.application.GeradorDocumentacaoPdfHtml
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$tempImgs = "$env:TEMP\sazonalisei_imagens"
$htmlPath = "$tempImgs\documentacao_completa.html"
$pdfTemp = "$tempImgs\Documentacao_Completa_Sazonalisei_e_ChatBotTerminal.pdf"

$chrome = "C:\Program Files\Google\Chrome\Application\chrome.exe"
if (-not (Test-Path $chrome)) {
    $chrome = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
}

Write-Host "--- Convertendo HTML para PDF com Chrome/Edge Headless ---" -ForegroundColor Green
& "$chrome" --headless --disable-gpu --run-all-compositor-stages-before-draw --no-pdf-header-footer "--print-to-pdf=$pdfTemp" "$htmlPath"

if (Test-Path $pdfTemp) {
    $len = (Get-Item $pdfTemp).Length
    Write-Host "PDF gerado com sucesso em temp: $pdfTemp ($len bytes)" -ForegroundColor Green
} else {
    Write-Host "Falha ao gerar PDF!" -ForegroundColor Red
    exit 1
}
