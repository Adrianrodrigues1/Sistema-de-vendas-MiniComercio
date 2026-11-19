package br.com.adriano.service;

import br.com.adriano.db.ConexaoManager;
import br.com.adriano.migration.MigrationService;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Classe base para testes de serviços - configura banco em memória com schema v2.0.
 */
public abstract class BaseServiceTest {

    protected ConexaoManager conexaoManager;
    protected MigrationService migrationService;

    @BeforeEach
    void setUp() throws Exception {
        conexaoManager = ConexaoManager.emMemoria();
        migrationService = new MigrationService(conexaoManager);
        migrationService.migrar();

        // Dados básicos para testes
        try (Connection conn = conexaoManager.conectar();
             Statement stmt = conn.createStatement()) {
            // Usuário admin para FKs
            stmt.execute("INSERT OR IGNORE INTO usuarios (id, usuario, senha, nome, perfil, hash, salt, ativo) VALUES (1, 'admin', '1234', 'Administrador', 'ADMIN', 'hash', 'salt', 1)");
            stmt.execute("INSERT OR IGNORE INTO usuarios (id, usuario, senha, nome, perfil, hash, salt, ativo) VALUES (2, 'operador', '1234', 'Operador', 'OPERADOR', 'hash', 'salt', 1)");
        }
    }

    protected ConexaoManager getConexaoManager() { return conexaoManager; }
    protected MigrationService getMigrationService() { return migrationService; }
}