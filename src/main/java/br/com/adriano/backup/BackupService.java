package br.com.adriano.backup;

import br.com.adriano.db.ConexaoManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Serviço de backup e restauração para Mini Comércio 2.0.
 * <p>
 * Utiliza {@code VACUUM INTO} (SQLite 3.27+) para backups consistentes sem bloquear o banco.
 * Suporta retenção automática por idade e/ou quantidade.
 *
 * @since 2.0
 */
public final class BackupService {

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String BACKUP_PREFIX = "backup_";
    private static final String BACKUP_EXT = ".db";

    private final ConexaoManager conexaoManager;
    private final Path diretorioBackups;
    private final int retencaoDias;
    private final int retencaoMaxima;

    public BackupService(ConexaoManager conexaoManager, Path diretorioBackups) {
        this(conexaoManager, diretorioBackups, 30, 50);
    }

    public BackupService(ConexaoManager conexaoManager, Path diretorioBackups, int retencaoDias, int retencaoMaxima) {
        this.conexaoManager = Objects.requireNonNull(conexaoManager);
        this.diretorioBackups = Objects.requireNonNull(diretorioBackups);
        this.retencaoDias = Math.max(1, retencaoDias);
        this.retencaoMaxima = Math.max(1, retencaoMaxima);
        criarDiretorioSeNecessario();
    }

    /**
     * Realiza backup do banco atual usando VACUUM INTO.
     * <p>
     * Operação atômica e consistente — não bloqueia leitores.
     *
     * @return caminho do arquivo de backup criado
     * @throws SQLException  se falhar o backup
     * @throws IOException   se falhar criação do diretório
     */
    public Path backup() throws SQLException, IOException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        String nomeArquivo = BACKUP_PREFIX + timestamp + BACKUP_EXT;
        Path destino = diretorioBackups.resolve(nomeArquivo);

        // Garante diretório
        Files.createDirectories(diretorioBackups);

        String sql = "VACUUM INTO '" + destino.toAbsolutePath().toString().replace("'", "''") + "'";
        try (Connection conn = conexaoManager.conectar();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }

        // Aplicar retenção após backup bem-sucedido
        aplicarRetencao();

        return destino;
    }

    /**
     * Realiza backup com nome customizado.
     */
    public Path backupComNome(String sufixo) throws SQLException, IOException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        String nomeArquivo = BACKUP_PREFIX + timestamp + "_" + sufixo + BACKUP_EXT;
        Path destino = diretorioBackups.resolve(nomeArquivo);

        Files.createDirectories(diretorioBackups);

        String sql = "VACUUM INTO '" + destino.toAbsolutePath().toString().replace("'", "''") + "'";
        try (Connection conn = conexaoManager.conectar();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }

        aplicarRetencao();
        return destino;
    }

    /**
     * Lista backups disponíveis, mais recentes primeiro.
     */
    public List<Path> listarBackups() throws IOException {
        try (Stream<Path> stream = Files.list(diretorioBackups)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith(BACKUP_PREFIX))
                    .filter(p -> p.getFileName().toString().endsWith(BACKUP_EXT))
                    .sorted((a, b) -> {
                        try {
                            // Ordenar por tempo de modificação do arquivo (mais recente primeiro)
                            long timeA = Files.getLastModifiedTime(a).toMillis();
                            long timeB = Files.getLastModifiedTime(b).toMillis();
                            return Long.compare(timeB, timeA);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .toList();
        }
    }

    /**
     * Restaura um backup para o banco atual.
     * <p>
     * ATENÇÃO: Substitui o banco atual. Feche todas as conexões antes de chamar.
     *
     * @param backup arquivo de backup a restaurar
     * @throws IOException se falhar a cópia
     */
    public void restaurar(Path backup) throws IOException {
        Objects.requireNonNull(backup, "Backup não pode ser nulo");
        if (!Files.exists(backup)) {
            throw new IOException("Arquivo de backup não encontrado: " + backup);
        }

        // Obter URL do banco atual
        String url = conexaoManager.getUrl();
        if (url.contains(":memory:")) {
            throw new IllegalStateException("Não é possível restaurar em banco de memória");
        }

        // Extrair caminho do arquivo do JDBC URL
        String caminhoBanco = url.replace("jdbc:sqlite:", "");
        Path destino = Paths.get(caminhoBanco);

        // Backup do banco atual antes de restaurar (segurança)
        if (Files.exists(destino)) {
            Path backupAtual = destino.resolveSibling(destino.getFileName() + ".pre_restore_" + LocalDateTime.now().format(TIMESTAMP_FMT) + ".db");
            Files.copy(destino, backupAtual);
        }

        // Restaurar
        Files.copy(backup, destino, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Restaura o backup mais recente.
     */
    public void restaurarMaisRecente() throws IOException, SQLException {
        List<Path> backups = listarBackups();
        if (backups.isEmpty()) {
            throw new IllegalStateException("Nenhum backup disponível para restauração");
        }
        restaurar(backups.getFirst());
    }

    /**
     * Remove backups antigos baseado na política de retenção.
     */
    public void aplicarRetencao() throws IOException {
        List<Path> backups = listarBackups();

        // Retenção por quantidade: manter apenas os N mais recentes
        if (backups.size() > retencaoMaxima) {
            for (int i = retencaoMaxima; i < backups.size(); i++) {
                Files.deleteIfExists(backups.get(i));
            }
            backups = listarBackups(); // Atualizar lista
        }

        // Retenção por idade: remover backups mais antigos que X dias
        LocalDateTime corte = LocalDateTime.now().minusDays(retencaoDias);
        for (Path backup : backups) {
            String nome = backup.getFileName().toString();
            // Formato: backup_YYYYMMDD_HHmmss[_sufixo].db
            String timestampStr = nome.substring(BACKUP_PREFIX.length(), BACKUP_PREFIX.length() + 15);
            try {
                LocalDateTime dataBackup = LocalDateTime.parse(timestampStr, DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                if (dataBackup.isBefore(corte)) {
                    Files.deleteIfExists(backup);
                }
            } catch (Exception ignored) {
                // Se não conseguir parsear, não remove por segurança
            }
        }
    }

    /**
     * Verifica integridade de um arquivo de backup.
     */
    public boolean verificarIntegridade(Path backup) {
        // Criar conexão temporária com o backup e rodar PRAGMA integrity_check
        try {
            ConexaoManager tempManager = ConexaoManager.paraArquivo(backup.toString());
            try (Connection conn = tempManager.conectar();
                 Statement stmt = conn.createStatement();
                 var rs = stmt.executeQuery("PRAGMA integrity_check")) {
                if (rs.next()) {
                    return "ok".equalsIgnoreCase(rs.getString(1).trim());
                }
            }
        } catch (SQLException ignored) {
        }
        return false;
    }

    private void criarDiretorioSeNecessario() {
        try {
            Files.createDirectories(diretorioBackups);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível criar diretório de backups: " + diretorioBackups, e);
        }
    }

    public Path getDiretorioBackups() { return diretorioBackups; }
    public int getRetencaoDias() { return retencaoDias; }
    public int getRetencaoMaxima() { return retencaoMaxima; }
}