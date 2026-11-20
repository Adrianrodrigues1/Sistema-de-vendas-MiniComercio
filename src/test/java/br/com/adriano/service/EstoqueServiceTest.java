package br.com.adriano.service;

import br.com.adriano.model.Produto;
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
 * Testes específicos para funcionalidades de Estoque (Fase 3).
 * <p>
 * Cobre: histórico de movimentos, relatório de baixo estoque, importação/exportação CSV,
 * integração PDV-Estoque, validações de borda.
 */
@DisplayName("EstoqueService - funcionalidades Fase 3: estoque, auditoria, CSV")
class EstoqueServiceTest extends BaseServiceTest {

    private ProdutoService service;

    @BeforeEach
    @Override
    void setUp() throws Exception {
        super.setUp();
        service = new ProdutoService(getConexaoManager());
    }

    // ===== HISTÓRICO DE MOVIMENTOS (AUDITORIA) =====

    @Test
    @DisplayName("Deve registrar movimento de ENTRADA com todos os campos")
    void registrarMovimentoEntrada() throws Exception {
        Produto p = new Produto(null, "EST001", "Produto Teste", "Teste",
                new BigDecimal("10.00"), 50, null, true);
        Produto salvo = service.salvar(p);

        service.ajustarEstoque(salvo.getId(), 25, "Compra fornecedor XYZ", "COMPRA", 1001, 2);

        // Verificar estoque atualizado
        assertEquals(75, service.buscarPorId(salvo.getId()).get().getEstoque());

        // Verificar registro em movimentos_estoque
        try (Connection conn = getConexaoManager().conectar();
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
            assertFalse(rs.next(), "Deve ter apenas um movimento");
        }
    }

    @Test
    @DisplayName("Deve registrar movimento de SAÍDA com todos os campos")
    void registrarMovimentoSaida() throws Exception {
        Produto p = new Produto(null, "EST002", "Produto Saída", "Teste",
                new BigDecimal("10.00"), 100, null, true);
        Produto salvo = service.salvar(p);

        service.ajustarEstoque(salvo.getId(), -30, "Venda balcão", "VENDA", 2001, 1);

        assertEquals(70, service.buscarPorId(salvo.getId()).get().getEstoque());

        try (Connection conn = getConexaoManager().conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT tipo, quantidade, quantidade_anterior, quantidade_nova, motivo FROM movimentos_estoque WHERE produto_id = " + salvo.getId())) {
            assertTrue(rs.next());
            assertEquals("SAIDA", rs.getString("tipo"));
            assertEquals(30, rs.getInt("quantidade")); // quantidade é sempre positiva
            assertEquals(100, rs.getInt("quantidade_anterior"));
            assertEquals(70, rs.getInt("quantidade_nova"));
            assertEquals("Venda balcão", rs.getString("motivo"));
        }
    }

    @Test
    @DisplayName("Deve registrar movimento de AJUSTE absoluto")
    void registrarMovimentoAjusteAbsoluto() throws Exception {
        Produto p = new Produto(null, "EST003", "Produto Ajuste", "Teste",
                new BigDecimal("10.00"), 100, null, true);
        Produto salvo = service.salvar(p);

        // Ajuste para 42 (delta = -58)
        service.ajustarEstoque(salvo.getId(), -58, "Inventário físico", "AJUSTE_MANUAL", null, 1);

        assertEquals(42, service.buscarPorId(salvo.getId()).get().getEstoque());

        try (Connection conn = getConexaoManager().conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT tipo, quantidade, quantidade_anterior, quantidade_nova, motivo, referencia_tipo, referencia_id FROM movimentos_estoque WHERE produto_id = " + salvo.getId())) {
            assertTrue(rs.next());
            assertEquals("SAIDA", rs.getString("tipo")); // delta negativo = SAIDA
            assertEquals(58, rs.getInt("quantidade"));
            assertEquals(100, rs.getInt("quantidade_anterior"));
            assertEquals(42, rs.getInt("quantidade_nova"));
            assertEquals("Inventário físico", rs.getString("motivo"));
            assertEquals("AJUSTE_MANUAL", rs.getString("referencia_tipo"));
            assertNull(rs.getString("referencia_id"));
        }
    }

