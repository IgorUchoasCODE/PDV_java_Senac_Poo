eu quero criar encurtadores para daculiat instruções para IA 

para melhora mais a interação ppara usuario mais avancado 
exemplo:  
Oque eu acho e deveria fazer a @src/main/java/br/com/senac/integracao/ia/BandejaOperacoesContexto.java

quero criar um produto A, UM
quero criar produto B, KG
nota de cpompra fornecedor X, produto A, quantidade 120, valor 10. 
adicionar produto a nota produto B, 16, valor 40
adiciona data ontem 

instruções 
verifica se A existe, se sim pergunta se quer modificar, se não retorna as informações de estoque desse produto 
caso queira modidica, confirma novamente se existe e quais atribuito quer modificar e se os parametros difarente. 
e se quiser deletar confirma se existe, e se tem quantidade de estoque, só pode deletar produto com a quantidade igual a zero. naso queira, faz o abatimento constando nota de perca, ative a opção que essa perca não foi for conta do fornecedor. Insetando o fornecedor da responsabilidade de ressarsir 
quando quero criar uma nota, quero que possibilite instruções dentro de sub instruções. 
ao criar nota quero pode adicionar os intem ou remover ou auterar sequencialmenente, todas as notas que envolve itens 

as notas de pagamento pode referenciar varias notas, pode quitar ou dar baixo. tanto de maneira integral ou parcial e com todas as validações de existencias mas lembrando, compra subtrair caixa, venda adiciiona no caixa e lucro, devolução  subtrair e o valor do caixa lucro gerado pelo nota de venda e devolver o produto,  e perda apate no lucro o custo integral ou parcial do produto assim como o valor em caixa. assim como quando ha uma perda de um produto dentro da validade esta ativo para registrar o ressaarcimento assumindo os valores bruto do produto perdito e infromando que aquele produto perdido já foi ressarcido. 

então instrução principla de notas de compra 
origem e formad de pagamento etc 
sub intrução 
adicionar produto e valores assim com ressarcimento do produto perdido daquele fornecedor e por ser produto crusado perdi ovos mas no ressarcimento me trouxe uma 1L açai. ovo unida custo 10, açai custo por L 10, toda veis que adiciona um item gera um sub total, e pode adicionar pagamento como outra sub instrução, 

agora quero gerar encurtadores de instrução para usuario mais avançados 

muitas das vezes fica muito verboso 

quero cadastrar uma nota de compra do fornecedor X, data de ontem 
adiciona produto A, quantidade 12, custo 10
adiciona produto B, quantidade 12, custo 10
adiciona produto C, quantidade 12, custo 10
adiciona produto D, quantidade 12, custo 10
adicior pagamento 
dinheiro valor 1120, ontem 
pix 600, hoje 

poderia ser mais simples 



C= criar 
R= Buscar
u= modificar
D= deleta 

R= deve conter umas propriedades de buscar por sigla em produto, pessoas, tipo 
R= #Pessoa nm> i.s.u = buscar pessoa com nome igor da silva uchoa
R= #Pessoa nm> i s u = buscar pessoa com nome igor da silva uchoa
R= #Pessoa nm> >isu = buscar pessoa com nome igor da silva uchoa

R= #Produto nm> o.b.a = buscar produto Ovos pranco tipo A
R= #Produto nm> o b a = buscar produto Ovos pranco tipo A
R= #Produto nm> >oba = buscar produto Ovos pranco tipo A
R= #Produto nm> >otba = buscar produto Ovos pranco tipo A ou iniciis de cada letra

caso venha lista de produto com sila igual, jogue para o usuario escolher 
integre isso a @src/main/java/br/com/senac/repository/dao/BuscaPalavrasParciaisUtil.java

# = oque 
++ = adicionar 
-- = remover 
$$ = inicar 
; fim de instrução 
, separador atributo 


[] = sub instrução sequêncial 

