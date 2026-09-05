package br.com.adriano.dao;

import br.com.adriano.view.TelaVenda.ItemCarrinho;
import br.com.adriano.util.FabricaConexao;

import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class VendaDAO {

    private static final DateTimeFormatter FORMATO_BANCO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public VendaDAO() {
        criarTabelasSeNaoExistirem();
    }

    private void criarTabelasSeNaoExistirem() {
        String sqlVendas = "CREATE TABLE IF NOT EXISTS vendas (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "data TEXT, " +
                "forma_pagamento TEXT, " +
                "total REAL);";

        String sqlItens = "CREATE TABLE IF NOT EXISTS itens_venda (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "venda_id INTEGER, " +
                "produto_id INTEGER, " +
                "quantidade INTEGER, " +
                "preco_unitario REAL);";

        try (Connection conn = FabricaConexao.conectar();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sqlVendas);
            stmt.execute(sqlItens);

            // Garante compatibilidade adicionando colunas faltantes em bancos antigos
            try { stmt.execute("ALTER TABLE vendas ADD COLUMN data TEXT;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE vendas ADD COLUMN forma_pagamento TEXT;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE vendas ADD COLUMN total REAL;"); } catch (SQLException ignored) {}

        } catch (SQLException e) {
            System.out.println("Erro ao inicializar tabelas de vendas: " + e.getMessage());
        }
    }

    public boolean registrarVendaCompleta(double total, String formaPagamento, List<ItemCarrinho> itens) {
        String sqlVenda = "INSERT INTO vendas (data, forma_pagamento, total) VALUES (?, ?, ?)";
        String sqlItem = "INSERT INTO itens_venda (venda_id, produto_id, quantidade, preco_unitario) VALUES (?, ?, ?, ?)";
        String sqlEstoque = "UPDATE produtos SET estoque = estoque - ? WHERE id = ?";

        String dataBrasilia = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo")).format(FORMATO_BANCO);

        Connection conn = null;
        try {
            conn = FabricaConexao.conectar();
            conn.setAutoCommit(false);

            int vendaId = -1;
            try (PreparedStatement stmtVenda = conn.prepareStatement(sqlVenda, Statement.RETURN_GENERATED_KEYS)) {
                stmtVenda.setString(1, dataBrasilia);
                stmtVenda.setString(2, formaPagamento);
                stmtVenda.setDouble(3, total);
                stmtVenda.executeUpdate();

                try (ResultSet rs = stmtVenda.getGeneratedKeys()) {
                    if (rs.next()) {
                        vendaId = rs.getInt(1);
                    }
                }
            }

            if (vendaId == -1) {
                conn.rollback();
                return false;
            }

            try (PreparedStatement stmtItem = conn.prepareStatement(sqlItem);
                 PreparedStatement stmtEstoque = conn.prepareStatement(sqlEstoque)) {

                for (ItemCarrinho item : itens) {
                    stmtItem.setInt(1, vendaId);
                    stmtItem.setInt(2, item.produto.getId());
                    stmtItem.setInt(3, item.quantidade);
                    stmtItem.setDouble(4, item.produto.getPreco());
                    stmtItem.addBatch();

                    stmtEstoque.setInt(1, item.quantidade);
                    stmtEstoque.setInt(2, item.produto.getId());
                    stmtEstoque.addBatch();
                }
                stmtItem.executeBatch();
                stmtEstoque.executeBatch();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            System.out.println("Erro ao registrar venda: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    public List<Object[]> listarVendasFiltradas(boolean apenasHoje) {
        List<Object[]> lista = new ArrayList<>();
        String dataHojeBrasilia = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        String sql = "SELECT id, data, forma_pagamento, total FROM vendas ";
        if (apenasHoje) {
            sql += "WHERE data LIKE '" + dataHojeBrasilia + "%' ";
        }
        sql += "ORDER BY id DESC";

        try (Connection conn = FabricaConexao.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String dataBr = rs.getString("data");
                String formaPgto = rs.getString("forma_pagamento");
                double total = rs.getDouble("total");

                String dataFormatada = dataBr;
                try {
                    if (dataBr != null && dataBr.contains(" ")) {
                        String[] partes = dataBr.split(" ");
                        String[] dataPartes = partes[0].split("-");
                        if (dataPartes.length == 3) {
                            dataFormatada = dataPartes[2] + "/" + dataPartes[1] + "/" + dataPartes[0] + " " + partes[1];
                        }
                    }
                } catch (Exception e) {
                    dataFormatada = dataBr;
                }

                lista.add(new Object[]{id, dataFormatada, formaPgto, String.format("R$ %.2f", total), total});
            }

        } catch (SQLException e) {
            System.out.println("Erro ao listar vendas: " + e.getMessage());
        }
        return lista;
    }

    public boolean excluirVenda(int idVenda) {
        String sqlItens = "DELETE FROM itens_venda WHERE venda_id = ?";
        String sqlVenda = "DELETE FROM vendas WHERE id = ?";

        Connection conn = null;
        try {
            conn = FabricaConexao.conectar();
            conn.setAutoCommit(false);

            try (PreparedStatement stmtItens = conn.prepareStatement(sqlItens)) {
                stmtItens.setInt(1, idVenda);
                stmtItens.executeUpdate();
            }

            try (PreparedStatement stmtVenda = conn.prepareStatement(sqlVenda)) {
                stmtVenda.setInt(1, idVenda);
                stmtVenda.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            System.out.println("Erro ao excluir venda: " + e.getMessage());
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }
}