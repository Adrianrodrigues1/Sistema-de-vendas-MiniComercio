# Progresso de Desenvolvimento — Mini Comércio 2.0 "Caldos da Ana"

> **Regra**: Atualizar este arquivo a cada marco concluído. Distinguir testes automatizados de validações manuais. Não usar dados reais.

---

## Fase 1A — UI Demonstrativa (CONCLUÍDA ✅)

### Entregáveis
- [x] `GerenciadorTema` — enum `Tema { CLARO, ESCURO }` com paleta de 12 cores por tema (mockup oficial)
- [x] `Componentes` — fábrica de `PainelArredondado`, labels, botões com roles semânticas (primário, perigo, ghost)
- [x] `Shell` — JFrame único com sidebar navegação, header (relógio/caixa/usuário), `CardLayout` central
- [x] `PainelPdv` — grade responsiva 1-4 colunas, busca case/acento-insensível, combo categoria, carrinho JList, totais, botões pagamento
- [x] `GatewayDemonstracao` — 8 produtos em 4 categorias, valida preço/estoque/total, decrementa estoque em venda
- [x] `DemoLauncher` — entry point, instala tema CLARO, cria `Shell` com gateway demo

### Testes
- **158 testes unitários** passando (inclui 9 testes de integração tema)
- Cobertura: componentes, tema, PDV, gateway, dinheiro, pagamento, carrinho

### Validações manuais pendentes
- [ ] Homologação visual tema ESCURO no IntelliJ (requer execução manual)

---

## Fase 1B — Fundação Técnica (CONCLUÍDA ✅)

### Entregáveis
- [x] **ConexaoManager** — SQLite com `foreign_keys=ON`, `journal_mode=WAL`, `busy_timeout=5000`, `synchronous=NORMAL`. Método `emMemoria()` usa arquivo temporário (não `:memory:`) para compartilhar schema entre conexões
- [x] **MigrationService** — 3 migrações via `PRAGMA user_version`:
  - v1: Schema legado compatível v1.0 (usuarios, produtos, vendas, itens_venda, caixa)
  - v2: Schema completo v2.0 (produtos: codigo, categoria, imagem, ativo; vendas: subtotal, desconto, cliente_id, usuario_id, status, cancelamento_*, numero; itens_venda: preco_total; caixa: usuario_abertura_id, usuario_fechamento_id; usuarios: nome, perfil, hash, salt, ativo, data_criacao, usuario_criador_id, usuario_atualizador_id; novas tabelas: clientes, configuracoes, movimentos_estoque, suprimentos_retiradas)
  - v3: Índices de performance (13 índices)
- [x] **BackupService** — `VACUUM INTO` para backups consistentes, retenção por quantidade e idade, verificação `PRAGMA integrity_check`, bloqueio de restauração em banco de memória
- [x] **Serviços de domínio** (6):
  - `ProdutoService` — CRUD, ajuste atômico de estoque, listagem por categoria/busca
  - `VendaService` — registro transacional com itens, estoque, movimentos, cancelamento com estorno, totais do caixa
  - `CaixaService` — abertura/fechamento, suprimentos/retiradas, saldo disponível (corrigido: subqueries para evitar cross-join)
  - `UsuarioService` — autenticação PBKDF2-like (SHA-256 + salt, 10k iterações), migração senha legada, perfis ADMIN/OPERADOR/GERENTE, bloqueio exclusão admin
  - `ClienteService` — CRUD, validação CPF/CNPJ (11/14 dígitos), limpeza formatação, busca por termo
  - `ConfiguracaoService` — chave/valor tipado (STRING, INTEGER, BOOLEAN), defaults

### Testes
- **158 testes unitários** passando (100% success)
- Cobertura: todos os 6 serviços + migração + backup + conexão + componentes UI

### Correções aplicadas durante desenvolvimento
1. **SQLite in-memory isolation** → `emMemoria()` usa arquivo temporário com `deleteOnExit()`
2. **Migration connection management** → Single connection for all migrations (transactional)
3. **Missing @BeforeEach** → Added to all 10 service test classes
4. **Missing columns in migration** → Added all v2.0 columns to V2 migration
5. **SQLite "FOR UPDATE" syntax error** → Removed from `ProdutoService.ajustarEstoque()`
6. **DEFAULT CURRENT_TIMESTAMP in ALTER TABLE** → Changed to "TEXT" without default
7. **BigDecimal.equals() scale sensitivity** → Changed to `compareTo() == 0` in tests
8. **UNIQUE column ALTER TABLE** → Removed UNIQUE from ALTER, added `CREATE UNIQUE INDEX IF NOT EXISTS` after
9. **CaixaService saldo cross-join** → Rewrote with subqueries
10. **Carrinho stock validation** → Moved to service layer (gateway/service), Carrinho only validates positive quantity
11. **ClienteService CPF/CNPJ cleaning** → Clean formatting before save
12. **Migration V1 admin user** → Added admin (id=1) and operador (id=2) with proper perfil in V2

