package br.com.senac.view.av2;

import java.math.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Dados fictícios, sem banco e sem cobrança real. Não substitui o domínio da equipe. */
public final class DemoGateway implements PdvGateway {
    private final List<Produto> catalogo = new ArrayList<>();
    private final List<Cliente> pessoas = new ArrayList<>();
    private final LinkedHashMap<String,Integer> carrinho = new LinkedHashMap<>();
    private final List<Venda> historico = new ArrayList<>();
    public DemoGateway() {
        salvarProduto(new Produto("P001", "Teclado sem fio", "Físico", new BigDecimal("129.90"), 12, "0,65 kg", "44 x 14 x 3 cm", "", ""));
        salvarProduto(new Produto("P002", "Mouse ergonômico", "Físico", new BigDecimal("89.90"), 8, "0,12 kg", "12 x 7 x 4 cm", "", ""));
        salvarProduto(new Produto("D001", "E-book: primeiros passos em Java", "Digital", new BigDecimal("49.90"), 30, "", "", "https://example.com/java", "12 MB"));
        salvarProduto(new Produto("D002", "Licença do editor de código", "Digital", new BigDecimal("199.00"), 5, "", "", "https://example.com/editor", "150 MB"));
        salvarProduto(new Produto("P003", "Headset USB", "Físico", new BigDecimal("159.90"), 0, "0,3 kg", "20 x 18 x 8 cm", "", ""));
        salvarCliente(new Cliente("Ana Souza", "Cliente demonstrativo 01", "ana@example.com", "(92) 99999-0000", "Rua das Flores, 120 · Manaus/AM"));
    }
    public List<Produto> produtos() { return List.copyOf(catalogo); }
    public List<Cliente> clientes() { return List.copyOf(pessoas); }
    public List<Venda> vendas() { return List.copyOf(historico); }
    private Produto produto(String codigo) { return catalogo.stream().filter(p -> p.codigo().equals(codigo)).findFirst().orElseThrow(() -> new IllegalArgumentException("Código não encontrado.")); }
    public List<Item> itens() { return carrinho.entrySet().stream().map(e -> new Item(produto(e.getKey()),e.getValue())).toList(); }
    public void salvarProduto(Produto p) {
        if(p.codigo().isBlank() || p.nome().isBlank()) throw new IllegalArgumentException("Informe código e nome.");
        if(p.preco().signum() <= 0 || p.estoque() < 0) throw new IllegalArgumentException("Preço deve ser positivo e estoque não pode ser negativo.");
        if(!List.of("Físico","Digital").contains(p.tipo())) throw new IllegalArgumentException("Tipo inválido.");
        if(p.tipo().equals("Físico") && (p.peso().isBlank() || p.dimensoes().isBlank())) throw new IllegalArgumentException("Informe peso e dimensões.");
        if(p.tipo().equals("Digital") && (p.link().isBlank() || p.tamanho().isBlank())) throw new IllegalArgumentException("Informe link e tamanho do arquivo.");
        if(carrinho.getOrDefault(p.codigo(),0)>p.estoque()) throw new IllegalArgumentException("O estoque não pode ser menor que a quantidade no carrinho.");
        catalogo.removeIf(x -> x.codigo().equals(p.codigo())); catalogo.add(p);
    }
    public void salvarCliente(Cliente c) {
        if(c.nome().isBlank() || c.documento().isBlank()) throw new IllegalArgumentException("Informe nome e documento/identificação.");
        if(!c.email().isBlank() && !c.email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("E-mail inválido.");
        pessoas.removeIf(x -> x.documento().equals(c.documento())); pessoas.add(c);
    }
    public void adicionar(String codigo,int qtd) { if(qtd<=0) throw new IllegalArgumentException("A quantidade deve ser maior que zero."); alterarQuantidade(codigo,Math.addExact(carrinho.getOrDefault(codigo,0),qtd)); }
    public void alterarQuantidade(String codigo,int qtd) {
        Produto p = produto(codigo);
        if(qtd<=0) throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
        if(qtd>p.estoque()) throw new IllegalArgumentException("Estoque insuficiente: há " + p.estoque() + " unidade(s) de " + p.nome() + ".");
        carrinho.put(codigo,qtd);
    }
    public void remover(String codigo) { carrinho.remove(codigo); }
    public void limpar() { carrinho.clear(); }
    static BigDecimal money(BigDecimal v) { return v.setScale(2,RoundingMode.HALF_UP); }
    public Resumo resumo(String metodo) {
        if(!List.of("PIX","Dinheiro","Cartão de crédito").contains(metodo)) throw new IllegalArgumentException("Selecione um pagamento válido.");
        BigDecimal sub = itens().stream().map(i -> i.produto().preco().multiply(BigDecimal.valueOf(i.quantidade()))).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal frete = itens().stream().anyMatch(i -> i.produto().tipo().equals("Físico")) ? new BigDecimal("15.00") : BigDecimal.ZERO;
        // PIX incide sobre produtos; cartão sobre produtos + frete. Decisão documentada no README.
        BigDecimal ajuste = money(metodo.equals("PIX") ? sub.multiply(new BigDecimal("-0.05")) : metodo.equals("Cartão de crédito") ? sub.add(frete).multiply(new BigDecimal("0.03")) : BigDecimal.ZERO);
        return new Resumo(money(sub),money(frete),ajuste,money(sub.add(frete).add(ajuste)));
    }
    public Venda confirmar(Cliente cliente,String endereco,String metodo,BigDecimal recebido) {
        if(carrinho.isEmpty()) throw new IllegalArgumentException("Adicione produtos antes de finalizar.");
        if(cliente==null) throw new IllegalArgumentException("Selecione um cliente.");
        boolean fisico=itens().stream().anyMatch(i -> i.produto().tipo().equals("Físico"));
        if(fisico && endereco.isBlank()) throw new IllegalArgumentException("Informe o endereço de entrega dos produtos físicos.");
        for(Item i:itens()) if(i.quantidade()>i.produto().estoque()) throw new IllegalArgumentException("Estoque insuficiente.");
        Resumo r=resumo(metodo);
        if(metodo.equals("Dinheiro") && (recebido==null || recebido.compareTo(r.total())<0)) throw new IllegalArgumentException("Valor recebido é menor que o total.");
        String numero=String.format("%04d",historico.size()+1), data=LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        StringBuilder cupom=new StringBuilder("SENAC PDV · COMPROVANTE DEMONSTRATIVO\nSEM VALOR FISCAL · SEM COBRANÇA REAL\nVenda #"+numero+" · "+data+"\nCliente: "+cliente.nome()+"\n-------------------------------------------\n");
        for(Item i:itens()) cupom.append(i.produto().codigo()+" · "+i.produto().nome()+"\n  "+i.quantidade()+" x "+brl(i.produto().preco())+" = "+brl(i.produto().preco().multiply(BigDecimal.valueOf(i.quantidade())))+"\n");
        cupom.append("-------------------------------------------\nProdutos: "+brl(r.subtotal())+"\nFrete: "+brl(r.frete())+"\nDesconto/taxa: "+brl(r.ajuste())+"\nTOTAL: "+brl(r.total())+"\nPagamento: "+metodo+"\n");
        if(metodo.equals("Dinheiro")) cupom.append("Recebido: "+brl(recebido)+"\nTroco: "+brl(recebido.subtract(r.total()))+"\n");
        if(fisico) cupom.append("Entrega: "+endereco+"\n");
        for(Item i:itens()) if(i.produto().tipo().equals("Digital")) cupom.append("Acesso demonstrativo: "+i.produto().link()+"\n");
        List<Item> comprados=itens(); carrinho.clear();
        for(Item i:comprados) { Produto p=i.produto(); salvarProduto(new Produto(p.codigo(),p.nome(),p.tipo(),p.preco(),p.estoque()-i.quantidade(),p.peso(),p.dimensoes(),p.link(),p.tamanho())); }
        Venda venda=new Venda(numero,data,cliente.nome(),metodo,r.total(),cupom.toString()); historico.add(0,venda); return venda;
    }
    static String brl(BigDecimal valor) { return java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(valor); }
}
