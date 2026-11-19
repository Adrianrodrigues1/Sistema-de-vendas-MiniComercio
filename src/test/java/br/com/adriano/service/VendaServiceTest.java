package br.com.adriano.service;

import br.com.adriano.model.Produto;
import br.com.adriano.ui.pdv.Carrinho;
import br.com.adriano.ui.pdv.Dinheiro;
import br.com.adriano.ui.pdv.Pagamento;
import br.com.adriano.ui.pdv.ProdutoPdv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VendaService - regras de negócio de vendas")
class VendaServiceTest extends BaseServiceTest {

    private ProdutoService produtoService;
    private VendaService vendaService;

    @BeforeEach
    @Override
    void setUp() throws Exception {
        super.setUp();
        produtoService = new ProdutoService(getConexaoManager());
        vendaService = new VendaService(getConexaoManager(), produtoService);

        // Produtos para testes (legacy model para service)
        produtoService.salvar(new Produto(null, "CVD001", "Caldo Verde", "Caldos", new BigDecimal("15.00"), 100, null, true));
        produtoService.salvar(new Produto(null, "CVD002", "Caldo de Feijão", "Caldos", new BigDecimal("18.00"), 50, null, true));
        produtoService.salvar(new Produto(null, "REF001", "Refrigerante", "Bebidas", new BigDecimal("6.00"), 200, null, true));
    }

    // Helper para converter Produto legacy para ProdutoPdv
    private ProdutoPdv toPdv(String codigo) throws SQLException {
        Produto p = produtoService.buscarPorCodigo(codigo).orElseThrow();
        return new ProdutoPdv(p.getId(), p.getCodigo(), p.getNome(), p.getCategoria(), p.getPrecoBigDecimal(), p.getEstoque());
    }

    // Helper para pagamento em dinheiro
    private Pagamento dinheiro(BigDecimal total, BigDecimal recebido) {
        return Pagamento.calcular(Pagamento.Forma.DINHEIRO, total, recebido);
    }

    // Helper para pagamento PIX
    private Pagamento pix(BigDecimal total) {
        return Pagamento.calcular(Pagamento.Forma.PIX, total, total);
    }

    // Helper para pagamento débito
    private Pagamento debito(BigDecimal total) {
        return Pagamento.calcular(Pagamento.Forma.DEBITO, total, total);
    }

    // Helper para pagamento crédito
    private Pagamento credito(BigDecimal total) {
        return Pagamento.calcular(Pagamento.Forma.CREDITO, total, total);
    }

    @Test
    @DisplayName("Deve registrar venda com pagamento em dinheiro")
    void registrarVendaDinheiro() throws Exception {
        Carrinho carrinho = new Carrinho();
        carrinho.adicionar(toPdv("CVD001"), 2); // 2x 15.00 = 30.00
        carrinho.adicionar(toPdv("REF001"), 1); // 1x 6.00 = 6.00
        // Total = 36.00

        Pagamento pagamento = dinheiro(carrinho.total(), Dinheiro.ler("50,00")); // Troco 14.00

        int vendaId = vendaService.registrarVenda(carrinho, pagamento, null, 2); // usuario 2 = operador

        assertTrue(vendaId > 0);

        // Verificar estoque baixado
        assertEquals(98, produtoService.buscarPorCodigo("CVD001").get().getEstoque());
        assertEquals(199, produtoService.buscarPorCodigo("REF001").get().getEstoque());

        // Verificar venda registrada
        Optional<VendaService.VendaResumo> venda = vendaService.buscarVenda(vendaId);
        assertTrue(venda.isPresent());
        assertEquals("DINHEIRO", venda.get().formaPagamento());
        assertEquals(0, new BigDecimal("36.00").compareTo(venda.get().total()));
        assertEquals(2, venda.get().usuarioId());
    }

