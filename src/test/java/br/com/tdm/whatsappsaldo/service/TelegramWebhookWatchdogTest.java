package br.com.tdm.whatsappsaldo.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramWebhookWatchdogTest {

    @Mock
    private TelegramWebhookReconciler reconciler;

    private TelegramProperties properties;
    private TelegramWebhookWatchdog watchdog;

    @BeforeEach
    void setUp() {
        properties = new TelegramProperties();
        watchdog = new TelegramWebhookWatchdog(properties, reconciler);
    }

    @Test
    void watchdogDesabilitadoNaoExecutaReconciliacao() {
        watchdog.checkWebhook();

        verify(reconciler, never()).reconcile();
    }

    @Test
    void watchdogHabilitadoExecutaReconciliacao() {
        properties.getWebhook().setWatchdogEnabled(true);

        watchdog.checkWebhook();

        verify(reconciler).reconcile();
    }
}
