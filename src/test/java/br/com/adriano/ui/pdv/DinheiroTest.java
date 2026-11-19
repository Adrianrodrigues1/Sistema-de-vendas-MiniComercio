package br.com.adriano.ui.pdv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Dinheiro - utilitários monetários")
class DinheiroTest {

    @Nested
    @DisplayName("Formatação")
    class Formatacao {
        @Test
        @DisplayName("Deve formatar valor em reais brasileiro")
        void formatarReais() {
            // NumberFormat pode usar espaço não separável (U+00A0) entre símbolo e valor
            String fmt19 = Dinheiro.formatar(new BigDecimal("19.40"));
            String fmt0 = Dinheiro.formatar(BigDecimal.ZERO);
            String fmt1234 = Dinheiro.formatar(new BigDecimal("1234.56"));

            assertTrue(fmt19.contains("19,40") && fmt19.contains("R$"));
            assertTrue(fmt0.contains("0,00") && fmt0.contains("R$"));
            assertTrue(fmt1234.contains("1.234,56") && fmt1234.contains("R$"));
        }

        @Test
        @DisplayName("Deve usar locale pt-BR")
        void usarLocalePtBr() {
            String formatado = Dinheiro.formatar(new BigDecimal("1234.56"));
            assertTrue(formatado.contains(",")); // separador decimal brasileiro
            assertTrue(formatado.contains(".")); // separador de milhar brasileiro
        }
    }

    @Nested
    @DisplayName("Leitura/parsing")
    class Leitura {
        @Test
        @DisplayName("Deve ler valor com vírgula decimal")
        void lerComVirgula() {
            assertEquals(new BigDecimal("12.50"), Dinheiro.ler("12,50"));
            assertEquals(new BigDecimal("19.40"), Dinheiro.ler("19,40"));
        }

        @Test
        @DisplayName("Deve ler valor com ponto decimal")
        void lerComPonto() {
            assertEquals(new BigDecimal("12.50"), Dinheiro.ler("12.50"));
            assertEquals(new BigDecimal("19.40"), Dinheiro.ler("19.40"));
        }

        @Test
        @DisplayName("Deve ler valor inteiro")
        void lerInteiro() {
            assertEquals(new BigDecimal("10.00"), Dinheiro.ler("10"));
            assertEquals(new BigDecimal("100.00"), Dinheiro.ler("100"));
        }

        @Test
        @DisplayName("Deve normalizar para 2 casas decimais")
        void normalizarDuasCasas() {
            assertEquals(new BigDecimal("12.50"), Dinheiro.ler("12,5"));
            assertEquals(new BigDecimal("12.50"), Dinheiro.ler("12.500"));
        }

        @Test
        @DisplayName("Deve rejeitar formatos inválidos")
        void rejeitarInvalidos() {
            assertThrows(IllegalArgumentException.class, () -> Dinheiro.ler("abc"));
            assertThrows(IllegalArgumentException.class, () -> Dinheiro.ler("12,34,56"));
            assertThrows(IllegalArgumentException.class, () -> Dinheiro.ler("-10,00"));
            assertThrows(IllegalArgumentException.class, () -> Dinheiro.ler(""));
            assertThrows(IllegalArgumentException.class, () -> Dinheiro.ler("   "));
        }
    }

    @Nested
    @DisplayName("Normalização de valor (valor)")
    class Normalizacao {
        @Test
        @DisplayName("Deve arredondar para 2 casas com HALF_UP")
        void arredondarHalfUp() {
            assertEquals(new BigDecimal("12.35"), Dinheiro.valor(new BigDecimal("12.345")));
            assertEquals(new BigDecimal("12.34"), Dinheiro.valor(new BigDecimal("12.344")));
            assertEquals(new BigDecimal("12.35"), Dinheiro.valor(new BigDecimal("12.346")));
        }

        @Test
        @DisplayName("Deve preservar zeros à direita")
        void preservarZeros() {
            assertEquals(new BigDecimal("10.00"), Dinheiro.valor(new BigDecimal("10")));
            assertEquals(new BigDecimal("10.50"), Dinheiro.valor(new BigDecimal("10.5")));
        }
    }
}