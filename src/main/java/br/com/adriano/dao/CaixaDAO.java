package br.com.adriano.dao;

import br.com.adriano.util.FabricaConexao;

import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class CaixaDAO {

    private static final DateTimeFormatter FORMATO_BANCO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public boolean isCaixaAberto() {
        String sql = "SELECT status FROM caixa WHERE status = 'ABERTO' LIMIT 1";
        try (Connection conn = FabricaConexao.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next();
        } catch (SQLException e) {
            // Caso a tabela ainda não exista, cria dinamicamente
            criarTabelaCaixaSeNaoExistir();
            return false;
        }
    }

    public boolean abrirCaixa(double valorInicial) {
        if (isCaixaAberto()) return false;

        String dataAbertura = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo")).format(FORMATO_BANCO);
        String sql = "INSERT INTO caixa (data_abertura, valor_inicial, status) VALUES (?, ?, 'ABERTO')";

        try (Connection conn = FabricaConexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dataAbertura);
            stmt.setDouble(2, valorInicial);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Erro ao abrir caixa: " + e.getMessage());
            return false;
        }
    }

    public boolean fecharCaixa() {
        if (!isCaixaAberto()) return false;

        String dataFechamento = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo")).format(FORMATO_BANCO);
        String sql = "UPDATE caixa SET data_fechamento = ?, status = 'FECHADO' WHERE status = 'ABERTO'";

        try (Connection conn = FabricaConexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dataFechamento);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Erro ao fechar caixa: " + e.getMessage());
            return false;
        }
    }

    public double getValorInicialAtual() {
        String sql = "SELECT valor_inicial FROM caixa WHERE status = 'ABERTO' ORDER BY id DESC LIMIT 1";
        try (Connection conn = FabricaConexao.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getDouble("valor_inicial");
            }
        } catch (SQLException e) {
            System.out.println("Erro ao buscar valor inicial: " + e.getMessage());
        }
        return 0.0;
    }

    public double getTotalVendasCaixaAtual() {
        String sqlData = "SELECT data_abertura FROM caixa WHERE status = 'ABERTO' ORDER BY id DESC LIMIT 1";
        String dataAbertura = null;

        try (Connection conn = FabricaConexao.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlData)) {
            if (rs.next()) {
                dataAbertura = rs.getString("data_abertura");
            }
        } catch (SQLException e) {
            return 0.0;
        }

        if (dataAbertura == null) return 0.0;

        String sqlVendas = "SELECT SUM(total) AS total_vendas FROM vendas WHERE data >= ?";
        try (Connection conn = FabricaConexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sqlVendas)) {
            stmt.setString(1, dataAbertura);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("total_vendas");
            }
        } catch (SQLException e) {
            System.out.println("Erro ao calcular total de vendas do caixa: " + e.getMessage());
        }
        return 0.0;
    }

    private void criarTabelaCaixaSeNaoExistir() {
        String sql = "CREATE TABLE IF NOT EXISTS caixa ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "data_abertura TEXT, "
                + "data_fechamento TEXT, "
                + "valor_inicial REAL, "
                + "status TEXT"
                + ");";
        try (Connection conn = FabricaConexao.conectar();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}