id> id
nm> nome 
val> valor "valor senerico"
vc> valor custo
vv> valor venda 
q> quantidade
dt> data "pode ser ontem, hoje ou <3 dias atras ou 3> daqui 3 dias a frete vazio hoje "
close<> abortar instrução 
close:: aborta todas as intruções 


exemplos: 
$$
C= #NotaCompra nm> Graja Marinhon, >dt <4, ++ #Produto[++ nm> ovo tipo a, >vl 20, q> 10; ++ nm> caixa de ovos, vc> 200, q> 5;] ++ #Pagamento [ ++ dinheiro, 1000, dt> <3; ++ pix , 800, hoje; ++ pix , 800, ontem];  

quero criar ima nota de compra Do fornecedor Granja marinho que foi entrete a quatro  dias atrás, veio 10 do produto ovos tipo a custando 20, e veio 5 caixas de ovos, custando 200 cada. Fis o pagamento em dinheiro de 1000 a 3 dias, pix de 800 hoje e 800 ontem


criar oque = nota de compra 
nome = Granja MArinho
quando foi = a 4 dias atrás 
adicionar oque = adicionar produto 
iniciar instruçao adicionar produto 
adicionar oque do produto 
nome = ovo tipo a 
valor = 20 "adicionar qualque valor numerico contabil"
quantidade = 10
fim desse intrução
adicionar oque do produto
nome = caixa de ovos
valor de custo = 200 "sitando que é valor de custo"
quantidade = 5
fim desse intrução
adicionar oque = pagamento
iniciar instruçao adicionar pagamento
nome = dinhero
valor = 1000
quando foi = a 3 dias atrás
nome = Pix
valor = 800
quando foi = hoje
nome = Pix
valor = 800
quando foi = ontem
fim desse intrução
fim dessa instrução


to pensando em usar a biblioteca nativa java steam para ser o copilador assim vai executar tudo sequencialmente 
primeiro ira validar as infromações depois da confirmação ira executar as instruções. desta forma evita de apagar um produto que ainda  não cadastrei. 

depois da verificação ele ira retina uma texto para o usuaario ler em linguagem normal, pode usar a IA para execitar ou criar um pré texto mas robisto ja formatado, caso a pessoa queira modifica indique o id da instrução salva em memoria de execução par amapear, depois da modificação ou exlução de toda ou algumas a instrução ai sim irá validar novamento. 

quero que integre isso em toda conversação ao colocar caso coloque no inicio  o prompet com $$ atribui a classe de compilação desses intruções 



---

# Especificação Técnica Completa: DSL de Encurtadores (`$$`) e Preparação de Contexto

## 1. Dicionário Oficial de Tokens, Operadores e Atributos

| Categoria | Token / Operador | Significado | Exemplo |
|---|---|---|---|
| **Gatilho** | `$$` | Inicia o modo compilador de instruções encurtadas (pode conter 1 ou N instruções separadas por `;`) | `$$ C= #Produto ...;` |
| **Operação CRUD** | `C=` | **Create** (Criar / Cadastrar / Emitir) | `C= #ProdutoFisico` |
| **Operação CRUD** | `R=` | **Read** (Buscar / Consultar / Listar — suporta siglas `o.b.a`, `o b a`, `>oba`, `>otba`) | `R= #Produto nm> >otba;` |
| **Operação CRUD** | `U=` | **Update** (Modificar / Atualizar / Quitar / Definir Principal) | `U= #Produto id> 10, vv> 25.00;` |
| **Operação CRUD** | `D=` | **Delete** (Deletar / Excluir / Remover / Baixar Perda para zerar estoque) | `D= #Produto nm> >oba;` |
| **Alvo / Entidade** | `#Classe` | Define a entidade ou sub-recurso alvo da operação | `#NotaCompra`, `#Pessoa`, `#Produto` |
| **Sub-operação** | `++` | Adicionar item, componente, registro, imagem, pagamento ou abatimento na sub-instrução | `++ #Produto[...]` |
| **Sub-operação** | `--` | Remover/abater item, componente, imagem ou abater perda na sub-instrução | `-- #Perda[...]` |
| **Agrupador** | `[ ... ]` | Bloco de sub-instruções sequenciais vinculadas à instrução pai | `++ #Pagamento[ ++ pix, val> 100; ]` |
| **Separadores** | `,` e `;` | `,` separa atributos da mesma instrução; `;` finaliza uma instrução ou sub-instrução | `nm> Ovos, q> 10;` |
| **Abortar** | `close<>` / `close::` | `close<>` aborta a instrução atual (ou `close<OP-2>` por ID da memória); `close::` aborta toda a bandeja | `$$ close<OP-2>;` / `$$ close::` |

