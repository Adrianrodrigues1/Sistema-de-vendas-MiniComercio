package br.com.adriano.ui.estoque;

import br.com.adriano.db.ConexaoManager;
import br.com.adriano.model.Produto;
import br.com.adriano.service.ProdutoService;
import br.com.adriano.ui.componentes.Componentes;
import br.com.adriano.ui.componentes.Icone;
import br.com.adriano.ui.tema.GerenciadorTema;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Painel de controle de estoque.
 * <p>
 * Funcionalidades:
 * - Ajuste de estoque com motivo e auditoria (movimentos_estoque)
 * - Relatório de produtos abaixo do mínimo
 * - Importação/exportação CSV
 * - Histórico de movimentos
 */
public final class PainelEstoque extends JPanel {

    private final ProdutoService service;
    private final ConexaoManager conexaoManager;
    private final Consumer<String> aoStatus;
    private DefaultTableModel modeloTabela;
    private JTable tabela;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField txtBusca;
    private JComboBox<String> comboCategoria;
    private JComboBox<String> comboFiltroEstoque;
    private JButton btnAjustar;
    private JButton btnBaixoEstoque;
    private JButton btnImportar;
    private JButton btnExportar;
    private JButton btnHistorico;
    private JLabel lblTotal;

    public PainelEstoque(ProdutoService service, ConexaoManager conexaoManager, Consumer<String> aoStatus) {
        this.service = service;
        this.conexaoManager = conexaoManager;
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
        JLabel titulo = Componentes.texto("Controle de Estoque", 18, true);
        titulo.setIcon(new Icone(Icone.Tipo.ESTOQUE, 24));
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

        // Linha 2: Categoria + Filtro Estoque
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

        JLabel lblFiltroEstoque = Componentes.texto("Estoque:", 13, true);
        lblFiltroEstoque.setBorder(BorderFactory.createEmptyBorder(8, 16, 0, 0));
        filtrosLinha2.add(lblFiltroEstoque, BorderLayout.EAST);

        comboFiltroEstoque = new JComboBox<>(new String[]{"Todos", "Baixo (<=5)", "Zerados", "Normal (>5)"});
        comboFiltroEstoque.setPreferredSize(new Dimension(150, 38));
        comboFiltroEstoque.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        GerenciadorTema.estilo(comboFiltroEstoque, "inputFundo", "inputTexto");
        comboFiltroEstoque.addActionListener(e -> filtrar());
        filtrosLinha2.add(comboFiltroEstoque, BorderLayout.EAST);

        JPanel filtrosCompleto = Componentes.painel(new GridLayout(2, 1, 8, 0), "fundo");
        filtrosCompleto.add(buscaPainel);
        filtrosCompleto.add(filtrosLinha2);
        filtros.add(filtrosCompleto, BorderLayout.CENTER);

        topo.add(filtros, BorderLayout.CENTER);

        // Botões de ação
        JPanel botoes = Componentes.painel(new GridLayout(1, 5, 8, 0), "fundo");

        btnAjustar = Componentes.botao("Ajustar (F3)", Icone.Tipo.CONFIGURACAO, this::ajustarEstoque);
        Componentes.primario(btnAjustar);
        btnAjustar.setEnabled(false);
        botoes.add(btnAjustar);

        btnBaixoEstoque = Componentes.botao("Baixo Estoque", Icone.Tipo.RELATORIO, this::mostrarBaixoEstoque);
        GerenciadorTema.estilo(btnBaixoEstoque, "painelElevado", "texto");
        botoes.add(btnBaixoEstoque);

        btnHistorico = Componentes.botao("Histórico", Icone.Tipo.RELATORIO, this::mostrarHistorico);
        GerenciadorTema.estilo(btnHistorico, "painelElevado", "texto");
        botoes.add(btnHistorico);

        btnImportar = Componentes.botao("Importar CSV", Icone.Tipo.SETABAIXO, this::importarCSV);
        GerenciadorTema.estilo(btnImportar, "painelElevado", "texto");
        botoes.add(btnImportar);

        btnExportar = Componentes.botao("Exportar CSV", Icone.Tipo.SETACIMA, this::exportarCSV);
        GerenciadorTema.estilo(btnExportar, "painelElevado", "texto");
        botoes.add(btnExportar);

        topo.add(botoes, BorderLayout.EAST);

        return topo;
    }

