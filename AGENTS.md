# Diretrizes do Agente — PDV Java Senac POO

Regras de arquitetura, economia de tokens e verificação proativa para o projeto `PDV_java_Senac_Poo`.

## 1. Arquitetura e Camadas (`src/main/java/br/com/senac`)

- **Stack**: Java 21, Hibernate 6.4 (JPA) + HikariCP, H2 (modo MySQL em dev/testes) / MySQL 8.0 (runtime), Jackson JSON.
- **Fluxo de Camadas**:
  - `application`: Pontos de entrada, CLI (`ChatBotTerminal`), servidor MCP e suítes de teste (`TesteEndpointsControllers`, `TesteBandejaContexto`, `TesteGeminiIntegracao`).
  - `controller`: Endpoints REST-like centralizados no `RoteadorController` (72 rotas) e controllers de domínio (`ProdutoController`, `NotaController`, `UsuarioController`, etc.).
  - `service` / `repository.dao`: Regras de negócio e persistência via `JPAUtil` (respeitando SOLID — nunca importar classes específicas do driver MySQL diretamente no código).
  - `integracao.ia` e `integracao.gemini`: Integração com LLMs (`ProvedorIA`, `GeminiApiProvedor`, `OpenAiCompativelProvedor`), `GeminiAgenteOperacional`, `GeminiToolRegistry` e `BandejaOperacoesContexto`.

## 2. Economia de Tokens e Contexto

- **No Agente Antigravity**:
  - Prefira buscas direcionadas e leitura parcial de arquivos grandes (`StartLine`/`EndLine`).
  - Consulte a skill local `pdv-testes` apenas quando for compilar ou executar baterias de teste.
- **Na Integração IA do Projeto (`BandejaOperacoesContexto`)**:
  - Preserve o padrão de enfileiramento em lote (staging na bandeja antes do commit no banco) e resumos compactos para minimizar chamadas e consumo de tokens da API Gemini/OpenAI.

## 3. Proatividade no Desenvolvimento

- **Validação Automática**: Sempre que alterar classes em `controller`, `service`, `repository` ou `integracao`, execute proativamente o script `./executar_testes.ps1` para garantir que a compilação e as 72 rotas + bandeja de contexto continuam passando.
- **Integridade da Documentação**: Mantenha os comentários e Javadocs existentes intactos ao editar arquivos.
