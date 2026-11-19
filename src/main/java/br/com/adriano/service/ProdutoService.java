package br.com.adriano.service;

import br.com.adriano.db.ConexaoManager;
import br.com.adriano.model.Produto;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Serviço de produtos - regras de negócio e transações.
 * <p>
 * Camada entre UI/Controller e DAO. Não expõe SQL nem Connection.
 *
 * @since 2.0
 */
public final class ProdutoService {

    private final ConexaoManager conexaoManager;

    public ProdutoService(ConexaoManager conexaoManager) {
        this.conexaoManager = Objects.requireNonNull(conexaoManager);
    }

    public Produto salvar(Produto produto) throws SQLException {
        validar(produto);

        String sql = """
            INSERT INTO produtos (codigo, nome, categoria, preco, estoque, imagem, ativo)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, produto.getCodigo());
            stmt.setString(2, produto.getNome());
            stmt.setString(3, produto.getCategoria());
            stmt.setBigDecimal(4, produto.getPrecoBigDecimal());
            stmt.setInt(5, produto.getEstoque());
            stmt.setString(6, produto.getImagem());
            stmt.setInt(7, produto.isAtivo() ? 1 : 0);

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return new Produto(
                            rs.getInt(1),
                            produto.getCodigo(),
                            produto.getNome(),
                            produto.getCategoria(),
                            produto.getPrecoBigDecimal(),
                            produto.getEstoque(),
                            produto.getImagem(),
                            produto.isAtivo()
                    );
                }
            }
        }
        throw new SQLException("Falha ao obter ID gerado");
    }

    public Produto atualizar(Produto produto) throws SQLException {
        validar(produto);
        if (produto.getId() == null || produto.getId() <= 0) {
            throw new IllegalArgumentException("ID inválido para atualização");
        }

        String sql = """
            UPDATE produtos
            SET codigo = ?, nome = ?, categoria = ?, preco = ?, estoque = ?, imagem = ?, ativo = ?
            WHERE id = ?
            """;

        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, produto.getCodigo());
            stmt.setString(2, produto.getNome());
            stmt.setString(3, produto.getCategoria());
            stmt.setBigDecimal(4, produto.getPrecoBigDecimal());
            stmt.setInt(5, produto.getEstoque());
            stmt.setString(6, produto.getImagem());
            stmt.setInt(7, produto.isAtivo() ? 1 : 0);
            stmt.setInt(8, produto.getId());

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Produto não encontrado: " + produto.getId());
            }
        }
        return produto;
    }

    public boolean excluir(int id) throws SQLException {
        // Verificar se tem vendas associadas
        if (temVendasAssociadas(id)) {
            throw new IllegalStateException("Não é possível excluir produto com vendas registradas. Desative-o.");
        }

        String sql = "DELETE FROM produtos WHERE id = ?";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        }
    }

    public Optional<Produto> buscarPorId(int id) throws SQLException {
        String sql = "SELECT * FROM produtos WHERE id = ?";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Produto> buscarPorCodigo(String codigo) throws SQLException {
        String sql = "SELECT * FROM produtos WHERE codigo = ?";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, codigo);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Produto> listarTodos(boolean apenasAtivos) throws SQLException {
        String sql = "SELECT * FROM produtos" + (apenasAtivos ? " WHERE ativo = 1" : "") + " ORDER BY nome";
        List<Produto> lista = new ArrayList<>();
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                lista.add(mapRow(rs));
            }
        }
        return lista;
    }

    public List<Produto> buscarPorCategoria(String categoria) throws SQLException {
        String sql = "SELECT * FROM produtos WHERE categoria = ? AND ativo = 1 ORDER BY nome";
        List<Produto> lista = new ArrayList<>();
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, categoria);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapRow(rs));
                }
            }
        }
        return lista;
    }

    public List<Produto> buscarPorTermo(String termo) throws SQLException {
        String sql = """
            SELECT * FROM produtos
            WHERE ativo = 1 AND (nome LIKE ? OR codigo LIKE ?)
            ORDER BY nome
            """;
        List<Produto> lista = new ArrayList<>();
        String like = "%" + termo + "%";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, like);
            stmt.setString(2, like);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapRow(rs));
                }
            }
        }
        return lista;
    }

    public List<String> listarCategorias() throws SQLException {
        String sql = "SELECT DISTINCT categoria FROM produtos WHERE ativo = 1 AND categoria IS NOT NULL ORDER BY categoria";
        List<String> categorias = new ArrayList<>();
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                categorias.add(rs.getString("categoria"));
            }
        }
        return categorias;
    }

    public boolean ajustarEstoque(int produtoId, int delta, String motivo, String referenciaTipo, Integer referenciaId, Integer usuarioId) throws SQLException {
        if (delta == 0) return true;

        String sqlSelect = "SELECT estoque FROM produtos WHERE id = ?";
        String sqlUpdate = "UPDATE produtos SET estoque = estoque + ? WHERE id = ?";
        String sqlMovimento = """
            INSERT INTO movimentos_estoque (produto_id, tipo, quantidade, quantidade_anterior, quantidade_nova, motivo, referencia_tipo, referencia_id, usuario_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = conexaoManager.conectar()) {
            conn.setAutoCommit(false);
            try {
                int estoqueAtual;
                try (PreparedStatement stmt = conn.prepareStatement(sqlSelect)) {
                    stmt.setInt(1, produtoId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Produto não encontrado: " + produtoId);
                        }
                        estoqueAtual = rs.getInt("estoque");
                    }
                }

                int novoEstoque = estoqueAtual + delta;
                if (novoEstoque < 0) {
                    throw new IllegalStateException("Estoque insuficiente. Atual: " + estoqueAtual + ", solicitado: " + (-delta));
                }

                try (PreparedStatement stmt = conn.prepareStatement(sqlUpdate)) {
                    stmt.setInt(1, delta);
                    stmt.setInt(2, produtoId);
                    stmt.executeUpdate();
                }

                try (PreparedStatement stmt = conn.prepareStatement(sqlMovimento)) {
                    String tipo = delta > 0 ? "ENTRADA" : "SAIDA";
                    stmt.setInt(1, produtoId);
                    stmt.setString(2, tipo);
                    stmt.setInt(3, Math.abs(delta));
                    stmt.setInt(4, estoqueAtual);
                    stmt.setInt(5, novoEstoque);
                    stmt.setString(6, motivo);
                    stmt.setString(7, referenciaTipo);
                    if (referenciaId != null) stmt.setInt(8, referenciaId); else stmt.setNull(8, Types.INTEGER);
                    if (usuarioId != null) stmt.setInt(9, usuarioId); else stmt.setNull(9, Types.INTEGER);
                    stmt.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private boolean temVendasAssociadas(int produtoId) throws SQLException {
        String sql = "SELECT 1 FROM itens_venda WHERE produto_id = ? LIMIT 1";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, produtoId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void validar(Produto produto) {
        Objects.requireNonNull(produto, "Produto não pode ser nulo");
        if (produto.getCodigo() == null || produto.getCodigo().isBlank()) {
            throw new IllegalArgumentException("Código é obrigatório");
        }
        if (produto.getNome() == null || produto.getNome().isBlank()) {
            throw new IllegalArgumentException("Nome é obrigatório");
        }
        if (produto.getPrecoBigDecimal() == null || produto.getPrecoBigDecimal().signum() < 0) {
            throw new IllegalArgumentException("Preço deve ser >= 0");
        }
        if (produto.getEstoque() < 0) {
            throw new IllegalArgumentException("Estoque não pode ser negativo");
        }
    }

    private Produto mapRow(ResultSet rs) throws SQLException {
        BigDecimal preco = rs.getBigDecimal("preco");
        return new Produto(
                rs.getInt("id"),
                rs.getString("codigo"),
                rs.getString("nome"),
                rs.getString("categoria"),
                preco != null ? preco : BigDecimal.ZERO,
                rs.getInt("estoque"),
                rs.getString("imagem"),
                rs.getInt("ativo") == 1
        );
    }
}