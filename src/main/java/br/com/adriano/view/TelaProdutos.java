package br.com.adriano.view;

import br.com.adriano.dao.ProdutoDAO;
import br.com.adriano.model.Produto;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class TelaProdutos extends JFrame {

    private JTextField txtId, txtNome, txtPreco, txtEstoque;
    private JButton btnSalvar, btnExcluir, btnLimpar;
    private JTable tabela;
    private DefaultTableModel tableModel;
    private ProdutoDAO produtoDAO = new ProdutoDAO();

    public TelaProdutos() {
        setTitle("Gerenciamento e Controle de Estoque");
        setSize(780, 560);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(12, 12));
        getContentPane().setBackground(new Color(245, 247, 250));

        // --- PAINEL DE FORMULÁRIO (CARD SUPERIOR) ---
        JPanel painelForm = new JPanel(new GridBagLayout());
        painelForm.setBackground(Color.WHITE);
        painelForm.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(15, 18, 15, 18)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // ID (Somente leitura)
        gbc.gridx = 0; gbc.gridy = 0;
        painelForm.add(new JLabel("Código ID:"), gbc);

        gbc.gridx = 1;
        txtId = new JTextField(5);
        txtId.setEditable(false);
        txtId.setBackground(new Color(235, 240, 245));
        txtId.setFont(new Font("Segoe UI", Font.BOLD, 12));
        txtId.setHorizontalAlignment(JTextField.CENTER);
        painelForm.add(txtId, gbc);

        // Nome
        gbc.gridx = 2; gbc.gridy = 0;
        painelForm.add(new JLabel("Nome do Produto:"), gbc);

        gbc.gridx = 3; gbc.weightx = 1.0;
        txtNome = new JTextField();
        txtNome.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        painelForm.add(txtNome, gbc);

        // Preço
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        painelForm.add(new JLabel("Preço (R$):"), gbc);

        gbc.gridx = 1;
        txtPreco = new JTextField();
        txtPreco.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        painelForm.add(txtPreco, gbc);

        // Estoque
        gbc.gridx = 2; gbc.gridy = 1;
        painelForm.add(new JLabel("Qtd. Estoque:"), gbc);

        gbc.gridx = 3;
        txtEstoque = new JTextField();
        txtEstoque.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        painelForm.add(txtEstoque, gbc);

        add(painelForm, BorderLayout.NORTH);

        // --- TABELA CENTRAL ---
        tableModel = new DefaultTableModel(new String[]{"ID", "Nome do Produto", "Preço Unitário", "Estoque Atual"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        tabela = new JTable(tableModel);
        tabela.setRowHeight(28);
        tabela.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabela.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabela.getTableHeader().setBackground(new Color(230, 235, 242));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        tabela.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        tabela.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);

        JScrollPane scroll = new JScrollPane(tabela);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(220, 224, 230)));
        add(scroll, BorderLayout.CENTER);

        // --- BARRA DE BOTÕES (RODAPÉ) ---
        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        painelBotoes.setBackground(Color.WHITE);
        painelBotoes.setBorder(BorderFactory.createLineBorder(new Color(220, 224, 230)));

        btnSalvar = new JButton("SALVAR / ATUALIZAR");
        btnSalvar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSalvar.setBackground(new Color(0, 102, 204));
        btnSalvar.setForeground(Color.WHITE);
        btnSalvar.setFocusPainted(false);
        btnSalvar.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btnExcluir = new JButton("EXCLUIR PRODUTO");
        btnExcluir.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnExcluir.setBackground(new Color(220, 53, 69));
        btnExcluir.setForeground(Color.WHITE);
        btnExcluir.setFocusPainted(false);
        btnExcluir.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btnLimpar = new JButton("NOVO / LIMPAR");
        btnLimpar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnLimpar.setBackground(new Color(108, 117, 125));
        btnLimpar.setForeground(Color.WHITE);
        btnLimpar.setFocusPainted(false);
        btnLimpar.setCursor(new Cursor(Cursor.HAND_CURSOR));

        painelBotoes.add(btnSalvar);
        painelBotoes.add(btnExcluir);
        painelBotoes.add(btnLimpar);

        add(painelBotoes, BorderLayout.SOUTH);

        // --- EVENTOS ---
        btnSalvar.addActionListener(e -> salvarOuAtualizar());
        btnExcluir.addActionListener(e -> excluirProduto());
        btnLimpar.addActionListener(e -> limparCampos());

        // Clique na tabela para preencher os campos automaticamente
        tabela.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                carregarProdutoSelecionado();
            }
        });

        carregarTabela();
    }

    private void salvarOuAtualizar() {
        try {
            String nome = txtNome.getText().trim();
            String precoStr = txtPreco.getText().replace(",", ".").trim();
            String estoqueStr = txtEstoque.getText().trim();

            if (nome.isEmpty() || precoStr.isEmpty() || estoqueStr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Preencha todos os campos!", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }

            double preco = Double.parseDouble(precoStr);
            int estoque = Integer.parseInt(estoqueStr);

            if (txtId.getText().isEmpty()) {
                // Novo Cadastro
                Produto p = new Produto(0, nome, preco, estoque);
                if (produtoDAO.salvar(p)) {
                    JOptionPane.showMessageDialog(this, "Produto cadastrado com sucesso!");
                    limparCampos();
                    carregarTabela();
                }
            } else {
                // Atualização de registro existente
                int id = Integer.parseInt(txtId.getText());
                Produto p = new Produto(id, nome, preco, estoque);
                if (produtoDAO.atualizar(p)) {
                    JOptionPane.showMessageDialog(this, "Produto atualizado com sucesso!");
                    limparCampos();
                    carregarTabela();
                }
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Informe valores válidos para Preço e Estoque!", "Erro de Validação", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void excluirProduto() {
        if (txtId.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Selecione um produto na tabela para excluir!", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int id = Integer.parseInt(txtId.getText());
        String nome = txtNome.getText();

        int confirmacao = JOptionPane.showConfirmDialog(
                this,
                "Tem certeza que deseja excluir o produto '" + nome + "' (ID: " + id + ")?",
                "Confirmar Exclusão",
                JOptionPane.YES_NO_OPTION
        );

        if (confirmacao == JOptionPane.YES_OPTION) {
            if (produtoDAO.excluir(id)) {
                JOptionPane.showMessageDialog(this, "Produto removido com sucesso!");
                limparCampos();
                carregarTabela();
            } else {
                JOptionPane.showMessageDialog(this, "Erro ao excluir o produto do banco de dados.", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void carregarProdutoSelecionado() {
        int linha = tabela.getSelectedRow();
        if (linha != -1) {
            txtId.setText(tableModel.getValueAt(linha, 0).toString());
            txtNome.setText(tableModel.getValueAt(linha, 1).toString());

            String precoTexto = tableModel.getValueAt(linha, 2).toString().replace("R$", "").trim();
            txtPreco.setText(precoTexto);

            txtEstoque.setText(tableModel.getValueAt(linha, 3).toString());
        }
    }

    private void limparCampos() {
        txtId.setText("");
        txtNome.setText("");
        txtPreco.setText("");
        txtEstoque.setText("");
        tabela.clearSelection();
    }

    private void carregarTabela() {
        tableModel.setRowCount(0);
        List<Produto> lista = produtoDAO.listarTodos();
        for (Produto p : lista) {
            tableModel.addRow(new Object[]{
                    p.getId(),
                    p.getNome(),
                    String.format("R$ %.2f", p.getPreco()),
                    p.getEstoque()
            });
        }
    }
}