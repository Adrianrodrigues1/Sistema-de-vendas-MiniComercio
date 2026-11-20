package br.com.adriano.ui.tema;

import br.com.adriano.ui.componentes.Icone;
import br.com.adriano.ui.componentes.PainelArredondado;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.Map;

/**
 * Gerenciador de temas claro/escuro para Mini Comércio 2.0.
 * <p>
 * Um único layout: alternar tema muda exclusivamente as cores.
 * Usa FlatLaf com propriedades UIManager abrangentes para garantir
 * que todos os componentes Swing respeitem a paleta.
 */
public final class GerenciadorTema {
    public enum Tema { CLARO, ESCURO }

    private static Tema atual = Tema.CLARO;

    // Paleta baseada nos protótipos aprovados
    // Tema CLARO (referência de layout) | Tema ESCURO (protótipo visual)
    private static final Map<String, String[]> CORES = Map.ofEntries(
            // Fundos - tons de grafite uniformes, sem contraste excessivo
            Map.entry("fundo",         new String[]{"#FBFAFA", "#1C1B1C"}),       // Fundo principal
            Map.entry("sidebar",       new String[]{"#F4F0F0", "#1C1B1C"}),       // Sidebar = mesmo fundo (sem linha divisória)
            Map.entry("painel",        new String[]{"#FFFFFF", "#1F1E1F"}),       // Cards/painéis elevados
            Map.entry("painelElevado", new String[]{"#F4F1EE", "#242324"}),       // Painéis levemente elevados
            Map.entry("suave",         new String[]{"#F4F1EE", "#2A282A"}),       // Áreas suaves (placeholders, hover)
            // Cores de destaque e ação - bege/dourado conforme protótipo
            Map.entry("destaque",      new String[]{"#DFD2C7", "#BC9162"}),       // Item selecionado, seleção, hover forte
            Map.entry("primario",      new String[]{"#7F7266", "#A07549"}),       // Botão primário (Finalizar), bordas foco
            // Textos
            Map.entry("texto",         new String[]{"#28231F", "#F5F2EC"}),       // Texto principal
            Map.entry("muted",         new String[]{"#70685F", "#A8A098"}),       // Texto secundário/muted (mais legível no escuro)
            // Bordas - discretas, quase invisíveis no escuro
            Map.entry("borda",         new String[]{"#E6E0DC", "#353334"}),       // Bordas sutis (era #45403C - muito clara)
            // Textos sobre cores de destaque/primário
            Map.entry("sobreDestaque", new String[]{"#28231F", "#1A1819"}),       // Texto sobre destaque (escuro, diferente de fundo)
            Map.entry("sobrePrimario", new String[]{"#FFFFFF", "#FFFFFF"}),       // Texto sobre primário (branco)
            // Status (badge caixa)
            Map.entry("status",        new String[]{"#E8F1E9", "#1E3A2E"}),       // Fundo badge (verde bem escuro)
            Map.entry("statusTexto",   new String[]{"#256036", "#8FC99E"}),       // Texto badge
            // Inputs
            Map.entry("inputFundo",    new String[]{"#FFFFFF", "#242324"}),       // Fundo inputs (era #2A282A - muito claro)
            Map.entry("inputTexto",    new String[]{"#28231F", "#F5F2EC"}),       // Texto inputs
            // Estados interativos
            Map.entry("hover",         new String[]{"#EDE6E0", "#2E2D2E"}),       // Hover sutil
            Map.entry("pressed",       new String[]{"#DFD2C7", "#3A3839"}));      // Pressed

    private GerenciadorTema() { }

    public static Tema atual() { return atual; }

    public static Color cor(String papel) {
        String[] cores = CORES.get(papel);
        if (cores == null) throw new IllegalArgumentException("Cor desconhecida: " + papel);
        return Color.decode(cores[atual == Tema.CLARO ? 0 : 1]);
    }

