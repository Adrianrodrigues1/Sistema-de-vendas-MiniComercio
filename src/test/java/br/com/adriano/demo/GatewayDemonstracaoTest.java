package br.com.adriano.demo;

import br.com.adriano.ui.pdv.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GatewayDemonstracao - simulação em memória")
class GatewayDemonstracaoTest {

    private GatewayDemonstracao gateway;

    @BeforeEach
    void setup() {
        gateway = new GatewayDemonstracao();
    }

    @Nested
    @DisplayName("Listar produtos")
    class ListarProdutos {
        @Test
        @DisplayName("Deve retornar lista imutável de produtos demonstrativos")
        void listaImutavel() {
            List<ProdutoPdv> produtos = gateway.listarProdutos();
            assertEquals(8, produtos.size()); // 8 produtos do construtor

            // Verificar se lista é cópia (imutável)
            assertThrows(UnsupportedOperationException.class, () -> produtos.add(null));
        }

        @Test
        @DisplayName("Deve conter produtos com categorias corretas")
        void categoriasCorretas() {
            List<ProdutoPdv> produtos = gateway.listarProdutos();
            long caldos = produtos.stream().filter(p -> p.categoria().equals("Caldos")).count();
            long bebidas = produtos.stream().filter(p -> p.categoria().equals("Bebidas")).count();
            long acompanhamentos = produtos.stream().filter(p -> p.categoria().equals("Acompanhamentos")).count();
            long outros = produtos.stream().filter(p -> p.categoria().equals("Outros")).count();

            assertEquals(4, caldos);
            assertEquals(1, bebidas);
            assertEquals(2, acompanhamentos);
            assertEquals(1, outros);
        }
    }

    @Nested
    @DisplayName("Concluir venda")
    class ConcluirVenda {
        @Test
        @DisplayName("Deve processar venda simples e baixar estoque")
        void vendaSimples() {
            // Pegar um produto
            ProdutoPdv caldo = gateway.listarProdutos().stream()
                .filter(p -> p.codigo().equals("001"))
                .findFirst().orElseThrow();
            int estoqueInicial = caldo.estoque();

            Carrinho.Item item = new Carrinho.Item(caldo, 2);
            PdvGateway.Pedido pedido = new PdvGateway.Pedido(
                List.of(item),
                BigDecimal.ZERO,
                Pagamento.calcular(Pagamento.Forma.DINHEIRO, caldo.preco().multiply(BigDecimal.valueOf(2)), caldo.preco().multiply(BigDecimal.valueOf(2))),
                null
            );

            PdvGateway.Resultado resultado = gateway.concluirVenda(pedido);

            assertNotNull(resultado.identificador());
            assertTrue(resultado.identificador().startsWith("DEMO-"));
            assertEquals(estoqueInicial - 2, gateway.listarProdutos().stream()
                .filter(p -> p.id() == caldo.id()).findFirst().orElseThrow().estoque());
        }

        @Test
        @DisplayName("Deve somar quantidades do mesmo produto no pedido")
        void somarQuantidadesMesmoProduto() {
            ProdutoPdv caldo = gateway.listarProdutos().stream()
                .filter(p -> p.codigo().equals("001"))
                .findFirst().orElseThrow();
            int estoqueInicial = caldo.estoque();

            // Dois itens do mesmo produto no carrinho
            Carrinho.Item item1 = new Carrinho.Item(caldo, 1);
            Carrinho.Item item2 = new Carrinho.Item(caldo, 2); // mesmo produto, quantidade diferente
            PdvGateway.Pedido pedido = new PdvGateway.Pedido(
                List.of(item1, item2),
                BigDecimal.ZERO,
                Pagamento.calcular(Pagamento.Forma.DINHEIRO, caldo.preco().multiply(BigDecimal.valueOf(3)), caldo.preco().multiply(BigDecimal.valueOf(3))),
                null
            );

            gateway.concluirVenda(pedido);

            // Deve ter baixado 3 no total
            assertEquals(estoqueInicial - 3, gateway.listarProdutos().stream()
                .filter(p -> p.id() == caldo.id()).findFirst().orElseThrow().estoque());
        }

        @Test
        @DisplayName("Deve rejeitar venda com quantidade acima do estoque no gateway")
        void rejeitarEstoqueInsuficiente() {
            ProdutoPdv caldo = gateway.listarProdutos().stream()
                .filter(p -> p.codigo().equals("001"))
                .findFirst().orElseThrow();
            int estoque = caldo.estoque();

            // Carrinho.Item permite quantidade acima do estoque (validação é no gateway)
            Carrinho.Item item = new Carrinho.Item(caldo, estoque + 1);
            PdvGateway.Pedido pedido = new PdvGateway.Pedido(
                List.of(item),
                BigDecimal.ZERO,
                Pagamento.calcular(Pagamento.Forma.DINHEIRO, caldo.preco().multiply(BigDecimal.valueOf(estoque + 1)), caldo.preco().multiply(BigDecimal.valueOf(estoque + 1))),
                null
            );

            // Gateway deve rejeitar por estoque insuficiente
            assertThrows(IllegalArgumentException.class, () -> gateway.concluirVenda(pedido));
        }

