package br.com.adriano.ui.componentes;

import br.com.adriano.ui.tema.GerenciadorTema;
import javax.swing.*;
import java.awt.*;

public class PainelArredondado extends JPanel {
    private final String papel;
    public PainelArredondado(LayoutManager layout, String papel) {
        super(layout); this.papel = papel; setOpaque(false);
    }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(GerenciadorTema.cor(papel)); g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
        g.setColor(GerenciadorTema.cor("borda")); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
        g.dispose(); super.paintComponent(graphics);
    }
}
