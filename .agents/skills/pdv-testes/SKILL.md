---
name: pdv-testes
description: >-
  Compila e executa rapidamente as suítes de teste do projeto PDV Java Senac
  (TesteEndpointsControllers com 72 rotas, TesteBandejaContexto e TesteGeminiIntegracao)
  usando javac/java incremental sem sobrecarga. Use sempre que modificar código em src/main.
---

# Execução Rápida de Testes — PDV Java

Use este procedimento para validar alterações em `src/main` com baixo custo de tempo e tokens.

## 1. Suite Completa Padrão (Compilação + 72 Rotas + Bandeja de Contexto)

Execute na raiz do workspace (`C:\workspace\Java\Sistema de Vendas`):

```powershell
powershell -ExecutionPolicy Bypass -File .\executar_testes.ps1
```

O script `executar_testes.ps1`:
1. Lê o classpath em `target/cp.txt` ajustando automaticamente o caminho do `.m2\repository` do usuário atual.
2. Compila todos os arquivos `.java` em `UTF-8` para `target/classes`.
3. Executa `br.com.senac.application.TesteEndpointsControllers` (valida as 72 rotas do `RoteadorController`).
4. Executa `br.com.senac.application.TesteBandejaContexto` (valida staging, confirmação em lote e emissão de notas).

## 2. Caso `target/cp.txt` não exista ou dependências do `pom.xml` mudem

Gere novamente o arquivo de classpath com o Maven antes de rodar os testes:

```powershell
mvn dependency:build-classpath -Dmdep.outputFile=target/cp.txt
```

## 3. Teste Individual da Integração Gemini (Opcional)

Para validar apenas a integração com a IA:

```powershell
powershell -NoProfile -Command "$cp = (Get-Content target\cp.txt -Raw).Trim() -replace 'C:\\Users\\[^\\]+\\.m2\\repository', \"$env:USERPROFILE\.m2\repository\"; java '-Dfile.encoding=UTF-8' -cp \"$cp;target\classes\" br.com.senac.application.TesteGeminiIntegracao"
```
