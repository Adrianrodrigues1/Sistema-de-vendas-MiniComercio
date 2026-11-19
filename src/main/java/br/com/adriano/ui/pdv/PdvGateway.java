package br.com.adriano.ui.pdv;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Porta para adaptação futura ao service. Chamadas da UI sempre fora da EDT.
 * Implementações reais deverão validar preços, autorização e estoque atomicamente no backend.
 * Nenhuma implementação desta fase acessa JDBC, banco, caixa ou arquivos de vendas.
 */
public interface PdvGateway {
    List<ProdutoPdv> listarProdutos() throws Exception;
    Resultado concluirVenda(Pedido pedido) throws Exception;

    record Pedido(List<Carrinho.Item> itens, BigDecimal desconto, Pagamento pagamento, String cliente) {
        public Pedido {
            itens = List.copyOf(itens);
            if (itens.isEmpty()) throw new IllegalArgumentException("Carrinho vazio.");
            desconto = Dinheiro.valor(Objects.requireNonNull(desconto));
            Objects.requireNonNull(pagamento);
            BigDecimal subtotal = itens.stream().map(Carrinho.Item::total).reduce(Dinheiro.ZERO, BigDecimal::add);
            if (desconto.signum() < 0 || desconto.compareTo(subtotal) > 0
                    || subtotal.subtract(desconto).compareTo(pagamento.total()) != 0)
                throw new IllegalArgumentException("Totais do pedido inconsistentes.");
        }
    }
    record Resultado(String identificador, String mensagem) { }
}
