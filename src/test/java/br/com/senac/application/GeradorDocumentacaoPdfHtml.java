package br.com.senac.application;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Gerador da Documentação Técnica Oficial Completa do Sazonalisei ERP em HTML/CSS para conversão em PDF.
 * Inclui arquitetura core, 85 rotas, hidrologia amazônica, matemática contábil BACEN, IA,
 * e os 9 módulos da interface gráfica JavaFX com screenshots reais em alta resolução.
 */
public class GeradorDocumentacaoPdfHtml {

    public static void main(String[] args) throws Exception {
        System.out.println("--- Gerando Documentação Técnica Oficial Sazonalisei (HTML/PDF) ---");
        String tempDir = System.getProperty("java.io.tmpdir") + File.separator + "sazonalisei_imagens";
        File dirImgs = new File(tempDir);

        Map<String, String> b64Imgs = new HashMap<>();
        String[] nomesImgs = {
                "01_home_dashboard_clima.png",
                "02_pdv_balcao_multi_pagamentos.png",
                "03_catalogo_estoque_lotes.png",
                "04_financeiro_saude_contabil.png",
                "05_estatistica_preditiva_causas.png",
                "06_mapa_gps_clima_relevo.png",
                "07_planejamento_demanda_kanban.png",
                "08_controle_operacional_ia.png",
                "09_configuracoes_mapeador_csv.png",
                "10_tema_escuro_interface.png"
        };

        for (String nome : nomesImgs) {
            File f = new File(dirImgs, nome + ".b64");
            if (f.exists()) {
                b64Imgs.put(nome, Files.readString(f.toPath(), StandardCharsets.UTF_8).trim());
            } else {
                b64Imgs.put(nome, "");
            }
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"pt-BR\">\n<head>\n<meta charset=\"UTF-8\">\n");
        html.append("<title>Documentação Técnica Sazonalisei ERP & ChatBotTerminal</title>\n");
        html.append("<style>\n");
        html.append("@page {\n");
        html.append("    size: A4;\n");
        html.append("    margin: 18mm 14mm 18mm 14mm;\n");
        html.append("    @bottom-right {\n");
        html.append("        content: counter(page);\n");
        html.append("        font-family: 'Segoe UI', Arial, sans-serif;\n");
        html.append("        font-size: 8pt;\n");
        html.append("        color: #64748B;\n");
        html.append("    }\n");
        html.append("    @bottom-left {\n");
        html.append("        content: 'Sazonalisei ERP • Senac POO — Documentação Técnica Oficial';\n");
        html.append("        font-family: 'Segoe UI', Arial, sans-serif;\n");
        html.append("        font-size: 8pt;\n");
        html.append("        color: #94A3B8;\n");
        html.append("    }\n");
        html.append("}\n");
        html.append("body {\n");
        html.append("    font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, Helvetica, Arial, sans-serif;\n");
        html.append("    font-size: 9.5pt;\n");
        html.append("    line-height: 1.55;\n");
        html.append("    color: #1E293B;\n");
        html.append("    background-color: #FFFFFF;\n");
        html.append("    margin: 0;\n");
        html.append("    padding: 0;\n");
        html.append("}\n");
        html.append("h1, h2, h3, h4 { color: #0F172A; font-weight: 700; margin-top: 18pt; margin-bottom: 8pt; }\n");
        html.append("h1 { font-size: 20pt; border-bottom: 2px solid #0284C7; padding-bottom: 6pt; page-break-before: always; }\n");
        html.append("h1.primeira-pagina { page-break-before: avoid; margin-top: 0; }\n");
        html.append("h2 { font-size: 14pt; color: #0369A1; border-bottom: 1px solid #E2E8F0; padding-bottom: 4pt; }\n");
        html.append("h3 { font-size: 11pt; color: #0F172A; margin-top: 12pt; }\n");
        html.append("p { margin-top: 0; margin-bottom: 8pt; text-align: justify; }\n");
        html.append(".capa {\n");
        html.append("    text-align: center;\n");
        html.append("    padding: 50pt 20pt 30pt 20pt;\n");
        html.append("    page-break-after: always;\n");
        html.append("}\n");
        html.append(".capa-badge {\n");
        html.append("    display: inline-block;\n");
        html.append("    background-color: #E0F2FE;\n");
        html.append("    color: #0369A1;\n");
        html.append("    padding: 4pt 12pt;\n");
        html.append("    border-radius: 12pt;\n");
        html.append("    font-weight: 700;\n");
        html.append("    font-size: 9pt;\n");
        html.append("    letter-spacing: 0.5pt;\n");
        html.append("    margin-bottom: 14pt;\n");
        html.append("}\n");
        html.append(".capa h1 {\n");
        html.append("    font-size: 26pt;\n");
        html.append("    color: #0F172A;\n");
        html.append("    border: none;\n");
        html.append("    margin-bottom: 8pt;\n");
        html.append("    page-break-before: avoid;\n");
        html.append("}\n");
        html.append(".capa .sub {\n");
        html.append("    font-size: 12pt;\n");
        html.append("    color: #475569;\n");
        html.append("    max-width: 520pt;\n");
        html.append("    margin: 0 auto 30pt auto;\n");
        html.append("    line-height: 1.6;\n");
        html.append("}\n");
        html.append(".capa-meta {\n");
        html.append("    margin-top: 60pt;\n");
        html.append("    padding-top: 20pt;\n");
        html.append("    border-top: 1px solid #E2E8F0;\n");
        html.append("    font-size: 9pt;\n");
        html.append("    color: #64748B;\n");
        html.append("    line-height: 1.8;\n");
        html.append("}\n");
        html.append(".box-resumo {\n");
        html.append("    background-color: #F8FAFC;\n");
        html.append("    border: 1px solid #CBD5E1;\n");
        html.append("    border-left: 4px solid #0284C7;\n");
        html.append("    border-radius: 6px;\n");
        html.append("    padding: 10pt 14pt;\n");
        html.append("    margin: 12pt 0;\n");
        html.append("    font-size: 9pt;\n");
        html.append("}\n");
        html.append(".box-aviso {\n");
        html.append("    background-color: #FEF3C7;\n");
        html.append("    border: 1px solid #FCD34D;\n");
        html.append("    border-left: 4px solid #D97706;\n");
        html.append("    border-radius: 6px;\n");
        html.append("    padding: 8pt 12pt;\n");
        html.append("    margin: 10pt 0;\n");
        html.append("    font-size: 8.5pt;\n");
        html.append("    color: #92400E;\n");
        html.append("}\n");
        html.append(".box-sucesso {\n");
        html.append("    background-color: #ECFDF5;\n");
        html.append("    border: 1px solid #A7F3D0;\n");
        html.append("    border-left: 4px solid #059669;\n");
        html.append("    border-radius: 6px;\n");
        html.append("    padding: 8pt 12pt;\n");
        html.append("    margin: 10pt 0;\n");
        html.append("    font-size: 8.5pt;\n");
        html.append("    color: #065F46;\n");
        html.append("}\n");
        html.append("table {\n");
        html.append("    width: 100%;\n");
        html.append("    border-collapse: collapse;\n");
        html.append("    margin: 10pt 0;\n");
        html.append("    font-size: 8.5pt;\n");
        html.append("}\n");
        html.append("th {\n");
        html.append("    background-color: #0F172A;\n");
        html.append("    color: #FFFFFF;\n");
        html.append("    font-weight: 600;\n");
        html.append("    text-align: left;\n");
        html.append("    padding: 6pt 8pt;\n");
        html.append("    border: 1px solid #334155;\n");
        html.append("}\n");
        html.append("td {\n");
        html.append("    padding: 5pt 8pt;\n");
        html.append("    border: 1px solid #E2E8F0;\n");
        html.append("    vertical-align: top;\n");
        html.append("}\n");
        html.append("tr:nth-child(even) td { background-color: #F8FAFC; }\n");
        html.append(".tag-endpoint {\n");
        html.append("    display: inline-block;\n");
        html.append("    background-color: #EFF6FF;\n");
        html.append("    color: #1D4ED8;\n");
        html.append("    border: 1px solid #BFDBFE;\n");
        html.append("    padding: 1pt 5pt;\n");
        html.append("    border-radius: 4px;\n");
        html.append("    font-family: Consolas, monospace;\n");
        html.append("    font-size: 7.8pt;\n");
        html.append("    font-weight: 600;\n");
        html.append("}\n");
        html.append(".screenshot-card {\n");
        html.append("    background-color: #FFFFFF;\n");
        html.append("    border: 1px solid #CBD5E1;\n");
        html.append("    border-radius: 8px;\n");
        html.append("    padding: 8pt;\n");
        html.append("    margin: 14pt 0;\n");
        html.append("    page-break-inside: avoid;\n");
        html.append("    box-shadow: 0 2px 4px rgba(0,0,0,0.04);\n");
        html.append("}\n");
        html.append(".screenshot-img {\n");
        html.append("    width: 100%;\n");
        html.append("    max-height: 380pt;\n");
        html.append("    object-fit: contain;\n");
        html.append("    border: 1px solid #E2E8F0;\n");
        html.append("    border-radius: 6px;\n");
        html.append("    display: block;\n");
        html.append("}\n");
        html.append(".screenshot-legenda {\n");
        html.append("    margin-top: 6pt;\n");
        html.append("    font-size: 8pt;\n");
        html.append("    color: #64748B;\n");
        html.append("    text-align: center;\n");
        html.append("}\n");
        html.append(".screenshot-legenda strong {\n");
        html.append("    color: #0F172A;\n");
        html.append("}\n");
        html.append(".diagrama-ascii {\n");
        html.append("    background-color: #0F172A;\n");
        html.append("    color: #38BDF8;\n");
        html.append("    padding: 8pt 12pt;\n");
        html.append("    border-radius: 6px;\n");
        html.append("    font-family: Consolas, 'Courier New', monospace;\n");
        html.append("    font-size: 7.5pt;\n");
        html.append("    line-height: 1.35;\n");
        html.append("    white-space: pre;\n");
        html.append("    overflow-x: auto;\n");
        html.append("    margin: 10pt 0;\n");
        html.append("}\n");
        html.append(".grid-2 {\n");
        html.append("    display: flex;\n");
        html.append("    gap: 10pt;\n");
        html.append("}\n");
        html.append(".grid-2 > div {\n");
        html.append("    flex: 1;\n");
        html.append("}\n");
        html.append("</style>\n</head>\n<body>\n");

        // CAPA
        html.append("<div class=\"capa\">\n");
        html.append("  <div class=\"capa-badge\">SISTEMA SAZONALISEI ERP • VERSÃO 2.5 INTEGRADA</div>\n");
        html.append("  <h1>Documentação Técnica Oficial & Arquitetura</h1>\n");
        html.append("  <div class=\"sub\">Plataforma Inteligente de Gestão Comercial, Inteligência Sazonal Hidrológica, Matemática Contábil BACEN, Motor de IA com Function Calling e Interface Gráfica JavaFX Corporativa de 9 Módulos.</div>\n");
        html.append("  <div class=\"box-resumo\" style=\"max-width: 440pt; margin: 0 auto; text-align: left;\">\n");
        html.append("    <strong>Principais Especificações de Engenharia:</strong><br>\n");
        html.append("    • <strong>Linguagem & Runtime:</strong> Java 21 LTS (Virtual Threads, Records, Pattern Matching)<br>\n");
        html.append("    • <strong>Persistência & Dados:</strong> Hibernate 6.4 (JPA) + HikariCP Connection Pool (H2 MySQL Mode / MySQL 8.0)<br>\n");
        html.append("    • <strong>Catálogo de Integração:</strong> 85 Rotas REST-like no <code>RoteadorController</code> + 85 Ferramentas Gemini Tool Registry<br>\n");
        html.append("    • <strong>Interface Gráfica:</strong> JavaFX 21 Modular com 9 Abas Especializadas, Carregamento Sob Demanda e Tema Climático Dinâmico<br>\n");
        html.append("    • <strong>Conformidade Contábil:</strong> Arredondamento Bancário BACEN Half-Even e Rigor Fiscal de Lotes FIFO<br>\n");
        html.append("  </div>\n");
        html.append("  <div class=\"capa-meta\">\n");
        html.append("    <strong>Projeto Acadêmico e Comercial:</strong> Senac POO — Sistema de Vendas Sazonalisei<br>\n");
        html.append("    <strong>Autoria / Engenharia:</strong> Equipe de Desenvolvimento Sazonalisei & Antigravity AI Assistant<br>\n");
        html.append("    <strong>Data de Homologação:</strong> Novembro de 2026 • 100% dos Testes Aprovados (85/85 Rotas + Bateria de Integração)\n");
        html.append("  </div>\n");
        html.append("</div>\n");

        // CAPÍTULO 1: ARQUITETURA
        html.append("<h1 class=\"primeira-pagina\">1. Visão Geral e Arquitetura Hexagonal</h1>\n");
        html.append("<p>O <strong>Sazonalisei ERP</strong> é uma solução corporativa de alta precisão desenvolvida sob a filosofia de arquitetura em camadas e princípios SOLID, garantindo isolamento rigoroso entre as regras de negócio contábeis, a camada de persistência e os adaptadores de interface e inteligência artificial.</p>\n");
        html.append("<div class=\"diagrama-ascii\">\n");
        html.append(" +-------------------------------------------------------------------------------------------------+\n");
        html.append(" |                            CAMADA DE APRESENTAÇÃO E CONTROLE                                    |\n");
        html.append(" |   +------------------------------------+    +-----------------------------------------------+   |\n");
        html.append(" |   |  Interface Gráfica JavaFX (9 Abas) |    |  Terminal CLI / MCP / ChatBot Operacional IA  |   |\n");
        html.append(" |   +-----------------+------------------+    +-----------------------+-----------------------+   |\n");
        html.append(" +---------------------|-----------------------------------------------|---------------------------+\n");
        html.append("                       | (PonteRoteadorIA / Dispatcher Não Bloqueante) |\n");
        html.append(" +---------------------v-----------------------------------------------v---------------------------+\n");
        html.append(" |                    ROTEADOR CENTRAL DO SISTEMA (85 ROTAS HOMOLOGADAS)                          |\n");
        html.append(" |  ProdutoController | PessoaController | EstoqueController | NotaController | PagamentoController |\n");
        html.append(" |  EstatisticaController | FatorExternoController | SistemaController                             |\n");
        html.append(" +---------------------------------------------+---------------------------------------------------+\n");
        html.append("                                               |\n");
        html.append(" +---------------------------------------------v---------------------------------------------------+\n");
        html.append(" |                     SERVIÇOS DE DOMÍNIO E MATEMÁTICA CONTÁBIL                                    |\n");
        html.append(" |  ManageEstoque | ManageNotas (FIFO) | MatematicaContabilBacen | FatorExternoSazonalidadeService  |\n");
        html.append(" +---------------------------------------------+---------------------------------------------------+\n");
        html.append("                                               |\n");
        html.append(" +---------------------------------------------v---------------------------------------------------+\n");
        html.append(" |                PERSISTÊNCIA JPA / HIBERNATE 6.4 + HIKARICP CONNECTION POOL                      |\n");
        html.append(" |  JPAUtil (Driver Isolation) -> Dialeto MySQL / H2 MySQL-Mode (Zero vazamento de driver nativo)  |\n");
        html.append(" +-------------------------------------------------------------------------------------------------+\n");
        html.append("</div>\n");
        html.append("<p>A separação assegura que qualquer módulo da interface gráfica ou ferramenta da IA opere através do <code>RoteadorController</code>, impedindo acesso acoplado ou direto ao EntityManager ou DAOs.</p>\n");

        // CAPÍTULO 2: INTELIGÊNCIA SAZONAL E HIDROLOGIA
        html.append("<h1>2. Inteligência Sazonal e Hidrologia Amazônica</h1>\n");
        html.append("<p>O Sazonalisei integra a sazonalidade climática e hidrológica diretamente à gestão comercial de estoques. O sistema monitora a Bacia Amazônica (Rio Negro e Solimões) e adapta estoques e estratégias comerciais a 4 regimes hidrológicos críticos:</p>\n");
        html.append("<table>\n");
        html.append("  <tr><th>Regime Hidrológico</th><th>Nível do Rio (Metros)</th><th>Impacto Operacional no Comércio</th><th>Ação Recomendada pela IA</th></tr>\n");
        html.append("  <tr><td><strong>Seca Severa</strong></td><td>Abaixo de 16,00 m</td><td>Isolamento fluvial, fretes fluviais até 300% mais caros, escassez de produtos volumosos.</td><td>Antecipar compras de itens pesados, ampliar margem de segurança e reforçar produtos essenciais.</td></tr>\n");
        html.append("  <tr><td><strong>Vazante</strong></td><td>16,00 m a 22,00 m</td><td>Descida das águas, restrições progressivas de calado nas hidrovias.</td><td>Planejar pedidos em lotes consolidados junto aos fornecedores antes do pico de seca.</td></tr>\n");
        html.append("  <tr><td><strong>Cheia Normal</strong></td><td>22,00 m a 28,00 m</td><td>Navegação fluida, fluxo regular de suprimentos e abastecimento pleno.</td><td>Campanhas ativas de vendas sazonais, liquidação de excedentes e giro de mercadorias.</td></tr>\n");
        html.append("  <tr><td><strong>Cheia Histórica</strong></td><td>Acima de 28,00 m</td><td>Alagamento de áreas ribeirinhas e comerciais, risco de perdas em estoque físico no nível térreo.</td><td>Elevação física de lotes, foco em itens de proteção (capas, lonas, kits) e ajuste de prazo.</td></tr>\n");
        html.append("</table>\n");

        // CAPÍTULO 3: ENGENHARIA DE ESTOQUE E FIFO
        html.append("<h1>3. Engenharia de Estoque e Algoritmo FIFO</h1>\n");
        html.append("<p>A baixa de produtos na venda e em perdas segue estritamente a ordem cronológica de entrada (FIFO — First-In, First-Out) com controle de data de validade (FEFO):</p>\n");
        html.append("<ul>\n");
        html.append("  <li><strong>Desmembramento Automático:</strong> Quando a quantidade solicitada em uma venda supera o saldo disponível no lote mais antigo, o sistema consome integralmente o lote corrente e encadeia o consumo do lote subsequente, gerando múltiplos itens de venda com rastreabilidade unitária.</li>\n");
        html.append("  <li><strong>Cálculo do CMV Unitário Real:</strong> Cada item de venda armazena a referência do lote de compra de onde saiu, permitindo que o Custo das Mercadorias Vendidas (CMV) reflita o valor real de aquisição, sem médias imprecisas.</li>\n");
        html.append("  <li><strong>Baixa Direta de Perdas em Estoque:</strong> Registra perdas por avaria, vencimento ou extravio, gerando automaticamente a <code>NotaPerda</code> vinculada ao lote específico e alimentando a auditoria contábil.</li>\n");
        html.append("</ul>\n");

        // CAPÍTULO 4: FINANCEIRO E BACEN
        html.append("<h1>4. Matemática Contábil BACEN e Módulo Financeiro</h1>\n");
        html.append("<p>Todas as operações de cálculo financeiro do Sazonalisei utilizam a classe <code>MoedaContabilBacen</code> em conformidade com as regras do Banco Central do Brasil:</p>\n");
        html.append("<ul>\n");
        html.append("  <li><strong>Precisão Interna vs. Final:</strong> Cálculos internos operam com 6 a 8 casas decimais (<code>MathContext.DECIMAL64</code>), e o arredondamento final de fechamento contábil e de parcelas adota 2 casas decimais no padrão <code>RoundingMode.HALF_EVEN</code> (arredondamento bancário).</li>\n");
        html.append("  <li><strong>Quitação e Múltiplos Pagamentos:</strong> O sistema suporta a associação de múltiplos pagamentos a uma mesma nota (ex: R$ 50,00 no PIX à vista + R$ 100,00 no Cartão de Crédito em 3x). A nota só atinge o estado de liquidação plena quando o somatório dos pagamentos cobre 100% do valor líquido.</li>\n");
        html.append("  <li><strong>DRE Dinâmica:</strong> A Demonstração do Resultado do Exercício calcula em tempo real o Faturamento Bruto, Descontos Concedidos, Faturamento Líquido, CMV Total, Lucro Bruto Contábil, Taxas de Operadoras e Lucro Líquido Real.</li>\n");
        html.append("</ul>\n");

        // CAPÍTULO 5: MATRIZ DE 85 ROTAS
        html.append("<h1>5. Matriz Completa das 85 Rotas Homologadas</h1>\n");
        html.append("<p>O <code>RoteadorController</code> centraliza a totalidade dos 85 endpoints do sistema divididos em 9 domínios funcionais:</p>\n");
        html.append("<table>\n");
        html.append("  <tr><th>Domínio</th><th>Qtd</th><th>Rotas Principais</th><th>Descrição Operacional</th></tr>\n");
        html.append("  <tr><td><strong>SISTEMA</strong></td><td>3</td><td><span class=\"tag-endpoint\">/sistema/inicializar</span>, <span class=\"tag-endpoint\">/sistema/reset-restrito</span>, <span class=\"tag-endpoint\">/ia/busca-padrao</span></td><td>Carga de dados padrão, reset de segurança em testes e busca polimórfica para IA.</td></tr>\n");
        html.append("  <tr><td><strong>PRODUTO</strong></td><td>18</td><td><span class=\"tag-endpoint\">/produtos/fisico/cadastrar</span>, <span class=\"tag-endpoint\">/produtos/kit/componente</span>, <span class=\"tag-endpoint\">/produtos/catalogo/atualizar-preco</span></td><td>Cadastro de produtos físicos, digitais e kits, controle de dimensões, catálogo e imagens.</td></tr>\n");
        html.append("  <tr><td><strong>PESSOA</strong></td><td>16</td><td><span class=\"tag-endpoint\">/pessoas/clientes/pf/cadastrar</span>, <span class=\"tag-endpoint\">/pessoas/fornecedores/pj/cadastrar</span>, <span class=\"tag-endpoint\">/pessoas/historico-completo</span></td><td>Clientes PF/PJ, fornecedores, limites de crédito, contatos auxiliares e histórico 360º.</td></tr>\n");
        html.append("  <tr><td><strong>PAGAMENTO</strong></td><td>9</td><td><span class=\"tag-endpoint\">/pagamentos/formas/salvar</span>, <span class=\"tag-endpoint\">/pagamentos/quitar</span>, <span class=\"tag-endpoint\">/pagamentos/parcela/quitar</span></td><td>Configuração de formas de pagamento, taxas MDR, juros, quitação integral e por parcela.</td></tr>\n");
        html.append("  <tr><td><strong>ESTOQUE</strong></td><td>8</td><td><span class=\"tag-endpoint\">/estoque/saldo-consolidado</span>, <span class=\"tag-endpoint\">/estoque/lotes-fifo</span>, <span class=\"tag-endpoint\">/estoque/perda/baixar</span></td><td>Saldos consolidados, fila FIFO de lotes, produtos próximos ao vencimento e perdas.</td></tr>\n");
        html.append("  <tr><td><strong>NOTA</strong></td><td>13</td><td><span class=\"tag-endpoint\">/notas/venda/emitir-fifo</span>, <span class=\"tag-endpoint\">/notas/venda/emitir-balcao-padrao</span>, <span class=\"tag-endpoint\">/notas/devolucao/registrar</span></td><td>Emissão de notas de compra, venda FIFO/lote, devoluções pós-venda e abatimento.</td></tr>\n");
        html.append("  <tr><td><strong>ESTATISTICA</strong></td><td>8</td><td><span class=\"tag-endpoint\">/estatisticas/curva-abc</span>, <span class=\"tag-endpoint\">/estatisticas/dashboard-geral</span>, <span class=\"tag-endpoint\">/estatisticas/fatores-produto</span></td><td>Curva ABC de faturamento, margens, produtos mais rentáveis e correlações climáticas.</td></tr>\n");
        html.append("  <tr><td><strong>FATOR EXTERNO</strong></td><td>6</td><td><span class=\"tag-endpoint\">/fatores-externos/clima/atual</span>, <span class=\"tag-endpoint\">/fatores-externos/nivel-rio/atual</span>, <span class=\"tag-endpoint\">/fatores-externos/eventos</span></td><td>Sensores climáticos Open-Meteo, telemetria de nível do rio e eventos sazonais.</td></tr>\n");
        html.append("  <tr><td><strong>CONTABILIDADE</strong></td><td>4</td><td><span class=\"tag-endpoint\">/contabilidade/dre</span>, <span class=\"tag-endpoint\">/contabilidade/fluxo-caixa</span>, <span class=\"tag-endpoint\">/contabilidade/relatorio-lucro</span></td><td>DRE estruturada, demonstrativo de fluxo de caixa e relatórios fiscais BACEN.</td></tr>\n");
        html.append("</table>\n");

        // CAPÍTULO 6: INTEGRAÇÃO COM IA E CHATBOT
        html.append("<h1>6. Integração com IA, LLM e ChatBotTerminal</h1>\n");
        html.append("<p>A camada de IA do Sazonalisei integra modelos de linguagem de ponta (Google Gemini 2.5 Flash / Pro e provedores compatíveis com OpenAI) com salvaguardas rigorosas contra alucinações e comandos prematuros:</p>\n");
        html.append("<ul>\n");
        html.append("  <li><strong>Bandeja de Operações em Lote (Staging Area):</strong> Qualquer comando interpretado pela IA que gere alteração no banco de dados (ex: cadastrar 5 produtos, emitir nota de compra, reajustar preço) não é gravado diretamente. Ele é inserido em estado <em>PENDENTE</em> na <code>BandejaOperacoesContexto</code>.</li>\n");
        html.append("  <li><strong>Pré-Validação Atômica em 3 Fases:</strong> Antes de permitir a gravação, o motor executa: (1) Validação de sintaxe e tipos; (2) Auditoria semântica contra o banco de dados; (3) Propagação de dependências em cascata (ex: se a criação de uma unidade falha, os produtos que dependem dela são suspensos com explicação transparente).</li>\n");
        html.append("  <li><strong>Function Calling Rigoroso (Gemini Tool Registry):</strong> O <code>RoteadorController</code> exporta dinamicamente a declaração formal de ferramentas (OpenAPI/Gemini JSON Schema) correspondente às 85 rotas, permitindo que o LLM chame as ferramentas com tipagem rígida.</li>\n");
        html.append("</ul>\n");

        // CAPÍTULO 7: INTERFACE GRÁFICA JAVAFX
        html.append("<h1>7. Nova Interface Gráfica Corporativa JavaFX</h1>\n");
        html.append("<p>A interface gráfica em JavaFX (pacote <code>br.com.senac.view.javafx</code>) foi construída para atender aos padrões mais exigentes de usabilidade corporativa e alta densidade de informação sem poluição visual:</p>\n");
        html.append("<ul>\n");
        html.append("  <li><strong>Arquitetura Lazy Loading:</strong> Nenhuma tela executa consultas bloqueantes no construtor. Cada aba implementa o método <code>carregarSeNecessario()</code>, sendo carregada e renderizada apenas quando o operador clica no respectivo menu.</li>\n");
        html.append("  <li><strong>Ponte Não Bloqueante (Virtual Threads):</strong> A classe <code>PonteRoteadorIA</code> despacha todas as chamadas de API e consultas em Virtual Threads (Java 21 Project Loom), retornando à JavaFX Application Thread exclusivamente via <code>Platform.runLater(...)</code>, garantindo taxa contínua de 60 FPS na UI.</li>\n");
        html.append("  <li><strong>Sistema de Temas Climáticos e Modo Escuro:</strong> O <code>GerenciadorTemaClimatico</code> adapta os tons do aplicativo (azul chuva, dourado seca, verde cheia normal) ou comuta para o Modo Escuro profundo (Dark Mode), garantindo conforto visual durante jornadas de operação intensa.</li>\n");
        html.append("  <li><strong>Copiloto IA Flutuante:</strong> O assistente de inteligência artificial é disponibilizado como uma bolha flutuante no canto inferior direito. O operador pode interagir com o assistente a qualquer momento sem perder o contexto da tela em que está trabalhando.</li>\n");
        html.append("</ul>\n");

        // CAPÍTULO 8: OS 9 MÓDULOS E SCREENSHOTS REAIS
        html.append("<h1>8. Detalhamento e Galeria Ilustrada dos 9 Módulos JavaFX</h1>\n");
        html.append("<p>A seguir são apresentadas as capturas reais de alta resolução de cada um dos 9 módulos da interface gráfica JavaFX do Sazonalisei ERP, acompanhadas de seus fluxos funcionais e operacionais detalhados.</p>\n");

        // MÓDULO 1
        html.append("<h2>8.1. Módulo 1 — Início & Inteligência Climática</h2>\n");
        html.append("<p>O painel principal do sistema consolida a telemetria climática em tempo real de Manaus/AM, o nível do Rio Negro e a projeção de vendas em um gráfico vetorial contínuo com interpolação Bézier suavizada e marcadores contextuais.</p>\n");
        html.append("<div class=\"screenshot-card\">\n");
        if (!b64Imgs.get("01_home_dashboard_clima.png").isEmpty()) {
            html.append("  <img class=\"screenshot-img\" src=\"data:image/png;base64,").append(b64Imgs.get("01_home_dashboard_clima.png")).append("\" alt=\"Módulo Início & Inteligência Climática\" />\n");
        }
        html.append("  <div class=\"screenshot-legenda\"><strong>Figura 1:</strong> Módulo 1 — Dashboard Home com Cards de Temperatura, Cota do Rio Negro, Alertas de Impacto e Gráfico Sazonal Fluido.</div>\n");
        html.append("</div>\n");
        html.append("<ul>\n");
        html.append("  <li><strong>Cards de Telemetria:</strong> Exibem temperatura atual, umidade, índice UV e a cota oficial do Rio Negro com indicador de tendência (Subindo, Descendo ou Estável).</li>\n");
        html.append("  <li><strong>Alertas Climáticos Expansíveis:</strong> Notificações sobre risco de estiagem, foco de chuva ou bloqueio hidroviário com botão de envio direto ao Assistente IA para análise contextual.</li>\n");
        html.append("  <li><strong>Gráfico Sazonal Fluido:</strong> Curva de vendas interpolada que previne sobreposição de rótulos através de algoritmo de dispersão vertical e compressão visual inteligente.</li>\n");
        html.append("</ul>\n");

        // MÓDULO 2
        html.append("<h2>8.2. Módulo 2 — PDV Balcão & Múltiplos Pagamentos</h2>\n");
        html.append("<p>O terminal de ponto de venda de balcão foi reestruturado para proporcionar máxima limpeza e velocidade operacional, permitindo operar 100% via teclado (atalhos F1 a F10) ou comandos por voz integrados.</p>\n");
        html.append("<div class=\"screenshot-card\">\n");
        if (!b64Imgs.get("02_pdv_balcao_multi_pagamentos.png").isEmpty()) {
            html.append("  <img class=\"screenshot-img\" src=\"data:image/png;base64,").append(b64Imgs.get("02_pdv_balcao_multi_pagamentos.png")).append("\" alt=\"Módulo PDV Balcão\" />\n");
        }
        html.append("  <div class=\"screenshot-legenda\"><strong>Figura 2:</strong> Módulo 2 — PDV Balcão: Resumo da Nota, Busca Instantânea por Sigla e Janela de Multi-Pagamentos À Vista/Parcelado.</div>\n");
        html.append("</div>\n");
        html.append("<div class=\"box-sucesso\">\n");
        html.append("  <strong>Inovação no Registro de Pagamentos (Multi-Pagamento Flexível):</strong><br>\n");
        html.append("  • <strong>Cliente Padrão (Consumidor Final):</strong> Permite dividir o total em múltiplas formas À Vista (ex: R$ 30,00 no PIX + R$ 20,00 em Dinheiro com troco).<br>\n");
        html.append("  • <strong>Cliente Registrado:</strong> Permite combinar livremente pagamentos À Vista, A Prazo e Parcelado no mesmo cupom fiscal (ex: R$ 100,00 de entrada no PIX + R$ 300,00 parcelado em 3x no Cartão de Crédito).<br>\n");
        html.append("  • <strong>Botões de Divisão Rápida:</strong> Ações <code>➗ 50%/50%</code> e <code>➗ 3x Formas</code> rateiam automaticamente o valor da nota entre as modalidades desejadas.<br>\n");
        html.append("  • <strong>Edição de Linha Direta:</strong> Cada pagamento adicionado pode ter seu valor editado diretamente no campo de texto da lista, recalculando o saldo restante instantaneamente.<br>\n");
        html.append("  • <strong>Emissão em Papel A4:</strong> Comprovante fiscal gerado na largura exata de página A4, evitando quebras ou cortes de margem na impressão.<br>\n");
        html.append("</div>\n");

        // MÓDULO 3
        html.append("<h2>8.3. Módulo 3 — Estoque, Catálogo & Lotes 360º</h2>\n");
        html.append("<p>Módulo responsável pela gestão unificada de produtos físicos, digitais e kits, controle de unidades de medida, conferência de lotes e registro de perdas.</p>\n");
        html.append("<div class=\"screenshot-card\">\n");
        if (!b64Imgs.get("03_catalogo_estoque_lotes.png").isEmpty()) {
            html.append("  <img class=\"screenshot-img\" src=\"data:image/png;base64,").append(b64Imgs.get("03_catalogo_estoque_lotes.png")).append("\" alt=\"Módulo Catálogo & Estoque\" />\n");
        }
        html.append("  <div class=\"screenshot-legenda\"><strong>Figura 3:</strong> Módulo 3 — Catálogo de Produtos, Tabela de Lotes com Validade, Carrossel de Imagens e Histórico 360º.</div>\n");
        html.append("</div>\n");
        html.append("<ul>\n");
        html.append("  <li><strong>Carrossel de Imagens Sob Demanda:</strong> Permite visualizar e cadastrar fotos de produtos físicos e digitais com carregamento assíncrono para prevenir travamentos de memória.</li>\n");
        html.append("  <li><strong>Visão Lote 360º:</strong> Ao clicar em qualquer lote, abre um card com todas as informações da nota de entrada de origem, fornecedor, quantidade original, saldo restante e todas as notas de venda que consumiram frações daquele lote.</li>\n");
        html.append("  <li><strong>Montagem Dinâmica de Kits:</strong> Construtor interativo de conjuntos que permite adicionar múltiplos componentes e quantidades em lote, com validação de recursão e conferência de custo unitário.</li>\n");
        html.append("</ul>\n");

        // MÓDULO 4
        html.append("<h2>8.4. Módulo 4 — Financeiro, DRE & Saúde Contábil</h2>\n");
        html.append("<p>Central contábil e financeira que apresenta os indicadores de liquidez, contas a pagar e receber com controle de parcelas, rastreamento completo de notas e emissão de notas de entrada.</p>\n");
        html.append("<div class=\"screenshot-card\">\n");
        if (!b64Imgs.get("04_financeiro_saude_contabil.png").isEmpty()) {
            html.append("  <img class=\"screenshot-img\" src=\"data:image/png;base64,").append(b64Imgs.get("04_financeiro_saude_contabil.png")).append("\" alt=\"Módulo Financeiro & Contabilidade\" />\n");
        }
        html.append("  <div class=\"screenshot-legenda\"><strong>Figura 4:</strong> Módulo 4 — Financeiro: Dashboard Dinâmico de Saúde Financeira, DRE BACEN, Contas a Pagar/Receber e Rastreamento.</div>\n");
        html.append("</div>\n");
        html.append("<ul>\n");
        html.append("  <li><strong>Dashboard de Saúde Financeira:</strong> Indicadores visuais dinâmicos de Margem Operacional, Saldo em Aberto, Ponto de Equilíbrio e Ticket Médio.</li>\n");
        html.append("  <li><strong>Quitação Parcial e Parcelas:</strong> Ao quitar contas a pagar ou receber, o operador pode registrar pagamentos parciais, múltiplas datas de quitação e rateio entre diferentes bancos.</li>\n");
        html.append("  <li><strong>Rastreamento Completo de Notas:</strong> Retorna o ciclo de vida integral da nota (entrada, lotes criados, itens vendidos, pagamentos quitados, taxas retidas e eventuais perdas e devoluções associadas).</li>\n");
        html.append("</ul>\n");

        // MÓDULO 5
        html.append("<h2>8.5. Módulo 5 — Estatística Preditiva & Inteligência</h2>\n");
        html.append("<p>Módulo analítico que correlaciona o comportamento histórico de vendas e perdas com os eventos sazonais e fatores climáticos, gerando recomendações proativas de compra e venda.</p>\n");
        html.append("<div class=\"screenshot-card\">\n");
        if (!b64Imgs.get("05_estatistica_preditiva_causas.png").isEmpty()) {
            html.append("  <img class=\"screenshot-img\" src=\"data:image/png;base64,").append(b64Imgs.get("05_estatistica_preditiva_causas.png")).append("\" alt=\"Módulo Estatística Preditiva\" />\n");
        }
        html.append("  <div class=\"screenshot-legenda\"><strong>Figura 5:</strong> Módulo 5 — Cards de Aviso Preditivo de Compra/Venda, Análise de Causa e Efeito e Modal Estratégico.</div>\n");
        html.append("</div>\n");
        html.append("<ul>\n");
        html.append("  <li><strong>Cards Preditivos com Índice de Vantagem:</strong> Linha de cartões compactos indicando o produto, o índice percentual de oportunidade e o motivo sazonal resumido.</li>\n");
        html.append("  <li><strong>Modal Estratégico com Gráfico Detalhado:</strong> Ao clicar no card, abre uma janela dedicada com gráfico temporal, justificativa econômica e conexão com o planejamento de demanda.</li>\n");
        html.append("  <li><strong>Diagrama de Ciclo de Vida em Tela Cheia:</strong> Permite expandir o mapa mental procedural com navegação por teclado e instruções claras de saída via tecla <code>ESC</code>.</li>\n");
        html.append("</ul>\n");

        // MÓDULO 6
        html.append("<h2>8.6. Módulo 6 — Mapa GPS, Relevo & Clima 7 Dias</h2>\n");
        html.append("<p>Visualizador georreferenciado e hidrológico com projeção em relevo do estado do Amazonas e da calha do Rio Negro, previsões estendidas de 7 dias e mapeamento de eventos.</p>\n");
        html.append("<div class=\"screenshot-card\">\n");
        if (!b64Imgs.get("06_mapa_gps_clima_relevo.png").isEmpty()) {
            html.append("  <img class=\"screenshot-img\" src=\"data:image/png;base64,").append(b64Imgs.get("06_mapa_gps_clima_relevo.png")).append("\" alt=\"Módulo Mapa GPS & Relevo\" />\n");
        }
        html.append("  <div class=\"screenshot-legenda\"><strong>Figura 6:</strong> Módulo 6 — Mapa GPS Vetorial Interativo com Relevo Amazônico, Zoom (+/-) e Ficha Completa de Localidades.</div>\n");
        html.append("</div>\n");
        html.append("<ul>\n");
        html.append("  <li><strong>Controles Interativos de Zoom e Pan:</strong> Botões <code>[+]</code> e <code>[-]</code> com reset de visualização e arraste fluido para examinar rotas fluviais e pólos comerciais.</li>\n");
        html.append("  <li><strong>Ficha Completa Expansível:</strong> Exibe detalhes climáticos, regime fluvial atual, volume pluviométrico esperado e sugestões de abastecimento geradas pela IA.</li>\n");
        html.append("</ul>\n");

        // MÓDULO 7
        html.append("<h2>8.7. Módulo 7 — Planejamento de Demanda Kanban</h2>\n");
        html.append("<p>Quadro Kanban especializado em correlação de demanda entre clientes, fornecedores e eventos sazonais externos, organizando a esteira operacional de vendas e abastecimento.</p>\n");
        html.append("<div class=\"screenshot-card\">\n");
        if (!b64Imgs.get("07_planejamento_demanda_kanban.png").isEmpty()) {
            html.append("  <img class=\"screenshot-img\" src=\"data:image/png;base64,").append(b64Imgs.get("07_planejamento_demanda_kanban.png")).append("\" alt=\"Módulo Planejamento de Demanda Kanban\" />\n");
        }
        html.append("  <div class=\"screenshot-legenda\"><strong>Figura 7:</strong> Módulo 7 — Planejamento Kanban: Esteira Sincronizada (Mapeamento -> Estratégia -> Execução -> Conclusão).</div>\n");
        html.append("</div>\n");
        html.append("<ul>\n");
        html.append("  <li><strong>Cards Anti-Estouro e Arraste por Setas:</strong> Layout rígido que não quebra mesmo com a barra lateral expandida, com botões direcionais <code>[→ Avançar]</code> e <code>[← Retornar]</code>.</li>\n");
        html.append("  <li><strong>Geração de Estratégias pela IA:</strong> Instrução especializada que analisa o clima e sugere reforçar produtos em estoque (ex: capas de chuva, água mineral) ou recomenda cadastrar novos produtos sob demanda.</li>\n");
        html.append("</ul>\n");

        // MÓDULO 8
        html.append("<h2>8.8. Módulo 8 — Central Operacional & Chatbot IA</h2>\n");
        html.append("<p>Ambiente unificado para controle operacional, execução de instruções em linguagem natural, gestão da Bandeja de Contexto e auditoria de lotes antes do commit.</p>\n");
        html.append("<div class=\"screenshot-card\">\n");
        if (!b64Imgs.get("08_controle_operacional_ia.png").isEmpty()) {
            html.append("  <img class=\"screenshot-img\" src=\"data:image/png;base64,").append(b64Imgs.get("08_controle_operacional_ia.png")).append("\" alt=\"Módulo Central Operacional IA\" />\n");
        }
        html.append("  <div class=\"screenshot-legenda\"><strong>Figura 8:</strong> Módulo 8 — Central Operacional IA: Visualizador da Bandeja de Contexto, Terminal de Conversação e Auditoria.</div>\n");
        html.append("</div>\n");
        html.append("<ul>\n");
        html.append("  <li><strong>Execução de Ferramentas em Tempo Real:</strong> Permite consultar estoque, verificar clientes, simular compras e emitir notas diretamente pelo chat com confirmação humana obrigatória.</li>\n");
        html.append("  <li><strong>Relatório Explicativo de Contexto:</strong> Quando a IA explica um motivo de perda ou flutuação, cita compulsoriamente os dados de origem (nota, lote, data e evento climático associado).</li>\n");
        html.append("</ul>\n");

        // MÓDULO 9
        html.append("<h2>8.9. Módulo 9 — Configurações & Mapeador Dinâmico de CSV</h2>\n");
        html.append("<p>Painel de controle corporativo para configuração de chaves de API, alternância de tema, personalização de cores de fundo e ferramenta avançada de importação e mapeamento flexível de arquivos CSV.</p>\n");
        html.append("<div class=\"screenshot-card\">\n");
        if (!b64Imgs.get("09_configuracoes_mapeador_csv.png").isEmpty()) {
            html.append("  <img class=\"screenshot-img\" src=\"data:image/png;base64,").append(b64Imgs.get("09_configuracoes_mapeador_csv.png")).append("\" alt=\"Módulo Configurações & Mapeador CSV\" />\n");
        }
        html.append("  <div class=\"screenshot-legenda\"><strong>Figura 9:</strong> Módulo 9 — Configurações: Credenciais de IA, Score de Fornecedores e Mapeador Dinâmico de Colunas CSV.</div>\n");
        html.append("</div>\n");
        html.append("<ul>\n");
        html.append("  <li><strong>Mapeador Inteligente de Colunas CSV:</strong> Permite carregar qualquer planilha CSV e associar visualmente cada coluna ao campo correspondente do sistema (Nome, Unidade, Preço, Quantidade, Fornecedor) com suporte a expressões combinadas (ex: <code>col1 + ' - ' + col2</code>).</li>\n");
        html.append("  <li><strong>Score de Fornecedores por Produto:</strong> Algoritmo de classificação que compara fornecedores que entregam o mesmo item, pontuando por preço histórico, pontualidade de entrega e índice de perdas registradas.</li>\n");
        html.append("</ul>\n");

        // TEMA ESCURO
        html.append("<h2>8.10. Modo Escuro (Dark Mode) Integrado</h2>\n");
        html.append("<p>O sistema conta com Modo Escuro completo em todas as 9 abas, otimizado para ambientes com baixa luminosidade e estações de trabalho de frente de caixa noturnas:</p>\n");
        html.append("<div class=\"screenshot-card\">\n");
        if (!b64Imgs.get("10_tema_escuro_interface.png").isEmpty()) {
            html.append("  <img class=\"screenshot-img\" src=\"data:image/png;base64,").append(b64Imgs.get("10_tema_escuro_interface.png")).append("\" alt=\"Interface em Modo Escuro\" />\n");
        }
        html.append("  <div class=\"screenshot-legenda\"><strong>Figura 10:</strong> Sazonalisei ERP — Tema Escuro Corporativo Ativo (Paleta Slate/Navy de Alto Contraste).</div>\n");
        html.append("</div>\n");

        // CAPÍTULO 9: PROCEDIMENTOS DE HOMOLOGAÇÃO
        html.append("<h1>9. Procedimentos de Testes, Compilação e Homologação</h1>\n");
        html.append("<p>A integridade e estabilidade do Sazonalisei são verificadas através de suítes de testes automatizados incrementais que cobrem 100% dos fluxos críticos do sistema:</p>\n");
        html.append("<table>\n");
        html.append("  <tr><th>Suíte de Testes</th><th>Quantidade de Asserções</th><th>Escopo da Homologação</th><th>Status</th></tr>\n");
        html.append("  <tr><td><code>TesteEndpointsControllers</code></td><td>85 de 85 rotas</td><td>Homologação ponta a ponta de todas as 85 rotas do <code>RoteadorController</code>, incluindo multi-pagamentos.</td><td><span style=\"color:#059669; font-weight:bold;\">✔ 100% APROVADO</span></td></tr>\n");
        html.append("  <tr><td><code>TesteBandejaContexto</code></td><td>64 de 64 verificações</td><td>Enfileiramento em lote, staging de instruções, dependências entre entidades e pré-validação.</td><td><span style=\"color:#059669; font-weight:bold;\">✔ 100% APROVADO</span></td></tr>\n");
        html.append("  <tr><td><code>TesteBandejaPassoAPasso</code></td><td>71 de 71 verificações</td><td>Execução procedural com <code>proxima</code>, <code>pular</code>, exclusão e conferência atômica no banco.</td><td><span style=\"color:#059669; font-weight:bold;\">✔ 100% APROVADO</span></td></tr>\n");
        html.append("  <tr><td><code>TesteParserEPreProcessador</code></td><td>70 de 70 verificações</td><td>Reconhecimento de atalhos e siglas (<code>>oba</code>, <code>i.s.u</code>), sanitização e isolamento sintático.</td><td><span style=\"color:#059669; font-weight:bold;\">✔ 100% APROVADO</span></td></tr>\n");
        html.append("  <tr><td><code>TesteFluxoPromptLongoEValidacao</code></td><td>36 de 36 verificações</td><td>Decomposição de prompts complexos de 15 operações em 5 etapas ordenadas com auditoria real.</td><td><span style=\"color:#059669; font-weight:bold;\">✔ 100% APROVADO</span></td></tr>\n");
        html.append("  <tr><td><code>TesteImportadorCsvEReset</code></td><td>17 de 17 verificações</td><td>Leitura de CSV com múltiplos delimitadores, mapeamento dinâmico e reset restrito de segurança.</td><td><span style=\"color:#059669; font-weight:bold;\">✔ 100% APROVADO</span></td></tr>\n");
        html.append("</table>\n");
        html.append("<div class=\"box-resumo\">\n");
        html.append("  <strong>Como executar os testes e iniciar o sistema:</strong><br>\n");
        html.append("  • Bateria completa de testes automatizados: <code>.\\executar_testes.ps1</code><br>\n");
        html.append("  • Inicialização da interface gráfica JavaFX: <code>.\\iniciar_interface_javafx.ps1</code><br>\n");
        html.append("  • Inicialização do ChatBotTerminal IA: <code>.\\iniciar_terminal.ps1</code><br>\n");
        html.append("</div>\n");

        html.append("</body>\n</html>\n");

        File htmlOut = new File(tempDir, "documentacao_completa.html");
        Files.writeString(htmlOut.toPath(), html.toString(), StandardCharsets.UTF_8);
        System.out.println("  ✔ HTML da documentação gerado com sucesso: " + htmlOut.getAbsolutePath() + " (" + htmlOut.length() + " bytes)");
    }
}
