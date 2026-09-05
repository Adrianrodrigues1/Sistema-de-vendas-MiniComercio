package br.com.adriano.dao;

import br.com.adriano.model.Produto;
import br.com.adriano.util.FabricaConexao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProdutoDAO {

    public boolean salvar(Produto produto) {
        String sql = "INSERT INTO produtos (nome, preco, estoque) VALUES (?, ?, ?)";
        try (Connection conn = FabricaConexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, produto.getNome());
            stmt.setDouble(2, produto.getPreco());
            stmt.setInt(3, produto.getEstoque());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Erro ao salvar produto: " + e.getMessage());
            return false;
        }
    }

    public boolean atualizar(Produto produto) {
        String sql = "UPDATE produtos SET nome = ?, preco = ?, estoque = ? WHERE id = ?";
        try (Connection conn = FabricaConexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, produto.getNome());
            stmt.setDouble(2, produto.getPreco());
            stmt.setInt(3, produto.getEstoque());
            stmt.setInt(4, produto.getId());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Erro ao atualizar produto: " + e.getMessage());
            return false;
        }
    }

    public boolean excluir(int id) {
        String sql = "DELETE FROM produtos WHERE id = ?";
        try (Connection conn = FabricaConexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Erro ao excluir produto: " + e.getMessage());
            return false;
        }
    }

    public List<Produto> listarTodos() {
        List<Produto> lista = new ArrayList<>();
        String sql = "SELECT * FROM produtos ORDER BY id DESC";
        try (Connection conn = FabricaConexao.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Produto p = new Produto(
                        rs.getInt("id"),
                        rs.getString("nome"),
                        rs.getDouble("preco"),
                        rs.getInt("estoque")
                );
                lista.add(p);
            }
        } catch (SQLException e) {
            System.out.println("Erro ao listar produtos: " + e.getMessage());
        }
        return lista;
    }

    public Produto buscarPorId(int id) {
        String sql = "SELECT * FROM produtos WHERE id = ?";
        try (Connection conn = FabricaConexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Produto(
                            rs.getInt("id"),
                            rs.getString("nome"),
                            rs.getDouble("preco"),
                            rs.getInt("estoque")
                    );
                }
            }
        } catch (SQLException e) {
            System.out.println("Erro ao buscar produto por ID: " + e.getMessage());
        }
        return null;
    }

    public boolean atualizarEstoque(int id, int quantidadeVendida) {
        String sql = "UPDATE produtos SET estoque = estoque - ? WHERE id = ? AND estoque >= ?";
        try (Connection conn = FabricaConexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, quantidadeVendida);
            stmt.setInt(2, id);
            stmt.setInt(3, quantidadeVendida);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Erro ao atualizar estoque: " + e.getMessage());
            return false;
        }
    }
}