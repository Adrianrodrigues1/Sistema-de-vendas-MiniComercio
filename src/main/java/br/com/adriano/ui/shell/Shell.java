package br.com.adriano.ui.shell;

import br.com.adriano.db.ConexaoManager;
import br.com.adriano.service.CaixaService;
import br.com.adriano.service.ProdutoService;
import br.com.adriano.service.RelatorioService;
import br.com.adriano.service.VendaService;
import br.com.adriano.ui.caixa.PainelCaixa;
import br.com.adriano.ui.componentes.Componentes;
import br.com.adriano.ui.componentes.Icone;
import br.com.adriano.ui.pdv.*;
import br.com.adriano.ui.relatorios.PainelRelatorios;
import br.com.adriano.ui.tema.GerenciadorTema;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Janela única: sidebar, cabeçalho, CardLayout para módulos. Fiel ao mockup do PDV.
 * <p>
 * Suporta dois modos de inicialização:
 * - Demo: apenas PdvGateway (GatewayDemonstracao) - usa placeholders para módulos não implementados
 * - Real: PdvGateway + ProdutoService + ConexaoManager - carrega painéis funcionais completos
 */
public final class Shell extends JFrame {
    private final PdvGateway gateway;
    private final ProdutoService produtoService;
    private final ConexaoManager conexaoManager;
    private final CardLayout cards = new CardLayout();
    private final JPanel centro = new JPanel(cards);
    private final JLabel lblHora = new JLabel();
    private final JLabel lblCaixa = new JLabel();
    private final JLabel lblUsuario = new JLabel();
    private final Timer relogio;
    private final boolean modoOperador;
    private String cardAtual = "PDV";
    private PainelPdv painelPdv;
    private PainelCaixa painelCaixa;
    private PainelRelatorios painelRelatorios;

    /**
     * Construtor para modo demo (apenas PdvGateway).
     * Módulos Produtos e Estoque ficam como placeholders.
     */
    public Shell(PdvGateway gateway, boolean modoOperador) {
        this(gateway, null, null, modoOperador);
    }

    /**
     * Construtor completo para modo real com todos os serviços.
     *
     * @param gateway         gateway do PDV (PdvGatewayReal)
     * @param produtoService  serviço de produtos (pode ser null para modo demo)
     * @param conexaoManager  gerenciador de conexões (pode ser null para modo demo)
     * @param modoOperador    true para perfil operador (acesso restrito)
     */
    public Shell(PdvGateway gateway, ProdutoService produtoService, ConexaoManager conexaoManager, boolean modoOperador) {
        super("Mini Comércio 2.0 — Caldos da Ana");
        this.gateway = gateway;
        this.produtoService = produtoService;
        this.conexaoManager = conexaoManager;
        this.modoOperador = modoOperador;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(1100, 700));
        GerenciadorTema.aplicar(this);

        add(montarSidebar(), BorderLayout.WEST);
        add(montarCabecalho(), BorderLayout.NORTH);
        add(centro, BorderLayout.CENTER);

        montarPaineis();

        relogio = new Timer(1000, e -> atualizarHora());
        relogio.start();
        atualizarHora();

