package br.com.adriano.service;

import br.com.adriano.db.ConexaoManager;

import java.math.BigDecimal;
import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Serviço de caixa - abertura, fechamento, suprimentos e retiradas.
 *
 * @since 2.0
 */
public final class CaixaService {

    private static final DateTimeFormatter FMT_BANCO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");

    private final ConexaoManager conexaoManager;

    public CaixaService(ConexaoManager conexaoManager) {
        this.conexaoManager = Objects.requireNonNull(conexaoManager);
    }

    public boolean isCaixaAberto() throws SQLException {
        String sql = "SELECT id FROM caixa WHERE status = 'ABERTO' LIMIT 1";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next();
        }
    }

    public Optional<CaixaInfo> getCaixaAberto() throws SQLException {
        String sql = "SELECT * FROM caixa WHERE status = 'ABERTO' ORDER BY id DESC LIMIT 1";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return Optional.of(mapCaixa(rs));
            }
        }
        return Optional.empty();
    }

    public CaixaInfo abrirCaixa(BigDecimal valorInicial, int usuarioId) throws SQLException {
        if (isCaixaAberto()) {
            throw new IllegalStateException("Já existe um caixa aberto");
        }
        if (valorInicial == null || valorInicial.signum() < 0) {
            throw new IllegalArgumentException("Valor inicial deve ser >= 0");
        }

        String dataAbertura = ZonedDateTime.now(ZONA_BR).format(FMT_BANCO);
        String sql = "INSERT INTO caixa (data_abertura, valor_inicial, status, usuario_abertura_id) VALUES (?, ?, 'ABERTO', ?)";

        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, dataAbertura);
            stmt.setBigDecimal(2, valorInicial);
            stmt.setInt(3, usuarioId);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return new CaixaInfo(
                            rs.getInt(1),
                            dataAbertura,
                            null,
                            valorInicial,
                            "ABERTO",
                            usuarioId,
                            null
                    );
                }
            }
        }
        throw new SQLException("Falha ao abrir caixa");
    }

    public CaixaInfo fecharCaixa(int caixaId, int usuarioId) throws SQLException {
        String sqlSelect = "SELECT * FROM caixa WHERE id = ? AND status = 'ABERTO'";
        String sqlUpdate = "UPDATE caixa SET data_fechamento = ?, status = 'FECHADO', usuario_fechamento_id = ? WHERE id = ?";

        String dataFechamento = ZonedDateTime.now(ZONA_BR).format(FMT_BANCO);

        try (Connection conn = conexaoManager.conectar()) {
            conn.setAutoCommit(false);
            try {
                CaixaInfo caixa;
                try (PreparedStatement stmt = conn.prepareStatement(sqlSelect)) {
                    stmt.setInt(1, caixaId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("Caixa não encontrado ou já fechado");
                        caixa = mapCaixa(rs);
                    }
                }

                try (PreparedStatement stmt = conn.prepareStatement(sqlUpdate)) {
                    stmt.setString(1, dataFechamento);
                    stmt.setInt(2, usuarioId);
                    stmt.setInt(3, caixaId);
                    stmt.executeUpdate();
                }

                conn.commit();
                return new CaixaInfo(
                        caixa.id(),
                        caixa.dataAbertura(),
                        dataFechamento,
                        caixa.valorInicial(),
                        "FECHADO",
                        caixa.usuarioAberturaId(),
                        usuarioId
                );
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public void suprimento(int caixaId, BigDecimal valor, String motivo, int usuarioId) throws SQLException {
        validarCaixaAberto(caixaId);
        if (valor == null || valor.signum() <= 0) throw new IllegalArgumentException("Valor deve ser > 0");

        String data = ZonedDateTime.now(ZONA_BR).format(FMT_BANCO);
        String sql = "INSERT INTO suprimentos_retiradas (caixa_id, tipo, valor, motivo, usuario_id, data) VALUES (?, 'SUPRIMENTO', ?, ?, ?, ?)";

        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, caixaId);
            stmt.setBigDecimal(2, valor);
            stmt.setString(3, motivo);
            stmt.setInt(4, usuarioId);
            stmt.setString(5, data);
            stmt.executeUpdate();
        }
    }

    public void retirada(int caixaId, BigDecimal valor, String motivo, int usuarioId) throws SQLException {
        validarCaixaAberto(caixaId);
        if (valor == null || valor.signum() <= 0) throw new IllegalArgumentException("Valor deve ser > 0");

        // Verificar se tem saldo suficiente
        BigDecimal disponivel = getSaldoDisponivel(caixaId);
        if (valor.compareTo(disponivel) > 0) {
            throw new IllegalStateException("Saldo insuficiente. Disponível: " + disponivel);
        }

        String data = ZonedDateTime.now(ZONA_BR).format(FMT_BANCO);
        String sql = "INSERT INTO suprimentos_retiradas (caixa_id, tipo, valor, motivo, usuario_id, data) VALUES (?, 'RETIRADA', ?, ?, ?, ?)";

        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, caixaId);
            stmt.setBigDecimal(2, valor);
            stmt.setString(3, motivo);
            stmt.setInt(4, usuarioId);
            stmt.setString(5, data);
            stmt.executeUpdate();
        }
    }

    public BigDecimal getSaldoDisponivel(int caixaId) throws SQLException {
        // Valor inicial + vendas dinheiro + suprimentos - retiradas
        // Use subqueries to avoid cross-join multiplication
        String sql = """
            SELECT
                c.valor_inicial,
                COALESCE((SELECT SUM(v.total) FROM vendas v
                    WHERE v.data >= c.data_abertura AND v.status = 'FINALIZADA' AND v.forma_pagamento = 'DINHEIRO'), 0) AS vendas_dinheiro,
                COALESCE((SELECT SUM(sr.valor) FROM suprimentos_retiradas sr
                    WHERE sr.caixa_id = c.id AND sr.tipo = 'SUPRIMENTO'), 0) AS suprimentos,
                COALESCE((SELECT SUM(sr.valor) FROM suprimentos_retiradas sr
                    WHERE sr.caixa_id = c.id AND sr.tipo = 'RETIRADA'), 0) AS retiradas
            FROM caixa c
            WHERE c.id = ?
            """;

        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, caixaId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BigDecimal inicial = rs.getBigDecimal("valor_inicial");
                    BigDecimal vendas = rs.getBigDecimal("vendas_dinheiro");
                    BigDecimal supr = rs.getBigDecimal("suprimentos");
                    BigDecimal ret = rs.getBigDecimal("retiradas");
                    return inicial.add(vendas).add(supr).subtract(ret);
                }
            }
        }
        return BigDecimal.ZERO;
    }

    public List<MovimentoCaixa> listarMovimentos(int caixaId) throws SQLException {
        String sql = """
            SELECT sr.*, u.nome as usuario_nome
            FROM suprimentos_retiradas sr
            JOIN usuarios u ON sr.usuario_id = u.id
            WHERE sr.caixa_id = ?
            ORDER BY sr.data
            """;
        List<MovimentoCaixa> lista = new ArrayList<>();
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, caixaId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(new MovimentoCaixa(
                            rs.getInt("id"),
                            rs.getString("tipo"),
                            rs.getBigDecimal("valor"),
                            rs.getString("motivo"),
                            rs.getInt("usuario_id"),
                            rs.getString("usuario_nome"),
                            rs.getString("data")
                    ));
                }
            }
        }
        return lista;
    }

    private void validarCaixaAberto(int caixaId) throws SQLException {
        String sql = "SELECT status FROM caixa WHERE id = ?";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, caixaId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next() || !"ABERTO".equals(rs.getString("status"))) {
                    throw new IllegalStateException("Caixa não está aberto");
                }
            }
        }
    }

    private CaixaInfo mapCaixa(ResultSet rs) throws SQLException {
        Integer usuarioFechamentoId = null;
        int usuarioFechamentoIdVal = rs.getInt("usuario_fechamento_id");
        if (!rs.wasNull()) {
            usuarioFechamentoId = usuarioFechamentoIdVal;
        }
        return new CaixaInfo(
                rs.getInt("id"),
                rs.getString("data_abertura"),
                rs.getString("data_fechamento"),
                rs.getBigDecimal("valor_inicial"),
                rs.getString("status"),
                rs.getInt("usuario_abertura_id"),
                usuarioFechamentoId
        );
    }

    /**
     * Informações do caixa.
     */
    public record CaixaInfo(
            int id,
            String dataAbertura,
            String dataFechamento,
            BigDecimal valorInicial,
            String status,
            int usuarioAberturaId,
            Integer usuarioFechamentoId
    ) {}

    /**
     * Movimento de caixa (suprimento/retirada).
     */
    public record MovimentoCaixa(
            int id,
            String tipo,
            BigDecimal valor,
            String motivo,
            int usuarioId,
            String usuarioNome,
            String data
    ) {}
}