package br.com.adriano.service;

import br.com.adriano.db.ConexaoManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Serviço de relatórios gerenciais.
 * <p>
 * Fornece consultas agregadas para relatórios de vendas, produtos, operadores,
 * formas de pagamento, cancelamentos, histórico de caixas e resumo diário.
 * Inclui exportação para CSV e PDF (via reflexão iText).
 *
 * @since 2.0 - Fase 4
 */
public final class RelatorioService {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00", new DecimalFormatSymbols(Locale.US));

    private final ConexaoManager conexaoManager;

    public RelatorioService(ConexaoManager conexaoManager) {
        this.conexaoManager = Objects.requireNonNull(conexaoManager, "conexaoManager não pode ser null");
    }

    // ============= RELATÓRIOS =============

    /**
     * Relatório de vendas por período (agregado por dia).
     * <p>
     * Usa comparação de string ISO (yyyy-MM-dd) em vez de date() do SQLite
     * para evitar problemas de timezone/fuso horário.
     */
    public List<VendaPorPeriodo> vendasPorPeriodo(LocalDate inicio, LocalDate fim, Integer operadorId, String formaPagamento) throws SQLException {
        String sql = """
            SELECT
                substr(v.data, 1, 10) as data,
                COUNT(*) as qtd_vendas,
                SUM(v.total) as total,
                SUM(CASE WHEN v.forma_pagamento = 'DINHEIRO' THEN v.total ELSE 0 END) as total_dinheiro,
                SUM(CASE WHEN v.forma_pagamento = 'PIX' THEN v.total ELSE 0 END) as total_pix,
                SUM(CASE WHEN v.forma_pagamento = 'DEBITO' THEN v.total ELSE 0 END) as total_debito,
                SUM(CASE WHEN v.forma_pagamento = 'CREDITO' THEN v.total ELSE 0 END) as total_credito
            FROM vendas v
            WHERE substr(v.data, 1, 10) BETWEEN ? AND ?
              AND v.status = 'FINALIZADA'
            """ + (operadorId != null ? " AND v.usuario_id = ?" : "") +
                (formaPagamento != null ? " AND v.forma_pagamento = ?" : "") + """
            GROUP BY substr(v.data, 1, 10)
            ORDER BY substr(v.data, 1, 10)
            """;

        List<VendaPorPeriodo> resultados = new ArrayList<>();
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int idx = 1;
            stmt.setString(idx++, inicio.toString());
            stmt.setString(idx++, fim.toString());
            if (operadorId != null) stmt.setInt(idx++, operadorId);
            if (formaPagamento != null) stmt.setString(idx++, formaPagamento);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    resultados.add(new VendaPorPeriodo(
                            rs.getString("data"),
                            rs.getInt("qtd_vendas"),
                            getBigDecimal(rs, "total"),
                            getBigDecimal(rs, "total_dinheiro"),
                            getBigDecimal(rs, "total_pix"),
                            getBigDecimal(rs, "total_debito"),
                            getBigDecimal(rs, "total_credito")
                    ));
                }
            }
        }
        return resultados;
    }

    /**
     * Relatório de vendas detalhadas (cada venda individual).
     * <p>
     * Usa comparação de string ISO (yyyy-MM-dd) em vez de date() do SQLite
     * para evitar problemas de timezone/fuso horário.
     */
    public List<VendaDetalhada> vendasDetalhadas(LocalDate inicio, LocalDate fim, Integer operadorId, String formaPagamento) throws SQLException {
        String sql = """
            SELECT v.id, v.numero, v.data, v.forma_pagamento, v.subtotal, v.desconto, v.total,
                   u.nome as usuario_nome, v.status
            FROM vendas v
            JOIN usuarios u ON v.usuario_id = u.id
            WHERE substr(v.data, 1, 10) BETWEEN ? AND ?
              AND v.status = 'FINALIZADA'
            """ + (operadorId != null ? " AND v.usuario_id = ?" : "") +
                (formaPagamento != null ? " AND v.forma_pagamento = ?" : "") + """
            ORDER BY v.data DESC
            """;

        List<VendaDetalhada> resultados = new ArrayList<>();
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int idx = 1;
            stmt.setString(idx++, inicio.toString());
            stmt.setString(idx++, fim.toString());
            if (operadorId != null) stmt.setInt(idx++, operadorId);
            if (formaPagamento != null) stmt.setString(idx++, formaPagamento);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    resultados.add(new VendaDetalhada(
                            rs.getInt("id"),
                            rs.getString("numero"),
                            rs.getString("data"),
                            rs.getString("forma_pagamento"),
                            getBigDecimal(rs, "subtotal"),
                            getBigDecimal(rs, "desconto"),
                            getBigDecimal(rs, "total"),
                            rs.getString("usuario_nome"),
                            rs.getString("status")
                    ));
                }
            }
        }
        return resultados;
    }

    /**
     * Produtos mais vendidos no período.
     * <p>
     * Usa comparação de string ISO (yyyy-MM-dd) em vez de date() do SQLite
     * para evitar problemas de timezone/fuso horário.
     */
    public List<ProdutoVendido> produtosMaisVendidos(LocalDate inicio, LocalDate fim, int limite) throws SQLException {
        String sql = """
            SELECT p.id, p.codigo, p.nome, p.categoria,
                   SUM(iv.quantidade) as qtd_vendida,
                   SUM(iv.preco_total) as total_vendido,
                   CASE WHEN SUM(iv.quantidade) > 0 THEN SUM(iv.preco_total) / SUM(iv.quantidade) ELSE 0 END as preco_medio
            FROM itens_venda iv
            JOIN vendas v ON iv.venda_id = v.id
            JOIN produtos p ON iv.produto_id = p.id
            WHERE substr(v.data, 1, 10) BETWEEN ? AND ?
              AND v.status = 'FINALIZADA'
            GROUP BY p.id, p.codigo, p.nome, p.categoria
            ORDER BY qtd_vendida DESC
            LIMIT ?
            """;

        List<ProdutoVendido> resultados = new ArrayList<>();
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, inicio.toString());
            stmt.setString(2, fim.toString());
            stmt.setInt(3, limite);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    resultados.add(new ProdutoVendido(
                            rs.getInt("id"),
                            rs.getString("codigo"),
                            rs.getString("nome"),
                            rs.getString("categoria"),
                            rs.getInt("qtd_vendida"),
                            getBigDecimal(rs, "total_vendido"),
                            getBigDecimal(rs, "preco_medio")
                    ));
                }
            }
        }
        return resultados;
    }

    /**
     * Vendas por operador.
     * <p>
     * Usa comparação de string ISO (yyyy-MM-dd) em vez de date() do SQLite
     * para evitar problemas de timezone/fuso horário.
     */
    public List<VendasPorOperador> vendasPorOperador(LocalDate inicio, LocalDate fim) throws SQLException {
        String sql = """
            SELECT u.id, u.nome, u.perfil,
                   COUNT(*) as qtd_vendas,
                   SUM(v.total) as total_vendas,
                   SUM(CASE WHEN v.forma_pagamento = 'DINHEIRO' THEN v.total ELSE 0 END) as total_dinheiro,
                   SUM(CASE WHEN v.forma_pagamento = 'PIX' THEN v.total ELSE 0 END) as total_pix,
                   SUM(CASE WHEN v.forma_pagamento = 'DEBITO' THEN v.total ELSE 0 END) as total_debito,
                   SUM(CASE WHEN v.forma_pagamento = 'CREDITO' THEN v.total ELSE 0 END) as total_credito
            FROM vendas v
            JOIN usuarios u ON v.usuario_id = u.id
            WHERE substr(v.data, 1, 10) BETWEEN ? AND ?
              AND v.status = 'FINALIZADA'
            GROUP BY u.id, u.nome, u.perfil
            ORDER BY total_vendas DESC
            """;

        List<VendasPorOperador> resultados = new ArrayList<>();
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, inicio.toString());
            stmt.setString(2, fim.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    resultados.add(new VendasPorOperador(
                            rs.getInt("id"),
                            rs.getString("nome"),
                            rs.getString("perfil"),
                            rs.getInt("qtd_vendas"),
                            getBigDecimal(rs, "total_vendas"),
                            getBigDecimal(rs, "total_dinheiro"),
                            getBigDecimal(rs, "total_pix"),
                            getBigDecimal(rs, "total_debito"),
                            getBigDecimal(rs, "total_credito")
                    ));
                }
            }
        }
        return resultados;
    }

    /**
     * Vendas por forma de pagamento.
     * <p>
     * Usa comparação de string ISO (yyyy-MM-dd) em vez de date() do SQLite
     * para evitar problemas de timezone/fuso horário.
     */
    public List<VendasPorFormaPagamento> vendasPorFormaPagamento(LocalDate inicio, LocalDate fim) throws SQLException {
        String sql = """
            SELECT v.forma_pagamento,
                   COUNT(*) as qtd_vendas,
                   SUM(v.total) as total_vendas
            FROM vendas v
            WHERE substr(v.data, 1, 10) BETWEEN ? AND ?
              AND v.status = 'FINALIZADA'
            GROUP BY v.forma_pagamento
            ORDER BY total_vendas DESC
            """;

        List<VendasPorFormaPagamento> resultados = new ArrayList<>();
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, inicio.toString());
            stmt.setString(2, fim.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    resultados.add(new VendasPorFormaPagamento(
                            rs.getString("forma_pagamento"),
                            rs.getInt("qtd_vendas"),
                            getBigDecimal(rs, "total_vendas")
                    ));
                }
            }
        }
        return resultados;
    }

    /**
     * Cancelamentos no período.
     * <p>
     * Usa comparação de string ISO (yyyy-MM-dd) em vez de date() do SQLite
     * para evitar problemas de timezone/fuso horário.
     */
    public List<CancelamentoInfo> cancelamentos(LocalDate inicio, LocalDate fim) throws SQLException {
        String sql = """
            SELECT v.id, v.numero, v.data, v.forma_pagamento, v.total,
                   u.nome as usuario_nome,
                   v.cancelamento_motivo, v.cancelamento_data,
                   uc.nome as cancelador_nome
            FROM vendas v
            JOIN usuarios u ON v.usuario_id = u.id
            LEFT JOIN usuarios uc ON v.cancelamento_usuario_id = uc.id
            WHERE substr(v.data, 1, 10) BETWEEN ? AND ?
              AND v.status = 'CANCELADA'
            ORDER BY v.cancelamento_data DESC
            """;

        List<CancelamentoInfo> resultados = new ArrayList<>();
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, inicio.toString());
            stmt.setString(2, fim.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    resultados.add(new CancelamentoInfo(
                            rs.getInt("id"),
                            rs.getString("numero"),
                            rs.getString("data"),
                            rs.getString("forma_pagamento"),
                            getBigDecimal(rs, "total"),
                            rs.getString("usuario_nome"),
                            rs.getString("cancelamento_motivo"),
                            rs.getString("cancelamento_data"),
                            rs.getString("cancelador_nome")
                    ));
                }
            }
        }
        return resultados;
    }

    /**
     * Histórico de caixas (aberturas/fechamentos).
     * <p>
     * Usa comparação de string ISO (yyyy-MM-dd) em vez de date() do SQLite
     * para evitar problemas de timezone/fuso horário.
     */
    public List<CaixaHistorico> historicoCaixas(LocalDate inicio, LocalDate fim) throws SQLException {
        String sql = """
            SELECT c.id, c.data_abertura, c.data_fechamento, c.valor_inicial, c.status,
                   ua.nome as usuario_abertura_nome,
                   uf.nome as usuario_fechamento_nome
            FROM caixa c
            JOIN usuarios ua ON c.usuario_abertura_id = ua.id
            LEFT JOIN usuarios uf ON c.usuario_fechamento_id = uf.id
            WHERE substr(c.data_abertura, 1, 10) BETWEEN ? AND ?
            ORDER BY c.data_abertura DESC
            """;

        List<CaixaHistorico> resultados = new ArrayList<>();
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, inicio.toString());
            stmt.setString(2, fim.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    resultados.add(new CaixaHistorico(
                            rs.getInt("id"),
                            rs.getString("data_abertura"),
                            rs.getString("data_fechamento"),
                            getBigDecimal(rs, "valor_inicial"),
                            rs.getString("status"),
                            rs.getString("usuario_abertura_nome"),
                            rs.getString("usuario_fechamento_nome")
                    ));
                }
            }
        }
        return resultados;
    }

    /**
     * Calcula resumo de fechamento de caixa (valores esperados vs conferidos).
     */
    public TotaisCaixa calcularResumoFechamento(int caixaId) throws SQLException {
        // Buscar info do caixa
        CaixaService.CaixaInfo caixaInfo = getCaixaInfo(caixaId);
        if (caixaInfo == null) {
            throw new IllegalStateException("Caixa não encontrado: " + caixaId);
        }

        // Totais de vendas por forma de pagamento neste caixa
        String sql = """
            SELECT
                SUM(CASE WHEN v.forma_pagamento = 'DINHEIRO' THEN v.total ELSE 0 END) as total_dinheiro,
                SUM(CASE WHEN v.forma_pagamento = 'PIX' THEN v.total ELSE 0 END) as total_pix,
                SUM(CASE WHEN v.forma_pagamento = 'DEBITO' THEN v.total ELSE 0 END) as total_debito,
                SUM(CASE WHEN v.forma_pagamento = 'CREDITO' THEN v.total ELSE 0 END) as total_credito,
                COUNT(*) as qtd_vendas
            FROM vendas v
            WHERE v.caixa_id = ?
              AND v.status = 'FINALIZADA'
            """;

        BigDecimal totalDinheiro = BigDecimal.ZERO;
        BigDecimal totalPix = BigDecimal.ZERO;
        BigDecimal totalDebito = BigDecimal.ZERO;
        BigDecimal totalCredito = BigDecimal.ZERO;
        int qtdVendas = 0;

        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, caixaId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    totalDinheiro = getBigDecimal(rs, "total_dinheiro");
                    totalPix = getBigDecimal(rs, "total_pix");
                    totalDebito = getBigDecimal(rs, "total_debito");
                    totalCredito = getBigDecimal(rs, "total_credito");
                    qtdVendas = rs.getInt("qtd_vendas");
                }
            }
        }

        // Movimentos (suprimentos/retiradas)
        BigDecimal totalSuprimentos = BigDecimal.ZERO;
        BigDecimal totalRetiradas = BigDecimal.ZERO;
        String sqlMov = "SELECT tipo, SUM(valor) as total FROM suprimentos_retiradas WHERE caixa_id = ? GROUP BY tipo";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sqlMov)) {
            stmt.setInt(1, caixaId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String tipo = rs.getString("tipo");
                    BigDecimal valor = getBigDecimal(rs, "total");
                    if ("SUPRIMENTO".equals(tipo)) totalSuprimentos = valor;
                    else if ("RETIRADA".equals(tipo)) totalRetiradas = valor;
                }
            }
        }

        BigDecimal totalGeral = totalDinheiro.add(totalPix).add(totalDebito).add(totalCredito);
        BigDecimal valorEsperado = caixaInfo.valorInicial()
                .add(totalDinheiro)
                .add(totalSuprimentos)
                .subtract(totalRetiradas);

        return new TotaisCaixa(
                caixaId,
                caixaInfo.valorInicial(),
                totalDinheiro,
                totalPix,
                totalDebito,
                totalCredito,
                totalGeral,
                qtdVendas,
                totalSuprimentos,
                totalRetiradas,
                valorEsperado
        );
    }

    // ============= EXPORTAÇÃO CSV =============

    /**
     * Exporta vendas para CSV.
     */
    public String exportarVendasCSV(LocalDate inicio, LocalDate fim, Integer operadorId, String formaPagamento) throws SQLException {
        List<VendaDetalhada> dados = vendasDetalhadas(inicio, fim, operadorId, formaPagamento);
        StringBuilder sb = new StringBuilder();
        sb.append("ID,Número,Data,Hora,Forma Pagamento,Subtotal,Desconto,Total,Operador,Status\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        for (VendaDetalhada v : dados) {
            String[] parts = v.data().split(" ");
            String data = parts.length > 0 ? parts[0] : "";
            String hora = parts.length > 1 ? parts[1] : "";
            sb.append(v.id()).append(',')
              .append(v.numero()).append(',')
              .append(data).append(',')
              .append(hora).append(',')
              .append(v.formaPagamento()).append(',')
              .append(formatDecimal(v.subtotal())).append(',')
              .append(formatDecimal(v.desconto())).append(',')
              .append(formatDecimal(v.total())).append(',')
              .append(v.usuarioNome()).append(',')
              .append(v.status()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Exporta produtos mais vendidos para CSV.
     */
    public String exportarProdutosMaisVendidosCSV(LocalDate inicio, LocalDate fim, int limite) throws SQLException {
        List<ProdutoVendido> dados = produtosMaisVendidos(inicio, fim, limite);
        StringBuilder sb = new StringBuilder();
        sb.append("ID,Código,Nome,Categoria,Qtd Vendida,Total Vendido,Preço Médio\n");
        for (ProdutoVendido p : dados) {
            sb.append(p.id()).append(',')
              .append(p.codigo()).append(',')
              .append(escapeCsv(p.nome())).append(',')
              .append(escapeCsv(p.categoria())).append(',')
              .append(p.quantidadeVendida()).append(',')
              .append(formatDecimal(p.totalVendido())).append(',')
              .append(formatDecimal(p.precoMedio())).append('\n');
        }
        return sb.toString();
    }

    /**
     * Exporta vendas por operador para CSV.
     */
    public String exportarVendasPorOperadorCSV(LocalDate inicio, LocalDate fim) throws SQLException {
        List<VendasPorOperador> dados = vendasPorOperador(inicio, fim);
        StringBuilder sb = new StringBuilder();
        sb.append("Operador,Perfil,Qtd Vendas,Total,Dinheiro,PIX,Débito,Crédito\n");
        for (VendasPorOperador o : dados) {
            sb.append(escapeCsv(o.usuarioNome())).append(',')
              .append(o.perfil()).append(',')
              .append(o.qtdVendas()).append(',')
              .append(formatDecimal(o.totalVendas())).append(',')
              .append(formatDecimal(o.totalDinheiro())).append(',')
              .append(formatDecimal(o.totalPix())).append(',')
              .append(formatDecimal(o.totalDebito())).append(',')
              .append(formatDecimal(o.totalCredito())).append('\n');
        }
        return sb.toString();
    }

    /**
     * Exporta histórico de caixas para CSV.
     */
    public String exportarCaixaHistoricoCSV(LocalDate inicio, LocalDate fim) throws SQLException {
        List<CaixaHistorico> dados = historicoCaixas(inicio, fim);
        StringBuilder sb = new StringBuilder();
        sb.append("ID,Abertura,Fechamento,Valor Inicial,Status,Abertura por,Fechamento por\n");
        for (CaixaHistorico c : dados) {
            sb.append(c.id()).append(',')
              .append(c.dataAbertura()).append(',')
              .append(c.dataFechamento() != null ? c.dataFechamento() : "").append(',')
              .append(formatDecimal(c.valorInicial())).append(',')
              .append(c.status()).append(',')
              .append(escapeCsv(c.usuarioAberturaNome())).append(',')
              .append(c.usuarioFechamentoNome() != null ? escapeCsv(c.usuarioFechamentoNome()) : "").append('\n');
        }
        return sb.toString();
    }

    // ============= EXPORTAÇÃO PDF (via iText reflexão) =============

    /**
     * Exporta relatório para PDF usando iText se disponível.
     * Se iText não estiver no classpath, lança exceção informativa.
     */
    public void exportarPDF(String titulo, String[] cabecalhos, List<?> dados, File arquivo, java.util.function.Function<Object, String[]> mapeador) throws Exception {
        Class<?> pdfWriterClass = Class.forName("com.itextpdf.kernel.pdf.PdfWriter");
        Class<?> pdfDocumentClass = Class.forName("com.itextpdf.kernel.pdf.PdfDocument");
        Class<?> documentClass = Class.forName("com.itextpdf.layout.Document");
        Class<?> tableClass = Class.forName("com.itextpdf.layout.element.Table");
        Class<?> cellClass = Class.forName("com.itextpdf.layout.element.Cell");
        Class<?> paragraphClass = Class.forName("com.itextpdf.layout.element.Paragraph");
        Class<?> pageSizeClass = Class.forName("com.itextpdf.kernel.geom.PageSize");
        Class<?> unitValueClass = Class.forName("com.itextpdf.layout.property.UnitValue");

        Object writer = pdfWriterClass.getConstructor(String.class).newInstance(arquivo.getAbsolutePath());
        Object pdfDoc = pdfDocumentClass.getConstructor(pdfWriterClass).newInstance(writer);
        Object pageSize = pageSizeClass.getField("A4").get(null);
        Object document = documentClass.getConstructor(pdfDocumentClass).newInstance(pdfDoc);
        // document.setMargins(36, 36, 36, 36);

        Object table = tableClass.getConstructor(unitValueClass).newInstance(
                unitValueClass.getMethod("createPercentArray", int[].class).invoke(null, (Object) new int[]{1}) // placeholder
        );
        // Usar Table(float[]) constructor
        float[] widths = new float[cabecalhos.length];
        Arrays.fill(widths, 100f / cabecalhos.length);
        table = tableClass.getConstructor(float[].class).newInstance((Object) widths);

        // Cabeçalho
        for (String h : cabecalhos) {
            Object cell = cellClass.getConstructor().newInstance();
            Object p = paragraphClass.getConstructor(String.class).newInstance(h);
            cellClass.getMethod("add", paragraphClass).invoke(cell, p);
            tableClass.getMethod("addHeaderCell", cellClass).invoke(table, cell);
        }

        // Dados
        for (Object item : dados) {
            String[] valores = mapeador.apply(item);
            for (String v : valores) {
                Object cell = cellClass.getConstructor().newInstance();
                Object p = paragraphClass.getConstructor(String.class).newInstance(v);
                cellClass.getMethod("add", paragraphClass).invoke(cell, p);
                tableClass.getMethod("addCell", cellClass).invoke(table, cell);
            }
        }

        documentClass.getMethod("add", tableClass).invoke(document, table);
        documentClass.getMethod("close").invoke(document);
    }

    // ============= HELPERS =============

    private CaixaService.CaixaInfo getCaixaInfo(int caixaId) throws SQLException {
        String sql = "SELECT id, data_abertura, data_fechamento, valor_inicial, status, usuario_abertura_id, usuario_fechamento_id FROM caixa WHERE id = ?";
        try (Connection conn = conexaoManager.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, caixaId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new CaixaService.CaixaInfo(
                            rs.getInt("id"),
                            rs.getString("data_abertura"),
                            rs.getString("data_fechamento"),
                            getBigDecimal(rs, "valor_inicial"),
                            rs.getString("status"),
                            rs.getInt("usuario_abertura_id"),
                            rs.getInt("usuario_fechamento_id")
                    );
                }
            }
        }
        return null;
    }

    private BigDecimal getBigDecimal(ResultSet rs, String col) throws SQLException {
        BigDecimal val = rs.getBigDecimal(col);
        return val != null ? val : BigDecimal.ZERO;
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String formatDecimal(BigDecimal value) {
        return value != null ? DECIMAL_FORMAT.format(value) : "0.00";
    }

    // ============= RECORDS (DTOs) =============

    public record VendaPorPeriodo(
            String data,
            int qtdVendas,
            BigDecimal total,
            BigDecimal totalDinheiro,
            BigDecimal totalPix,
            BigDecimal totalDebito,
            BigDecimal totalCredito
    ) {}

    public record VendaDetalhada(
            int id,
            String numero,
            String data,
            String formaPagamento,
            BigDecimal subtotal,
            BigDecimal desconto,
            BigDecimal total,
            String usuarioNome,
            String status
    ) {}

    public record ProdutoVendido(
            int id,
            String codigo,
            String nome,
            String categoria,
            int quantidadeVendida,
            BigDecimal totalVendido,
            BigDecimal precoMedio
    ) {}

    public record VendasPorOperador(
            int usuarioId,
            String usuarioNome,
            String perfil,
            int qtdVendas,
            BigDecimal totalVendas,
            BigDecimal totalDinheiro,
            BigDecimal totalPix,
            BigDecimal totalDebito,
            BigDecimal totalCredito
    ) {}

    public record VendasPorFormaPagamento(
            String formaPagamento,
            int qtdVendas,
            BigDecimal totalVendas
    ) {}

    public record CancelamentoInfo(
            int id,
            String numero,
            String data,
            String formaPagamento,
            BigDecimal total,
            String usuarioNome,
            String cancelamentoMotivo,
            String cancelamentoData,
            String canceladorNome
    ) {}

    public record CaixaHistorico(
            int id,
            String dataAbertura,
            String dataFechamento,
            BigDecimal valorInicial,
            String status,
            String usuarioAberturaNome,
            String usuarioFechamentoNome
    ) {}

    public record TotaisCaixa(
            int caixaId,
            BigDecimal valorInicial,
            BigDecimal totalDinheiro,
            BigDecimal totalPix,
            BigDecimal totalDebito,
            BigDecimal totalCredito,
            BigDecimal totalGeral,
            int quantidadeVendas,
            BigDecimal totalSuprimentos,
            BigDecimal totalRetiradas,
            BigDecimal valorEsperado
    ) {
        public BigDecimal getTotalEsperadoCaixa(BigDecimal valorInicial) {
            return valorInicial.add(totalDinheiro).add(totalSuprimentos).subtract(totalRetiradas);
        }
    }
}