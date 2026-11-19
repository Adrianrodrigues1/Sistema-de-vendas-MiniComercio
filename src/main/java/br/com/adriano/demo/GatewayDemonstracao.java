package br.com.adriano.demo;

import br.com.adriano.ui.pdv.*;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Somente memória. Reabrir a demo restaura os produtos; nenhuma venda é persistida. */
public final class GatewayDemonstracao implements PdvGateway {
    private final Map<Long, ProdutoPdv> produtos = new LinkedHashMap<>();
    private int sequencia;
    public GatewayDemonstracao() {
        adicionar(1, "001", "Caldo de Mocotó", "Caldos", "19.40", 16);
        adicionar(2, "002", "Caldo de Frango", "Caldos", "16.80", 24);
        adicionar(3, "003", "Caldo de Costela", "Caldos", "21.20", 12);
        adicionar(4, "004", "Caldo de Feijão", "Caldos", "14.60", 20);
        adicionar(10, "010", "Pão Torrado", "Acompanhamentos", "4.30", 32);
        adicionar(11, "011", "Queijo Ralado", "Acompanhamentos", "3.70", 21);
        adicionar(20, "020", "Refrigerante", "Bebidas", "6.40", 27);
        adicionar(30, "030", "Embalagem (Pote)", "Outros", "1.20", 45);
    }
    private void adicionar(long id, String codigo, String nome, String categoria, String preco, int estoque) {
        produtos.put(id, new ProdutoPdv(id, codigo, nome, categoria, new BigDecimal(preco), estoque));
    }
    @Override public synchronized List<ProdutoPdv> listarProdutos() { return List.copyOf(produtos.values()); }
    @Override public synchronized Resultado concluirVenda(Pedido pedido) {
        Map<Long, Integer> quantidades = new LinkedHashMap<>();
        for (Carrinho.Item item : pedido.itens()) {
            ProdutoPdv atual = produtos.get(item.produto().id());
            if (atual == null || atual.preco().compareTo(item.produto().preco()) != 0)
                throw new IllegalArgumentException("Produto alterado. Recarregue a demonstração.");
            quantidades.merge(atual.id(), item.quantidade(), Math::addExact);
        }
        quantidades.forEach((id, qtd) -> {
            if (qtd > produtos.get(id).estoque()) throw new IllegalArgumentException("Estoque demonstrativo insuficiente.");
        });
        quantidades.forEach((id, qtd) -> {
            ProdutoPdv p = produtos.get(id);
            produtos.put(id, new ProdutoPdv(p.id(), p.codigo(), p.nome(), p.categoria(), p.preco(), p.estoque() - qtd));
        });
        return new Resultado("DEMO-" + (++sequencia), "Simulação concluída. Nenhuma venda real foi registrada.");
    }
}
