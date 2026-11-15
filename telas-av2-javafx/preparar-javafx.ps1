$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
New-Item -ItemType Directory -Force lib | Out-Null
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
foreach ($modulo in @('base', 'graphics', 'controls')) {
    $nome = "javafx-$modulo-21.0.6-win.jar"
    $destino = Join-Path "$PSScriptRoot/lib" $nome
    if (-not (Test-Path -LiteralPath $destino)) {
        Write-Host "Obtendo JavaFX 21.0.6: $modulo..."
        $url = "https://repo.maven.apache.org/maven2/org/openjfx/javafx-$modulo/21.0.6/$nome"
        Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $destino
    }
}
