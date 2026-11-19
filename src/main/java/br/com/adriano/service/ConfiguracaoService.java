package br.com.adriano.service;

import br.com.adriano.db.ConexaoManager;
import br.com.adriano.ui.tema.GerenciadorTema;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Serviço de configurações do sistema.
 *
 * @since 2.0
 */
public final class ConfiguracaoService {

    private final ConexaoManager conexaoManager;

    public ConfiguracaoService(ConexaoManager conexaoManager) {
        this.conexaoManager = Objects.requireNonNull(conexaoManager);
    }

    public Optional<String> get(String chave) throws SQLException {
        String sql = "SELECT valor FROM configuracoes WHERE chave = ?";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, chave);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.ofNullable(rs.getString("valor"));
            }
        }
        return Optional.empty();
    }

    public String getOrDefault(String chave, String padrao) throws SQLException {
        return get(chave).orElse(padrao);
    }

    public void set(String chave, String valor) throws SQLException {
        String sql = """
            INSERT INTO configuracoes (chave, valor) VALUES (?, ?)
            ON CONFLICT(chave) DO UPDATE SET valor = excluded.valor
            """;
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, chave);
            stmt.setString(2, valor);
            stmt.executeUpdate();
        }
    }

    public Map<String, String> getAll() throws SQLException {
        Map<String, String> mapa = new HashMap<>();
        String sql = "SELECT chave, valor FROM configuracoes";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                mapa.put(rs.getString("chave"), rs.getString("valor"));
            }
        }
        return mapa;
    }

    // ===== Configurações tipadas =====

    public GerenciadorTema.Tema getTema() throws SQLException {
        String valor = getOrDefault("tema", "CLARO");
        return GerenciadorTema.Tema.valueOf(valor);
    }

    public void setTema(GerenciadorTema.Tema tema) throws SQLException {
        set("tema", tema.name());
    }

    public String getMoeda() throws SQLException {
        return getOrDefault("moeda", "BRL");
    }

    public int getCasasDecimais() throws SQLException {
        return Integer.parseInt(getOrDefault("casas_decimais", "2"));
    }

    public String getTimezone() throws SQLException {
        return getOrDefault("timezone", "America/Sao_Paulo");
    }

    public boolean isBackupAuto() throws SQLException {
        return Boolean.parseBoolean(getOrDefault("backup_auto", "true"));
    }

    public int getBackupRetencaoDias() throws SQLException {
        return Integer.parseInt(getOrDefault("backup_retencao_dias", "30"));
    }

    public void setBackupAuto(boolean ativo) throws SQLException {
        set("backup_auto", String.valueOf(ativo));
    }

    public void setBackupRetencaoDias(int dias) throws SQLException {
        set("backup_retencao_dias", String.valueOf(Math.max(1, dias)));
    }
}