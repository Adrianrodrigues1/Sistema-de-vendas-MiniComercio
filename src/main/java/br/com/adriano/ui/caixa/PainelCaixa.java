package br.com.adriano.ui.caixa;

import br.com.adriano.db.ConexaoManager;
import br.com.adriano.service.CaixaService;
import br.com.adriano.service.RelatorioService;
import br.com.adriano.service.VendaService;
import br.com.adriano.ui.componentes.Componentes;
import br.com.adriano.ui.componentes.Icone;
import br.com.adriano.ui.tema.GerenciadorTema;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.Dialog.ModalityType;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Painel de gerenciamento de caixa (abertura, movimentações, fechamento).
 * <p>
 * Funcionalidades:
 * - Abertura de caixa com valor inicial
 * - Suprimento e retirada (sangria) com motivo obrigatório
 * - Fechamento com conferência de valores
 * - Histórico de movimentações
 * - Visualização de caixa aberto/fechado
 *
 * @since 2.0 - Fase 4
 */
public final class PainelCaixa extends JPanel {

    private final CaixaService caixaService;
    private final VendaService vendaService;
    private final RelatorioService relatorioService;
    private final ConexaoManager conexaoManager;
    private final Consumer<String> aoStatus;

    // Estado do caixa
    private CaixaService.CaixaInfo caixaAtual = null;

    // Componentes UI
    private JLabel lblStatusCaixa;
    private JLabel lblValorInicial;
    private JLabel lblSaldoDisponivel;
    private JLabel lblTotalVendas;

