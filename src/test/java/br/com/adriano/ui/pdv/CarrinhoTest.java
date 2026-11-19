package br.com.adriano.ui.pdv;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Carrinho - lógica de negócio do PDV")
class CarrinhoTest {

    private ProdutoPdv caldoMocoto;
    private ProdutoPdv caldoFrango;
    private ProdutoPdv paoTostado;

    @BeforeEach
    void setup() {
        caldoMocoto = new ProdutoPdv(1, "001", "Caldo de Mocotó", "Caldos", new BigDecimal("19.40"), 16);
        caldoFrango = new ProdutoPdv(2, "002", "Caldo de Frango", "Caldos", new BigDecimal("16.80"), 24);
        paoTostado = new ProdutoPdv(10, "010", "Pão Torrado", "Acompanhamentos", new BigDecimal("4.30"), 32);
    }

    @Nested
    @DisplayName("Adicionar itens")
    class Adicionar {
        @Test
        @DisplayName("Deve adicionar produto ao carrinho vazio")
        void adicionarProdutoSimples() {
            Carrinho carrinho = new Carrinho();
            carrinho.adicionar(caldoMocoto, 2);

            assertEquals(1, carrinho.itens().size());
            assertEquals(2, carrinho.itens().getFirst().quantidade());
            assertEquals(new BigDecimal("38.80"), carrinho.subtotal());
        }

        @Test
        @DisplayName("Deve somar quantidades do mesmo produto adicionado múltiplas vezes")
        void somarQuantidadesMesmoProduto() {
            Carrinho carrinho = new Carrinho();
            carrinho.adicionar(caldoMocoto, 2);
            carrinho.adicionar(caldoMocoto, 3);

            assertEquals(1, carrinho.itens().size());
            assertEquals(5, carrinho.itens().getFirst().quantidade());
            assertEquals(new BigDecimal("97.00"), carrinho.subtotal());
        }

        @Test
        @DisplayName("Deve rejeitar quantidade zero ou negativa")
        void rejeitarQuantidadeInvalida() {
            Carrinho carrinho = new Carrinho();
            assertThrows(IllegalArgumentException.class, () -> carrinho.adicionar(caldoMocoto, 0));
            assertThrows(IllegalArgumentException.class, () -> carrinho.adicionar(caldoMocoto, -1));
        }

        @Test
        @DisplayName("Deve permitir adicionar quantidade acima do estoque (validação é no service)")
        void permitirQuantidadeAcimaEstoque() {
            Carrinho carrinho = new Carrinho();
            // Carrinho não valida mais estoque - a validação ocorre no VendaService
            carrinho.adicionar(caldoMocoto, 20);
            assertEquals(20, carrinho.itens().getFirst().quantidade());
        }
    }

    @Nested
    @DisplayName("Alterar quantidade")
    class AlterarQuantidade {
        @Test
        @DisplayName("Deve alterar quantidade de item existente")
        void alterarQuantidade() {
            Carrinho carrinho = new Carrinho();
            carrinho.adicionar(caldoMocoto, 2);
            carrinho.quantidade(caldoMocoto.id(), 5);

            assertEquals(5, carrinho.itens().getFirst().quantidade());
            assertEquals(new BigDecimal("97.00"), carrinho.subtotal());
        }

        @Test
        @DisplayName("Deve remover item ao definir quantidade zero")
        void removerAoDefinirZero() {
            Carrinho carrinho = new Carrinho();
            carrinho.adicionar(caldoMocoto, 2);
            carrinho.quantidade(caldoMocoto.id(), 0);

            assertTrue(carrinho.vazio());
            assertEquals(Dinheiro.ZERO, carrinho.subtotal());
        }

        @Test
        @DisplayName("Deve ajustar desconto ao reduzir subtotal")
        void ajustarDescontoAoReduzir() {
            Carrinho carrinho = new Carrinho();
            carrinho.adicionar(caldoMocoto, 5); // 97.00
            carrinho.desconto(new BigDecimal("10.00"));
            carrinho.quantidade(caldoMocoto.id(), 2); // 38.80

            assertEquals(new BigDecimal("38.80"), carrinho.subtotal());
            assertEquals(new BigDecimal("10.00"), carrinho.desconto()); // desconto não muda automaticamente
        }
    }

    @Nested
    @DisplayName("Remover itens")
    class Remover {
        @Test
        @DisplayName("Deve remover item específico")
        void removerItem() {
            Carrinho carrinho = new Carrinho();
            carrinho.adicionar(caldoMocoto, 2);
            carrinho.adicionar(paoTostado, 3);
            carrinho.remover(caldoMocoto.id());

            assertEquals(1, carrinho.itens().size());
            assertEquals(paoTostado.id(), carrinho.itens().getFirst().produto().id());
        }
    }

    @Nested
    @DisplayName("Desconto")
    class Desconto {
        @Test
        @DisplayName("Deve aplicar desconto válido")
        void aplicarDescontoValido() {
            Carrinho carrinho = new Carrinho();
            carrinho.adicionar(caldoMocoto, 2); // 38.80
            carrinho.desconto(new BigDecimal("5.00"));

            assertEquals(new BigDecimal("5.00"), carrinho.desconto());
            assertEquals(new BigDecimal("33.80"), carrinho.total());
        }

        @Test
        @DisplayName("Deve rejeitar desconto negativo")
        void rejeitarDescontoNegativo() {
            Carrinho carrinho = new Carrinho();
            carrinho.adicionar(caldoMocoto, 1);
            assertThrows(IllegalArgumentException.class, () -> carrinho.desconto(new BigDecimal("-1.00")));
        }

