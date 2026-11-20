package br.com.adriano.service;

import br.com.adriano.db.ConexaoManager;
import br.com.adriano.migration.MigrationService;
import br.com.adriano.model.Produto;
import br.com.adriano.service.PdvGatewayReal;
import br.com.adriano.ui.pdv.ProdutoPdv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validação funcional completa da Fase 3: Produtos & Estoque.
 * <p>
 * Cobre todos os requisitos:
 * <ul>
 *   <li>CRUD completo de produtos</li>
 *   <li>Busca e filtros</li>
 *   <li>Edição e inativação</li>
 *   <li>Ajustes de estoque (entrada/saída/ajuste absoluto) com motivo e auditoria</li>
 *   <li>Importação/Exportação CSV</li>
 *   <li>Validações: código duplicado, estoque insuficiente, campos inválidos</li>
 *   <li>Integração PDV-Estoque: alterações refletem no PDV</li>
 * </ul>
 */
@DisplayName("Fase 3 - Validação Funcional Completa: Produtos & Estoque")
class Fase3FunctionalValidationTest {

    private ConexaoManager conexaoManager;
    private ProdutoService produtoService;
    private PdvGatewayReal pdvGateway;

    @BeforeEach
    void setUp() throws Exception {
        conexaoManager = ConexaoManager.emMemoria();
        MigrationService migrationService = new MigrationService(conexaoManager);
        migrationService.migrar();

        // Usuários para FKs
        try (Connection conn = conexaoManager.conectar();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT OR IGNORE INTO usuarios (id, usuario, senha, nome, perfil, hash, salt, ativo) VALUES (1, 'admin', '1234', 'Administrador', 'ADMIN', 'hash', 'salt', 1)");
            stmt.execute("INSERT OR IGNORE INTO usuarios (id, usuario, senha, nome, perfil, hash, salt, ativo) VALUES (2, 'operador', '1234', 'Operador', 'OPERADOR', 'hash', 'salt', 1)");
        }

        produtoService = new ProdutoService(conexaoManager);
        pdvGateway = new PdvGatewayReal(conexaoManager);
    }

    // ===== 1. CRUD COMPLETO DE PRODUTOS =====

    @Test
    @DisplayName("1.1 - Deve criar produto com todos os campos")
    void criarProdutoCompleto() throws Exception {
        Produto p = new Produto(null, "PROD001", "Caldo Verde", "Caldos",
                new BigDecimal("19.90"), 50, "caldo_verde.jpg", true);
        Produto salvo = produtoService.salvar(p);

        assertNotNull(salvo.getId());
        assertEquals("PROD001", salvo.getCodigo());
        assertEquals("Caldo Verde", salvo.getNome());
        assertEquals("Caldos", salvo.getCategoria());
        assertEquals(new BigDecimal("19.90"), salvo.getPrecoBigDecimal());
        assertEquals(50, salvo.getEstoque());
        assertEquals("caldo_verde.jpg", salvo.getImagem());
        assertTrue(salvo.isAtivo());
    }

    @Test
    @DisplayName("1.2 - Deve buscar produto por ID")
    void buscarPorId() throws Exception {
        Produto salvo = produtoService.salvar(new Produto(null, "PROD002", "Caldo Feijão", "Caldos",
                new BigDecimal("18.50"), 30, null, true));

        Optional<Produto> encontrado = produtoService.buscarPorId(salvo.getId());
        assertTrue(encontrado.isPresent());
        assertEquals("Caldo Feijão", encontrado.get().getNome());
    }

    @Test
    @DisplayName("1.3 - Deve buscar produto por código")
    void buscarPorCodigo() throws Exception {
        produtoService.salvar(new Produto(null, "PROD003", "Refrigerante", "Bebidas",
                new BigDecimal("6.00"), 100, null, true));

        Optional<Produto> encontrado = produtoService.buscarPorCodigo("PROD003");
        assertTrue(encontrado.isPresent());
        assertEquals("Refrigerante", encontrado.get().getNome());
    }

