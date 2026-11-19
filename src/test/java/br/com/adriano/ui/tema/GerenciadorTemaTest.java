package br.com.adriano.ui.tema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GerenciadorTema - alternância de temas claro/escuro")
class GerenciadorTemaTest {

    @Test
    @DisplayName("Tema padrão deve ser CLARO")
    void temaPadraoClaro() {
        // Garantir estado inicial
        GerenciadorTema.instalar(GerenciadorTema.Tema.CLARO);
        assertEquals(GerenciadorTema.Tema.CLARO, GerenciadorTema.atual());
    }

    @Test
    @DisplayName("Deve alternar para ESCURO")
    void alternarParaEscuro() {
        GerenciadorTema.instalar(GerenciadorTema.Tema.ESCURO);
        assertEquals(GerenciadorTema.Tema.ESCURO, GerenciadorTema.atual());
    }

    @Test
    @DisplayName("Deve retornar cores corretas para tema claro")
    void coresTemaClaro() {
        GerenciadorTema.instalar(GerenciadorTema.Tema.CLARO);

        assertEquals(new Color(0xFBFAFA), GerenciadorTema.cor("fundo"));
        assertEquals(new Color(0xF4F0F0), GerenciadorTema.cor("sidebar"));
        assertEquals(new Color(0xFFFFFF), GerenciadorTema.cor("painel"));
        assertEquals(new Color(0x7F7266), GerenciadorTema.cor("primario"));
        assertEquals(new Color(0x28231F), GerenciadorTema.cor("texto"));
        assertEquals(new Color(0xDFD2C7), GerenciadorTema.cor("destaque"));
    }

    @Test
    @DisplayName("Deve retornar cores corretas para tema escuro")
    void coresTemaEscuro() {
        GerenciadorTema.instalar(GerenciadorTema.Tema.ESCURO);

        assertEquals(new Color(0x1C1B1C), GerenciadorTema.cor("fundo"));
        assertEquals(new Color(0x181618), GerenciadorTema.cor("sidebar"));
        assertEquals(new Color(0x1F1E1F), GerenciadorTema.cor("painel"));
        assertEquals(new Color(0xA07549), GerenciadorTema.cor("primario"));
        assertEquals(new Color(0xF5F2EC), GerenciadorTema.cor("texto"));
        assertEquals(new Color(0xBC9162), GerenciadorTema.cor("destaque"));
    }

    @Test
    @DisplayName("Deve lançar exceção para papel de cor desconhecido")
    void corDesconhecida() {
        assertThrows(IllegalArgumentException.class, () -> GerenciadorTema.cor("inexistente"));
    }

    @Test
    @DisplayName("Cores de status devem ser diferentes entre temas")
    void coresStatusDiferentes() {
        GerenciadorTema.instalar(GerenciadorTema.Tema.CLARO);
        Color statusClaro = GerenciadorTema.cor("status");
        Color statusTextoClaro = GerenciadorTema.cor("statusTexto");

        GerenciadorTema.instalar(GerenciadorTema.Tema.ESCURO);
        Color statusEscuro = GerenciadorTema.cor("status");
        Color statusTextoEscuro = GerenciadorTema.cor("statusTexto");

        assertNotEquals(statusClaro, statusEscuro);
        assertNotEquals(statusTextoClaro, statusTextoEscuro);
    }

    @Test
    @DisplayName("Cores sobre fundo devem ter contraste")
    void contrasteSobreFundo() {
        GerenciadorTema.instalar(GerenciadorTema.Tema.CLARO);
        Color fundoClaro = GerenciadorTema.cor("fundo");
        Color sobreDestaqueClaro = GerenciadorTema.cor("sobreDestaque");

        GerenciadorTema.instalar(GerenciadorTema.Tema.ESCURO);
        Color fundoEscuro = GerenciadorTema.cor("fundo");
        Color sobreDestaqueEscuro = GerenciadorTema.cor("sobreDestaque");

        // Verificar que as cores sobre fundo são diferentes do fundo
        assertNotEquals(fundoClaro, sobreDestaqueClaro);
        assertNotEquals(fundoEscuro, sobreDestaqueEscuro);
    }

    @Test
    @DisplayName("trocar() deve alternar tema em árvore de componentes sem exceção")
    void trocarTemaEmArvoreComponentes() {
        // Criar uma hierarquia simples de componentes para testar
        JPanel raiz = new JPanel(new BorderLayout());
        JPanel painel1 = new JPanel();
        JPanel painel2 = new JPanel(new FlowLayout());
        JButton botao = new JButton("Teste");
        JLabel label = new JLabel("Label");
        JTextField campo = new JTextField(10);

        painel2.add(botao);
        painel2.add(label);
        painel2.add(campo);
        painel1.add(painel2);
        raiz.add(painel1, BorderLayout.CENTER);

        // Aplicar estilo a alguns componentes
        GerenciadorTema.estilo(painel1, "painel", "texto");
        GerenciadorTema.estilo(botao, "primario", "sobrePrimario");
        GerenciadorTema.estilo(label, "painel", "muted");
        GerenciadorTema.estilo(campo, "painel", "texto");

        // Tema inicial claro
        GerenciadorTema.instalar(GerenciadorTema.Tema.CLARO);
        Color fundoClaroAntes = GerenciadorTema.cor("fundo");

        // Trocar para escuro via trocar()
        assertDoesNotThrow(() -> GerenciadorTema.trocar(GerenciadorTema.Tema.ESCURO, raiz));
        assertEquals(GerenciadorTema.Tema.ESCURO, GerenciadorTema.atual());
        Color fundoEscuroDepois = GerenciadorTema.cor("fundo");
        assertNotEquals(fundoClaroAntes, fundoEscuroDepois);

        // Trocar de volta para claro
        assertDoesNotThrow(() -> GerenciadorTema.trocar(GerenciadorTema.Tema.CLARO, raiz));
        assertEquals(GerenciadorTema.Tema.CLARO, GerenciadorTema.atual());
        Color fundoClaroDepois = GerenciadorTema.cor("fundo");
        assertEquals(fundoClaroAntes, fundoClaroDepois);
    }

    @Test
    @DisplayName("trocar() deve aceitar JFrame como raiz (Component não-JComponent)")
    void trocarTemaComJFrame() {
        JFrame frame = new JFrame("Teste");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.add(new JLabel("Teste"));
        frame.pack();

        GerenciadorTema.instalar(GerenciadorTema.Tema.CLARO);
        assertDoesNotThrow(() -> GerenciadorTema.trocar(GerenciadorTema.Tema.ESCURO, frame));
        assertEquals(GerenciadorTema.Tema.ESCURO, GerenciadorTema.atual());

        assertDoesNotThrow(() -> GerenciadorTema.trocar(GerenciadorTema.Tema.CLARO, frame));
        assertEquals(GerenciadorTema.Tema.CLARO, GerenciadorTema.atual());

        frame.dispose();
    }
}