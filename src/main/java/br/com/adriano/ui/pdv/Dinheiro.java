package br.com.adriano.ui.pdv;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public final class Dinheiro {
    public static final BigDecimal ZERO = new BigDecimal("0.00");
    private Dinheiro() { }
    public static BigDecimal valor(BigDecimal valor) { return valor.setScale(2, RoundingMode.HALF_UP); }
    public static String formatar(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(valor);
    }
    /** Aceita decimal brasileiro (12,50) ou ponto (12.50), sem separador de milhar. */
    public static BigDecimal ler(String texto) {
        String limpo = texto.strip();
        if (!limpo.matches("\\d+([,.]\\d+)?"))
            throw new IllegalArgumentException("Informe um valor positivo, por exemplo 12,50 (sem milhar).");
        return valor(new BigDecimal(limpo.replace(',', '.')));
    }
}