    /**
     * Instala o LookAndFeel FlatLaf e configura TODAS as propriedades UIManager
     * necessárias para que componentes Swing padrão respeitem a paleta.
     * Remove bordas padrão que criam linhas brancas no tema escuro.
     */
    public static void instalar(Tema tema) {
        atual = tema;
        try {
            UIManager.setLookAndFeel(tema == Tema.CLARO ? new FlatLightLaf() : new FlatDarkLaf());
        } catch (UnsupportedLookAndFeelException e) {
            throw new IllegalStateException("FlatLaf indisponível", e);
        }

        // Fonte padrão
        FontUIResource fonte = new FontUIResource("Segoe UI", Font.PLAIN, 14);
        UIManager.put("defaultFont", fonte);

        // Cantos arredondados globais
        UIManager.put("Component.arc", 14);
        UIManager.put("Button.arc", 14);
        UIManager.put("TextComponent.arc", 14);
        UIManager.put("ComboBox.arc", 14);
        UIManager.put("ScrollBar.arc", 14);
        UIManager.put("ProgressBar.arc", 14);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);

        // Cores base
        Color fundo = cor("fundo");
        Color painel = cor("painel");
        Color painelElevado = cor("painelElevado");
        Color sidebar = cor("sidebar");
        Color texto = cor("texto");
        Color muted = cor("muted");
        Color borda = cor("borda");
        Color primario = cor("primario");
        Color destaque = cor("destaque");
        Color sobrePrimario = cor("sobrePrimario");
        Color sobreDestaque = cor("sobreDestaque");
        Color inputFundo = cor("inputFundo");
        Color inputTexto = cor("inputTexto");
        Color hover = cor("hover");
        Color pressed = cor("pressed");
        Color status = cor("status");
        Color statusTexto = cor("statusTexto");

        // ===== Painéis e containers - SEM bordas padrão =====
        UIManager.put("Panel.background", fundo);
        UIManager.put("Panel.foreground", texto);
        UIManager.put("Viewport.background", fundo);
        UIManager.put("ScrollPane.background", fundo);
        UIManager.put("ScrollPane.viewportBackground", fundo);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        // Remover borda interna do JScrollPane que cria linha branca
        UIManager.put("ScrollPane.viewportBorder", BorderFactory.createEmptyBorder());

        // ===== Labels =====
        UIManager.put("Label.background", fundo);
        UIManager.put("Label.foreground", texto);
        UIManager.put("Label.disabledForeground", muted);

        // ===== Botões =====
        UIManager.put("Button.background", painel);
        UIManager.put("Button.foreground", texto);
        UIManager.put("Button.border", BorderFactory.createEmptyBorder()); // Sem borda padrão
        UIManager.put("Button.focusBorder", new LineBorder(primario, 2, true));
        UIManager.put("Button.hoverBackground", hover);
        UIManager.put("Button.pressedBackground", pressed);
        UIManager.put("Button.disabledBackground", muted);
        UIManager.put("Button.disabledForeground", muted);
        UIManager.put("Button.margin", new Insets(8, 16, 8, 16));

        // ===== ToggleButton (usado na sidebar) =====
        UIManager.put("ToggleButton.background", sidebar);
        UIManager.put("ToggleButton.foreground", texto);
        UIManager.put("ToggleButton.selectedBackground", destaque);
        UIManager.put("ToggleButton.selectedForeground", sobreDestaque);
        UIManager.put("ToggleButton.hoverBackground", hover);
        UIManager.put("ToggleButton.pressedBackground", pressed);
        UIManager.put("ToggleButton.border", BorderFactory.createEmptyBorder(10, 14, 10, 14));
        UIManager.put("ToggleButton.focusBorder", new LineBorder(primario, 2, true));
        UIManager.put("ToggleButton.textIconGap", 12);
        UIManager.put("ToggleButton.iconTextGap", 12);

