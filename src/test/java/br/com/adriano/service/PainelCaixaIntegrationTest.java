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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integração do PainelCaixa - fluxo completo: abrir → vender → fechar
 * <p>
 * Valida a integração entre CaixaService, VendaService, RelatorioService
 * e o painel de UI sem depender de Swing (teste headless).
 */
@DisplayName("PainelCaixa - Integração: fluxo completo abrir → vender → fechar")
class PainelCaixaIntegrationTest extends BaseServiceTest {

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

        // Produtos
        produtoService.salvar(new Produto(null, "CVD001", "Caldo Verde", "Caldos", new BigDecimal("15.00"), 100, null, true));
        produtoService.salvar(new Produto(null, "CVD002", "Caldo de Feijão", "Caldos", new BigDecimal("18.00"), 50, null, true));
        produtoService.salvar(new Produto(null, "REF001", "Refrigerante", "Bebidas", new BigDecimal("6.00"), 200, null, true));
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

    @Test
    @DisplayName("Fluxo completo: abrir caixa → realizar vendas → suprimento/retirada → fechar caixa → verificar totais")
    void fluxoCompletoAbrirVenderFechar() throws Exception {
        // ===== 1. ABRIR CAIXA =====
        CaixaService.CaixaInfo caixaAberto = caixaService.abrirCaixa(new BigDecimal("200.00"), 1);

        assertTrue(caixaService.isCaixaAberto());
        assertEquals("ABERTO", caixaAberto.status());
        assertEquals(0, new BigDecimal("200.00").compareTo(caixaAberto.valorInicial()));

        // ===== 2. REALIZAR VENDAS (diversas formas de pagamento) =====
        // Venda 1: Dinheiro - 2x Caldo Verde = 30.00
        Carrinho c1 = new Carrinho();
        c1.adicionar(toPdv("CVD001"), 2);
        int v1 = vendaService.registrarVenda(c1, dinheiro(c1.total(), new BigDecimal("50.00")), null, 1);

        // Venda 2: PIX - 1x Caldo de Feijão = 18.00
        Carrinho c2 = new Carrinho();
        c2.adicionar(toPdv("CVD002"), 1);
        int v2 = vendaService.registrarVenda(c2, pix(c2.total()), null, 1);

        // Venda 3: Débito - 2x Refrigerante = 12.00
        Carrinho c3 = new Carrinho();
        c3.adicionar(toPdv("REF001"), 2);
        int v3 = vendaService.registrarVenda(c3, debito(c3.total()), null, 1);

        // Venda 4: Crédito - 1x Caldo Verde = 15.00
        Carrinho c4 = new Carrinho();
        c4.adicionar(toPdv("CVD001"), 1);
        int v4 = vendaService.registrarVenda(c4, credito(c4.total()), null, 1);

        // Verificar vendas registradas
        assertTrue(v1 > 0 && v2 > 0 && v3 > 0 && v4 > 0);

        // ===== 3. SUPRIMENTO E RETIRADA =====
        caixaService.suprimento(caixaAberto.id(), new BigDecimal("100.00"), "Troco adicional", 1);
        caixaService.retirada(caixaAberto.id(), new BigDecimal("50.00"), "Pagamento fornecedor", 1);

        // Verificar movimentos
        List<CaixaService.MovimentoCaixa> movimentos = caixaService.listarMovimentos(caixaAberto.id());
        assertEquals(2, movimentos.size());
        assertEquals("SUPRIMENTO", movimentos.get(0).tipo());
        assertEquals("RETIRADA", movimentos.get(1).tipo());

        // ===== 4. FECHAR CAIXA =====
        CaixaService.CaixaInfo caixaFechado = caixaService.fecharCaixa(caixaAberto.id(), 1);

        assertFalse(caixaService.isCaixaAberto());
        assertEquals("FECHADO", caixaFechado.status());
        assertNotNull(caixaFechado.dataFechamento());

        // ===== 5. VERIFICAR TOTAIS (via RelatorioService) =====
        RelatorioService.TotaisCaixa totais = relatorioService.calcularResumoFechamento(caixaAberto.id());

        // Valores esperados:
        // Dinheiro: 30.00 (venda 1)
        // PIX: 18.00 (venda 2)
        // Débito: 12.00 (venda 3)
        // Crédito: 15.00 (venda 4)
        // Total geral: 75.00
        // Suprimentos: 100.00
        // Retiradas: 50.00
        // Valor esperado no caixa: 200 + 30 + 100 - 50 = 280.00

        assertEquals(0, new BigDecimal("30.00").compareTo(totais.totalDinheiro()));
        assertEquals(0, new BigDecimal("18.00").compareTo(totais.totalPix()));
        assertEquals(0, new BigDecimal("12.00").compareTo(totais.totalDebito()));
        assertEquals(0, new BigDecimal("15.00").compareTo(totais.totalCredito()));
        assertEquals(0, new BigDecimal("75.00").compareTo(totais.totalGeral()));
        assertEquals(4, totais.quantidadeVendas());
        assertEquals(0, new BigDecimal("100.00").compareTo(totais.totalSuprimentos()));
        assertEquals(0, new BigDecimal("50.00").compareTo(totais.totalRetiradas()));
        assertEquals(0, new BigDecimal("280.00").compareTo(totais.valorEsperado()));
    }

