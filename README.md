# 🛒 Sazonalisei ERP — PDV Java Senac POO

Sistema completo de **Gestão Comercial, Ponto de Venda (PDV), Estoque FIFO por Lotes, Financeiro/Contabilidade e Inteligência Sazonal/Climática com Agente de IA Integrado**, desenvolvido em **Java 21**, **JavaFX 21**, **Hibernate 6 (JPA)** e **HikariCP**.

---

## 📋 Índice

1. [Como Baixar e Rodar no Computador](#-como-baixar-e-rodar-no-computador)
2. [Configuração de Banco de Dados e IA](#-configuração-de-banco-de-dados-e-ia)
3. [Como Executar (GUI, Terminal, MCP e Testes)](#-como-executar-gui-terminal-mcp-e-testes)
4. [O que o Sistema Faz (Funcionalidades Atuais)](#-o-que-o-sistema-faz-funcionalidades-atuais)
5. [O que Pode Fazer (Capacidades Avançadas)](#-o-que-pode-fazer-capacidades-avançadas)
6. [O que Precisa Fazer (Roadmap e Requisitos de Produção)](#-o-que-precisa-fazer-roadmap-e-requisitos-de-produção)
7. [Estrutura do Projeto](#-estrutura-do-projeto)

---

## 💻 Como Baixar e Rodar no Computador

### 1. Pré-requisitos
Certifique-se de ter instalado em sua máquina:
- **Java Development Kit (JDK) 21** (verifique com `java -version` e `javac -version`)
- **Apache Maven 3.9+** (verifique com `mvn -version`)
- **Git** (verifique com `git --version`)
- **Windows PowerShell** (para os scripts `.ps1` de inicialização rápida)
- **MySQL 8.0** *(opcional — para desenvolvimento e testes o sistema também suporta banco embarcado **H2** em modo MySQL)*

### 2. Clonar o Repositório
Abra o terminal (PowerShell ou Git Bash) na pasta onde deseja salvar o projeto e execute:

```bash
git clone https://github.com/IgorUchoasCODE/PDV_java_Senac_Poo.git
cd "PDV_java_Senac_Poo"
```

### 3. Baixar Dependências e Gerar o Classpath
Execute o Maven para baixar todas as bibliotecas (JavaFX 21, Hibernate 6, Jackson, H2, MySQL Connector) e montar o arquivo `target/cp.txt` usado pelos scripts:

```powershell
mvn clean compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
```

---

## ⚙️ Configuração de Banco de Dados e IA

### Banco de Dados (MySQL 8.0 ou H2 Embarcado)
- O projeto utiliza **JPA / Hibernate 6** com pool **HikariCP** configurado em `src/main/resources/META-INF/persistence.xml`.
- Para **desenvolvimento rápido e suíte de testes**, o H2 embarcado em `MODE=MySQL` já funciona sem exigir instalação de servidor externo.
- Para **uso com MySQL 8.0**, crie o schema no seu MySQL local e ajuste usuário/senha/URL em `persistence.xml` (e opcionalmente aplique views/procedures de `Schema_Views_Procedures_FIFO.sql`).

### Configuração dos Provedores de IA (Gemini / OpenAI / Groq / Ollama)
1. Copie o arquivo de exemplo da pasta `config`:
   ```powershell
   Copy-Item config\gemini.properties.example config\gemini.properties
   ```
2. Preencha sua chave da API Gemini em `config/gemini.properties`:
   ```properties
   gemini.api.key=SUA_CHAVE_AQUI
   gemini.model=gemini-3.8-flash
   ```
   *(Alternativamente, defina a variável de ambiente `GEMINI_API_KEY` no sistema operacional).*
3. O sistema também suporta **failover multi-provedor** configurado em `config/ia.properties` (ou pela aba **Configurações & CSV** na própria interface gráfica), permitindo alternar entre **Gemini**, **Groq**, **OpenRouter**, **Cerebras**, **Mistral** e servidores locais compatíveis com OpenAI (como **Ollama** / **LM Studio**).

---

## 🚀 Como Executar (GUI, Terminal, MCP e Testes)

### 1. Interface Gráfica Desktop (JavaFX — Recomendado)
Inicia o aplicativo completo com os 9 módulos visuais, gráficos sazonais, mapa GPS e Copiloto IA:
```powershell
.\iniciar_interface_javafx.ps1
```
*(Ou via Maven: `mvn javafx:run`)*

### 2. Assistente Operacional via Terminal (CLI)
Executa o `ChatBotTerminal` em modo texto/voz diretamente no console:
```powershell
.\iniciar_terminal.ps1
```

### 3. Servidor MCP (Model Context Protocol)
Expõe as ferramentas do ERP para integração com agentes externos compatíveis com MCP:
```powershell
.\iniciar_mcp.ps1
```

### 4. Executar Toda a Bateria de Testes
Compila e valida end-to-end as **85 rotas do `RoteadorController`**, os **64 testes da Bandeja de Contexto** e os **36 testes de fluxo de prompt longo com IA**:
```powershell
.\executar_testes.ps1
```

---

## ✅ O que o Sistema Faz (Funcionalidades Atuais)

A interface gráfica conta com **9 módulos integrados** + **Copiloto IA flutuante**:

1. **🏠 Home & Dashboard Sazonal/Climático**
   - Painel executivo com KPIs em tempo real (clientes, saúde do estoque, faturamento e pendências).
   - Gráfico bipartido orgânico relacionando vendas com **clima/temperatura**, **nível dos rios** e **eventos**.
   - Filtros de período (`7D`, `30D`, `90D`, personalizado) e adaptação dinâmica de tema conforme o clima ou modo escuro.

2. **🛒 PDV Balcão (Frente de Caixa & Pós-Venda)**
   - Busca instantânea de produtos por nome parcial, código ou sigla/UM (`F2`).
   - Modo **Seleção Automática (FIFO/Validade)** ou **Escolha Manual de Lote**.
   - Carrinho com ajuste rápido de quantidade (`+`/`−`), edição rápida de preço unitário (`F3`), desconto global e cupom.
   - **Múltiplas formas de pagamento** no mesmo cupom (ex.: PIX + Dinheiro, ou Parcelado/A Prazo para clientes cadastrados), com divisão rápida (`50%/50%`, `3x`) e cálculo de taxas.
   - **Pós-Venda integrado**: registro de devolução com encadeamento automático de Nota de Perda para abatimento com fornecedor.
   - Comprovante imprimível e exportação em HTML.

3. **📦 Estoque, Produtos, Kits & Lotes FIFO**
   - Cadastro de **Produtos Físicos** (dimensões/pesos), **Produtos Digitais** (links) e **Kits/Conjuntos** compostos.
   - Controle de lotes com validade, alertas de vencimento próximo e baixa FIFO.
   - Galeria de **imagens do produto carregada sob demanda** (carrossel + zoom lightbox).
   - Gestão unificada de **Clientes (PF/PJ)**, **Fornecedores** e **Unidades de Medida**.

4. **💰 Financeiro & Contabilidade**
   - Emissão e consulta de **Notas de Compra (Entrada)** e **Notas de Venda**.
   - Contas a pagar/receber, parcelas, baixa de quitação e cálculo automático de taxas de operadora.
   - Controle de **crédito/abatimento de perdas** com fornecedores.

5. **📊 Estatística Preditiva & Causas**
   - Análise de variação de demanda e correlação com fatores externos (chuva, calor, cheia/vazante dos rios e eventos próximos).

6. **🗺️ Mapa GPS, Clima & Eventos Próximos**
   - Mapa interativo com *tiles* reais (CartoDB Voyager / OpenStreetMap) e cache offline em `storage/map_tiles/`.
   - Geocodificação automática de eventos próximos e cálculo de distância em km (**Fórmula de Haversine**) a partir da localização da loja.
   - Atalhos para perguntar estratégia à IA ou montar compra na Bandeja com base no evento.

7. **🗂️ Planejamento de Demanda (Kanban)**
   - Quadro Kanban para organizar reposições, lançamentos de produtos e estratégias sazonais.

8. **🤖 Central de Controle Operacional por IA**
   - **Console Livre & Histórico**: execução de comandos complexos em linguagem natural ou voz, com histórico expansível.
   - **Central Unificada por Setor**: playbooks prontos para PDV, Estoque, Financeiro e Clima/Kanban.
   - **Bandeja de Operações em Lote (`BandejaOperacoesContexto`)**: pré-visualiza, valida conflitos e dependências entre múltiplas operações antes de gravar no banco de dados.

9. **⚙️ Configurações & Importação CSV**
   - Gerenciamento de provedores/modelos de IA, personalidade do assistente, tamanho de fonte para acessibilidade no balcão e importador guiado de planilhas CSV (`ImportadorCsvService`).

---

## 🌟 O que Pode Fazer (Capacidades Avançadas)

- **Orquestração Multi-Etapas com IA**: Um único prompt complexo (ex.: cadastrar unidades, fornecedores, produtos, montar kits e emitir nota de compra) é decomposto em etapas ordenadas por dependência, pré-validado na Bandeja e executado atomicamente após confirmação.
- **Operação por Voz no Balcão**: Captura de áudio via microfone (`CapturadorVozMicrofone`) com fallback para ditado assistido.
- **Integração com Agentes Externos via MCP**: Permite que ferramentas de IA externas consultem e operem as 85 rotas do sistema de forma padronizada.
- **Simulação e Resiliência Offline**: O mapa possui modo radar offline em cache local e o banco pode rodar de forma embarcada (H2) sem depender de internet para o PDV básico.

---

## 🛠️ O que Precisa Fazer (Roadmap e Requisitos de Produção)

Para levar o projeto a um ambiente comercial em produção, ainda é necessário ou recomendado implementar:

1. **Emissão Fiscal Oficial (SEFAZ)**: Integrar assinatura digital (Certificado A1/A3) e transmissão de **NFC-e / NF-e** (atualmente o sistema gera comprovantes operacionais e recibos A4/HTML).
2. **Controle de Acesso e Login Multi-operador**: Implementar tela de login com perfis de permissão (Caixa, Estoquista, Gerente/Admin), abertura/fechamento de caixa por operador e auditoria por usuário.
3. **Banco de Dados Dedicado em Produção**: Configurar instância **MySQL 8.0** dedicada com rotina automatizada de backup e variáveis de ambiente seguras (evitando credenciais em arquivos locais).
4. **Configuração de Chaves de IA Privadas**: Inserir chaves próprias de API (`GEMINI_API_KEY` ou equivalente) no ambiente de produção e monitorar cotas de consumo.
5. **Testes Automatizados de Interface (UI)**: Adicionar suíte de testes E2E para JavaFX (ex.: TestFX) complementando os testes já existentes de controllers, rotas e motor de IA.
6. **Empacotamento Instalador (`jpackage`)**: Gerar executável/instalador nativo (`.exe` / `.msi`) para distribuição em máquinas de balcão sem necessidade de Maven instalado.

---

## 📁 Estrutura do Projeto

```text
├── config/                     # Configurações de provedores IA (gemini.properties, ia.properties) e personalidade
├── docs/                       # Documentação arquitetural, PDF oficial e capturas da interface
├── src/
│   ├── main/java/br/com/senac/
│   │   ├── application/        # Pontos de entrada (Main, ChatBotTerminal, servidor MCP)
│   │   ├── controller/         # RoteadorController (85 rotas) e controllers de domínio
│   │   ├── service/            # Regras de negócio, estoque FIFO, geolocalização, CSV e clima
│   │   ├── repository/         # Entidades JPA (modal) e DAOs
│   │   ├── integracao/         # Motor de IA (Gemini/OpenAI), ToolRegistry e BandejaOperacoesContexto
│   │   └── view/javafx/        # Interface Gráfica JavaFX (9 abas, componentes, tema e ponte IA)
│   └── test/java/              # Suítes de teste (TesteEndpointsControllers, TesteBandejaContexto, etc.)
├── executar_testes.ps1         # Script de compilação e execução completa dos testes
├── iniciar_interface_javafx.ps1# Compila e abre a interface gráfica JavaFX
├── iniciar_terminal.ps1        # Compila e abre o ChatBot no terminal
└── pom.xml                     # Dependências Maven (Java 21, JavaFX 21, Hibernate 6, H2, MySQL, Jackson)
```
