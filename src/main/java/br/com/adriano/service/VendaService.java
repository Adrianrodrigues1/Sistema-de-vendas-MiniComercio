package br.com.adriano.service;

import br.com.adriano.db.ConexaoManager;
import br.com.adriano.model.Produto;
import br.com.adriano.ui.pdv.Carrinho;
import br.com.adriano.ui.pdv.Dinheiro;
import br.com.adriano.ui.pdv.Pagamento;

import java.math.BigDecimal;
import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço de vendas - regras de negócio, transações e integração com PDV.
 *
 * @since 2.0
 */
public final class VendaService {

    private static final DateTimeFormatter FMT_BANCO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");

    private final ConexaoManager conexaoManager;
    private final ProdutoService produtoService;

    public VendaService(ConexaoManager conexaoManager, ProdutoService produtoService) {
        this.conexaoManager = Objects.requireNonNull(conexaoManager);
        this.produtoService = Objects.requireNonNull(produtoService);
    }

    /**
     * Registra uma venda completa com itens, pagamento e baixa de estoque.
     *
     * @param carrinho       carrinho com itens
     * @param pagamento      pagamento realizado
     * @param clienteId      ID do cliente (opcional)
     * @param usuarioId      ID do usuário operador
     * @return ID da venda gerada
     * @throws SQLException  se falhar
     */
    public int registrarVenda(Carrinho carrinho, Pagamento pagamento, Integer clienteId, int usuarioId) throws SQLException {
        validar(carrinho, pagamento);

        String dataBrasilia = ZonedDateTime.now(ZONA_BR).format(FMT_BANCO);
        String numeroVenda = "V" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String sqlVenda = """
            INSERT INTO vendas (numero, data, forma_pagamento, subtotal, desconto, total, cliente_id, usuario_id, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'FINALIZADA')
            """;
        String sqlItem = """
            INSERT INTO itens_venda (venda_id, produto_id, quantidade, preco_unitario, preco_total)
            VALUES (?, ?, ?, ?, ?)
            """;
        String sqlEstoque = "UPDATE produtos SET estoque = estoque - ? WHERE id = ? AND estoque >= ?";
        String sqlMovimento = """
            INSERT INTO movimentos_estoque (produto_id, tipo, quantidade, quantidade_anterior, quantidade_nova, motivo, referencia_tipo, referencia_id, usuario_id)
            VALUES (?, 'SAIDA', ?, ?, ?, 'VENDA', 'VENDA', ?, ?)
            """;

        try (Connection conn = conexaoManager.conectar()) {
            conn.setAutoCommit(false);
            try {
                int vendaId;

                // 1. Inserir venda
                try (PreparedStatement stmt = conn.prepareStatement(sqlVenda, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, numeroVenda);
                    stmt.setString(2, dataBrasilia);
                    stmt.setString(3, pagamento.forma().name());
                    stmt.setBigDecimal(4, carrinho.subtotal());
                    stmt.setBigDecimal(5, carrinho.desconto());
                    stmt.setBigDecimal(6, carrinho.total());
                    if (clienteId != null) stmt.setInt(7, clienteId); else stmt.setNull(7, Types.INTEGER);
                    stmt.setInt(8, usuarioId);
                    stmt.executeUpdate();

                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (!rs.next()) throw new SQLException("Falha ao gerar ID da venda");
                        vendaId = rs.getInt(1);
                    }
                }

                // 2. Inserir itens e baixar estoque
                try (PreparedStatement stmtItem = conn.prepareStatement(sqlItem);
                     PreparedStatement stmtEstoque = conn.prepareStatement(sqlEstoque);
                     PreparedStatement stmtMov = conn.prepareStatement(sqlMovimento)) {

                    for (Carrinho.Item item : carrinho.itens()) {
                        Produto prod = produtoService.buscarPorId((int) item.produto().id()).orElseThrow();

                        // Verificar e baixar estoque atomicamente
                        stmtEstoque.setInt(1, item.quantidade());
                        stmtEstoque.setInt(2, prod.getId());
                        stmtEstoque.setInt(3, item.quantidade());
                        int rows = stmtEstoque.executeUpdate();
                        if (rows == 0) {
                            throw new SQLException("Estoque insuficiente para: " + prod.getNome() + " (ID: " + prod.getId() + ")");
                        }

                        // Item da venda
                        BigDecimal precoTotal = prod.getPrecoBigDecimal().multiply(BigDecimal.valueOf(item.quantidade()));
                        stmtItem.setInt(1, vendaId);
                        stmtItem.setInt(2, prod.getId());
                        stmtItem.setInt(3, item.quantidade());
                        stmtItem.setBigDecimal(4, prod.getPrecoBigDecimal());
                        stmtItem.setBigDecimal(5, precoTotal);
                        stmtItem.addBatch();

                        // Movimento de estoque (pegar estoque anterior)
                        int estoqueAnterior = prod.getEstoque();
                        int estoqueNovo = estoqueAnterior - item.quantidade();
                        stmtMov.setInt(1, prod.getId());
                        stmtMov.setInt(2, item.quantidade());
                        stmtMov.setInt(3, estoqueAnterior);
                        stmtMov.setInt(4, estoqueNovo);
                        stmtMov.setInt(5, vendaId);
                        stmtMov.setInt(6, usuarioId);
                        stmtMov.addBatch();
                    }
                    stmtItem.executeBatch();
                    stmtMov.executeBatch();
                }

                conn.commit();
                return vendaId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Cancela uma venda (estorno) com motivo e auditoria.
     *
     * @param vendaId           ID da venda
     * @param motivo            motivo do cancelamento
     * @param usuarioCancelador ID do usuário que cancela
     * @return true se cancelou
     * @throws SQLException se falhar
     */
    public boolean cancelarVenda(int vendaId, String motivo, int usuarioCancelador) throws SQLException {
        String sqlVenda = "SELECT status FROM vendas WHERE id = ?";
        String sqlItens = "SELECT produto_id, quantidade FROM itens_venda WHERE venda_id = ?";
        String sqlUpdateVenda = """
            UPDATE vendas
            SET status = 'CANCELADA', cancelamento_motivo = ?, cancelamento_data = ?, cancelamento_usuario_id = ?
            WHERE id = ? AND status = 'FINALIZADA'
            """;
        String sqlEstoque = "UPDATE produtos SET estoque = estoque + ? WHERE id = ?";
        String sqlMovimento = """
            INSERT INTO movimentos_estoque (produto_id, tipo, quantidade, quantidade_anterior, quantidade_nova, motivo, referencia_tipo, referencia_id, usuario_id)
            VALUES (?, 'DEVOLUCAO', ?, ?, ?, ?, 'CANCELAMENTO', ?, ?)
            """;

        try (Connection conn = conexaoManager.conectar()) {
            conn.setAutoCommit(false);
            try {
                // Verificar status
                String status;
                try (PreparedStatement stmt = conn.prepareStatement(sqlVenda)) {
                    stmt.setInt(1, vendaId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) return false;
                        status = rs.getString("status");
                    }
                }
                if (!"FINALIZADA".equals(status)) {
                    throw new IllegalStateException("Venda não pode ser cancelada. Status: " + status);
                }

                // Buscar itens para estornar estoque
                List<ItemEstoque> itens = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement(sqlItens)) {
                    stmt.setInt(1, vendaId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            itens.add(new ItemEstoque(rs.getInt("produto_id"), rs.getInt("quantidade")));
                        }
                    }
                }

                // Estornar estoque e registrar movimentos
                try (PreparedStatement stmtEstoque = conn.prepareStatement(sqlEstoque);
                     PreparedStatement stmtMov = conn.prepareStatement(sqlMovimento)) {

                    for (ItemEstoque item : itens) {
                        // Obter estoque atual
                        int estoqueAtual;
                        try (PreparedStatement stmt = conn.prepareStatement("SELECT estoque FROM produtos WHERE id = ?")) {
                            stmt.setInt(1, item.produtoId);
                            try (ResultSet rs = stmt.executeQuery()) {
                                if (!rs.next()) throw new SQLException("Produto não encontrado: " + item.produtoId);
                                estoqueAtual = rs.getInt("estoque");
                            }
                        }

                        int estoqueNovo = estoqueAtual + item.quantidade;

                        stmtEstoque.setInt(1, item.quantidade);
                        stmtEstoque.setInt(2, item.produtoId);
                        stmtEstoque.executeUpdate();

                        stmtMov.setInt(1, item.produtoId);
                        stmtMov.setInt(2, item.quantidade);
                        stmtMov.setInt(3, estoqueAtual);
                        stmtMov.setInt(4, estoqueNovo);
                        stmtMov.setString(5, "Cancelamento: " + motivo);
                        stmtMov.setInt(6, vendaId);
                        stmtMov.setInt(7, usuarioCancelador);
                        stmtMov.addBatch();
                    }
                    stmtMov.executeBatch();
                }

                // Marcar venda como cancelada
                String dataCancelamento = ZonedDateTime.now(ZONA_BR).format(FMT_BANCO);
                try (PreparedStatement stmt = conn.prepareStatement(sqlUpdateVenda)) {
                    stmt.setString(1, motivo);
                    stmt.setString(2, dataCancelamento);
                    stmt.setInt(3, usuarioCancelador);
                    stmt.setInt(4, vendaId);
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

    public Optional<VendaResumo> buscarVenda(int vendaId) throws SQLException {
        String sql = "SELECT * FROM vendas WHERE id = ?";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, vendaId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapVendaResumo(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<VendaResumo> listarVendas(boolean apenasHoje, int limite) throws SQLException {
        String dataHoje = ZonedDateTime.now(ZONA_BR).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        StringBuilder sql = new StringBuilder("SELECT * FROM vendas ");
        if (apenasHoje) {
            sql.append("WHERE data LIKE '").append(dataHoje).append("%' ");
        }
        sql.append("ORDER BY id DESC");
        if (limite > 0) sql.append(" LIMIT ").append(limite);

        List<VendaResumo> lista = new ArrayList<>();
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql.toString());
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                lista.add(mapVendaResumo(rs));
            }
        }
        return lista;
    }

    public TotaisCaixa calcularTotaisCaixa(int caixaId) throws SQLException {
        String sqlVendas = """
            SELECT
                COALESCE(SUM(CASE WHEN forma_pagamento = 'DINHEIRO' THEN total ELSE 0 END), 0) AS total_dinheiro,
                COALESCE(SUM(CASE WHEN forma_pagamento = 'PIX' THEN total ELSE 0 END), 0) AS total_pix,
                COALESCE(SUM(CASE WHEN forma_pagamento = 'DEBITO' THEN total ELSE 0 END), 0) AS total_debito,
                COALESCE(SUM(CASE WHEN forma_pagamento = 'CREDITO' THEN total ELSE 0 END), 0) AS total_credito,
                COALESCE(SUM(total), 0) AS total_geral,
                COUNT(*) AS qtd_vendas
            FROM vendas
            WHERE data >= (SELECT data_abertura FROM caixa WHERE id = ?)
              AND status = 'FINALIZADA'
            """;
        String sqlSuprimentos = """
            SELECT
                COALESCE(SUM(CASE WHEN tipo = 'SUPRIMENTO' THEN valor ELSE 0 END), 0) AS total_suprimentos,
                COALESCE(SUM(CASE WHEN tipo = 'RETIRADA' THEN valor ELSE 0 END), 0) AS total_retiradas
            FROM suprimentos_retiradas
            WHERE caixa_id = ?
            """;

        TotaisCaixa totais = new TotaisCaixa();
        try (Connection conn = conexaoManager.conectar()) {
            try (PreparedStatement stmt = conn.prepareStatement(sqlVendas)) {
                stmt.setInt(1, caixaId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totais.totalDinheiro = rs.getBigDecimal("total_dinheiro");
                        totais.totalPix = rs.getBigDecimal("total_pix");
                        totais.totalDebito = rs.getBigDecimal("total_debito");
                        totais.totalCredito = rs.getBigDecimal("total_credito");
                        totais.totalGeral = rs.getBigDecimal("total_geral");
                        totais.quantidadeVendas = rs.getInt("qtd_vendas");
                    }
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(sqlSuprimentos)) {
                stmt.setInt(1, caixaId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totais.totalSuprimentos = rs.getBigDecimal("total_suprimentos");
                        totais.totalRetiradas = rs.getBigDecimal("total_retiradas");
                    }
                }
            }
        }
        return totais;
    }

    private void validar(Carrinho carrinho, Pagamento pagamento) {
        Objects.requireNonNull(carrinho, "Carrinho não pode ser nulo");
        Objects.requireNonNull(pagamento, "Pagamento não pode ser nulo");
        if (carrinho.vazio()) throw new IllegalStateException("Carrinho vazio");
        if (carrinho.total().signum() <= 0) throw new IllegalStateException("Total inválido");
        if (pagamento.forma() == Pagamento.Forma.DINHEIRO && pagamento.recebido().compareTo(carrinho.total()) < 0) {
            throw new IllegalStateException("Valor recebido menor que total");
        }
    }

    private VendaResumo mapVendaResumo(ResultSet rs) throws SQLException {
        Integer clienteId = null;
        int clienteIdVal = rs.getInt("cliente_id");
        if (!rs.wasNull()) {
            clienteId = clienteIdVal;
        }
        return new VendaResumo(
                rs.getInt("id"),
                rs.getString("numero"),
                rs.getString("data"),
                rs.getString("forma_pagamento"),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("desconto"),
                rs.getBigDecimal("total"),
                clienteId,
                rs.getInt("usuario_id"),
                rs.getString("status")
        );
    }

    private record ItemEstoque(int produtoId, int quantidade) {}

    /**
     * Resumo de venda para listagens/relatórios.
     */
    public record VendaResumo(
            int id,
            String numero,
            String data,
            String formaPagamento,
            BigDecimal subtotal,
            BigDecimal desconto,
            BigDecimal total,
            Integer clienteId,
            int usuarioId,
            String status
    ) {}

    /**
     * Totais do caixa para fechamento.
     */
    public static class TotaisCaixa {
        public BigDecimal totalDinheiro = BigDecimal.ZERO;
        public BigDecimal totalPix = BigDecimal.ZERO;
        public BigDecimal totalDebito = BigDecimal.ZERO;
        public BigDecimal totalCredito = BigDecimal.ZERO;
        public BigDecimal totalSuprimentos = BigDecimal.ZERO;
        public BigDecimal totalRetiradas = BigDecimal.ZERO;
        public BigDecimal totalGeral = BigDecimal.ZERO;
        public int quantidadeVendas = 0;

        public BigDecimal getTotalEsperadoCaixa(BigDecimal valorInicial) {
            return valorInicial.add(totalDinheiro).add(totalSuprimentos).subtract(totalRetiradas);
        }
    }
}