    @Test
    @DisplayName("1.4 - Deve atualizar produto")
    void atualizarProduto() throws Exception {
        Produto salvo = produtoService.salvar(new Produto(null, "PROD004", "Original", "Cat",
                new BigDecimal("10.00"), 10, null, true));

        salvo.setNome("Atualizado");
        salvo.setPrecoBigDecimal(new BigDecimal("15.00"));
        salvo.setEstoque(20);
        salvo.setAtivo(false);
        produtoService.atualizar(salvo);

        Produto verificado = produtoService.buscarPorId(salvo.getId()).get();
        assertEquals("Atualizado", verificado.getNome());
        assertEquals(new BigDecimal("15.00"), verificado.getPrecoBigDecimal());
        assertEquals(20, verificado.getEstoque());
        assertFalse(verificado.isAtivo());
    }

    @Test
    @DisplayName("1.5 - Deve inativar produto (soft delete)")
    void inativarProduto() throws Exception {
        Produto salvo = produtoService.salvar(new Produto(null, "PROD005", "Para Inativar", "Cat",
                new BigDecimal("10.00"), 10, null, true));

        salvo.setAtivo(false);
        produtoService.atualizar(salvo);

        // Não aparece em listarTodos(true) = apenas ativos para PDV
        List<Produto> ativos = produtoService.listarTodos(true);
        assertFalse(ativos.stream().anyMatch(p -> p.getId().equals(salvo.getId())));

        // Mas aparece em listarTodos(false) = todos
        List<Produto> todos = produtoService.listarTodos(false);
        assertTrue(todos.stream().anyMatch(p -> p.getId().equals(salvo.getId())));
    }

    @Test
    @DisplayName("1.6 - Deve listar produtos com filtro apenas ativos")
    void listarApenasAtivos() throws Exception {
        produtoService.salvar(new Produto(null, "LIS001", "Ativo 1", "Cat", new BigDecimal("10.00"), 10, null, true));
        produtoService.salvar(new Produto(null, "LIS002", "Ativo 2", "Cat", new BigDecimal("10.00"), 10, null, true));
        Produto inativo = new Produto(null, "LIS003", "Inativo", "Cat", new BigDecimal("10.00"), 10, null, false);
        produtoService.salvar(inativo);

        List<Produto> ativos = produtoService.listarTodos(true);
        List<Produto> todos = produtoService.listarTodos(false);

        assertEquals(2, ativos.size());
        assertEquals(3, todos.size());
    }

    // ===== 2. BUSCA E FILTROS =====

    @Test
    @DisplayName("2.1 - Busca por termo (case-insensitive ASCII)")
    void buscaPorTermo() throws Exception {
        produtoService.salvar(new Produto(null, "BUS001", "Caldo Verde", "Caldos", new BigDecimal("15.00"), 10, null, true));
        produtoService.salvar(new Produto(null, "BUS002", "CALDO DE FEIJAO", "Caldos", new BigDecimal("18.00"), 10, null, true));
        produtoService.salvar(new Produto(null, "BUS003", "Refrigerante", "Bebidas", new BigDecimal("6.00"), 10, null, true));

        List<Produto> porCaldo = produtoService.buscarPorTermo("caldo");
        List<Produto> porCALDO = produtoService.buscarPorTermo("CALDO");
        List<Produto> porCodigo = produtoService.buscarPorTermo("BUS001");

        assertEquals(2, porCaldo.size());
        assertEquals(2, porCALDO.size());
        assertEquals(1, porCodigo.size());
        assertEquals("Caldo Verde", porCodigo.get(0).getNome());
    }

