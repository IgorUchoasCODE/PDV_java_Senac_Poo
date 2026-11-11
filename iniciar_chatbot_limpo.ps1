# Script para iniciar o Novo Prompt Limpo e Humanizado (Sem logs do Hibernate/SLF4J e sem poluição visual)
$ErrorActionPreference = "SilentlyContinue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$cp = (Get-Content target\cp.txt -Raw).Trim() -replace 'C:\\Users\\[^\\]+\\.m2\\repository', "$env:USERPROFILE\.m2\repository"
java "-Dfile.encoding=UTF-8" -cp "$cp;target\classes" br.com.senac.application.ChatBotPromptLimpo
