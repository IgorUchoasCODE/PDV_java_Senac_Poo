param(
    [string]$Nucleo,
    [ValidateSet('UI','Oficial','Terminal','Carga','MCP')][string]$Modo='UI',
    [switch]$Validar,
    [string]$MavenRepository
)
$ErrorActionPreference='Stop'
$modulo=$PSScriptRoot
if (-not $Nucleo) {
    $pai=Split-Path $modulo
    if (Test-Path (Join-Path $pai 'pom.xml')) { $Nucleo=$pai }
    elseif (Test-Path "$env:USERPROFILE/Documents/PDV-javafx-av2/pom.xml") { $Nucleo="$env:USERPROFILE/Documents/PDV-javafx-av2" }
    else { throw 'Informe -Nucleo com a pasta original do projeto, que contem pom.xml.' }
}
$Nucleo=(Resolve-Path -LiteralPath $Nucleo).Path
if (-not (Test-Path "$Nucleo/pom.xml")) { throw 'Nucleo invalido: pom.xml nao encontrado.' }
$compilador=$null
$opcoes=@()
if ($env:JAVA_HOME) { $opcoes+=Join-Path $env:JAVA_HOME 'bin/javac.exe' }
$instalacoes=Join-Path $env:ProgramFiles 'Java'
if (Test-Path $instalacoes) { $opcoes+=Get-ChildItem $instalacoes -Directory -Filter 'jdk*' | Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'bin/javac.exe' } }
$encontrado=Get-Command javac.exe -ErrorAction SilentlyContinue
if ($encontrado) { $opcoes+=$encontrado.Source }
foreach ($candidato in $opcoes) {
    if (-not (Test-Path -LiteralPath $candidato)) { continue }
    $saida=& $candidato -version 2>&1 | Out-String
    if ($saida -match 'javac\s+(\d+)' -and [int]$Matches[1] -ge 21) { $compilador=$candidato;break }
}
if (-not $compilador) { throw 'Instale JDK 21 ou superior e configure JAVA_HOME.' }
$env:JAVA_HOME=Split-Path (Split-Path $compilador)
$build=Join-Path $modulo '.build'
New-Item -ItemType Directory -Force $build | Out-Null
$mvn=Get-Command mvn.cmd -ErrorAction SilentlyContinue
if (-not $mvn) {
    $ferramentas=Join-Path $build 'ferramentas'
    $mavenLocal=Join-Path $ferramentas 'apache-maven-3.9.9/bin/mvn.cmd'
    if (-not (Test-Path $mavenLocal)) {
        New-Item -ItemType Directory -Force $ferramentas | Out-Null
        [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
        $zip=Join-Path $ferramentas 'maven.zip'
        Write-Host 'Obtendo Maven somente na pasta .build desta camada adicional...'
        Invoke-WebRequest -UseBasicParsing 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip' -OutFile $zip
        Expand-Archive -LiteralPath $zip -DestinationPath $ferramentas -Force
    }
    $mvn=Get-Item $mavenLocal
    $mavenExecutavel=$mvn.FullName
} else { $mavenExecutavel=$mvn.Source }
$protegidos=@(Get-ChildItem "$Nucleo/src/main/java","$Nucleo/src/main/resources","$Nucleo/src/test/java" -Recurse -File)
$protegidos+=Get-Item "$Nucleo/pom.xml"
$protegidos+=Get-ChildItem $Nucleo -Filter '*.ps1' -File
$antes=@{}
foreach ($arquivo in $protegidos) { $antes[$arquivo.FullName]=(Get-FileHash -LiteralPath $arquivo.FullName -Algorithm SHA256).Hash }
Set-Location $modulo
$cpArquivo=Join-Path $build 'core-cp.txt'
$mavenArgs=@('-f',"$Nucleo/pom.xml",'-q','dependency:build-classpath',"-Dmdep.outputFile=$cpArquivo")
if ($MavenRepository) { $mavenArgs+="-Dmaven.repo.local=$MavenRepository" }
& $mavenExecutavel @mavenArgs
if ($LASTEXITCODE -ne 0) { throw 'Falha ao resolver as dependencias do pom original.' }
$cp=(Get-Content -LiteralPath $cpArquivo -Raw).Trim()
$coreClasses=Join-Path $build 'core-classes'
$uiClasses=Join-Path $build 'ui-classes'
New-Item -ItemType Directory -Force $coreClasses,$uiClasses | Out-Null
$fontesCore=Get-ChildItem "$Nucleo/src/main/java" -Recurse -Filter '*.java' | ForEach-Object { '"'+($_.FullName -replace '\\','/')+'"' }
$fontesUi=Get-ChildItem "$modulo/java" -Recurse -Filter '*.java' | ForEach-Object { '"'+($_.FullName -replace '\\','/')+'"' }
if ($Validar) { $fontesUi+=Get-ChildItem "$modulo/testes" -Recurse -Filter '*.java' | ForEach-Object { '"'+($_.FullName -replace '\\','/')+'"' } }
$arquivoCore=Join-Path $build 'fontes-core.txt'
$arquivoUi=Join-Path $build 'fontes-ui.txt'
[IO.File]::WriteAllLines($arquivoCore,[string[]]$fontesCore,(New-Object Text.UTF8Encoding($false)))
[IO.File]::WriteAllLines($arquivoUi,[string[]]$fontesUi,(New-Object Text.UTF8Encoding($false)))
& $compilador --release 21 -encoding UTF-8 -cp "$cp" -d "$coreClasses" "@$arquivoCore"
if ($LASTEXITCODE -ne 0) { throw 'A compilacao do nucleo original falhou. Nenhum fonte foi alterado.' }
& $compilador --release 21 -encoding UTF-8 -cp "$coreClasses;$cp" -d "$uiClasses" "@$arquivoUi"
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar a camada nova de UI.' }
$recursos=Join-Path $Nucleo 'src/main/resources'
Get-ChildItem $recursos -Recurse -File | Where-Object { $_.Name -ne 'persistence.xml' -and $_.Name -notmatch '(?i)secret|credential|\.key$|\.env$' } | ForEach-Object {
    $rel=$_.FullName.Substring($recursos.Length).TrimStart('\','/')
    $dest=Join-Path $coreClasses $rel
    New-Item -ItemType Directory -Force (Split-Path $dest) | Out-Null
    Copy-Item -LiteralPath $_.FullName -Destination $dest -Force
}
Copy-Item "$modulo/recursos/*" $uiClasses -Recurse -Force
$ambiente=Join-Path $build 'ambiente'
if ($Validar) { $ambiente=Join-Path $build ('validacao-'+[Guid]::NewGuid().ToString('N')) }
$meta=Join-Path $ambiente 'src/main/resources/META-INF'
New-Item -ItemType Directory -Force $meta,(Join-Path $ambiente 'config'),(Join-Path $ambiente 'docs') | Out-Null
$modelo=Join-Path $Nucleo 'src/main/resources/META-INF/persistence-embarcado.xml'
$persistencia=Join-Path $meta 'persistence.xml'
if (-not (Test-Path $persistencia)) {
    $xml=[IO.File]::ReadAllText($modelo)
    if ($Validar) {
        $xml=$xml -replace 'jdbc:h2:[^"]+', 'jdbc:h2:mem:ui_documentada;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1'
    }
    [IO.File]::WriteAllText($persistencia,$xml,(New-Object Text.UTF8Encoding($false)))
}
Copy-Item -LiteralPath $modelo -Destination (Join-Path $meta 'persistence-embarcado.xml') -Force
$metaCompilada=Join-Path $coreClasses 'META-INF'
New-Item -ItemType Directory -Force $metaCompilada | Out-Null
Copy-Item -LiteralPath $persistencia -Destination (Join-Path $metaCompilada 'persistence.xml') -Force
$ia=Join-Path $ambiente 'config/ia.properties'
if (-not (Test-Path $ia)) { [IO.File]::WriteAllText($ia,"ia.provedor=manual`nia.provedores.failover=`n",(New-Object Text.UTF8Encoding($false))) }
foreach ($nome in @('ia-contexto.properties','personalidade.txt')) {
    $orig=Join-Path "$Nucleo/config" $nome
    $dest=Join-Path "$ambiente/config" $nome
    if ((Test-Path $orig) -and -not (Test-Path $dest)) { Copy-Item -LiteralPath $orig -Destination $dest }
}
$csv=Join-Path $Nucleo 'docs/testesDadosReais18M.csv'
if (Test-Path $csv) { Copy-Item -LiteralPath $csv -Destination (Join-Path $ambiente 'docs/testesDadosReais18M.csv') -Force }
foreach ($caminho in $antes.Keys) {
    if ((Get-FileHash -LiteralPath $caminho -Algorithm SHA256).Hash -ne $antes[$caminho]) { throw "Arquivo existente do nucleo foi alterado fora desta camada: $caminho" }
}
$evidencia=Join-Path $modulo 'evidencias/nucleo-preservado.json'
$registros=@($antes.Keys | Sort-Object | ForEach-Object { @{arquivo=$_.Substring($Nucleo.Length).TrimStart('\','/');sha256=$antes[$_]} })
[IO.File]::WriteAllText($evidencia,($registros | ConvertTo-Json -Depth 4),(New-Object Text.UTF8Encoding($false)))
$classe=switch($Modo) {
    'UI' {'br.com.senac.ui.documentada.EntradaUI'}
    'Oficial' {'br.com.senac.application.SazonaliseiJavaFXLauncher'}
    'Terminal' {'br.com.senac.application.ChatBotTerminal'}
    'Carga' {'br.com.senac.application.ConsoleResetEIntegracao'}
    'MCP' {'br.com.senac.integracao.mcp.McpServidorSazonalisei'}
}
if ($Validar) { $classe='br.com.senac.ui.documentada.VerificarCamadaUI' }
$java=Join-Path $env:JAVA_HOME 'bin/java.exe'
Set-Location $ambiente
& $java '-Dfile.encoding=UTF-8' '--enable-native-access=ALL-UNNAMED' "-Djavafx.cachedir=$build/fx-cache" "-Dui.documentada.evidencias=$modulo/evidencias" -cp "$uiClasses;$coreClasses;$cp" $classe
$codigo=$LASTEXITCODE
Set-Location $modulo
foreach ($caminho in $antes.Keys) {
    if ((Get-FileHash -LiteralPath $caminho -Algorithm SHA256).Hash -ne $antes[$caminho]) { throw "Arquivo existente do nucleo alterado durante a execucao: $caminho" }
}
if ($codigo -ne 0) { throw "Execucao encerrada com codigo $codigo." }
Write-Host "Verificado: $($antes.Count) arquivos existentes do nucleo preservados."
