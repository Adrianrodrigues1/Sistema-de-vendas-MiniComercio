package br.com.adriano.view;

import br.com.adriano.dao.CaixaDAO;
import br.com.adriano.dao.RetiradaDAO;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;

public class TelaCaixa extends JFrame {

    // Instâncias dos DAOs para persistência no SQLite
    private final CaixaDAO caixaDAO = new CaixaDAO();
    private final RetiradaDAO retiradaDAO = new RetiradaDAO();

    // Componentes da Coluna Esquerda (Controle do Caixa)
    private JLabel lblStatus;
    private JTextField txtValorInicial;
    private JTextField txtTotalVendas;
    private JButton btnAbrirCaixa;
    private JButton btnFecharCaixa;

    // Componentes da Coluna Direita (Retiradas e Despesas)
    private JTextField txtValorRetirada;
    private JComboBox<String> cbMotivo;
    private JButton btnLancarOperacao;

    // Tabela de Histórico
    private JTable tabelaOperacoes;
    private DefaultTableModel modelTabela;

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    public TelaCaixa() {
        setTitle("Gestão de Caixa - MiniComércio");
        setSize(900, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        initComponents();
        configurarEventos();
        carregarEstadoAtual();
    }

    private void initComponents() {
        // =========================================================================
        // PAINEL SUPERIOR - LAYOUT EM DUAS COLUNAS ESPELHADAS (1 LINHA, 2 COLUNAS)
        // =========================================================================
        JPanel painelSuperior = new JPanel(new GridLayout(1, 2, 15, 15));
        painelSuperior.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // -------------------------------------------------------------------------
        // COLUNA 1 (ESQUERDA): CONTROLE DE CAIXA
        // -------------------------------------------------------------------------
        JPanel painelEsquerdo = new JPanel(new GridBagLayout());
        painelEsquerdo.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Controle do Caixa", TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints gbcEsq = new GridBagConstraints();
        gbcEsq.insets = new Insets(5, 5, 5, 5);
        gbcEsq.fill = GridBagConstraints.HORIZONTAL;

        // Status
        gbcEsq.gridx = 0; gbcEsq.gridy = 0;
        painelEsquerdo.add(new JLabel("Status:"), gbcEsq);

        gbcEsq.gridx = 1; gbcEsq.gridy = 0;
        lblStatus = new JLabel("VERIFICANDO...");
        lblStatus.setFont(new Font("SansSerif", Font.BOLD, 13));
        painelEsquerdo.add(lblStatus, gbcEsq);

        // Valor Inicial (Troco)
        gbcEsq.gridx = 0; gbcEsq.gridy = 1;
        painelEsquerdo.add(new JLabel("Valor Inicial (R$):"), gbcEsq);

        gbcEsq.gridx = 1; gbcEsq.gridy = 1;
        txtValorInicial = new JTextField(10);
        painelEsquerdo.add(txtValorInicial, gbcEsq);

        // Total de Vendas
        gbcEsq.gridx = 0; gbcEsq.gridy = 2;
        painelEsquerdo.add(new JLabel("Total Vendas:"), gbcEsq);

        gbcEsq.gridx = 1; gbcEsq.gridy = 2;
        txtTotalVendas = new JTextField(currencyFormat.format(0.0), 10);
        txtTotalVendas.setEditable(false);
        txtTotalVendas.setBackground(new Color(240, 240, 240));
        painelEsquerdo.add(txtTotalVendas, gbcEsq);

        // Painel de Botões de Abertura / Fechamento
        JPanel painelBotoesCaixa = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        btnAbrirCaixa = new JButton("Abrir Caixa");
        btnAbrirCaixa.setBackground(new Color(40, 167, 69));
        btnAbrirCaixa.setForeground(Color.WHITE);

        btnFecharCaixa = new JButton("Fechar Caixa");
        btnFecharCaixa.setBackground(new Color(220, 53, 69));
        btnFecharCaixa.setForeground(Color.WHITE);

        painelBotoesCaixa.add(btnAbrirCaixa);
        painelBotoesCaixa.add(btnFecharCaixa);

        gbcEsq.gridx = 0; gbcEsq.gridy = 3; gbcEsq.gridwidth = 2;
        gbcEsq.insets = new Insets(10, 5, 5, 5);
        painelEsquerdo.add(painelBotoesCaixa, gbcEsq);

        // -------------------------------------------------------------------------
        // COLUNA 2 (DIREITA): RETIRADAS E DESPESAS (SANGRIA)
        // -------------------------------------------------------------------------
        JPanel painelDireito = new JPanel(new GridBagLayout());
        painelDireito.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Retirada / Despesa", TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints gbcDir = new GridBagConstraints();
        gbcDir.insets = new Insets(5, 5, 5, 5);
        gbcDir.fill = GridBagConstraints.HORIZONTAL;

        // Valor da Retirada
        gbcDir.gridx = 0; gbcDir.gridy = 0;
        painelDireito.add(new JLabel("Valor (R$):"), gbcDir);

        gbcDir.gridx = 1; gbcDir.gridy = 0;
        txtValorRetirada = new JTextField(10);
        painelDireito.add(txtValorRetirada, gbcDir);

        // Motivo
        gbcDir.gridx = 0; gbcDir.gridy = 1;
        painelDireito.add(new JLabel("Motivo:"), gbcDir);

        gbcDir.gridx = 1; gbcDir.gridy = 1;
        String[] motivos = {"Pagamento de Entrega", "Compra de Insumos", "Sangria de Segurança", "Outros"};
        cbMotivo = new JComboBox<>(motivos);
        painelDireito.add(cbMotivo, gbcDir);

        // Botão Lançar Operação
        btnLancarOperacao = new JButton("Lançar Retirada");
        btnLancarOperacao.setBackground(new Color(255, 140, 0));
        btnLancarOperacao.setForeground(Color.WHITE);

        gbcDir.gridx = 0; gbcDir.gridy = 2; gbcDir.gridwidth = 2;
        gbcDir.insets = new Insets(15, 5, 5, 5);
        painelDireito.add(btnLancarOperacao, gbcDir);

        // Adiciona as duas colunas no painel superior
        painelSuperior.add(painelEsquerdo);
        painelSuperior.add(painelDireito);

        add(painelSuperior, BorderLayout.NORTH);

        // =========================================================================
        // PAINEL CENTRAL (HISTÓRICO E RESUMO DAS OPERAÇÕES)
        // =========================================================================
        JPanel painelCentral = new JPanel(new BorderLayout());
        painelCentral.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Histórico de Movimentações", TitledBorder.LEFT, TitledBorder.TOP));

        String[] colunas = {"Horário", "Tipo", "Descrição / Motivo", "Valor"};
        modelTabela = new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tabelaOperacoes = new JTable(modelTabela);
        painelCentral.add(new JScrollPane(tabelaOperacoes), BorderLayout.CENTER);
        add(painelCentral, BorderLayout.CENTER);
    }

    private void carregarEstadoAtual() {
        boolean aberto = caixaDAO.isCaixaAberto();

        if (aberto) {
            lblStatus.setText("ABERTO");
            lblStatus.setForeground(new Color(0, 128, 0));
            btnAbrirCaixa.setEnabled(false);
            btnFecharCaixa.setEnabled(true);
            txtValorInicial.setEditable(false);
        } else {
            lblStatus.setText("FECHADO");
            lblStatus.setForeground(Color.RED);
            btnAbrirCaixa.setEnabled(true);
            btnFecharCaixa.setEnabled(false);
            txtValorInicial.setEditable(true);
            txtValorInicial.setText("");
            txtTotalVendas.setText(currencyFormat.format(0.0));
        }
    }

    private void configurarEventos() {
        btnAbrirCaixa.addActionListener(e -> executarAberturaCaixa());
        btnFecharCaixa.addActionListener(e -> executarFechamentoCaixa());
        btnLancarOperacao.addActionListener(e -> executarLancamentoRetirada());
    }

    private void executarAberturaCaixa() {
        try {
            double valorInicial = parseDoubleComSeguranca(txtValorInicial.getText());

            if (valorInicial < 0) {
                JOptionPane.showMessageDialog(this, "O valor inicial não pode ser negativo.", "Atenção", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (caixaDAO.abrirCaixa(valorInicial)) {
                carregarEstadoAtual();
                JOptionPane.showMessageDialog(this, "Caixa aberto com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Erro ao abrir o caixa no banco de dados.", "Erro", JOptionPane.ERROR_MESSAGE);
            }

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Digite um valor numérico válido para o Valor Inicial.", "Erro de Validação", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void executarFechamentoCaixa() {
        int confirmacao = JOptionPane.showConfirmDialog(
                this, "Deseja realmente fechar o caixa atual?", "Confirmação", JOptionPane.YES_NO_OPTION);

        if (confirmacao == JOptionPane.YES_OPTION) {
            if (caixaDAO.fecharCaixa()) {
                carregarEstadoAtual();
                JOptionPane.showMessageDialog(this, "Caixa fechado com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Erro ao fechar o caixa no banco de dados.", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void executarLancamentoRetirada() {
        if (!caixaDAO.isCaixaAberto()) {
            JOptionPane.showMessageDialog(this, "O caixa precisa estar ABERTO para lançar retiradas.", "Atenção", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            double valorRetirada = parseDoubleComSeguranca(txtValorRetirada.getText());

            if (valorRetirada <= 0) {
                JOptionPane.showMessageDialog(this, "Informe um valor maior que zero para a retirada.", "Atenção", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String motivo = (String) cbMotivo.getSelectedItem();

            if (retiradaDAO.registrarRetirada(valorRetirada, motivo)) {
                String horaAtual = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                modelTabela.addRow(new Object[]{horaAtual, "RETIRADA / SANGRIA", motivo, currencyFormat.format(valorRetirada)});

                JOptionPane.showMessageDialog(this, "Retirada registrada com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                txtValorRetirada.setText("");
            } else {
                JOptionPane.showMessageDialog(this, "Erro ao gravar retirada.", "Erro", JOptionPane.ERROR_MESSAGE);
            }

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Digite um valor numérico válido para a retirada.", "Erro de Validação", JOptionPane.ERROR_MESSAGE);
        }
    }

    private double parseDoubleComSeguranca(String texto) throws NumberFormatException {
        if (texto == null || texto.trim().isEmpty()) {
            throw new NumberFormatException("Campo vazio");
        }
        return Double.parseDouble(texto.trim().replace(",", "."));
    }
}