    @Test
    @DisplayName("2.2 - Busca por categoria")
    void buscaPorCategoria() throws Exception {
        produtoService.salvar(new Produto(null, "CAT001", "Caldo 1", "Caldos", new BigDecimal("15.00"), 10, null, true));
        produtoService.salvar(new Produto(null, "CAT002", "Caldo 2", "Caldos", new BigDecimal("18.00"), 10, null, true));
        produtoService.salvar(new Produto(null, "CAT003", "Refri", "Bebidas", new BigDecimal("6.00"), 10, null, true));

        List<Produto> caldos = produtoService.buscarPorCategoria("Caldos");
        List<Produto> bebidas = produtoService.buscarPorCategoria("Bebidas");

        assertEquals(2, caldos.size());
        assertEquals(1, bebidas.size());
    }

    @Test
    @DisplayName("2.3 - Listar categorias distintas")
    void listarCategorias() throws Exception {
        produtoService.salvar(new Produto(null, "CAT004", "P1", "Caldos", new BigDecimal("10.00"), 10, null, true));
        produtoService.salvar(new Produto(null, "CAT005", "P2", "Bebidas", new BigDecimal("10.00"), 10, null, true));
        produtoService.salvar(new Produto(null, "CAT006", "P3", "Caldos", new BigDecimal("10.00"), 10, null, true));

        List<String> categorias = produtoService.listarCategorias();
        assertEquals(2, categorias.size());
        assertTrue(categorias.contains("Caldos"));
        assertTrue(categorias.contains("Bebidas"));
    }

    // ===== 3. AJUSTES DE ESTOQUE COM AUDITORIA =====

    @Test
    @DisplayName("3.1 - Entrada de estoque registra movimento com todos os campos")
    void entradaEstoqueComAuditoria() throws Exception {
        Produto salvo = produtoService.salvar(new Produto(null, "EST001", "Produto Entrada", "Teste",
                new BigDecimal("10.00"), 50, null, true));

        // Entrada de 25 unidades
        produtoService.ajustarEstoque(salvo.getId(), 25, "Compra fornecedor XYZ", "COMPRA", 1001, 2);

        // Verificar estoque
        assertEquals(75, produtoService.buscarPorId(salvo.getId()).get().getEstoque());

        // Verificar auditoria completa
        try (Connection conn = conexaoManager.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT tipo, quantidade, quantidade_anterior, quantidade_nova, motivo, referencia_tipo, referencia_id, usuario_id FROM movimentos_estoque WHERE produto_id = " + salvo.getId())) {
            assertTrue(rs.next());
            assertEquals("ENTRADA", rs.getString("tipo"));
            assertEquals(25, rs.getInt("quantidade"));
            assertEquals(50, rs.getInt("quantidade_anterior"));
            assertEquals(75, rs.getInt("quantidade_nova"));
            assertEquals("Compra fornecedor XYZ", rs.getString("motivo"));
            assertEquals("COMPRA", rs.getString("referencia_tipo"));
            assertEquals(1001, rs.getInt("referencia_id"));
            assertEquals(2, rs.getInt("usuario_id"));
        }
    }

    @Test
    @DisplayName("3.2 - Saída de estoque registra movimento")
    void saidaEstoqueComAuditoria() throws Exception {
        Produto salvo = produtoService.salvar(new Produto(null, "EST002", "Produto Saída", "Teste",
                new BigDecimal("10.00"), 100, null, true));

        produtoService.ajustarEstoque(salvo.getId(), -30, "Venda balcão", "VENDA", 2001, 1);

        assertEquals(70, produtoService.buscarPorId(salvo.getId()).get().getEstoque());

        try (Connection conn = conexaoManager.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT tipo, quantidade, quantidade_anterior, quantidade_nova, motivo, referencia_tipo, referencia_id, usuario_id FROM movimentos_estoque WHERE produto_id = " + salvo.getId())) {
            assertTrue(rs.next());
            assertEquals("SAIDA", rs.getString("tipo"));
            assertEquals(30, rs.getInt("quantidade"));
            assertEquals(100, rs.getInt("quantidade_anterior"));
            assertEquals(70, rs.getInt("quantidade_nova"));
            assertEquals("Venda balcão", rs.getString("motivo"));
            assertEquals("VENDA", rs.getString("referencia_tipo"));
            assertEquals(2001, rs.getInt("referencia_id"));
            assertEquals(1, rs.getInt("usuario_id"));
        }
    }

