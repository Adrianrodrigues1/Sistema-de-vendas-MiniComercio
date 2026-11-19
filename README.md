# Sistema de Vendas — Mini Comércio

Este repositório contém duas versões do sistema:

---

## 📦 Versão 1.0 (Legado — branch `main`)

- **Stack**: Java 21, Swing, SQLite (JDBC), Maven
- **Funcionalidades**: Login, PDV básico, Cadastro de Produtos, Caixa, Relatórios, Usuários
- **Entry point**: `br.com.adriano.Main`
- **Banco**: `comercio.db` (schema v1: usuarios, produtos, vendas, itens_venda, caixa)
- **DAO legados**: `FabricaConexao`, `CriadorTabelas`, `ProdutoDAO`, `UsuarioDAO`, `VendaDAO`, `CaixaDAO`, `RetiradaDAO`
- **Interface**: `TelaLogin`, `TelaPrincipal`, `TelaProdutos`, `TelaVenda`, `TelaCaixa`, `TelaRelatorios`

> ⚠️ **Preservação**: O código v1.0 em `src/main/java/br/com/adriano/{dao,model,util,view}/` e `Main.java` deve permanecer **inalterado** para compatibilidade e rollback.

---

## 🚀 Versão 2.0 "Caldos da Ana" (branch `desenvolvimento-2.0`)

### Fases Concluídas

| Fase | Descrição | Testes |
|------|-----------|--------|
| **1A** | UI Demonstrativa: Tema CLARO/ESCURO, 7 componentes reutilizáveis, Shell (JFrame único, sidebar, header, CardLayout), PDV Demo (busca, grade 1-4 colunas, carrinho, 4 formas pagamento), GatewayDemonstracao (8 produtos mock), DemoLauncher | 59 |
| **1B** | Infraestrutura: ConexaoManager (SQLite WAL, FK, busy_timeout), MigrationService (3 versões: v1 legado → v2 completo → v3 índices), BackupService (VACUUM INTO, integrity_check), 6 Services (Produto, Venda, Caixa, Usuario, Cliente, Configuracao) | 158 |
| **2** | PDV Real com SQLite: PdvGatewayReal, RealPdvLauncher, integração completa busca→grade→carrinho→pagamento→venda→estoque, validação caixa aberto, desconto, rollback transacional | 166 |

### Novos Pacotes v2.0

```
src/main/java/br/com/adriano/
├── backup/          # BackupService
├── db/              # ConexaoManager
├── demo/            # DemoLauncher, GatewayDemonstracao, RealPdvLauncher
├── migration/       # MigrationService
├── service/         # 7 Services de domínio
├── ui/
│   ├── componentes/ # 7 componentes reutilizáveis
│   ├── pdv/         # Modelo PDV independente de Swing
│   ├── shell/       # Shell, PainelPdv
│   └── tema/        # GerenciadorTema (CLARO/ESCURO)
```

### Entry Points v2.0
- `DemoLauncher` — UI demonstrativa (dados em memória, sem banco)
- `RealPdvLauncher` — PDV real com SQLite (migração automática, banco temporário/persistente)

### Testes
```bash
mvn test
# 166 testes, 0 falhas
```

### Build Executável
```bash
mvn package
# Gera target/MiniComercio-2.0.0-SNAPSHOT-executavel.jar
java -jar target/MiniComercio-2.0.0-SNAPSHOT-executavel.jar [admin|operador]
```

---

## 🛠️ Como Executar

### Pré-requisitos
- JDK 21+
- Maven 3.9+

### v1.0 (Legado)
```bash
mvn compile exec:java -Dexec.mainClass=br.com.adriano.Main
```

### v2.0 Demo (sem banco)
```bash
mvn compile exec:java -Dexec.mainClass=br.com.adriano.demo.DemoLauncher
# ou
java -cp target/classes br.com.adriano.demo.DemoLauncher [admin|operador]
```

### v2.0 PDV Real (com SQLite)
```bash
mvn compile exec:java -Dexec.mainClass=br.com.adriano.demo.RealPdvLauncher
```

---

## 📁 Estrutura de Branches

| Branch | Conteúdo |
|--------|----------|
| `main` | Versão 1.0 estável (produção) |
| `desenvolvimento-2.0` | Versão 2.0 em desenvolvimento (Fases 1A, 1B, 2 concluídas) |

---

## 📋 Documentação Técnica

- `PROGRESSO-DESENVOLVIMENTO.md` — Relatório detalhado de marcos, testes, correções e próximas fases
- `docs/DECISOES-E-DEPENDENCIAS.md` — Decisões de arquitetura, isolamento, POM, ambiente

---

## ⚠️ Observações Importantes

1. **Não commitar bancos de dados** — `comercio.db` e arquivos `.sqlite` devem ficar fora do controle de versão
2. **Legado intocado** — Arquivos v1.0 em `dao/`, `model/`, `util/`, `view/`, `Main.java` são preservados byte-a-byte
3. **Tema escuro** — Validação visual pendente (homologação manual no IntelliJ)
4. **Fases 3-6** — Planejadas: Produtos & Estoque, Caixa & Relatórios, Clientes/Usuários/Config, Instalador & Entrega

---

## 📄 Licença

Uso interno — Mini Comércio 2.0 "Caldos da Ana"