    @Test
    @DisplayName("Deve manter histórico sequencial de múltiplos movimentos")
    void historicoMultiplosMovimentos() throws Exception {
        Produto p = new Produto(null, "EST004", "Produto Histórico", "Teste",
                new BigDecimal("10.00"), 100, null, true);
        Produto salvo = service.salvar(p);

        service.ajustarEstoque(salvo.getId(), 10, "Entrada 1", "COMPRA", 1, 1);
        service.ajustarEstoque(salvo.getId(), -5, "Saída 1", "VENDA", 2, 1);
        service.ajustarEstoque(salvo.getId(), 20, "Entrada 2", "COMPRA", 3, 2);

        assertEquals(125, service.buscarPorId(salvo.getId()).get().getEstoque());

        try (Connection conn = getConexaoManager().conectar();
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
            assertEquals(3, count, "Deve ter 3 movimentos");
        }
    }

    @Test
    @DisplayName("Deve permitir ajuste sem motivo (validação é na UI, banco aceita NULL)")
    void permitirAjusteSemMotivo() throws Exception {
        Produto p = new Produto(null, "EST005", "Produto Sem Motivo", "Teste",
                new BigDecimal("10.00"), 100, null, true);
        Produto salvo = service.salvar(p);

        // O banco permite motivo NULL (coluna TEXT sem NOT NULL)
        // A validação de motivo obrigatório é feita na UI (PainelEstoque)
        assertDoesNotThrow(() -> service.ajustarEstoque(salvo.getId(), 10, null, "COMPRA", 1, 1));

        // Verificar que o movimento foi registrado com motivo NULL
        try (Connection conn = getConexaoManager().conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT motivo FROM movimentos_estoque WHERE produto_id = " + salvo.getId())) {
            assertTrue(rs.next());
            assertNull(rs.getString("motivo"), "Motivo deve ser NULL quando não informado");
        }
    }

    // ===== RELATÓRIO DE BAIXO ESTOQUE =====

    @Test
    @DisplayName("Deve identificar produtos zerados")
    void identificarProdutosZerados() throws Exception {
        service.salvar(new Produto(null, "ZER001", "Produto Zero", "Teste", new BigDecimal("10.00"), 0, null, true));
        service.salvar(new Produto(null, "ZER002", "Produto Com Estoque", "Teste", new BigDecimal("10.00"), 10, null, true));
        service.salvar(new Produto(null, "ZER003", "Outro Zero", "Teste", new BigDecimal("10.00"), 0, null, false)); // inativo

        List<Produto> todos = service.listarTodos(false);
        long zerados = todos.stream().filter(prod -> prod.getEstoque() == 0).count();
        long zeradosAtivos = todos.stream().filter(prod -> prod.getEstoque() == 0 && prod.isAtivo()).count();

        assertEquals(2, zerados, "Total de zerados (inclui inativos)");
        assertEquals(1, zeradosAtivos, "Apenas 1 zerado ativo");
    }

    @Test
    @DisplayName("Deve identificar produtos abaixo do mínimo (≤5)")
    void identificarProdutosBaixoEstoque() throws Exception {
        service.salvar(new Produto(null, "BAX001", "Estoque 1", "Teste", new BigDecimal("10.00"), 1, null, true));
        service.salvar(new Produto(null, "BAX002", "Estoque 5", "Teste", new BigDecimal("10.00"), 5, null, true));
        service.salvar(new Produto(null, "BAX003", "Estoque 6", "Teste", new BigDecimal("10.00"), 6, null, true));
        service.salvar(new Produto(null, "BAX004", "Estoque 0", "Teste", new BigDecimal("10.00"), 0, null, true));

        List<Produto> todos = service.listarTodos(false);
        long baixo = todos.stream().filter(prod -> prod.getEstoque() > 0 && prod.getEstoque() <= 5).count();
        long zerados = todos.stream().filter(prod -> prod.getEstoque() == 0).count();
        long normal = todos.stream().filter(prod -> prod.getEstoque() > 5).count();

        assertEquals(2, baixo, "Produtos com 1 e 5 unidades");
        assertEquals(1, zerados, "Produto zerado");
        assertEquals(1, normal, "Produto com 6 unidades");
    }

    // ===== IMPORTAÇÃO/EXPORTAÇÃO CSV =====

