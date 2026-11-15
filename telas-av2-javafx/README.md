# Senac PDV — Telas AV2 em JavaFX

**Esta é a versão JavaFX da entrega. Substitui a versão Swing anterior.**

Interface gráfica para a apresentação de Sistema de Vendas e Carrinho de Compras, disciplina de Programação Orientada a Objetos, professor Carlos Bruno Oliveira Lopes. O módulo é adicional ao repositório da equipe e usa **Java 21 + JavaFX 21.0.6**, a versão de JavaFX encontrada no `pom.xml` do projeto principal.

## Abrir no Windows x64

1. Extraia o ZIP inteiro. Preserve `lib`, o JAR, os scripts e demais pastas.
2. Tenha **JDK 21 ou superior** instalado. O script procura em `JAVA_HOME`, nas instalações de JDK em `Program Files/Java` e no `PATH`, e descarta versões abaixo de 21.
3. Abra **iniciar.bat**. Não execute o JAR com duplo clique: JavaFX precisa do caminho de módulos.

O ZIP inclui as três bibliotecas oficiais OpenJFX para Windows x64 e o JAR compilado. A execução desse pacote não requer banco, Maven ou internet. Os scripts geram somente cache local na pasta `build` do módulo. **O JDK não acompanha o pacote.**

Se você clonou os fontes do GitHub, os JARs ignorados pelo Git serão baixados automaticamente pelo script `preparar-javafx.ps1` e o executável será compilado na primeira abertura. Esse primeiro preparo exige internet e JDK.

Com o Java 21+ já configurado no PATH, execução manual:

```powershell
java --module-path lib --add-modules javafx.controls -jar senac-pdv-javafx.jar
```

## Compilar ou testar

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\compilar.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\compilar.ps1 -Testar
```

O primeiro comando gera o JAR. A opção `-Testar` executa **25 verificações da demonstração** e **30 verificações de interface JavaFX**, abrindo e fechando a interface automaticamente e recriando 15 capturas em `capturas/`. Use apenas dados fictícios: os testes executam operações de demonstração.

Foi compilado com JDK 26 usando `--release 21`. Os testes foram executados em Windows x64, com renderização JavaFX por software. O JDK 26 pode exibir avisos do próprio JavaFX 21 sobre APIs internas; a compilação e os testes concluíram normalmente. Recomendação de ambiente da equipe: JDK 21.

Maven é uma alternativa para IDEs ou outras plataformas, com JDK 21+ e internet:

```powershell
mvn clean compile javafx:run
```

O `pom.xml` é exclusivo deste módulo. O Maven não estava disponível no ambiente de entrega; a compilação validada usou os scripts com `javac`, `jar` e os mesmos módulos JavaFX. A opção Maven não foi executada. Para Linux/macOS, use as dependências da plataforma resolvidas pelo Maven; os binários de `lib/` são Windows x64.

## Telas e opções

| Tela | O que o usuário pode fazer |
|---|---|
| Frente de caixa | Buscar por código/nome, filtrar físico/digital, selecionar na tabela ou digitar código, informar quantidade e adicionar |
| Carrinho | Ver itens, subtotal unitário e acumulado; alterar quantidade, remover e cancelar com confirmação |
| Produtos e estoque | Buscar, cadastrar e editar; ver disponibilidade e ajustar estoque; campos específicos por tipo |
| Clientes | Buscar, cadastrar e editar identificação, nome, e-mail, telefone e endereço |
| Pagamento | Selecionar cliente, endereço e método; comparar valores; calcular troco; voltar ou confirmar recebimento |
| Comprovante | Ver itens, taxas, desconto, frete, total, pagamento e troco; salvar TXT e imprimir pelo JavaFX PrinterJob |
| Histórico | Consultar vendas da sessão e reabrir comprovantes |
| Guia da apresentação | Ler o roteiro para a demonstração ao professor Bruno |

Toda a interface utiliza **JavaFX**: `Application`, `Scene`, `BorderPane`, `TableView`, `Dialog`, `GridPane`, `ComboBox`, `Spinner` e CSS. **Não há importação Swing ou AWT.** O layout é montado em Java; não há FXML ou dependência do SceneBuilder.

Janela inicial: 1380 × 880; mínimo: 1120 × 760, incluindo bordas da janela. As tabelas e o guia têm rolagem. As capturas mostram cenas e conteúdo dos diálogos reais, sem as bordas do sistema operacional.

## Escopo: telas com demonstração em memória

Não há integração com banco ou controllers reais, autenticação, cobrança ou emissão fiscal. Cadastros e histórico reiniciam ao fechar. Os links `example.com` e os dados de clientes são fictícios. O comprovante é identificado como demonstrativo, sem valor fiscal.

A demonstração bloqueia estoque zero, quantidade inválida ou superior ao disponível, inclusive em adições repetidas. Impede estoque negativo e edição para estoque inferior ao carrinho. Apenas a confirmação bem-sucedida reduz o estoque e limpa o carrinho. Voltar, cancelar ou falhar no pagamento preserva o estoque.

### Regras de cálculo adotadas

- Frete único de **R$ 15 por entrega**, se houver qualquer produto físico. Não multiplica por unidade ou produto.
- Só digitais: frete zero e endereço desabilitado.
- PIX: desconto de 5% sobre produtos, sem descontar o frete.
- Dinheiro: sem taxa/desconto; valor recebido deve cobrir o total, com cálculo de troco.
- Crédito: 3% sobre produtos + frete.
- Valores em `BigDecimal`, centavos arredondados com `HALF_UP`.

Exemplo: uma unidade de P001 + uma de D001 soma R$ 179,80 em produtos e R$ 15,00 em frete. PIX: **R$ 185,81**; dinheiro: **R$ 194,80**; crédito: **R$ 200,64**. Recebendo R$ 200,00 em dinheiro, o troco é **R$ 5,20**.

O texto do professor traz exemplos com possíveis interpretações diferentes para o frete e a base das taxas. Estas decisões devem ser alinhadas pela equipe com o professor antes da integração final.

## Arquitetura e integração

```text
telas-av2-javafx/
  src/main/java/br/com/senac/view/av2/
    PdvApplication.java        # entrada JavaFX
    PdvView.java               # telas, formulários, navegação e eventos
    PdvGateway.java            # contrato que a interface consome
    DemoGateway.java           # implementação em memória para apresentação
    VerificacaoInterface.java  # aceitação pelos controles JavaFX
  src/main/resources/pdv-av2.css
  testes/VerificarDemo.java
  pom.xml
  compilar.ps1
  iniciar.bat
  lib/                        # módulos JavaFX do ZIP; ignorados no Git
  licencas/                   # textos de licença e avisos do OpenJFX
  capturas/
