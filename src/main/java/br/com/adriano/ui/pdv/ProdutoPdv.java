package br.com.adriano.ui.pdv;

import java.math.BigDecimal;
import java.util.Objects;

/** DTO independente dos models/DAOs legados. Valores da demo são fictícios. */
public record ProdutoPdv(long id, String codigo, String nome, String categoria,
                         BigDecimal preco, int estoque) {
    public ProdutoPdv {
        Objects.requireNonNull(codigo);
        Objects.requireNonNull(nome);
        Objects.requireNonNull(categoria);
        preco = Dinheiro.valor(Objects.requireNonNull(preco));
        if (preco.signum() < 0 || estoque < 0) throw new IllegalArgumentException("Preço/estoque inválido.");
    }
}
