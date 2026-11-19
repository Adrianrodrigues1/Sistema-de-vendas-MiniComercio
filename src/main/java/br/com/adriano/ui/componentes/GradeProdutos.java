package br.com.adriano.ui.componentes;

import javax.swing.*;
import java.awt.*;

/** Grade que acompanha o viewport, de uma a quatro colunas, sem rolagem horizontal. */
public final class GradeProdutos extends JPanel implements Scrollable {
    public static final int ALTURA_CARTAO = 248;
    public static final int LARGURA_MINIMA = 166;
    public static final int ESPACO = 12;
    public GradeProdutos() { setOpaque(false); setLayout(new GradeLayout()); }
    public int colunas(int largura) { return Math.max(1, Math.min(4, (largura + ESPACO) / (LARGURA_MINIMA + ESPACO))); }
    @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(720, 520); }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 24; }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return ALTURA_CARTAO; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
    private final class GradeLayout implements LayoutManager {
        public void addLayoutComponent(String name, Component comp) { }
        public void removeLayoutComponent(Component comp) { }
        public Dimension preferredLayoutSize(Container parent) {
            int largura = getWidth() > 0 ? getWidth() : 720;
            int linhas = (parent.getComponentCount() + colunas(largura) - 1) / colunas(largura);
            return new Dimension(largura, Math.max(0, linhas * (ALTURA_CARTAO + ESPACO) - ESPACO));
        }
        public Dimension minimumLayoutSize(Container parent) { return new Dimension(LARGURA_MINIMA, ALTURA_CARTAO); }
        public void layoutContainer(Container parent) {
            int n = colunas(parent.getWidth());
            int largura = Math.max(0, (parent.getWidth() - ESPACO * (n - 1)) / n);
            for (int i = 0; i < parent.getComponentCount(); i++)
                parent.getComponent(i).setBounds((i % n) * (largura + ESPACO), (i / n) * (ALTURA_CARTAO + ESPACO), largura, ALTURA_CARTAO);
        }
    }
}