    @Test
    @DisplayName("3.3 - Ajuste absoluto (definir estoque para valor específico)")
    void ajusteAbsoluto() throws Exception {
        Produto salvo = produtoService.salvar(new Produto(null, "EST003", "Produto Ajuste", "Teste",
                new BigDecimal("10.00"), 100, null, true));

        // Ajustar para 42 (delta = -58)
        produtoService.ajustarEstoque(salvo.getId(), -58, "Inventário físico", "AJUSTE_MANUAL", null, 1);

        assertEquals(42, produtoService.buscarPorId(salvo.getId()).get().getEstoque());

        try (Connection conn = conexaoManager.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT tipo, quantidade, quantidade_anterior, quantidade_nova, motivo, referencia_tipo FROM movimentos_estoque WHERE produto_id = " + salvo.getId())) {
            assertTrue(rs.next());
            assertEquals("SAIDA", rs.getString("tipo"));
            assertEquals(58, rs.getInt("quantidade"));
            assertEquals(100, rs.getInt("quantidade_anterior"));
            assertEquals(42, rs.getInt("quantidade_nova"));
            assertEquals("Inventário físico", rs.getString("motivo"));
            assertEquals("AJUSTE_MANUAL", rs.getString("referencia_tipo"));
        }
    }

    @Test
    @DisplayName("3.4 - Histórico sequencial de múltiplos movimentos")
    void historicoMultiplosMovimentos() throws Exception {
        Produto salvo = produtoService.salvar(new Produto(null, "EST004", "Histórico", "Teste",
                new BigDecimal("10.00"), 100, null, true));

        produtoService.ajustarEstoque(salvo.getId(), 10, "Entrada 1", "COMPRA", 1, 1);
        produtoService.ajustarEstoque(salvo.getId(), -5, "Saída 1", "VENDA", 2, 1);
        produtoService.ajustarEstoque(salvo.getId(), 20, "Entrada 2", "COMPRA", 3, 2);

        assertEquals(125, produtoService.buscarPorId(salvo.getId()).get().getEstoque());

        try (Connection conn = conexaoManager.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT tipo, quantidade, motivo FROM movimentos_estoque WHERE produto_id = " + salvo.getId() + " ORDER BY id")) {
            int count = 0;
            while (rs.next()) {
                count++;
                if (count == 1) {
                    assertEquals("ENTRADA", rs.getString("tipo"));
                    assertEquals(10, rs.getInt("quantidade"));
                    assertEquals("Entrada 1", rs.getString("motivo"));
                } else if (count == 2) {
                    assertEquals("SAIDA", rs.getString("tipo"));
                    assertEquals(5, rs.getInt("quantidade"));
                    assertEquals("Saída 1", rs.getString("motivo"));
                } else if (count == 3) {
                    assertEquals("ENTRADA", rs.getString("tipo"));
                    assertEquals(20, rs.getInt("quantidade"));
                    assertEquals("Entrada 2", rs.getString("motivo"));
                }
            }
            assertEquals(3, count);
        }
    }

    @Test
    @DisplayName("3.5 - Permite zerar estoque")
    void permitirZerarEstoque() throws Exception {
        Produto salvo = produtoService.salvar(new Produto(null, "EST005", "Para Zerar", "Teste",
                new BigDecimal("10.00"), 10, null, true));

        produtoService.ajustarEstoque(salvo.getId(), -10, "Zerar estoque", "AJUSTE_MANUAL", null, 1);

        assertEquals(0, produtoService.buscarPorId(salvo.getId()).get().getEstoque());
    }

    // ===== 4. IMPORTAÇÃO/EXPORTAÇÃO CSV =====

