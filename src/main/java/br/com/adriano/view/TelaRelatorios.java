package br.com.adriano.view;

import br.com.adriano.dao.VendaDAO;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class TelaRelatorios extends JFrame {

    private JComboBox<String> comboFiltro;
    private JLabel lblFaturamentoTotal, lblTotalDinheiro, lblTotalPix, lblTotalCredito, lblTotalDebito;
    private JTable tabelaVendas;
    private DefaultTableModel tableModel;
    private JButton btnExcluir;

    private VendaDAO vendaDAO = new VendaDAO();

    public TelaRelatorios() {
        setTitle("Dashboard e Relatório de Vendas");
        setSize(950, 650);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(12, 12));
        getContentPane().setBackground(new Color(245, 247, 250));

        // --- PAINEL SUPERIOR: Dashboard de Faturamento por Categoria ---
        JPanel painelCards = new JPanel(new GridLayout(1, 5, 10, 0));
        painelCards.setBackground(new Color(245, 247, 250));
        painelCards.setBorder(BorderFactory.createEmptyBorder(12, 12, 5, 12));

        lblFaturamentoTotal = criarCard(painelCards, "FATURAMENTO TOTAL", new Color(40, 167, 69));
        lblTotalDinheiro = criarCard(painelCards, "DINHEIRO", new Color(0, 123, 255));
        lblTotalPix = criarCard(painelCards, "PIX", new Color(23, 162, 184));
        lblTotalCredito = criarCard(painelCards, "CRÉDITO", new Color(111, 66, 193));
        lblTotalDebito = criarCard(painelCards, "DÉBITO", new Color(255, 193, 7));

        add(painelCards, BorderLayout.NORTH);

        // --- PAINEL CENTRAL: Filtro + Tabela ---
        JPanel painelCentro = new JPanel(new BorderLayout(10, 10));
        painelCentro.setBackground(new Color(245, 247, 250));
        painelCentro.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));

        // Barra de Filtro
        JPanel painelFiltro = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        painelFiltro.setBackground(Color.WHITE);
        painelFiltro.setBorder(BorderFactory.createLineBorder(new Color(220, 224, 230)));

        painelFiltro.add(new JLabel("Filtrar Exibição:"));
        comboFiltro = new JComboBox<>(new String[]{"Todas as Vendas", "Vendas de Hoje"});
        comboFiltro.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        painelFiltro.add(comboFiltro);

        painelCentro.add(painelFiltro, BorderLayout.NORTH);

        // Tabela de Vendas
        tableModel = new DefaultTableModel(new String[]{"ID Venda", "Data e Hora", "Forma de Pagamento", "Valor Total"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tabelaVendas = new JTable(tableModel);
        tabelaVendas.setRowHeight(28);
        tabelaVendas.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabelaVendas.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabelaVendas.getTableHeader().setBackground(new Color(230, 235, 242));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        tabelaVendas.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        tabelaVendas.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        tabelaVendas.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);

        JScrollPane scrollTabela = new JScrollPane(tabelaVendas);
        scrollTabela.setBorder(BorderFactory.createLineBorder(new Color(220, 224, 230)));
        painelCentro.add(scrollTabela, BorderLayout.CENTER);

        add(painelCentro, BorderLayout.CENTER);

        // --- PAINEL INFERIOR: Botão Excluir Venda ---
        JPanel painelRodape = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        painelRodape.setBackground(Color.WHITE);
        painelRodape.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 224, 230)));

        btnExcluir = new JButton("Excluir Venda Selecionada");
        btnExcluir.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnExcluir.setBackground(new Color(220, 53, 69));
        btnExcluir.setForeground(Color.WHITE);
        btnExcluir.setFocusPainted(false);
        btnExcluir.setCursor(new Cursor(Cursor.HAND_CURSOR));

        painelRodape.add(btnExcluir);
        add(painelRodape, BorderLayout.SOUTH);

        // Eventos
        comboFiltro.addActionListener(e -> carregarDados());
        btnExcluir.addActionListener(e -> excluirVendaSelecionada());

        carregarDados();
    }

    private JLabel criarCard(JPanel container, String titulo, Color corTexto) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JLabel lblTitulo = new JLabel(titulo, SwingConstants.CENTER);
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblTitulo.setForeground(new Color(120, 130, 140));

        JLabel lblValor = new JLabel("R$ 0,00", SwingConstants.CENTER);
        lblValor.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblValor.setForeground(corTexto);

        card.add(lblTitulo, BorderLayout.NORTH);
        card.add(lblValor, BorderLayout.CENTER);

        container.add(card);
        return lblValor;
    }

    private void carregarDados() {
        tableModel.setRowCount(0);
        boolean apenasHoje = comboFiltro.getSelectedIndex() == 1;

        List<Object[]> vendas = vendaDAO.listarVendasFiltradas(apenasHoje);

        double totalGeral = 0;
        double totalDinheiro = 0;
        double totalPix = 0;
        double totalCredito = 0;
        double totalDebito = 0;

        for (Object[] v : vendas) {
            tableModel.addRow(v);

            String forma = (String) v[2];
            double valor = (Double) v[4]; // Valor numérico para cálculo

            totalGeral += valor;
            if ("Dinheiro".equalsIgnoreCase(forma)) totalDinheiro += valor;
            else if ("PIX".equalsIgnoreCase(forma)) totalPix += valor;
            else if ("Cartão de Crédito".equalsIgnoreCase(forma) || "Credito".equalsIgnoreCase(forma)) totalCredito += valor;
            else if ("Cartão de Débito".equalsIgnoreCase(forma) || "Debito".equalsIgnoreCase(forma)) totalDebito += valor;
        }

        lblFaturamentoTotal.setText(String.format("R$ %.2f", totalGeral));
        lblTotalDinheiro.setText(String.format("R$ %.2f", totalDinheiro));
        lblTotalPix.setText(String.format("R$ %.2f", totalPix));
        lblTotalCredito.setText(String.format("R$ %.2f", totalCredito));
        lblTotalDebito.setText(String.format("R$ %.2f", totalDebito));
    }

    private void excluirVendaSelecionada() {
        int linhaSelecionada = tabelaVendas.getSelectedRow();
        if (linhaSelecionada == -1) {
            JOptionPane.showMessageDialog(this, "Selecione uma venda na tabela para excluir!", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int idVenda = (int) tabelaVendas.getValueAt(linhaSelecionada, 0);

        int confirmacao = JOptionPane.showConfirmDialog(
                this,
                "Tem certeza que deseja excluir a venda #" + idVenda + "?",
                "Confirmar Exclusão",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirmacao == JOptionPane.YES_OPTION) {
            if (vendaDAO.excluirVenda(idVenda)) {
                JOptionPane.showMessageDialog(this, "Venda excluída com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                carregarDados();
            } else {
                JOptionPane.showMessageDialog(this, "Erro ao excluir a venda do banco de dados.", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}