package br.com.adriano.demo;

import br.com.adriano.ui.shell.Shell;
import br.com.adriano.ui.tema.GerenciadorTema;
import javax.swing.*;

/**
 * Ponto de entrada da demonstração da Fase 1A.
 * Não substitui o Main original (br.com.adriano.Main).
 * Executa com dados fictícios em memória, sem acessar banco de dados.
 *
 * Perfis de demonstração:
 * - ADMIN (padrão): acesso completo a todos os módulos
 * - OPERADOR: relatórios, usuários e configurações avançadas bloqueados
 *
 * Uso: java -cp target/MiniComercio-2.0.0-SNAPSHOT-executavel.jar br.com.adriano.demo.DemoLauncher [admin|operador]
 */
public class DemoLauncher {
    public static void main(String[] args) {
        boolean modoOperador = args.length > 0 && args[0].equalsIgnoreCase("operador");
        String perfil = modoOperador ? "OPERADOR" : "ADMIN";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  Mini Comércio 2.0 — Caldos da Ana (Fase 1A Demo)            ║");
        System.out.println("║  Perfil: " + String.format("%-50s", perfil) + "║");
        System.out.println("║  Dados: FICTÍCIOS — Nenhuma venda real é registrada          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        // Instala tema inicial antes de criar componentes
        GerenciadorTema.instalar(GerenciadorTema.Tema.CLARO);

        SwingUtilities.invokeLater(() -> {
            GatewayDemonstracao gateway = new GatewayDemonstracao();
            Shell shell = new Shell(gateway, modoOperador);
            shell.setVisible(true);
        });
    }
}