    @Test
    @DisplayName("Deve importar produtos novos via CSV")
    void importarCSVNovos() throws Exception {
        // Simular importação usando o service diretamente
        String[][] dados = {
                {"IMP001", "Importado 1", "Importados", "25.50", "10", "", "true"},
                {"IMP002", "Importado 2", "Importados", "30.00", "5", "img.jpg", "false"}
        };

        int importados = 0;
        for (String[] v : dados) {
            Produto p = new Produto(null, v[0], v[1], v[2],
                    new BigDecimal(v[3]), Integer.parseInt(v[4]),
                    v[5].isEmpty() ? null : v[5],
                    Boolean.parseBoolean(v[6]));
            service.salvar(p);
            importados++;
        }

        assertEquals(2, importados);
        assertEquals("Importado 1", service.buscarPorCodigo("IMP001").get().getNome());
        assertEquals(new BigDecimal("25.50"), service.buscarPorCodigo("IMP001").get().getPrecoBigDecimal());
        assertEquals(10, service.buscarPorCodigo("IMP001").get().getEstoque());
        assertTrue(service.buscarPorCodigo("IMP001").get().isAtivo());
        assertFalse(service.buscarPorCodigo("IMP002").get().isAtivo());
    }

    @Test
    @DisplayName("Deve atualizar produtos existentes via CSV (upsert por código)")
    void importarCSVAtualizar() throws Exception {
        // Produto existente
        Produto existente = new Produto(null, "UPS001", "Original", "Teste",
                new BigDecimal("10.00"), 100, null, true);
        service.salvar(existente);

        // "Importar" com mesmo código - deve atualizar
        Produto atualizado = new Produto(null, "UPS001", "Atualizado", "Nova Categoria",
                new BigDecimal("20.00"), 50, "nova.jpg", false);
        // Simular lógica de upsert do PainelEstoque
        var opt = service.buscarPorCodigo("UPS001");
        if (opt.isPresent()) {
            Produto p = opt.get();
            p.setNome(atualizado.getNome());
            p.setCategoria(atualizado.getCategoria());
            p.setPrecoBigDecimal(atualizado.getPrecoBigDecimal());
            p.setEstoque(atualizado.getEstoque());
            p.setImagem(atualizado.getImagem());
            p.setAtivo(atualizado.isAtivo());
            service.atualizar(p);
        }

        Produto verificado = service.buscarPorCodigo("UPS001").get();
        assertEquals("Atualizado", verificado.getNome());
        assertEquals("Nova Categoria", verificado.getCategoria());
        assertEquals(new BigDecimal("20.00"), verificado.getPrecoBigDecimal());
        assertEquals(50, verificado.getEstoque());
        assertEquals("nova.jpg", verificado.getImagem());
        assertFalse(verificado.isAtivo());
    }

