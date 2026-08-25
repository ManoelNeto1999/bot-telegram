package br.com.tdm.whatsappsaldo.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramWebhookInitializerTest {

    private static final String WEBHOOK_URL = "https://bot.example.com/api/telegram/webhook";

    @Mock
    private TelegramWebhookManagementService managementService;

    private TelegramProperties telegramProperties;
    private TelegramWebhookInitializer initializer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        telegramProperties = new TelegramProperties();
        telegramProperties.getBot().setToken("token-teste");
        telegramProperties.getWebhook().setUrl(WEBHOOK_URL);
        telegramProperties.getWebhook().setSecret("segredo-teste");
        telegramProperties.getWebhook().setRequireSecret(true);
        telegramProperties.getWebhook().setAutoRegister(true);
        initializer = new TelegramWebhookInitializer(telegramProperties, managementService);
        objectMapper = new ObjectMapper();
    }

    @Test
    void naoDeveConsultarTelegramQuandoAutoRegistroEstaDesabilitado() {
        telegramProperties.getWebhook().setAutoRegister(false);

        initializer.initializeWebhook();

        verify(managementService, never()).consultarWebhookInfo();
    }

    @Test
    void naoDeveRegistrarNovamenteQuandoUrlJaEstaCorreta() throws Exception {
        when(managementService.consultarWebhookInfo()).thenReturn(objectMapper.readTree("""
                {"ok":true,"result":{"url":"https://bot.example.com/api/telegram/webhook"}}
                """));

        initializer.initializeWebhook();

        verify(managementService, never()).registrarWebhook(WEBHOOK_URL);
    }

    @Test
    void deveRegistrarQuandoUrlAtualEhDiferente() throws Exception {
        when(managementService.consultarWebhookInfo()).thenReturn(objectMapper.readTree("""
                {"ok":true,"result":{"url":"https://old.example.com/api/telegram/webhook"}}
                """));
        when(managementService.registrarWebhook(WEBHOOK_URL)).thenReturn(objectMapper.readTree("{" +
                "\"ok\":true}"));

        initializer.initializeWebhook();

        verify(managementService).registrarWebhook(WEBHOOK_URL);
    }

    @Test
    void falhaNaConsultaNaoDeveDerrubarAplicacao() {
        when(managementService.consultarWebhookInfo()).thenThrow(new IllegalStateException("falha simulada"));

        assertThatCode(initializer::initializeWebhook).doesNotThrowAnyException();
    }
}
