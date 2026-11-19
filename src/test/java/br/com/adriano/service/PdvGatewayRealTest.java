package br.com.adriano.service;

import br.com.adriano.db.ConexaoManager;
import br.com.adriano.migration.MigrationService;
import br.com.adriano.model.Produto;
import br.com.adriano.ui.pdv.Carrinho;
import br.com.adriano.ui.pdv.Dinheiro;
import br.com.adriano.ui.pdv.Pagamento;
import br.com.adriano.ui.pdv.PdvGateway;
import br.com.adriano.ui.pdv.ProdutoPdv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PdvGatewayReal - integração real com SQLite")
class PdvGatewayRealTest extends BaseServiceTest {

    private ProdutoService produtoService;
    private PdvGatewayReal gateway;

    @BeforeEach
    @Override
    void setUp() throws Exception {
        super.setUp();
        produtoService = new ProdutoService(getConexaoManager());
        gateway = new PdvGatewayReal(getConexaoManager());

        // Abrir caixa padrão para testes
        CaixaService caixaService = new CaixaService(getConexaoManager());
        caixaService.abrirCaixa(new BigDecimal("100.00"), 1);

        // Inserir produtos de teste
        produtoService.salvar(new Produto(null, "CVD001", "Caldo Verde", "Caldos",
                new BigDecimal("15.00"), 100, null, true));
        produtoService.salvar(new Produto(null, "CVD002", "Caldo de Feijão", "Caldos",
                new BigDecimal("18.00"), 50, null, true));
        produtoService.salvar(new Produto(null, "REF001", "Refrigerante", "Bebidas",
                new BigDecimal("6.00"), 200, null, true));
    }

    // Helper para converter Produto legacy para ProdutoPdv
    private ProdutoPdv toPdv(String codigo) throws Exception {
        Produto p = produtoService.buscarPorCodigo(codigo).orElseThrow();
        return new ProdutoPdv(p.getId(), p.getCodigo(), p.getNome(), p.getCategoria(),
                p.getPrecoBigDecimal(), p.getEstoque());
    }

    // Helper para pagamento em dinheiro
    private Pagamento dinheiro(BigDecimal total, BigDecimal recebido) {
        return Pagamento.calcular(Pagamento.Forma.DINHEIRO, total, recebido);
    }

    // Helper para pagamento PIX
    private Pagamento pix(BigDecimal total) {
        return Pagamento.calcular(Pagamento.Forma.PIX, total, total);
    }

    @Test
    @DisplayName("Deve listar produtos ativos do banco")
    void listarProdutos() throws Exception {
        List<ProdutoPdv> produtos = gateway.listarProdutos();

        assertEquals(3, produtos.size());
        assertTrue(produtos.stream().anyMatch(p -> p.codigo().equals("CVD001")));
        assertTrue(produtos.stream().anyMatch(p -> p.codigo().equals("CVD002")));
        assertTrue(produtos.stream().anyMatch(p -> p.codigo().equals("REF001")));
    }

    @Test
    @DisplayName("Deve processar venda simples com dinheiro e baixar estoque")
    void vendaSimplesDinheiro() throws Exception {
        ProdutoPdv caldo = toPdv("CVD001");
        int estoqueInicial = caldo.estoque();

        Carrinho carrinho = new Carrinho();
        carrinho.adicionar(caldo, 2); // 2 x 15.00 = 30.00

        PdvGateway.Pedido pedido = new PdvGateway.Pedido(
                carrinho.itens(),
                BigDecimal.ZERO,
                dinheiro(carrinho.total(), Dinheiro.ler("50,00")),
                null
        );

        PdvGateway.Resultado resultado = gateway.concluirVenda(pedido);

        assertNotNull(resultado.identificador());
        assertTrue(resultado.identificador().startsWith("VENDA-"));
        assertTrue(resultado.mensagem().contains("concluída"));

        // Verificar estoque baixado no banco
        Optional<Produto> prodAtualizado = produtoService.buscarPorCodigo("CVD001");
        assertTrue(prodAtualizado.isPresent());
        assertEquals(estoqueInicial - 2, prodAtualizado.get().getEstoque());
    }

    @Test
    @DisplayName("Deve processar venda com PIX")
    void vendaPix() throws Exception {
        ProdutoPdv caldo = toPdv("CVD001");

        Carrinho carrinho = new Carrinho();
        carrinho.adicionar(caldo, 1);

        PdvGateway.Pedido pedido = new PdvGateway.Pedido(
                carrinho.itens(),
                BigDecimal.ZERO,
                pix(carrinho.total()),
                null
        );

        PdvGateway.Resultado resultado = gateway.concluirVenda(pedido);

        assertNotNull(resultado.identificador());
        assertTrue(resultado.identificador().startsWith("VENDA-"));
    }