    @Test
    @DisplayName("Deve registrar venda com PIX")
    void registrarVendaPix() throws Exception {
        Carrinho carrinho = new Carrinho();
        carrinho.adicionar(toPdv("CVD001"), 1);

        Pagamento pagamento = pix(carrinho.total());

        int vendaId = vendaService.registrarVenda(carrinho, pagamento, null, 2);

        Optional<VendaService.VendaResumo> venda = vendaService.buscarVenda(vendaId);
        assertTrue(venda.isPresent());
        assertEquals("PIX", venda.get().formaPagamento());
    }

    @Test
    @DisplayName("Deve registrar venda com cliente")
    void registrarVendaComCliente() throws Exception {
        // Criar cliente
        ClienteService clienteService = new ClienteService(getConexaoManager());
        ClienteService.ClienteInfo cliente = clienteService.salvar(new ClienteService.ClienteInfo(
                null, "João Silva", "12345678901", "11999999999", "joao@email.com", "Rua A, 123", true, null
        ));

        Carrinho carrinho = new Carrinho();
        carrinho.adicionar(toPdv("CVD001"), 1);

        Pagamento pagamento = debito(carrinho.total());

        int vendaId = vendaService.registrarVenda(carrinho, pagamento, cliente.id(), 2);

        Optional<VendaService.VendaResumo> venda = vendaService.buscarVenda(vendaId);
        assertTrue(venda.isPresent());
        assertEquals(cliente.id(), venda.get().clienteId());
    }

    @Test
    @DisplayName("Deve aplicar desconto no carrinho")
    void registrarVendaComDesconto() throws Exception {
        Carrinho carrinho = new Carrinho();
        carrinho.adicionar(toPdv("CVD001"), 2); // 30.00
        carrinho.desconto(new BigDecimal("5.00")); // Desconto 5.00

        Pagamento pagamento = dinheiro(carrinho.total(), Dinheiro.ler("25,00"));

        int vendaId = vendaService.registrarVenda(carrinho, pagamento, null, 2);

        Optional<VendaService.VendaResumo> venda = vendaService.buscarVenda(vendaId);
        assertTrue(venda.isPresent());
        assertEquals(0, new BigDecimal("30.00").compareTo(venda.get().subtotal()));
        assertEquals(0, new BigDecimal("5.00").compareTo(venda.get().desconto()));
        assertEquals(0, new BigDecimal("25.00").compareTo(venda.get().total()));
    }

    @Test
    @DisplayName("Deve rejeitar carrinho vazio")
    void rejeitarCarrinhoVazio() {
        Carrinho carrinho = new Carrinho();
        Pagamento pagamento = dinheiro(BigDecimal.TEN, Dinheiro.ler("10,00"));

        assertThrows(IllegalStateException.class, () -> vendaService.registrarVenda(carrinho, pagamento, null, 2));
    }

    @Test
    @DisplayName("Deve rejeitar estoque insuficiente")
    void rejeitarEstoqueInsuficiente() throws Exception {
        Carrinho carrinho = new Carrinho();
        // Tentar vender mais do que tem no estoque (100)
        carrinho.adicionar(toPdv("CVD001"), 150);

        Pagamento pagamento = dinheiro(carrinho.total(), Dinheiro.ler("2250,00"));

        assertThrows(Exception.class, () -> vendaService.registrarVenda(carrinho, pagamento, null, 2));
    }

    @Test
    @DisplayName("Deve cancelar venda (estorno de estoque)")
    void cancelarVenda() throws Exception {
        Carrinho carrinho = new Carrinho();
        carrinho.adicionar(toPdv("CVD001"), 3); // Estoque era 100 -> 97
        carrinho.adicionar(toPdv("REF001"), 2); // Estoque era 200 -> 198

        Pagamento pagamento = debito(carrinho.total());
        int vendaId = vendaService.registrarVenda(carrinho, pagamento, null, 2);

        // Verificar estoque baixado
        assertEquals(97, produtoService.buscarPorCodigo("CVD001").get().getEstoque());
        assertEquals(198, produtoService.buscarPorCodigo("REF001").get().getEstoque());

        // Cancelar
        boolean cancelado = vendaService.cancelarVenda(vendaId, "Cliente desistiu", 1); // admin cancela

        assertTrue(cancelado);

        // Verificar estoque estornado
        assertEquals(100, produtoService.buscarPorCodigo("CVD001").get().getEstoque());
        assertEquals(200, produtoService.buscarPorCodigo("REF001").get().getEstoque());

        // Verificar status da venda
        Optional<VendaService.VendaResumo> venda = vendaService.buscarVenda(vendaId);
        assertTrue(venda.isPresent());
        assertEquals("CANCELADA", venda.get().status());
    }

