package br.com.adriano.ui.componentes;

import javax.swing.Icon;
import java.awt.*;
import java.awt.geom.*;

/** Ícones vetoriais locais, sem imagens reais, fontes de símbolos ou dependências adicionais. */
public final class Icone implements Icon {
    public enum Tipo { CARRINHO, PRODUTO, ESTOQUE, CAIXA, RELATORIO, CLIENTE, CONFIGURACAO, BUSCA, CODIGO, LIXEIRA, IMAGEM, PREVIA, CHECK, X, DINHEIRO, PIX, CARTAO, TIGELA, SETABAIXO, SETACIMA }
    private final Tipo tipo;
    private final int tamanho;
    public Icone(Tipo tipo, int tamanho) { this.tipo = tipo; this.tamanho = tamanho; }
    @Override public int getIconWidth() { return tamanho; }
    @Override public int getIconHeight() { return tamanho; }
    @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(x, y); g.scale(tamanho / 24.0, tamanho / 24.0);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(c.getForeground());
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (tipo) {
            case CARRINHO -> { line(g, 2, 3, 5, 3); line(g, 5, 3, 8, 16); line(g, 8, 16, 20, 16); g.draw(new Rectangle2D.Double(7, 6, 14, 7)); g.drawOval(8, 19, 2, 2); g.drawOval(18, 19, 2, 2); }
            case BUSCA -> { g.drawOval(3, 3, 13, 13); line(g, 15, 15, 21, 21); }
            case CODIGO -> { for (int n : new int[]{3, 6, 8, 12, 15, 19, 21}) line(g, n, 4, n, n % 2 == 0 ? 18 : 21); }
            case LIXEIRA -> { line(g, 4, 6, 20, 6); g.drawRect(7, 6, 10, 15); line(g, 9, 3, 15, 3); line(g, 10, 10, 10, 17); line(g, 14, 10, 14, 17); }
            case CLIENTE -> { g.drawOval(8, 2, 8, 8); g.draw(new Arc2D.Double(3, 13, 18, 17, 0, 180, Arc2D.OPEN)); line(g, 3, 21, 21, 21); }
            case IMAGEM -> { g.drawRoundRect(2, 3, 20, 18, 3, 3); g.drawOval(14, 6, 4, 4); line(g, 3, 18, 9, 11); line(g, 9, 11, 15, 17); line(g, 15, 17, 18, 14); line(g, 18, 14, 21, 18); }
            case CHECK -> { line(g, 4, 12, 9, 18); line(g, 9, 18, 21, 5); }
            case X -> { line(g, 5, 5, 19, 19); line(g, 19, 5, 5, 19); }
            case DINHEIRO -> { g.drawRect(2, 6, 20, 13); g.drawOval(9, 9, 6, 7); line(g, 5, 9, 5, 12); line(g, 19, 13, 19, 16); }
            case CARTAO -> { g.drawRoundRect(2, 5, 20, 15, 3, 3); line(g, 2, 10, 22, 10); line(g, 5, 16, 10, 16); }
            case PIX -> { g.drawPolygon(new int[]{12, 21, 12, 3}, new int[]{2, 12, 22, 12}, 4); line(g, 7, 7, 17, 17); line(g, 17, 7, 7, 17); }
            case RELATORIO -> { g.drawRect(3, 14, 4, 7); g.drawRect(10, 8, 4, 13); g.drawRect(17, 3, 4, 18); }
            case PREVIA -> { g.drawRect(5, 2, 14, 20); for (int i = 7; i < 20; i += 4) line(g, 8, i, 16, i); }
            case CONFIGURACAO -> { g.drawOval(5, 5, 14, 14); g.drawOval(9, 9, 6, 6); for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; line(g, 12 + 7 * Math.cos(a), 12 + 7 * Math.sin(a), 12 + 10 * Math.cos(a), 12 + 10 * Math.sin(a)); } }
            case TIGELA -> { g.draw(new Arc2D.Double(2, 5, 20, 16, 180, 180, Arc2D.OPEN)); line(g, 2, 13, 22, 13); g.draw(new CubicCurve2D.Double(10, 10, 5, 5, 15, 5, 10, 1)); g.draw(new CubicCurve2D.Double(16, 10, 11, 5, 21, 5, 16, 1)); }
            case ESTOQUE -> { for (int i = 0; i < 3; i++) g.drawPolygon(new int[]{2, 12, 22, 12}, new int[]{6 + i * 5, 2 + i * 5, 6 + i * 5, 10 + i * 5}, 4); }
            case CAIXA -> { g.drawRoundRect(3, 10, 18, 11, 2, 2); g.drawRect(7, 3, 10, 7); line(g, 7, 16, 12, 16); g.drawOval(16, 15, 2, 2); }
            case PRODUTO -> { g.drawPolygon(new int[]{3, 12, 21, 21, 12, 3}, new int[]{6, 2, 6, 18, 22, 18}, 6); line(g, 3, 6, 12, 11); line(g, 21, 6, 12, 11); line(g, 12, 11, 12, 22); }
            case SETABAIXO -> { line(g, 12, 3, 12, 18); line(g, 6, 12, 12, 18); line(g, 18, 12, 12, 18); }
            case SETACIMA -> { line(g, 12, 21, 12, 6); line(g, 6, 12, 12, 6); line(g, 18, 12, 12, 6); }
        }
        g.dispose();
    }
    private static void line(Graphics2D g, double x, double y, double x2, double y2) { g.draw(new Line2D.Double(x, y, x2, y2)); }
}
