package br.com.adriano.ui.relatorios;

import br.com.adriano.db.ConexaoManager;
import br.com.adriano.service.RelatorioService;
import br.com.adriano.ui.componentes.Componentes;
import br.com.adriano.ui.componentes.Icone;
import br.com.adriano.ui.tema.GerenciadorTema;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Painel de relatórios gerenciais.
 * <p>
 * Relatórios disponíveis:
 * - Vendas por período
 * - Produtos mais vendidos
 * - Vendas por operador
 * - Vendas por forma de pagamento
 * - Cancelamentos
 * - Histórico de caixas
 * - Resumo diário (fechamento de caixa)
 * <p>
 * Exportação: CSV e PDF (via iText se disponível, senão apenas CSV).
 *
 * @since 2.0 - Fase 4
 */
public final class PainelRelatorios extends JPanel {

    private final RelatorioService relatorioService;
    private final ConexaoManager conexaoManager;
    private final Consumer<String> aoStatus;

    // Filtros
    private JFormattedTextField txtDataInicio;
    private JFormattedTextField txtDataFim;
    private JComboBox<String> comboOperador;
    private JComboBox<String> comboFormaPagamento;
    private JComboBox<String> comboRelatorio;

    // Tabela de resultados
    private DefaultTableModel modeloTabela;
    private JTable tabela;
    private TableRowSorter<DefaultTableModel> sorter;

    // Tipo de relatório atual
    private TipoRelatorio tipoAtual = TipoRelatorio.VENDAS_PERIODO;
    private JLabel lblTotal;

    public PainelRelatorios(RelatorioService relatorioService, ConexaoManager conexaoManager, Consumer<String> aoStatus) {
        this.relatorioService = relatorioService;
        this.conexaoManager = conexaoManager;
        this.aoStatus = aoStatus;

        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setOpaque(false);
        GerenciadorTema.aplicar(this);

        add(montarTopo(), BorderLayout.NORTH);
        add(montarCentro(), BorderLayout.CENTER);
        add(montarRodape(), BorderLayout.SOUTH);

        // Carregar operadores e definir datas padrão
        carregarOperadores();
        definirDatasPadrao();
        carregarRelatorio();
    }

