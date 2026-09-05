package br.com.adriano.dao;

import br.com.adriano.util.FabricaConexao;

import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class RetiradaDAO {

    private static final DateTimeFormatter FORMATO_BANCO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public RetiradaDAO() {
        criarTabelaSeNaoExiste();
    }

    private void criarTabelaSeNaoExiste() {
        String sql = "CREATE TABLE IF NOT EXISTS retiradas (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "data TEXT, " +
                "valor REAL, " +
                "motivo TEXT);";
        try (Connection conn = FabricaConexao.conectar();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.out.println("Erro ao criar tabela retiradas: " + e.getMessage());
        }
    }

    public boolean registrarRetirada(double valor, String motivo) {
        String sql = "INSERT INTO retiradas (data, valor, motivo) VALUES (?, ?, ?)";
        String dataBrasilia = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo")).format(FORMATO_BANCO);

        try (Connection conn = FabricaConexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dataBrasilia);
            stmt.setDouble(2, valor);
            stmt.setString(3, motivo);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Erro ao registrar retirada: " + e.getMessage());
            return false;
        }
    }

    public double getTotalRetiradasHoje() {
        String dataHoje = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String sql = "SELECT SUM(valor) FROM retiradas WHERE data LIKE '" + dataHoje + "%'";

        try (Connection conn = FabricaConexao.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            System.out.println("Erro ao calcular total de retiradas: " + e.getMessage());
        }
        return 0.0;
    }
}