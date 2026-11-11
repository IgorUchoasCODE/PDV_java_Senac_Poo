$dir = $PSScriptRoot
Set-Location $dir

$cp = (Get-Content "$dir\target\cp.txt" -Raw).Trim() -replace 'C:\\Users\\[^\\]+\\.m2\\repository', "$env:USERPROFILE\.m2\repository"
if ($cp.StartsWith('-cp ')) {
    $cp = $cp.Substring(4).Trim().Trim('"')
}
$sources = Get-ChildItem -Path "$dir\src\main\java" -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
[System.IO.File]::WriteAllLines("$dir\target\sources.txt", $sources, [System.Text.Encoding]::ASCII)
$srcPath = (Get-Item "$dir\target\sources.txt").FullName

Write-Host "--- Compilando Projeto Java ---" -ForegroundColor Cyan
& javac -encoding UTF-8 -cp "$cp;$dir\target\classes" -d "$dir\target\classes" "@$srcPath"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Erro na compilação!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "--- Executando Teste de Todas as 72 Rotas (TesteEndpointsControllers) ---" -ForegroundColor Cyan
& java "-Dfile.encoding=UTF-8" -cp "$cp;$dir\target\classes" br.com.senac.application.TesteEndpointsControllers

Write-Host "--- Executando Teste da Bandeja de Contexto e Emissão de Notas (TesteBandejaContexto) ---" -ForegroundColor Cyan
& java "-Dfile.encoding=UTF-8" -cp "$cp;$dir\target\classes" br.com.senac.application.TesteBandejaContexto
