param([switch]$Testar)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
. "$PSScriptRoot/ferramentas.ps1"
& "$PSScriptRoot/preparar-javafx.ps1"
$compilador = Get-JavaTool 'javac'
$pastaBin = Split-Path $compilador
$empacotador = Join-Path $pastaBin 'jar.exe'
$executor = Join-Path $pastaBin 'java.exe'
New-Item -ItemType Directory -Force build/classes,build/cache,build/temp | Out-Null
$fontes = Get-ChildItem src/main/java -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
& $compilador --release 21 --module-path lib --add-modules javafx.controls -encoding UTF-8 -d build/classes $fontes
if ($LASTEXITCODE -ne 0) { throw 'Falha na compilacao JavaFX.' }
Copy-Item src/main/resources/* build/classes -Force
$tempJar = (Resolve-Path build/temp).Path
& $empacotador "-J-Djava.io.tmpdir=$tempJar" --create --file "$PSScriptRoot/senac-pdv-javafx.jar" --main-class br.com.senac.view.av2.PdvApplication -C build/classes .
if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar JAR JavaFX.' }
Write-Host 'JavaFX compilado. Abra iniciar.bat para executar.'
if ($Testar) {
    & $compilador --release 21 -encoding UTF-8 -cp build/classes -d build/classes testes/VerificarDemo.java
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar verificacoes.' }
    & $executor -cp build/classes br.com.senac.view.av2.VerificarDemo
    if ($LASTEXITCODE -ne 0) { throw 'Falha nas verificacoes da demonstracao.' }
    $cache = (Resolve-Path build/cache).Path
    & $executor '--enable-native-access=javafx.graphics' "-Djavafx.cachedir=$cache" '-Dprism.order=sw' --module-path lib --add-modules javafx.controls -jar senac-pdv-javafx.jar --verificar-ui
    if ($LASTEXITCODE -ne 0) { throw 'Falha na verificacao da interface JavaFX.' }
}
