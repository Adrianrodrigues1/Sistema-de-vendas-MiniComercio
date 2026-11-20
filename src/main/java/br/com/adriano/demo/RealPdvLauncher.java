package br.com.adriano.demo;

import br.com.adriano.db.ConexaoManager;
import br.com.adriano.migration.MigrationService;
import br.com.adriano.service.PdvGatewayReal;
import br.com.adriano.service.ProdutoService;
import br.com.adriano.ui.shell.Shell;
import br.com.adriano.ui.tema.GerenciadorTema;
import javax.swing.*;

/**
 * Ponto de entrada do PDV Real (Fase 4: Caixa/Relatórios) com SQLite.
 * <p>
 * Diferente do DemoLauncher, esta versão usa banco de dados real,
 * migrações automáticas e serviços de domínio completos.
 *
 * Uso: java -cp target/MiniComercio-2.0.0-SNAPSHOT-executavel.jar br.com.adriano.demo.RealPdvLauncher [admin|operador] [memoria|arquivo]
 */
public class RealPdvLauncher {

    public static void main(String[] args) {
        boolean modoOperador = args.length > 0 && args[0].equalsIgnoreCase("operador");
        String perfil = modoOperador ? "OPERADOR" : "ADMIN";

        // Tipo de banco: memoria (padrão, arquivo temporário) ou arquivo (persistente)
        String tipoBanco = args.length > 1 ? args[1] : "memoria";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  Mini Comércio 2.0 — Caldos da Ana (Fase 4: Caixa/Relatórios)║");
        System.out.println("║  Perfil: " + String.format("%-50s", perfil) + "║");
        System.out.println("║  Banco:  " + String.format("%-50s", tipoBanco.toUpperCase()) + "║");
        System.out.println("║  Dados:  REAIS (SQLite) — Vendas são persistidas             ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        // Instala tema inicial antes de criar componentes
        GerenciadorTema.instalar(GerenciadorTema.Tema.CLARO);

        SwingUtilities.invokeLater(() -> {
            try {
                ConexaoManager conexaoManager;
                if ("arquivo".equalsIgnoreCase(tipoBanco)) {
                    conexaoManager = ConexaoManager.paraArquivo("comercio.db");
                    System.out.println("[INFO] Usando banco persistente: comercio.db");
                } else {
                    conexaoManager = ConexaoManager.emMemoria();
                    System.out.println("[INFO] Usando banco temporário em arquivo (memória compartilhada)");
                }

                // Executar migrações
                System.out.println("[INFO] Executando migrações de schema...");
                MigrationService migrationService = new MigrationService(conexaoManager);
                int executadas = migrationService.migrar();
                System.out.println("[INFO] Migrações executadas: " + executadas + " (versão atual: " + migrationService.obterVersaoAtual() + ")");

                // Criar serviços de domínio
                ProdutoService produtoService = new ProdutoService(conexaoManager);

                // Criar gateway real
                PdvGatewayReal gateway = new PdvGatewayReal(conexaoManager);

                // Criar e mostrar shell com todos os serviços
                Shell shell = new Shell(gateway, produtoService, conexaoManager, modoOperador);
                shell.setVisible(true);

                // Hook para fechar conexões ao sair
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    System.out.println("[INFO] Encerrando aplicação...");
                }));

            } catch (Exception ex) {
                System.err.println("[ERRO] Falha ao inicializar PDV Real: " + ex.getMessage());
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        "Erro ao inicializar: " + ex.getMessage(),
                        "Erro de Inicialização",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}