    @Test
    @DisplayName("4.1 - Importar produtos novos via CSV (simulado)")
    void importarCSVNovos() throws Exception {
        // Simular linhas de CSV
        String[][] dados = {
                {"CSV001", "Importado 1", "Importados", "25.50", "10", "", "true"},
                {"CSV002", "Importado 2", "Importados", "30.00", "5", "img.jpg", "false"}
        };

        int importados = 0;
        for (String[] v : dados) {
            Produto p = new Produto(null, v[0], v[1], v[2],
                    new BigDecimal(v[3]), Integer.parseInt(v[4]),
                    v[5].isEmpty() ? null : v[5],
                    Boolean.parseBoolean(v[6]));
            produtoService.salvar(p);
            importados++;
        }

        assertEquals(2, importados);
        assertEquals("Importado 1", produtoService.buscarPorCodigo("CSV001").get().getNome());
        assertEquals(new BigDecimal("25.50"), produtoService.buscarPorCodigo("CSV001").get().getPrecoBigDecimal());
        assertEquals(10, produtoService.buscarPorCodigo("CSV001").get().getEstoque());
        assertTrue(produtoService.buscarPorCodigo("CSV001").get().isAtivo());
        assertFalse(produtoService.buscarPorCodigo("CSV002").get().isAtivo());
    }

    @Test
    @DisplayName("4.2 - Atualizar produtos existentes via CSV (upsert por código)")
    void importarCSVAtualizar() throws Exception {
        // Produto existente
        Produto existente = new Produto(null, "UPS001", "Original", "Teste",
                new BigDecimal("10.00"), 100, null, true);
        produtoService.salvar(existente);

        // "Importar" com mesmo código - upsert
        Produto atualizado = new Produto(null, "UPS001", "Atualizado", "Nova Cat",
                new BigDecimal("20.00"), 50, "nova.jpg", false);
        var opt = produtoService.buscarPorCodigo("UPS001");
        if (opt.isPresent()) {
            Produto p = opt.get();
            p.setNome(atualizado.getNome());
            p.setCategoria(atualizado.getCategoria());
            p.setPrecoBigDecimal(atualizado.getPrecoBigDecimal());
            p.setEstoque(atualizado.getEstoque());
            p.setImagem(atualizado.getImagem());
            p.setAtivo(atualizado.isAtivo());
            produtoService.atualizar(p);
        }

        Produto verificado = produtoService.buscarPorCodigo("UPS001").get();
        assertEquals("Atualizado", verificado.getNome());
        assertEquals("Nova Cat", verificado.getCategoria());
        assertEquals(new BigDecimal("20.00"), verificado.getPrecoBigDecimal());
        assertEquals(50, verificado.getEstoque());
        assertEquals("nova.jpg", verificado.getImagem());
        assertFalse(verificado.isAtivo());
    }

    @Test
    @DisplayName("4.3 - Exportar produtos para formato CSV")
    void exportarCSV() throws Exception {
        produtoService.salvar(new Produto(null, "EXP001", "Exportado 1", "Cat1", new BigDecimal("15.50"), 100, "img1.jpg", true));
        produtoService.salvar(new Produto(null, "EXP002", "Exportado 2", "Cat2", new BigDecimal("25.00"), 50, null, false));

        List<Produto> produtos = produtoService.listarTodos(false);

        StringBuilder csv = new StringBuilder();
        csv.append("codigo,nome,categoria,preco,estoque,imagem,ativo\n");
        for (Produto p : produtos) {
            csv.append(String.join(",",
                    p.getCodigo(),
                    p.getNome(),
                    p.getCategoria(),
                    p.getPrecoBigDecimal().toPlainString(),
                    String.valueOf(p.getEstoque()),
                    p.getImagem() != null ? p.getImagem() : "",
                    p.isAtivo() ? "true" : "false"
            )).append("\n");
        }

        String result = csv.toString();
        assertTrue(result.contains("EXP001,Exportado 1,Cat1,15.50,100,img1.jpg,true"));
        assertTrue(result.contains("EXP002,Exportado 2,Cat2,25.00,50,,false"));
    }

