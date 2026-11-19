package br.com.adriano.ui.componentes;

import javax.swing.*;
import java.awt.*;
import java.util.function.IntConsumer;

public final class SeletorQuantidade extends JPanel {
    public SeletorQuantidade(int valor, int minimo, int maximo, IntConsumer alterar) {
        super(new BorderLayout(0, 0)); setOpaque(false);
        JButton menos = Componentes.botao("−", null, () -> alterar.accept(valor - 1));
        JButton mais = Componentes.botao("+", null, () -> alterar.accept(valor + 1));
        menos.setMargin(new Insets(0, 0, 0, 0)); mais.setMargin(new Insets(0, 0, 0, 0));
        menos.setPreferredSize(new Dimension(27, 30)); mais.setPreferredSize(new Dimension(27, 30));
        menos.setEnabled(valor > minimo); mais.setEnabled(valor < maximo);
        menos.getAccessibleContext().setAccessibleName("Diminuir quantidade");
        mais.getAccessibleContext().setAccessibleName("Aumentar quantidade");
        JLabel quantidade = Componentes.texto(Integer.toString(valor), 14, true);
        quantidade.setHorizontalAlignment(SwingConstants.CENTER);
        add(menos, BorderLayout.WEST); add(quantidade, BorderLayout.CENTER); add(mais, BorderLayout.EAST);
        setPreferredSize(new Dimension(86, 30)); setMinimumSize(new Dimension(86, 30));
    }
}