### Atributos Padronizados (`chave> valor`)
- `id>` : ID numérico da entidade ou da instrução em memória (`id> 15` ou `id> OP-1`)
- `nm>` : Nome, razão social, sigla ou termo de busca por iniciais (`nm> Granja Marinho`, `nm> >isu`, `nm> i.s.u`)
- `um>` : Sigla da Unidade de Medida (`um> UN`, `um> KG`, `um> L`, `um> CX`)
- `doc>` / `reg>` : Documento ou registro (`doc> 123.456.789-00`, `reg> RG:1234567`)
- `tp>` : Tipo ou papel (`tp> CLIENTE`, `tp> FORNECEDOR`, `tp> AMBOS`, `tp> PF`, `tp> PJ`, `tp> FIFO`, `tp> BALCAO`, `tp> LOTE`)
- `val>` / `vl>` : Valor genérico contábil, limite de crédito ou valor de parcela (`val> 1000.00`)
- `vc>` : Valor de custo unitário (`vc> 10.00`)
- `vv>` : Valor de venda unitário (`vv> 18.50`)
- `q>` : Quantidade ou número de parcelas (`q> 120`, `q> 3x`)
- `dt>` : Data relativa ou absoluta (`dt> hoje`, `dt> ontem`, `dt> <4` [4 dias atrás], `dt> 30>` [daqui a 30 dias], `dt> 2026-12-31`)
- `val_dt>` : Data de validade do lote (`val_dt> 30>` [vence em 30 dias])
- `dim>` : Dimensões físicas `AxLxC` em cm e pesos `Bruto/Liquido` em kg (`dim> 10x20x30, pb> 1.5, pl> 1.2`)
- `resp>` : Responsabilidade do fornecedor em perdas/devoluções (`resp> forn` [fornecedor ressarcirá] ou `resp> isento` [sem culpa do fornecedor])
- `mot>` : Motivo de perda ou devolução (`mot> Quebra no transporte interno`)

---

## 2. Exemplos Textuais (`$$`) para TODAS as Classes de Registro e CRUD

### 2.1. Módulo Produto (`br.com.senac.repository.modal.produto`)

#### A) `Um` (Unidade de Medida)
- **Criar (`C=`):**
  - **Encurtado:** `$$ C= #Um nm> CX, desc> Caixa com 30 duzias, val> 360;`
  - **Texto equivalente:** *"Cadastrar unidade de medida CX (Caixa com 30 dúzias) com fator base 360."*
- **Buscar (`R=`):**
  - **Encurtado:** `$$ R= #Um;` ou `$$ R= #Um nm> KG;`
  - **Texto equivalente:** *"Listar todas as unidades de medida ou buscar a unidade KG."*
- **Modificar (`U=`):**
  - **Encurtado:** `$$ U= #Um nm> CX, desc> Caixa Padrao 30 dz, val> 360;`
  - **Texto equivalente:** *"Atualizar a descrição e base da unidade de medida CX."*
- **Deletar (`D=`):**
  - **Encurtado:** `$$ D= #Um nm> CX;`
  - **Texto equivalente:** *"Excluir a unidade de medida CX."*

