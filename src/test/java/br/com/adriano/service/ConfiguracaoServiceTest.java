package br.com.adriano.service;

import br.com.adriano.ui.tema.GerenciadorTema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfiguracaoService - configurações do sistema")
class ConfiguracaoServiceTest extends BaseServiceTest {

    private ConfiguracaoService service;

    @BeforeEach
    @Override
    void setUp() throws Exception {
        super.setUp();
        service = new ConfiguracaoService(getConexaoManager());
    }

    @Test
    @DisplayName("Deve obter e definir configuração string")
    void getSetString() throws Exception {
        assertTrue(service.get("chave_teste").isEmpty());

        service.set("chave_teste", "valor_teste");
        Optional<String> valor = service.get("chave_teste");

        assertTrue(valor.isPresent());
        assertEquals("valor_teste", valor.get());
    }

    @Test
    @DisplayName("Deve retornar padrão quando chave não existe")
    void getOrDefault() throws Exception {
        String valor = service.getOrDefault("inexistente", "padrao");
        assertEquals("padrao", valor);
    }

    @Test
    @DisplayName("Deve sobrescrever valor existente")
    void sobrescreverValor() throws Exception {
        service.set("chave", "valor1");
        service.set("chave", "valor2");

        assertEquals("valor2", service.get("chave").orElseThrow());
    }

    @Test
    @DisplayName("Deve retornar todas as configurações")
    void getAll() throws Exception {
        service.set("chave1", "valor1");
        service.set("chave2", "valor2");

        Map<String, String> todas = service.getAll();

        assertTrue(todas.size() >= 8); // 6 padrão + 2 novas
        assertEquals("valor1", todas.get("chave1"));
        assertEquals("valor2", todas.get("chave2"));
    }

    @Test
    @DisplayName("Deve gerenciar tema")
    void gerenciarTema() throws Exception {
        // Padrão é CLARO
        assertEquals(GerenciadorTema.Tema.CLARO, service.getTema());

        service.setTema(GerenciadorTema.Tema.ESCURO);
        assertEquals(GerenciadorTema.Tema.ESCURO, service.getTema());

        service.setTema(GerenciadorTema.Tema.CLARO);
        assertEquals(GerenciadorTema.Tema.CLARO, service.getTema());
    }

    @Test
    @DisplayName("Deve gerenciar configurações monetárias")
    void configuracoesMonetarias() throws Exception {
        assertEquals("BRL", service.getMoeda());
        assertEquals(2, service.getCasasDecimais());
    }

    @Test
    @DisplayName("Deve gerenciar timezone")
    void timezone() throws Exception {
        assertEquals("America/Sao_Paulo", service.getTimezone());
    }

    @Test
    @DisplayName("Deve gerenciar backup automático")
    void backupAuto() throws Exception {
        assertTrue(service.isBackupAuto());

        service.setBackupAuto(false);
        assertFalse(service.isBackupAuto());

        service.setBackupAuto(true);
        assertTrue(service.isBackupAuto());
    }

    @Test
    @DisplayName("Deve gerenciar retenção de backup")
    void backupRetencao() throws Exception {
        assertEquals(30, service.getBackupRetencaoDias());

        service.setBackupRetencaoDias(60);
        assertEquals(60, service.getBackupRetencaoDias());

        // Não deve permitir menor que 1
        service.setBackupRetencaoDias(-5);
        assertEquals(1, service.getBackupRetencaoDias());
    }

    @Test
    @DisplayName("Configurações padrão devem existir após migração")
    void configuracoesPadraoExistem() throws Exception {
        assertEquals("CLARO", service.getOrDefault("tema", "ERRO"));
        assertEquals("BRL", service.getOrDefault("moeda", "ERRO"));
        assertEquals("2", service.getOrDefault("casas_decimais", "ERRO"));
        assertEquals("America/Sao_Paulo", service.getOrDefault("timezone", "ERRO"));
        assertEquals("true", service.getOrDefault("backup_auto", "ERRO"));
        assertEquals("30", service.getOrDefault("backup_retencao_dias", "ERRO"));
    }
}