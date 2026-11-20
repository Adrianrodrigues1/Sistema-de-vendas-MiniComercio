package br.com.adriano.ui.shell;

import br.com.adriano.ui.componentes.*;
import br.com.adriano.ui.pdv.*;
import br.com.adriano.ui.tema.GerenciadorTema;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

/** Painel do PDV: busca, grade de produtos, carrinho, totais, pagamento. */
public final class PainelPdv extends JPanel {
    private final PdvGateway gateway;
    private final Consumer<Runnable> aoFinalizar;
    private final Runnable aoCliente;
    private final Carrinho carrinho = new Carrinho();
    private final GradeProdutos grade = new GradeProdutos();
    private final DefaultListModel<Carrinho.Item> modeloCarrinho = new DefaultListModel<>();
    private final JLabel lblSubtotal = new JLabel();
    private final JLabel lblDesconto = new JLabel();
    private final JLabel lblTotal = new JLabel();
    private final JTextField txtBusca = new JTextField();
    private final JComboBox<String> comboCategoria = new JComboBox<>(new String[]{"Todos", "Caldos", "Bebidas", "Acompanhamentos", "Outros"});
    private final JList<Carrinho.Item> listaCarrinho = new JList<>(modeloCarrinho);
    private List<ProdutoPdv> todosProdutos;
    private final JPanel painelCarrinho = new JPanel();

    public PainelPdv(PdvGateway gateway, Consumer<Runnable> aoFinalizar, Runnable aoCliente) {
        this.gateway = gateway;
        this.aoFinalizar = aoFinalizar;
        this.aoCliente = aoCliente;
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setOpaque(false);
        GerenciadorTema.aplicar(this);

        add(montarTopo(), BorderLayout.NORTH);
        add(montarCentro(), BorderLayout.CENTER);
        add(montarDireita(), BorderLayout.EAST);

        carregarProdutos();
    }

