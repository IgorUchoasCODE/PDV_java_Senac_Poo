package br.com.senac.integracao.ia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orquestrador de Alta Disponibilidade (Failover Chain of Responsibility) para múltiplos provedores de IA.
 * Implementa {@link ProvedorIA} de forma 100% transparente: caso a IA primária falhe (timeout, queda de rede,
 * limite de cota HTTP 429/503 ou falta de chave), a próxima IA da fila assume imediatamente a requisição
 * sem exigir nenhuma modificação no {@code GeminiAgenteOperacional} ou nas camadas superiores.
 */
public class ProvedorIAComFailover implements ProvedorIA {

    private final List<ProvedorIA> cadeiaProvedores;
    private volatile ProvedorIA ultimoProvedorSucesso;
    private final java.util.Map<String, Long> cooldownsAteMs = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long COOLDOWN_PADRAO_MS = 60_000L;

    public ProvedorIAComFailover(List<ProvedorIA> provedores) {
        if (provedores == null || provedores.isEmpty()) {
            throw new IllegalArgumentException("A cadeia de failover deve conter pelo menos um ProvedorIA.");
        }
        this.cadeiaProvedores = List.copyOf(provedores);
        this.ultimoProvedorSucesso = this.cadeiaProvedores.get(0);
    }

    public List<ProvedorIA> getCadeiaProvedores() {
        return Collections.unmodifiableList(cadeiaProvedores);
    }

    public ProvedorIA getProvedorAtivoAtual() {
        return ultimoProvedorSucesso != null ? ultimoProvedorSucesso : cadeiaProvedores.get(0);
    }

    @Override
    public String getId() {
        return getProvedorAtivoAtual().getId();
    }

    @Override
    public String getDescricao() {
        String nomes = cadeiaProvedores.stream()
                .map(ProvedorIA::getId)
                .collect(Collectors.joining(" -> "));
        return getProvedorAtivoAtual().getDescricao() + " [Failover: " + nomes + "]";
    }

    @Override
    public boolean suportaMidia() {
        return cadeiaProvedores.stream().anyMatch(ProvedorIA::suportaMidia);
    }

    @Override
    public String verificarPronto() {
        List<String> motivos = new ArrayList<>();
        for (ProvedorIA p : cadeiaProvedores) {
            String erro = p.verificarPronto();
            if (erro == null) {
                return null; // Pelo menos um provedor da cadeia está pronto!
            }
            motivos.add("[" + p.getId() + "] " + erro);
        }
        return String.join(" | ", motivos);
    }

    @Override
    public RespostaIA conversar(PedidoIA pedido) throws Exception {
        PedidoIA pedidoEnriquecido = enriquecerSeContiverDsl(pedido);
        List<String> errosTentativas = new ArrayList<>();
        long agora = System.currentTimeMillis();

        // Se todos os provedores estiverem em cooldown, limpa os cooldowns para tentar novamente
        boolean todosEmCooldown = cadeiaProvedores.stream()
                .allMatch(p -> cooldownsAteMs.getOrDefault(p.getId(), 0L) > agora);
        if (todosEmCooldown) {
            cooldownsAteMs.clear();
        }

        for (ProvedorIA provedor : cadeiaProvedores) {
            if (pedidoEnriquecido.anexo() != null && !provedor.suportaMidia()) {
                continue;
            }
            if (cooldownsAteMs.getOrDefault(provedor.getId(), 0L) > agora) {
                errosTentativas.add(provedor.getId() + ": em cooldown temporário após erro recente");
                continue;
            }
            String bloqueio = provedor.verificarPronto();
            if (bloqueio != null) {
                errosTentativas.add(provedor.getId() + ": não configurado");
                continue;
            }
            try {
                RespostaIA resposta = provedor.conversar(pedidoEnriquecido);
                this.ultimoProvedorSucesso = provedor;
                cooldownsAteMs.remove(provedor.getId());
                return resposta;
            } catch (Exception ex) {
                cooldownsAteMs.put(provedor.getId(), System.currentTimeMillis() + COOLDOWN_PADRAO_MS);
                errosTentativas.add(provedor.getId() + ": " + ex.getMessage());
            }
        }

        throw new RuntimeException("Nenhum provedor de IA na cadeia de failover conseguiu responder. Tentativas: "
                + String.join(" -> ", errosTentativas));
    }

    /**
     * Caso uma mensagem enviada à IA contenha a sintaxe {@code $$} (ex: {@code $$ C= #Produto, nm> Ovo, vl> 20, um> UN}),
     * anexa automaticamente a leitura traduzida em linguagem natural para que qualquer modelo de IA compreenda
     * perfeitamente a intenção sem ambiguidade.
     */
    private PedidoIA enriquecerSeContiverDsl(PedidoIA original) {
        if (original == null || original.mensagem() == null) return original;
        String msg = original.mensagem().trim();
        if (msg.contains("$$") || msg.matches("(?is).*[CRUD]\\s*=\\s*#[A-Za-z0-9_]+.*")) {
            String traducao = ContextoIABuilder.traduzirDslParaLinguagemNatural(msg);
            if (!traducao.isBlank()) {
                String msgCombinada = msg + "\n[Leitura interpretada da instrução: " + traducao + "]";
                return new PedidoIA(msgCombinada, original.anexo(), original.mimeType(), original.historico());
            }
        }
        return original;
    }
}
