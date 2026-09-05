package br.com.adriano.dao;

import br.com.adriano.util.FabricaConexao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UsuarioDAO {

    public boolean autenticar(String usuario, String senha) {
        String sql = "SELECT * FROM usuarios WHERE usuario = ? AND senha = ?";

        try (Connection conn = FabricaConexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, usuario);
            stmt.setString(2, senha);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next(); // Retorna true se encontrou o usuário
            }

        } catch (SQLException e) {
            System.out.println("Erro ao autenticar usuário: " + e.getMessage());
            return false;
        }
    }
}