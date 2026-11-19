# Mini Comércio 2.0 — decisões de desenvolvimento

## Isolamento

- Trabalho em diretório isolado `desenvolvimento-2.0`, sem repositório Git, commits, push ou merge.
- Pacote fornecido preservado em `entrada-pacote-original`; fontes 1.0 extraídas em `legado-preservado`.
- Nenhum banco acompanha o pacote. Não localizar nem abrir o banco do cliente.
- `Main.java`, `view/Tela*`, DAOs e utilitários originais devem permanecer byte a byte intactos. A versão nova usa outro ponto de entrada e serviços próprios.
- A autorização de 18/11/2026 permite todas as fases e alterações no POM da cópia. Não permite implantação, migração real ou substituição do Main original.

## Alterações do pom.xml de desenvolvimento

O POM original está integralmente preservado no legado e no ZIP de entrada.

1. Versão do projeto: `1.0-SNAPSHOT` → `2.0.0-SNAPSHOT`.
2. Compilação Java `release 21`, UTF-8 e maven-compiler-plugin 3.13.0.
3. Mantido sqlite-jdbc 3.45.2.0 para compatibilidade com a referência; atualização e auditoria de vulnerabilidades são tarefas de pré-produção.
4. Adicionado FlatLaf 3.7.2 (existência confirmada nos metadados do Maven Central em 18/11/2026). Não é necessário flatlaf-extras para os componentes iniciais.
5. Adicionado JUnit Jupiter 5.11.4, escopo teste, e Surefire 3.5.2.
6. Adicionado slf4j-nop 1.7.36 para o provedor de log do driver SQLite; auditoria da aplicação não depende de SLF4J.
7. Adicionado Shade 3.6.0: artefato `-executavel.jar`, serviços JDBC mesclados, assinaturas de dependências excluídas e manifesto Multi-Release. O Main do legado não é o ponto de entrada do executável novo.
8. `app.main` permite escolher o launcher novo sem editar o Main original.

## Ambiente observado

- Windows 10, JDK Oracle 21.0.12.1, compilador disponível.
- Maven não encontrado no PATH; Maven 3.9.9 baixado do Maven Central em pasta separada, SHA-512 comparado ao checksum publicado, sem alteração global do sistema.
- `jpackage.exe` disponível no JDK. WiX `candle`/`light` não encontrado no PATH na inspeção inicial. A disponibilidade de jpackage não comprova geração nem instalação de um instalador.

## Critério de relato

Uma tela visível não comprova um módulo implementado. Relatórios dos marcos devem distinguir testes executados, limitações e validações manuais pendentes. Dados, preços e credenciais de teste nunca são dados reais do cliente.
