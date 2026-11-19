package br.com.adriano.service;

import br.com.adriano.db.ConexaoManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Serviço de usuários - autenticação, perfis, hash de senha.
 * <p>
 * Usa PBKDF2-like com SHA-256 + salt para armazenamento seguro.
 *
 * @since 2.0
 */
public final class UsuarioService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_BYTES = 16;
    private static final int HASH_ITERATIONS = 10000;

    private final ConexaoManager conexaoManager;

    public UsuarioService(ConexaoManager conexaoManager) {
        this.conexaoManager = Objects.requireNonNull(conexaoManager);
    }

    public Optional<UsuarioInfo> autenticar(String login, String senha) throws SQLException {
        String sql = "SELECT * FROM usuarios WHERE usuario = ? AND ativo = 1";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, login);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String hashArmazenado = rs.getString("hash");
                    String saltArmazenado = rs.getString("salt");

                    // Se não tem hash/salt (legado v1.0), comparar senha direta
                    if (hashArmazenado == null || saltArmazenado == null) {
                        String senhaLegada = rs.getString("senha");
                        if (senha != null && senha.equals(senhaLegada)) {
                            // Migrar para hash moderno
                            migrarSenhaLegada(rs.getInt("id"), senha);
                            return Optional.of(mapUsuario(rs));
                        }
                        return Optional.empty();
                    }

                    // Verificar hash PBKDF2
                    if (verificarHash(senha, saltArmazenado, hashArmazenado)) {
                        return Optional.of(mapUsuario(rs));
                    }
                }
            }
        }
        return Optional.empty();
    }

    public UsuarioInfo criarUsuario(String login, String senha, String nome, String perfil, int usuarioCriadorId) throws SQLException {
        validarNovoUsuario(login, senha, perfil);

        String salt = gerarSalt();
        String hash = gerarHash(senha, salt);

        String sql = """
            INSERT INTO usuarios (usuario, senha, nome, perfil, hash, salt, ativo, data_criacao, usuario_criador_id)
            VALUES (?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP, ?)
            """;

        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, login);
            stmt.setString(2, senha); // Manter senha legada para compatibilidade
            stmt.setString(3, nome);
            stmt.setString(4, perfil);
            stmt.setString(5, hash);
            stmt.setString(6, salt);
            stmt.setInt(7, usuarioCriadorId);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return buscarPorId(rs.getInt(1)).orElseThrow();
                }
            }
        }
        throw new SQLException("Falha ao criar usuário");
    }

    public UsuarioInfo atualizarUsuario(int id, String nome, String perfil, boolean ativo, Integer usuarioAtualizadorId) throws SQLException {
        String sql = "UPDATE usuarios SET nome = ?, perfil = ?, ativo = ?, usuario_atualizador_id = ? WHERE id = ?";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nome);
            stmt.setString(2, perfil);
            stmt.setInt(3, ativo ? 1 : 0);
            if (usuarioAtualizadorId != null) stmt.setInt(4, usuarioAtualizadorId); else stmt.setNull(4, Types.INTEGER);
            stmt.setInt(5, id);
            if (stmt.executeUpdate() == 0) throw new SQLException("Usuário não encontrado: " + id);
        }
        return buscarPorId(id).orElseThrow();
    }

    public boolean alterarSenha(int usuarioId, String senhaAtual, String novaSenha) throws SQLException {
        Optional<UsuarioInfo> usuario = buscarPorId(usuarioId);
        if (usuario.isEmpty()) return false;

        // Verificar senha atual
        if (!autenticar(usuario.get().login(), senhaAtual).isPresent()) {
            return false;
        }

        String salt = gerarSalt();
        String hash = gerarHash(novaSenha, salt);

        String sql = "UPDATE usuarios SET senha = ?, hash = ?, salt = ? WHERE id = ?";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, novaSenha); // Legado
            stmt.setString(2, hash);
            stmt.setString(3, salt);
            stmt.setInt(4, usuarioId);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean redefinirSenha(int usuarioId, String novaSenha, int usuarioAdminId) throws SQLException {
        String salt = gerarSalt();
        String hash = gerarHash(novaSenha, salt);

        String sql = "UPDATE usuarios SET senha = ?, hash = ?, salt = ? WHERE id = ?";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, novaSenha);
            stmt.setString(2, hash);
            stmt.setString(3, salt);
            stmt.setInt(4, usuarioId);
            return stmt.executeUpdate() > 0;
        }
    }

    public Optional<UsuarioInfo> buscarPorId(int id) throws SQLException {
        String sql = "SELECT * FROM usuarios WHERE id = ?";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapUsuario(rs));
            }
        }
        return Optional.empty();
    }

    public Optional<UsuarioInfo> buscarPorLogin(String login) throws SQLException {
        String sql = "SELECT * FROM usuarios WHERE usuario = ?";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, login);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapUsuario(rs));
            }
        }
        return Optional.empty();
    }

    public List<UsuarioInfo> listarTodos() throws SQLException {
        String sql = "SELECT * FROM usuarios ORDER BY usuario";
        List<UsuarioInfo> lista = new ArrayList<>();
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                lista.add(mapUsuario(rs));
            }
        }
        return lista;
    }

    public boolean excluir(int id) throws SQLException {
        // Não permitir excluir a si mesmo ou último admin
        String sql = "DELETE FROM usuarios WHERE id = ? AND perfil != 'ADMIN'";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        }
    }

    private void validarNovoUsuario(String login, String senha, String perfil) {
        if (login == null || login.isBlank()) throw new IllegalArgumentException("Login obrigatório");
        if (senha == null || senha.length() < 4) throw new IllegalArgumentException("Senha deve ter pelo menos 4 caracteres");
        if (perfil == null || (!perfil.equals("ADMIN") && !perfil.equals("OPERADOR") && !perfil.equals("GERENTE"))) {
            throw new IllegalArgumentException("Perfil inválido: " + perfil);
        }
    }

    private void migrarSenhaLegada(int usuarioId, String senhaLegada) throws SQLException {
        String salt = gerarSalt();
        String hash = gerarHash(senhaLegada, salt);
        String sql = "UPDATE usuarios SET hash = ?, salt = ? WHERE id = ?";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, hash);
            stmt.setString(2, salt);
            stmt.setInt(3, usuarioId);
            stmt.executeUpdate();
        }
    }

    private String gerarSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private String gerarHash(String senha, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] saltBytes = Base64.getDecoder().decode(salt);
            byte[] hash = (senha + new String(saltBytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);

            for (int i = 0; i < HASH_ITERATIONS; i++) {
                digest.update(hash);
                hash = digest.digest(saltBytes);
            }
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }

    private boolean verificarHash(String senha, String salt, String hashEsperado) {
        String hashCalculado = gerarHash(senha, salt);
        return MessageDigest.isEqual(hashCalculado.getBytes(StandardCharsets.UTF_8), hashEsperado.getBytes(StandardCharsets.UTF_8));
    }

    private UsuarioInfo mapUsuario(ResultSet rs) throws SQLException {
        return new UsuarioInfo(
                rs.getInt("id"),
                rs.getString("usuario"),
                rs.getString("nome"),
                rs.getString("perfil"),
                rs.getInt("ativo") == 1
        );
    }

    /**
     * Informações do usuário (sem dados sensíveis).
     */
    public record UsuarioInfo(
            int id,
            String login,
            String nome,
            String perfil,
            boolean ativo
    ) {}
}