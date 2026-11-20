package br.com.adriano.ui.componentes;

import br.com.adriano.ui.tema.GerenciadorTema;
import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class Componentes {
    private Componentes() { }

    /**
     * Cria um painel com layout especificado e opcionalmente aplica role de cor de fundo.
     * Use roleFundo = "fundo", "sidebar", "painel", "painelElevado", "suave", etc.
     */
    public static JPanel painel(LayoutManager layout, String roleFundo) {
        JPanel p = new JPanel(layout);
        if (roleFundo != null) {
            GerenciadorTema.estilo(p, roleFundo, null);
        } else {
            p.setOpaque(false);
        }
        return p;
    }

    /** Painel transparente (compatibilidade) */
    public static JPanel painel(LayoutManager layout) { return painel(layout, null); }

    public static JLabel texto(String texto, int tamanho, boolean negrito) {
        JLabel label = new JLabel(texto);
        label.setFont(new Font("Segoe UI", negrito ? Font.BOLD : Font.PLAIN, tamanho));
        return GerenciadorTema.estilo(label, null, "texto");
    }

    public static JLabel secundario(String texto) { return GerenciadorTema.estilo(texto(texto, 12, false), null, "muted"); }

    /**
     * Cria botão com estilização de tema padrão (role "painelElevado"/"texto" para secundários).
     * Use {@link #primario(JButton)} para botão de destaque (bege/dourado).
     */
    public static JButton botao(String texto, Icone.Tipo icone, Runnable acao) {
        JButton b = new JButton(texto, icone == null ? null : new Icone(icone, 20));
        b.setIconTextGap(8);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> acao.run());
        b.getAccessibleContext().setAccessibleName(texto);
        // Botão secundário: fundo painelElevado, texto normal, borda sutil via UIManager
        GerenciadorTema.estilo(b, "painelElevado", "texto");
        return b;
    }

    public static void primario(JButton botao) { GerenciadorTema.estilo(botao, "primario", "sobrePrimario"); }

    public static JComponent placeholder(int altura) {
        PainelArredondado p = new PainelArredondado(new GridBagLayout(), "suave");
        JLabel label = secundario("Sem imagem");
        label.setIcon(new Icone(Icone.Tipo.IMAGEM, altura > 70 ? 40 : 24));
        label.setHorizontalTextPosition(SwingConstants.CENTER);
        label.setVerticalTextPosition(SwingConstants.BOTTOM);
        label.setIconTextGap(7); p.add(label);
        p.setPreferredSize(new Dimension(90, altura));
        p.getAccessibleContext().setAccessibleName("Produto sem imagem");
        return p;
    }

    public static void aoEditar(JTextField campo, Consumer<String> acao) {
        campo.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { acao.accept(campo.getText()); }
            public void removeUpdate(DocumentEvent e) { acao.accept(campo.getText()); }
            public void changedUpdate(DocumentEvent e) { acao.accept(campo.getText()); }
        });
    }
}
