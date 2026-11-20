package br.com.adriano.ui.produtos;

import br.com.adriano.model.Produto;
import br.com.adriano.service.ProdutoService;
import br.com.adriano.ui.componentes.Componentes;
import br.com.adriano.ui.componentes.Icone;
import br.com.adriano.ui.componentes.PainelArredondado;
import br.com.adriano.ui.tema.GerenciadorTema;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

/**
 * Painel de cadastro e gerenciamento de produtos.
 * <p>
 * CRUD completo: código, nome, categoria, preço, estoque, imagem, ativo.
 * Busca case/acento-insensível, paginação, ordenação por colunas.
 */
public final class PainelProdutos extends JPanel {

    private final ProdutoService service;
    private final Consumer<String> aoStatus;
    private DefaultTableModel modeloTabela;
    private JTable tabela;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField txtBusca;
    private JComboBox<String> comboCategoria;
    private JCheckBox chkApenasAtivos;
    private JButton btnNovo;
    private JButton btnEditar;
    private JButton btnExcluir;
    private JButton btnAtivarDesativar;
    private JLabel lblTotal;

    public PainelProdutos(ProdutoService service, Consumer<String> aoStatus) {
        this.service = service;
        this.aoStatus = aoStatus;
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setOpaque(false);
        GerenciadorTema.aplicar(this);

        add(montarTopo(), BorderLayout.NORTH);
        add(montarCentro(), BorderLayout.CENTER);
        add(montarRodape(), BorderLayout.SOUTH);

        carregarDados();
    }

    private JComponent montarTopo() {
        JPanel topo = Componentes.painel(new BorderLayout(12, 0), "fundo");

        // Título
        JLabel titulo = Componentes.texto("Cadastro de Produtos", 18, true);
        titulo.setIcon(new Icone(Icone.Tipo.PRODUTO, 24));
        titulo.setIconTextGap(10);
        topo.add(titulo, BorderLayout.WEST);

        // Busca e filtros
        JPanel filtros = Componentes.painel(new BorderLayout(8, 8), "fundo");

        // Linha 1: Busca
        JPanel buscaPainel = Componentes.painel(new BorderLayout(8, 0), "fundo");
        JLabel lblBusca = Componentes.texto("Buscar", 13, true);
        lblBusca.setIcon(new Icone(Icone.Tipo.BUSCA, 18));
        lblBusca.setIconTextGap(8);
        buscaPainel.add(lblBusca, BorderLayout.WEST);

        txtBusca = new JTextField();
        txtBusca.setPreferredSize(new Dimension(300, 38));
        txtBusca.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        GerenciadorTema.estilo(txtBusca, "inputFundo", "inputTexto");
        txtBusca.putClientProperty("JTextField.placeholderText", "Código, nome ou categoria...");
        txtBusca.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filtrar(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filtrar(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filtrar(); }
        });
        buscaPainel.add(txtBusca, BorderLayout.CENTER);

        // Linha 2: Categoria + Ativos
        JPanel filtrosLinha2 = Componentes.painel(new BorderLayout(8, 0), "fundo");

        JLabel lblCategoria = Componentes.texto("Categoria:", 13, true);
        lblCategoria.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        filtrosLinha2.add(lblCategoria, BorderLayout.WEST);

        comboCategoria = new JComboBox<>();
        comboCategoria.setPreferredSize(new Dimension(180, 38));
        comboCategoria.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        GerenciadorTema.estilo(comboCategoria, "inputFundo", "inputTexto");
        comboCategoria.addActionListener(e -> filtrar());
        filtrosLinha2.add(comboCategoria, BorderLayout.CENTER);

        chkApenasAtivos = new JCheckBox("Apenas ativos");
        chkApenasAtivos.setSelected(true);
        chkApenasAtivos.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        GerenciadorTema.estilo(chkApenasAtivos, null, "texto");
        chkApenasAtivos.addActionListener(e -> filtrar());
        filtrosLinha2.add(chkApenasAtivos, BorderLayout.EAST);

        JPanel filtrosCompleto = Componentes.painel(new GridLayout(2, 1, 8, 0), "fundo");
        filtrosCompleto.add(buscaPainel);
        filtrosCompleto.add(filtrosLinha2);
        filtros.add(filtrosCompleto, BorderLayout.CENTER);

