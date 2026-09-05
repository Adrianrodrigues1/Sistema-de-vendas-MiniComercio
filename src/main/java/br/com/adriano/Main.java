package br.com.adriano;

import br.com.adriano.util.CriadorTabelas;
import br.com.adriano.view.TelaLogin;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {
        // Assegura a criação do banco e tabelas antes de abrir a interface
        CriadorTabelas.criarTabelas();

        // Abre a Tela de Login na Thread de interface do Swing
        SwingUtilities.invokeLater(() -> {
            new TelaLogin().setVisible(true);
        });
    }
}