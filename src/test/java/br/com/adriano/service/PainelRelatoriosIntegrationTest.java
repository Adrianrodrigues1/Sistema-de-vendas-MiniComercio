package br.com.adriano.service;

import br.com.adriano.db.ConexaoManager;
import br.com.adriano.model.Produto;
import br.com.adriano.service.CaixaService;
import br.com.adriano.service.ProdutoService;
import br.com.adriano.service.RelatorioService;
import br.com.adriano.service.VendaService;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integração do PainelRelatórios - filtros + exportação
 * <p>
 * Valida os 8 tipos de relatórios com filtros (período, operador, forma pagamento)
 * e exportação CSV/PDF.
 */
@DisplayName("PainelRelatorios - Integração: filtros + exportação CSV")
class PainelRelatoriosIntegrationTest extends BaseServiceTest {

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

        // Produtos variados
        produtoService.salvar(new Produto(null, "CVD001", "Caldo Verde", "Caldos", new BigDecimal("15.00"), 100, null, true));
        produtoService.salvar(new Produto(null, "CVD002", "Caldo de Feijão", "Caldos", new BigDecimal("18.00"), 50, null, true));
        produtoService.salvar(new Produto(null, "REF001", "Refrigerante", "Bebidas", new BigDecimal("6.00"), 200, null, true));
        produtoService.salvar(new Produto(null, "SUCO01", "Suco Natural", "Bebidas", new BigDecimal("8.00"), 80, null, true));
        produtoService.salvar(new Produto(null, "SOB001", "Pudim", "Sobremesas", new BigDecimal("12.00"), 30, null, true));

        // Criar vendas variadas
        criarCenarioVendas();
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

    private Pagamento credito(BigDecimal total) {
        return Pagamento.calcular(Pagamento.Forma.CREDITO, total, total);
    }

    private void criarCenarioVendas() throws Exception {
        // Vendas do operador 1 (admin)
        Carrinho c1 = new Carrinho(); c1.adicionar(toPdv("CVD001"), 2); // 30.00 dinheiro
        vendaService.registrarVenda(c1, dinheiro(c1.total(), new BigDecimal("50.00")), null, 1);

        Carrinho c2 = new Carrinho(); c2.adicionar(toPdv("CVD002"), 1); // 18.00 PIX
        vendaService.registrarVenda(c2, pix(c2.total()), null, 1);

        Carrinho c3 = new Carrinho(); c3.adicionar(toPdv("REF001"), 3); // 18.00 débito
        vendaService.registrarVenda(c3, debito(c3.total()), null, 1);

        Carrinho c4 = new Carrinho(); c4.adicionar(toPdv("SUCO01"), 2); // 16.00 crédito
        vendaService.registrarVenda(c4, credito(c4.total()), null, 1);

        // Suprimento e retirada
        caixaService.suprimento(1, new BigDecimal("50.00"), "Troco", 1);
        caixaService.retirada(1, new BigDecimal("20.00"), "Fornecedor", 1);
    }

    // ============= TESTES DE FILTROS =============

    @Test
    @DisplayName("Filtro por período: apenas hoje")
    void filtroPeriodoHoje() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.VendaPorPeriodo> rel = relatorioService.vendasPorPeriodo(hoje, hoje, null, null);