        // ===== Campos de texto =====
        UIManager.put("TextField.background", inputFundo);
        UIManager.put("TextField.foreground", inputTexto);
        UIManager.put("TextField.caretForeground", primario);
        UIManager.put("TextField.selectionBackground", destaque);
        UIManager.put("TextField.selectionForeground", sobreDestaque);
        UIManager.put("TextField.border", new LineBorder(borda, 1, true));
        UIManager.put("TextField.focusBorder", new LineBorder(primario, 2, true));
        UIManager.put("TextField.margin", new Insets(8, 12, 8, 12));

        UIManager.put("PasswordField.background", inputFundo);
        UIManager.put("PasswordField.foreground", inputTexto);
        UIManager.put("PasswordField.caretForeground", primario);
        UIManager.put("PasswordField.selectionBackground", destaque);
        UIManager.put("PasswordField.selectionForeground", sobreDestaque);
        UIManager.put("PasswordField.border", new LineBorder(borda, 1, true));

        UIManager.put("TextArea.background", inputFundo);
        UIManager.put("TextArea.foreground", inputTexto);
        UIManager.put("TextArea.caretForeground", primario);
        UIManager.put("TextArea.selectionBackground", destaque);
        UIManager.put("TextArea.selectionForeground", sobreDestaque);
        UIManager.put("TextArea.border", new LineBorder(borda, 1, true));

        // ===== ComboBox =====
        UIManager.put("ComboBox.background", inputFundo);
        UIManager.put("ComboBox.foreground", inputTexto);
        UIManager.put("ComboBox.buttonBackground", painelElevado);
        UIManager.put("ComboBox.buttonArrowColor", texto);
        UIManager.put("ComboBox.selectionBackground", destaque);
        UIManager.put("ComboBox.selectionForeground", sobreDestaque);
        UIManager.put("ComboBox.border", new LineBorder(borda, 1, true));
        UIManager.put("ComboBox.focusBorder", new LineBorder(primario, 2, true));
        UIManager.put("ComboBox.popupBorder", new LineBorder(borda, 1, true));
        UIManager.put("ComboBox.rendererUseListColors", true);
        UIManager.put("ComboBox.buttonBorder", BorderFactory.createEmptyBorder());

        // ===== List =====
        UIManager.put("List.background", painel);
        UIManager.put("List.foreground", texto);
        UIManager.put("List.selectionBackground", destaque);
        UIManager.put("List.selectionForeground", sobreDestaque);
        UIManager.put("List.focusCellHighlightBorder", new LineBorder(primario, 1, true));
        UIManager.put("List.fixedCellHeight", 40);
        UIManager.put("List.cellRenderer", new javax.swing.DefaultListCellRenderer());

        // ===== Table =====
        UIManager.put("Table.background", painel);
        UIManager.put("Table.foreground", texto);
        UIManager.put("Table.gridColor", borda);
        UIManager.put("Table.selectionBackground", destaque);
        UIManager.put("Table.selectionForeground", sobreDestaque);
        UIManager.put("TableHeader.background", painelElevado);
        UIManager.put("TableHeader.foreground", texto);
        UIManager.put("TableHeader.border", BorderFactory.createEmptyBorder());
        UIManager.put("Table.intercellSpacing", new Dimension(0, 0));

        // ===== ScrollBar =====
        UIManager.put("ScrollBar.background", fundo);
        UIManager.put("ScrollBar.thumb", muted);
        UIManager.put("ScrollBar.thumbHover", texto);
        UIManager.put("ScrollBar.thumbPressed", primario);
        UIManager.put("ScrollBar.track", fundo);
        UIManager.put("ScrollBar.width", 8);
        UIManager.put("ScrollBar.arc", 4);
        UIManager.put("ScrollBar.showButtons", false);

        // ===== Separator =====
        UIManager.put("Separator.foreground", borda);
        UIManager.put("Separator.thickness", 1);