    @Test
    @DisplayName("Não deve permitir venda com caixa fechado")
    void naoPermitirVendaComCaixaFechado() throws Exception {
        // Abrir e fechar caixa
        CaixaService.CaixaInfo caixa = caixaService.abrirCaixa(new BigDecimal("100.00"), 1);
        caixaService.fecharCaixa(caixa.id(), 1);

        // Tentar vender
        Carrinho c = new Carrinho();
        c.adicionar(toPdv("CVD001"), 1);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> vendaService.registrarVenda(c, dinheiro(c.total(), new BigDecimal("20.00")), null, 1));

        assertTrue(ex.getMessage().toLowerCase().contains("caixa"));
    }

    @Test
    @DisplayName("Não deve permitir suprimento/retirada com caixa fechado")
    void naoPermitirMovimentoComCaixaFechado() throws Exception {
        CaixaService.CaixaInfo caixa = caixaService.abrirCaixa(new BigDecimal("100.00"), 1);
        caixaService.fecharCaixa(caixa.id(), 1);

        assertThrows(IllegalStateException.class,
                () -> caixaService.suprimento(caixa.id(), new BigDecimal("50.00"), "Teste", 1));

        assertThrows(IllegalStateException.class,
                () -> caixaService.retirada(caixa.id(), new BigDecimal("50.00"), "Teste", 1));
    }

    @Test
    @DisplayName("Saldo disponível deve refletir vendas + suprimentos - retiradas")
    void saldoDisponivelCorreto() throws Exception {
        CaixaService.CaixaInfo caixa = caixaService.abrirCaixa(new BigDecimal("150.00"), 1);

        // Vendas
        Carrinho c1 = new Carrinho(); c1.adicionar(toPdv("CVD001"), 1); // 15.00 dinheiro
        Carrinho c2 = new Carrinho(); c2.adicionar(toPdv("CVD002"), 1); // 18.00 PIX
        vendaService.registrarVenda(c1, dinheiro(c1.total(), new BigDecimal("20.00")), null, 1);
        vendaService.registrarVenda(c2, pix(c2.total()), null, 1);

        // Suprimento/retirada
        caixaService.suprimento(caixa.id(), new BigDecimal("30.00"), "Troco", 1);
        caixaService.retirada(caixa.id(), new BigDecimal("20.00"), "Fornecedor", 1);

        // Saldo = 150 (inicial) + 15 (dinheiro) + 30 (suprimento) - 20 (retirada) = 175
        BigDecimal saldo = caixaService.getSaldoDisponivel(caixa.id());
        assertEquals(0, new BigDecimal("175.00").compareTo(saldo));
    }

    @Test
    @DisplayName("Exportar resumo de fechamento para CSV")
    void exportarResumoFechamentoCSV(@TempDir Path tempDir) throws Exception {
        CaixaService.CaixaInfo caixa = caixaService.abrirCaixa(new BigDecimal("100.00"), 1);

        Carrinho c = new Carrinho(); c.adicionar(toPdv("CVD001"), 2);
        vendaService.registrarVenda(c, dinheiro(c.total(), new BigDecimal("50.00")), null, 1);

        caixaService.suprimento(caixa.id(), new BigDecimal("50.00"), "Troco", 1);
        caixaService.retirada(caixa.id(), new BigDecimal("20.00"), "Fornecedor", 1);

        caixaService.fecharCaixa(caixa.id(), 1);

        // Exportar via RelatorioService - usar range mais amplo para evitar problemas de timezone
        LocalDate hoje = java.time.LocalDate.now();
        String csv = relatorioService.exportarCaixaHistoricoCSV(
                hoje.minusDays(1), hoje.plusDays(1)
        );

        assertTrue(csv.contains("100.00"));
        assertTrue(csv.contains("FECHADO"));

        // Salvar arquivo
        File arquivo = tempDir.resolve("caixa_historico.csv").toFile();
        java.nio.file.Files.writeString(arquivo.toPath(), csv);
        assertTrue(arquivo.exists());
        assertTrue(arquivo.length() > 0);
    }

    @Test
    @DisplayName("Múltiplos caixas no mesmo dia - histórico separado")
    void multiplosCaixasMesmoDia() throws Exception {
        // Caixa 1
        CaixaService.CaixaInfo caixa1 = caixaService.abrirCaixa(new BigDecimal("100.00"), 1);
        Carrinho c1 = new Carrinho(); c1.adicionar(toPdv("CVD001"), 1);
        vendaService.registrarVenda(c1, dinheiro(c1.total(), new BigDecimal("20.00")), null, 1);
        caixaService.fecharCaixa(caixa1.id(), 1);

        // Small delay to ensure different timestamps (seconds precision)
        Thread.sleep(1100);

        // Caixa 2 (novo caixa no mesmo dia)
        CaixaService.CaixaInfo caixa2 = caixaService.abrirCaixa(new BigDecimal("200.00"), 1);
        Carrinho c2 = new Carrinho(); c2.adicionar(toPdv("CVD002"), 1);
        vendaService.registrarVenda(c2, pix(c2.total()), null, 1);
        caixaService.fecharCaixa(caixa2.id(), 1);

        // Histórico deve mostrar 2 caixas - usar range mais amplo para evitar problemas de timezone
        LocalDate hoje = java.time.LocalDate.now();
        List<RelatorioService.CaixaHistorico> historico = relatorioService.historicoCaixas(
                hoje.minusDays(1), hoje.plusDays(1)
        );

        assertEquals(2, historico.size());
        // Verificar que ambos os caixas estão no histórico (ordem pode variar se timestamps iguais)
        Set<Integer> ids = historico.stream().map(RelatorioService.CaixaHistorico::id).collect(Collectors.toSet());
        assertTrue(ids.contains(caixa1.id()));
        assertTrue(ids.contains(caixa2.id()));
    }

    @Test
    @DisplayName("Cancelamento de venda deve estornar estoque e não afetar totais do caixa")
    void cancelamentoVendaNaoAfetaTotaisCaixa() throws Exception {
        CaixaService.CaixaInfo caixa = caixaService.abrirCaixa(new BigDecimal("100.00"), 1);

        // Venda
        Carrinho c = new Carrinho(); c.adicionar(toPdv("CVD001"), 2); // 30.00
        int vendaId = vendaService.registrarVenda(c, dinheiro(c.total(), new BigDecimal("50.00")), null, 1);

        // Verificar estoque baixado
        assertEquals(98, produtoService.buscarPorCodigo("CVD001").get().getEstoque());

        // Cancelar
        vendaService.cancelarVenda(vendaId, "Erro", 1);

        // Estoque estornado
        assertEquals(100, produtoService.buscarPorCodigo("CVD001").get().getEstoque());

        // Totais do caixa não devem incluir venda cancelada
        RelatorioService.TotaisCaixa totais = relatorioService.calcularResumoFechamento(caixa.id());
        assertEquals(0, BigDecimal.ZERO.compareTo(totais.totalDinheiro()));
        assertEquals(0, totais.quantidadeVendas());
    }

    @Test
    @DisplayName("Relatório de vendas detalhadas deve incluir todas as vendas do caixa")
    void relatorioVendasDetalhadasCompleto() throws Exception {
        CaixaService.CaixaInfo caixa = caixaService.abrirCaixa(new BigDecimal("100.00"), 1);

        Carrinho c1 = new Carrinho(); c1.adicionar(toPdv("CVD001"), 1);
        Carrinho c2 = new Carrinho(); c2.adicionar(toPdv("CVD002"), 1);
        vendaService.registrarVenda(c1, dinheiro(c1.total(), new BigDecimal("20.00")), null, 1);
        vendaService.registrarVenda(c2, pix(c2.total()), null, 1);

        List<RelatorioService.VendaDetalhada> rel = relatorioService.vendasDetalhadas(
                java.time.LocalDate.now(), java.time.LocalDate.now(), null, null
        );

        assertEquals(2, rel.size());
        assertTrue(rel.stream().anyMatch(v -> "DINHEIRO".equals(v.formaPagamento())));
        assertTrue(rel.stream().anyMatch(v -> "PIX".equals(v.formaPagamento())));
    }
}