#### B) `Produto` e `ProdutoFisico` (Catálogo e Dimensões)
- **Criar (`C=`) com verificação automática de existência:**
  - **Encurtado:** `$$ C= #Produto nm> Ovos Branco Tipo A, um> UN, vv> 1.50, dim> 5x4x4, pb> 0.06, pl> 0.05;`
  - **Encurtado Simples (como no exemplo):** `$$ C= #Produto nm> Produto A, um> UN; C= #Produto nm> Produto B, um> KG;`
  - **Regra do Compilador:** Verifica se `Produto A` já existe. Se existir, retorna o saldo de estoque e pergunta se deseja modificar (`U=`); se não existir, agenda a criação na bandeja.
- **Buscar (`R=`) com suporte a Siglas (`BuscaPalavrasParciaisUtil`):**
  - **Encurtado (ponto):** `$$ R= #Produto nm> o.b.a;`
  - **Encurtado (espaço):** `$$ R= #Produto nm> o b a;`
  - **Encurtado (sigla contínua):** `$$ R= #Produto nm> >oba;` ou `$$ R= #Produto nm> >otba;`
  - **Encurtado (apenas com estoque válido):** `$$ R= #ProdutoEstoque nm> >oba;`
  - **Regra do Compilador:** Busca `"Ovos Branco Tipo A"`. Se houver mais de um produto com a mesma sigla, pausa e exibe a lista numerada para o usuário escolher.
- **Modificar (`U=`):**
  - **Encurtado (preço e nome):** `$$ U= #Produto nm> >oba, novo_nm> Ovos Extra Branco Tipo A, vv> 1.80;`
  - **Encurtado (dimensões físicas):** `$$ U= #ProdutoFisico id> 1, dim> 15x10x10, pb> 0.80, pl> 0.75;`
  - **Regra do Compilador:** Confirma se o produto existe, mostra os atributos atuais versus os novos parâmetros diferentes e retém na bandeja para confirmação.
- **Deletar (`D=`) com regra de estoque zero ou abatimento por perda isenta:**
  - **Encurtado (direto se estoque = 0):** `$$ D= #Produto nm> >oba;`
  - **Encurtado (forçando baixa de estoque remanescente como perda sem culpa do fornecedor):**
    `$$ D= #Produto nm> >oba, -- #NotaPerda[ q> total, resp> isento, mot> Descarte para exclusao de cadastro ];`
  - **Regra do Compilador:** Confirma se existe e checa o saldo FIFO. Só permite deletar com `quantidade == 0`. Se tiver saldo e o usuário incluir `-- #NotaPerda[ resp> isento ]` (ou aceitar na pergunta), gera primeiro a baixa de perda isentando o fornecedor de ressarcimento e em seguida agenda a exclusão do produto.

#### C) `ProdutoDigital` e `LinkProdutoDigital`
- **Criar (`C=`):**
  - **Encurtado:** `$$ C= #ProdutoDigital nm> Ebook Receitas Acai, vv> 29.90, ++ #Link[ nm> Download PDF, url> https://senac.br/ebook.pdf ];`
- **Buscar (`R=`):**
  - **Encurtado:** `$$ R= #ProdutoDigital nm> >era;`
- **Modificar (`U=`):**
  - **Encurtado:** `$$ U= #ProdutoDigital nm> >era, vv> 19.90;`
- **Deletar (`D=`):**
  - **Encurtado:** `$$ D= #ProdutoDigital nm> >era;`

#### D) `ConjuntoProduto` (Kit / Combo de Produtos)
- **Criar / Adicionar Componente ao Kit (`C=` / `U=`):**
  - **Encurtado:** `$$ C= #ConjuntoProduto nm> Kit Cafe da Manha, vv> 45.00, ++ #Componente[ ++ nm> >oba, q> 12; ++ nm> Pao de Forma, q> 1; ];`
- **Buscar (`R=`):**
  - **Encurtado:** `$$ R= #ConjuntoProduto nm> >kcm;`