        @Test
        @DisplayName("Deve rejeitar carrinho vazio no construtor do Pedido")
        void rejeitarCarrinhoVazio() {
            assertThrows(IllegalArgumentException.class, () -> new PdvGateway.Pedido(
                List.of(),
                BigDecimal.ZERO,
                Pagamento.calcular(Pagamento.Forma.DINHEIRO, BigDecimal.ZERO, BigDecimal.ZERO),
                null
            ));
        }

        @Test
        @DisplayName("Deve validar preço do produto no momento da venda")
        void validarPrecoNaVenda() {
            ProdutoPdv caldo = gateway.listarProdutos().stream()
                .filter(p -> p.codigo().equals("001"))
                .findFirst().orElseThrow();

            // Criar item com preço diferente (simulando produto alterado)
            ProdutoPdv caldoAlterado = new ProdutoPdv(caldo.id(), caldo.codigo(), caldo.nome(), caldo.categoria(),
                caldo.preco().add(BigDecimal.ONE), caldo.estoque());
            Carrinho.Item item = new Carrinho.Item(caldoAlterado, 1);
            PdvGateway.Pedido pedido = new PdvGateway.Pedido(
                List.of(item),
                BigDecimal.ZERO,
                Pagamento.calcular(Pagamento.Forma.DINHEIRO, caldoAlterado.preco(), caldoAlterado.preco()),
                null
            );

            assertThrows(IllegalArgumentException.class, () -> gateway.concluirVenda(pedido));
        }

        @Test
        @DisplayName("Deve processar venda com desconto")
        void vendaComDesconto() {
            ProdutoPdv caldo = gateway.listarProdutos().stream()
                .filter(p -> p.codigo().equals("001"))
                .findFirst().orElseThrow();

            BigDecimal subtotal = caldo.preco().multiply(BigDecimal.valueOf(2)); // 38.80
            BigDecimal desconto = new BigDecimal("3.80");
            BigDecimal total = subtotal.subtract(desconto); // 35.00

            Carrinho.Item item = new Carrinho.Item(caldo, 2);
            PdvGateway.Pedido pedido = new PdvGateway.Pedido(
                List.of(item),
                desconto,
                Pagamento.calcular(Pagamento.Forma.DINHEIRO, total, total),
                null
            );

            PdvGateway.Resultado resultado = gateway.concluirVenda(pedido);
            assertNotNull(resultado.identificador());
        }

        @Test
        @DisplayName("Deve validar totais do pedido no construtor (subtotal - desconto = total do pagamento)")
        void validarTotaisPedido() {
            ProdutoPdv caldo = gateway.listarProdutos().stream()
                .filter(p -> p.codigo().equals("001"))
                .findFirst().orElseThrow();

            Carrinho.Item item = new Carrinho.Item(caldo, 2);
            BigDecimal totalErrado = caldo.preco().multiply(BigDecimal.valueOf(3)); // errado

            assertThrows(IllegalArgumentException.class, () -> new PdvGateway.Pedido(
                List.of(item),
                BigDecimal.ZERO,
                Pagamento.calcular(Pagamento.Forma.DINHEIRO, totalErrado, totalErrado),
                null
            ));
        }
    }

    @Nested
    @DisplayName("Isolamento entre instâncias")
    class Isolamento {
        @Test
        @DisplayName("Cada instância deve ter seu próprio estoque")
        void isolamentoEstoque() {
            GatewayDemonstracao g1 = new GatewayDemonstracao();
            GatewayDemonstracao g2 = new GatewayDemonstracao();

            ProdutoPdv p1 = g1.listarProdutos().stream().filter(p -> p.codigo().equals("001")).findFirst().orElseThrow();
            ProdutoPdv p2 = g2.listarProdutos().stream().filter(p -> p.codigo().equals("001")).findFirst().orElseThrow();

            assertEquals(p1.estoque(), p2.estoque());

            // Venda em g1 não afeta g2
            Carrinho.Item item = new Carrinho.Item(p1, 2);
            PdvGateway.Pedido pedido = new PdvGateway.Pedido(
                List.of(item),
                BigDecimal.ZERO,
                Pagamento.calcular(Pagamento.Forma.DINHEIRO, p1.preco().multiply(BigDecimal.valueOf(2)), p1.preco().multiply(BigDecimal.valueOf(2))),
                null
            );
            g1.concluirVenda(pedido);

            assertEquals(p1.estoque() - 2, g1.listarProdutos().stream().filter(p -> p.codigo().equals("001")).findFirst().orElseThrow().estoque());
            assertEquals(p2.estoque(), g2.listarProdutos().stream().filter(p -> p.codigo().equals("001")).findFirst().orElseThrow().estoque());
        }
    }
}