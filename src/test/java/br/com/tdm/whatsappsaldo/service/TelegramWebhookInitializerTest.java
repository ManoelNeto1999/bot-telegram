package br.com.tdm.whatsappsaldo.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramWebhookInitializerTest {

    @Mock
    private TelegramWebhookReconciler reconciler;

    @InjectMocks
    private TelegramWebhookInitializer initializer;

    @Test
    void verificaWebhookNoStartup() {
        initializer.initializeWebhook();

        verify(reconciler).reconcile();
    }
}
