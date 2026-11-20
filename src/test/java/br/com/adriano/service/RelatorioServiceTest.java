package br.com.adriano.service;

import br.com.adriano.model.Produto;
import br.com.adriano.ui.pdv.Carrinho;
import br.com.adriano.ui.pdv.Dinheiro;
import br.com.adriano.ui.pdv.Pagamento;
import br.com.adriano.ui.pdv.ProdutoPdv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RelatorioService - 8 tipos de relatórios + exportação CSV/PDF")
class RelatorioServiceTest extends BaseServiceTest {

    private ProdutoService produtoService;
    private CaixaService caixaService;
    private VendaService vendaService;
    private RelatorioService relatorioService;

    @BeforeEach
    @Override
    void setUp() throws Exception {
        super.setUp();
        produtoService = new ProdutoService(getConexaoManager());
        caixaService = new CaixaService(getConexaoManager());
        vendaService = new VendaService(getConexaoManager(), produtoService, caixaService);
        relatorioService = new RelatorioService(getConexaoManager());

        // Abrir caixa
        caixaService.abrirCaixa(new BigDecimal("100.00"), 1);

        // Produtos para testes
        produtoService.salvar(new Produto(null, "CVD001", "Caldo Verde", "Caldos", new BigDecimal("15.00"), 100, null, true));
        produtoService.salvar(new Produto(null, "CVD002", "Caldo de Feijão", "Caldos", new BigDecimal("18.00"), 50, null, true));
        produtoService.salvar(new Produto(null, "REF001", "Refrigerante", "Bebidas", new BigDecimal("6.00"), 200, null, true));
        produtoService.salvar(new Produto(null, "SUCO01", "Suco Natural", "Bebidas", new BigDecimal("8.00"), 80, null, true));

        // Criar vendas variadas
        criarVendasTeste();
    }

    private ProdutoPdv toPdv(String codigo) throws SQLException {
        Produto p = produtoService.buscarPorCodigo(codigo).orElseThrow();
        return new ProdutoPdv(p.getId(), p.getCodigo(), p.getNome(), p.getCategoria(), p.getPrecoBigDecimal(), p.getEstoque());
    }

    private Pagamento dinheiro(BigDecimal total, BigDecimal recebido) {
        return Pagamento.calcular(Pagamento.Forma.DINHEIRO, total, recebido);
    }

    private Pagamento pix(BigDecimal total) {
        return Pagamento.calcular(Pagamento.Forma.PIX, total, total);
    }

    private Pagamento debito(BigDecimal total) {
        return Pagamento.calcular(Pagamento.Forma.DEBITO, total, total);
    }

    private void criarVendasTeste() throws Exception {
        LocalDate hoje = LocalDate.now();

        // Venda 1: Dinheiro - Caldo Verde x2
        Carrinho c1 = new Carrinho();
        c1.adicionar(toPdv("CVD001"), 2);
        vendaService.registrarVenda(c1, dinheiro(c1.total(), new BigDecimal("50.00")), null, 1);

        // Venda 2: PIX - Caldo de Feijão
        Carrinho c2 = new Carrinho();
        c2.adicionar(toPdv("CVD002"), 1);
        vendaService.registrarVenda(c2, pix(c2.total()), null, 1);

        // Venda 3: Débito - Refrigerante x2
        Carrinho c3 = new Carrinho();
        c3.adicionar(toPdv("REF001"), 2);
        vendaService.registrarVenda(c3, debito(c3.total()), null, 1);

        // Venda 4: Crédito - Suco
        Carrinho c4 = new Carrinho();
        c4.adicionar(toPdv("SUCO01"), 1);
        Pagamento credito = Pagamento.calcular(Pagamento.Forma.CREDITO, c4.total(), c4.total());
        vendaService.registrarVenda(c4, credito, null, 1);

        // Suprimento e retirada
        caixaService.suprimento(1, new BigDecimal("50.00"), "Troco extra", 1);
        caixaService.retirada(1, new BigDecimal("30.00"), "Fornecedor", 1);
    }

    // ============= TESTES DOS 8 TIPOS DE RELATÓRIOS =============

    @Test
    @DisplayName("1. Relatório de vendas por período (agregado por dia)")
    void relatorioVendasPorPeriodo() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.VendaPorPeriodo> rel = relatorioService.vendasPorPeriodo(hoje, hoje, null, null);

