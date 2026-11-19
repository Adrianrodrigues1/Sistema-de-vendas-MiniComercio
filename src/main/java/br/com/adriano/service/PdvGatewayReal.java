package br.com.adriano.service;

import br.com.adriano.db.ConexaoManager;
import br.com.adriano.model.Produto;
import br.com.adriano.ui.pdv.Carrinho;
import br.com.adriano.ui.pdv.Pagamento;
import br.com.adriano.ui.pdv.PdvGateway;
import br.com.adriano.ui.pdv.ProdutoPdv;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementação real do PdvGateway usando os serviços de domínio (SQLite).
 * <p>
 * Esta implementação substitui o {@code GatewayDemonstracao} e usa
 * {@code ProdutoService} e {@code VendaService} para operações reais
 * com persistência em banco de dados.
 *
 * @since 2.0
 */
public final class PdvGatewayReal implements PdvGateway {

    private final ProdutoService produtoService;
    private final VendaService vendaService;
    private final CaixaService caixaService;
    private final UsuarioService usuarioService;

    public PdvGatewayReal(ConexaoManager conexaoManager) {
        this.produtoService = new ProdutoService(conexaoManager);
        this.vendaService = new VendaService(conexaoManager, produtoService);
        this.caixaService = new CaixaService(conexaoManager);
        this.usuarioService = new UsuarioService(conexaoManager);
    }

    @Override
    public List<ProdutoPdv> listarProdutos() throws SQLException {
        List<Produto> produtos = produtoService.listarTodos(true); // apenas ativos
        return produtos.stream()
                .map(this::toProdutoPdv)
                .collect(Collectors.toList());
    }

    @Override
    public PdvGateway.Resultado concluirVenda(PdvGateway.Pedido pedido) throws SQLException {
        // Converter itens do carrinho para ProdutoPdv (já são ProdutoPdv no Carrinho)
        List<ProdutoPdv> itensPdv = pedido.itens().stream()
                .map(Carrinho.Item::produto)
                .collect(Collectors.toList());

        // Verificar estoque antes de processar
        for (Carrinho.Item item : pedido.itens()) {
            Optional<Produto> prodOpt = produtoService.buscarPorId((int) item.produto().id());
            if (prodOpt.isEmpty()) {
                throw new SQLException("Produto não encontrado: " + item.produto().id());
            }
            Produto prod = prodOpt.get();
            if (prod.getEstoque() < item.quantidade()) {
                throw new SQLException("Estoque insuficiente para: " + prod.getNome() +
                        " (disponível: " + prod.getEstoque() + ", solicitado: " + item.quantidade() + ")");
            }
        }

        // Obter usuário atual (operador padrão = 2, admin = 1)
        int usuarioId = obterUsuarioPadrao();

        // Verificar se caixa está aberto
        if (!caixaService.isCaixaAberto()) {
            throw new IllegalStateException("Caixa não está aberto. Abra o caixa antes de realizar vendas.");
        }

        // Registrar venda
        int vendaId = vendaService.registrarVenda(
                criarCarrinhoDoPedido(pedido),
                pedido.pagamento(),
                null, // clienteId - opcional por enquanto
                usuarioId
        );

        return new PdvGateway.Resultado(
                "VENDA-" + vendaId,
                "Venda nº " + vendaId + " concluída com sucesso!"
        );
    }

    private Carrinho criarCarrinhoDoPedido(PdvGateway.Pedido pedido) {
        Carrinho carrinho = new Carrinho();
        for (Carrinho.Item item : pedido.itens()) {
            carrinho.adicionar(item.produto(), item.quantidade());
        }
        carrinho.desconto(pedido.desconto());
        return carrinho;
    }

    private int obterUsuarioPadrao() {
        // Tenta buscar operador (id=2), senão admin (id=1)
        try {
            Optional<UsuarioService.UsuarioInfo> operador = usuarioService.buscarPorLogin("operador");
            if (operador.isPresent()) return operador.get().id();
        } catch (SQLException ignored) {}
        return 1; // fallback para admin
    }

    private ProdutoPdv toProdutoPdv(Produto p) {
        return new ProdutoPdv(
                p.getId(),
                p.getCodigo(),
                p.getNome(),
                p.getCategoria(),
                p.getPrecoBigDecimal(),
                p.getEstoque()
        );
    }
}