```

O projeto principal já possui JavaFX, `SistemaGateway`, controllers, entidades e persistência. Esta contribuição fica em uma pasta própria na raiz e **não substitui Main, launcher, pom ou views existentes**. Para execução, entre no diretório deste módulo.

O gerente pode manter a execução independente para esta entrega e integrar depois:

1. Criar um adaptador que implemente `PdvGateway`, delegando aos controllers reais e convertendo DTOs. Não usar `DemoGateway` para dados reais.
2. Na aplicação JavaFX existente, criar `new PdvView(seuGateway)` e aplicar `/pdv-av2.css` à Scene. Copiar as classes de interface e o CSS para as pastas correspondentes do projeto, sem duplicar as classes que eventualmente já tenham sido copiadas antes.
3. Manter confirmação de venda e baixa de estoque transacionais na camada de domínio. A view não substitui essa proteção.
4. Executar operações de banco/rede em `Task`/`Service` e atualizar controles na JavaFX Application Thread. O contrato desta demonstração é síncrono porque trabalha apenas em memória; o adaptador final deve tratar carregamento, erros e concorrência.

O módulo demonstra desacoplamento via interface, mas **não comprova sozinho os quatro pilares pedidos no domínio final**. A herança `ProdutoFisico/ProdutoDigital` e a interface/estratégias `MetodoPagamento` continuam sob responsabilidade da implementação da equipe. Os records do gateway são dados de apresentação, não entidades JPA.

## Roteiro de apresentação (5–7 minutos)

1. Explique a dor: erro de cálculo, lentidão na venda e falta de controle de estoque.
2. Mostre Produtos, os campos específicos de físico/digital e o cadastro de Clientes.
3. Adicione P001 e D001. Altere a quantidade, remova e adicione novamente.
4. Tente P003 (esgotado) e depois tente 13 unidades de P001 para provocar a validação.
5. No checkout, compare PIX/dinheiro/crédito. Em dinheiro, digite 200 para mostrar o troco. Volte sem confirmar.
6. Confirme por PIX e marque a confirmação de recebimento. Mostre o comprovante, histórico e estoque reduzido.
7. Faça compra só de D001: sem frete nem endereço obrigatório.
8. Explique sua contribuição: interface JavaFX e contrato de integração. Indique o que ainda será conectado pela equipe.

## Subir no GitHub — publicação por você

1. Pelo GitHub Desktop, clone ou atualize `https://github.com/IgorUchoasCODE/PDV_java_Senac_Poo`.
2. Crie uma branch `codex/telas-av2-javafx` a partir da `main` atualizada.
3. Copie **somente `telas-av2-javafx`** para a raiz do clone. Não envie o módulo Swing antigo. Se já enviou, peça ao gerente para substituir a contribuição antiga na revisão.
4. Confira Changes: apenas a contribuição JavaFX. `build`, `target`, classes compiladas, JAR do projeto e JARs de `lib` são ignorados. O código, CSS, scripts, imagens e licenças são versionados.
5. Commit: `Adiciona telas do PDV em JavaFX para AV2`.
6. Publish branch / Push origin; crie Pull Request, explique que é um módulo demonstrativo ainda sem ligação ao backend e peça revisão do gerente antes do merge.
7. Se não tiver permissão no repositório, use um fork na sua conta para abrir o PR.

**Não houve commit, push ou publicação nesta entrega.** O JAR e os módulos JavaFX acompanham o ZIP para apresentação; colegas que clonarem o código recompilam usando os scripts.

## Evidências e limites da validação

- Compilação das classes JavaFX com alvo Java 21 e empacotamento do JAR aprovados.
- 25 verificações do `DemoGateway` aprovadas: totais, estoque, pagamento, cancelamento e consistência.
- 30 verificações pelos controles JavaFX aprovadas: pesquisa/filtro, inclusão/alteração/remoção, formulários, e-mail inválido, checkout, troco, taxas, confirmação, compra digital, cancelamento e histórico.
- 15 capturas geradas pelo próprio JavaFX e revisadas visualmente.
- Impressão física, seletor de arquivo, execução em Java 21 instalado separadamente, Maven e backend da equipe não foram exercitados. O alvo de bytecode foi validado por `--release 21`; a execução local usou JDK 26.

## Dependências e referências

OpenJFX 21.0.6, módulos base, graphics e controls, binários oficiais do Maven Central. Textos de licença e avisos em `licencas/`, com fontes correspondentes em [OpenJFX 21.0.6+3](https://github.com/openjdk/jfx21u/tree/21.0.6%2B3). Orientações de configuração: [documentação oficial do OpenJFX](https://openjfx.io/openjfx-docs/).