    @Test
    @DisplayName("Não deve cancelar venda já cancelada")
    void naoCancelarDuasVezes() throws Exception {
        Carrinho carrinho = new Carrinho();
        carrinho.adicionar(toPdv("CVD001"), 1);
        Pagamento pagamento = debito(carrinho.total());
        int vendaId = vendaService.registrarVenda(carrinho, pagamento, null, 2);

        vendaService.cancelarVenda(vendaId, "Motivo 1", 1);
        assertThrows(IllegalStateException.class, () -> vendaService.cancelarVenda(vendaId, "Motivo 2", 1));
    }

    @Test
    @DisplayName("Deve listar vendas do dia")
    void listarVendasHoje() throws Exception {
        Carrinho c1 = new Carrinho(); c1.adicionar(toPdv("CVD001"), 1);
        Carrinho c2 = new Carrinho(); c2.adicionar(toPdv("CVD002"), 1);

        vendaService.registrarVenda(c1, dinheiro(c1.total(), Dinheiro.ler("20,00")), null, 2);
        vendaService.registrarVenda(c2, pix(c2.total()), null, 2);

        List<VendaService.VendaResumo> vendas = vendaService.listarVendas(true, 10);

        assertEquals(2, vendas.size());
    }

    @Test
    @DisplayName("Deve calcular totais do caixa")
    void calcularTotaisCaixa() throws Exception {
        // Abrir caixa
        CaixaService caixaService = new CaixaService(getConexaoManager());
        CaixaService.CaixaInfo caixa = caixaService.abrirCaixa(new BigDecimal("100.00"), 1);

        // Vendas
        Carrinho c1 = new Carrinho(); c1.adicionar(toPdv("CVD001"), 1); // 15.00 dinheiro
        Carrinho c2 = new Carrinho(); c2.adicionar(toPdv("CVD002"), 1); // 18.00 PIX
        Carrinho c3 = new Carrinho(); c3.adicionar(toPdv("REF001"), 2); // 12.00 débito

        vendaService.registrarVenda(c1, dinheiro(c1.total(), Dinheiro.ler("20,00")), null, 2);
        vendaService.registrarVenda(c2, pix(c2.total()), null, 2);
        vendaService.registrarVenda(c3, debito(c3.total()), null, 2);

        // Suprimento/retirada
        caixaService.suprimento(caixa.id(), new BigDecimal("50.00"), "Troco", 1);
        caixaService.retirada(caixa.id(), new BigDecimal("30.00"), "Pagamento fornecedor", 1);

        VendaService.TotaisCaixa totais = vendaService.calcularTotaisCaixa(caixa.id());

        assertEquals(0, new BigDecimal("15.00").compareTo(totais.totalDinheiro));
        assertEquals(0, new BigDecimal("18.00").compareTo(totais.totalPix));
        assertEquals(0, new BigDecimal("12.00").compareTo(totais.totalDebito));
        assertEquals(0, BigDecimal.ZERO.compareTo(totais.totalCredito));
        assertEquals(0, new BigDecimal("45.00").compareTo(totais.totalGeral));
        assertEquals(3, totais.quantidadeVendas);
        assertEquals(0, new BigDecimal("50.00").compareTo(totais.totalSuprimentos));
        assertEquals(0, new BigDecimal("30.00").compareTo(totais.totalRetiradas));

        // Total esperado no caixa = inicial + dinheiro + suprimentos - retiradas
        BigDecimal esperado = new BigDecimal("100.00").add(new BigDecimal("15.00")).add(new BigDecimal("50.00")).subtract(new BigDecimal("30.00"));
        assertEquals(0, esperado.compareTo(totais.getTotalEsperadoCaixa(new BigDecimal("100.00"))));
    }
}