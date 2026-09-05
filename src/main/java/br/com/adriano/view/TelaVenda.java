package br.com.adriano.view;

import br.com.adriano.dao.CaixaDAO;
import br.com.adriano.dao.ProdutoDAO;
import br.com.adriano.dao.VendaDAO;
import br.com.adriano.model.Produto;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TelaVenda extends JFrame {

    private JTextField txtCodigo, txtQuantidade, txtNomeProduto, txtPreco, txtValorPago;
    private JComboBox<String> comboPagamento;
    private JLabel lblTotal, lblTroco, lblResumoItens, lblStatusCaixaTopo;
    private JTable tabelaCarrinho;
    private DefaultTableModel tableModel;
    private JButton btnFinalizar, btnRemoverItem, btnAdicionar, btnBuscar;

    private ProdutoDAO produtoDAO = new ProdutoDAO();
    private VendaDAO vendaDAO = new VendaDAO();
    private CaixaDAO caixaDAO = new CaixaDAO();

    private Produto produtoSelecionado = null;
    private double valorTotalVenda = 0.0;
    private List<ItemCarrinho> carrinho = new ArrayList<>();

    public static class ItemCarrinho {
        public Produto produto;
        public int quantidade;
        public double subtotal;

        public ItemCarrinho(Produto produto, int quantidade, double subtotal) {
            this.produto = produto;
            this.quantidade = quantidade;
            this.subtotal = subtotal;
        }
    }

    public TelaVenda() {
        setTitle("Frente de Caixa - PDV v1.0");
        setSize(880, 720);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(12, 12));
        getContentPane().setBackground(new Color(245, 247, 250));

        // --- PAINEL NORTE: Banner de Status do Caixa + Campos de Entrada ---
        JPanel painelNorte = new JPanel(new BorderLayout(0, 8));
        painelNorte.setBackground(new Color(245, 247, 250));

        // 1. Banner Superior de Status do Caixa
        boolean caixaAberto = caixaDAO.isCaixaAberto();
        lblStatusCaixaTopo = new JLabel(
                caixaAberto ? "● CAIXA ABERTO" : "● CAIXA FECHADO - Abra o caixa para realizar vendas",
                SwingConstants.CENTER
        );
        lblStatusCaixaTopo.setOpaque(true);
        lblStatusCaixaTopo.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblStatusCaixaTopo.setPreferredSize(new Dimension(0, 35));

        if (caixaAberto) {
            lblStatusCaixaTopo.setBackground(new Color(212, 237, 218));
            lblStatusCaixaTopo.setForeground(new Color(25, 135, 84));
            lblStatusCaixaTopo.setBorder(BorderFactory.createLineBorder(new Color(195, 230, 203)));
        } else {
            lblStatusCaixaTopo.setBackground(new Color(248, 215, 218));
            lblStatusCaixaTopo.setForeground(new Color(220, 53, 69));
            lblStatusCaixaTopo.setBorder(BorderFactory.createLineBorder(new Color(245, 198, 203)));
        }

        painelNorte.add(lblStatusCaixaTopo, BorderLayout.NORTH);

        // 2. Formulário de Entrada de Produtos
        JPanel painelEntrada = new JPanel(new BorderLayout(10, 10));
        painelEntrada.setBackground(Color.WHITE);
        painelEntrada.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(12, 15, 12, 15)
        ));

        JPanel gridCampos = new JPanel(new GridLayout(2, 4, 10, 8));
        gridCampos.setBackground(Color.WHITE);

        gridCampos.add(new JLabel("Código ID:"));
        JPanel painelBuscaId = new JPanel(new BorderLayout(5, 0));
        painelBuscaId.setBackground(Color.WHITE);
        txtCodigo = criarCampoTexto();
        btnBuscar = new JButton("Buscar");
        btnBuscar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        painelBuscaId.add(txtCodigo, BorderLayout.CENTER);
        painelBuscaId.add(btnBuscar, BorderLayout.EAST);
        gridCampos.add(painelBuscaId);

        gridCampos.add(new JLabel("Produto:"));
        txtNomeProduto = criarCampoTexto();
        txtNomeProduto.setEditable(false);
        txtNomeProduto.setBackground(new Color(240, 242, 245));
        gridCampos.add(txtNomeProduto);

        gridCampos.add(new JLabel("Preço Unitário:"));
        txtPreco = criarCampoTexto();
        txtPreco.setEditable(false);
        txtPreco.setBackground(new Color(240, 242, 245));
        gridCampos.add(txtPreco);

        gridCampos.add(new JLabel("Quantidade:"));
        txtQuantidade = criarCampoTexto();
        txtQuantidade.setText("1");
        gridCampos.add(txtQuantidade);

        painelEntrada.add(gridCampos, BorderLayout.CENTER);

        btnAdicionar = new JButton("+ Adicionar ao Carrinho");
        btnAdicionar.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnAdicionar.setBackground(new Color(0, 102, 204));
        btnAdicionar.setForeground(Color.WHITE);
        btnAdicionar.setFocusPainted(false);
        btnAdicionar.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JPanel painelBtnAdd = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 8));
        painelBtnAdd.setBackground(Color.WHITE);
        painelBtnAdd.add(btnAdicionar);
        painelEntrada.add(painelBtnAdd, BorderLayout.SOUTH);

        painelNorte.add(painelEntrada, BorderLayout.CENTER);

        add(painelNorte, BorderLayout.NORTH);

        // --- PAINEL CENTRAL: Tabela de Itens do Carrinho ---
        JPanel painelCentro = new JPanel(new BorderLayout(8, 8));
        painelCentro.setBackground(new Color(245, 247, 250));

        tableModel = new DefaultTableModel(new String[]{"ID", "Produto", "Qtd", "Preço Unit.", "Subtotal"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tabelaCarrinho = new JTable(tableModel);
        tabelaCarrinho.setRowHeight(28);
        tabelaCarrinho.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabelaCarrinho.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabelaCarrinho.getTableHeader().setBackground(new Color(230, 235, 242));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        tabelaCarrinho.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        tabelaCarrinho.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);

        JScrollPane scrollTabela = new JScrollPane(tabelaCarrinho);
        scrollTabela.setBorder(BorderFactory.createLineBorder(new Color(220, 224, 230)));
        painelCentro.add(scrollTabela, BorderLayout.CENTER);

        JPanel barraAcoesCarrinho = new JPanel(new BorderLayout());
        barraAcoesCarrinho.setBackground(new Color(245, 247, 250));

        lblResumoItens = new JLabel("Itens no Carrinho: 0 | Total de Unidades: 0");
        lblResumoItens.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblResumoItens.setForeground(new Color(100, 110, 120));

        btnRemoverItem = new JButton("Remover Item Selecionado");
        btnRemoverItem.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnRemoverItem.setBackground(new Color(220, 53, 69));
        btnRemoverItem.setForeground(Color.WHITE);
        btnRemoverItem.setFocusPainted(false);
        btnRemoverItem.setCursor(new Cursor(Cursor.HAND_CURSOR));

        barraAcoesCarrinho.add(lblResumoItens, BorderLayout.WEST);
        barraAcoesCarrinho.add(btnRemoverItem, BorderLayout.EAST);

        painelCentro.add(barraAcoesCarrinho, BorderLayout.SOUTH);
        add(painelCentro, BorderLayout.CENTER);

        // --- PAINEL INFERIOR: Pagamento e Totalização ---
        JPanel painelRodape = new JPanel(new BorderLayout(10, 10));
        painelRodape.setBackground(Color.WHITE);
        painelRodape.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        JPanel painelPagamento = new JPanel(new GridLayout(2, 2, 10, 10));
        painelPagamento.setBackground(Color.WHITE);

        painelPagamento.add(new JLabel("Forma de Pagamento:"));
        String[] formasPagamento = {"Dinheiro", "PIX", "Cartão de Crédito", "Cartão de Débito"};
        comboPagamento = new JComboBox<>(formasPagamento);
        comboPagamento.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        painelPagamento.add(comboPagamento);

        painelPagamento.add(new JLabel("Valor Pago:"));
        txtValorPago = criarCampoTexto();
        txtValorPago.setFont(new Font("Segoe UI", Font.BOLD, 14));
        painelPagamento.add(txtValorPago);

        painelRodape.add(painelPagamento, BorderLayout.WEST);

        JPanel painelTotaisAcao = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 5));
        painelTotaisAcao.setBackground(Color.WHITE);

        JPanel painelTextosTotais = new JPanel(new GridLayout(2, 1, 5, 2));
        painelTextosTotais.setBackground(Color.WHITE);

        lblTotal = new JLabel("TOTAL: R$ 0,00", SwingConstants.RIGHT);
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblTotal.setForeground(new Color(0, 128, 0));

        lblTroco = new JLabel("Troco: R$ 0,00", SwingConstants.RIGHT);
        lblTroco.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTroco.setForeground(Color.DARK_GRAY);

        painelTextosTotais.add(lblTotal);
        painelTextosTotais.add(lblTroco);

        btnFinalizar = new JButton("FINALIZAR VENDA");
        btnFinalizar.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnFinalizar.setBackground(new Color(34, 139, 34));
        btnFinalizar.setForeground(Color.WHITE);
        btnFinalizar.setPreferredSize(new Dimension(180, 45));
        btnFinalizar.setFocusPainted(false);
        btnFinalizar.setCursor(new Cursor(Cursor.HAND_CURSOR));

        painelTotaisAcao.add(painelTextosTotais);
        painelTotaisAcao.add(btnFinalizar);

        painelRodape.add(painelTotaisAcao, BorderLayout.EAST);

        add(painelRodape, BorderLayout.SOUTH);

        // --- LISTENERS E EVENTOS ---
        btnBuscar.addActionListener(e -> buscarProduto());
        btnAdicionar.addActionListener(e -> adicionarAoCarrinho());
        btnRemoverItem.addActionListener(e -> removerItemDoCarrinho());
        btnFinalizar.addActionListener(e -> finalizarVenda());

        txtValorPago.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { calcularTroco(); }
            public void removeUpdate(DocumentEvent e) { calcularTroco(); }
            public void changedUpdate(DocumentEvent e) { calcularTroco(); }
        });

        comboPagamento.addActionListener(e -> {
            boolean isDinheiro = "Dinheiro".equals(comboPagamento.getSelectedItem());
            txtValorPago.setEnabled(isDinheiro);
            if (!isDinheiro) {
                txtValorPago.setText("");
                lblTroco.setText("Troco: R$ 0,00");
                lblTroco.setForeground(Color.DARK_GRAY);
            } else {
                calcularTroco();
            }
        });
    }

    private JTextField criarCampoTexto() {
        JTextField tf = new JTextField();
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        return tf;
    }

    private void buscarProduto() {
        try {
            int id = Integer.parseInt(txtCodigo.getText().trim());
            produtoSelecionado = produtoDAO.buscarPorId(id);

            if (produtoSelecionado != null) {
                txtNomeProduto.setText(produtoSelecionado.getNome());
                txtPreco.setText(String.format("%.2f", produtoSelecionado.getPreco()));
            } else {
                JOptionPane.showMessageDialog(this, "Produto não encontrado!", "Aviso", JOptionPane.WARNING_MESSAGE);
                txtNomeProduto.setText("");
                txtPreco.setText("");
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Informe um código ID válido!", "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void adicionarAoCarrinho() {
        if (produtoSelecionado == null) {
            JOptionPane.showMessageDialog(this, "Busque um produto primeiro!", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            int qtd = Integer.parseInt(txtQuantidade.getText().trim());

            if (qtd <= 0 || qtd > produtoSelecionado.getEstoque()) {
                JOptionPane.showMessageDialog(this, "Estoque insuficiente ou quantidade inválida! Estoque atual: " + produtoSelecionado.getEstoque(), "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }

            double subtotal = produtoSelecionado.getPreco() * qtd;
            carrinho.add(new ItemCarrinho(produtoSelecionado, qtd, subtotal));

            tableModel.addRow(new Object[]{
                    produtoSelecionado.getId(),
                    produtoSelecionado.getNome(),
                    qtd,
                    String.format("R$ %.2f", produtoSelecionado.getPreco()),
                    String.format("R$ %.2f", subtotal)
            });

            txtCodigo.setText("");
            txtNomeProduto.setText("");
            txtPreco.setText("");
            txtQuantidade.setText("1");
            produtoSelecionado = null;

            recalcularTotais();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Informe uma quantidade válida!", "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removerItemDoCarrinho() {
        int linhaSelecionada = tabelaCarrinho.getSelectedRow();

        if (linhaSelecionada == -1) {
            JOptionPane.showMessageDialog(this, "Selecione um item no carrinho para remover!", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        carrinho.remove(linhaSelecionada);
        tableModel.removeRow(linhaSelecionada);

        recalcularTotais();
    }

    private void recalcularTotais() {
        valorTotalVenda = 0.0;
        int totalUnidades = 0;

        for (ItemCarrinho item : carrinho) {
            valorTotalVenda += item.subtotal;
            totalUnidades += item.quantidade;
        }

        lblTotal.setText(String.format("TOTAL: R$ %.2f", valorTotalVenda));
        lblResumoItens.setText(String.format("Itens no Carrinho: %d | Total de Unidades: %d", carrinho.size(), totalUnidades));

        calcularTroco();
    }

    private void calcularTroco() {
        String formaPagamento = (String) comboPagamento.getSelectedItem();

        if (!"Dinheiro".equals(formaPagamento)) {
            lblTroco.setText("Troco: R$ 0,00");
            lblTroco.setForeground(Color.DARK_GRAY);
            return;
        }

        String textoPago = txtValorPago.getText().replace(",", ".").trim();

        if (textoPago.isEmpty()) {
            lblTroco.setText("Troco: R$ 0,00");
            lblTroco.setForeground(Color.DARK_GRAY);
            return;
        }

        try {
            double valorPago = Double.parseDouble(textoPago);
            double troco = valorPago - valorTotalVenda;

            if (troco >= 0) {
                lblTroco.setText(String.format("Troco: R$ %.2f", troco));
                lblTroco.setForeground(new Color(34, 139, 34));
            } else {
                lblTroco.setText(String.format("Falta: R$ %.2f", Math.abs(troco)));
                lblTroco.setForeground(new Color(220, 53, 69));
            }
        } catch (NumberFormatException e) {
            lblTroco.setText("Valor pago inválido");
            lblTroco.setForeground(new Color(220, 53, 69));
        }
    }

    private void finalizarVenda() {
        // Validação 1: Verificar se o Caixa está ABERTO
        if (!caixaDAO.isCaixaAberto()) {
            JOptionPane.showMessageDialog(this, "Não é possível realizar vendas com o Caixa FECHADO!\nAbra o caixa na tela de Gestão de Caixa.", "Caixa Fechado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Validação 2: Verificar carrinho vazio
        if (carrinho.isEmpty()) {
            JOptionPane.showMessageDialog(this, "O carrinho está vazio!", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String formaPagamento = (String) comboPagamento.getSelectedItem();

        // Validação 3: Pagamento em Dinheiro
        if ("Dinheiro".equals(formaPagamento)) {
            String textoPago = txtValorPago.getText().replace(",", ".").trim();
            try {
                double valorPago = Double.parseDouble(textoPago);
                if (valorPago < valorTotalVenda) {
                    JOptionPane.showMessageDialog(this, "O valor pago é menor que o valor total da venda!", "Atenção", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Informe um valor pago válido para pagamentos em dinheiro!", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        // Processamento da venda
        if (vendaDAO.registrarVendaCompleta(valorTotalVenda, formaPagamento, carrinho)) {
            JOptionPane.showMessageDialog(this, "Venda realizada com sucesso via " + formaPagamento + "!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } else {
            JOptionPane.showMessageDialog(this, "Erro ao registrar venda no banco de dados!", "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}