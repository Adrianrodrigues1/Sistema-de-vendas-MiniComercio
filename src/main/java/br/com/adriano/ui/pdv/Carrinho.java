package br.com.adriano.ui.pdv;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Modelo da sessão do PDV, independente de Swing. Alterações feitas na EDT pela interface. */
public final class Carrinho {
    public record Item(ProdutoPdv produto, int quantidade) {
        public Item {
            if (produto == null || quantidade < 1)
                throw new IllegalArgumentException("Quantidade deve ser positiva.");
        }
        public BigDecimal total() { return Dinheiro.valor(produto.preco().multiply(BigDecimal.valueOf(quantidade))); }
    }
    private final Map<Long, Item> itens = new LinkedHashMap<>();
    private BigDecimal desconto = Dinheiro.ZERO;

    public void adicionar(ProdutoPdv produto, int quantidade) {
        if (quantidade <= 0) throw new IllegalArgumentException("A quantidade deve ser positiva.");
        Item atual = itens.get(produto.id());
        int total = Math.addExact(atual == null ? 0 : atual.quantidade(), quantidade);
        // Valida a soma das inclusões do mesmo produto, não somente a última inclusão.
        itens.put(produto.id(), new Item(produto, total));
    }
    public void quantidade(long id, int quantidade) {
        Item atual = itens.get(id);
        if (atual == null) throw new IllegalArgumentException("Produto não está no carrinho.");
        if (quantidade < 0) throw new IllegalArgumentException("Quantidade inválida.");
        if (quantidade == 0) itens.remove(id);
        else itens.put(id, new Item(atual.produto(), quantidade));
        ajustarDesconto();
    }
    public void remover(long id) { itens.remove(id); ajustarDesconto(); }
    private void ajustarDesconto() { desconto = desconto.min(subtotal()); }
    public List<Item> itens() { return List.copyOf(itens.values()); }
    public int quantidade() { return itens.values().stream().mapToInt(Item::quantidade).sum(); }
    public BigDecimal subtotal() { return itens.values().stream().map(Item::total).reduce(Dinheiro.ZERO, BigDecimal::add); }
    public BigDecimal desconto() { return desconto; }
    public void desconto(BigDecimal valor) {
        if (valor.signum() < 0) throw new IllegalArgumentException("Desconto não pode ser negativo.");
        valor = Dinheiro.valor(valor);
        if (valor.compareTo(subtotal()) > 0) throw new IllegalArgumentException("Desconto não pode superar o subtotal.");
        desconto = valor;
    }
    public BigDecimal total() { return subtotal().subtract(desconto); }
    public boolean vazio() { return itens.isEmpty(); }
    public void limpar() { itens.clear(); desconto = Dinheiro.ZERO; }

    public static List<ProdutoPdv> buscar(List<ProdutoPdv> produtos, String busca, String categoria) {
        String termo = normalizar(busca.strip());
        return produtos.stream().filter(p -> categoria.equals("Todos") || p.categoria().equals(categoria))
                .filter(p -> normalizar(p.nome()).contains(termo) || normalizar(p.codigo()).contains(termo)).toList();
    }
    private static String normalizar(String valor) {
        return Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
