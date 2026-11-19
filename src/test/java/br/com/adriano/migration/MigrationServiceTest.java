package br.com.adriano.migration;

import br.com.adriano.db.ConexaoManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MigrationService - migração de schema")
class MigrationServiceTest {

    @Test
    @DisplayName("Deve executar migração v1 (schema legado)")
    void migracaoV1SchemaLegado() throws SQLException {
        ConexaoManager manager = ConexaoManager.emMemoria();
        MigrationService service = new MigrationService(manager);

        int executadas = service.migrar();
        assertEquals(3, executadas, "Deve executar 3 migrações (v1, v2, v3)");
        assertEquals(3, service.obterVersaoAtual());

        // Verificar tabelas legadas
        assertTrue(service.tabelaExiste("usuarios"));
        assertTrue(service.tabelaExiste("produtos"));
        assertTrue(service.tabelaExiste("vendas"));
        assertTrue(service.tabelaExiste("itens_venda"));
        assertTrue(service.tabelaExiste("caixa"));

        // Verificar colunas legadas
        List<MigrationService.ColunaInfo> colsProdutos = service.inspecionarTabela("produtos");
        assertTrue(colsProdutos.stream().anyMatch(c -> c.nome().equals("nome")));
        assertTrue(colsProdutos.stream().anyMatch(c -> c.nome().equals("preco")));
        assertTrue(colsProdutos.stream().anyMatch(c -> c.nome().equals("estoque")));

        // Usuário admin padrão
        try (Connection conn = manager.conectar();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT * FROM usuarios WHERE usuario = 'admin'")) {
            assertTrue(rs.next());
            assertEquals("admin", rs.getString("usuario"));
            assertEquals("1234", rs.getString("senha"));
        }
    }

    @Test
    @DisplayName("Deve executar migração v2 (schema completo v2.0)")
    void migracaoV2SchemaCompleto() throws SQLException {
        ConexaoManager manager = ConexaoManager.emMemoria();
        MigrationService service = new MigrationService(manager);

        service.migrar(); // Executa v1, v2, v3

        // Verificar colunas v2.0 em produtos
        List<MigrationService.ColunaInfo> colsProdutos = service.inspecionarTabela("produtos");
        assertTrue(colsProdutos.stream().anyMatch(c -> c.nome().equals("codigo")), "Deve ter coluna codigo");
        assertTrue(colsProdutos.stream().anyMatch(c -> c.nome().equals("categoria")), "Deve ter coluna categoria");
        assertTrue(colsProdutos.stream().anyMatch(c -> c.nome().equals("imagem")), "Deve ter coluna imagem");
        assertTrue(colsProdutos.stream().anyMatch(c -> c.nome().equals("ativo")), "Deve ter coluna ativo");

        // Verificar colunas v2.0 em vendas
        List<MigrationService.ColunaInfo> colsVendas = service.inspecionarTabela("vendas");
        assertTrue(colsVendas.stream().anyMatch(c -> c.nome().equals("subtotal")), "Deve ter subtotal");
        assertTrue(colsVendas.stream().anyMatch(c -> c.nome().equals("desconto")), "Deve ter desconto");
        assertTrue(colsVendas.stream().anyMatch(c -> c.nome().equals("cliente_id")), "Deve ter cliente_id");
        assertTrue(colsVendas.stream().anyMatch(c -> c.nome().equals("usuario_id")), "Deve ter usuario_id");
        assertTrue(colsVendas.stream().anyMatch(c -> c.nome().equals("status")), "Deve ter status");
        assertTrue(colsVendas.stream().anyMatch(c -> c.nome().equals("cancelamento_motivo")), "Deve ter cancelamento_motivo");

        // Verificar colunas v2.0 em usuarios
        List<MigrationService.ColunaInfo> colsUsuarios = service.inspecionarTabela("usuarios");
        assertTrue(colsUsuarios.stream().anyMatch(c -> c.nome().equals("nome")), "Deve ter nome");
        assertTrue(colsUsuarios.stream().anyMatch(c -> c.nome().equals("perfil")), "Deve ter perfil");
        assertTrue(colsUsuarios.stream().anyMatch(c -> c.nome().equals("hash")), "Deve ter hash");
        assertTrue(colsUsuarios.stream().anyMatch(c -> c.nome().equals("salt")), "Deve ter salt");

        // Verificar novas tabelas
        assertTrue(service.tabelaExiste("clientes"), "Tabela clientes deve existir");
        assertTrue(service.tabelaExiste("configuracoes"), "Tabela configuracoes deve existir");
        assertTrue(service.tabelaExiste("movimentos_estoque"), "Tabela movimentos_estoque deve existir");
        assertTrue(service.tabelaExiste("suprimentos_retiradas"), "Tabela suprimentos_retiradas deve existir");
    }