- **Modificar (`U=`):**
  - **Encurtado:** `$$ U= #ConjuntoProduto id> 10, ++ #Componente[ ++ nm> Queijo Coalho, q> 1; ];`
- **Deletar (`D=`):**
  - **Encurtado:** `$$ D= #ConjuntoProduto id> 10;`

#### E) `ProdutoImagem` (Galeria de Imagens do Produto)
- **Criar / Adicionar (`C=`):**
  - **Encurtado:** `$$ C= #ProdutoImagem id_prod> 1, arq> C:/fotos/ovos_a.png, princ> true;`
- **Buscar (`R=`):**
  - **Encurtado:** `$$ R= #ProdutoImagem id_prod> 1;`
- **Modificar / Definir Principal (`U=`):**
  - **Encurtado:** `$$ U= #ProdutoImagem id_prod> 1, id> 5, princ> true;`
- **Deletar (`D=`):**
  - **Encurtado:** `$$ D= #ProdutoImagem id> 5;`

---

### 2.2. Módulo Pessoa (`br.com.senac.repository.modal.pessoa`)

#### A) `Pessoa` (Cadastro Simples, Cliente Padrão e Busca por Sigla)
- **Criar (`C=`):**
  - **Encurtado:** `$$ C= #Pessoa nm> Igor da Silva Uchoa, tp> AMBOS, val> 2500.00;`
- **Buscar por Sigla (`R=`):**
  - **Encurtado (ponto):** `$$ R= #Pessoa nm> i.s.u;`
  - **Encurtado (espaço):** `$$ R= #Pessoa nm> i s u;`
  - **Encurtado (sigla contínua):** `$$ R= #Pessoa nm> >isu;`
  - **Encurtado (histórico 360º):** `$$ R= #PessoaHistorico nm> >isu;`
- **Modificar (`U=`):**
  - **Encurtado:** `$$ U= #Pessoa nm> >isu, novo_nm> Igor da Silva Uchoa Junior, val> 5000.00;`
- **Deletar (`D=`):**
  - **Encurtado:** `$$ D= #Pessoa nm> >isu;`

#### B) `PessoaFisica` (Cliente PF e Fornecedor Produtor Rural PF)
- **Criar Cliente PF (`C=`):**
  - **Encurtado:** `$$ C= #PessoaFisica nm> Maria Oliveira Souza, tp> CLIENTE, doc> 123.456.789-01, val> 1500.00;`
- **Criar Fornecedor PF (`C=`):**
  - **Encurtado:** `$$ C= #PessoaFisica nm> Sitio Boa Vista, tp> FORNECEDOR, doc> 987.654.321-00;`
- **Buscar (`R=`):**
  - **Encurtado:** `$$ R= #PessoaFisica nm> >mos;`
- **Modificar Limite ou Nome (`U=`):**
  - **Encurtado:** `$$ U= #PessoaFisica nm> >mos, val> 3000.00;`
- **Deletar (`D=`):**
  - **Encurtado:** `$$ D= #PessoaFisica id> 4;`

#### C) `PessoaJuridica` (Cliente PJ e Fornecedor PJ)
- **Criar Fornecedor PJ (`C=`):**
  - **Encurtado:** `$$ C= #PessoaJuridica nm> Granja Marinho LTDA, tp> FORNECEDOR, doc> 12.345.678/0001-99;`
- **Criar Cliente PJ (`C=`):**
  - **Encurtado:** `$$ C= #PessoaJuridica nm> Padaria Pao Quente, tp> CLIENTE, doc> 22.333.444/0001-55, val> 10000.00;`
- **Buscar (`R=`):**
  - **Encurtado:** `$$ R= #PessoaJuridica nm> >gml;`
- **Modificar (`U=`):**
  - **Encurtado:** `$$ U= #PessoaJuridica nm> >gml, novo_nm> Granja Marinho Ovos S/A;`
