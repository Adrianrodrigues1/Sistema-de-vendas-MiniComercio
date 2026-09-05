package br.com.adriano.view;

import br.com.adriano.dao.UsuarioDAO;

import javax.swing.*;
import java.awt.*;

public class TelaLogin extends JFrame {

    private JTextField txtUsuario;
    private JPasswordField txtSenha;
    private UsuarioDAO usuarioDAO = new UsuarioDAO();

    public TelaLogin() {
        setTitle("MiniComércio - Login de Acesso");
        setSize(420, 420);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        JPanel painelFundo = new JPanel(new BorderLayout());
        painelFundo.setBackground(new Color(245, 247, 250));
        painelFundo.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        // Card Central
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(20, 25, 20, 25)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 5, 8, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Banner Superior do Card
        JLabel lblTitulo = new JLabel("BEM-VINDO", SwingConstants.CENTER);
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitulo.setForeground(new Color(0, 102, 204));

        JLabel lblSub = new JLabel("Informe suas credenciais para acessar", SwingConstants.CENTER);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(new Color(120, 130, 140));

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        card.add(lblTitulo, gbc);

        gbc.gridy = 1;
        card.add(lblSub, gbc);

        // Campos de Entrada
        gbc.gridwidth = 1;
        gbc.gridy = 2; gbc.gridx = 0;
        JLabel lblUser = new JLabel("Usuário:");
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 12));
        card.add(lblUser, gbc);

        gbc.gridx = 1;
        txtUsuario = new JTextField(15);
        txtUsuario.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        card.add(txtUsuario, gbc);

        gbc.gridy = 3; gbc.gridx = 0;
        JLabel lblPass = new JLabel("Senha:");
        lblPass.setFont(new Font("Segoe UI", Font.BOLD, 12));
        card.add(lblPass, gbc);

        gbc.gridx = 1;
        txtSenha = new JPasswordField(15);
        txtSenha.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        card.add(txtSenha, gbc);

        // Botão de Entrar
        gbc.gridy = 4; gbc.gridx = 0; gbc.gridwidth = 2;
        gbc.insets = new Insets(15, 5, 5, 5);

        JButton btnEntrar = new JButton("ENTRAR NO SISTEMA");
        btnEntrar.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnEntrar.setBackground(new Color(0, 102, 204));
        btnEntrar.setForeground(Color.WHITE);
        btnEntrar.setFocusPainted(false);
        btnEntrar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnEntrar.setPreferredSize(new Dimension(200, 40));

        card.add(btnEntrar, gbc);

        painelFundo.add(card, BorderLayout.CENTER);
        add(painelFundo);

        btnEntrar.addActionListener(e -> autenticar());
        getRootPane().setDefaultButton(btnEntrar);
    }

    private void autenticar() {
        String usuario = txtUsuario.getText().trim();
        String senha = new String(txtSenha.getPassword()).trim();

        if (usuarioDAO.autenticar(usuario, senha)) {
            new TelaPrincipal().setVisible(true);
            dispose();
        } else {
            JOptionPane.showMessageDialog(this, "Usuário ou senha incorretos!", "Acesso Negado", JOptionPane.ERROR_MESSAGE);
        }
    }
}