    @Test
    @DisplayName("Deve processar venda com desconto")
    void vendaComDesconto() throws Exception {
        ProdutoPdv caldo = toPdv("CVD001");

        Carrinho carrinho = new Carrinho();
        carrinho.adicionar(caldo, 2); // 30.00
        carrinho.desconto(new BigDecimal("5.00")); // Desconto 5.00

        PdvGateway.Pedido pedido = new PdvGateway.Pedido(
                carrinho.itens(),
                carrinho.desconto(),
                dinheiro(carrinho.total(), Dinheiro.ler("25,00")),
                null
        );

        PdvGateway.Resultado resultado = gateway.concluirVenda(pedido);

        assertNotNull(resultado.identificador());
        // Verificar que venda foi registrada com desconto
        // (o ID da venda está no identificador)
        String vendaIdStr = resultado.identificador().replace("VENDA-", "");
        int vendaId = Integer.parseInt(vendaIdStr);
        Optional<VendaService.VendaResumo> venda = new VendaService(getConexaoManager(), produtoService).buscarVenda(vendaId);
        assertTrue(venda.isPresent());
        assertEquals(0, new BigDecimal("5.00").compareTo(venda.get().desconto()));
        assertEquals(0, new BigDecimal("25.00").compareTo(venda.get().total()));
    }

    @Test
    @DisplayName("Deve rejeitar venda com estoque insuficiente")
    void rejeitarEstoqueInsuficiente() throws Exception {
        ProdutoPdv caldo = toPdv("CVD001");
        int estoque = caldo.estoque(); // 100

        Carrinho carrinho = new Carrinho();
        carrinho.adicionar(caldo, estoque + 10); // Tentar vender mais que tem

        PdvGateway.Pedido pedido = new PdvGateway.Pedido(
                carrinho.itens(),
                BigDecimal.ZERO,
                dinheiro(carrinho.total(), Dinheiro.ler("2000,00")),
                null
        );

        assertThrows(SQLException.class, () -> gateway.concluirVenda(pedido));

        // Verificar que estoque NÃO foi alterado
        Optional<Produto> prodAtualizado = produtoService.buscarPorCodigo("CVD001");
        assertTrue(prodAtualizado.isPresent());
        assertEquals(estoque, prodAtualizado.get().getEstoque());
    }

    @Test
    @DisplayName("Deve rejeitar venda quando caixa está fechado")
    void rejeitarVendaCaixaFechado() throws Exception {
        // Fechar o caixa que foi aberto no setUp
        CaixaService caixaService = new CaixaService(getConexaoManager());
        Optional<CaixaService.CaixaInfo> caixaAberto = caixaService.getCaixaAberto();
        if (caixaAberto.isPresent()) {
            caixaService.fecharCaixa(caixaAberto.get().id(), 1);
        }

        ProdutoPdv caldo = toPdv("CVD001");

        Carrinho carrinho = new Carrinho();
        carrinho.adicionar(caldo, 1);

        PdvGateway.Pedido pedido = new PdvGateway.Pedido(
                carrinho.itens(),
                BigDecimal.ZERO,
                pix(carrinho.total()),
                null
        );

        assertThrows(IllegalStateException.class, () -> gateway.concluirVenda(pedido));
    }

    @Test
    @DisplayName("Deve permitir venda com caixa aberto (setup padrão)")
    void vendaComCaixaAberto() throws Exception {
        ProdutoPdv caldo = toPdv("CVD001");

        Carrinho carrinho = new Carrinho();
        carrinho.adicionar(caldo, 1);

        PdvGateway.Pedido pedido = new PdvGateway.Pedido(
                carrinho.itens(),
                BigDecimal.ZERO,
                pix(carrinho.total()),
                null
        );

        PdvGateway.Resultado resultado = gateway.concluirVenda(pedido);

        assertNotNull(resultado.identificador());
        assertTrue(resultado.identificador().startsWith("VENDA-"));
    }

    @Test
    @DisplayName("Deve somar quantidades do mesmo produto no pedido")
    void somarQuantidadesMesmoProduto() throws Exception {

        ProdutoPdv caldo = toPdv("CVD001");
        int estoqueInicial = caldo.estoque();

        Carrinho carrinho = new Carrinho();
        carrinho.adicionar(caldo, 1);
        carrinho.adicionar(caldo, 2); // Mesmo produto, soma = 3

        PdvGateway.Pedido pedido = new PdvGateway.Pedido(
                carrinho.itens(),
                BigDecimal.ZERO,
                pix(carrinho.total()),
                null
        );

        gateway.concluirVenda(pedido);

        // Verificar que baixou 3 no total
        Optional<Produto> prodAtualizado = produtoService.buscarPorCodigo("CVD001");
        assertTrue(prodAtualizado.isPresent());
        assertEquals(estoqueInicial - 3, prodAtualizado.get().getEstoque());
    }
}