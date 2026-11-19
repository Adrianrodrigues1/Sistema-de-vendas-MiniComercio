package br.com.adriano.service;

import br.com.adriano.db.ConexaoManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Serviço de clientes.
 *
 * @since 2.0
 */
public final class ClienteService {

    private final ConexaoManager conexaoManager;

    public ClienteService(ConexaoManager conexaoManager) {
        this.conexaoManager = Objects.requireNonNull(conexaoManager);
    }

    public ClienteInfo salvar(ClienteInfo cliente) throws SQLException {
        validar(cliente);

        // Limpar CPF/CNPJ (remover formatação) antes de salvar
        String cpfCnpjLimpo = cliente.cpfCnpj() != null
            ? cliente.cpfCnpj().replaceAll("[^0-9]", "")
            : null;

        ClienteInfo clienteLimpo = new ClienteInfo(
                cliente.id(),
                cliente.nome(),
                cpfCnpjLimpo,
                cliente.telefone(),
                cliente.email(),
                cliente.endereco(),
                cliente.ativo(),
                cliente.dataCadastro()
        );

        if (cliente.id() == null || cliente.id() <= 0) {
            return inserir(clienteLimpo);
        } else {
            return atualizar(clienteLimpo);
        }
    }

    private ClienteInfo inserir(ClienteInfo cliente) throws SQLException {
        String sql = """
            INSERT INTO clientes (nome, cpf_cnpj, telefone, email, endereco, ativo)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, cliente.nome());
            stmt.setString(2, cliente.cpfCnpj());
            stmt.setString(3, cliente.telefone());
            stmt.setString(4, cliente.email());
            stmt.setString(5, cliente.endereco());
            stmt.setInt(6, cliente.ativo() ? 1 : 0);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return new ClienteInfo(
                            rs.getInt(1),
                            cliente.nome(),
                            cliente.cpfCnpj(),
                            cliente.telefone(),
                            cliente.email(),
                            cliente.endereco(),
                            cliente.ativo(),
                            null // data_cadastro
                    );
                }
            }
        }
        throw new SQLException("Falha ao inserir cliente");
    }

    private ClienteInfo atualizar(ClienteInfo cliente) throws SQLException {
        String sql = """
            UPDATE clientes
            SET nome = ?, cpf_cnpj = ?, telefone = ?, email = ?, endereco = ?, ativo = ?
            WHERE id = ?
            """;

        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, cliente.nome());
            stmt.setString(2, cliente.cpfCnpj());
            stmt.setString(3, cliente.telefone());
            stmt.setString(4, cliente.email());
            stmt.setString(5, cliente.endereco());
            stmt.setInt(6, cliente.ativo() ? 1 : 0);
            stmt.setInt(7, cliente.id());

            if (stmt.executeUpdate() == 0) {
                throw new SQLException("Cliente não encontrado: " + cliente.id());
            }
        }
        return cliente;
    }

    public Optional<ClienteInfo> buscarPorId(int id) throws SQLException {
        String sql = "SELECT * FROM clientes WHERE id = ?";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapCliente(rs));
            }
        }
        return Optional.empty();
    }

    public Optional<ClienteInfo> buscarPorCpfCnpj(String cpfCnpj) throws SQLException {
        if (cpfCnpj == null || cpfCnpj.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM clientes WHERE cpf_cnpj = ?";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, cpfCnpj);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapCliente(rs));
            }
        }
        return Optional.empty();
    }

    public List<ClienteInfo> listarTodos(boolean apenasAtivos) throws SQLException {
        String sql = "SELECT * FROM clientes" + (apenasAtivos ? " WHERE ativo = 1" : "") + " ORDER BY nome";
        List<ClienteInfo> lista = new ArrayList<>();
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                lista.add(mapCliente(rs));
            }
        }
        return lista;
    }

    public List<ClienteInfo> buscarPorTermo(String termo) throws SQLException {
        String sql = """
            SELECT * FROM clientes
            WHERE ativo = 1 AND (nome LIKE ? OR cpf_cnpj LIKE ? OR telefone LIKE ?)
            ORDER BY nome
            """;
        List<ClienteInfo> lista = new ArrayList<>();
        String like = "%" + termo + "%";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, like);
            stmt.setString(2, like);
            stmt.setString(3, like);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapCliente(rs));
                }
            }
        }
        return lista;
    }

    public boolean excluir(int id) throws SQLException {
        // Verificar se tem vendas associadas
        String sqlCheck = "SELECT 1 FROM vendas WHERE cliente_id = ? LIMIT 1";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sqlCheck)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalStateException("Não é possível excluir cliente com vendas associadas. Desative-o.");
                }
            }
        }

        String sql = "DELETE FROM clientes WHERE id = ?";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        }
    }

    private void validar(ClienteInfo cliente) {
        if (cliente.nome() == null || cliente.nome().isBlank()) {
            throw new IllegalArgumentException("Nome é obrigatório");
        }
        if (cliente.cpfCnpj() != null && !cliente.cpfCnpj().isBlank()) {
            String limpo = cliente.cpfCnpj().replaceAll("[^0-9]", "");
            if (limpo.length() != 11 && limpo.length() != 14) {
                throw new IllegalArgumentException("CPF/CNPJ inválido");
            }
        }
    }

    private ClienteInfo mapCliente(ResultSet rs) throws SQLException {
        return new ClienteInfo(
                rs.getInt("id"),
                rs.getString("nome"),
                rs.getString("cpf_cnpj"),
                rs.getString("telefone"),
                rs.getString("email"),
                rs.getString("endereco"),
                rs.getInt("ativo") == 1,
                rs.getString("data_cadastro")
        );
    }

    /**
     * Informações do cliente.
     */
    public record ClienteInfo(
            Integer id,
            String nome,
            String cpfCnpj,
            String telefone,
            String email,
            String endereco,
            boolean ativo,
            String dataCadastro
    ) {}
}