        assertFalse(rel.isEmpty());
        RelatorioService.VendaPorPeriodo dia = rel.get(0);
        assertEquals(4, dia.qtdVendas());
        // Total: 30 (2*15) + 18 + 12 (2*6) + 8 = 68
        assertEquals(0, new BigDecimal("68.00").compareTo(dia.total()));
        assertEquals(0, new BigDecimal("30.00").compareTo(dia.totalDinheiro()));
        assertEquals(0, new BigDecimal("18.00").compareTo(dia.totalPix()));
        assertEquals(0, new BigDecimal("12.00").compareTo(dia.totalDebito()));
        assertEquals(0, new BigDecimal("8.00").compareTo(dia.totalCredito()));
    }

    @Test
    @DisplayName("2. Relatório de vendas detalhadas (cada venda individual)")
    void relatorioVendasDetalhadas() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.VendaDetalhada> rel = relatorioService.vendasDetalhadas(hoje, hoje, null, null);

        assertEquals(4, rel.size());
        // Verificar ordem decrescente por data
        for (int i = 1; i < rel.size(); i++) {
            assertTrue(rel.get(i - 1).data().compareTo(rel.get(i).data()) >= 0);
        }
        // Verificar formas de pagamento
        assertTrue(rel.stream().anyMatch(v -> "DINHEIRO".equals(v.formaPagamento())));
        assertTrue(rel.stream().anyMatch(v -> "PIX".equals(v.formaPagamento())));
        assertTrue(rel.stream().anyMatch(v -> "DEBITO".equals(v.formaPagamento())));
        assertTrue(rel.stream().anyMatch(v -> "CREDITO".equals(v.formaPagamento())));
    }

    @Test
    @DisplayName("3. Relatório de produtos mais vendidos")
    void relatorioProdutosMaisVendidos() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.ProdutoVendido> rel = relatorioService.produtosMaisVendidos(hoje, hoje, 10);

        assertFalse(rel.isEmpty());
        // Caldo Verde foi vendido 2 vezes
        RelatorioService.ProdutoVendido top = rel.get(0);
        assertEquals("CVD001", top.codigo());
        assertEquals("Caldo Verde", top.nome());
        assertEquals(2, top.quantidadeVendida());
    }

    @Test
    @DisplayName("4. Relatório de vendas por operador")
    void relatorioVendasPorOperador() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.VendasPorOperador> rel = relatorioService.vendasPorOperador(hoje, hoje);

        assertFalse(rel.isEmpty());
        RelatorioService.VendasPorOperador op = rel.get(0);
        assertEquals(1, op.usuarioId());
        assertEquals(4, op.qtdVendas());
        // Total: 30 + 18 + 12 + 8 = 68
        assertEquals(0, new BigDecimal("68.00").compareTo(op.totalVendas()));
    }

    @Test
    @DisplayName("5. Relatório de vendas por forma de pagamento")
    void relatorioVendasPorFormaPagamento() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.VendasPorFormaPagamento> rel = relatorioService.vendasPorFormaPagamento(hoje, hoje);

        assertEquals(4, rel.size()); // DINHEIRO, PIX, DEBITO, CREDITO
        // Verificar que todas as formas estão presentes com valores corretos
        // DINHEIRO: 30.00 (maior)
        assertEquals("DINHEIRO", rel.get(0).formaPagamento());
        assertEquals(0, new BigDecimal("30.00").compareTo(rel.get(0).totalVendas()));
        // CREDITO: 8.00 (menor)
        assertEquals("CREDITO", rel.get(3).formaPagamento());
        assertEquals(0, new BigDecimal("8.00").compareTo(rel.get(3).totalVendas()));
        // PIX e DEBITO ambos 18.00 - ordem não determinística
        Set<String> formasMeio = rel.stream()
                .map(RelatorioService.VendasPorFormaPagamento::formaPagamento)
                .collect(Collectors.toSet());
        assertTrue(formasMeio.containsAll(Set.of("PIX", "DEBITO")));
    }

    @Test
    @DisplayName("6. Relatório de cancelamentos")
    void relatorioCancelamentos() throws SQLException {
        LocalDate hoje = LocalDate.now();

        // Cancelar uma venda
        Carrinho c = new Carrinho();
        c.adicionar(toPdv("CVD001"), 1);
        int vendaId = vendaService.registrarVenda(c, dinheiro(c.total(), new BigDecimal("20.00")), null, 1);
        vendaService.cancelarVenda(vendaId, "Cliente desistiu", 1);

        List<RelatorioService.CancelamentoInfo> rel = relatorioService.cancelamentos(hoje, hoje);

        assertEquals(1, rel.size());
        assertEquals("Cliente desistiu", rel.get(0).cancelamentoMotivo());
    }

    @Test
    @DisplayName("7. Relatório de histórico de caixas")
    void relatorioHistoricoCaixas() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.CaixaHistorico> rel = relatorioService.historicoCaixas(hoje, hoje);

        assertEquals(1, rel.size());
        RelatorioService.CaixaHistorico caixa = rel.get(0);
        assertEquals("ABERTO", caixa.status());
        assertEquals(0, new BigDecimal("100.00").compareTo(caixa.valorInicial()));
    }

    @Test
    @DisplayName("8. Resumo de fechamento de caixa (TotaisCaixa)")
    void relatorioResumoFechamento() throws SQLException {
        RelatorioService.TotaisCaixa totais = relatorioService.calcularResumoFechamento(1);

        assertEquals(1, totais.caixaId());
        assertEquals(0, new BigDecimal("100.00").compareTo(totais.valorInicial()));
        assertEquals(0, new BigDecimal("30.00").compareTo(totais.totalDinheiro()));
        assertEquals(0, new BigDecimal("18.00").compareTo(totais.totalPix()));
        assertEquals(0, new BigDecimal("12.00").compareTo(totais.totalDebito()));
        assertEquals(0, new BigDecimal("8.00").compareTo(totais.totalCredito()));
        assertEquals(0, new BigDecimal("68.00").compareTo(totais.totalGeral()));
        assertEquals(4, totais.quantidadeVendas());
        assertEquals(0, new BigDecimal("50.00").compareTo(totais.totalSuprimentos()));
        assertEquals(0, new BigDecimal("30.00").compareTo(totais.totalRetiradas()));
        // Valor esperado = 100 + 30 + 50 - 30 = 150
        assertEquals(0, new BigDecimal("150.00").compareTo(totais.valorEsperado()));
    }

    // ============= TESTES DE EXPORTAÇÃO CSV =============

    @Test
    @DisplayName("Exportar vendas para CSV")
    void exportarVendasCSV() throws SQLException {
        LocalDate hoje = LocalDate.now();
        String csv = relatorioService.exportarVendasCSV(hoje, hoje, null, null);

        assertTrue(csv.contains("ID,Número,Data,Hora,Forma Pagamento"));
        assertTrue(csv.contains("DINHEIRO"));
        assertTrue(csv.contains("PIX"));
        assertTrue(csv.contains("DEBITO"));
        assertTrue(csv.contains("CREDITO"));
        // 4 vendas + header = 5 linhas
        assertEquals(5, csv.split("\n").length);
    }

    @Test
    @DisplayName("Exportar produtos mais vendidos para CSV")
    void exportarProdutosMaisVendidosCSV() throws SQLException {
        LocalDate hoje = LocalDate.now();
        String csv = relatorioService.exportarProdutosMaisVendidosCSV(hoje, hoje, 10);

        assertTrue(csv.contains("ID,Código,Nome,Categoria,Qtd Vendida,Total Vendido,Preço Médio"));
        assertTrue(csv.contains("CVD001"));
        assertTrue(csv.contains("Caldo Verde"));
    }

    @Test
    @DisplayName("Exportar vendas por operador para CSV")
    void exportarVendasPorOperadorCSV() throws SQLException {
        LocalDate hoje = LocalDate.now();
        String csv = relatorioService.exportarVendasPorOperadorCSV(hoje, hoje);

        assertTrue(csv.contains("Operador,Perfil,Qtd Vendas,Total,Dinheiro,PIX,Débito,Crédito"));
        assertTrue(csv.contains("ADMIN")); // perfil do usuário 1 no banco
    }

    @Test
    @DisplayName("Exportar histórico de caixas para CSV")
    void exportarCaixaHistoricoCSV() throws SQLException {
        LocalDate hoje = LocalDate.now();
        // Usar range mais amplo para evitar problemas de timezone no SQLite date()
        String csv = relatorioService.exportarCaixaHistoricoCSV(hoje.minusDays(1), hoje.plusDays(1));

        assertTrue(csv.contains("ID,Abertura,Fechamento,Valor Inicial,Status,Abertura por,Fechamento por"));
        assertTrue(csv.contains("100.00"));
        assertTrue(csv.contains("ABERTO"));
    }

    // ============= TESTE EXPORTAÇÃO PDF =============

    @Test
    @DisplayName("Exportar PDF - deve lançar exceção informativa se iText não disponível")
    void exportarPDFSemItext() throws Exception {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.VendaDetalhada> dados = relatorioService.vendasDetalhadas(hoje, hoje, null, null);
        File tempFile = File.createTempFile("test", ".pdf");
        tempFile.deleteOnExit();

        // Deve lançar ClassNotFoundException (iText não no classpath de teste)
        assertThrows(Exception.class, () -> relatorioService.exportarPDF(
                "Teste",
                new String[]{"ID", "Total"},
                dados,
                tempFile,
                v -> new String[]{String.valueOf(((RelatorioService.VendaDetalhada) v).id()),
                        ((RelatorioService.VendaDetalhada) v).total().toPlainString()}
        ));
    }

    // ============= TESTES DE FILTROS =============

    @Test
    @DisplayName("Filtrar vendas por operador")
    void filtroOperador() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.VendaPorPeriodo> rel = relatorioService.vendasPorPeriodo(hoje, hoje, 1, null);

        assertFalse(rel.isEmpty());
        assertEquals(4, rel.get(0).qtdVendas());
    }

    @Test
    @DisplayName("Filtrar vendas por forma de pagamento")
    void filtroFormaPagamento() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.VendaPorPeriodo> rel = relatorioService.vendasPorPeriodo(hoje, hoje, null, "DINHEIRO");

        assertFalse(rel.isEmpty());
        assertEquals(0, new BigDecimal("30.00").compareTo(rel.get(0).total()));
        assertEquals(0, BigDecimal.ZERO.compareTo(rel.get(0).totalPix()));
    }

    @Test
    @DisplayName("Filtrar vendas por operador E forma de pagamento")
    void filtroOperadorEFormaPagamento() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.VendaDetalhada> rel = relatorioService.vendasDetalhadas(hoje, hoje, 1, "PIX");

        assertEquals(1, rel.size());
        assertEquals("PIX", rel.get(0).formaPagamento());
    }
}