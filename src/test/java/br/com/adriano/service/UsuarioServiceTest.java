package br.com.adriano.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UsuarioService - autenticação, perfis, hash de senha")
class UsuarioServiceTest extends BaseServiceTest {

    private UsuarioService service;

    @BeforeEach
    @Override
    void setUp() throws Exception {
        super.setUp();
        service = new UsuarioService(getConexaoManager());
    }

    @Test
    @DisplayName("Deve autenticar usuário admin legado (senha direta)")
    void autenticarAdminLegado() throws Exception {
        Optional<UsuarioService.UsuarioInfo> user = service.autenticar("admin", "1234");

        assertTrue(user.isPresent());
        assertEquals("admin", user.get().login());
        assertEquals("ADMIN", user.get().perfil());
    }

    @Test
    @DisplayName("Deve autenticar operador legado")
    void autenticarOperadorLegado() throws Exception {
        Optional<UsuarioService.UsuarioInfo> user = service.autenticar("operador", "1234");

        assertTrue(user.isPresent());
        assertEquals("operador", user.get().login());
        assertEquals("OPERADOR", user.get().perfil());
    }

    @Test
    @DisplayName("Deve rejeitar senha incorreta")
    void rejeitarSenhaIncorreta() throws Exception {
        Optional<UsuarioService.UsuarioInfo> user = service.autenticar("admin", "senha_errada");
        assertTrue(user.isEmpty());
    }

    @Test
    @DisplayName("Deve rejeitar usuário inexistente")
    void rejeitarUsuarioInexistente() throws Exception {
        Optional<UsuarioService.UsuarioInfo> user = service.autenticar("inexistente", "1234");
        assertTrue(user.isEmpty());
    }

    @Test
    @DisplayName("Deve criar novo usuário com hash PBKDF2")
    void criarUsuarioComHash() throws Exception {
        UsuarioService.UsuarioInfo novo = service.criarUsuario("novo_user", "senha123", "Novo Usuário", "OPERADOR", 1);

        assertNotNull(novo.id());
        assertEquals("novo_user", novo.login());
        assertEquals("Novo Usuário", novo.nome());
        assertEquals("OPERADOR", novo.perfil());
        assertTrue(novo.ativo());

        // Verificar que hash e salt foram salvos
        Optional<UsuarioService.UsuarioInfo> buscado = service.buscarPorLogin("novo_user");
        assertTrue(buscado.isPresent());
    }

    @Test
    @DisplayName("Deve autenticar usuário criado com hash")
    void autenticarUsuarioComHash() throws Exception {
        service.criarUsuario("user_hash", "minhaSenha456", "User Hash", "GERENTE", 1);

        Optional<UsuarioService.UsuarioInfo> user = service.autenticar("user_hash", "minhaSenha456");

        assertTrue(user.isPresent());
        assertEquals("GERENTE", user.get().perfil());
    }

    @Test
    @DisplayName("Deve rejeitar login duplicado")
    void rejeitarLoginDuplicado() throws Exception {
        service.criarUsuario("duplicado", "senha1", "User 1", "OPERADOR", 1);
        assertThrows(Exception.class, () -> service.criarUsuario("duplicado", "senha2", "User 2", "OPERADOR", 1));
    }

    @Test
    @DisplayName("Deve validar senha mínima de 4 caracteres")
    void validarSenhaMinima() {
        assertThrows(IllegalArgumentException.class, () -> service.criarUsuario("user", "123", "User", "OPERADOR", 1));
    }

    @Test
    @DisplayName("Deve validar perfil válido")
    void validarPerfil() {
        assertThrows(IllegalArgumentException.class, () -> service.criarUsuario("user", "senha123", "User", "INVALIDO", 1));
    }

    @Test
    @DisplayName("Deve alterar senha com senha atual correta")
    void alterarSenha() throws Exception {
        service.criarUsuario("user_senha", "senhaAntiga", "User", "OPERADOR", 1);
        UsuarioService.UsuarioInfo user = service.buscarPorLogin("user_senha").orElseThrow();

        boolean ok = service.alterarSenha(user.id(), "senhaAntiga", "senhaNova123");

        assertTrue(ok);
        // Nova senha deve funcionar
        Optional<UsuarioService.UsuarioInfo> aut = service.autenticar("user_senha", "senhaNova123");
        assertTrue(aut.isPresent());
        // Senha antiga não deve mais funcionar
        aut = service.autenticar("user_senha", "senhaAntiga");
        assertTrue(aut.isEmpty());
    }