        topo.add(filtros, BorderLayout.CENTER);

        // Botões de ação
        JPanel botoes = Componentes.painel(new GridLayout(1, 4, 8, 0), "fundo");

        btnNovo = Componentes.botao("Novo (F2)", Icone.Tipo.PRODUTO, this::novoProduto);
        Componentes.primario(btnNovo);
        botoes.add(btnNovo);

        btnEditar = Componentes.botao("Editar", Icone.Tipo.CONFIGURACAO, this::editarProduto);
        GerenciadorTema.estilo(btnEditar, "painelElevado", "texto");
        btnEditar.setEnabled(false);
        botoes.add(btnEditar);

        btnAtivarDesativar = Componentes.botao("Desativar", Icone.Tipo.X, this::alternarAtivo);
        GerenciadorTema.estilo(btnAtivarDesativar, "painelElevado", "texto");
        btnAtivarDesativar.setEnabled(false);
        botoes.add(btnAtivarDesativar);

        btnExcluir = Componentes.botao("Excluir", Icone.Tipo.LIXEIRA, this::excluirProduto);
        GerenciadorTema.estilo(btnExcluir, "painelElevado", "muted");
        btnExcluir.setEnabled(false);
        botoes.add(btnExcluir);

        topo.add(botoes, BorderLayout.EAST);