        @Test
        @DisplayName("Deve rejeitar desconto maior que subtotal")
        void rejeitarDescontoMaiorQueSubtotal() {
            Carrinho carrinho = new Carrinho();
            carrinho.adicionar(caldoMocoto, 1); // 19.40
            assertThrows(IllegalArgumentException.class, () -> carrinho.desconto(new BigDecimal("20.00")));
        }
    }

    @Nested
    @DisplayName("Totais")
    class Totais {
        @Test
        @DisplayName("Deve calcular subtotal, desconto e total corretamente")
        void calcularTotais() {
            Carrinho carrinho = new Carrinho();
            carrinho.adicionar(caldoMocoto, 2); // 38.80
            carrinho.adicionar(paoTostado, 1); // 4.30
            carrinho.desconto(new BigDecimal("3.10"));

            assertEquals(new BigDecimal("43.10"), carrinho.subtotal());
            assertEquals(new BigDecimal("3.10"), carrinho.desconto());
            assertEquals(new BigDecimal("40.00"), carrinho.total());
        }

        @Test
        @DisplayName("Deve retornar zero para carrinho vazio")
        void carrinhoVazioZeros() {
            Carrinho carrinho = new Carrinho();
            assertEquals(Dinheiro.ZERO, carrinho.subtotal());
            assertEquals(Dinheiro.ZERO, carrinho.desconto());
            assertEquals(Dinheiro.ZERO, carrinho.total());
            assertTrue(carrinho.vazio());
            assertEquals(0, carrinho.quantidade());
        }
    }

    @Nested
    @DisplayName("Limpar carrinho")
    class Limpar {
        @Test
        @DisplayName("Deve limpar todos os itens e desconto")
        void limparTudo() {
            Carrinho carrinho = new Carrinho();
            carrinho.adicionar(caldoMocoto, 2);
            carrinho.desconto(new BigDecimal("5.00"));
            carrinho.limpar();

            assertTrue(carrinho.vazio());
            assertEquals(Dinheiro.ZERO, carrinho.subtotal());
            assertEquals(Dinheiro.ZERO, carrinho.desconto());
            assertEquals(Dinheiro.ZERO, carrinho.total());
        }
    }

    @Nested
    @DisplayName("Busca de produtos")
    class Busca {
        private List<ProdutoPdv> produtos;

        @BeforeEach
        void setupProdutos() {
            produtos = List.of(
                caldoMocoto,
                caldoFrango,
                paoTostado,
                new ProdutoPdv(20, "020", "Refrigerante", "Bebidas", new BigDecimal("6.40"), 27),
                new ProdutoPdv(30, "030", "Embalagem (Pote)", "Outros", new BigDecimal("1.20"), 45)
            );
        }

        @Test
        @DisplayName("Deve buscar por nome ignorando maiúsculas/minúsculas")
        void buscarPorNomeCaseInsensitive() {
            var resultado = Carrinho.buscar(produtos, "mocotó", "Todos");
            assertEquals(1, resultado.size());
            assertEquals("Caldo de Mocotó", resultado.getFirst().nome());

            resultado = Carrinho.buscar(produtos, "MOCOTO", "Todos");
            assertEquals(1, resultado.size());
        }

        @Test
        @DisplayName("Deve buscar tolerante a acentos")
        void buscarToleranteAcentos() {
            var resultado = Carrinho.buscar(produtos, "mocoto", "Todos"); // sem acento
            assertEquals(1, resultado.size());
            assertEquals("Caldo de Mocotó", resultado.getFirst().nome());

            resultado = Carrinho.buscar(produtos, "frango", "Todos");
            assertEquals(1, resultado.size());
        }

        @Test
        @DisplayName("Deve buscar por código comercial")
        void buscarPorCodigo() {
            var resultado = Carrinho.buscar(produtos, "001", "Todos");
            assertEquals(1, resultado.size());
            assertEquals("001", resultado.getFirst().codigo());

            resultado = Carrinho.buscar(produtos, "010", "Todos");
            assertEquals(1, resultado.size());
        }

        @Test
        @DisplayName("Deve filtrar por categoria")
        void filtrarPorCategoria() {
            var caldos = Carrinho.buscar(produtos, "", "Caldos");
            assertEquals(2, caldos.size());

            var acompanhamentos = Carrinho.buscar(produtos, "", "Acompanhamentos");
            assertEquals(1, acompanhamentos.size());

            var todos = Carrinho.buscar(produtos, "", "Todos");
            assertEquals(5, todos.size());
        }

        @Test
        @DisplayName("Deve combinar busca textual e categoria")
        void combinarBuscaECategoria() {
            var resultado = Carrinho.buscar(produtos, "caldo", "Caldos");
            assertEquals(2, resultado.size());

            resultado = Carrinho.buscar(produtos, "caldo", "Bebidas");
            assertTrue(resultado.isEmpty());
        }

        @Test
        @DisplayName("Deve manter ordem de inserção quando não há filtro (LinkedHashMap preserva ordem)")
        void manterOrdemInsercaoSemFiltro() {
            var resultado = Carrinho.buscar(produtos, "", "Todos");
            var nomes = resultado.stream().map(ProdutoPdv::nome).toList();
            // A ordem reflete a ordem de inserção no LinkedHashMap do gateway demo
            assertEquals(List.of("Caldo de Mocotó", "Caldo de Frango", "Pão Torrado", "Refrigerante", "Embalagem (Pote)"), nomes);
        }
    }
}