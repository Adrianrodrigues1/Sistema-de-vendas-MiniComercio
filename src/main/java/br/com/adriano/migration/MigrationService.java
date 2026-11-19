package br.com.adriano.migration;

import br.com.adriano.db.ConexaoManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Serviço de migração de schema para Mini Comércio 2.0.
 * <p>
 * Utiliza {@code PRAGMA user_version} para controlar versão do schema.
 * Cada migração é numerada sequencialmente e executada uma única vez.
 * Suporta inspeção de tabelas/colunas/FKs via {@code PRAGMA table_info} e {@code PRAGMA foreign_key_list}.
 *
 * @since 2.0
 */
public final class MigrationService {

    private static final String PRAGMA_USER_VERSION = "PRAGMA user_version";
    private static final String PRAGMA_TABLE_INFO = "PRAGMA table_info(%s)";
    private static final String PRAGMA_FK_LIST = "PRAGMA foreign_key_list(%s)";
    private static final String PRAGMA_INDEX_LIST = "PRAGMA index_list(%s)";

    private final ConexaoManager conexaoManager;
    private final List<Migracao> migracoes = new ArrayList<>();

    public MigrationService(ConexaoManager conexaoManager) {
        this.conexaoManager = Objects.requireNonNull(conexaoManager, "ConexaoManager não pode ser nulo");
        registrarMigracoes();
    }