    private JComponent montarCentro() {
        JPanel centro = Componentes.painel(new BorderLayout(), "fundo");

        String[] colunas = {"ID", "Código", "Nome", "Categoria", "Preço", "Estoque", "Mínimo", "Status", "Último Movimento"};
        modeloTabela = new DefaultTableModel(colunas, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int columnIndex) {
                return switch (columnIndex) {
                    case 0 -> Integer.class;
                    case 4 -> BigDecimal.class;
                    case 5 -> Integer.class;
                    case 6 -> Integer.class;
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

        configurarRenderizadores();

        tabela.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                boolean temSelecao = tabela.getSelectedRow() >= 0;
                btnAjustar.setEnabled(temSelecao);
            }
        });

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
        // Coluna Preço
        tabela.getColumnModel().getColumn(4).setCellRenderer((table, value, isSelected, hasFocus, row, col) -> {
            JLabel label = new JLabel();
            label.setHorizontalAlignment(SwingConstants.RIGHT);
            label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
            if (value instanceof BigDecimal bd) {
                label.setText(br.com.adriano.ui.pdv.Dinheiro.formatar(bd));
            }
            aplicarEstiloLinha(label, isSelected);
            return label;
        });

        // Coluna Estoque - destaque visual
        tabela.getColumnModel().getColumn(5).setCellRenderer((table, value, isSelected, hasFocus, row, col) -> {
            JLabel label = new JLabel();
            label.setHorizontalAlignment(SwingConstants.CENTER);
            int estoque = value instanceof Integer ? (Integer) value : 0;
            label.setText(String.valueOf(estoque));
            if (estoque == 0) {
                label.setForeground(GerenciadorTema.cor("muted"));
                label.setIcon(new Icone(Icone.Tipo.LIXEIRA, 14));
            } else if (estoque <= 5) {
                label.setForeground(GerenciadorTema.cor("muted"));
                label.setIcon(new Icone(Icone.Tipo.ESTOQUE, 14));
            }
            aplicarEstiloLinha(label, isSelected);
            return label;
        });

        // Coluna Mínimo
        tabela.getColumnModel().getColumn(6).setCellRenderer((table, value, isSelected, hasFocus, row, col) -> {
            JLabel label = new JLabel();
            label.setHorizontalAlignment(SwingConstants.CENTER);
            int min = value instanceof Integer ? (Integer) value : 5;
            label.setText(String.valueOf(min));
            aplicarEstiloLinha(label, isSelected);
            return label;
        });

        // Coluna Status - badge colorido
        tabela.getColumnModel().getColumn(7).setCellRenderer((table, value, isSelected, hasFocus, row, col) -> {
            JLabel label = new JLabel();
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setOpaque(true);
            String status = value instanceof String ? (String) value : "OK";
            label.setText(" " + status + " ");
            label.setFont(new Font("Segoe UI", Font.BOLD, 11));
            label.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            if (!isSelected) {
                switch (status) {
                    case "ZERADO" -> {
                        label.setBackground(new Color(0xC0392B));
                        label.setForeground(Color.WHITE);
                    }
                    case "BAIXO" -> {
                        label.setBackground(new Color(0xE67E22));
                        label.setForeground(Color.WHITE);
                    }
                    default -> {
                        label.setBackground(new Color(0x27AE60));
                        label.setForeground(Color.WHITE);
                    }
                }
            } else {
                label.setBackground(GerenciadorTema.cor("destaque"));
                label.setForeground(GerenciadorTema.cor("sobreDestaque"));
            }
            return label;
        });