- **Deletar (`D=`):**
  - **Encurtado:** `$$ D= #PessoaJuridica id> 8;`

#### D) `Registro` (Documentos Auxiliares: CPF, CNPJ, RG, IE, IM, CNH)
- **Criar / Adicionar Documento (`C=`):**
  - **Encurtado:** `$$ C= #Registro pes> >isu, tp> RG, doc> 1234567-8;`
- **Buscar Pessoa por Registro Único (`R=`):**
  - **Encurtado:** `$$ R= #Registro tp> CPF, doc> 123.456.789-01;`
- **Modificar (`U=`):**
  - **Encurtado:** `$$ U= #Registro pes> >isu, tp> IE, doc> 99887766;`
- **Deletar (`D=`):**
  - **Encurtado:** `$$ D= #Registro id> 3;`

---

### 2.3. Módulo Pagamento (`br.com.senac.repository.modal.pagamento`)

#### A) `FormaPagamento` (Regras Contábeis, Taxas MDR, Descontos e Parcelamento)
- **Criar (`C=`):**
  - **Encurtado:** `$$ C= #FormaPagamento cod> PIX_EMP, nm> Pix Empresarial, avista> true, desc_av> 2.5, taxa> 0.50;`
- **Buscar (`R=`):**
  - **Encurtado:** `$$ R= #FormaPagamento;`
- **Modificar Taxas / Status (`U=`):**
  - **Encurtado:** `$$ U= #FormaPagamento cod> CARTAO_CREDITO, max_p> 12, sem_juros> 6, taxa> 3.49, juros> 1.99;`
  - **Encurtado (Inativar):** `$$ U= #FormaPagamento cod> CHEQUE, ativo> false;`
- **Deletar (`D=`):**
  - **Encurtado:** `$$ D= #FormaPagamento cod> CHEQUE;`

#### B) `NotaPagamento` (`PagamentoAVista`, `PagamentoAPrazo`, `PagamentoParcelado` e `ParcelaPagamento`)
- **Criar Pagamento À Vista (`C=`):**
  - **Encurtado:** `$$ C= #PagamentoAVista nm> dinheiro, val> 1120.00, dt> ontem;`
  - **Encurtado:** `$$ C= #PagamentoAVista nm> pix, val> 600.00, dt> hoje;`
- **Criar Pagamento A Prazo (`C=`):**
  - **Encurtado:** `$$ C= #PagamentoAPrazo nm> boleto, val> 1500.00, dt> 30>;`
- **Criar Pagamento Parcelado (`C=`):**
  - **Encurtado:** `$$ C= #PagamentoParcelado nm> cartao_credito, val> 2400.00, q> 4;`
- **Modificar / Quitar Integral ou Parcialmente uma ou várias Notas (`U=`):**
  - **Encurtado (Quitar múltiplas notas integral ou parcialmente):**
    `$$ U= #NotaPagamento notas> [10, 11], ++ #Baixa[ ++ pix, val> 800.00, dt> hoje; ++ dinheiro, val> 400.00, dt> ontem; ];`
  - **Encurtado (Quitar parcela específica de `ParcelaPagamento`):**
    `$$ U= #ParcelaPagamento id_pag> 15, parc> 2, dt> hoje;`

---

### 2.4. Módulo Estoque e Notas Operacionais (`br.com.senac.repository.modal.nota`)

#### A) `Item` (Lote FIFO e Controle de Estoque)
- **Buscar Lotes Válidos / Fila FIFO (`R=`):**
  - **Encurtado (Saldo consolidado):** `$$ R= #Estoque nm> >oba;`
  - **Encurtado (Fila FIFO de lotes válidos):** `$$ R= #Item nm> >oba, tp> FIFO;`
  - **Encurtado (Lotes próximos ao vencimento em 15 dias):** `$$ R= #Item dt> 15>;`