    private JComponent montarTopo() {
        JPanel topo = Componentes.painel(new BorderLayout(12, 0), "fundo");
        // topo.setOpaque(false); // agora usa role "fundo"

        // Busca
        JPanel buscaPainel = Componentes.painel(new BorderLayout(8, 0), "fundo");
        JLabel lblBusca = Componentes.texto("Buscar produto", 13, true);
        lblBusca.setIcon(new Icone(Icone.Tipo.BUSCA, 18));
        lblBusca.setIconTextGap(8);
        buscaPainel.add(lblBusca, BorderLayout.WEST);

        txtBusca.setPreferredSize(new Dimension(300, 38));
        txtBusca.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        GerenciadorTema.estilo(txtBusca, "inputFundo", "inputTexto");
        txtBusca.putClientProperty("JTextField.placeholderText", "Nome ou código...");
        txtBusca.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filtrar(); }
            public void removeUpdate(DocumentEvent e) { filtrar(); }
            public void changedUpdate(DocumentEvent e) { filtrar(); }
        });
        buscaPainel.add(txtBusca, BorderLayout.CENTER);

        // Categoria
        comboCategoria.setPreferredSize(new Dimension(170, 38));
        comboCategoria.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        GerenciadorTema.estilo(comboCategoria, "inputFundo", "inputTexto");
        comboCategoria.addActionListener(e -> filtrar());

        JPanel direitaTopo = Componentes.painel(new BorderLayout(8, 0), "fundo");
        direitaTopo.add(Componentes.secundario("Categoria:"), BorderLayout.WEST);
        direitaTopo.add(comboCategoria, BorderLayout.CENTER);

        topo.add(buscaPainel, BorderLayout.WEST);
        topo.add(direitaTopo, BorderLayout.EAST);
        return topo;
    }

    private JComponent montarCentro() {
        JPanel centro = Componentes.painel(new BorderLayout(), "fundo");
        JLabel titulo = Componentes.texto("Produtos Disponíveis", 14, true);
        titulo.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        centro.add(titulo, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(grade, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder()); // Sem borda
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        // Garantir que o viewport tenha a cor de fundo correta
        GerenciadorTema.estilo(scroll.getViewport(), "fundo", null);
        centro.add(scroll, BorderLayout.CENTER);
        return centro;
    }

    private JComponent montarDireita() {
        JPanel direita = Componentes.painel(new BorderLayout(0, 12), "fundo");
        direita.setPreferredSize(new Dimension(420, 0));
        direita.setMaximumSize(new Dimension(420, Integer.MAX_VALUE));

        // Título carrinho
        JPanel cabecalhoCarrinho = Componentes.painel(new BorderLayout(), "fundo");
        JLabel titulo = Componentes.texto("Venda em Andamento", 14, true);
        titulo.setIcon(new Icone(Icone.Tipo.CARRINHO, 20));
        titulo.setIconTextGap(8);
        cabecalhoCarrinho.add(titulo, BorderLayout.WEST);
        JButton btnLimpar = Componentes.botao("Limpar", Icone.Tipo.LIXEIRA, this::limparCarrinho);
        // Botão secundário - escuro com borda discreta (não primario)
        GerenciadorTema.estilo(btnLimpar, "painelElevado", "texto");
        cabecalhoCarrinho.add(btnLimpar, BorderLayout.EAST);
        direita.add(cabecalhoCarrinho, BorderLayout.NORTH);

        // Lista do carrinho
        listaCarrinho.setCellRenderer(new CarrinhoRenderer());
        listaCarrinho.setFixedCellHeight(96);
        listaCarrinho.setOpaque(false);
        listaCarrinho.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaCarrinho.setBorder(null);
        JScrollPane scrollCarrinho = new JScrollPane(listaCarrinho, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollCarrinho.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0)); // SEM bordas laterais
        scrollCarrinho.getViewport().setOpaque(false);
        scrollCarrinho.setOpaque(false);
        GerenciadorTema.estilo(scrollCarrinho.getViewport(), "fundo", null);
        direita.add(scrollCarrinho, BorderLayout.CENTER);

        // Totais e ações
        JPanel rodape = Componentes.painel(new GridBagLayout(), "fundo");
        rodape.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 0, 4, 0);
        gbc.anchor = GridBagConstraints.WEST;

        // Subtotal
        gbc.gridy = 0; gbc.gridx = 0;
        rodape.add(Componentes.secundario("Subtotal"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.EAST; gbc.weightx = 1;
        lblSubtotal.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        GerenciadorTema.estilo(lblSubtotal, null, "texto");
        rodape.add(lblSubtotal, gbc);

        // Desconto
        gbc.gridy = 1; gbc.gridx = 0; gbc.anchor = GridBagConstraints.WEST; gbc.weightx = 0;
        rodape.add(Componentes.secundario("Desconto"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.EAST; gbc.weightx = 1;
        lblDesconto.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        GerenciadorTema.estilo(lblDesconto, null, "muted");
        rodape.add(lblDesconto, gbc);

        // Linha separadora
        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        JSeparator sep = new JSeparator();
        sep.setForeground(GerenciadorTema.cor("borda"));
        rodape.add(sep, gbc);

        // Total
        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.WEST; gbc.weightx = 0;
        JLabel lblTotalTitulo = Componentes.texto("TOTAL", 16, true);
        rodape.add(lblTotalTitulo, gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.EAST; gbc.weightx = 1;
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 20));
        GerenciadorTema.estilo(lblTotal, "primario", "sobrePrimario");
        rodape.add(lblTotal, gbc);

        // Botões de pagamento
        gbc.gridy = 4; gbc.gridx = 0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.insets = new Insets(16, 0, 0, 0);
        JPanel botoesPag = Componentes.painel(new GridLayout(1, 4, 8, 0), "fundo");
        for (Pagamento.Forma forma : Pagamento.Forma.values()) {
            JButton btn = new PagamentoButton(forma, () -> processarPagamento(forma));
            botoesPag.add(btn);
        }
        rodape.add(botoesPag, gbc);

        // Botões de ação
        gbc.gridy = 5; gbc.insets = new Insets(12, 0, 0, 0);
        JPanel acoes = Componentes.painel(new GridLayout(1, 3, 8, 0), "fundo");
        JButton btnPrevia = Componentes.botao("Prévia", Icone.Tipo.PREVIA, () -> JOptionPane.showMessageDialog(this, "Prévia da venda (simulada)", "Prévia", JOptionPane.INFORMATION_MESSAGE));
        GerenciadorTema.estilo(btnPrevia, "painelElevado", "texto"); // Secundário
        JButton btnCliente = Componentes.botao("Cliente (F2)", Icone.Tipo.CLIENTE, aoCliente);
        GerenciadorTema.estilo(btnCliente, "painelElevado", "texto"); // Secundário
        JButton btnFinalizar = Componentes.botao("Finalizar (F10)", Icone.Tipo.CHECK, this::finalizarVenda);
        Componentes.primario(btnFinalizar); // Primário - bege/dourado
        btnFinalizar.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0), "finalizar");
        btnFinalizar.getActionMap().put("finalizar", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { finalizarVenda(); }
        });
        acoes.add(btnPrevia); acoes.add(btnCliente); acoes.add(btnFinalizar);
        rodape.add(acoes, gbc);

        direita.add(rodape, BorderLayout.SOUTH);

        atualizarCarrinho();
        return direita;
    }

    private void carregarProdutos() {
        try {
            todosProdutos = gateway.listarProdutos();
            filtrar();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao carregar produtos: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void filtrar() {
        String busca = txtBusca.getText();
        String categoria = (String) comboCategoria.getSelectedItem();
        List<ProdutoPdv> filtrados = Carrinho.buscar(todosProdutos, busca, categoria);
        grade.removeAll();
        for (ProdutoPdv p : filtrados) {
            grade.add(new CartaoProduto(p, qtd -> adicionarAoCarrinho(p, qtd)));
        }
        grade.revalidate();
        grade.repaint();
    }

    private void adicionarAoCarrinho(ProdutoPdv produto, int quantidade) {
        if (produto.estoque() == 0) {
            JOptionPane.showMessageDialog(this, "Produto sem estoque.", "Atenção", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int qtdFinal = Math.min(quantidade, produto.estoque());
        try {
            carrinho.adicionar(produto, qtdFinal);
            atualizarCarrinho();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Atenção", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void atualizarCarrinho() {
        modeloCarrinho.clear();
        for (Carrinho.Item item : carrinho.itens()) {
            modeloCarrinho.addElement(item);
        }
        lblSubtotal.setText(Dinheiro.formatar(carrinho.subtotal()));
        lblDesconto.setText("- " + Dinheiro.formatar(carrinho.desconto()));
        lblTotal.setText(Dinheiro.formatar(carrinho.total()));
        boolean temItens = !carrinho.vazio();
    }

    void limparCarrinho() {
        if (carrinho.vazio()) return;
        int op = JOptionPane.showConfirmDialog(this, "Limpar todos os itens do carrinho?", "Confirmar", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (op == JOptionPane.YES_OPTION) {
            carrinho.limpar();
            atualizarCarrinho();
        }
    }

    private void processarPagamento(Pagamento.Forma forma) {
        if (carrinho.vazio()) {
            JOptionPane.showMessageDialog(this, "Carrinho vazio.", "Atenção", JOptionPane.WARNING_MESSAGE);
            return;
        }
        BigDecimal total = carrinho.total();
        if (forma == Pagamento.Forma.DINHEIRO) {
            String valor = (String) JOptionPane.showInputDialog(this, "Valor recebido:", "Pagamento em Dinheiro", JOptionPane.QUESTION_MESSAGE, null, null, Dinheiro.formatar(total));
            if (valor == null) return;
            try {
                BigDecimal recebido = Dinheiro.ler(valor);
                Pagamento pgto = Pagamento.calcular(forma, total, recebido);
                confirmarVenda(pgto);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Valor inválido", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            Pagamento pgto = Pagamento.calcular(forma, total, total);
            confirmarVenda(pgto);
        }
    }

    private void confirmarVenda(Pagamento pagamento) {
        String msg = "Confirmar venda?\nTotal: " + Dinheiro.formatar(carrinho.total()) +
                     "\nForma: " + pagamento.forma() +
                     (pagamento.troco().signum() > 0 ? "\nTroco: " + Dinheiro.formatar(pagamento.troco()) : "");
        int op = JOptionPane.showConfirmDialog(this, msg, "Confirmar Venda", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (op != JOptionPane.YES_OPTION) return;

        // Executa em background
        new SwingWorker<PdvGateway.Resultado, Void>() {
            @Override protected PdvGateway.Resultado doInBackground() throws Exception {
                return gateway.concluirVenda(new PdvGateway.Pedido(carrinho.itens(), carrinho.desconto(), pagamento, null));
            }
            @Override protected void done() {
                try {
                    PdvGateway.Resultado r = get();
                    JOptionPane.showMessageDialog(PainelPdv.this, r.mensagem(), "Venda Concluída", JOptionPane.INFORMATION_MESSAGE);
                    carrinho.limpar();
                    atualizarCarrinho();
                    carregarProdutos(); // recarrega estoques
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(PainelPdv.this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    void finalizarVenda() {
        if (carrinho.vazio()) {
            JOptionPane.showMessageDialog(this, "Carrinho vazio.", "Atenção", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // Abre diálogo de pagamento - usar DINHEIRO como padrão
        processarPagamento(Pagamento.Forma.DINHEIRO);
    }

    private static class CarrinhoRenderer extends JPanel implements ListCellRenderer<Carrinho.Item> {
        public CarrinhoRenderer() { setOpaque(false); setLayout(new BorderLayout()); }
        @Override public Component getListCellRendererComponent(JList<? extends Carrinho.Item> list, Carrinho.Item value, int index, boolean isSelected, boolean cellHasFocus) {
            removeAll();
            add(new LinhaCarrinho(value,
                qtd -> { /* quantidade alterada via UI não suportada direto no renderer */ },
                () -> { /* remover via UI não suportado direto no renderer */ }
            ), BorderLayout.CENTER);
            if (isSelected) setBackground(GerenciadorTema.cor("destaque"));
            else setBackground(GerenciadorTema.cor("painel"));
            return this;
        }
    }

    private static class PagamentoButton extends JButton {
        public PagamentoButton(Pagamento.Forma forma, Runnable acao) {
            super(forma.toString(), new Icone(iconeParaForma(forma), 22));
            setHorizontalTextPosition(SwingConstants.CENTER);
            setVerticalTextPosition(SwingConstants.BOTTOM);
            setIconTextGap(6);
            setFont(new Font("Segoe UI", Font.PLAIN, 12));
            setPreferredSize(new Dimension(80, 70));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusPainted(false);
            addActionListener(e -> acao.run());
            if (forma == Pagamento.Forma.DINHEIRO) {
                Componentes.primario(this); // Primário - bege/dourado
            } else {
                GerenciadorTema.estilo(this, "painelElevado", "texto"); // Secundário - escuro
            }
        }
        private static Icone.Tipo iconeParaForma(Pagamento.Forma forma) {
            return switch (forma) {
                case DINHEIRO -> Icone.Tipo.DINHEIRO;
                case PIX -> Icone.Tipo.PIX;
                case DEBITO, CREDITO -> Icone.Tipo.CARTAO;
            };
        }
    }
}