param(
    [string]$Nucleo,
    [ValidateSet('UI','Oficial','Terminal')][string]$Modo='UI',
    [switch]$Validar,
    [string]$MavenRepository
)
$ErrorActionPreference='Stop'
$parametros=@{} + $PSBoundParameters
$logPasta=Join-Path $PSScriptRoot '.build'
New-Item -ItemType Directory -Force $logPasta | Out-Null
$log=Join-Path $logPasta 'inicializacao.log'
$transcricao=$false
try {
    Start-Transcript -LiteralPath $log -Force | Out-Null
    $transcricao=$true
    & (Join-Path $PSScriptRoot 'iniciar_formatado.ps1') @parametros
} catch {
    Write-Host ''
    Write-Host 'NAO FOI POSSIVEL ABRIR O COMPLEMENTO' -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Yellow
    Write-Host "Registro do erro: $log"
    exit 1
} finally {
    if ($transcricao) { Stop-Transcript | Out-Null }
}