        assertEquals(1, rel.size()); // 1 dia
        assertEquals(4, rel.get(0).qtdVendas());
        assertEquals(0, new BigDecimal("82.00").compareTo(rel.get(0).total())); // 30+18+18+16
    }

    @Test
    @DisplayName("Filtro por operador: apenas vendas do operador 1")
    void filtroOperador() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.VendaPorPeriodo> rel = relatorioService.vendasPorPeriodo(hoje, hoje, 1, null);

        assertEquals(4, rel.get(0).qtdVendas());
    }

    @Test
    @DisplayName("Filtro por forma de pagamento: apenas DINHEIRO")
    void filtroFormaPagamento() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.VendaPorPeriodo> rel = relatorioService.vendasPorPeriodo(hoje, hoje, null, "DINHEIRO");

        assertEquals(0, new BigDecimal("30.00").compareTo(rel.get(0).total()));
        assertEquals(0, BigDecimal.ZERO.compareTo(rel.get(0).totalPix()));
    }

    @Test
    @DisplayName("Filtro combinado: operador + forma de pagamento")
    void filtroCombinado() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.VendaDetalhada> rel = relatorioService.vendasDetalhadas(hoje, hoje, 1, "PIX");

        assertEquals(1, rel.size());
        assertEquals("PIX", rel.get(0).formaPagamento());
        assertEquals(0, new BigDecimal("18.00").compareTo(rel.get(0).total()));
    }

    @Test
    @DisplayName("Filtro por período de múltiplos dias")
    void filtroMultiplosDias() throws SQLException {
        LocalDate hoje = LocalDate.now();
        LocalDate ontem = hoje.minusDays(1);

        List<RelatorioService.VendaPorPeriodo> rel = relatorioService.vendasPorPeriodo(ontem, hoje, null, null);

        // Deve retornar 1 ou 2 dias dependendo se ontem tem dados
        assertTrue(rel.size() >= 1);
        // Hoje deve ter 4 vendas
        RelatorioService.VendaPorPeriodo hojeRel = rel.stream()
                .filter(r -> r.data().equals(hoje.toString()))
                .findFirst()
                .orElseThrow();
        assertEquals(4, hojeRel.qtdVendas());
    }

    // ============= TESTES DOS 8 RELATÓRIOS COM FILTROS =============

    @Test
    @DisplayName("1. Vendas por período com filtro de operador")
    void relatorio1VendasPeriodoComFiltroOperador() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.VendaPorPeriodo> rel = relatorioService.vendasPorPeriodo(hoje, hoje, 1, null);

        assertEquals(1, rel.size());
        assertEquals(4, rel.get(0).qtdVendas());
        // Verificar breakdown por forma de pagamento
        assertEquals(0, new BigDecimal("30.00").compareTo(rel.get(0).totalDinheiro()));
        assertEquals(0, new BigDecimal("18.00").compareTo(rel.get(0).totalPix()));
        assertEquals(0, new BigDecimal("18.00").compareTo(rel.get(0).totalDebito()));
        assertEquals(0, new BigDecimal("16.00").compareTo(rel.get(0).totalCredito()));
    }

    @Test
    @DisplayName("2. Vendas detalhadas com filtro de forma pagamento")
    void relatorio2VendasDetalhadasFiltroForma() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.VendaDetalhada> rel = relatorioService.vendasDetalhadas(hoje, hoje, null, "CREDITO");

        assertEquals(1, rel.size());
        assertEquals("CREDITO", rel.get(0).formaPagamento());
        assertEquals(0, new BigDecimal("16.00").compareTo(rel.get(0).total()));
    }

    @Test
    @DisplayName("3. Produtos mais vendidos - top 3")
    void relatorio3ProdutosMaisVendidosTop3() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.ProdutoVendido> rel = relatorioService.produtosMaisVendidos(hoje, hoje, 3);

        assertEquals(3, rel.size());
        // Ordenado por qtd_vendida desc
        // REF001: 3 unidades (1º)
        // CVD001: 2 unidades (2º)
        // SUCO01: 2 unidades (3º - empate, ordem por total)
        assertEquals("REF001", rel.get(0).codigo());
        assertEquals(3, rel.get(0).quantidadeVendida());
    }

    @Test
    @DisplayName("4. Vendas por operador")
    void relatorio4VendasPorOperador() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.VendasPorOperador> rel = relatorioService.vendasPorOperador(hoje, hoje);

        assertEquals(1, rel.size());
        RelatorioService.VendasPorOperador op = rel.get(0);
        assertEquals(1, op.usuarioId());
        assertEquals(4, op.qtdVendas());
        assertEquals(0, new BigDecimal("82.00").compareTo(op.totalVendas()));
        assertEquals(0, new BigDecimal("30.00").compareTo(op.totalDinheiro()));
        assertEquals(0, new BigDecimal("18.00").compareTo(op.totalPix()));
        assertEquals(0, new BigDecimal("18.00").compareTo(op.totalDebito()));
        assertEquals(0, new BigDecimal("16.00").compareTo(op.totalCredito()));
    }

    @Test
    @DisplayName("5. Vendas por forma de pagamento")
    void relatorio5VendasPorFormaPagamento() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.VendasPorFormaPagamento> rel = relatorioService.vendasPorFormaPagamento(hoje, hoje);

        assertEquals(4, rel.size());
        // DINHEIRO: 30.00 (maior)
        assertEquals("DINHEIRO", rel.get(0).formaPagamento());
        assertEquals(0, new BigDecimal("30.00").compareTo(rel.get(0).totalVendas()));
        // CREDITO: 16.00 (menor)
        assertEquals("CREDITO", rel.get(3).formaPagamento());
        assertEquals(0, new BigDecimal("16.00").compareTo(rel.get(3).totalVendas()));
        // DEBITO e PIX ambos 18.00 - ordem não determinística
        Set<String> formasMeio = rel.stream()
                .map(RelatorioService.VendasPorFormaPagamento::formaPagamento)
                .collect(Collectors.toSet());
        assertTrue(formasMeio.containsAll(Set.of("PIX", "DEBITO")));
    }

    @Test
    @DisplayName("6. Cancelamentos no período")
    void relatorio6Cancelamentos() throws SQLException {
        LocalDate hoje = LocalDate.now();

        // Fazer e cancelar uma venda
        Carrinho c = new Carrinho(); c.adicionar(toPdv("CVD001"), 1);
        int vendaId = vendaService.registrarVenda(c, dinheiro(c.total(), new BigDecimal("20.00")), null, 1);
        vendaService.cancelarVenda(vendaId, "Teste cancelamento", 1);

        List<RelatorioService.CancelamentoInfo> rel = relatorioService.cancelamentos(hoje, hoje);

        assertEquals(1, rel.size());
        assertEquals("Teste cancelamento", rel.get(0).cancelamentoMotivo());
    }

    @Test
    @DisplayName("7. Histórico de caixas")
    void relatorio7HistoricoCaixas() throws SQLException {
        LocalDate hoje = LocalDate.now();
        // Usar range mais amplo para evitar problemas de timezone no SQLite date()
        List<RelatorioService.CaixaHistorico> rel = relatorioService.historicoCaixas(hoje.minusDays(1), hoje.plusDays(1));

        assertEquals(1, rel.size());
        assertEquals("ABERTO", rel.get(0).status());
        assertEquals(0, new BigDecimal("100.00").compareTo(rel.get(0).valorInicial()));
    }

    @Test
    @DisplayName("8. Resumo de fechamento (TotaisCaixa)")
    void relatorio8ResumoFechamento() throws SQLException {
        RelatorioService.TotaisCaixa totais = relatorioService.calcularResumoFechamento(1);

        assertEquals(1, totais.caixaId());
        assertEquals(4, totais.quantidadeVendas());
        assertEquals(0, new BigDecimal("82.00").compareTo(totais.totalGeral()));
        assertEquals(0, new BigDecimal("50.00").compareTo(totais.totalSuprimentos()));
        assertEquals(0, new BigDecimal("20.00").compareTo(totais.totalRetiradas()));
        // Esperado: 100 + 30 (dinheiro) + 50 (suprimento) - 20 (retirada) = 160
        assertEquals(0, new BigDecimal("160.00").compareTo(totais.valorEsperado()));
    }

    // ============= TESTES DE EXPORTAÇÃO CSV =============

    @Test
    @DisplayName("Exportar CSV - Vendas detalhadas")
    void exportarCSVVendasDetalhadas(@TempDir Path tempDir) throws SQLException, java.io.IOException {
        LocalDate hoje = LocalDate.now();
        String csv = relatorioService.exportarVendasCSV(hoje, hoje, null, null);

        File arquivo = tempDir.resolve("vendas.csv").toFile();
        java.nio.file.Files.writeString(arquivo.toPath(), csv);

        assertTrue(arquivo.exists());
        String conteudo = java.nio.file.Files.readString(arquivo.toPath());

        // Header + 4 vendas = 5 linhas
        assertEquals(5, conteudo.split("\n").length);
        assertTrue(conteudo.contains("ID,Número,Data,Hora,Forma Pagamento"));
        assertTrue(conteudo.contains("DINHEIRO"));
        assertTrue(conteudo.contains("PIX"));
        assertTrue(conteudo.contains("DEBITO"));
        assertTrue(conteudo.contains("CREDITO"));
    }

    @Test
    @DisplayName("Exportar CSV - Produtos mais vendidos")
    void exportarCSVProdutosMaisVendidos(@TempDir Path tempDir) throws SQLException, java.io.IOException {
        LocalDate hoje = LocalDate.now();
        String csv = relatorioService.exportarProdutosMaisVendidosCSV(hoje, hoje, 10);

        File arquivo = tempDir.resolve("produtos_mais_vendidos.csv").toFile();
        java.nio.file.Files.writeString(arquivo.toPath(), csv);

        assertTrue(arquivo.exists());
        String conteudo = java.nio.file.Files.readString(arquivo.toPath());

        assertTrue(conteudo.contains("ID,Código,Nome,Categoria,Qtd Vendida,Total Vendido,Preço Médio"));
        assertTrue(conteudo.contains("REF001"));
        assertTrue(conteudo.contains("Refrigerante"));
    }

    @Test
    @DisplayName("Exportar CSV - Vendas por operador")
    void exportarCSVVendasPorOperador(@TempDir Path tempDir) throws SQLException, java.io.IOException {
        LocalDate hoje = LocalDate.now();
        String csv = relatorioService.exportarVendasPorOperadorCSV(hoje, hoje);

        File arquivo = tempDir.resolve("vendas_por_operador.csv").toFile();
        java.nio.file.Files.writeString(arquivo.toPath(), csv);

        assertTrue(arquivo.exists());
        String conteudo = java.nio.file.Files.readString(arquivo.toPath());

        assertTrue(conteudo.contains("Operador,Perfil,Qtd Vendas,Total,Dinheiro,PIX,Débito,Crédito"));
        assertTrue(conteudo.contains("ADMIN")); // perfil do usuário 1 no banco
    }

    @Test
    @DisplayName("Exportar CSV - Histórico de caixas")
    void exportarCSVHistoricoCaixas(@TempDir Path tempDir) throws SQLException, java.io.IOException {
        LocalDate hoje = LocalDate.now();
        // Usar range mais amplo para evitar problemas de timezone no SQLite date()
        String csv = relatorioService.exportarCaixaHistoricoCSV(hoje.minusDays(1), hoje.plusDays(1));

        File arquivo = tempDir.resolve("historico_caixas.csv").toFile();
        java.nio.file.Files.writeString(arquivo.toPath(), csv);

        assertTrue(arquivo.exists());
        String conteudo = java.nio.file.Files.readString(arquivo.toPath());

        assertTrue(conteudo.contains("ID,Abertura,Fechamento,Valor Inicial,Status,Abertura por,Fechamento por"));
        assertTrue(conteudo.contains("100.00"));
        assertTrue(conteudo.contains("ABERTO"));
    }

    @Test
    @DisplayName("Exportar CSV com filtros aplicados")
    void exportarCSVComFiltros(@TempDir Path tempDir) throws SQLException, java.io.IOException {
        LocalDate hoje = LocalDate.now();
        // Exportar apenas vendas PIX
        String csv = relatorioService.exportarVendasCSV(hoje, hoje, 1, "PIX");

        File arquivo = tempDir.resolve("vendas_pix.csv").toFile();
        java.nio.file.Files.writeString(arquivo.toPath(), csv);

        String conteudo = java.nio.file.Files.readString(arquivo.toPath());
        // Header + 1 venda PIX = 2 linhas
        assertEquals(2, conteudo.split("\n").length);
        assertTrue(conteudo.contains("PIX"));
        assertFalse(conteudo.contains("DINHEIRO"));
    }

    @Test
    @DisplayName("CSV com caracteres especiais - escape correto")
    void exportarCSVEscapamentoCorreto(@TempDir Path tempDir) throws SQLException, java.io.IOException {
        // Produto com vírgula no nome
        produtoService.salvar(new Produto(null, "ESP001", "Caldo, Especial", "Caldos", new BigDecimal("20.00"), 10, null, true));

        Carrinho c = new Carrinho(); c.adicionar(toPdv("ESP001"), 1);
        vendaService.registrarVenda(c, dinheiro(c.total(), new BigDecimal("30.00")), null, 1);

        LocalDate hoje = LocalDate.now();
        // Usar exportarProdutosMaisVendidosCSV que inclui nomes de produtos
        String csv = relatorioService.exportarProdutosMaisVendidosCSV(hoje, hoje, 10);

        File arquivo = tempDir.resolve("produtos_especial.csv").toFile();
        java.nio.file.Files.writeString(arquivo.toPath(), csv);

        String conteudo = java.nio.file.Files.readString(arquivo.toPath());
        // Deve conter aspas ao redor do nome com vírgula
        assertTrue(conteudo.contains("\"Caldo, Especial\""));
    }

    // ============= TESTES DE PERFORMANCE/CONSISTÊNCIA =============

    @Test
    @DisplayName("Relatórios consistentes: soma dos detalhes = agregado por período")
    void consistenciaDetalheVsAgregado() throws SQLException {
        LocalDate hoje = LocalDate.now();

        // Agregado
        List<RelatorioService.VendaPorPeriodo> agregado = relatorioService.vendasPorPeriodo(hoje, hoje, null, null);
        // Detalhado
        List<RelatorioService.VendaDetalhada> detalhe = relatorioService.vendasDetalhadas(hoje, hoje, null, null);

        BigDecimal totalAgregado = agregado.stream()
                .map(RelatorioService.VendaPorPeriodo::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDetalhe = detalhe.stream()
                .map(RelatorioService.VendaDetalhada::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, totalAgregado.compareTo(totalDetalhe));
        assertEquals(agregado.get(0).qtdVendas(), detalhe.size());
    }

    @Test
    @DisplayName("Relatório de produtos mais vendidos - quantidades condizem com itens_venda")
    void consistenciaProdutosMaisVendidos() throws SQLException {
        LocalDate hoje = LocalDate.now();
        List<RelatorioService.ProdutoVendido> rel = relatorioService.produtosMaisVendidos(hoje, hoje, 10);

        // Verificar REF001: 3 unidades vendidas
        RelatorioService.ProdutoVendido ref = rel.stream()
                .filter(p -> "REF001".equals(p.codigo()))
                .findFirst()
                .orElseThrow();

        assertEquals(3, ref.quantidadeVendida());
        assertEquals(0, new BigDecimal("18.00").compareTo(ref.totalVendido())); // 3 * 6.00
        assertEquals(0, new BigDecimal("6.00").compareTo(ref.precoMedio()));
    }
}