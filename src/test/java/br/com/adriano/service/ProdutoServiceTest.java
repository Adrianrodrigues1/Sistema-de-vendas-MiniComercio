package br.com.adriano.service;

import br.com.adriano.model.Produto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProdutoService - regras de negócio de produtos")
class ProdutoServiceTest extends BaseServiceTest {

    private ProdutoService service;

    @BeforeEach
    @Override
    void setUp() throws Exception {
        super.setUp();
        service = new ProdutoService(getConexaoManager());
    }

    @Test
    @DisplayName("Deve salvar produto com todos os campos v2.0")
    void salvarProdutoCompleto() throws Exception {
        Produto p = new Produto(null, "CVD001", "Caldo Verde", "Caldos",
                new BigDecimal("15.50"), 100, "caldo_verde.jpg", true);

        Produto salvo = service.salvar(p);

        assertNotNull(salvo.getId());
        assertEquals("CVD001", salvo.getCodigo());
        assertEquals("Caldo Verde", salvo.getNome());
        assertEquals("Caldos", salvo.getCategoria());
        assertEquals(new BigDecimal("15.50"), salvo.getPrecoBigDecimal());
        assertEquals(100, salvo.getEstoque());
        assertEquals("caldo_verde.jpg", salvo.getImagem());
        assertTrue(salvo.isAtivo());
    }

    @Test
    @DisplayName("Deve rejeitar produto sem código")
    void rejeitarSemCodigo() {
        Produto p = new Produto(null, null, "Caldo Verde", "Caldos",
                new BigDecimal("15.50"), 100, null, true);

        assertThrows(IllegalArgumentException.class, () -> service.salvar(p));
    }

    @Test
    @DisplayName("Deve rejeitar produto sem nome")
    void rejeitarSemNome() {
        Produto p = new Produto(null, "CVD001", null, "Caldos",
                new BigDecimal("15.50"), 100, null, true);

        assertThrows(IllegalArgumentException.class, () -> service.salvar(p));
    }

    @Test
    @DisplayName("Deve rejeitar preço negativo")
    void rejeitarPrecoNegativo() {
        Produto p = new Produto(null, "CVD001", "Caldo Verde", "Caldos",
                new BigDecimal("-5.00"), 100, null, true);

        assertThrows(IllegalArgumentException.class, () -> service.salvar(p));
    }

    @Test
    @DisplayName("Deve rejeitar estoque negativo")
    void rejeitarEstoqueNegativo() {
        Produto p = new Produto(null, "CVD001", "Caldo Verde", "Caldos",
                new BigDecimal("15.50"), -10, null, true);

        assertThrows(IllegalArgumentException.class, () -> service.salvar(p));
    }

    @Test
    @DisplayName("Deve atualizar produto existente")
    void atualizarProduto() throws Exception {
        Produto p = new Produto(null, "CVD001", "Caldo Verde", "Caldos",
                new BigDecimal("15.50"), 100, null, true);
        Produto salvo = service.salvar(p);

        salvo.setNome("Caldo Verde Especial");
        salvo.setPrecoBigDecimal(new BigDecimal("18.00"));
        salvo.setEstoque(80);
        salvo.setAtivo(false);

        Produto atualizado = service.atualizar(salvo);

        assertEquals("Caldo Verde Especial", atualizado.getNome());
        assertEquals(new BigDecimal("18.00"), atualizado.getPrecoBigDecimal());
        assertEquals(80, atualizado.getEstoque());
        assertFalse(atualizado.isAtivo());
    }

    @Test
    @DisplayName("Deve buscar por ID")
    void buscarPorId() throws Exception {
        Produto p = new Produto(null, "CVD001", "Caldo Verde", "Caldos",
                new BigDecimal("15.50"), 100, null, true);
        Produto salvo = service.salvar(p);

        Optional<Produto> encontrado = service.buscarPorId(salvo.getId());

        assertTrue(encontrado.isPresent());
        assertEquals("CVD001", encontrado.get().getCodigo());
    }

    @Test
    @DisplayName("Deve buscar por código")
    void buscarPorCodigo() throws Exception {
        Produto p = new Produto(null, "CVD001", "Caldo Verde", "Caldos",
                new BigDecimal("15.50"), 100, null, true);
        service.salvar(p);

        Optional<Produto> encontrado = service.buscarPorCodigo("CVD001");

        assertTrue(encontrado.isPresent());
        assertEquals("Caldo Verde", encontrado.get().getNome());
    }

    @Test
    @DisplayName("Deve listar todos os produtos")
    void listarTodos() throws Exception {
        service.salvar(new Produto(null, "CVD001", "Caldo Verde", "Caldos", new BigDecimal("15.50"), 100, null, true));
        service.salvar(new Produto(null, "CVD002", "Caldo de Feijão", "Caldos", new BigDecimal("18.00"), 50, null, true));
        service.salvar(new Produto(null, "REF001", "Refrigerante", "Bebidas", new BigDecimal("6.00"), 200, null, false)); // inativo

        List<Produto> todos = service.listarTodos(false);
        List<Produto> ativos = service.listarTodos(true);

        assertEquals(3, todos.size());
        assertEquals(2, ativos.size());
    }