    /**
     * Executa todas as migrações pendentes até a versão alvo.
     *
     * @return número de migrações executadas
     * @throws SQLException se houver erro na migração
     */
    public int migrar() throws SQLException {
        // Use a single connection for all migrations to support in-memory databases
        // and avoid shared cache locking issues
        try (Connection conn = conexaoManager.conectar()) {
            conn.setAutoCommit(false);
            int versaoAtual;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(PRAGMA_USER_VERSION)) {
                versaoAtual = rs.next() ? rs.getInt(1) : 0;
            }
            int versaoAlvo = migracoes.size();

            if (versaoAtual >= versaoAlvo) {
                conn.commit(); // Commit the read-only transaction
                return 0; // Já está atualizado
            }

            int executadas = 0;
            try {
                for (int i = versaoAtual; i < versaoAlvo; i++) {
                    Migracao migracao = migracoes.get(i);
                    migracao.executar(conn);
                    definirVersao(i + 1, conn);
                    executadas++;
                }
                conn.commit();
                return executadas;
            } catch (SQLException e) {
                conn.rollback();
                throw new SQLException("Falha na migração: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Obtém a versão atual do schema no banco.
     */
    public int obterVersaoAtual() throws SQLException {
        try (Connection conn = conexaoManager.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(PRAGMA_USER_VERSION)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Inspeciona a estrutura de uma tabela.
     *
     * @param tabela nome da tabela
     * @return lista de colunas com suas propriedades
     */
    public List<ColunaInfo> inspecionarTabela(String tabela) throws SQLException {
        List<ColunaInfo> colunas = new ArrayList<>();
        try (Connection conn = conexaoManager.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(String.format(PRAGMA_TABLE_INFO, tabela))) {
            while (rs.next()) {
                colunas.add(new ColunaInfo(
                        rs.getInt("cid"),
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getInt("notnull") == 1,
                        rs.getString("dflt_value"),
                        rs.getInt("pk") == 1
                ));
            }
        }
        return colunas;
    }

    /**
     * Inspeciona foreign keys de uma tabela.
     */
    public List<FkInfo> inspecionarForeignKeys(String tabela) throws SQLException {
        List<FkInfo> fks = new ArrayList<>();
        try (Connection conn = conexaoManager.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(String.format(PRAGMA_FK_LIST, tabela))) {
            while (rs.next()) {
                fks.add(new FkInfo(
                        rs.getInt("id"),
                        rs.getInt("seq"),
                        rs.getString("table"),
                        rs.getString("from"),
                        rs.getString("to"),
                        rs.getString("on_update"),
                        rs.getString("on_delete"),
                        rs.getString("match")
                ));
            }
        }
        return fks;
    }

    /**
     * Verifica se uma tabela existe.
     */
    public boolean tabelaExiste(String tabela) throws SQLException {
        try (Connection conn = conexaoManager.conectar()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, tabela, new String[]{"TABLE"})) {
                return rs.next();
            }
        }
    }

    /**
     * Verifica se uma coluna existe em uma tabela.
     */
    public boolean colunaExiste(String tabela, String coluna) throws SQLException {
        return inspecionarTabela(tabela).stream().anyMatch(c -> c.nome().equalsIgnoreCase(coluna));
    }

    /**
     * Verifica se uma coluna existe em uma tabela (usa conexão fornecida).
     */
    private boolean colunaExiste(Connection conn, String tabela, String coluna) throws SQLException {
        return inspecionarTabela(conn, tabela).stream().anyMatch(c -> c.nome().equalsIgnoreCase(coluna));
    }

    /**
     * Inspeciona a estrutura de uma tabela (usa conexão fornecida).
     */
    private List<ColunaInfo> inspecionarTabela(Connection conn, String tabela) throws SQLException {
        List<ColunaInfo> colunas = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(String.format(PRAGMA_TABLE_INFO, tabela))) {
            while (rs.next()) {
                colunas.add(new ColunaInfo(
                        rs.getInt("cid"),
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getInt("notnull") == 1,
                        rs.getString("dflt_value"),
                        rs.getInt("pk") == 1
                ));
            }
        }
        return colunas;
    }

    /**
     * Executa SQL arbitrário (para migrações customizadas).
     */
    public void executarSql(String sql) throws SQLException {
        try (Connection conn = conexaoManager.conectar();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Executa SQL em uma conexão específica (usado durante migrações transacionais).
     */
    private void executarSql(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void registrarMigracoes() {
        // v1: Schema legado (compatibilidade com v1.0)
        migracoes.add(this::migracaoV1SchemaLegado);

        // v2: Schema completo v2.0
        migracoes.add(this::migracaoV2SchemaCompleto);

        // v3: Índices de performance
        migracoes.add(this::migracaoV3Indices);
    }

    // ===== MIGRAÇÃO V1: Schema legado (compatível com v1.0) =====
    private void migracaoV1SchemaLegado(Connection conn) throws SQLException {
        String[] sqls = {
                // usuarios (legado: usuario, senha)
                "CREATE TABLE IF NOT EXISTS usuarios ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "usuario TEXT UNIQUE NOT NULL, "
                + "senha TEXT NOT NULL"
                + ");",

                // produtos (legado: nome, preco, estoque)
                "CREATE TABLE IF NOT EXISTS produtos ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "nome TEXT NOT NULL, "
                + "preco REAL NOT NULL, "
                + "estoque INTEGER NOT NULL DEFAULT 0"
                + ");",

                // vendas (legado: data, forma_pagamento, total)
                "CREATE TABLE IF NOT EXISTS vendas ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "data TEXT DEFAULT CURRENT_TIMESTAMP, "
                + "forma_pagamento TEXT NOT NULL, "
                + "total REAL NOT NULL"
                + ");",

                // itens_venda
                "CREATE TABLE IF NOT EXISTS itens_venda ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "venda_id INTEGER NOT NULL, "
                + "produto_id INTEGER NOT NULL, "
                + "quantidade INTEGER NOT NULL, "
                + "preco_unitario REAL NOT NULL, "
                + "FOREIGN KEY (venda_id) REFERENCES vendas(id) ON DELETE CASCADE, "
                + "FOREIGN KEY (produto_id) REFERENCES produtos(id) ON DELETE RESTRICT"
                + ");",

                // caixa (legado)
                "CREATE TABLE IF NOT EXISTS caixa ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "data_abertura TEXT, "
                + "data_fechamento TEXT, "
                + "valor_inicial REAL, "
                + "status TEXT"
                + ");",

                // Usuário admin padrão (senha: 1234 - apenas para demo)
                "INSERT OR IGNORE INTO usuarios (id, usuario, senha) VALUES (1, 'admin', '1234');"
        };

        for (String sql : sqls) {
            executarSql(conn, sql);
        }
    }

    // ===== MIGRAÇÃO V2: Schema completo v2.0 =====
    private void migracaoV2SchemaCompleto(Connection conn) throws SQLException {
        // 1. Estender tabela produtos (sem UNIQUE na coluna, será criado índice único depois)
        adicionarColunaSeNaoExiste(conn, "produtos", "codigo", "TEXT");
        adicionarColunaSeNaoExiste(conn, "produtos", "categoria", "TEXT");
        adicionarColunaSeNaoExiste(conn, "produtos", "imagem", "TEXT");
        adicionarColunaSeNaoExiste(conn, "produtos", "ativo", "INTEGER NOT NULL DEFAULT 1");

        // Índice único para codigo (criado após migração de dados se necessário)
        executarSql(conn, "CREATE UNIQUE INDEX IF NOT EXISTS idx_produtos_codigo_unique ON produtos(codigo) WHERE codigo IS NOT NULL;");

        // 2. Estender tabela vendas
        adicionarColunaSeNaoExiste(conn, "vendas", "subtotal", "REAL NOT NULL DEFAULT 0");
        adicionarColunaSeNaoExiste(conn, "vendas", "desconto", "REAL NOT NULL DEFAULT 0");
        adicionarColunaSeNaoExiste(conn, "vendas", "cliente_id", "INTEGER");
        adicionarColunaSeNaoExiste(conn, "vendas", "usuario_id", "INTEGER");
        adicionarColunaSeNaoExiste(conn, "vendas", "status", "TEXT NOT NULL DEFAULT 'FINALIZADA'");
        adicionarColunaSeNaoExiste(conn, "vendas", "cancelamento_motivo", "TEXT");
        adicionarColunaSeNaoExiste(conn, "vendas", "cancelamento_data", "TEXT");
        adicionarColunaSeNaoExiste(conn, "vendas", "cancelamento_usuario_id", "INTEGER");
        adicionarColunaSeNaoExiste(conn, "vendas", "numero", "INTEGER");

        // 2b. Estender tabela itens_venda
        adicionarColunaSeNaoExiste(conn, "itens_venda", "preco_total", "REAL NOT NULL DEFAULT 0");

        // 2c. Estender tabela caixa
        adicionarColunaSeNaoExiste(conn, "caixa", "usuario_abertura_id", "INTEGER");
        adicionarColunaSeNaoExiste(conn, "caixa", "usuario_fechamento_id", "INTEGER");

        // 3. Estender tabela usuarios
        adicionarColunaSeNaoExiste(conn, "usuarios", "nome", "TEXT");
        adicionarColunaSeNaoExiste(conn, "usuarios", "perfil", "TEXT NOT NULL DEFAULT 'OPERADOR'");
        adicionarColunaSeNaoExiste(conn, "usuarios", "hash", "TEXT");
        adicionarColunaSeNaoExiste(conn, "usuarios", "salt", "TEXT");
        adicionarColunaSeNaoExiste(conn, "usuarios", "ativo", "INTEGER NOT NULL DEFAULT 1");
        adicionarColunaSeNaoExiste(conn, "usuarios", "data_criacao", "TEXT");
        adicionarColunaSeNaoExiste(conn, "usuarios", "usuario_criador_id", "INTEGER");
        adicionarColunaSeNaoExiste(conn, "usuarios", "usuario_atualizador_id", "INTEGER");

        // 4. Nova tabela: clientes
        executarSql(conn, "CREATE TABLE IF NOT EXISTS clientes ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "nome TEXT NOT NULL, "
                + "cpf_cnpj TEXT UNIQUE, "
                + "telefone TEXT, "
                + "email TEXT, "
                + "endereco TEXT, "
                + "ativo INTEGER NOT NULL DEFAULT 1, "
                + "data_cadastro TEXT DEFAULT CURRENT_TIMESTAMP"
                + ");");

        // 5. Nova tabela: configuracoes
        executarSql(conn, "CREATE TABLE IF NOT EXISTS configuracoes ("
                + "chave TEXT PRIMARY KEY, "
                + "valor TEXT NOT NULL, "
                + "descricao TEXT, "
                + "tipo TEXT NOT NULL DEFAULT 'STRING'"
                + ");");

        // 6. Nova tabela: movimentos_estoque
        executarSql(conn, "CREATE TABLE IF NOT EXISTS movimentos_estoque ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "produto_id INTEGER NOT NULL, "
                + "tipo TEXT NOT NULL, " // ENTRADA, SAIDA, AJUSTE, PERDA, DEVOLUCAO
                + "quantidade INTEGER NOT NULL, "
                + "quantidade_anterior INTEGER NOT NULL, "
                + "quantidade_nova INTEGER NOT NULL, "
                + "motivo TEXT, "
                + "referencia_tipo TEXT, " // VENDA, COMPRA, AJUSTE_MANUAL, etc.
                + "referencia_id INTEGER, "
                + "usuario_id INTEGER, "
                + "data TEXT DEFAULT CURRENT_TIMESTAMP, "
                + "FOREIGN KEY (produto_id) REFERENCES produtos(id) ON DELETE RESTRICT"
                + ");");

        // 7. Nova tabela: suprimentos_retiradas (movimentações de caixa)
        executarSql(conn, "CREATE TABLE IF NOT EXISTS suprimentos_retiradas ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "caixa_id INTEGER NOT NULL, "
                + "tipo TEXT NOT NULL, " // SUPRIMENTO, RETIRADA
                + "valor REAL NOT NULL, "
                + "motivo TEXT, "
                + "usuario_id INTEGER, "
                + "data TEXT DEFAULT CURRENT_TIMESTAMP, "
                + "FOREIGN KEY (caixa_id) REFERENCES caixa(id) ON DELETE CASCADE"
                + ");");

        // 8. Popular configurações padrão
        popularConfiguracoesPadrao(conn);

        // 9. Usuários padrão (após adicionar colunas nome, perfil, ativo)
        executarSql(conn, "UPDATE usuarios SET nome = 'Administrador', perfil = 'ADMIN', ativo = 1 WHERE id = 1 AND usuario = 'admin';");
        executarSql(conn, "INSERT OR IGNORE INTO usuarios (id, usuario, senha, nome, perfil, ativo) VALUES (2, 'operador', '1234', 'Operador', 'OPERADOR', 1);");
    }

    // ===== MIGRAÇÃO V3: Índices de performance =====
    private void migracaoV3Indices(Connection conn) throws SQLException {
        String[] indices = {
                // idx_produtos_codigo único parcial já criado na v2
                "CREATE INDEX IF NOT EXISTS idx_produtos_categoria ON produtos(categoria);",
                "CREATE INDEX IF NOT EXISTS idx_produtos_ativo ON produtos(ativo);",
                "CREATE INDEX IF NOT EXISTS idx_vendas_data ON vendas(data);",
                "CREATE INDEX IF NOT EXISTS idx_vendas_status ON vendas(status);",
                "CREATE INDEX IF NOT EXISTS idx_vendas_cliente ON vendas(cliente_id);",
                "CREATE INDEX IF NOT EXISTS idx_vendas_usuario ON vendas(usuario_id);",
                "CREATE INDEX IF NOT EXISTS idx_itens_venda_venda ON itens_venda(venda_id);",
                "CREATE INDEX IF NOT EXISTS idx_itens_venda_produto ON itens_venda(produto_id);",
                "CREATE INDEX IF NOT EXISTS idx_caixa_status ON caixa(status);",
                "CREATE INDEX IF NOT EXISTS idx_clientes_cpf ON clientes(cpf_cnpj);",
                "CREATE INDEX IF NOT EXISTS idx_movimentos_produto ON movimentos_estoque(produto_id);",
                "CREATE INDEX IF NOT EXISTS idx_movimentos_data ON movimentos_estoque(data);",
                "CREATE INDEX IF NOT EXISTS idx_suprimentos_caixa ON suprimentos_retiradas(caixa_id);"
        };

        for (String sql : indices) {
            executarSql(conn, sql);
        }
    }

    private void adicionarColunaSeNaoExiste(Connection conn, String tabela, String coluna, String definicao) throws SQLException {
        if (!colunaExiste(conn, tabela, coluna)) {
            executarSql(conn, "ALTER TABLE " + tabela + " ADD COLUMN " + coluna + " " + definicao + ";");
        }
    }

    private void popularConfiguracoesPadrao(Connection conn) throws SQLException {
        String[][] configs = {
                {"tema", "CLARO", "Tema da interface (CLARO/ESCURO)", "STRING"},
                {"moeda", "BRL", "Código da moeda (ISO 4217)", "STRING"},
                {"casas_decimais", "2", "Casas decimais para valores monetários", "INTEGER"},
                {"timezone", "America/Sao_Paulo", "Fuso horário padrão", "STRING"},
                {"backup_auto", "true", "Backup automático ao fechar caixa", "BOOLEAN"},
                {"backup_retencao_dias", "30", "Dias de retenção de backups", "INTEGER"}
        };

        for (String[] cfg : configs) {
            executarSql(conn, String.format(
                    "INSERT OR IGNORE INTO configuracoes (chave, valor, descricao, tipo) VALUES ('%s', '%s', '%s', '%s');",
                    cfg[0], cfg[1], cfg[2], cfg[3]
            ));
        }
    }

    private void definirVersao(int versao, Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(PRAGMA_USER_VERSION + " = " + versao);
        }
    }

    @FunctionalInterface
    private interface Migracao {
        void executar(Connection conn) throws SQLException;
    }

    /**
     * Informações de uma coluna (resultado de PRAGMA table_info).
     */
    public record ColunaInfo(
            int cid,
            String nome,
            String tipo,
            boolean notNull,
            String valorPadrao,
            boolean primaryKey
    ) {}

    /**
     * Informações de uma foreign key (resultado de PRAGMA foreign_key_list).
     */
    public record FkInfo(
            int id,
            int seq,
            String tabelaReferenciada,
            String colunaOrigem,
            String colunaDestino,
            String onUpdate,
            String onDelete,
            String match
    ) {}
}