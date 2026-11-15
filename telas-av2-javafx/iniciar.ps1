$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
try {
    . "$PSScriptRoot/ferramentas.ps1"
    & "$PSScriptRoot/preparar-javafx.ps1"
    $executor = Get-JavaTool 'java'
    if (-not (Test-Path senac-pdv-javafx.jar)) { & "$PSScriptRoot/compilar.ps1" }
    New-Item -ItemType Directory -Force build/cache | Out-Null
    $cache = (Resolve-Path build/cache).Path
    & $executor '--enable-native-access=javafx.graphics' "-Djavafx.cachedir=$cache" --module-path lib --add-modules javafx.controls -jar senac-pdv-javafx.jar
    if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel iniciar a interface. Confira a versao do Java e a pasta lib.' }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    Read-Host 'Pressione Enter para fechar'
    exit 1
}