    // ===== 5. VALIDAÇÕES DE BORDA =====

    @Test
    @DisplayName("5.1 - Rejeitar código duplicado")
    void rejeitarCodigoDuplicado() throws Exception {
        produtoService.salvar(new Produto(null, "DUP001", "Produto 1", "Teste", new BigDecimal("10.00"), 10, null, true));

        Produto duplicado = new Produto(null, "DUP001", "Produto 2", "Teste", new BigDecimal("20.00"), 20, null, true);
        assertThrows(Exception.class, () -> produtoService.salvar(duplicado));
    }

    @Test
    @DisplayName("5.2 - Rejeitar estoque insuficiente em saída")
    void rejeitarEstoqueInsuficiente() throws Exception {
        Produto salvo = produtoService.salvar(new Produto(null, "INS001", "Estoque Baixo", "Teste",
                new BigDecimal("10.00"), 5, null, true));

        assertThrows(IllegalStateException.class,
                () -> produtoService.ajustarEstoque(salvo.getId(), -10, "Tentativa saída", "VENDA", 1, 1));
    }

    @Test
    @DisplayName("5.3 - Validar campos obrigatórios")
    void validarCamposObrigatorios() {
        // Código obrigatório
        assertThrows(IllegalArgumentException.class, () ->
                produtoService.salvar(new Produto(null, "", "Nome", "Cat", new BigDecimal("10.00"), 10, null, true)));

        // Nome obrigatório
        assertThrows(IllegalArgumentException.class, () ->
                produtoService.salvar(new Produto(null, "COD", "", "Cat", new BigDecimal("10.00"), 10, null, true)));

        // Preço negativo
        assertThrows(IllegalArgumentException.class, () ->
                produtoService.salvar(new Produto(null, "COD", "Nome", "Cat", new BigDecimal("-1.00"), 10, null, true)));

        // Estoque negativo
        assertThrows(IllegalArgumentException.class, () ->
                produtoService.salvar(new Produto(null, "COD", "Nome", "Cat", new BigDecimal("10.00"), -1, null, true)));
    }

    @Test
    @DisplayName("5.4 - Produto com vendas associadas não pode ser excluído")
    void naoExcluirComVendas() throws Exception {
        Produto salvo = produtoService.salvar(new Produto(null, "VEND001", "Com Vendas", "Teste",
                new BigDecimal("10.00"), 10, null, true));

        // Simular venda associada
        try (Connection conn = conexaoManager.conectar();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO vendas (id, forma_pagamento, total, usuario_id, status) VALUES (1, 'DINHEIRO', 10.00, 1, 'FINALIZADA')");
            stmt.execute("INSERT INTO itens_venda (venda_id, produto_id, quantidade, preco_unitario, preco_total) VALUES (1, " + salvo.getId() + ", 1, 10.00, 10.00)");
        }

        assertThrows(IllegalStateException.class, () -> produtoService.excluir(salvo.getId()));
    }

    // ===== 6. INTEGRAÇÃO PDV-ESTOQUE =====

    @Test
    @DisplayName("6.1 - Ajuste de estoque reflete no PDV (listarTodos true)")
    void ajusteRefleteNoPDV() throws Exception {
        Produto salvo = produtoService.salvar(new Produto(null, "PDV001", "Produto PDV", "Caldos",
                new BigDecimal("19.90"), 10, null, true));

        // PDV lista apenas ativos
        List<Produto> produtosPDV = produtoService.listarTodos(true);
        Produto noPDV = produtosPDV.stream().filter(p -> p.getId().equals(salvo.getId())).findFirst().orElseThrow();
        assertEquals(10, noPDV.getEstoque());

        // Ajustar estoque (simula venda)
        produtoService.ajustarEstoque(salvo.getId(), -3, "Venda PDV", "VENDA", 999, 1);

        // Verificar que listarTodos reflete a mudança
        produtosPDV = produtoService.listarTodos(true);
        noPDV = produtosPDV.stream().filter(p -> p.getId().equals(salvo.getId())).findFirst().orElseThrow();
        assertEquals(7, noPDV.getEstoque(), "Estoque deve diminuir após venda");
    }

