package br.com.adriano.ui.pdv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pagamento - cálculo de troco e validações")
class PagamentoTest {

    private static final BigDecimal TOTAL = new BigDecimal("43.10");

    @Nested
    @DisplayName("Dinheiro com troco")
    class Dinheiro {
        @Test
        @DisplayName("Deve calcular troco corretamente")
        void calcularTroco() {
            Pagamento pgto = Pagamento.calcular(Pagamento.Forma.DINHEIRO, TOTAL, new BigDecimal("50.00"));

            assertEquals(Pagamento.Forma.DINHEIRO, pgto.forma());
            assertEquals(new BigDecimal("43.10"), pgto.total());
            assertEquals(new BigDecimal("50.00"), pgto.recebido());
            assertEquals(new BigDecimal("6.90"), pgto.troco());
        }

        @Test
        @DisplayName("Deve aceitar pagamento exato (troco zero)")
        void pagamentoExato() {
            Pagamento pgto = Pagamento.calcular(Pagamento.Forma.DINHEIRO, TOTAL, TOTAL);

            assertEquals(0, pgto.troco().signum());
        }

        @Test
        @DisplayName("Deve rejeitar valor recebido menor que total")
        void rejeitarValorInsuficiente() {
            assertThrows(IllegalArgumentException.class, () ->
                Pagamento.calcular(Pagamento.Forma.DINHEIRO, TOTAL, new BigDecimal("40.00")));
        }
    }

    @Nested
    @DisplayName("PIX, Débito, Crédito - sem troco")
    class SemTroco {
        @Test
        @DisplayName("Deve exigir valor exato para PIX")
        void pixValorExato() {
            Pagamento pgto = Pagamento.calcular(Pagamento.Forma.PIX, TOTAL, TOTAL);
            assertEquals(0, pgto.troco().signum());
        }

        @Test
        @DisplayName("Deve exigir valor exato para Débito")
        void debitoValorExato() {
            Pagamento pgto = Pagamento.calcular(Pagamento.Forma.DEBITO, TOTAL, TOTAL);
            assertEquals(0, pgto.troco().signum());
        }

        @Test
        @DisplayName("Deve exigir valor exato para Crédito")
        void creditoValorExato() {
            Pagamento pgto = Pagamento.calcular(Pagamento.Forma.CREDITO, TOTAL, TOTAL);
            assertEquals(0, pgto.troco().signum());
        }

        @Test
        @DisplayName("Deve rejeitar troco não-zero para formas não-dinheiro")
        void rejeitarTrocoNaoDinheiro() {
            // Pagamento.calcular não permite passar recebido diferente de total para não-dinheiro
            // O construtor valida isso
            assertThrows(IllegalArgumentException.class, () ->
                new Pagamento(Pagamento.Forma.PIX, TOTAL, TOTAL, new BigDecimal("5.00")));
            assertThrows(IllegalArgumentException.class, () ->
                new Pagamento(Pagamento.Forma.DEBITO, TOTAL, TOTAL, new BigDecimal("5.00")));
            assertThrows(IllegalArgumentException.class, () ->
                new Pagamento(Pagamento.Forma.CREDITO, TOTAL, TOTAL, new BigDecimal("5.00")));
        }
    }

    @Nested
    @DisplayName("Valores persistidos (compatibilidade v1.0)")
    class ValoresPersistidos {
        @Test
        @DisplayName("Deve manter rótulos originais do v1.0")
        void rotulosOriginais() {
            assertEquals("Dinheiro", Pagamento.Forma.DINHEIRO.valorPersistido());
            assertEquals("PIX", Pagamento.Forma.PIX.valorPersistido());
            assertEquals("Cartão de Débito", Pagamento.Forma.DEBITO.valorPersistido());
            assertEquals("Cartão de Crédito", Pagamento.Forma.CREDITO.valorPersistido());
        }

        @Test
        @DisplayName("Deve ter rótulos curtos para botões")
        void rotulosCurtos() {
            assertEquals("Dinheiro", Pagamento.Forma.DINHEIRO.toString());
            assertEquals("PIX", Pagamento.Forma.PIX.toString());
            assertEquals("Débito", Pagamento.Forma.DEBITO.toString());
            assertEquals("Crédito", Pagamento.Forma.CREDITO.toString());
        }
    }

    @Nested
    @DisplayName("Construtor direto")
    class Construtor {
        @Test
        @DisplayName("Deve validar troco correto para dinheiro")
        void validarTrocoDinheiro() {
            BigDecimal total = new BigDecimal("43.10");
            BigDecimal recebido = new BigDecimal("50.00");
            BigDecimal troco = new BigDecimal("6.90");
            Pagamento pgto = new Pagamento(Pagamento.Forma.DINHEIRO, total, recebido, troco);
            assertEquals(troco, pgto.troco());
        }

        @Test
        @DisplayName("Deve rejeitar troco incorreto")
        void rejeitarTrocoIncorreto() {
            assertThrows(IllegalArgumentException.class, () ->
                new Pagamento(Pagamento.Forma.DINHEIRO, TOTAL, new BigDecimal("50.00"), new BigDecimal("5.00")));
        }
    }
}