    @Test
    @DisplayName("Deve exportar todos os produtos para formato CSV válido")
    void exportarCSV() throws Exception {
        service.salvar(new Produto(null, "EXP001", "Exportado 1", "Cat1", new BigDecimal("15.50"), 100, "img1.jpg", true));
        service.salvar(new Produto(null, "EXP002", "Exportado 2", "Cat2", new BigDecimal("25.00"), 50, null, false));

        List<Produto> produtos = service.listarTodos(false);

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

    // ===== INTEGRAÇÃO PDV-ESTOQUE =====

    @Test
    @DisplayName("Ajuste de estoque deve refletir no PDV (listarProdutos)")
    void ajusteEstoqueRefleteNoPDV() throws Exception {
        Produto p = new Produto(null, "PDV001", "Produto PDV", "Caldos",
                new BigDecimal("19.90"), 10, null, true);
        Produto salvo = service.salvar(p);

        // Simular gateway real listando produtos
        List<Produto> produtosPDV = service.listarTodos(true); // apenas ativos
        Produto noPDV = produtosPDV.stream().filter(prod -> prod.getId().equals(salvo.getId())).findFirst().orElseThrow();
        assertEquals(10, noPDV.getEstoque());

        // Ajustar estoque
        service.ajustarEstoque(salvo.getId(), -3, "Venda PDV", "VENDA", 999, 1);

        // Verificar que listarTodos reflete a mudança
        produtosPDV = service.listarTodos(true);
        noPDV = produtosPDV.stream().filter(prod -> prod.getId().equals(salvo.getId())).findFirst().orElseThrow();
        assertEquals(7, noPDV.getEstoque(), "Estoque deve diminuir após venda");
    }

    @Test
    @DisplayName("Produto inativado não deve aparecer no PDV")
    void produtoInativadoNaoApareceNoPDV() throws Exception {
        Produto p = new Produto(null, "PDV002", "Produto Inativo", "Caldos",
                new BigDecimal("19.90"), 10, null, true);
        Produto salvo = service.salvar(p);

        // Inativar
        salvo.setAtivo(false);
        service.atualizar(salvo);

        // listarTodos(true) = apenas ativos para PDV
        List<Produto> produtosPDV = service.listarTodos(true);
        boolean encontrado = produtosPDV.stream().anyMatch(prod -> prod.getId().equals(salvo.getId()));
        assertFalse(encontrado, "Produto inativo não deve aparecer no PDV");
    }

    // ===== VALIDAÇÕES DE BORDA =====

    @Test
    @DisplayName("Deve rejeitar código duplicado na importação/criação")
    void rejeitarCodigoDuplicado() throws Exception {
        Produto p1 = new Produto(null, "DUP001", "Produto 1", "Teste", new BigDecimal("10.00"), 10, null, true);
        service.salvar(p1);

        Produto p2 = new Produto(null, "DUP001", "Produto 2", "Teste", new BigDecimal("20.00"), 20, null, true);
        assertThrows(Exception.class, () -> service.salvar(p2), "Código duplicado deve lançar exceção");
    }

    @Test
    @DisplayName("Deve rejeitar estoque insuficiente em ajuste de saída")
    void rejeitarEstoqueInsuficienteSaida() throws Exception {
        Produto p = new Produto(null, "INS001", "Estoque Baixo", "Teste", new BigDecimal("10.00"), 5, null, true);
        Produto salvo = service.salvar(p);

        assertThrows(IllegalStateException.class,
                () -> service.ajustarEstoque(salvo.getId(), -10, "Tentativa saída", "VENDA", 1, 1));
    }

    @Test
    @DisplayName("Deve permitir ajuste que zere o estoque")
    void permitirZerarEstoque() throws Exception {
        Produto p = new Produto(null, "ZER004", "Para Zerar", "Teste", new BigDecimal("10.00"), 10, null, true);
        Produto salvo = service.salvar(p);

        service.ajustarEstoque(salvo.getId(), -10, "Zerar estoque", "AJUSTE_MANUAL", null, 1);

        assertEquals(0, service.buscarPorId(salvo.getId()).get().getEstoque());
    }

    @Test
    @DisplayName("Deve validar campos obrigatórios: código, nome, preço >= 0")
    void validarCamposObrigatorios() {
        // Código obrigatório
        assertThrows(IllegalArgumentException.class, () ->
                service.salvar(new Produto(null, "", "Nome", "Cat", new BigDecimal("10.00"), 10, null, true)));

        // Nome obrigatório
        assertThrows(IllegalArgumentException.class, () ->
                service.salvar(new Produto(null, "COD", "", "Cat", new BigDecimal("10.00"), 10, null, true)));

        // Preço negativo
        assertThrows(IllegalArgumentException.class, () ->
                service.salvar(new Produto(null, "COD", "Nome", "Cat", new BigDecimal("-1.00"), 10, null, true)));

        // Estoque negativo
        assertThrows(IllegalArgumentException.class, () ->
                service.salvar(new Produto(null, "COD", "Nome", "Cat", new BigDecimal("10.00"), -1, null, true)));
    }

    @Test
    @DisplayName("Deve buscar produtos por termo case-insensitive (ASCII)")
    void buscarPorTermoCaseInsensitive() throws Exception {
        service.salvar(new Produto(null, "TER001", "Caldo Verde", "Caldos", new BigDecimal("15.00"), 10, null, true));
        service.salvar(new Produto(null, "TER002", "CALDO DE FEIJAO", "Caldos", new BigDecimal("18.00"), 10, null, true));
        service.salvar(new Produto(null, "TER003", "Refrigerante", "Bebidas", new BigDecimal("6.00"), 10, null, true));

        List<Produto> porCaldo = service.buscarPorTermo("caldo"); // minúsculo
        List<Produto> porCALDO = service.buscarPorTermo("CALDO"); // maiúsculo
        List<Produto> porFeijao = service.buscarPorTermo("feijao"); // sem acento (SQLite LIKE não é accent-insensitive por padrão)

        assertEquals(2, porCaldo.size(), "Busca case-insensitive 'caldo'");
        assertEquals(2, porCALDO.size(), "Busca case-insensitive 'CALDO'");
        assertEquals(1, porFeijao.size(), "Busca 'feijao' encontra 'FEIJAO'");
    }
}