    private JComponent montarTopo() {
        JPanel topo = Componentes.painel(new BorderLayout(12, 0));
        GerenciadorTema.estilo(topo, "fundo", null);

        // Título
        JLabel titulo = Componentes.texto("Relatórios Gerenciais", 18, true);
        titulo.setIcon(new Icone(Icone.Tipo.RELATORIO, 24));
        titulo.setIconTextGap(10);
        topo.add(titulo, BorderLayout.WEST);

        // Filtros
        JPanel filtros = Componentes.painel(new BorderLayout(8, 8));
        GerenciadorTema.estilo(filtros, "fundo", null);
        filtros.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(GerenciadorTema.cor("borda")),
                " Filtros ",
                0, 0, new Font("Segoe UI", Font.BOLD, 13), GerenciadorTema.cor("texto")));

        // Linha 1: Tipo de relatório + Período
        JPanel linha1 = Componentes.painel(new BorderLayout(8, 8));
        GerenciadorTema.estilo(linha1, "fundo", null);

        // Tipo de relatório
        JPanel tipoPanel = Componentes.painel(new BorderLayout(8, 0));
        GerenciadorTema.estilo(tipoPanel, "fundo", null);
        JLabel lblTipo = Componentes.texto("Relatório:", 13, true);
        lblTipo.setIcon(new Icone(Icone.Tipo.RELATORIO, 18));
        lblTipo.setIconTextGap(8);
        tipoPanel.add(lblTipo, BorderLayout.WEST);

        comboRelatorio = new JComboBox<>(new String[]{
                "Vendas por Período",
                "Vendas Detalhadas",
                "Produtos Mais Vendidos",
                "Vendas por Operador",
                "Vendas por Forma de Pagamento",
                "Cancelamentos",
                "Histórico de Caixas",
                "Resumo Diário (Fechamento)"
        });
        comboRelatorio.setPreferredSize(new Dimension(300, 38));
        comboRelatorio.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        GerenciadorTema.estilo(comboRelatorio, "inputFundo", "inputTexto");
        comboRelatorio.addActionListener(e -> {
            tipoAtual = TipoRelatorio.values()[comboRelatorio.getSelectedIndex()];
            atualizarVisibilidadeFiltros();
            carregarRelatorio();
        });
        tipoPanel.add(comboRelatorio, BorderLayout.CENTER);
        linha1.add(tipoPanel, BorderLayout.WEST);

        // Período
        JPanel periodoPanel = Componentes.painel(new BorderLayout(8, 0));
        GerenciadorTema.estilo(periodoPanel, "fundo", null);
        JLabel lblPeriodo = Componentes.texto("Período:", 13, true);
        lblPeriodo.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        periodoPanel.add(lblPeriodo, BorderLayout.WEST);

        JPanel datasPanel = Componentes.painel(new GridLayout(1, 4, 8, 0));
        GerenciadorTema.estilo(datasPanel, "fundo", null);
        txtDataInicio = new JFormattedTextField(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        txtDataInicio.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        GerenciadorTema.estilo(txtDataInicio, "inputFundo", "inputTexto");
        txtDataInicio.setPreferredSize(new Dimension(120, 38));
        txtDataInicio.setValue(LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(30));
        datasPanel.add(txtDataInicio);

        JLabel lblAte = Componentes.texto("até", 13, false);
        lblAte.setHorizontalAlignment(SwingConstants.CENTER);
        datasPanel.add(lblAte);

        txtDataFim = new JFormattedTextField(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        txtDataFim.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        GerenciadorTema.estilo(txtDataFim, "inputFundo", "inputTexto");
        txtDataFim.setPreferredSize(new Dimension(120, 38));
        txtDataFim.setValue(LocalDate.now(ZoneId.of("America/Sao_Paulo")));
        datasPanel.add(txtDataFim);

        JButton btnHoje = Componentes.botao("Hoje", Icone.Tipo.CHECK, () -> {
            LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
            txtDataInicio.setValue(hoje);
            txtDataFim.setValue(hoje);
            carregarRelatorio();
        });
        GerenciadorTema.estilo(btnHoje, "painelElevado", "texto");
        datasPanel.add(btnHoje);

        periodoPanel.add(datasPanel, BorderLayout.CENTER);
        linha1.add(periodoPanel, BorderLayout.CENTER);

        filtros.add(linha1, BorderLayout.NORTH);

        // Linha 2: Operador + Forma Pagamento
        JPanel linha2 = Componentes.painel(new BorderLayout(8, 0));
        GerenciadorTema.estilo(linha2, "fundo", null);

        JPanel operadorPanel = Componentes.painel(new BorderLayout(8, 0));
        GerenciadorTema.estilo(operadorPanel, "fundo", null);
        JLabel lblOperador = Componentes.texto("Operador:", 13, true);
        lblOperador.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        operadorPanel.add(lblOperador, BorderLayout.WEST);

        comboOperador = new JComboBox<>();
        comboOperador.setPreferredSize(new Dimension(250, 38));
        comboOperador.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        GerenciadorTema.estilo(comboOperador, "inputFundo", "inputTexto");
        comboOperador.addActionListener(e -> carregarRelatorio());
        operadorPanel.add(comboOperador, BorderLayout.CENTER);
        linha2.add(operadorPanel, BorderLayout.WEST);

        JPanel formaPanel = Componentes.painel(new BorderLayout(8, 0));
        GerenciadorTema.estilo(formaPanel, "fundo", null);
        JLabel lblForma = Componentes.texto("Forma Pagamento:", 13, true);
        lblForma.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        formaPanel.add(lblForma, BorderLayout.WEST);

        comboFormaPagamento = new JComboBox<>(new String[]{"Todas", "DINHEIRO", "PIX", "DEBITO", "CREDITO"});
        comboFormaPagamento.setPreferredSize(new Dimension(180, 38));
        comboFormaPagamento.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        GerenciadorTema.estilo(comboFormaPagamento, "inputFundo", "inputTexto");
        comboFormaPagamento.addActionListener(e -> carregarRelatorio());
        formaPanel.add(comboFormaPagamento, BorderLayout.CENTER);
        linha2.add(formaPanel, BorderLayout.CENTER);

        filtros.add(linha2, BorderLayout.CENTER);

        topo.add(filtros, BorderLayout.CENTER);
        return topo;
    }

    private JComponent montarCentro() {
        JPanel centro = Componentes.painel(new BorderLayout(0, 8));
        GerenciadorTema.estilo(centro, "fundo", null);

        modeloTabela = new DefaultTableModel();
        tabela = new JTable(modeloTabela);
        tabela.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabela.setRowHeight(28);
        tabela.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabela.getTableHeader().setBackground(GerenciadorTema.cor("headerFundo"));
        tabela.getTableHeader().setForeground(GerenciadorTema.cor("headerTexto"));
        tabela.setOpaque(false);
        tabela.setFillsViewportHeight(true);

        sorter = new TableRowSorter<>(modeloTabela);
        tabela.setRowSorter(sorter);

        JScrollPane scroll = new JScrollPane(tabela, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, GerenciadorTema.cor("borda")),
                BorderFactory.createEmptyBorder(8, 0, 8, 0)));
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        GerenciadorTema.estilo(scroll.getViewport(), "fundo", null);

        centro.add(scroll, BorderLayout.CENTER);
        return centro;
    }

    private JComponent montarRodape() {
        JPanel rodape = Componentes.painel(new BorderLayout());
        GerenciadorTema.estilo(rodape, "fundo", null);
        lblTotal = Componentes.texto("Total de registros: 0", 13, false);
        lblTotal.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        lblTotal.setName("lblTotal");
        rodape.add(lblTotal, BorderLayout.WEST);

        // Botões de exportação
        JPanel botoes = Componentes.painel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        GerenciadorTema.estilo(botoes, "fundo", null);
        JButton btnCSV = Componentes.botao("Exportar CSV", Icone.Tipo.DINHEIRO, () -> exportarCSV());
        btnCSV.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JButton btnPDF = Componentes.botao("Exportar PDF", Icone.Tipo.RELATORIO, () -> exportarPDF());
        btnPDF.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JButton btnImprimir = Componentes.botao("Imprimir", Icone.Tipo.CONFIGURACAO, () -> imprimir());
        btnImprimir.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        botoes.add(btnCSV);
        botoes.add(btnPDF);
        botoes.add(btnImprimir);
        rodape.add(botoes, BorderLayout.EAST);

        return rodape;
    }

    private void definirDatasPadrao() {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        txtDataInicio.setValue(hoje.minusDays(30));
        txtDataFim.setValue(hoje);
    }

    private void carregarOperadores() {
        try {
            // Buscar operadores do banco
            String sql = "SELECT id, nome FROM usuarios WHERE ativo = 1 ORDER BY nome";
            try (var conn = conexaoManager.conectar();
                 var stmt = conn.prepareStatement(sql);
                 var rs = stmt.executeQuery()) {
                comboOperador.removeAllItems();
                comboOperador.addItem("Todos");
                while (rs.next()) {
                    comboOperador.addItem(rs.getInt("id") + " - " + rs.getString("nome"));
                }
            }
        } catch (Exception ex) {
            aoStatus.accept("Erro ao carregar operadores: " + ex.getMessage());
        }
    }

    private void atualizarVisibilidadeFiltros() {
        boolean mostraOperador = tipoAtual != TipoRelatorio.PRODUTOS_MAIS_VENDIDOS &&
                tipoAtual != TipoRelatorio.VENDAS_FORMA_PAGAMENTO &&
                tipoAtual != TipoRelatorio.HISTORICO_CAIXAS &&
                tipoAtual != TipoRelatorio.RESUMO_DIARIO;
        boolean mostraFormaPagamento = tipoAtual == TipoRelatorio.VENDAS_PERIODO ||
                tipoAtual == TipoRelatorio.VENDAS_DETALHADAS ||
                tipoAtual == TipoRelatorio.CANCELAMENTOS;

        comboOperador.setEnabled(mostraOperador);
        comboFormaPagamento.setEnabled(mostraFormaPagamento);
    }

    private void carregarRelatorio() {
        try {
            LocalDate inicio = parseData(txtDataInicio.getText());
            LocalDate fim = parseData(txtDataFim.getText());
            if (inicio == null || fim == null) {
                JOptionPane.showMessageDialog(this, "Datas inválidas. Use formato dd/MM/yyyy.", "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (inicio.isAfter(fim)) {
                JOptionPane.showMessageDialog(this, "Data inicial não pode ser posterior à final.", "Erro", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Integer operadorId = null;
            String operadorSel = (String) comboOperador.getSelectedItem();
            if (operadorSel != null && !"Todos".equals(operadorSel)) {
                operadorId = Integer.parseInt(operadorSel.split(" - ")[0]);
            }

            String formaPagamento = (String) comboFormaPagamento.getSelectedItem();
            if ("Todas".equals(formaPagamento)) formaPagamento = null;

            switch (tipoAtual) {
                case VENDAS_PERIODO -> carregarVendasPeriodo(inicio, fim, operadorId, formaPagamento);
                case VENDAS_DETALHADAS -> carregarVendasDetalhadas(inicio, fim, operadorId, formaPagamento);
                case PRODUTOS_MAIS_VENDIDOS -> carregarProdutosMaisVendidos(inicio, fim);
                case VENDAS_POR_OPERADOR -> carregarVendasPorOperador(inicio, fim);
                case VENDAS_FORMA_PAGAMENTO -> carregarVendasPorFormaPagamento(inicio, fim);
                case CANCELAMENTOS -> carregarCancelamentos(inicio, fim);
                case HISTORICO_CAIXAS -> carregarHistoricoCaixas(inicio, fim);
                case RESUMO_DIARIO -> carregarResumoDiario(inicio, fim);
            }

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao carregar relatório: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private LocalDate parseData(String texto) {
        try {
            return LocalDate.parse(texto, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return null;
        }
    }

    private void carregarVendasPeriodo(LocalDate inicio, LocalDate fim, Integer operadorId, String formaPagamento) throws Exception {
        List<RelatorioService.VendaPorPeriodo> dados = relatorioService.vendasPorPeriodo(inicio, fim, operadorId, formaPagamento);
        String[] cols = {"Data", "Qtd Vendas", "Total", "Dinheiro", "PIX", "Débito", "Crédito"};
        modeloTabela.setColumnIdentifiers(cols);
        modeloTabela.setRowCount(0);
        BigDecimal totalGeral = BigDecimal.ZERO;
        for (RelatorioService.VendaPorPeriodo v : dados) {
            modeloTabela.addRow(new Object[]{
                    v.data(), v.qtdVendas(),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(v.total()),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(v.totalDinheiro()),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(v.totalPix()),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(v.totalDebito()),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(v.totalCredito())
            });
            totalGeral = totalGeral.add(v.total());
        }
        atualizarTotal(dados.size(), "Total Período: " + br.com.adriano.ui.pdv.Dinheiro.formatar(totalGeral));
    }

    private void carregarVendasDetalhadas(LocalDate inicio, LocalDate fim, Integer operadorId, String formaPagamento) throws Exception {
        List<RelatorioService.VendaDetalhada> dados = relatorioService.vendasDetalhadas(inicio, fim, operadorId, formaPagamento);
        String[] cols = {"ID", "Número", "Data", "Hora", "Pagamento", "Subtotal", "Desconto", "Total", "Operador", "Status"};
        modeloTabela.setColumnIdentifiers(cols);
        modeloTabela.setRowCount(0);
        BigDecimal totalGeral = BigDecimal.ZERO;
        for (RelatorioService.VendaDetalhada v : dados) {
            String[] parts = v.data().split(" ");
            String data = parts.length > 0 ? parts[0] : "";
            String hora = parts.length > 1 ? parts[1] : "";
            modeloTabela.addRow(new Object[]{
                    v.id(), v.numero(), data, hora, v.formaPagamento(),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(v.subtotal()),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(v.desconto()),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(v.total()),
                    v.usuarioNome(), v.status()
            });
            totalGeral = totalGeral.add(v.total());
        }
        atualizarTotal(dados.size(), "Total: " + br.com.adriano.ui.pdv.Dinheiro.formatar(totalGeral));
    }

    private void carregarProdutosMaisVendidos(LocalDate inicio, LocalDate fim) throws Exception {
        List<RelatorioService.ProdutoVendido> dados = relatorioService.produtosMaisVendidos(inicio, fim, 50);
        String[] cols = {"ID", "Código", "Nome", "Categoria", "Qtd Vendida", "Total Vendido", "Preço Médio"};
        modeloTabela.setColumnIdentifiers(cols);
        modeloTabela.setRowCount(0);
        for (RelatorioService.ProdutoVendido p : dados) {
            modeloTabela.addRow(new Object[]{
                    p.id(), p.codigo(), p.nome(), p.categoria(),
                    p.quantidadeVendida(),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(p.totalVendido()),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(p.precoMedio())
            });
        }
        atualizarTotal(dados.size(), "");
    }

    private void carregarVendasPorOperador(LocalDate inicio, LocalDate fim) throws Exception {
        List<RelatorioService.VendasPorOperador> dados = relatorioService.vendasPorOperador(inicio, fim);
        String[] cols = {"Operador", "Perfil", "Qtd Vendas", "Total", "Dinheiro", "PIX", "Débito", "Crédito"};
        modeloTabela.setColumnIdentifiers(cols);
        modeloTabela.setRowCount(0);
        BigDecimal totalGeral = BigDecimal.ZERO;
        for (RelatorioService.VendasPorOperador o : dados) {
            modeloTabela.addRow(new Object[]{
                    o.usuarioNome(), o.perfil(), o.qtdVendas(),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(o.totalVendas()),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(o.totalDinheiro()),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(o.totalPix()),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(o.totalDebito()),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(o.totalCredito())
            });
            totalGeral = totalGeral.add(o.totalVendas());
        }
        atualizarTotal(dados.size(), "Total Geral: " + br.com.adriano.ui.pdv.Dinheiro.formatar(totalGeral));
    }

    private void carregarVendasPorFormaPagamento(LocalDate inicio, LocalDate fim) throws Exception {
        List<RelatorioService.VendasPorFormaPagamento> dados = relatorioService.vendasPorFormaPagamento(inicio, fim);
        String[] cols = {"Forma Pagamento", "Qtd Vendas", "Total"};
        modeloTabela.setColumnIdentifiers(cols);
        modeloTabela.setRowCount(0);
        BigDecimal totalGeral = BigDecimal.ZERO;
        for (RelatorioService.VendasPorFormaPagamento f : dados) {
            modeloTabela.addRow(new Object[]{
                    f.formaPagamento(), f.qtdVendas(),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(f.totalVendas())
            });
            totalGeral = totalGeral.add(f.totalVendas());
        }
        atualizarTotal(dados.size(), "Total: " + br.com.adriano.ui.pdv.Dinheiro.formatar(totalGeral));
    }

    private void carregarCancelamentos(LocalDate inicio, LocalDate fim) throws Exception {
        List<RelatorioService.CancelamentoInfo> dados = relatorioService.cancelamentos(inicio, fim);
        String[] cols = {"ID", "Número", "Data", "Pagamento", "Total", "Operador", "Motivo", "Data Cancel.", "Cancelador"};
        modeloTabela.setColumnIdentifiers(cols);
        modeloTabela.setRowCount(0);
        for (RelatorioService.CancelamentoInfo c : dados) {
            modeloTabela.addRow(new Object[]{
                    c.id(), c.numero(), c.data(), c.formaPagamento(),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(c.total()),
                    c.usuarioNome(), c.cancelamentoMotivo(), c.cancelamentoData(), c.canceladorNome()
            });
        }
        atualizarTotal(dados.size(), "");
    }

    private void carregarHistoricoCaixas(LocalDate inicio, LocalDate fim) throws Exception {
        List<RelatorioService.CaixaHistorico> dados = relatorioService.historicoCaixas(inicio, fim);
        String[] cols = {"ID", "Abertura", "Fechamento", "Valor Inicial", "Status", "Abertura por", "Fechamento por"};
        modeloTabela.setColumnIdentifiers(cols);
        modeloTabela.setRowCount(0);
        for (RelatorioService.CaixaHistorico c : dados) {
            modeloTabela.addRow(new Object[]{
                    c.id(), c.dataAbertura(), c.dataFechamento() != null ? c.dataFechamento() : "—",
                    br.com.adriano.ui.pdv.Dinheiro.formatar(c.valorInicial()),
                    c.status(), c.usuarioAberturaNome(),
                    c.usuarioFechamentoNome() != null ? c.usuarioFechamentoNome() : "—"
            });
        }
        atualizarTotal(dados.size(), "");
    }

    private void carregarResumoDiario(LocalDate inicio, LocalDate fim) throws Exception {
        // Para resumo diário, mostramos um relatório agregado por dia
        List<RelatorioService.VendaPorPeriodo> dados = relatorioService.vendasPorPeriodo(inicio, fim, null, null);
        String[] cols = {"Data", "Qtd Vendas", "Total", "Dinheiro", "PIX", "Débito", "Crédito"};
        modeloTabela.setColumnIdentifiers(cols);
        modeloTabela.setRowCount(0);
        BigDecimal totalGeral = BigDecimal.ZERO;
        for (RelatorioService.VendaPorPeriodo v : dados) {
            modeloTabela.addRow(new Object[]{
                    v.data(), v.qtdVendas(),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(v.total()),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(v.totalDinheiro()),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(v.totalPix()),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(v.totalDebito()),
                    br.com.adriano.ui.pdv.Dinheiro.formatar(v.totalCredito())
            });
            totalGeral = totalGeral.add(v.total());
        }
        atualizarTotal(dados.size(), "Total Período: " + br.com.adriano.ui.pdv.Dinheiro.formatar(totalGeral));
    }

    private void atualizarTotal(int count, String extra) {
        if (lblTotal != null) {
            lblTotal.setText("Total de registros: " + count + (extra.isEmpty() ? "" : "  |  " + extra));
        }
    }

    // ============= EXPORTAÇÃO =============

    private void exportarCSV() {
        try {
            LocalDate inicio = parseData(txtDataInicio.getText());
            LocalDate fim = parseData(txtDataFim.getText());
            Integer operadorId = null;
            String operadorSel = (String) comboOperador.getSelectedItem();
            if (operadorSel != null && !"Todos".equals(operadorSel)) {
                operadorId = Integer.parseInt(operadorSel.split(" - ")[0]);
            }
            String formaPagamento = (String) comboFormaPagamento.getSelectedItem();
            if ("Todas".equals(formaPagamento)) formaPagamento = null;

            String csv;
            switch (tipoAtual) {
                case VENDAS_PERIODO, VENDAS_DETALHADAS ->
                        csv = relatorioService.exportarVendasCSV(inicio, fim, operadorId, formaPagamento);
                case PRODUTOS_MAIS_VENDIDOS ->
                        csv = relatorioService.exportarProdutosMaisVendidosCSV(inicio, fim, 50);
                case VENDAS_POR_OPERADOR ->
                        csv = relatorioService.exportarVendasPorOperadorCSV(inicio, fim);
                case HISTORICO_CAIXAS ->
                        csv = relatorioService.exportarCaixaHistoricoCSV(inicio, fim);
                default -> {
                    JOptionPane.showMessageDialog(this, "Exportação CSV não disponível para este relatório.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("relatorio_" + tipoAtual.name().toLowerCase() + "_" + LocalDate.now() + ".csv"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File arquivo = chooser.getSelectedFile();
                try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(arquivo), StandardCharsets.UTF_8)) {
                    writer.write(csv);
                }
                aoStatus.accept("CSV exportado: " + arquivo.getName());
                JOptionPane.showMessageDialog(this, "Exportação concluída com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao exportar CSV: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void exportarPDF() {
        try {
            LocalDate inicio = parseData(txtDataInicio.getText());
            LocalDate fim = parseData(txtDataFim.getText());
            Integer operadorId = null;
            String operadorSel = (String) comboOperador.getSelectedItem();
            if (operadorSel != null && !"Todos".equals(operadorSel)) {
                operadorId = Integer.parseInt(operadorSel.split(" - ")[0]);
            }
            String formaPagamento = (String) comboFormaPagamento.getSelectedItem();
            if ("Todas".equals(formaPagamento)) formaPagamento = null;

            String[] cabecalhos;
            List<?> dados;
            java.util.function.Function<Object, String[]> mapeador;

            switch (tipoAtual) {
                case VENDAS_PERIODO -> {
                    List<RelatorioService.VendaPorPeriodo> v = relatorioService.vendasPorPeriodo(inicio, fim, operadorId, formaPagamento);
                    cabecalhos = new String[]{"Data", "Qtd", "Total", "Dinheiro", "PIX", "Débito", "Crédito"};
                    dados = new ArrayList<>(v);
                    mapeador = obj -> {
                        RelatorioService.VendaPorPeriodo r = (RelatorioService.VendaPorPeriodo) obj;
                        return new String[]{r.data(), String.valueOf(r.qtdVendas()),
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.total()),
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.totalDinheiro()),
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.totalPix()),
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.totalDebito()),
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.totalCredito())};
                    };
                }
                case VENDAS_DETALHADAS -> {
                    List<RelatorioService.VendaDetalhada> v = relatorioService.vendasDetalhadas(inicio, fim, operadorId, formaPagamento);
                    cabecalhos = new String[]{"ID", "Número", "Data", "Pagamento", "Subtotal", "Desconto", "Total", "Operador", "Status"};
                    dados = new ArrayList<>(v);
                    mapeador = obj -> {
                        RelatorioService.VendaDetalhada r = (RelatorioService.VendaDetalhada) obj;
                        String[] parts = r.data().split(" ");
                        return new String[]{String.valueOf(r.id()), r.numero(), parts[0], r.formaPagamento(),
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.subtotal()),
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.desconto()),
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.total()),
                                r.usuarioNome(), r.status()};
                    };
                }
                case PRODUTOS_MAIS_VENDIDOS -> {
                    List<RelatorioService.ProdutoVendido> v = relatorioService.produtosMaisVendidos(inicio, fim, 50);
                    cabecalhos = new String[]{"ID", "Código", "Nome", "Categoria", "Qtd", "Total", "Preço Médio"};
                    dados = new ArrayList<>(v);
                    mapeador = obj -> {
                        RelatorioService.ProdutoVendido r = (RelatorioService.ProdutoVendido) obj;
                        return new String[]{String.valueOf(r.id()), r.codigo(), r.nome(), r.categoria(),
                                String.valueOf(r.quantidadeVendida()),
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.totalVendido()),
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.precoMedio())};
                    };
                }
                case VENDAS_POR_OPERADOR -> {
                    List<RelatorioService.VendasPorOperador> v = relatorioService.vendasPorOperador(inicio, fim);
                    cabecalhos = new String[]{"Operador", "Perfil", "Qtd", "Total", "Dinheiro", "PIX", "Débito", "Crédito"};
                    dados = new ArrayList<>(v);
                    mapeador = obj -> {
                        RelatorioService.VendasPorOperador r = (RelatorioService.VendasPorOperador) obj;
                        return new String[]{r.usuarioNome(), r.perfil(), String.valueOf(r.qtdVendas()),
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.totalVendas()),
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.totalDinheiro()),
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.totalPix()),
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.totalDebito()),
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.totalCredito())};
                    };
                }
                case VENDAS_FORMA_PAGAMENTO -> {
                    List<RelatorioService.VendasPorFormaPagamento> v = relatorioService.vendasPorFormaPagamento(inicio, fim);
                    cabecalhos = new String[]{"Forma Pagamento", "Qtd", "Total"};
                    dados = new ArrayList<>(v);
                    mapeador = obj -> {
                        RelatorioService.VendasPorFormaPagamento r = (RelatorioService.VendasPorFormaPagamento) obj;
                        return new String[]{r.formaPagamento(), String.valueOf(r.qtdVendas()),
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.totalVendas())};
                    };
                }
                case CANCELAMENTOS -> {
                    List<RelatorioService.CancelamentoInfo> v = relatorioService.cancelamentos(inicio, fim);
                    cabecalhos = new String[]{"ID", "Número", "Data", "Pagamento", "Total", "Operador", "Motivo", "Data Cancel.", "Cancelador"};
                    dados = new ArrayList<>(v);
                    mapeador = obj -> {
                        RelatorioService.CancelamentoInfo r = (RelatorioService.CancelamentoInfo) obj;
                        return new String[]{String.valueOf(r.id()), r.numero(), r.data(), r.formaPagamento(),
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.total()),
                                r.usuarioNome(), r.cancelamentoMotivo(), r.cancelamentoData(), r.canceladorNome()};
                    };
                }
                case HISTORICO_CAIXAS -> {
                    List<RelatorioService.CaixaHistorico> v = relatorioService.historicoCaixas(inicio, fim);
                    cabecalhos = new String[]{"ID", "Abertura", "Fechamento", "Valor Inicial", "Status", "Abertura por", "Fechamento por"};
                    dados = new ArrayList<>(v);
                    mapeador = obj -> {
                        RelatorioService.CaixaHistorico r = (RelatorioService.CaixaHistorico) obj;
                        return new String[]{String.valueOf(r.id()), r.dataAbertura(),
                                r.dataFechamento() != null ? r.dataFechamento() : "",
                                br.com.adriano.ui.pdv.Dinheiro.formatar(r.valorInicial()),
                                r.status(), r.usuarioAberturaNome(),
                                r.usuarioFechamentoNome() != null ? r.usuarioFechamentoNome() : ""};
                    };
                }
                default -> {
                    JOptionPane.showMessageDialog(this, "Exportação PDF não disponível para este relatório.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("relatorio_" + tipoAtual.name().toLowerCase() + "_" + LocalDate.now() + ".pdf"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File arquivo = chooser.getSelectedFile();
                try {
                    relatorioService.exportarPDF("Relatório - " + tipoAtual.name().replace("_", " "), cabecalhos, dados, arquivo, mapeador);
                    aoStatus.accept("PDF exportado: " + arquivo.getName());
                    JOptionPane.showMessageDialog(this, "PDF exportado com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                } catch (ClassNotFoundException ex) {
                    // iText não disponível - fallback para CSV
                    JOptionPane.showMessageDialog(this,
                            "Biblioteca iText não encontrada. Exportando CSV como alternativa.",
                            "Aviso", JOptionPane.WARNING_MESSAGE);
                    exportarCSV();
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao exportar PDF: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void imprimir() {
        try {
            boolean complete = tabela.print(JTable.PrintMode.FIT_WIDTH, null, null);
            if (complete) {
                aoStatus.accept("Relatório impresso.");
            } else {
                aoStatus.accept("Impressão cancelada.");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao imprimir: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ============= ENUM =============

    private enum TipoRelatorio {
        VENDAS_PERIODO,
        VENDAS_DETALHADAS,
        PRODUTOS_MAIS_VENDIDOS,
        VENDAS_POR_OPERADOR,
        VENDAS_FORMA_PAGAMENTO,
        CANCELAMENTOS,
        HISTORICO_CAIXAS,
        RESUMO_DIARIO
    }
}