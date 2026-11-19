package br.com.adriano.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CaixaService - abertura, fechamento, suprimentos e retiradas")
class CaixaServiceTest extends BaseServiceTest {

    private CaixaService service;

    @BeforeEach
    @Override
    void setUp() throws Exception {
        super.setUp();
        service = new CaixaService(getConexaoManager());
    }

    @Test
    @DisplayName("Deve retornar false quando nenhum caixa aberto")
    void caixaInicialmenteFechado() throws Exception {
        assertFalse(service.isCaixaAberto());
        assertTrue(service.getCaixaAberto().isEmpty());
    }

    @Test
    @DisplayName("Deve abrir caixa com valor inicial")
    void abrirCaixa() throws Exception {
        CaixaService.CaixaInfo caixa = service.abrirCaixa(new BigDecimal("200.00"), 1);

        assertTrue(service.isCaixaAberto());
        assertEquals(new BigDecimal("200.00"), caixa.valorInicial());
        assertEquals("ABERTO", caixa.status());
        assertEquals(1, caixa.usuarioAberturaId());
        assertNotNull(caixa.dataAbertura());
    }

    @Test
    @DisplayName("Deve rejeitar abrir caixa quando já existe um aberto")
    void rejeitarAbrirCaixaDuplicado() throws Exception {
        service.abrirCaixa(new BigDecimal("100.00"), 1);
        assertThrows(IllegalStateException.class, () -> service.abrirCaixa(new BigDecimal("50.00"), 2));
    }

    @Test
    @DisplayName("Deve rejeitar valor inicial negativo")
    void rejeitarValorInicialNegativo() {
        assertThrows(IllegalArgumentException.class, () -> service.abrirCaixa(new BigDecimal("-10.00"), 1));
    }

    @Test
    @DisplayName("Deve fechar caixa")
    void fecharCaixa() throws Exception {
        CaixaService.CaixaInfo aberto = service.abrirCaixa(new BigDecimal("150.00"), 1);
        CaixaService.CaixaInfo fechado = service.fecharCaixa(aberto.id(), 1);

        assertFalse(service.isCaixaAberto());
        assertEquals("FECHADO", fechado.status());
        assertNotNull(fechado.dataFechamento());
        assertEquals(1, fechado.usuarioFechamentoId());
    }

    @Test
    @DisplayName("Deve rejeitar fechar caixa inexistente")
    void rejeitarFecharInexistente() {
        assertThrows(IllegalStateException.class, () -> service.fecharCaixa(999, 1));
    }

    @Test
    @DisplayName("Deve registrar suprimento")
    void suprimento() throws Exception {
        CaixaService.CaixaInfo caixa = service.abrirCaixa(new BigDecimal("100.00"), 1);
        service.suprimento(caixa.id(), new BigDecimal("50.00"), "Troco extra", 1);

        List<CaixaService.MovimentoCaixa> movimentos = service.listarMovimentos(caixa.id());
        assertEquals(1, movimentos.size());
        assertEquals("SUPRIMENTO", movimentos.get(0).tipo());
        assertEquals(0, new BigDecimal("50.00").compareTo(movimentos.get(0).valor()));
    }

    @Test
    @DisplayName("Deve registrar retirada")
    void retirada() throws Exception {
        CaixaService.CaixaInfo caixa = service.abrirCaixa(new BigDecimal("200.00"), 1);
        service.retirada(caixa.id(), new BigDecimal("50.00"), "Pagamento fornecedor", 1);

        List<CaixaService.MovimentoCaixa> movimentos = service.listarMovimentos(caixa.id());
        assertEquals(1, movimentos.size());
        assertEquals("RETIRADA", movimentos.get(0).tipo());
        assertEquals(0, new BigDecimal("50.00").compareTo(movimentos.get(0).valor()));
    }

    @Test
    @DisplayName("Deve rejeitar retirada sem saldo suficiente")
    void rejeitarRetiradaSemSaldo() throws Exception {
        CaixaService.CaixaInfo caixa = service.abrirCaixa(new BigDecimal("50.00"), 1);
        assertThrows(IllegalStateException.class, () -> service.retirada(caixa.id(), new BigDecimal("100.00"), "Teste", 1));
    }

    @Test
    @DisplayName("Deve calcular saldo disponível corretamente")
    void saldoDisponivel() throws Exception {
        CaixaService.CaixaInfo caixa = service.abrirCaixa(new BigDecimal("100.00"), 1);

        // Vendas em dinheiro simuladas (inserção direta para testar cálculo)
        try (var conn = getConexaoManager().conectar();
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO vendas (data, forma_pagamento, total, status) VALUES (datetime('now'), 'DINHEIRO', 150.00, 'FINALIZADA')");
            stmt.execute("INSERT INTO vendas (data, forma_pagamento, total, status) VALUES (datetime('now'), 'PIX', 200.00, 'FINALIZADA')");
        }

        service.suprimento(caixa.id(), new BigDecimal("50.00"), "Troco", 1);
        service.retirada(caixa.id(), new BigDecimal("30.00"), "Fornecedor", 1);

        BigDecimal saldo = service.getSaldoDisponivel(caixa.id());
        // 100 (inicial) + 150 (vendas dinheiro) + 50 (suprimento) - 30 (retirada) = 270
        assertEquals(0, new BigDecimal("270.00").compareTo(saldo));
    }

    @Test
    @DisplayName("Deve listar movimentos em ordem cronológica")
    void listarMovimentosOrdenados() throws Exception {
        CaixaService.CaixaInfo caixa = service.abrirCaixa(new BigDecimal("100.00"), 1);
        service.suprimento(caixa.id(), new BigDecimal("10.00"), "A", 1);
        service.retirada(caixa.id(), new BigDecimal("20.00"), "B", 1);
        service.suprimento(caixa.id(), new BigDecimal("30.00"), "C", 1);

        List<CaixaService.MovimentoCaixa> movimentos = service.listarMovimentos(caixa.id());
        assertEquals(3, movimentos.size());
        assertEquals("SUPRIMENTO", movimentos.get(0).tipo());
        assertEquals("RETIRADA", movimentos.get(1).tipo());
        assertEquals("SUPRIMENTO", movimentos.get(2).tipo());
    }
}