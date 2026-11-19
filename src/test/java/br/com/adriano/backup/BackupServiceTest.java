package br.com.adriano.backup;

import br.com.adriano.db.ConexaoManager;
import br.com.adriano.migration.MigrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BackupService - backup e restauração")
class BackupServiceTest {

    private ConexaoManager criarManagerComDados(Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("teste.db");
        ConexaoManager manager = ConexaoManager.paraArquivo(dbFile.toString());
        new MigrationService(manager).migrar();

        // Inserir alguns dados de teste (usuários admin e operador já existem da migração)
        try (Connection conn = manager.conectar();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO produtos (codigo, nome, categoria, preco, estoque, ativo) VALUES ('P001', 'Caldo Verde', 'Caldos', 15.00, 100, 1)");
            stmt.execute("INSERT INTO produtos (codigo, nome, categoria, preco, estoque, ativo) VALUES ('P002', 'Caldo de Feijão', 'Caldos', 18.00, 50, 1)");
        }
        return manager;
    }

    @Test
    @DisplayName("Deve criar backup com VACUUM INTO")
    void backupBasico(@TempDir Path tempDir) throws Exception {
        ConexaoManager manager = criarManagerComDados(tempDir);
        Path backupDir = tempDir.resolve("backups");
        BackupService service = new BackupService(manager, backupDir);

        Path backup = service.backup();

        assertTrue(backup.toFile().exists());
        assertTrue(backup.getFileName().toString().startsWith("backup_"));
        assertTrue(backup.getFileName().toString().endsWith(".db"));
    }

    @Test
    @DisplayName("Deve criar backup com sufixo customizado")
    void backupComSufixo(@TempDir Path tempDir) throws Exception {
        ConexaoManager manager = criarManagerComDados(tempDir);
        Path backupDir = tempDir.resolve("backups");
        BackupService service = new BackupService(manager, backupDir);

        Path backup = service.backupComNome("fechamento_caixa");

        assertTrue(backup.getFileName().toString().contains("fechamento_caixa"));
    }

    @Test
    @DisplayName("Deve listar backups ordenados por data (mais recente primeiro)")
    void listarBackups(@TempDir Path tempDir) throws Exception {
        ConexaoManager manager = criarManagerComDados(tempDir);
        Path backupDir = tempDir.resolve("backups");
        BackupService service = new BackupService(manager, backupDir);

        service.backupComNome("primeiro");
        Thread.sleep(10); // Garantir timestamp diferente
        service.backupComNome("segundo");
        Thread.sleep(10);
        service.backupComNome("terceiro");

        List<Path> backups = service.listarBackups();
        assertEquals(3, backups.size());
        assertTrue(backups.get(0).getFileName().toString().contains("terceiro"));
        assertTrue(backups.get(1).getFileName().toString().contains("segundo"));
        assertTrue(backups.get(2).getFileName().toString().contains("primeiro"));
    }

    @Test
    @DisplayName("Deve aplicar retenção por quantidade")
    void retencaoQuantidade(@TempDir Path tempDir) throws Exception {
        ConexaoManager manager = criarManagerComDados(tempDir);
        Path backupDir = tempDir.resolve("backups");
        BackupService service = new BackupService(manager, backupDir, 30, 2); // Máximo 2 backups

        service.backupComNome("um");
        Thread.sleep(50);
        service.backupComNome("dois");
        Thread.sleep(50);
        service.backupComNome("tres"); // Deve remover o mais antigo

        List<Path> backups = service.listarBackups();
        assertEquals(2, backups.size());
        assertTrue(backups.stream().noneMatch(p -> p.getFileName().toString().contains("um")));
    }

    @Test
    @DisplayName("Deve verificar integridade do backup")
    void verificarIntegridade(@TempDir Path tempDir) throws Exception {
        ConexaoManager manager = criarManagerComDados(tempDir);
        Path backupDir = tempDir.resolve("backups");
        BackupService service = new BackupService(manager, backupDir);

        Path backup = service.backup();
        assertTrue(service.verificarIntegridade(backup), "Backup deve passar no integrity_check");
    }

    @Test
    @DisplayName("Não deve permitir restaurar em banco de memória")
    void naoRestaurarMemoria(@TempDir Path tempDir) {
        ConexaoManager manager = ConexaoManager.emMemoria();
        Path backupDir = tempDir.resolve("backups");
        BackupService service = new BackupService(manager, backupDir);

        assertThrows(IllegalStateException.class, () -> service.restaurarMaisRecente());
    }

    @Test
    @DisplayName("Deve lançar exceção para backup inexistente")
    void backupInexistente(@TempDir Path tempDir) {
        ConexaoManager manager = ConexaoManager.emMemoria();
        Path backupDir = tempDir.resolve("backups");
        BackupService service = new BackupService(manager, backupDir);

        Path inexistente = tempDir.resolve("nao_existe.db");
        assertThrows(java.io.IOException.class, () -> service.restaurar(inexistente));
    }
}