    @Test
    @DisplayName("Deve rejeitar alterar senha com senha atual incorreta")
    void rejeitarAlterarSenhaIncorreta() throws Exception {
        service.criarUsuario("user_senha2", "senhaCorreta", "User", "OPERADOR", 1);
        UsuarioService.UsuarioInfo user = service.buscarPorLogin("user_senha2").orElseThrow();

        boolean ok = service.alterarSenha(user.id(), "senhaErrada", "novaSenha");

        assertFalse(ok);
    }

    @Test
    @DisplayName("Deve permitir admin redefinir senha sem senha atual")
    void redefinirSenhaAdmin() throws Exception {
        service.criarUsuario("user_reset", "senhaOriginal", "User", "OPERADOR", 1);
        UsuarioService.UsuarioInfo user = service.buscarPorLogin("user_reset").orElseThrow();

        boolean ok = service.redefinirSenha(user.id(), "novaSenhaAdmin", 1); // admin id=1

        assertTrue(ok);
        Optional<UsuarioService.UsuarioInfo> aut = service.autenticar("user_reset", "novaSenhaAdmin");
        assertTrue(aut.isPresent());
    }

    @Test
    @DisplayName("Deve atualizar dados do usuário")
    void atualizarUsuario() throws Exception {
        service.criarUsuario("user_update", "senha123", "Nome Original", "OPERADOR", 1);
        UsuarioService.UsuarioInfo user = service.buscarPorLogin("user_update").orElseThrow();

        UsuarioService.UsuarioInfo atualizado = service.atualizarUsuario(user.id(), "Nome Atualizado", "GERENTE", false, 1);

        assertEquals("Nome Atualizado", atualizado.nome());
        assertEquals("GERENTE", atualizado.perfil());
        assertFalse(atualizado.ativo());
    }

    @Test
    @DisplayName("Deve listar todos os usuários")
    void listarTodos() throws Exception {
        service.criarUsuario("user_a", "senha", "User A", "OPERADOR", 1);
        service.criarUsuario("user_b", "senha", "User B", "GERENTE", 1);

        List<UsuarioService.UsuarioInfo> lista = service.listarTodos();

        assertTrue(lista.size() >= 4); // admin, operador (legado) + 2 novos
        assertTrue(lista.stream().anyMatch(u -> u.login().equals("user_a")));
        assertTrue(lista.stream().anyMatch(u -> u.login().equals("user_b")));
    }

    @Test
    @DisplayName("Não deve excluir usuário ADMIN")
    void naoExcluirAdmin() throws Exception {
        assertFalse(service.excluir(1)); // admin id=1
    }

    @Test
    @DisplayName("Deve excluir usuário OPERADOR")
    void excluirOperador() throws Exception {
        service.criarUsuario("user_excluir", "senha", "User", "OPERADOR", 1);
        UsuarioService.UsuarioInfo user = service.buscarPorLogin("user_excluir").orElseThrow();

        assertTrue(service.excluir(user.id()));
        assertTrue(service.buscarPorId(user.id()).isEmpty());
    }

    @Test
    @DisplayName("Deve migrar senha legada para hash na primeira autenticação")
    void migrarSenhaLegada() throws Exception {
        // Usuário legado sem hash/salt
        try (var conn = getConexaoManager().conectar();
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO usuarios (usuario, senha, nome, perfil, ativo) VALUES ('legado', 'senha123', 'Legado', 'OPERADOR', 1)");
        }

        // Primeira autenticação deve migrar
        Optional<UsuarioService.UsuarioInfo> user = service.autenticar("legado", "senha123");
        assertTrue(user.isPresent());

        // Segunda autenticação deve usar hash
        user = service.autenticar("legado", "senha123");
        assertTrue(user.isPresent());
    }
}