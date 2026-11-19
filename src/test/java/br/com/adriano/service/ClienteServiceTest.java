package br.com.adriano.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClienteService - cadastro de clientes")
class ClienteServiceTest extends BaseServiceTest {

    private ClienteService service;

    @BeforeEach
    @Override
    void setUp() throws Exception {
        super.setUp();
        service = new ClienteService(getConexaoManager());
    }

    @Test
    @DisplayName("Deve salvar cliente com todos os campos")
    void salvarClienteCompleto() throws Exception {
        ClienteService.ClienteInfo cliente = new ClienteService.ClienteInfo(
                null, "João Silva", "12345678901", "11999999999",
                "joao@email.com", "Rua das Flores, 123", true, null
        );

        ClienteService.ClienteInfo salvo = service.salvar(cliente);

        assertNotNull(salvo.id());
        assertEquals("João Silva", salvo.nome());
        assertEquals("12345678901", salvo.cpfCnpj());
        assertEquals("11999999999", salvo.telefone());
        assertEquals("joao@email.com", salvo.email());
        assertEquals("Rua das Flores, 123", salvo.endereco());
        assertTrue(salvo.ativo());
    }

    @Test
    @DisplayName("Deve rejeitar cliente sem nome")
    void rejeitarSemNome() {
        ClienteService.ClienteInfo cliente = new ClienteService.ClienteInfo(
                null, null, "12345678901", "11999999999", null, null, true, null
        );
        assertThrows(IllegalArgumentException.class, () -> service.salvar(cliente));
    }

    @Test
    @DisplayName("Deve validar CPF (11 dígitos)")
    void validarCpf() {
        ClienteService.ClienteInfo cpfCurto = new ClienteService.ClienteInfo(
                null, "Teste", "12345", "11999999999", null, null, true, null
        );
        assertThrows(IllegalArgumentException.class, () -> service.salvar(cpfCurto));

        ClienteService.ClienteInfo cpfLongo = new ClienteService.ClienteInfo(
                null, "Teste", "123456789012", "11999999999", null, null, true, null
        );
        assertThrows(IllegalArgumentException.class, () -> service.salvar(cpfLongo));
    }

    @Test
    @DisplayName("Deve validar CNPJ (14 dígitos)")
    void validarCnpj() throws Exception {
        ClienteService.ClienteInfo cnpj = new ClienteService.ClienteInfo(
                null, "Empresa LTDA", "12345678000195", "11999999999", null, null, true, null
        );
        ClienteService.ClienteInfo salvo = service.salvar(cnpj);
        assertEquals("12345678000195", salvo.cpfCnpj());
    }

    @Test
    @DisplayName("Deve ignorar formatação no CPF/CNPJ")
    void ignorarFormatacaoCpfCnpj() throws Exception {
        ClienteService.ClienteInfo cliente = new ClienteService.ClienteInfo(
                null, "Teste", "123.456.789-01", "11999999999", null, null, true, null
        );
        ClienteService.ClienteInfo salvo = service.salvar(cliente);
        // Deve salvar apenas números
        assertEquals("12345678901", salvo.cpfCnpj());
    }

    @Test
    @DisplayName("Deve atualizar cliente existente")
    void atualizarCliente() throws Exception {
        ClienteService.ClienteInfo salvo = service.salvar(new ClienteService.ClienteInfo(
                null, "Nome Original", "11111111111", "11999999999", null, null, true, null
        ));

        ClienteService.ClienteInfo atualizado = new ClienteService.ClienteInfo(
                salvo.id(), "Nome Atualizado", "11111111111", "11888888888",
                "novo@email.com", "Nova Rua, 456", false, salvo.dataCadastro()
        );

        ClienteService.ClienteInfo resultado = service.salvar(atualizado);

        assertEquals("Nome Atualizado", resultado.nome());
        assertEquals("11888888888", resultado.telefone());
        assertEquals("novo@email.com", resultado.email());
        assertFalse(resultado.ativo());
    }

    @Test
    @DisplayName("Deve buscar por ID")
    void buscarPorId() throws Exception {
        ClienteService.ClienteInfo salvo = service.salvar(new ClienteService.ClienteInfo(
                null, "Busca ID", "22222222222", null, null, null, true, null
        ));

        Optional<ClienteService.ClienteInfo> encontrado = service.buscarPorId(salvo.id());

        assertTrue(encontrado.isPresent());
        assertEquals("Busca ID", encontrado.get().nome());
    }

    @Test
    @DisplayName("Deve buscar por CPF/CNPJ")
    void buscarPorCpfCnpj() throws Exception {
        service.salvar(new ClienteService.ClienteInfo(
                null, "Busca CPF", "33333333333", null, null, null, true, null
        ));

        Optional<ClienteService.ClienteInfo> encontrado = service.buscarPorCpfCnpj("33333333333");

        assertTrue(encontrado.isPresent());
        assertEquals("Busca CPF", encontrado.get().nome());
    }

    @Test
    @DisplayName("Deve listar todos os clientes")
    void listarTodos() throws Exception {
        service.salvar(new ClienteService.ClienteInfo(null, "Cliente A", "11111111111", null, null, null, true, null));
        service.salvar(new ClienteService.ClienteInfo(null, "Cliente B", "22222222222", null, null, null, true, null));
        service.salvar(new ClienteService.ClienteInfo(null, "Cliente Inativo", "33333333333", null, null, null, false, null));

        List<ClienteService.ClienteInfo> todos = service.listarTodos(false);
        List<ClienteService.ClienteInfo> ativos = service.listarTodos(true);

        assertEquals(3, todos.size());
        assertEquals(2, ativos.size());
    }

    @Test
    @DisplayName("Deve buscar por termo (nome, CPF, telefone)")
    void buscarPorTermo() throws Exception {
        service.salvar(new ClienteService.ClienteInfo(null, "João Silva", "11111111111", "11999999999", null, null, true, null));
        service.salvar(new ClienteService.ClienteInfo(null, "Maria Santos", "22222222222", "11888888888", null, null, true, null));
        service.salvar(new ClienteService.ClienteInfo(null, "Pedro", "33333333333", "11777777777", null, null, false, null));

        List<ClienteService.ClienteInfo> porJoao = service.buscarPorTermo("João");
        List<ClienteService.ClienteInfo> por111 = service.buscarPorTermo("11111111111");
        List<ClienteService.ClienteInfo> por9999 = service.buscarPorTermo("99999999");

        assertEquals(1, porJoao.size());
        assertEquals(1, por111.size());
        assertEquals(1, por9999.size());
    }

    @Test
    @DisplayName("Não deve excluir cliente com vendas associadas")
    void naoExcluirComVendas() throws Exception {
        ClienteService.ClienteInfo salvo = service.salvar(new ClienteService.ClienteInfo(
                null, "Com Vendas", "44444444444", null, null, null, true, null
        ));

        // Simular venda associada
        try (var conn = getConexaoManager().conectar();
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO vendas (id, data, forma_pagamento, total, cliente_id, status) VALUES (888, datetime('now'), 'DINHEIRO', 50.00, " + salvo.id() + ", 'FINALIZADA')");
        }

        assertThrows(IllegalStateException.class, () -> service.excluir(salvo.id()));
    }

    @Test
    @DisplayName("Deve excluir cliente sem vendas")
    void excluirSemVendas() throws Exception {
        ClienteService.ClienteInfo salvo = service.salvar(new ClienteService.ClienteInfo(
                null, "Sem Vendas", "55555555555", null, null, null, true, null
        ));

        assertTrue(service.excluir(salvo.id()));
        assertTrue(service.buscarPorId(salvo.id()).isEmpty());
    }
}