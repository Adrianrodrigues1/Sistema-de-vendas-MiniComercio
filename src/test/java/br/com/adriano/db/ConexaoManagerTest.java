package br.com.adriano.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConexaoManager - gerenciamento de conexões SQLite")
class ConexaoManagerTest {

    @Test
    @DisplayName("Deve conectar em banco de memória")
    void conectarMemoria() throws SQLException {
        ConexaoManager manager = ConexaoManager.emMemoria();
        try (Connection conn = manager.conectar()) {
            assertNotNull(conn);
            assertFalse(conn.isClosed());
        }
    }

    @Test
    @DisplayName("Deve conectar em arquivo temporário")
    void conectarTemporario() throws SQLException {
        ConexaoManager manager = ConexaoManager.temporario("teste_");
        try (Connection conn = manager.conectar()) {
            assertNotNull(conn);
        }
    }

    @Test
    @DisplayName("Deve conectar em arquivo persistente")
    void conectarArquivo(@TempDir Path tempDir) throws SQLException {
        Path dbFile = tempDir.resolve("teste.db");
        ConexaoManager manager = ConexaoManager.paraArquivo(dbFile.toString());
        try (Connection conn = manager.conectar()) {
            assertNotNull(conn);
        }
        assertTrue(dbFile.toFile().exists());
    }

    @Test
    @DisplayName("Deve habilitar foreign_keys=ON")
    void foreignKeysHabilitado() throws SQLException {
        ConexaoManager manager = ConexaoManager.emMemoria();
        try (Connection conn = manager.conectar();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "foreign_keys deve estar ON");
        }
    }

    @Test
    @DisplayName("Deve configurar journal_mode=WAL")
    void journalModeWal() throws SQLException {
        ConexaoManager manager = ConexaoManager.emMemoria();
        try (Connection conn = manager.conectar();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("PRAGMA journal_mode")) {
            assertTrue(rs.next());
            assertEquals("wal", rs.getString(1).toLowerCase());
        }
    }

    @Test
    @DisplayName("Deve configurar busy_timeout")
    void busyTimeoutConfigurado() throws SQLException {
        ConexaoManager manager = ConexaoManager.emMemoria();
        try (Connection conn = manager.conectar();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("PRAGMA busy_timeout")) {
            assertTrue(rs.next());
            assertEquals(5000, rs.getInt(1));
        }
    }

    @Test
    @DisplayName("Deve lançar exceção em URL inválida")
    void urlInvalida() {
        // SQLite aceita quase qualquer string, mas testamos se não retorna null
        ConexaoManager manager = ConexaoManager.paraArquivo("/caminho/inexistente/arquivo.db");
        assertThrows(SQLException.class, () -> manager.conectar());
    }

    @Test
    @DisplayName("Deve permitir múltiplas conexões simultâneas (WAL)")
    void multiplasConexoes() throws SQLException {
        ConexaoManager manager = ConexaoManager.emMemoria();
        try (Connection c1 = manager.conectar();
             Connection c2 = manager.conectar();
             Connection c3 = manager.conectar()) {
            assertNotNull(c1);
            assertNotNull(c2);
            assertNotNull(c3);
            assertFalse(c1.isClosed());
            assertFalse(c2.isClosed());
            assertFalse(c3.isClosed());
        }
    }

    @Test
    @DisplayName("getUrl não deve expor caminho completo")
    void getUrlSeguro() {
        // emMemoria() agora usa arquivo temporário para suportar migrações compartilhadas
        ConexaoManager mem = ConexaoManager.emMemoria();
        assertEquals("jdbc:sqlite:<arquivo>", mem.getUrl());

        ConexaoManager arquivo = ConexaoManager.paraArquivo("/segredo/banco.db");
        assertEquals("jdbc:sqlite:<arquivo>", arquivo.getUrl());
    }
}