package br.com.adriano.view;

import javax.swing.*;
import java.awt.*;

public class TelaPrincipal extends JFrame {

    public TelaPrincipal() {
        setTitle("MiniComércio v1.0 - Painel Principal");
        setSize(800, 520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(15, 15));
        getContentPane().setBackground(new Color(245, 247, 250));

        // --- CABEÇALHO ---
        JPanel painelTopo = new JPanel(new BorderLayout());
        painelTopo.setBackground(new Color(0, 102, 204));
        painelTopo.setBorder(BorderFactory.createEmptyBorder(18, 25, 18, 25));

        JLabel lblTitulo = new JLabel("SISTEMA CALDOS DA ANA v1.0");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitulo.setForeground(Color.WHITE);

        JLabel lblSub = new JLabel("Painel de Controle e Gestão Comercial");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSub.setForeground(new Color(220, 235, 255));

        JPanel painelTextosTopo = new JPanel(new GridLayout(2, 1));
        painelTextosTopo.setOpaque(false);
        painelTextosTopo.add(lblTitulo);
        painelTextosTopo.add(lblSub);

        painelTopo.add(painelTextosTopo, BorderLayout.WEST);
        add(painelTopo, BorderLayout.NORTH);

        // --- GRID DE BOTÕES ---
        JPanel painelMenu = new JPanel(new GridLayout(2, 2, 20, 20));
        painelMenu.setBackground(new Color(245, 247, 250));
        painelMenu.setBorder(BorderFactory.createEmptyBorder(20, 35, 20, 35));

        JButton btnVendas = criarBotaoMenu("FRENTE DE CAIXA", "Realizar vendas, carrinho e troco", new Color(34, 139, 34));
        JButton btnProdutos = criarBotaoMenu("ESTOQUE / PRODUTOS", "Cadastrar e gerenciar produtos", new Color(0, 102, 204));
        JButton btnCaixa = criarBotaoMenu("GESTÃO DE CAIXA", "Abertura e fechamento diário", new Color(218, 124, 0));
        JButton btnRelatorios = criarBotaoMenu("DASHBOARD / RELATÓRIOS", "Resumo de faturamento e vendas", new Color(102, 51, 153));

        painelMenu.add(btnVendas);
        painelMenu.add(btnProdutos);
        painelMenu.add(btnCaixa);
        painelMenu.add(btnRelatorios);

        add(painelMenu, BorderLayout.CENTER);

        // --- RODAPÉ ---
        JPanel painelRodape = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 12));
        painelRodape.setBackground(Color.WHITE);
        painelRodape.setBorder(BorderFactory.createLineBorder(new Color(220, 224, 230)));

        JButton btnSair = new JButton("Sair do Sistema");
        btnSair.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSair.setBackground(new Color(220, 53, 69));
        btnSair.setForeground(Color.WHITE);
        btnSair.setFocusPainted(false);
        btnSair.setCursor(new Cursor(Cursor.HAND_CURSOR));

        painelRodape.add(btnSair);
        add(painelRodape, BorderLayout.SOUTH);

        // Eventos
        btnVendas.addActionListener(e -> new TelaVenda().setVisible(true));
        btnProdutos.addActionListener(e -> new TelaProdutos().setVisible(true));
        btnCaixa.addActionListener(e -> new TelaCaixa().setVisible(true));
        btnRelatorios.addActionListener(e -> new TelaRelatorios().setVisible(true));
        btnSair.addActionListener(e -> System.exit(0));
    }

    private JButton criarBotaoMenu(String titulo, String sub, Color cor) {
        JButton btn = new JButton("<html><center><b style='font-size:12pt;'>" + titulo + "</b><br><span style='font-size:9pt; font-weight:normal;'>" + sub + "</span></center></html>");
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setBackground(cor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        return btn;
    }
}