### Validações manuais pendentes
- [ ] Homologação visual tema ESCURO (herdado da Fase 1A)

---

## Fase 2 — PDV Real com SQLite (CONCLUÍDA ✅)

### Entregáveis
- [x] `PdvGatewayReal` — implementação real do `PdvGateway` usando `ProdutoService` e `VendaService` com SQLite
- [x] `RealPdvLauncher` — entry point para PDV real com migração automática e banco temporário/persistente
- [x] Integração completa: busca → grade responsiva → carrinho → pagamento (DINHEIRO/PIX/DEBITO/CREDITO) → venda → estoque baixado
- [x] Validação de caixa aberto antes de venda
- [x] Suporte a desconto no carrinho
- [x] Tratamento de estoque insuficiente com rollback transacional

### Testes
- **8 testes de integração** passando (`PdvGatewayRealTest`)
- Cenários cobertos: venda simples dinheiro, PIX, com desconto, estoque insuficiente, caixa fechado/aberto, soma de quantidades mesmo produto
- **Total de testes do projeto: 166** (158 fase 1A/1B + 8 fase 2)

### Validações manuais pendentes
- [ ] Execução manual do `RealPdvLauncher` para validação visual completa do fluxo PDV

---

## Fase 3 — Produtos & Estoque (PENDENTE)

- [ ] Tela de cadastro de produtos (código, nome, categoria, preço, estoque, imagem, ativo)
- [ ] Ajuste de estoque com motivo e auditoria (`movimentos_estoque`)
- [ ] Relatório de produtos abaixo do mínimo
- [ ] Importação/exportação CSV

---

## Fase 4 — Caixa & Relatórios (PENDENTE)

- [ ] Tela de abertura/fechamento de caixa
- [ ] Suprimentos e retiradas
- [ ] Relatório de vendas por período/forma/operador
- [ ] Fechamento de caixa com conferência (esperado vs. contado)

---

## Fase 5 — Clientes, Usuários, Config (PENDENTE)

- [ ] Tela de clientes (CRUD, busca, CPF/CNPJ formatado)
- [ ] Tela de usuários (perfis, hash, bloqueio admin)
- [ ] Tela de configurações (tema, moeda, backup, timezone)
- [ ] Persistência de tema na tabela `configuracoes`

---

## Fase 6 — Instalador & Entrega (PENDENTE)

- [ ] `jpackage` → `.exe` instalador Windows
- [ ] Assinatura digital (code signing) - *fora do escopo de dev*
- [ ] Documentação de instalação e uso
- [ ] Checklist de pré-produção (dependências, vulnerabilidades, JDK bundling)

---

## Resumo de Métricas

| Fase | Testes | Status | Observações |
|------|--------|--------|-------------|
| 1A   | 158    | ✅     | UI demo completa, tema CLARO validado |
| 1B   | 158    | ✅     | Infraestrutura completa, todos testes passam |
| 2    | 166    | ✅     | PDV real com SQLite, 8 testes integração |
| 3    | —      | ⏳     | Aguarda fase 2 |
| 4    | —      | ⏳     | Aguarda fase 3 |
| 5    | —      | ⏳     | Aguarda fase 4 |
| 6    | —      | ⏳     | Aguarda fase 5 |

---

**Última atualização**: 2026-11-18  
**Responsável**: Assistente IA (Claude Code)  
**Ambiente**: Windows 10, JDK 21, Maven 3.9.9 (isolado), SQLite 3.45.2

---

## Arquivos Criados/Modificados na Fase 2

### Novos arquivos
- `src/main/java/br/com/adriano/service/PdvGatewayReal.java` — Gateway real com serviços de domínio
- `src/main/java/br/com/adriano/demo/RealPdvLauncher.java` — Launcher para PDV real
- `src/test/java/br/com/adriano/service/PdvGatewayRealTest.java` — 8 testes de integração