    // CardLayout para alternar entre abertura e fechamento
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);

    // Card de abertura
    private JPanel cardAbertura;
    private JFormattedTextField txtValorInicial;

    // Card de fechamento
    private JPanel cardFechamento;
    private DefaultTableModel modeloMovimentos;
    private JTable tabelaMovimentos;
    private JLabel lblTotalDinheiro;
    private JLabel lblTotalPix;
    private JLabel lblTotalDebito;
    private JLabel lblTotalCredito;
    private JLabel lblTotalSuprimentos;
    private JLabel lblTotalRetiradas;
    private JLabel lblValorEsperado;
    private JFormattedTextField txtValorContado;
    private JLabel lblDiferenca;

    public PainelCaixa(CaixaService caixaService, VendaService vendaService,
                       RelatorioService relatorioService, ConexaoManager conexaoManager,
                       Consumer<String> aoStatus) {
        this.caixaService = caixaService;
        this.vendaService = vendaService;
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

        // Carregar estado atual
        atualizarEstado();
    }

    private JComponent montarTopo() {
        JPanel topo = Componentes.painel(new BorderLayout(12, 0));
        GerenciadorTema.estilo(topo, "fundo", null);

        // Título
        JLabel titulo = Componentes.texto("Gerenciamento de Caixa", 18, true);
        titulo.setIcon(new Icone(Icone.Tipo.CAIXA, 24));
        titulo.setIconTextGap(10);
        topo.add(titulo, BorderLayout.WEST);

        // Status do caixa
        JPanel statusPanel = Componentes.painel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        GerenciadorTema.estilo(statusPanel, "fundo", null);

        lblStatusCaixa = Componentes.texto("Caixa Fechado", 14, true);
        lblStatusCaixa.setIcon(new Icone(Icone.Tipo.X, 18));
        lblStatusCaixa.setIconTextGap(8);
        lblStatusCaixa.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GerenciadorTema.cor("statusFechado")),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        lblStatusCaixa.setOpaque(true);
        lblStatusCaixa.setBackground(GerenciadorTema.cor("statusFechadoFundo"));
        statusPanel.add(lblStatusCaixa);

        topo.add(statusPanel, BorderLayout.EAST);
        return topo;
    }

    private JComponent montarCentro() {
        JPanel centro = Componentes.painel(new BorderLayout(12, 12));
        GerenciadorTema.estilo(centro, "fundo", null);

        // Painel de informações (sempre visível)
        centro.add(montarInfoPanel(), BorderLayout.NORTH);

        // CardLayout com abertura/fechamento
        cardPanel.setOpaque(false);
        cardPanel.add(montarCardAbertura(), "ABERTURA");
        cardPanel.add(montarCardFechamento(), "FECHAMENTO");
        centro.add(cardPanel, BorderLayout.CENTER);

        return centro;
    }

    private JComponent montarInfoPanel() {
        JPanel info = Componentes.painel(new GridLayout(1, 4, 12, 0));
        GerenciadorTema.estilo(info, "fundo", null);
        info.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        lblValorInicial = criarCardInfo("Valor Inicial", "R$ 0,00", Icone.Tipo.DINHEIRO);
        lblSaldoDisponivel = criarCardInfo("Saldo Disponível", "R$ 0,00", Icone.Tipo.CARTAO);
        lblTotalVendas = criarCardInfo("Total Vendas", "R$ 0,00", Icone.Tipo.RELATORIO);

        // Card vazio para preencher
        JLabel lblVazio = criarCardInfo("Última Atualização", "—", Icone.Tipo.CONFIGURACAO);

        info.add(lblValorInicial);
        info.add(lblSaldoDisponivel);
        info.add(lblTotalVendas);
        info.add(lblVazio);

        return info;
    }

    private JLabel criarCardInfo(String titulo, String valor, Icone.Tipo icone) {
        JPanel card = Componentes.painel(new BorderLayout(8, 4));
        GerenciadorTema.estilo(card, "painelElevado", null);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GerenciadorTema.cor("borda")),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));

        JLabel lblTitulo = Componentes.texto(titulo, 12, false);
        lblTitulo.setIcon(new Icone(icone, 16));
        lblTitulo.setIconTextGap(6);
        GerenciadorTema.estilo(lblTitulo, null, "muted");

        JLabel lblValor = Componentes.texto(valor, 18, true);
        lblValor.setHorizontalAlignment(SwingConstants.CENTER);

        card.add(lblTitulo, BorderLayout.NORTH);
        card.add(lblValor, BorderLayout.CENTER);

        // Retornar o label de valor para atualização posterior
        return lblValor;
    }

    private JComponent montarCardAbertura() {
        JPanel card = Componentes.painel(new GridBagLayout());
        GerenciadorTema.estilo(card, "fundo", null);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel lblTitulo = Componentes.texto("Abertura de Caixa", 16, true);
        lblTitulo.setIcon(new Icone(Icone.Tipo.CAIXA, 20));
        lblTitulo.setIconTextGap(8);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        card.add(lblTitulo, gbc);

        JLabel lblDesc = Componentes.secundario("Informe o valor inicial do caixa (fundo de caixa/troco).");
        gbc.gridy = 1; gbc.gridwidth = 2;
        card.add(lblDesc, gbc);

        JLabel lblValor = Componentes.texto("Valor Inicial:", 14, true);
        gbc.gridy = 2; gbc.gridwidth = 1;
        card.add(lblValor, gbc);

        txtValorInicial = new JFormattedTextField(new java.text.DecimalFormat("#,##0.00"));
        txtValorInicial.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        txtValorInicial.setPreferredSize(new Dimension(200, 44));
        txtValorInicial.setValue(BigDecimal.ZERO);
        GerenciadorTema.estilo(txtValorInicial, "inputFundo", "inputTexto");
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        card.add(txtValorInicial, gbc);

        JLabel lblExemplo = Componentes.secundario("Ex: 200,00");
        gbc.gridy = 3; gbc.gridx = 1; gbc.fill = GridBagConstraints.NONE;
        card.add(lblExemplo, gbc);

        JButton btnAbrir = Componentes.botao("Abrir Caixa (F5)", Icone.Tipo.CAIXA, () -> abrirCaixa());
        btnAbrir.setPreferredSize(new Dimension(200, 44));
        btnAbrir.setFont(new Font("Segoe UI", Font.BOLD, 14));
        Componentes.primario(btnAbrir);
        gbc.gridy = 4; gbc.gridx = 0; gbc.gridwidth = 2; gbc.insets = new Insets(24, 8, 8, 8);
        card.add(btnAbrir, gbc);

        // Atalho F5
        InputMap im = card.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = card.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "abrir");
        am.put("abrir", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { abrirCaixa(); }
        });

        return card;
    }

    private JComponent montarCardFechamento() {
        JPanel card = Componentes.painel(new BorderLayout(12, 12));
        GerenciadorTema.estilo(card, "fundo", null);

        // Topo: Resumo de vendas por forma de pagamento
        JPanel resumoTopo = Componentes.painel(new GridLayout(1, 6, 8, 0));
        GerenciadorTema.estilo(resumoTopo, "fundo", null);
        resumoTopo.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(GerenciadorTema.cor("borda")),
                " Totais de Vendas ",
                0, 0, new Font("Segoe UI", Font.BOLD, 13), GerenciadorTema.cor("texto")));

        lblTotalDinheiro = criarLabelResumo("Dinheiro", "R$ 0,00", "success");
        lblTotalPix = criarLabelResumo("PIX", "R$ 0,00", "info");
        lblTotalDebito = criarLabelResumo("Débito", "R$ 0,00", "info");
        lblTotalCredito = criarLabelResumo("Crédito", "R$ 0,00", "info");
        lblTotalSuprimentos = criarLabelResumo("Suprimentos", "R$ 0,00", "success");
        lblTotalRetiradas = criarLabelResumo("Retiradas", "R$ 0,00", "danger");

        resumoTopo.add(lblTotalDinheiro);
        resumoTopo.add(lblTotalPix);
        resumoTopo.add(lblTotalDebito);
        resumoTopo.add(lblTotalCredito);
        resumoTopo.add(lblTotalSuprimentos);
        resumoTopo.add(lblTotalRetiradas);

        card.add(resumoTopo, BorderLayout.NORTH);

        // Centro: Movimentações (suprimentos/retiradas)
        JPanel centro = Componentes.painel(new BorderLayout(0, 8));
        GerenciadorTema.estilo(centro, "fundo", null);
        centro.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(GerenciadorTema.cor("borda")),
                " Movimentações (Suprimentos / Retiradas) ",
                0, 0, new Font("Segoe UI", Font.BOLD, 13), GerenciadorTema.cor("texto")));

        modeloMovimentos = new DefaultTableModel(
                new String[]{"ID", "Tipo", "Valor", "Motivo", "Data/Hora", "Usuário"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        tabelaMovimentos = new JTable(modeloMovimentos);
        tabelaMovimentos.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabelaMovimentos.setRowHeight(28);
        tabelaMovimentos.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabelaMovimentos.getTableHeader().setBackground(GerenciadorTema.cor("headerFundo"));
        tabelaMovimentos.getTableHeader().setForeground(GerenciadorTema.cor("headerTexto"));
        tabelaMovimentos.setOpaque(false);

        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(modeloMovimentos);
        tabelaMovimentos.setRowSorter(sorter);

        JScrollPane scroll = new JScrollPane(tabelaMovimentos, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        GerenciadorTema.estilo(scroll.getViewport(), "fundo", null);
        centro.add(scroll, BorderLayout.CENTER);

        // Botões de ação
        JPanel botoesMov = Componentes.painel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        GerenciadorTema.estilo(botoesMov, "fundo", null);
        JButton btnSuprimento = Componentes.botao("Suprimento (F6)", Icone.Tipo.DINHEIRO, () -> mostrarDialogoMovimento("SUPRIMENTO"));
        btnSuprimento.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JButton btnRetirada = Componentes.botao("Sangria/Retirada (F7)", Icone.Tipo.CARTAO, () -> mostrarDialogoMovimento("RETIRADA"));
        btnRetirada.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        botoesMov.add(btnSuprimento);
        botoesMov.add(btnRetirada);
        centro.add(botoesMov, BorderLayout.SOUTH);

        card.add(centro, BorderLayout.CENTER);

        // Rodapé: Fechamento
        JPanel rodape = Componentes.painel(new BorderLayout(12, 12));
        GerenciadorTema.estilo(rodape, "fundo", null);
        rodape.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(GerenciadorTema.cor("borda")),
                " Fechamento de Caixa ",
                0, 0, new Font("Segoe UI", Font.BOLD, 13), GerenciadorTema.cor("texto")));

        // Esquerda: Valor esperado
        JPanel esq = Componentes.painel(new GridLayout(3, 1, 4, 0));
        GerenciadorTema.estilo(esq, "fundo", null);
        lblValorEsperado = Componentes.texto("Valor Esperado no Caixa: R$ 0,00", 16, true);
        lblValorEsperado.setIcon(new Icone(Icone.Tipo.CONFIGURACAO, 20));
        lblValorEsperado.setIconTextGap(8);
        esq.add(lblValorEsperado);

        JPanel contadoPanel = Componentes.painel(new BorderLayout(8, 0));
        GerenciadorTema.estilo(contadoPanel, "fundo", null);
        JLabel lblContado = Componentes.texto("Valor Contado:", 14, true);
        lblContado.setPreferredSize(new Dimension(140, 36));
        contadoPanel.add(lblContado, BorderLayout.WEST);

        txtValorContado = new JFormattedTextField(new java.text.DecimalFormat("#,##0.00"));
        txtValorContado.setFont(new Font("Segoe UI", Font.BOLD, 18));
        txtValorContado.setValue(BigDecimal.ZERO);
        GerenciadorTema.estilo(txtValorContado, "inputFundo", "inputTexto");
        txtValorContado.addPropertyChangeListener("value", e -> calcularDiferenca());
        contadoPanel.add(txtValorContado, BorderLayout.CENTER);
        esq.add(contadoPanel);

        lblDiferenca = Componentes.texto("Diferença: R$ 0,00", 14, true);
        esq.add(lblDiferenca);

        rodape.add(esq, BorderLayout.CENTER);

        // Direita: Botão fechar
        JPanel dir = Componentes.painel(new FlowLayout(FlowLayout.CENTER, 0, 24));
        GerenciadorTema.estilo(dir, "fundo", null);
        JButton btnFechar = Componentes.botao("Confirmar Fechamento (F8)", Icone.Tipo.CHECK, () -> fecharCaixa());
        btnFechar.setPreferredSize(new Dimension(250, 50));
        btnFechar.setFont(new Font("Segoe UI", Font.BOLD, 14));
        Componentes.primario(btnFechar);
        dir.add(btnFechar);
        rodape.add(dir, BorderLayout.EAST);

        card.add(rodape, BorderLayout.SOUTH);

        // Atalhos F6, F7, F8
        InputMap im = card.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = card.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), "suprimento");
        am.put("suprimento", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { mostrarDialogoMovimento("SUPRIMENTO"); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), "retirada");
        am.put("retirada", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { mostrarDialogoMovimento("RETIRADA"); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0), "fechar");
        am.put("fechar", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { fecharCaixa(); }
        });

        return card;
    }

    private JLabel criarLabelResumo(String titulo, String valor, String estilo) {
        JPanel p = Componentes.painel(new GridLayout(2, 1, 2, 0));
        GerenciadorTema.estilo(p, "painelElevado", null);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GerenciadorTema.cor("borda")),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        JLabel lblTitulo = Componentes.texto(titulo, 11, false);
        GerenciadorTema.estilo(lblTitulo, null, "muted");
        p.add(lblTitulo);

        JLabel lblValor = Componentes.texto(valor, 15, true);
        lblValor.setHorizontalAlignment(SwingConstants.CENTER);
        if ("success".equals(estilo)) lblValor.setForeground(GerenciadorTema.cor("success"));
        else if ("danger".equals(estilo)) lblValor.setForeground(GerenciadorTema.cor("danger"));
        p.add(lblValor);

        // Retornamos o label de valor para atualização
        return lblValor;
    }

    private JComponent montarRodape() {
        JPanel rodape = Componentes.painel(new BorderLayout());
        GerenciadorTema.estilo(rodape, "fundo", null);

        JButton btnHistorico = Componentes.botao("Histórico de Caixas", Icone.Tipo.RELATORIO, () -> mostrarHistorico());
        btnHistorico.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        rodape.add(btnHistorico, BorderLayout.WEST);

        JLabel nota = Componentes.secundario("F5: Abrir | F6: Suprimento | F7: Sangria | F8: Fechar");
        rodape.add(nota, BorderLayout.EAST);

        return rodape;
    }

    // ============= LÓGICA DE NEGÓCIO =============

    private void atualizarEstado() {
        try {
            caixaAtual = caixaService.getCaixaAberto().orElse(null);
            if (caixaAtual != null) {
                lblStatusCaixa.setText("Caixa Aberto");
                lblStatusCaixa.setIcon(new Icone(Icone.Tipo.CHECK, 18));
                lblStatusCaixa.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(GerenciadorTema.cor("statusAberto")),
                        BorderFactory.createEmptyBorder(6, 12, 6, 12)));
                lblStatusCaixa.setBackground(GerenciadorTema.cor("statusAbertoFundo"));

                lblValorInicial.setText("R$ " + br.com.adriano.ui.pdv.Dinheiro.formatar(caixaAtual.valorInicial()));

                BigDecimal saldo = caixaService.getSaldoDisponivel(caixaAtual.id());
                lblSaldoDisponivel.setText("R$ " + br.com.adriano.ui.pdv.Dinheiro.formatar(saldo));

                VendaService.TotaisCaixa totais = vendaService.calcularTotaisCaixa(caixaAtual.id());
                lblTotalVendas.setText("R$ " + br.com.adriano.ui.pdv.Dinheiro.formatar(totais.totalGeral));

                cardLayout.show(cardPanel, "FECHAMENTO");
                carregarMovimentos();
                atualizarResumoFechamento(totais);
            } else {
                lblStatusCaixa.setText("Caixa Fechado");
                lblStatusCaixa.setIcon(new Icone(Icone.Tipo.X, 18));
                lblStatusCaixa.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(GerenciadorTema.cor("statusFechado")),
                        BorderFactory.createEmptyBorder(6, 12, 6, 12)));
                lblStatusCaixa.setBackground(GerenciadorTema.cor("statusFechadoFundo"));

                lblValorInicial.setText("R$ 0,00");
                lblSaldoDisponivel.setText("R$ 0,00");
                lblTotalVendas.setText("R$ 0,00");

                cardLayout.show(cardPanel, "ABERTURA");
                txtValorInicial.setValue(BigDecimal.ZERO);
                txtValorInicial.requestFocus();
            }
        } catch (Exception ex) {
            aoStatus.accept("Erro ao atualizar estado: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void abrirCaixa() {
        try {
            Object valorObj = txtValorInicial.getValue();
            BigDecimal valor = (valorObj instanceof Number) ? new BigDecimal(valorObj.toString()) : BigDecimal.ZERO;

            if (valor.compareTo(BigDecimal.ZERO) < 0) {
                JOptionPane.showMessageDialog(this, "Valor inicial não pode ser negativo.", "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }

            CaixaService.CaixaInfo caixa = caixaService.abrirCaixa(valor, 1); // TODO: pegar usuario real
            caixaAtual = caixa;
            atualizarEstado();
            aoStatus.accept("Caixa aberto com valor inicial: " + br.com.adriano.ui.pdv.Dinheiro.formatar(valor));
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao abrir caixa: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void carregarMovimentos() {
        if (caixaAtual == null) return;
        try {
            List<CaixaService.MovimentoCaixa> movimentos = caixaService.listarMovimentos(caixaAtual.id());
            modeloMovimentos.setRowCount(0);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            for (CaixaService.MovimentoCaixa m : movimentos) {
                String tipoIcon = "SUPRIMENTO".equals(m.tipo()) ? "▲" : "▼";
                modeloMovimentos.addRow(new Object[]{
                        m.id(),
                        tipoIcon + " " + m.tipo(),
                        br.com.adriano.ui.pdv.Dinheiro.formatar(m.valor()),
                        m.motivo(),
                        m.data().replace("T", " "),
                        m.usuarioNome()
                });
            }
        } catch (Exception ex) {
            aoStatus.accept("Erro ao carregar movimentos: " + ex.getMessage());
        }
    }

    private void atualizarResumoFechamento(VendaService.TotaisCaixa totais) {
        lblTotalDinheiro.setText("R$ " + br.com.adriano.ui.pdv.Dinheiro.formatar(totais.totalDinheiro));
        lblTotalPix.setText("R$ " + br.com.adriano.ui.pdv.Dinheiro.formatar(totais.totalPix));
        lblTotalDebito.setText("R$ " + br.com.adriano.ui.pdv.Dinheiro.formatar(totais.totalDebito));
        lblTotalCredito.setText("R$ " + br.com.adriano.ui.pdv.Dinheiro.formatar(totais.totalCredito));
        lblTotalSuprimentos.setText("R$ " + br.com.adriano.ui.pdv.Dinheiro.formatar(totais.totalSuprimentos));
        lblTotalRetiradas.setText("R$ " + br.com.adriano.ui.pdv.Dinheiro.formatar(totais.totalRetiradas));

        BigDecimal esperado = totais.getTotalEsperadoCaixa(caixaAtual.valorInicial());
        lblValorEsperado.setText("Valor Esperado no Caixa: R$ " + br.com.adriano.ui.pdv.Dinheiro.formatar(esperado));

        txtValorContado.setValue(esperado);
        calcularDiferenca();
    }

    private void calcularDiferenca() {
        if (caixaAtual == null) return;
        try {
            Object valorObj = txtValorContado.getValue();
            BigDecimal contado = (valorObj instanceof Number) ? new BigDecimal(valorObj.toString()) : BigDecimal.ZERO;

            VendaService.TotaisCaixa totais = vendaService.calcularTotaisCaixa(caixaAtual.id());
            BigDecimal esperado = totais.getTotalEsperadoCaixa(caixaAtual.valorInicial());
            BigDecimal diff = contado.subtract(esperado);

            lblDiferenca.setText("Diferença: R$ " + br.com.adriano.ui.pdv.Dinheiro.formatar(diff));
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                lblDiferenca.setForeground(GerenciadorTema.cor("success"));
            } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
                lblDiferenca.setForeground(GerenciadorTema.cor("danger"));
            } else {
                lblDiferenca.setForeground(GerenciadorTema.cor("texto"));
            }
        } catch (Exception ex) {
            lblDiferenca.setText("Diferença: Erro");
            lblDiferenca.setForeground(GerenciadorTema.cor("danger"));
        }
    }

    private void mostrarDialogoMovimento(String tipo) {
        if (caixaAtual == null) return;

        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Registro de " + tipo, ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(12, 12));
        dialog.setSize(400, 280);
        dialog.setLocationRelativeTo(this);
        dialog.getRootPane().setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GerenciadorTema.aplicar(dialog);

        JPanel painel = Componentes.painel(new GridBagLayout());
        GerenciadorTema.estilo(painel, "fundo", null);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel lblTitulo = Componentes.texto(tipo.equals("SUPRIMENTO") ? "Suprimento (Entrada de Valores)" : "Sangria/Retirada (Saída de Valores)", 14, true);
        lblTitulo.setIcon(new Icone(tipo.equals("SUPRIMENTO") ? Icone.Tipo.DINHEIRO : Icone.Tipo.CARTAO, 20));
        lblTitulo.setIconTextGap(8);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        painel.add(lblTitulo, gbc);

        JLabel lblValor = Componentes.texto("Valor:", 13, true);
        gbc.gridy = 1; gbc.gridwidth = 1;
        painel.add(lblValor, gbc);

        JFormattedTextField txtValor = new JFormattedTextField(new java.text.DecimalFormat("#,##0.00"));
        txtValor.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        txtValor.setPreferredSize(new Dimension(180, 38));
        txtValor.setValue(BigDecimal.ZERO);
        GerenciadorTema.estilo(txtValor, "inputFundo", "inputTexto");
        gbc.gridx = 1;
        painel.add(txtValor, gbc);

        JLabel lblMotivo = Componentes.texto("Motivo (obrigatório):", 13, true);
        gbc.gridy = 2; gbc.gridx = 0;
        painel.add(lblMotivo, gbc);

        JTextField txtMotivo = new JTextField();
        txtMotivo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtMotivo.setPreferredSize(new Dimension(180, 38));
        GerenciadorTema.estilo(txtMotivo, "inputFundo", "inputTexto");
        gbc.gridx = 1;
        painel.add(txtMotivo, gbc);

        JPanel botoes = Componentes.painel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        GerenciadorTema.estilo(botoes, "fundo", null);
        JButton btnCancelar = Componentes.botao("Cancelar", Icone.Tipo.X, () -> dialog.dispose());
        JButton btnConfirmar = Componentes.botao("Confirmar", Icone.Tipo.CHECK, () -> {
            String motivo = txtMotivo.getText().trim();
            if (motivo.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Motivo é obrigatório.", "Atenção", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Object valorObj = txtValor.getValue();
            BigDecimal valor = (valorObj instanceof Number) ? new BigDecimal(valorObj.toString()) : BigDecimal.ZERO;
            if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(dialog, "Valor deve ser maior que zero.", "Atenção", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                if ("SUPRIMENTO".equals(tipo)) {
                    caixaService.suprimento(caixaAtual.id(), valor, motivo, 1);
                } else {
                    caixaService.retirada(caixaAtual.id(), valor, motivo, 1);
                }
                dialog.dispose();
                carregarMovimentos();
                atualizarEstado();
                aoStatus.accept(tipo + " de " + br.com.adriano.ui.pdv.Dinheiro.formatar(valor) + " registrado.");
            } catch (IllegalStateException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });
        btnConfirmar.setFont(new Font("Segoe UI", Font.BOLD, 13));
        Componentes.primario(btnConfirmar);
        botoes.add(btnCancelar);
        botoes.add(btnConfirmar);
        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.EAST;
        painel.add(botoes, gbc);

        dialog.add(painel, BorderLayout.CENTER);
        dialog.setVisible(true);
        txtValor.requestFocus();
    }

    private void fecharCaixa() {
        if (caixaAtual == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Confirmar fechamento do caixa?\nEsta ação não pode ser desfeita.",
                "Confirmar Fechamento", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            CaixaService.CaixaInfo fechado = caixaService.fecharCaixa(caixaAtual.id(), 1);
            caixaAtual = null;
            atualizarEstado();
            aoStatus.accept("Caixa fechado com sucesso.");
            JOptionPane.showMessageDialog(this,
                    "Caixa fechado!\nValor inicial: " + br.com.adriano.ui.pdv.Dinheiro.formatar(fechado.valorInicial()) +
                            "\nData fechamento: " + fechado.dataFechamento(),
                    "Fechamento Concluído", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao fechar caixa: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void mostrarHistorico() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Histórico de Caixas", ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(800, 500);
        dialog.setLocationRelativeTo(this);
        GerenciadorTema.aplicar(dialog);

        try {
            List<RelatorioService.CaixaHistorico> dados = relatorioService.historicoCaixas(
                    LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(30),
                    LocalDate.now(ZoneId.of("America/Sao_Paulo")));

            DefaultTableModel model = new DefaultTableModel(
                    new String[]{"ID", "Abertura", "Fechamento", "Valor Inicial", "Status", "Abertura por", "Fechamento por"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };

            for (RelatorioService.CaixaHistorico c : dados) {
                model.addRow(new Object[]{
                        c.id(), c.dataAbertura(),
                        c.dataFechamento() != null ? c.dataFechamento() : "—",
                        br.com.adriano.ui.pdv.Dinheiro.formatar(c.valorInicial()),
                        c.status(), c.usuarioAberturaNome(),
                        c.usuarioFechamentoNome() != null ? c.usuarioFechamentoNome() : "—"
                });
            }

            JTable table = new JTable(model);
            table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            table.setRowHeight(28);

            JScrollPane scroll = new JScrollPane(table);
            dialog.add(scroll, BorderLayout.CENTER);

            JButton btnFechar = Componentes.botao("Fechar", Icone.Tipo.X, dialog::dispose);
            JPanel bp = Componentes.painel(new FlowLayout(FlowLayout.RIGHT));
            GerenciadorTema.estilo(bp, "fundo", null);
            bp.add(btnFechar);
            bp.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 12));
            dialog.add(bp, BorderLayout.SOUTH);

        } catch (Exception ex) {
            JLabel erro = Componentes.texto("Erro ao carregar histórico: " + ex.getMessage(), 14, false);
            erro.setForeground(GerenciadorTema.cor("danger"));
            dialog.add(erro, BorderLayout.CENTER);
            ex.printStackTrace();
        }

        dialog.setVisible(true);
    }
}