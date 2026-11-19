package br.com.adriano.db;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Gerenciador de conexões SQLite para Mini Comércio 2.0.
 * <p>
 * Diferente da {@code FabricaConexao} legada, esta implementação:
 * <ul>
 *   <li>Nunca retorna {@code null} — lança exceção em falha</li>
 *   <li>Habilita {@code foreign_keys=ON} em toda conexão</li>
 *   <li>Suporta bancos temporários para testes isolados</li>
 *   <li>Configura {@code journal_mode=WAL} para melhor concorrência</li>
 *   <li>Define {@code busy_timeout} para evitar lock contention</li>
 * </ul>
 *
 * @since 2.0
 */
public final class ConexaoManager {

    private static final String PRAGMA_FOREIGN_KEYS = "PRAGMA foreign_keys = ON";
    private static final String PRAGMA_JOURNAL_WAL = "PRAGMA journal_mode = WAL";
    private static final String PRAGMA_BUSY_TIMEOUT = "PRAGMA busy_timeout = 5000";
    private static final String PRAGMA_SYNCHRONOUS = "PRAGMA synchronous = NORMAL";

    private final String url;

    private ConexaoManager(String url) {
        this.url = Objects.requireNonNull(url, "URL não pode ser nula");
    }

    /**
     * Cria um manager para arquivo de banco persistente.
     *
     * @param caminhoArquivo caminho do arquivo .db (ex: "comercio.db")
     * @return nova instância configurada
     */
    public static ConexaoManager paraArquivo(String caminhoArquivo) {
        String url = "jdbc:sqlite:" + caminhoArquivo;
        return new ConexaoManager(url);
    }

    /**
     * Cria um manager para banco temporário em arquivo (ideal para testes unitários).
     * <p>
     * Usa um arquivo temporário que é excluído automaticamente ao fechar a JVM.
     * Isso garante que múltiplas conexões compartilhem o mesmo banco de dados,
     * essencial para migrações e transações, evitando os problemas de banco em memória
     * onde cada conexão cria um banco isolado.
     *
     * @return nova instância configurada para banco temporário em arquivo
     */
    public static ConexaoManager emMemoria() {
        try {
            java.io.File temp = java.io.File.createTempFile("test_", ".db");
            temp.deleteOnExit();
            return paraArquivo(temp.getAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao criar banco temporário", e);
        }
    }

    /**
     * Cria um manager para arquivo temporário que será excluído ao fechar JVM.
     *
     * @param prefixo prefixo do nome do arquivo (ex: "teste_")
     * @return nova instância configurada
     */
    public static ConexaoManager temporario(String prefixo) {
        try {
            java.io.File temp = java.io.File.createTempFile(prefixo, ".db");
            temp.deleteOnExit();
            return paraArquivo(temp.getAbsolutePath());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Falha ao criar banco temporário", e);
        }
    }

    /**
     * Obtém uma conexão configurada.
     * <p>
     * A conexão já vem com:
     * <ul>
     *   <li>foreign_keys = ON</li>
     *   <li>journal_mode = WAL</li>
     *   <li>busy_timeout = 5000ms</li>
     *   <li>synchronous = NORMAL</li>
     * </ul>
     *
     * @return conexão aberta e configurada
     * @throws SQLException se não conseguir conectar ou configurar
     */
    public Connection conectar() throws SQLException {
        Connection conn = DriverManager.getConnection(url);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(PRAGMA_FOREIGN_KEYS);
            stmt.execute(PRAGMA_JOURNAL_WAL);
            stmt.execute(PRAGMA_BUSY_TIMEOUT);
            stmt.execute(PRAGMA_SYNCHRONOUS);
        } catch (SQLException e) {
            try { conn.close(); } catch (SQLException ignored) {}
            throw e;
        }
        return conn;
    }

    /**
     * Obtém a URL de conexão (útil para logs/diagnóstico).
     */
    public String getUrl() {
        // Oculta caminho completo em logs, mostra apenas se é memória ou arquivo
        return url.contains(":memory:") ? "jdbc:sqlite::memory:" : "jdbc:sqlite:<arquivo>";
    }
}