    @Test
    @DisplayName("Deve executar migração v3 (índices)")
    void migracaoV3Indices() throws SQLException {
        ConexaoManager manager = ConexaoManager.emMemoria();
        MigrationService service = new MigrationService(manager);

        service.migrar();

        // Verificar se índices foram criados (consultando sqlite_master)
        try (Connection conn = manager.conectar();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'idx_%'")) {
            int count = 0;
            while (rs.next()) count++;
            assertTrue(count >= 14, "Deve ter pelo menos 14 índices, encontrados: " + count);
        }
    }

    @Test
    @DisplayName("Deve ser idempotente - segunda execução não falha")
    void idempotente() throws SQLException {
        ConexaoManager manager = ConexaoManager.emMemoria();
        MigrationService service = new MigrationService(manager);

        int primeira = service.migrar();
        int segunda = service.migrar();

        assertEquals(3, primeira);
        assertEquals(0, segunda, "Segunda execução não deve executar migrações");
        assertEquals(3, service.obterVersaoAtual());
    }

    @Test
    @DisplayName("Deve inspecionar foreign keys")
    void inspecionarForeignKeys() throws SQLException {
        ConexaoManager manager = ConexaoManager.emMemoria();
        MigrationService service = new MigrationService(manager);

        service.migrar();

        List<MigrationService.FkInfo> fksItens = service.inspecionarForeignKeys("itens_venda");
        assertEquals(2, fksItens.size(), "itens_venda deve ter 2 FKs");
        assertTrue(fksItens.stream().anyMatch(fk -> fk.tabelaReferenciada().equals("vendas")));
        assertTrue(fksItens.stream().anyMatch(fk -> fk.tabelaReferenciada().equals("produtos")));

        List<MigrationService.FkInfo> fksMovimentos = service.inspecionarForeignKeys("movimentos_estoque");
        assertTrue(fksMovimentos.stream().anyMatch(fk -> fk.tabelaReferenciada().equals("produtos")));
    }

    @Test
    @DisplayName("Deve verificar coluna existe")
    void colunaExiste() throws SQLException {
        ConexaoManager manager = ConexaoManager.emMemoria();
        MigrationService service = new MigrationService(manager);

        service.migrar();

        assertTrue(service.colunaExiste("produtos", "codigo"));
        assertTrue(service.colunaExiste("produtos", "categoria"));
        assertFalse(service.colunaExiste("produtos", "coluna_inexistente"));
    }

    @Test
    @DisplayName("Deve executar SQL arbitrário")
    void executarSql() throws SQLException {
        ConexaoManager manager = ConexaoManager.emMemoria();
        MigrationService service = new MigrationService(manager);

        service.migrar();
        service.executarSql("INSERT INTO configuracoes (chave, valor, descricao, tipo) VALUES ('teste', '123', 'Teste', 'STRING')");

        try (Connection conn = manager.conectar();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT valor FROM configuracoes WHERE chave = 'teste'")) {
            assertTrue(rs.next());
            assertEquals("123", rs.getString("valor"));
        }
    }
}