        // ===== ProgressBar =====
        UIManager.put("ProgressBar.background", painelElevado);
        UIManager.put("ProgressBar.foreground", primario);
        UIManager.put("ProgressBar.selectionBackground", destaque);
        UIManager.put("ProgressBar.selectionForeground", sobreDestaque);

        // ===== ToolTip =====
        UIManager.put("ToolTip.background", painelElevado);
        UIManager.put("ToolTip.foreground", texto);
        UIManager.put("ToolTip.border", new LineBorder(borda, 1, true));

        // ===== Menu/MenuBar/PopupMenu =====
        UIManager.put("MenuBar.background", sidebar);
        UIManager.put("MenuBar.foreground", texto);
        UIManager.put("Menu.background", painelElevado);
        UIManager.put("Menu.foreground", texto);
        UIManager.put("Menu.selectionBackground", destaque);
        UIManager.put("Menu.selectionForeground", sobreDestaque);
        UIManager.put("Menu.border", new LineBorder(borda, 1, true));
        UIManager.put("PopupMenu.background", painelElevado);
        UIManager.put("PopupMenu.foreground", texto);
        UIManager.put("PopupMenu.border", new LineBorder(borda, 1, true));
        UIManager.put("MenuItem.background", painelElevado);
        UIManager.put("MenuItem.foreground", texto);
        UIManager.put("MenuItem.selectionBackground", destaque);
        UIManager.put("MenuItem.selectionForeground", sobreDestaque);
        UIManager.put("MenuItem.acceleratorForeground", muted);
        UIManager.put("MenuItem.acceleratorSelectionForeground", sobreDestaque);

        // ===== TabbedPane =====
        UIManager.put("TabbedPane.background", fundo);
        UIManager.put("TabbedPane.foreground", texto);
        UIManager.put("TabbedPane.selectedBackground", painel);
        UIManager.put("TabbedPane.selectedForeground", primario);
        UIManager.put("TabbedPane.unselectedBackground", fundo);
        UIManager.put("TabbedPane.unselectedForeground", muted);
        UIManager.put("TabbedPane.hoverBackground", hover);
        UIManager.put("TabbedPane.pressedBackground", pressed);
        UIManager.put("TabbedPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("TabbedPane.contentAreaBorder", BorderFactory.createEmptyBorder());
        UIManager.put("TabbedPane.tabAreaInsets", new Insets(0, 0, 0, 0));
        UIManager.put("TabbedPane.tabInsets", new Insets(8, 16, 8, 16));

        // ===== Spinner =====
        UIManager.put("Spinner.background", inputFundo);
        UIManager.put("Spinner.foreground", inputTexto);
        UIManager.put("Spinner.border", new LineBorder(borda, 1, true));

        // ===== CheckBox/RadioButton =====
        UIManager.put("CheckBox.background", fundo);
        UIManager.put("CheckBox.foreground", texto);
        UIManager.put("CheckBox.icon.enabled", new Icone(Icone.Tipo.CHECK, 16));
        UIManager.put("RadioButton.background", fundo);
        UIManager.put("RadioButton.foreground", texto);

        // ===== Slider =====
        UIManager.put("Slider.background", fundo);
        UIManager.put("Slider.foreground", primario);
        UIManager.put("Slider.trackBackground", painelElevado);
        UIManager.put("Slider.thumb", primario);

        // ===== Status (badge caixa aberto) =====
        UIManager.put("status.background", status);
        UIManager.put("status.foreground", statusTexto);

        // ===== JFrame/RootPane - evitar fundo branco =====
        UIManager.put("RootPane.background", fundo);
        UIManager.put("InternalFrame.background", fundo);
        UIManager.put("Desktop.background", fundo);
        UIManager.put("DesktopIcon.background", fundo);
    }

