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

if (!(Test-Path "$buildDir\classes")) {
    New-Item -ItemType Directory -Force -Path "$buildDir\classes" | Out-Null
}
if (!(Test-Path "$buildDir\test-classes")) {
    New-Item -ItemType Directory -Force -Path "$buildDir\test-classes" | Out-Null
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
            $xmlTxt = $xmlTxt -replace 'jdbc:h2:\./data/sazonalisei;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;AUTO_SERVER=TRUE', 'jdbc:h2:mem:sazonalisei_embarcado;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1'
            $xmlTxt = $xmlTxt -replace 'jdbc:h2:\./data/sazonalisei', "jdbc:h2:$dataDir"
            [System.IO.File]::WriteAllText($pXml, $xmlTxt, $utf8SemBom)
        }
    }
}

$sources = Get-ChildItem -Path "$dir\src\main\java" -Filter "*.java" -Recurse | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
[System.IO.File]::WriteAllLines("$buildDir\sources.txt", $sources, $utf8SemBom)
$srcPath = (Get-Item "$buildDir\sources.txt").FullName

Write-Host "--- Compilando Projeto Java (src/main/java) ---" -ForegroundColor Cyan
& javac -encoding UTF-8 -cp "$cp;$buildDir\classes" -d "$buildDir\classes" "@$srcPath"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Erro na compilação principal!" -ForegroundColor Red
    exit $LASTEXITCODE
}

$testSources = Get-ChildItem -Path "$dir\src\test\java" -Filter "*.java" -Recurse | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
if ($testSources.Count -gt 0) {
    [System.IO.File]::WriteAllLines("$buildDir\test-sources.txt", $testSources, $utf8SemBom)
    $testSrcPath = (Get-Item "$buildDir\test-sources.txt").FullName
    Write-Host "--- Compilando Testes Java (src/test/java) ---" -ForegroundColor Cyan
    & javac -encoding UTF-8 -cp "$cp;$buildDir\classes;$buildDir\test-classes" -d "$buildDir\test-classes" "@$testSrcPath"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Erro na compilação dos testes!" -ForegroundColor Red
        exit $LASTEXITCODE
    }
}

$fullCp = "$buildDir\classes;$buildDir\test-classes;$cp"

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
