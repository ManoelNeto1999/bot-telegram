package br.com.tdm.whatsappsaldo.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramWebhookReconcilerTest {

    private static final String WEBHOOK_URL = "https://bot.example.com/api/telegram/webhook";

    @Mock
    private TelegramWebhookManagementService managementService;

    private TelegramProperties telegramProperties;
    private TelegramWebhookReconciler reconciler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        telegramProperties = new TelegramProperties();
        telegramProperties.getBot().setToken("token-teste");
        telegramProperties.getWebhook().setUrl(WEBHOOK_URL);
        telegramProperties.getWebhook().setSecret("segredo-teste");
        telegramProperties.getWebhook().setRequireSecret(true);
        telegramProperties.getWebhook().setAutoRegister(true);
        reconciler = new TelegramWebhookReconciler(telegramProperties, managementService);
        objectMapper = new ObjectMapper();
    }

    @Test
    void naoDeveConsultarTelegramQuandoAutoRegistroEstaDesabilitado() {
        telegramProperties.getWebhook().setAutoRegister(false);

        reconciler.reconcile();

        verify(managementService, never()).consultarWebhookInfo();
    }

    @Test
    void naoDeveRegistrarNovamenteQuandoUrlJaEstaCorreta() throws Exception {
        when(managementService.consultarWebhookInfo()).thenReturn(objectMapper.readTree("""
                {"ok":true,"result":{
                  "url":"https://bot.example.com/api/telegram/webhook",
                  "allowed_updates":[
                    "message",
                    "business_connection",
                    "business_message",
                    "edited_business_message",
                    "deleted_business_messages"
                  ]
                }}
                """));

        reconciler.reconcile();

        verify(managementService, never()).registrarWebhook(WEBHOOK_URL);
    }

    @Test
    void ordemDiferenteDeAllowedUpdatesContinuaCorreta() throws Exception {
        when(managementService.consultarWebhookInfo()).thenReturn(objectMapper.readTree("""
                {"ok":true,"result":{
                  "url":"https://bot.example.com/api/telegram/webhook",
                  "allowed_updates":[
                    "deleted_business_messages",
                    "business_message",
                    "message",
                    "edited_business_message",
                    "business_connection"
                  ]
                }}
                """));

        reconciler.reconcile();

        verify(managementService, never()).registrarWebhook(WEBHOOK_URL);
    }

    @Test
    void deveRegistrarQuandoUrlIgualMasAllowedUpdatesDiferem() throws Exception {
        when(managementService.consultarWebhookInfo()).thenReturn(objectMapper.readTree("""
                {"ok":true,"result":{
                  "url":"https://bot.example.com/api/telegram/webhook",
                  "allowed_updates":["message"]
                }}
                """));
        when(managementService.registrarWebhook(WEBHOOK_URL))
                .thenReturn(objectMapper.readTree("{\"ok\":true}"));

        reconciler.reconcile();

        verify(managementService).registrarWebhook(WEBHOOK_URL);
    }

    @Test
    void deveRegistrarQuandoUrlAtualEhDiferente() throws Exception {
        when(managementService.consultarWebhookInfo()).thenReturn(objectMapper.readTree("""
                {"ok":true,"result":{"url":"https://old.example.com/api/telegram/webhook"}}
                """));
        when(managementService.registrarWebhook(WEBHOOK_URL)).thenReturn(objectMapper.readTree("{" +
                "\"ok\":true}"));

        reconciler.reconcile();

        verify(managementService).registrarWebhook(WEBHOOK_URL);
    }

    @Test
    void deveRegistrarQuandoUrlEstaVazia() throws Exception {
        when(managementService.consultarWebhookInfo()).thenReturn(objectMapper.readTree("""
                {"ok":true,"result":{"url":"","pending_update_count":3}}
                """));
        when(managementService.registrarWebhook(WEBHOOK_URL))
                .thenReturn(objectMapper.readTree("{\"ok\":true}"));

        reconciler.reconcile();

        verify(managementService).registrarWebhook(WEBHOOK_URL);
    }

    @Test
    void falhaNaConsultaNaoDeveDerrubarAplicacao() {
        when(managementService.consultarWebhookInfo()).thenThrow(new IllegalStateException("falha simulada"));

        assertThatCode(reconciler::reconcile).doesNotThrowAnyException();
        verify(managementService, never()).registrarWebhook(WEBHOOK_URL);
    }

    @Test
    void falhaNoRegistroNaoDeveDerrubarAplicacao() throws Exception {
        when(managementService.consultarWebhookInfo()).thenReturn(objectMapper.readTree("""
                {"ok":true,"result":{"url":""}}
                """));
        when(managementService.registrarWebhook(WEBHOOK_URL))
                .thenThrow(new IllegalStateException("falha simulada"));

        assertThatCode(reconciler::reconcile).doesNotThrowAnyException();
    }

    @Test
    void falhaNaConsultaNaoRegistraMesmoComRespostaNaoConfirmada() throws Exception {
        when(managementService.consultarWebhookInfo()).thenReturn(objectMapper.readTree("{\"ok\":false}"));

        reconciler.reconcile();

        verify(managementService, never()).registrarWebhook(WEBHOOK_URL);
    }

    @Test
    void ciclosSimultaneosNaoGeramRegistrosDuplicados() throws Exception {
        CountDownLatch registroIniciado = new CountDownLatch(1);
        CountDownLatch liberarRegistro = new CountDownLatch(1);
        when(managementService.consultarWebhookInfo()).thenReturn(objectMapper.readTree("""
                {"ok":true,"result":{"url":""}}
                """));
        doAnswer(invocation -> {
            registroIniciado.countDown();
            if (!liberarRegistro.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Registro nao foi liberado");
            }
            return objectMapper.readTree("{\"ok\":true}");
        }).when(managementService).registrarWebhook(WEBHOOK_URL);

        Thread firstCycle = new Thread(reconciler::reconcile);
        firstCycle.start();
        try {
            assertThat(registroIniciado.await(5, TimeUnit.SECONDS)).isTrue();
            reconciler.reconcile();
            verify(managementService, times(1)).consultarWebhookInfo();
            verify(managementService, times(1)).registrarWebhook(WEBHOOK_URL);
        } finally {
            liberarRegistro.countDown();
            firstCycle.join(5000);
        }
        assertThat(firstCycle.isAlive()).isFalse();
    }
}