    @Test
    @DisplayName("6.2 - Produto inativado não aparece no PDV")
    void inativadoNaoApareceNoPDV() throws Exception {
        Produto salvo = produtoService.salvar(new Produto(null, "PDV002", "Inativo", "Caldos",
                new BigDecimal("19.90"), 10, null, true));

        salvo.setAtivo(false);
        produtoService.atualizar(salvo);

        List<Produto> produtosPDV = produtoService.listarTodos(true);
        boolean encontrado = produtosPDV.stream().anyMatch(p -> p.getId().equals(salvo.getId()));
        assertFalse(encontrado, "Produto inativo não deve aparecer no PDV");
    }

    @Test
    @DisplayName("6.3 - Gateway real do PDV usa mesma fonte de dados")
    void gatewayPDVUsaMesmaFonte() throws Exception {
        Produto salvo = produtoService.salvar(new Produto(null, "PDV003", "Via Gateway", "Caldos",
                new BigDecimal("25.00"), 15, null, true));

        // Gateway real lista produtos (retorna ProdutoPdv)
        List<ProdutoPdv> viaGateway = pdvGateway.listarProdutos();
        ProdutoPdv noGateway = viaGateway.stream().filter(p -> p.id() == salvo.getId()).findFirst().orElseThrow();
        assertEquals(15, noGateway.estoque());

        // Ajustar via service
        produtoService.ajustarEstoque(salvo.getId(), -5, "Venda", "VENDA", 1, 1);

        // Gateway reflete mudança
        viaGateway = pdvGateway.listarProdutos();
        noGateway = viaGateway.stream().filter(p -> p.id() == salvo.getId()).findFirst().orElseThrow();
        assertEquals(10, noGateway.estoque());
    }

    // ===== 7. RELATÓRIO DE BAIXO ESTOQUE =====

    @Test
    @DisplayName("7.1 - Identificar produtos zerados")
    void identificarZerados() throws Exception {
        produtoService.salvar(new Produto(null, "REL001", "Zero", "Teste", new BigDecimal("10.00"), 0, null, true));
        produtoService.salvar(new Produto(null, "REL002", "Com Estoque", "Teste", new BigDecimal("10.00"), 10, null, true));
        produtoService.salvar(new Produto(null, "REL003", "Zero Inativo", "Teste", new BigDecimal("10.00"), 0, null, false));

        List<Produto> todos = produtoService.listarTodos(false);
        long zeradosAtivos = todos.stream().filter(p -> p.getEstoque() == 0 && p.isAtivo()).count();
        long zeradosTotal = todos.stream().filter(p -> p.getEstoque() == 0).count();

        assertEquals(1, zeradosAtivos);
        assertEquals(2, zeradosTotal);
    }

    @Test
    @DisplayName("7.2 - Identificar produtos abaixo do mínimo (≤5)")
    void identificarBaixoEstoque() throws Exception {
        produtoService.salvar(new Produto(null, "REL004", "Estoque 1", "Teste", new BigDecimal("10.00"), 1, null, true));
        produtoService.salvar(new Produto(null, "REL005", "Estoque 5", "Teste", new BigDecimal("10.00"), 5, null, true));
        produtoService.salvar(new Produto(null, "REL006", "Estoque 6", "Teste", new BigDecimal("10.00"), 6, null, true));

        List<Produto> todos = produtoService.listarTodos(false);
        long baixo = todos.stream().filter(p -> p.getEstoque() > 0 && p.getEstoque() <= 5).count();
        long normal = todos.stream().filter(p -> p.getEstoque() > 5).count();

        assertEquals(2, baixo);
        assertEquals(1, normal);
    }
}