        return topo;
    }

    private JComponent montarCentro() {
        JPanel centro = Componentes.painel(new BorderLayout(), "fundo");

        String[] colunas = {"ID", "Código", "Nome", "Categoria", "Preço", "Estoque", "Ativo", "Imagem"};
        modeloTabela = new DefaultTableModel(colunas, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int columnIndex) {
                return switch (columnIndex) {
                    case 0 -> Integer.class;
                    case 4 -> BigDecimal.class;
                    case 5 -> Integer.class;
                    case 6 -> Boolean.class;
                    default -> String.class;
                };
            }
        };

        tabela = new JTable(modeloTabela);
        tabela.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabela.setRowHeight(32);
        tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabela.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabela.getTableHeader().setReorderingAllowed(false);
        tabela.setAutoCreateRowSorter(true);
        tabela.setFillsViewportHeight(true);
        tabela.setOpaque(false);

        sorter = new TableRowSorter<>(modeloTabela);
        tabela.setRowSorter(sorter);

        // Configurar renderizadores
        configurarRenderizadores();

        // Seleção
        tabela.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                boolean temSelecao = tabela.getSelectedRow() >= 0;
                btnEditar.setEnabled(temSelecao);
                btnAtivarDesativar.setEnabled(temSelecao);
                btnExcluir.setEnabled(temSelecao);
                atualizarBotaoAtivarDesativar();
            }
        });

        // Atalhos
        configurarAtalhos();

        JScrollPane scroll = new JScrollPane(tabela, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 1, 0, GerenciadorTema.cor("borda")),
            BorderFactory.createEmptyBorder(8, 0, 8, 0)));
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        GerenciadorTema.estilo(scroll.getViewport(), "fundo", null);

        centro.add(scroll, BorderLayout.CENTER);
        return centro;
    }

    private void configurarRenderizadores() {
        // Coluna Preço - alinhar à direita, formatar moeda
        tabela.getColumnModel().getColumn(4).setCellRenderer((table, value, isSelected, hasFocus, row, col) -> {
            JLabel label = new JLabel();
            label.setHorizontalAlignment(SwingConstants.RIGHT);
            label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
            if (value instanceof BigDecimal bd) {
                label.setText(br.com.adriano.ui.pdv.Dinheiro.formatar(bd));
            }
            if (isSelected) {
                label.setBackground(GerenciadorTema.cor("destaque"));
                label.setForeground(GerenciadorTema.cor("sobreDestaque"));
                label.setOpaque(true);
            } else {
                label.setBackground(GerenciadorTema.cor("painel"));
                label.setForeground(GerenciadorTema.cor("texto"));
                label.setOpaque(true);
            }
            return label;
        });

        // Coluna Estoque - alinhar centro, destacar baixo
        tabela.getColumnModel().getColumn(5).setCellRenderer((table, value, isSelected, hasFocus, row, col) -> {
            JLabel label = new JLabel();
            label.setHorizontalAlignment(SwingConstants.CENTER);
            int estoque = value instanceof Integer ? (Integer) value : 0;
            label.setText(String.valueOf(estoque));
            if (estoque <= 5 && estoque > 0) {
                label.setIcon(new Icone(Icone.Tipo.ESTOQUE, 14));
            } else if (estoque == 0) {
                label.setIcon(new Icone(Icone.Tipo.LIXEIRA, 14));
                label.setForeground(GerenciadorTema.cor("muted"));
            }
            if (isSelected) {
                label.setBackground(GerenciadorTema.cor("destaque"));
                label.setForeground(GerenciadorTema.cor("sobreDestaque"));
                label.setOpaque(true);
            } else {
                label.setBackground(GerenciadorTema.cor("painel"));
                label.setForeground(estoque == 0 ? GerenciadorTema.cor("muted") : GerenciadorTema.cor("texto"));
                label.setOpaque(true);
            }
            return label;
        });

        // Coluna Ativo - checkbox
        tabela.getColumnModel().getColumn(6).setCellRenderer((table, value, isSelected, hasFocus, row, col) -> {
            JCheckBox check = new JCheckBox();
            check.setHorizontalAlignment(SwingConstants.CENTER);
            check.setSelected(value instanceof Boolean ? (Boolean) value : false);
            check.setEnabled(false);
            if (isSelected) {
                check.setBackground(GerenciadorTema.cor("destaque"));
            } else {
                check.setBackground(GerenciadorTema.cor("painel"));
            }
            check.setOpaque(true);
            return check;
        });

        // Coluna Imagem - ícone
        tabela.getColumnModel().getColumn(7).setCellRenderer((table, value, isSelected, hasFocus, row, col) -> {
            JLabel label = new JLabel();
            label.setHorizontalAlignment(SwingConstants.CENTER);
            String img = value instanceof String ? (String) value : null;
            if (img != null && !img.isBlank()) {
                label.setIcon(new Icone(Icone.Tipo.IMAGEM, 18));
                label.setToolTipText("Possui imagem: " + img);
            } else {
                label.setIcon(new Icone(Icone.Tipo.IMAGEM, 18));
                label.setForeground(GerenciadorTema.cor("muted"));
                label.setToolTipText("Sem imagem");
            }
            if (isSelected) {
                label.setBackground(GerenciadorTema.cor("destaque"));
                label.setOpaque(true);
            } else {
                label.setBackground(GerenciadorTema.cor("painel"));
                label.setOpaque(true);
            }
            return label;
        });

        // Larguras das colunas
        tabela.getColumnModel().getColumn(0).setPreferredWidth(50);  // ID
        tabela.getColumnModel().getColumn(1).setPreferredWidth(100); // Código
        tabela.getColumnModel().getColumn(2).setPreferredWidth(250); // Nome
        tabela.getColumnModel().getColumn(3).setPreferredWidth(140); // Categoria
        tabela.getColumnModel().getColumn(4).setPreferredWidth(100); // Preço
        tabela.getColumnModel().getColumn(5).setPreferredWidth(80);  // Estoque
        tabela.getColumnModel().getColumn(6).setPreferredWidth(60);  // Ativo
        tabela.getColumnModel().getColumn(7).setPreferredWidth(60);  // Imagem
    }

    private JComponent montarRodape() {
        JPanel rodape = Componentes.painel(new BorderLayout(), "fundo");

        lblTotal = Componentes.texto("Total: 0 produtos", 13, false);
        lblTotal.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        rodape.add(lblTotal, BorderLayout.WEST);

        return rodape;
    }

    private void carregarDados() {
        try {
            // Carregar categorias
            List<String> categorias = service.listarCategorias();
            comboCategoria.removeAllItems();
            comboCategoria.addItem("Todas");
            for (String cat : categorias) {
                comboCategoria.addItem(cat);
            }

            // Carregar produtos
            List<Produto> produtos = service.listarTodos(chkApenasAtivos.isSelected());
            atualizarTabela(produtos);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao carregar produtos: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void atualizarTabela(List<Produto> produtos) {
        modeloTabela.setRowCount(0);
        for (Produto p : produtos) {
            modeloTabela.addRow(new Object[]{
                p.getId(),
                p.getCodigo(),
                p.getNome(),
                p.getCategoria(),
                p.getPrecoBigDecimal(),
                p.getEstoque(),
                p.isAtivo(),
                p.getImagem()
            });
        }
        lblTotal.setText("Total: " + produtos.size() + " produto" + (produtos.size() == 1 ? "" : "s"));
    }

    private void filtrar() {
        try {
            String busca = txtBusca.getText().trim();
            String categoria = (String) comboCategoria.getSelectedItem();
            boolean apenasAtivos = chkApenasAtivos.isSelected();

            List<Produto> produtos;
            if (!busca.isEmpty()) {
                produtos = service.buscarPorTermo(busca);
            } else if (categoria != null && !"Todas".equals(categoria)) {
                produtos = service.buscarPorCategoria(categoria);
            } else {
                produtos = service.listarTodos(apenasAtivos);
            }

            // Filtrar por ativo se necessário
            if (apenasAtivos) {
                produtos = produtos.stream().filter(Produto::isAtivo).toList();
            }

            atualizarTabela(produtos);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao filtrar: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void novoProduto() {
        ProdutoDialog dialog = new ProdutoDialog((Frame) SwingUtilities.getWindowAncestor(this), service, null);
        dialog.setVisible(true);
        if (dialog.isConfirmado()) {
            carregarDados();
            filtrar();
            aoStatus.accept("Produto cadastrado: " + dialog.getProduto().getNome());
        }
    }

    private void editarProduto() {
        int row = tabela.getSelectedRow();
        if (row < 0) return;
        int modelRow = tabela.convertRowIndexToModel(row);
        int id = (Integer) modeloTabela.getValueAt(modelRow, 0);

        try {
            Produto produto = service.buscarPorId(id).orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));
            ProdutoDialog dialog = new ProdutoDialog((Frame) SwingUtilities.getWindowAncestor(this), service, produto);
            dialog.setVisible(true);
            if (dialog.isConfirmado()) {
                carregarDados();
                filtrar();
                aoStatus.accept("Produto atualizado: " + dialog.getProduto().getNome());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao editar: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void alternarAtivo() {
        int row = tabela.getSelectedRow();
        if (row < 0) return;
        int modelRow = tabela.convertRowIndexToModel(row);
        int id = (Integer) modeloTabela.getValueAt(modelRow, 0);
        boolean ativo = (Boolean) modeloTabela.getValueAt(modelRow, 6);
        String nome = (String) modeloTabela.getValueAt(modelRow, 2);

        int op = JOptionPane.showConfirmDialog(this,
            "Deseja " + (ativo ? "desativar" : "ativar") + " o produto \"" + nome + "\"?",
            ativo ? "Desativar Produto" : "Ativar Produto",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (op == JOptionPane.YES_OPTION) {
            try {
                Produto produto = service.buscarPorId(id).orElseThrow();
                produto.setAtivo(!ativo);
                service.atualizar(produto);
                filtrar();
                aoStatus.accept("Produto " + (ativo ? "desativado" : "ativado") + ": " + nome);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void excluirProduto() {
        int row = tabela.getSelectedRow();
        if (row < 0) return;
        int modelRow = tabela.convertRowIndexToModel(row);
        int id = (Integer) modeloTabela.getValueAt(modelRow, 0);
        String nome = (String) modeloTabela.getValueAt(modelRow, 2);

        int op = JOptionPane.showConfirmDialog(this,
            "Excluir permanentemente o produto \"" + nome + "\"?\n\n" +
            "Atenção: só é possível excluir produtos sem vendas associadas.\n" +
            "Se houver vendas, o produto será apenas desativado.",
            "Excluir Produto",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (op == JOptionPane.YES_OPTION) {
            try {
                boolean excluido = service.excluir(id);
                if (excluido) {
                    filtrar();
                    aoStatus.accept("Produto excluído: " + nome);
                } else {
                    JOptionPane.showMessageDialog(this, "Produto não encontrado.", "Aviso", JOptionPane.WARNING_MESSAGE);
                }
            } catch (IllegalStateException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Não permitido", JOptionPane.WARNING_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void atualizarBotaoAtivarDesativar() {
        int row = tabela.getSelectedRow();
        if (row >= 0) {
            int modelRow = tabela.convertRowIndexToModel(row);
            boolean ativo = (Boolean) modeloTabela.getValueAt(modelRow, 6);
            btnAtivarDesativar.setText(ativo ? "Desativar" : "Ativar");
            btnAtivarDesativar.setIcon(new Icone(ativo ? Icone.Tipo.X : Icone.Tipo.CHECK, 20));
        }
    }

    private void configurarAtalhos() {
        InputMap im = tabela.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = tabela.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "novo");
        am.put("novo", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { novoProduto(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "editar");
        am.put("editar", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { editarProduto(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "excluir");
        am.put("excluir", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { excluirProduto(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "alternar");
        am.put("alternar", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { alternarAtivo(); }
        });
    }

    /**
     * Dialog para cadastro/edição de produto.
     */
    private static class ProdutoDialog extends JDialog {
        private final ProdutoService service;
        private final Produto produtoOriginal;
        private Produto produtoResultado;
        private boolean confirmado = false;

        private JTextField txtCodigo;
        private JTextField txtNome;
        private JComboBox<String> comboCategoria;
        private JFormattedTextField txtPreco;
        private JSpinner spnEstoque;
        private JTextField txtImagem;
        private JCheckBox chkAtivo;

        public ProdutoDialog(Frame parent, ProdutoService service, Produto produto) {
            super(parent, produto == null ? "Novo Produto" : "Editar Produto", true);
            this.service = service;
            this.produtoOriginal = produto;

            setLayout(new BorderLayout(12, 12));
            setPreferredSize(new Dimension(520, 480));
            setResizable(false);
            GerenciadorTema.aplicar(this);

            add(montarFormulario(), BorderLayout.CENTER);
            add(montarBotoes(), BorderLayout.SOUTH);

            pack();
            setLocationRelativeTo(parent);

            if (produto != null) {
                preencherCampos(produto);
            } else {
                chkAtivo.setSelected(true);
            }
        }

        private JComponent montarFormulario() {
            JPanel form = Componentes.painel(new GridBagLayout(), "fundo");
            form.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(8, 8, 8, 8);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1;

            // Código
            gbc.gridy = 0; gbc.gridx = 0;
            form.add(Componentes.texto("Código *", 13, true), gbc);
            gbc.gridx = 1;
            txtCodigo = new JTextField();
            txtCodigo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            GerenciadorTema.estilo(txtCodigo, "inputFundo", "inputTexto");
            txtCodigo.setPreferredSize(new Dimension(200, 36));
            form.add(txtCodigo, gbc);

            // Nome
            gbc.gridy = 1; gbc.gridx = 0;
            form.add(Componentes.texto("Nome *", 13, true), gbc);
            gbc.gridx = 1;
            txtNome = new JTextField();
            txtNome.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            GerenciadorTema.estilo(txtNome, "inputFundo", "inputTexto");
            form.add(txtNome, gbc);

            // Categoria
            gbc.gridy = 2; gbc.gridx = 0;
            form.add(Componentes.texto("Categoria", 13, true), gbc);
            gbc.gridx = 1;
            comboCategoria = new JComboBox<>();
            comboCategoria.setEditable(true);
            comboCategoria.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            GerenciadorTema.estilo(comboCategoria, "inputFundo", "inputTexto");
            try {
                List<String> cats = service.listarCategorias();
                for (String c : cats) comboCategoria.addItem(c);
            } catch (Exception ignored) {}
            form.add(comboCategoria, gbc);

            // Preço
            gbc.gridy = 3; gbc.gridx = 0;
            form.add(Componentes.texto("Preço (R$) *", 13, true), gbc);
            gbc.gridx = 1;
            txtPreco = new JFormattedTextField(new java.text.DecimalFormat("#,##0.00"));
            txtPreco.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            txtPreco.setValue(BigDecimal.ZERO);
            GerenciadorTema.estilo(txtPreco, "inputFundo", "inputTexto");
            txtPreco.setPreferredSize(new Dimension(150, 36));
            form.add(txtPreco, gbc);

            // Estoque
            gbc.gridy = 4; gbc.gridx = 0;
            form.add(Componentes.texto("Estoque Inicial", 13, true), gbc);
            gbc.gridx = 1;
            spnEstoque = new JSpinner(new SpinnerNumberModel(0, 0, 999999, 1));
            spnEstoque.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            JComponent editor = spnEstoque.getEditor();
            if (editor instanceof JSpinner.DefaultEditor de) {
                GerenciadorTema.estilo(de.getTextField(), "inputFundo", "inputTexto");
            }
            form.add(spnEstoque, gbc);

            // Imagem
            gbc.gridy = 5; gbc.gridx = 0;
            form.add(Componentes.texto("Caminho da Imagem", 13, true), gbc);
            gbc.gridx = 1;
            txtImagem = new JTextField();
            txtImagem.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            GerenciadorTema.estilo(txtImagem, "inputFundo", "inputTexto");
            txtImagem.putClientProperty("JTextField.placeholderText", "ex: imagens/produto.jpg (opcional)");
            form.add(txtImagem, gbc);

            // Ativo
            gbc.gridy = 6; gbc.gridx = 0; gbc.gridwidth = 2;
            chkAtivo = new JCheckBox("Produto ativo (visível no PDV)");
            chkAtivo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            GerenciadorTema.estilo(chkAtivo, null, "texto");
            form.add(chkAtivo, gbc);

            // Nota
            gbc.gridy = 7; gbc.insets = new Insets(16, 8, 0, 8);
            JLabel nota = Componentes.secundario("* Campos obrigatórios. Código deve ser único.");
            form.add(nota, gbc);

            return form;
        }

        private JComponent montarBotoes() {
            JPanel botoes = Componentes.painel(new FlowLayout(FlowLayout.RIGHT, 8, 8), "fundo");

            JButton btnCancelar = Componentes.botao("Cancelar", Icone.Tipo.X, this::dispose);
            GerenciadorTema.estilo(btnCancelar, "painelElevado", "texto");
            botoes.add(btnCancelar);

            JButton btnSalvar = Componentes.botao("Salvar", Icone.Tipo.CHECK, this::salvar);
            Componentes.primario(btnSalvar);
            botoes.add(btnSalvar);

            getRootPane().setDefaultButton(btnSalvar);
            return botoes;
        }

        private void preencherCampos(Produto p) {
            txtCodigo.setText(p.getCodigo());
            txtNome.setText(p.getNome());
            if (p.getCategoria() != null) comboCategoria.setSelectedItem(p.getCategoria());
            txtPreco.setValue(p.getPrecoBigDecimal());
            spnEstoque.setValue(p.getEstoque());
            txtImagem.setText(p.getImagem() != null ? p.getImagem() : "");
            chkAtivo.setSelected(p.isAtivo());
        }

        private void salvar() {
            try {
                String codigo = txtCodigo.getText().trim();
                String nome = txtNome.getText().trim();
                String categoria = ((String) comboCategoria.getSelectedItem());
                if (categoria != null) categoria = categoria.trim();
                BigDecimal preco = (BigDecimal) txtPreco.getValue();
                int estoque = (Integer) spnEstoque.getValue();
                String imagem = txtImagem.getText().trim();
                boolean ativo = chkAtivo.isSelected();

                if (codigo.isEmpty() || nome.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Código e Nome são obrigatórios.", "Validação", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (preco == null || preco.signum() < 0) {
                    JOptionPane.showMessageDialog(this, "Preço deve ser >= 0.", "Validação", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                Produto produto;
                if (produtoOriginal == null) {
                    produto = new Produto(null, codigo, nome, categoria, preco, estoque,
                            imagem.isEmpty() ? null : imagem, ativo);
                    produtoResultado = service.salvar(produto);
                } else {
                    produto = new Produto(produtoOriginal.getId(), codigo, nome, categoria, preco, estoque,
                            imagem.isEmpty() ? null : imagem, ativo);
                    produtoResultado = service.atualizar(produto);
                }

                confirmado = true;
                dispose();

            } catch (java.sql.SQLIntegrityConstraintViolationException ex) {
                JOptionPane.showMessageDialog(this, "Código já cadastrado. Use outro código.", "Erro", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erro ao salvar: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }

        public boolean isConfirmado() { return confirmado; }
        public Produto getProduto() { return produtoResultado; }
    }
}