- **Baixar Perda Direta no Lote (`U=` / `D=`):**
  - **Encurtado:** `$$ U= #Item id> 22, -- #Perda[ q> 5, resp> isento, mot> Embalagem rasgada no deposito ];`

#### B) `NotaCompra` (com Sub-instruções de Itens, Pagamentos e Ressarcimento Cruzado de Perdas)
- **Criar Nota de Compra Completa (`C=`):**
  - **Encurtado (Exemplo Clássico Granja Marinho):**
    ```text
    $$ C= #NotaCompra nm> Granja Marinho, dt> <4, ++ #Produto[ ++ nm> >oba, val> 20, q> 10; ++ nm> caixa de ovos, vc> 200, q> 5; ], ++ #Pagamento[ ++ dinheiro, val> 1000, dt> <3; ++ pix, val> 800, dt> hoje; ++ pix, val> 800, dt> ontem; ];
    ```
- **Criar Nota de Compra com Ressarcimento de Perda (Mesmo Produto ou Produto Cruzado — Ex: Ovos -> Açaí):**
  - **Encurtado:**
    ```text
    $$ C= #NotaCompra nm> >gml, dt> ontem, ++ #Produto[ ++ nm> Produto A, q> 120, vc> 10; ++ nm> Produto B, q> 16, vc> 40; ], ++ #Ressarcimento[ perdido> Ovos, q_perd> 1, vc_perd> 10, recebido> Acai 1L, q_rec> 1, vc_rec> 10; ], ++ #Pagamento[ ++ dinheiro, val> 1120, dt> ontem; ++ pix, val> 600, dt> hoje; ];
    ```
  - **Regra Contábil Executada:** Subtrai o caixa pelos pagamentos realizados, gera subtotal automático a cada item adicionado e marca a perda anterior do fornecedor como **ressarcida** (assumindo os valores brutos equivalentes mesmo em troca cruzada de *1 un Ovos [custo 10]* por *1L Açaí [custo 10]*).
- **Buscar (`R=`):**
  - **Encurtado:** `$$ R= #NotaCompra id> 10;` ou `$$ R= #NotaCompra nm> >gml;`
- **Modificar Data ou Itens em Estágio (`U=`):**
  - **Encurtado:** `$$ U= #NotaCompra id> OP-1, dt> ontem, ++ #Produto[ ++ nm> Produto C, q> 12, vc> 10; ], -- #Produto[ nm> Produto B; ];`

#### C) `NotaVenda` (FIFO, Venda Balcão e Lote Desejável)
- **Criar Venda FIFO (`C=`):**
  - **Encurtado:**
    ```text
    $$ C= #NotaVenda nm> >isu, dt> hoje, ++ #Produto[ ++ nm> >oba, q> 30, vv> 1.50; ++ nm> Acai 1L, q> 2, vv> 25.00; ], ++ #Pagamento[ ++ pix, val> 95.00, dt> hoje; ];
    ```
- **Criar Venda de Balcão (Cliente Padrão Automático):**
  - **Encurtado:**
    ```text
    $$ C= #NotaVenda tp> BALCAO, ++ #Produto[ ++ nm> >oba, q> 12; ], ++ #Pagamento[ ++ dinheiro, val> 18.00, dt> hoje; ];
    ```
- **Criar Venda Escolhendo Lote Desejável:**
  - **Encurtado:**
    ```text
    $$ C= #NotaVenda nm> >isu, tp> LOTE, ++ #Item[ ++ id> 45, q> 10; ], ++ #Pagamento[ ++ pix, val> 150.00; ];
    ```
  - **Regra Contábil Executada:** Consome estoque na ordem FIFO (ou do lote escolhido), adiciona receita líquida ao caixa e apura o lucro contábil (BACEN).

