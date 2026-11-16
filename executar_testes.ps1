$dir = $PSScriptRoot
Set-Location $dir

$cp = (Get-Content "$dir\target\cp.txt" -Raw).Trim() -replace 'C:\\Users\\[^\\]+\\.m2\\repository', "$env:USERPROFILE\.m2\repository"
if ($cp.StartsWith('-cp ')) {
    $cp = $cp.Substring(4).Trim().Trim('"')
}
if (!(Test-Path "$dir\target\classes")) {
    New-Item -ItemType Directory -Force -Path "$dir\target\classes" | Out-Null
}
if (!(Test-Path "$dir\target\test-classes")) {
    New-Item -ItemType Directory -Force -Path "$dir\target\test-classes" | Out-Null
}
if (Test-Path "$dir\src\main\resources") {
    Copy-Item -Path "$dir\src\main\resources\*" -Destination "$dir\target\classes" -Recurse -Force
}
$sources = Get-ChildItem -Path "$dir\src\main\java" -Filter "*.java" -Recurse | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
[System.IO.File]::WriteAllLines("$dir\target\sources.txt", $sources, (New-Object System.Text.UTF8Encoding($false)))
$srcPath = (Get-Item "$dir\target\sources.txt").FullName

Write-Host "--- Compilando Projeto Java (src/main/java) ---" -ForegroundColor Cyan
& javac -encoding UTF-8 -cp "$cp;$dir\target\classes" -d "$dir\target\classes" "@$srcPath"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Erro na compilação principal!" -ForegroundColor Red
    exit $LASTEXITCODE
}

$testSources = Get-ChildItem -Path "$dir\src\test\java" -Filter "*.java" -Recurse | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
if ($testSources.Count -gt 0) {
    [System.IO.File]::WriteAllLines("$dir\target\test-sources.txt", $testSources, (New-Object System.Text.UTF8Encoding($false)))
    $testSrcPath = (Get-Item "$dir\target\test-sources.txt").FullName
    Write-Host "--- Compilando Testes Java (src/test/java) ---" -ForegroundColor Cyan
    & javac -encoding UTF-8 -cp "$cp;$dir\target\classes;$dir\target\test-classes" -d "$dir\target\test-classes" "@$testSrcPath"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Erro na compilação dos testes!" -ForegroundColor Red
        exit $LASTEXITCODE
    }
}

$fullCp = "$cp;$dir\target\classes;$dir\target\test-classes"

Write-Host "--- Executando Teste de Todas as Rotas (TesteEndpointsControllers) ---" -ForegroundColor Cyan
& java "-Dfile.encoding=UTF-8" -cp "$fullCp" br.com.senac.application.TesteEndpointsControllers
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "--- Executando Teste da Bandeja de Contexto e Emissão de Notas (TesteBandejaContexto) ---" -ForegroundColor Cyan
& java "-Dfile.encoding=UTF-8" -cp "$fullCp" br.com.senac.application.TesteBandejaContexto
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "--- Executando Teste da Bandeja Passo a Passo (TesteBandejaPassoAPasso) ---" -ForegroundColor Cyan
& java "-Dfile.encoding=UTF-8" -cp "$fullCp" br.com.senac.application.TesteBandejaPassoAPasso
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "--- Executando Teste de Simulação e Perguntas na Bandeja (TesteSimulacaoBandeja) ---" -ForegroundColor Cyan
& java "-Dfile.encoding=UTF-8" -cp "$fullCp" br.com.senac.application.TesteSimulacaoBandeja
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "--- Executando Teste do Parser AST e Pré-processador (TesteParserEPreProcessador) ---" -ForegroundColor Cyan
& java "-Dfile.encoding=UTF-8" -cp "$fullCp" br.com.senac.application.TesteParserEPreProcessador
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "--- Executando Teste do Importador CSV e Reset Restrito (TesteImportadorCsvEReset) ---" -ForegroundColor Cyan
& java "-Dfile.encoding=UTF-8" -cp "$fullCp" br.com.senac.application.TesteImportadorCsvEReset
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "--- Executando Teste de Contexto: Prompt Longo e Validação 3 Fases (TesteFluxoPromptLongoEValidacao) ---" -ForegroundColor Cyan
& java "-Dfile.encoding=UTF-8" -cp "$fullCp" br.com.senac.application.TesteFluxoPromptLongoEValidacao
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