        configurarAtalhos();
        setLocationRelativeTo(null);
    }

    private JComponent montarSidebar() {
        JPanel sidebar = Componentes.painel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(220, 0));
        GerenciadorTema.estilo(sidebar, "sidebar", "texto");
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, GerenciadorTema.cor("borda")));

        // Topo: Logo
        JPanel topo = Componentes.painel(new GridBagLayout());
        topo.setBorder(BorderFactory.createEmptyBorder(18, 12, 10, 12));
        JLabel logo = Componentes.texto("Mini Comércio 2.0", 16, true);
        logo.setIcon(new Icone(Icone.Tipo.TIGELA, 22));
        logo.setIconTextGap(10);
        topo.add(logo);
        JLabel subtitulo = Componentes.secundario("Caldos da Ana");
        JPanel topoCompleto = Componentes.painel(new GridLayout(2, 1, 0, 2));
        topoCompleto.setOpaque(false);
        topoCompleto.add(logo);
        topoCompleto.add(subtitulo);
        sidebar.add(topoCompleto, BorderLayout.NORTH);

        // Centro: navegação
        String[][] itens = {
            {"PDV / Vendas", "CARRINHO", "PDV"},
            {"Produtos", "PRODUTO", "PRODUTOS"},
            {"Estoque", "ESTOQUE", "ESTOQUE"},
            {"Caixa", "CAIXA", "CAIXA"},
            {"Relatórios", "RELATORIO", "RELATORIOS"},
            {"Clientes", "CLIENTE", "CLIENTES"},
            {"Usuários", "CONFIGURACAO", "USUARIOS"},
            {"Configurações", "CONFIGURACAO", "CONFIGURACOES"}
        };
        JPanel nav = Componentes.painel(new GridBagLayout());
        nav.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 16, 4, 16);
        gbc.weightx = 1;
        ButtonGroup grupo = new ButtonGroup();
        for (int i = 0; i < itens.length; i++) {
            String[] item = itens[i];
            JToggleButton btn = new JToggleButton(item[0], new Icone(Icone.Tipo.valueOf(item[1]), 20));
            btn.setHorizontalAlignment(SwingConstants.LEFT);
            btn.setIconTextGap(12);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            GerenciadorTema.estilo(btn, "sidebar", "texto");
            btn.putClientProperty("demo.fundo", "sidebar");
            btn.putClientProperty("demo.texto", "texto");
            final String card = item[2];
            btn.addActionListener(e -> trocarCard(card));
            grupo.add(btn);
            gbc.gridy = i;
            nav.add(btn, gbc);
            if (i == 0) btn.setSelected(true);
            // Bloquear relatórios para operador
            if (modoOperador && (card.equals("RELATORIOS") || card.equals("USUARIOS") || card.equals("CONFIGURACOES"))) {
                btn.setEnabled(false);
                btn.setToolTipText("Acesso restrito a gerentes/administradores");
            }
        }
        sidebar.add(nav, BorderLayout.CENTER);

        // Rodapé
        JLabel rodape = Componentes.secundario("Desenvolvido por GOAT");
        rodape.setHorizontalAlignment(SwingConstants.CENTER);
        rodape.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        sidebar.add(rodape, BorderLayout.SOUTH);

        return sidebar;
    }

    private JComponent montarCabecalho() {
        JPanel header = Componentes.painel(new BorderLayout());
        header.setPreferredSize(new Dimension(0, 72));
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, GerenciadorTema.cor("borda")),
            BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        GerenciadorTema.estilo(header, "painel", "texto");

        // Esquerda: logo + título
        JPanel esquerda = Componentes.painel(new GridBagLayout());
        esquerda.setOpaque(false);
        JLabel logo = Componentes.texto("Sistema de Vendas e Gestão", 18, true);
        logo.setIcon(new Icone(Icone.Tipo.TIGELA, 24));
        logo.setIconTextGap(12);
        esquerda.add(logo);
        header.add(esquerda, BorderLayout.WEST);

        // Direita: data/hora, caixa, usuário
        JPanel direita = Componentes.painel(new GridBagLayout());
        direita.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(0, 16, 0, 0);

        lblHora.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        GerenciadorTema.estilo(lblHora, null, "muted");
        gbc.gridx = 0; direita.add(lblHora, gbc);

        lblCaixa.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblCaixa.setIcon(new Icone(Icone.Tipo.CAIXA, 16));
        lblCaixa.setIconTextGap(6);
        lblCaixa.setText("Caixa Aberto");
        GerenciadorTema.estilo(lblCaixa, "status", "statusTexto");
        lblCaixa.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        lblCaixa.setOpaque(true);
        gbc.gridx = 1; direita.add(lblCaixa, gbc);

        lblUsuario.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblUsuario.setIcon(new Icone(Icone.Tipo.CLIENTE, 16));
        lblUsuario.setIconTextGap(6);
        lblUsuario.setText(modoOperador ? "Operador (Demo)" : "Administrador (Demo)");
        GerenciadorTema.estilo(lblUsuario, null, "muted");
        gbc.gridx = 2; direita.add(lblUsuario, gbc);

        header.add(direita, BorderLayout.EAST);
        return header;
    }

    private void montarPaineis() {
        // PDV - sempre disponível
        painelPdv = new PainelPdv(gateway, this::executarAcaoPdv, this::mostrarDialogoCliente);
        JScrollPane scrollPdv = new JScrollPane(painelPdv, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPdv.setBorder(null);
        scrollPdv.getViewport().setOpaque(false);
        scrollPdv.setOpaque(false);
        GerenciadorTema.estilo(scrollPdv.getViewport(), "fundo", null);
        centro.add(scrollPdv, "PDV");

        // PRODUTOS - painel funcional se tiver serviço, senão placeholder
        if (produtoService != null) {
            // PainelProdutos não disponível neste backup - usar placeholder
            centro.add(placeholder("Produtos", Icone.Tipo.PRODUTO, "Cadastro e gerenciamento de produtos"), "PRODUTOS");
        } else {
            centro.add(placeholder("Produtos", Icone.Tipo.PRODUTO, "Cadastro e gerenciamento de produtos"), "PRODUTOS");
        }

        // ESTOQUE - painel funcional se tiver serviço e conexão, senão placeholder
        if (produtoService != null && conexaoManager != null) {
            // PainelEstoque não disponível neste backup - usar placeholder
            centro.add(placeholder("Estoque", Icone.Tipo.ESTOQUE, "Movimentações e controle de estoque"), "ESTOQUE");
        } else {
            centro.add(placeholder("Estoque", Icone.Tipo.ESTOQUE, "Movimentações e controle de estoque"), "ESTOQUE");
        }

        // CAIXA - painel funcional se tiver serviços completos
        if (produtoService != null && conexaoManager != null) {
            CaixaService caixaService = new CaixaService(conexaoManager);
            VendaService vendaService = new VendaService(conexaoManager, produtoService, caixaService);
            RelatorioService relatorioService = new RelatorioService(conexaoManager);
            painelCaixa = new PainelCaixa(caixaService, vendaService, relatorioService, conexaoManager, this::atualizarStatus);
            JScrollPane scrollCaixa = new JScrollPane(painelCaixa, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scrollCaixa.setBorder(null);
            scrollCaixa.getViewport().setOpaque(false);
            scrollCaixa.setOpaque(false);
            GerenciadorTema.estilo(scrollCaixa.getViewport(), "fundo", null);
            centro.add(scrollCaixa, "CAIXA");
        } else {
            centro.add(placeholder("Caixa", Icone.Tipo.CAIXA, "Abertura, suprimento, retirada e fechamento"), "CAIXA");
        }

        // RELATÓRIOS - painel funcional se tiver serviços completos
        if (produtoService != null && conexaoManager != null) {
            RelatorioService relatorioService = new RelatorioService(conexaoManager);
            painelRelatorios = new PainelRelatorios(relatorioService, conexaoManager, this::atualizarStatus);
            JScrollPane scrollRelatorios = new JScrollPane(painelRelatorios, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scrollRelatorios.setBorder(null);
            scrollRelatorios.getViewport().setOpaque(false);
            scrollRelatorios.setOpaque(false);
            GerenciadorTema.estilo(scrollRelatorios.getViewport(), "fundo", null);
            centro.add(scrollRelatorios, "RELATORIOS");
        } else {
            centro.add(placeholder("Relatórios", Icone.Tipo.RELATORIO, "Relatórios gerenciais (restrito)"), "RELATORIOS");
        }

        // Demais módulos como placeholders (Fases futuras)
        centro.add(placeholder("Clientes", Icone.Tipo.CLIENTE, "Cadastro de clientes"), "CLIENTES");
        centro.add(placeholder("Usuários", Icone.Tipo.CONFIGURACAO, "Administração de usuários (restrito)"), "USUARIOS");
        centro.add(montarPainelConfiguracoes(), "CONFIGURACOES");
    }

    private void trocarCard(String card) {
        cardAtual = card;
        cards.show(centro, card);
    }

    private void executarAcaoPdv(Runnable acao) {
        if ("PDV".equals(cardAtual) && painelPdv != null) {
            acao.run();
        }
    }

    private void atualizarStatus(String mensagem) {
        // Atualiza badge do usuário ou mostra toast (simplificado: apenas log)
        System.out.println("[STATUS] " + mensagem);
    }

    private JComponent placeholder(String titulo, Icone.Tipo icone, String descricao) {
        JPanel p = Componentes.painel(new GridBagLayout());
        p.setOpaque(false);
        JLabel label = Componentes.texto(titulo, 22, true);
        label.setIcon(new Icone(icone, 48));
        label.setIconTextGap(16);
        label.setVerticalTextPosition(SwingConstants.BOTTOM);
        label.setHorizontalTextPosition(SwingConstants.CENTER);
        p.add(label);
        JLabel sub = Componentes.secundario(descricao);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 1; gbc.insets = new Insets(12, 0, 0, 0);
        p.add(sub, gbc);
        if (modoOperador && (titulo.equals("Relatórios") || titulo.equals("Usuários") || titulo.equals("Configurações"))) {
            JLabel aviso = Componentes.secundario("Acesso restrito a gerentes/administradores");
            gbc.gridy = 2; gbc.insets = new Insets(24, 0, 0, 0);
            p.add(aviso, gbc);
        }
        return p;
    }

    private JComponent montarPainelConfiguracoes() {
        JPanel p = Componentes.painel(new GridBagLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        JLabel titulo = Componentes.texto("Configurações", 22, true);
        titulo.setIcon(new Icone(Icone.Tipo.CONFIGURACAO, 28));
        titulo.setIconTextGap(12);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(0, 0, 24, 0);
        p.add(titulo, gbc);

        JLabel lblTema = Componentes.texto("Tema da interface", 14, true);
        gbc.gridy = 1; gbc.gridwidth = 1; gbc.insets = new Insets(0, 0, 8, 16);
        p.add(lblTema, gbc);

        JComboBox<GerenciadorTema.Tema> comboTema = new JComboBox<>(GerenciadorTema.Tema.values());
        comboTema.setSelectedItem(GerenciadorTema.atual());
        comboTema.setPreferredSize(new Dimension(180, 36));
        comboTema.addActionListener(e -> {
            GerenciadorTema.Tema novo = (GerenciadorTema.Tema) comboTema.getSelectedItem();
            // Usar o JFrame (Shell) como raiz para atualizar toda a árvore de componentes
            GerenciadorTema.trocar(novo, Shell.this);
        });
        gbc.gridx = 1; gbc.insets = new Insets(0, 0, 8, 0);
        p.add(comboTema, gbc);

        JLabel nota = Componentes.secundario("A preferência de tema é armazenada apenas em memória nesta fase.");
        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 2; gbc.insets = new Insets(16, 0, 0, 0);
        p.add(nota, gbc);

        if (modoOperador) {
            JLabel aviso = Componentes.secundario("Outras configurações requerem permissão de gerente/administrador.");
            gbc.gridy = 3; gbc.insets = new Insets(24, 0, 0, 0);
            p.add(aviso, gbc);
        }
        return p;
    }

    private void atualizarHora() {
        java.time.ZonedDateTime agora = java.time.ZonedDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"));
        lblHora.setText(agora.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy  HH:mm:ss")));
    }

    private void configurarAtalhos() {
        InputMap im = centro.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = centro.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0), "finalizar");
        am.put("finalizar", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                executarAcaoPdv(() -> {
                    if (painelPdv != null) painelPdv.finalizarVenda();
                });
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelar");
        am.put("cancelar", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                executarAcaoPdv(() -> {
                    if (painelPdv != null) painelPdv.limparCarrinho();
                });
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "cliente");
        am.put("cliente", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                executarAcaoPdv(Shell.this::mostrarDialogoCliente);
            }
        });
    }

    private void mostrarDialogoCliente() {
        JOptionPane.showMessageDialog(this, "Cadastro de cliente (simulado na Fase 1A)", "Cliente", JOptionPane.INFORMATION_MESSAGE);
    }
}