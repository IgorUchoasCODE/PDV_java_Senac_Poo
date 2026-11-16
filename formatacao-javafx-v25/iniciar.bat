@echo off
setlocal
cd /d "%~dp0"
echo Sazonalisei 2.5 - Abrindo complemento de formatacao...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0abrir_complemento.ps1" %*
set "launchResult=%errorlevel%"
echo.
if errorlevel 1 echo A abertura falhou. Leia a mensagem acima e o arquivo .build\inicializacao.log.
pause
exit /b %launchResult%