        // Coluna Último Movimento
        tabela.getColumnModel().getColumn(8).setCellRenderer((table, value, isSelected, hasFocus, row, col) -> {
            JLabel label = new JLabel();
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            label.setForeground(GerenciadorTema.cor("muted"));
            if (value instanceof String) label.setText((String) value);
            aplicarEstiloLinha(label, isSelected);
            return label;
        });

        // Larguras
        tabela.getColumnModel().getColumn(0).setPreferredWidth(50);
        tabela.getColumnModel().getColumn(1).setPreferredWidth(100);
        tabela.getColumnModel().getColumn(2).setPreferredWidth(250);
        tabela.getColumnModel().getColumn(3).setPreferredWidth(140);
        tabela.getColumnModel().getColumn(4).setPreferredWidth(100);
        tabela.getColumnModel().getColumn(5).setPreferredWidth(70);
        tabela.getColumnModel().getColumn(6).setPreferredWidth(70);
        tabela.getColumnModel().getColumn(7).setPreferredWidth(80);
        tabela.getColumnModel().getColumn(8).setPreferredWidth(150);
    }

    private void aplicarEstiloLinha(JLabel label, boolean isSelected) {
        if (isSelected) {
            label.setBackground(GerenciadorTema.cor("destaque"));
            label.setForeground(GerenciadorTema.cor("sobreDestaque"));
            label.setOpaque(true);
        } else {
            label.setBackground(GerenciadorTema.cor("painel"));
            label.setOpaque(true);
        }
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
            List<String> categorias = service.listarCategorias();
            comboCategoria.removeAllItems();
            comboCategoria.addItem("Todas");
            for (String cat : categorias) {
                comboCategoria.addItem(cat);
            }

            filtrar();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao carregar: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void filtrar() {
        try {
            String busca = txtBusca.getText().trim();
            String categoria = (String) comboCategoria.getSelectedItem();
            String filtroEstoque = (String) comboFiltroEstoque.getSelectedItem();

            List<Produto> produtos;
            if (!busca.isEmpty()) {
                produtos = service.buscarPorTermo(busca);
            } else if (categoria != null && !"Todas".equals(categoria)) {
                produtos = service.buscarPorCategoria(categoria);
            } else {
                produtos = service.listarTodos(false);
            }

            // Aplicar filtro de estoque
            if ("Baixo (<=5)".equals(filtroEstoque)) {
                produtos = produtos.stream().filter(p -> p.getEstoque() > 0 && p.getEstoque() <= 5).toList();
            } else if ("Zerados".equals(filtroEstoque)) {
                produtos = produtos.stream().filter(p -> p.getEstoque() == 0).toList();
            } else if ("Normal (>5)".equals(filtroEstoque)) {
                produtos = produtos.stream().filter(p -> p.getEstoque() > 5).toList();
            }

            atualizarTabela(produtos);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao filtrar: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void atualizarTabela(List<Produto> produtos) {
        modeloTabela.setRowCount(0);
        for (Produto p : produtos) {
            int estoque = p.getEstoque();
            String status;
            if (estoque == 0) status = "ZERADO";
            else if (estoque <= 5) status = "BAIXO";
            else status = "OK";

            String ultimoMov = buscarUltimoMovimento(p.getId());

            modeloTabela.addRow(new Object[]{
                p.getId(),
                p.getCodigo(),
                p.getNome(),
                p.getCategoria(),
                p.getPrecoBigDecimal(),
                estoque,
                5, // mínimo padrão
                status,
                ultimoMov
            });
        }
        lblTotal.setText("Total: " + produtos.size() + " produto" + (produtos.size() == 1 ? "" : "s"));
    }

    private String buscarUltimoMovimento(int produtoId) {
        try (Connection conn = conexaoManager.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT tipo, quantidade, data FROM movimentos_estoque WHERE produto_id = " + produtoId + " ORDER BY id DESC LIMIT 1")) {
            if (rs.next()) {
                String tipo = rs.getString("tipo");
                int qtd = rs.getInt("quantidade");
                String data = rs.getString("data");
                return tipo + " " + qtd + " (" + data.substring(0, Math.min(16, data.length())) + ")";
            }
        } catch (Exception ignored) {}
        return "—";
    }

    private void ajustarEstoque() {
        int row = tabela.getSelectedRow();
        if (row < 0) return;
        int modelRow = tabela.convertRowIndexToModel(row);
        int id = (Integer) modeloTabela.getValueAt(modelRow, 0);
        String codigo = (String) modeloTabela.getValueAt(modelRow, 1);
        String nome = (String) modeloTabela.getValueAt(modelRow, 2);
        int estoqueAtual = (Integer) modeloTabela.getValueAt(modelRow, 5);

        AjusteDialog dialog = new AjusteDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            service, id, codigo, nome, estoqueAtual);
        dialog.setVisible(true);
        if (dialog.isConfirmado()) {
            filtrar();
            aoStatus.accept("Estoque ajustado: " + nome + " (" + dialog.getDelta() + ")");
        }
    }

    private void mostrarBaixoEstoque() {
        try {
            List<Produto> todos = service.listarTodos(false);
            List<Produto> baixos = todos.stream()
                .filter(p -> p.getEstoque() <= 5 && p.getEstoque() > 0)
                .toList();
            List<Produto> zerados = todos.stream()
                .filter(p -> p.getEstoque() == 0)
                .toList();

            StringBuilder sb = new StringBuilder();
            sb.append("=== RELATÓRIO DE BAIXO ESTOQUE ===\n");
            sb.append("Gerado em: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).append("\n\n");

            if (!zerados.isEmpty()) {
                sb.append("--- PRODUTOS ZERADOS (").append(zerados.size()).append(") ---\n");
                for (Produto p : zerados) {
                    sb.append(String.format("  %s - %s | Cat: %s | Preço: %s\n",
                        p.getCodigo(), p.getNome(), p.getCategoria(), br.com.adriano.ui.pdv.Dinheiro.formatar(p.getPrecoBigDecimal())));
                }
                sb.append("\n");
            }

            if (!baixos.isEmpty()) {
                sb.append("--- PRODUTOS ABAIXO DO MÍNIMO (").append(baixos.size()).append(") ---\n");
                for (Produto p : baixos) {
                    sb.append(String.format("  %s - %s | Estoque: %d | Cat: %s | Preço: %s\n",
                        p.getCodigo(), p.getNome(), p.getEstoque(), p.getCategoria(), br.com.adriano.ui.pdv.Dinheiro.formatar(p.getPrecoBigDecimal())));
                }
                sb.append("\n");
            }

            if (zerados.isEmpty() && baixos.isEmpty()) {
                sb.append("Nenhum produto com estoque baixo ou zerado.\n");
            }

            sb.append("\nTotal de produtos: ").append(todos.size());

            JTextArea area = new JTextArea(sb.toString());
            area.setFont(new Font("Monospaced", Font.PLAIN, 12));
            area.setEditable(false);
            area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            GerenciadorTema.estilo(area, "painel", "texto");

            JScrollPane scroll = new JScrollPane(area);
            scroll.setPreferredSize(new Dimension(550, 400));
            GerenciadorTema.estilo(scroll.getViewport(), "painel", null);

            JOptionPane.showMessageDialog(this, scroll, "Relatório de Baixo Estoque", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void mostrarHistorico() {
        int row = tabela.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecione um produto para ver o histórico.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = tabela.convertRowIndexToModel(row);
        int id = (Integer) modeloTabela.getValueAt(modelRow, 0);
        String nome = (String) modeloTabela.getValueAt(modelRow, 2);

        try (Connection conn = conexaoManager.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT tipo, quantidade, quantidade_anterior, quantidade_nova, motivo, referencia_tipo, referencia_id, usuario_id, data FROM movimentos_estoque WHERE produto_id = " + id + " ORDER BY id DESC")) {

            String[] colunas = {"Tipo", "Qtd", "Ant.", "Nova", "Motivo", "Ref. Tipo", "Ref. ID", "Usuário", "Data"};
            DefaultTableModel model = new DefaultTableModel(colunas, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };

            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getString("tipo"),
                    rs.getInt("quantidade"),
                    rs.getInt("quantidade_anterior"),
                    rs.getInt("quantidade_nova"),
                    rs.getString("motivo"),
                    rs.getString("referencia_tipo"),
                    rs.getInt("referencia_id"),
                    rs.getInt("usuario_id"),
                    rs.getString("data")
                });
            }

            JTable table = new JTable(model);
            table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            table.setRowHeight(28);
            table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
            GerenciadorTema.estilo(table, "painel", "texto");
            GerenciadorTema.estilo(table.getTableHeader(), "painelElevado", "texto");

            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(700, 350));
            GerenciadorTema.estilo(scroll.getViewport(), "painel", null);

            JOptionPane.showMessageDialog(this, scroll, "Histórico de Movimentos - " + nome, JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao carregar histórico: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void importarCSV() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Importar Produtos (CSV)");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV", "csv"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) throw new IOException("Arquivo vazio");

            String[] expected = {"codigo", "nome", "categoria", "preco", "estoque", "imagem", "ativo"};
            String[] cols = header.split(",");
            if (cols.length < 5) throw new IOException("Formato inválido. Esperado: codigo,nome,categoria,preco,estoque[,imagem,ativo]");

            int importados = 0, atualizados = 0, erros = 0;
            String linha;
            while ((linha = br.readLine()) != null) {
                if (linha.trim().isEmpty()) continue;
                String[] v = parseCSV(linha);
                if (v.length < 5) { erros++; continue; }

                try {
                    String codigo = v[0].trim();
                    String nome = v[1].trim();
                    String categoria = v.length > 2 ? v[2].trim() : "";
                    BigDecimal preco = new BigDecimal(v[3].trim().replace(",", "."));
                    int estoque = Integer.parseInt(v[4].trim());
                    String imagem = v.length > 5 && !v[5].trim().isEmpty() ? v[5].trim() : null;
                    boolean ativo = v.length <= 6 || !"0".equals(v[6].trim()) && !"false".equalsIgnoreCase(v[6].trim());

                    var opt = service.buscarPorCodigo(codigo);
                    if (opt.isPresent()) {
                        Produto p = opt.get();
                        p.setNome(nome);
                        p.setCategoria(categoria);
                        p.setPrecoBigDecimal(preco);
                        p.setEstoque(estoque);
                        p.setImagem(imagem);
                        p.setAtivo(ativo);
                        service.atualizar(p);
                        atualizados++;
                    } else {
                        Produto p = new Produto(null, codigo, nome, categoria, preco, estoque, imagem, ativo);
                        service.salvar(p);
                        importados++;
                    }
                } catch (Exception ex) {
                    erros++;
                }
            }

            filtrar();
            JOptionPane.showMessageDialog(this,
                "Importação concluída:\nNovos: " + importados + "\nAtualizados: " + atualizados + "\nErros: " + erros,
                "Resultado", JOptionPane.INFORMATION_MESSAGE);
            aoStatus.accept("CSV importado: " + importados + " novos, " + atualizados + " atualizados");

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Erro ao ler arquivo: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportarCSV() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Exportar Produtos (CSV)");
        chooser.setSelectedFile(new File("produtos_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".csv"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV", "csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            bw.write("codigo,nome,categoria,preco,estoque,imagem,ativo\n");
            List<Produto> produtos = service.listarTodos(false);
            for (Produto p : produtos) {
                bw.write(String.join(",",
                    escapeCSV(p.getCodigo()),
                    escapeCSV(p.getNome()),
                    escapeCSV(p.getCategoria()),
                    p.getPrecoBigDecimal().toPlainString(),
                    String.valueOf(p.getEstoque()),
                    escapeCSV(p.getImagem() != null ? p.getImagem() : ""),
                    p.isAtivo() ? "true" : "false"
                ));
                bw.newLine();
            }
            JOptionPane.showMessageDialog(this, "Exportados " + produtos.size() + " produtos para:\n" + file.getAbsolutePath(),
                "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            aoStatus.accept("CSV exportado: " + produtos.size() + " produtos");
        } catch (IOException | SQLException ex) {
            JOptionPane.showMessageDialog(this, "Erro ao gravar: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String[] parseCSV(String linha) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }

    private String escapeCSV(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private void configurarAtalhos() {
        InputMap im = tabela.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = tabela.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), "ajustar");
        am.put("ajustar", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { ajustarEstoque(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ajustarEnter");
        am.put("ajustarEnter", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { ajustarEstoque(); }
        });
    }

    /**
     * Dialog para ajuste de estoque com motivo obrigatório.
     */
    private static class AjusteDialog extends JDialog {
        private final ProdutoService service;
        private final int produtoId;
        private final int estoqueAtual;
        private boolean confirmado = false;
        private int delta = 0;

        private JLabel lblEstoqueAtual;
        private JSpinner spnQuantidade;
        private JComboBox<String> comboTipo;
        private JTextArea txtMotivo;

        public AjusteDialog(Frame parent, ProdutoService service, int produtoId, String codigo, String nome, int estoqueAtual) {
            super(parent, "Ajustar Estoque: " + codigo + " - " + nome, true);
            this.service = service;
            this.produtoId = produtoId;
            this.estoqueAtual = estoqueAtual;

            setLayout(new BorderLayout(12, 12));
            setPreferredSize(new Dimension(480, 420));
            setResizable(false);
            GerenciadorTema.aplicar(this);

            add(montarFormulario(), BorderLayout.CENTER);
            add(montarBotoes(), BorderLayout.SOUTH);

            pack();
            setLocationRelativeTo(parent);
        }

        private JComponent montarFormulario() {
            JPanel form = Componentes.painel(new GridBagLayout(), "fundo");
            form.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(8, 8, 8, 8);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1;

            // Estoque atual
            gbc.gridy = 0; gbc.gridx = 0;
            form.add(Componentes.texto("Estoque Atual:", 13, true), gbc);
            gbc.gridx = 1;
            lblEstoqueAtual = Componentes.texto(String.valueOf(estoqueAtual), 16, true);
            lblEstoqueAtual.setForeground(GerenciadorTema.cor("primario"));
            form.add(lblEstoqueAtual, gbc);

            // Tipo de ajuste
            gbc.gridy = 1; gbc.gridx = 0;
            form.add(Componentes.texto("Tipo:", 13, true), gbc);
            gbc.gridx = 1;
            comboTipo = new JComboBox<>(new String[]{"Entrada (+)", "Saída (-)", "Ajuste Absoluto (=)"});
            comboTipo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            GerenciadorTema.estilo(comboTipo, "inputFundo", "inputTexto");
            form.add(comboTipo, gbc);

            // Quantidade
            gbc.gridy = 2; gbc.gridx = 0;
            form.add(Componentes.texto("Quantidade:", 13, true), gbc);
            gbc.gridx = 1;
            spnQuantidade = new JSpinner(new SpinnerNumberModel(1, 0, 999999, 1));
            spnQuantidade.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            if (spnQuantidade.getEditor() instanceof JSpinner.DefaultEditor de) {
                GerenciadorTema.estilo(de.getTextField(), "inputFundo", "inputTexto");
            }
            form.add(spnQuantidade, gbc);

            // Motivo (obrigatório)
            gbc.gridy = 3; gbc.gridx = 0; gbc.anchor = GridBagConstraints.NORTHWEST;
            form.add(Componentes.texto("Motivo *:", 13, true), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 1;
            txtMotivo = new JTextArea();
            txtMotivo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            txtMotivo.setRows(4);
            txtMotivo.setLineWrap(true);
            txtMotivo.setWrapStyleWord(true);
            GerenciadorTema.estilo(txtMotivo, "inputFundo", "inputTexto");
            txtMotivo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GerenciadorTema.cor("borda"), 1, true),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
            JScrollPane scrollMotivo = new JScrollPane(txtMotivo);
            GerenciadorTema.estilo(scrollMotivo.getViewport(), "inputFundo", null);
            form.add(scrollMotivo, gbc);

            // Preview
            gbc.gridy = 4; gbc.gridx = 0; gbc.gridwidth = 2; gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.anchor = GridBagConstraints.WEST; gbc.insets = new Insets(16, 8, 8, 8);
            JLabel lblPreview = Componentes.secundario("Novo estoque: " + estoqueAtual);
            lblPreview.setName("preview");
            form.add(lblPreview, gbc);

            // Atualizar preview ao mudar valores
            javax.swing.event.ChangeListener updatePreview = e -> atualizarPreview(lblPreview);
            spnQuantidade.addChangeListener(updatePreview);
            comboTipo.addActionListener(e -> atualizarPreview(lblPreview));

            return form;
        }

        private void atualizarPreview(JLabel lbl) {
            int qtd = (Integer) spnQuantidade.getValue();
            int tipo = comboTipo.getSelectedIndex();
            int novoEstoque;
            switch (tipo) {
                case 0 -> novoEstoque = estoqueAtual + qtd;      // Entrada
                case 1 -> novoEstoque = estoqueAtual - qtd;      // Saída
                default -> novoEstoque = qtd;                     // Absoluto
            }
            String status = novoEstoque < 0 ? " (ESTOQUE NEGATIVO!)" : novoEstoque == 0 ? " (ZERADO)" : novoEstoque <= 5 ? " (BAIXO)" : "";
            lbl.setText("Novo estoque: " + Math.max(0, novoEstoque) + status);
            if (novoEstoque < 0) lbl.setForeground(GerenciadorTema.cor("muted"));
            else lbl.setForeground(GerenciadorTema.cor("texto"));
        }

        private JComponent montarBotoes() {
            JPanel botoes = Componentes.painel(new FlowLayout(FlowLayout.RIGHT, 8, 8), "fundo");

            JButton btnCancelar = Componentes.botao("Cancelar", Icone.Tipo.X, this::dispose);
            GerenciadorTema.estilo(btnCancelar, "painelElevado", "texto");
            botoes.add(btnCancelar);

            JButton btnConfirmar = Componentes.botao("Confirmar", Icone.Tipo.CHECK, this::confirmar);
            Componentes.primario(btnConfirmar);
            botoes.add(btnConfirmar);

            getRootPane().setDefaultButton(btnConfirmar);
            return botoes;
        }

        private void confirmar() {
            String motivo = txtMotivo.getText().trim();
            if (motivo.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Motivo é obrigatório.", "Validação", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int qtd = (Integer) spnQuantidade.getValue();
            int tipo = comboTipo.getSelectedIndex();

            try {
                switch (tipo) {
                    case 0 -> delta = qtd;      // Entrada
                    case 1 -> delta = -qtd;     // Saída
                    default -> {                // Absoluto
                        delta = qtd - estoqueAtual;
                    }
                }

                if (estoqueAtual + delta < 0) {
                    int op = JOptionPane.showConfirmDialog(this,
                        "O estoque ficará negativo (" + (estoqueAtual + delta) + "). Confirmar mesmo assim?",
                        "Aviso", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (op != JOptionPane.YES_OPTION) return;
                }

                service.ajustarEstoque(produtoId, delta, motivo, "AJUSTE_MANUAL", null, null);
                confirmado = true;
                dispose();

            } catch (IllegalStateException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Estoque Insuficiente", JOptionPane.WARNING_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }

        public boolean isConfirmado() { return confirmado; }
        public int getDelta() { return delta; }
    }
}