    /**
     * Troca o tema em tempo de execução e atualiza toda a árvore de componentes.
     *
     * @param tema  novo tema (CLARO ou ESCURO)
     * @param raiz  componente raiz (JFrame, JDialog, JPanel, etc.) — toda a hierarquia será atualizada
     */
    public static void trocar(Tema tema, Component raiz) {
        // 1. Instala novo L&F e propriedades UIManager
        instalar(tema);

        // 2. Atualiza UI delegates de toda a árvore
        SwingUtilities.updateComponentTreeUI(raiz);

        // 3. Aplica client properties (demo.fundo, demo.texto) recursivamente
        aplicar(raiz);

        // 4. Força revalidação e repaint na raiz
        if (raiz instanceof JComponent jc) {
            jc.revalidate();
            jc.repaint();
        } else if (raiz instanceof Container container) {
            container.validate();
            container.repaint();
        }

        // 5. Força repaint em janelas filhas (JOptionPane, JDialog, etc.)
        for (Window w : Window.getWindows()) {
            if (w != raiz && w.isDisplayable()) {
                SwingUtilities.updateComponentTreeUI(w);
                aplicar(w);
                w.repaint();
            }
        }
    }

    /**
     * Aplica estilo a um componente: define client properties para background/foreground
     * e aplica as cores imediatamente.
     */
    public static <T extends JComponent> T estilo(T componente, String fundo, String texto) {
        if (fundo != null) componente.putClientProperty("demo.fundo", fundo);
        if (texto != null) componente.putClientProperty("demo.texto", texto);
        aplicarCores(componente);
        return componente;
    }

    /**
     * Percorre recursivamente a hierarquia aplicando cores aos componentes
     * que têm client properties "demo.fundo" e/ou "demo.texto".
     * Também processa viewports de JScrollPane e células de JList/JTable.
     */
    public static void aplicar(Component componente) {
        if (componente instanceof JComponent jc) {
            aplicarCores(jc);
        }

        // Processar viewport de JScrollPane
        if (componente instanceof JScrollPane scrollPane) {
            JViewport viewport = scrollPane.getViewport();
            if (viewport != null) {
                aplicarCores(viewport);
                Component view = viewport.getView();
                if (view != null) {
                    aplicar(view);
                }
            }
        }

        if (componente instanceof Container container) {
            for (Component filho : container.getComponents()) {
                aplicar(filho);
            }
        }

        // Processar células de JList (renderer components não estão na hierarquia)
        if (componente instanceof JList<?> list) {
            list.repaint();
        }

        // Processar JTable
        if (componente instanceof JTable table) {
            table.repaint();
        }
    }

    private static void aplicarCores(JComponent componente) {
        // Aplicar background baseado em client property
        if (componente.getClientProperty("demo.fundo") instanceof String papel) {
            componente.setBackground(cor(papel));
        }

        // Aplicar foreground baseado em client property
        if (componente.getClientProperty("demo.texto") instanceof String papel) {
            componente.setForeground(cor(papel));
            // Forçar repaint de ícones filhos (usam foreground do componente)
            for (Component child : componente.getComponents()) {
                child.repaint();
            }
        }

        // Para PainelArredondado, forçar repaint pois ele pinta o fundo no paintComponent
        if (componente instanceof PainelArredondado) {
            componente.repaint();
        }

        // Para botões com ícones, forçar repaint do ícone
        if (componente instanceof AbstractButton btn) {
            btn.repaint();
        }

        // Para labels com ícones
        if (componente instanceof JLabel label) {
            label.repaint();
        }
    }

    /**
     * Força atualização completa de um componente e seus filhos.
     * Útil para componentes customizados que não usam client properties.
     */
    public static void forcarAtualizacao(Component componente) {
        if (componente instanceof JComponent jc) {
            // Atualizar background/foreground do próprio componente se tiver client properties
            aplicarCores(jc);

            // Recursão em filhos
            if (componente instanceof Container container) {
                for (Component filho : container.getComponents()) {
                    forcarAtualizacao(filho);
                }
            }
        }
    }
}