    @Test
    @DisplayName("Deve buscar por categoria")
    void buscarPorCategoria() throws Exception {
        service.salvar(new Produto(null, "CVD001", "Caldo Verde", "Caldos", new BigDecimal("15.50"), 100, null, true));
        service.salvar(new Produto(null, "CVD002", "Caldo de Feijão", "Caldos", new BigDecimal("18.00"), 50, null, true));
        service.salvar(new Produto(null, "REF001", "Refrigerante", "Bebidas", new BigDecimal("6.00"), 200, null, true));

        List<Produto> caldos = service.buscarPorCategoria("Caldos");
        List<Produto> bebidas = service.buscarPorCategoria("Bebidas");

        assertEquals(2, caldos.size());
        assertEquals(1, bebidas.size());
    }

    @Test
    @DisplayName("Deve buscar por termo (nome ou código)")
    void buscarPorTermo() throws Exception {
        service.salvar(new Produto(null, "CVD001", "Caldo Verde", "Caldos", new BigDecimal("15.50"), 100, null, true));
        service.salvar(new Produto(null, "CVD002", "Caldo de Feijão", "Caldos", new BigDecimal("18.00"), 50, null, true));
        service.salvar(new Produto(null, "REF001", "Refrigerante Lata", "Bebidas", new BigDecimal("6.00"), 200, null, true));

        List<Produto> porVerde = service.buscarPorTermo("Verde");
        List<Produto> porCVD = service.buscarPorTermo("CVD");
        List<Produto> porLata = service.buscarPorTermo("Lata");

        assertEquals(1, porVerde.size());
        assertEquals(2, porCVD.size());
        assertEquals(1, porLata.size());
    }

    @Test
    @DisplayName("Deve listar categorias distintas (apenas produtos ativos)")
    void listarCategorias() throws Exception {
        service.salvar(new Produto(null, "CVD001", "Caldo Verde", "Caldos", new BigDecimal("15.50"), 100, null, true));
        service.salvar(new Produto(null, "CVD002", "Caldo de Feijão", "Caldos", new BigDecimal("18.00"), 50, null, true));
        service.salvar(new Produto(null, "REF001", "Refrigerante", "Bebidas", new BigDecimal("6.00"), 200, null, true));
        service.salvar(new Produto(null, "ACO001", "Acompanhamento", "Acompanhamentos", new BigDecimal("5.00"), 50, null, false)); // inativo - não deve aparecer

        List<String> categorias = service.listarCategorias();

        assertEquals(2, categorias.size(), "Apenas categorias de produtos ativos");
        assertTrue(categorias.contains("Caldos"));
        assertTrue(categorias.contains("Bebidas"));
        assertFalse(categorias.contains("Acompanhamentos"), "Categoria de produto inativo não deve aparecer");
    }

    @Test
    @DisplayName("Deve ajustar estoque com registro de movimento")
    void ajustarEstoque() throws Exception {
        Produto p = new Produto(null, "CVD001", "Caldo Verde", "Caldos",
                new BigDecimal("15.50"), 100, null, true);
        Produto salvo = service.salvar(p);

        // Entrada
        service.ajustarEstoque(salvo.getId(), 50, "Compra de fornecedor", "COMPRA", 123, 1);
        Optional<Produto> ap = service.buscarPorId(salvo.getId());
        assertEquals(150, ap.get().getEstoque());

        // Saída
        service.ajustarEstoque(salvo.getId(), -30, "Venda balcão", "VENDA", 456, 1);
        ap = service.buscarPorId(salvo.getId());
        assertEquals(120, ap.get().getEstoque());
    }

    @Test
    @DisplayName("Deve rejeitar ajuste que deixe estoque negativo")
    void rejeitarEstoqueNegativoAjuste() throws Exception {
        Produto p = new Produto(null, "CVD001", "Caldo Verde", "Caldos",
                new BigDecimal("15.50"), 10, null, true);
        Produto salvo = service.salvar(p);

        assertThrows(IllegalStateException.class,
                () -> service.ajustarEstoque(salvo.getId(), -20, "Teste", "AJUSTE", null, 1));
    }

    @Test
    @DisplayName("Não deve excluir produto com vendas associadas")
    void naoExcluirComVendas() throws Exception {
        Produto p = new Produto(null, "CVD001", "Caldo Verde", "Caldos",
                new BigDecimal("15.50"), 100, null, true);
        Produto salvo = service.salvar(p);

        // Simular venda associada (inserir direto no banco)
        try (Connection conn = getConexaoManager().conectar();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO vendas (id, data, forma_pagamento, total, status) VALUES (999, '2024-01-01', 'DINHEIRO', 15.50, 'FINALIZADA')");
            stmt.execute("INSERT INTO itens_venda (venda_id, produto_id, quantidade, preco_unitario, preco_total) VALUES (999, " + salvo.getId() + ", 1, 15.50, 15.50)");
        }

        assertThrows(IllegalStateException.class, () -> service.excluir(salvo.getId()));
    }

    @Test
    @DisplayName("Deve excluir produto sem vendas")
    void excluirSemVendas() throws Exception {
        Produto p = new Produto(null, "CVD001", "Caldo Verde", "Caldos",
                new BigDecimal("15.50"), 100, null, true);
        Produto salvo = service.salvar(p);

        assertTrue(service.excluir(salvo.getId()));
        assertTrue(service.buscarPorId(salvo.getId()).isEmpty());
    }
}