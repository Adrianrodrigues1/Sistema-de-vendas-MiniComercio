package br.com.adriano.ui.pdv;

import java.math.BigDecimal;
import java.util.Objects;

public record Pagamento(Forma forma, BigDecimal total, BigDecimal recebido, BigDecimal troco) {
    public enum Forma {
        DINHEIRO("Dinheiro", "Dinheiro"), PIX("PIX", "PIX"),
        DEBITO("Débito", "Cartão de Débito"), CREDITO("Crédito", "Cartão de Crédito");
        private final String rotulo;
        private final String valorPersistido;
        Forma(String rotulo, String valorPersistido) { this.rotulo = rotulo; this.valorPersistido = valorPersistido; }
        public String valorPersistido() { return valorPersistido; }
        @Override public String toString() { return rotulo; }
    }
    public Pagamento {
        Objects.requireNonNull(forma);
        total = Dinheiro.valor(Objects.requireNonNull(total));
        recebido = Dinheiro.valor(Objects.requireNonNull(recebido));
        troco = Dinheiro.valor(Objects.requireNonNull(troco));
        if (total.signum() < 0 || recebido.compareTo(total) < 0)
            throw new IllegalArgumentException("Valor pago insuficiente.");
        BigDecimal esperado = recebido.subtract(total);
        if (troco.compareTo(esperado) != 0 || (forma != Forma.DINHEIRO && esperado.signum() != 0))
            throw new IllegalArgumentException("Troco somente em dinheiro; demais formas usam o valor exato.");
    }
    public static Pagamento calcular(Forma forma, BigDecimal total, BigDecimal recebido) {
        BigDecimal pago = Dinheiro.valor(recebido);
        BigDecimal valor = Dinheiro.valor(total);
        return new Pagamento(forma, valor, pago, pago.subtract(valor));
    }
}