#### D) `NotaDevolucao` (Reintegração ao Estoque ou Perda para o Fornecedor)
- **Criar Devolução de Venda (`C=`):**
  - **Encurtado (Produto íntegro -> volta ao estoque e estorna caixa/lucro):**
    `$$ C= #NotaDevolucao id_venda> 12, ++ #Produto[ ++ nm> >oba, q> 5; ], resp> isento, mot> Cliente desistiu da compra;`
  - **Encurtado (Produto estragado dentro da validade -> estorna caixa/lucro do cliente e gera crédito de ressarcimento contra o fornecedor):**
    `$$ C= #NotaDevolucao id_venda> 12, ++ #Produto[ ++ nm> >oba, q> 5; ], resp> forn, mot> Ovos estragados antes do vencimento;`

#### E) `NotaPerda` (Perda Operacional Isenta vs. Perda Dentro da Validade para Ressarcimento)
- **Criar Nota de Perda (`C=`):**
  - **Encurtado (Culpa interna / isentando fornecedor — abate no lucro/caixa o custo integral ou parcial):**
    `$$ C= #NotaPerda ++ #Item[ ++ id> 22, q> 10; ], resp> isento, mot> Queda de prateleira;`
  - **Encurtado (Dentro da validade por problema de origem — ativa flag de ressarcimento do fornecedor):**
    `$$ C= #NotaPerda ++ #Item[ ++ id> 22, q> 10; ], resp> forn, mot> Lote deteriorado dentro da validade;`

#### F) `VinculoFatorExternoNota` (Clima, Rios, Eventos e Calendário da Nota)
- **Buscar / Resolver Fatores Externos das Notas (`R=` / `U=`):**
  - **Encurtado (Consultar fatores de uma nota):** `$$ R= #VinculoFatorExternoNota id_nota> 10;`
  - **Encurtado (Resolver pendências em lote na janela de 6h):** `$$ U= #VinculoFatorExternoNota tp> RESOLVER_PENDENCIAS;`

---

## 3. Arquitetura Preparada para Implementação na Próxima Rodada

Caso aprovado, o desenvolvimento na próxima rodada seguirá este roteiro modular sem quebrar as 72 rotas existentes:

1. **`BuscaPalavrasParciaisUtil.java` (Aprimoramento de Siglas):**
   - Suporte nativo aos prefixos `>sigla` (`>isu`, `>oba`, `>otba`) e siglas pontuadas (`i.s.u`, `o.b.a`), retornando lista de candidatos quando houver empate de sigla para escolha do usuário.
2. **`CompiladorInstrucoesCurtaService.java` (Novo — `br.com.senac.integracao.ia`):**
   - Detecta qualquer mensagem iniciada com `$$`.
   - Usa `java.util.stream.Stream` para tokenizar instruções principais (`;`) e sub-instruções (`[...]`), mantendo uma **Tabela de Símbolos em Memória de Execução** (`OP-1`, `OP-2`, subtotais parciais e produtos recém-declarados no mesmo bloco `$$`, evitando erro de tentar usar ou apagar algo ainda não validado na sequência).
   - Suporta `close<>` (remove a instrução atual), `close<OP-X>` (remove instrução específica da memória) e `close::` (limpa toda a bandeja).
3. **`BandejaOperacoesContexto.java` e `GeminiAgenteOperacional.java`:**
   - Intercepta prompts iniciados por `$$` antes mesmo de gastar tokens de LLM (ou usa o resumo pré-formatado determinístico), populando os `ItemBandeja` com IDs mapeados (`OP-1`, `OP-2`...) e subtotais calculados.
   - Gera o **Resumo em Linguagem Natural Pré-Formatado** para revisão humana, permitindo que o usuário altere/exclua itens específicos pelo ID (`OP-1`) e revalide toda a cadeia antes do `SIM`.
4. **Suíte de Testes (`TesteCompiladorEncurtador.java`):**
   - Testes automatizados cobrindo todas as classes de CRUD acima, siglas `>isu`/`>oba`, sub-instruções de `NotaCompra` com ressarcimento cruzado e regras de exclusão de produto com estoque > 0.
