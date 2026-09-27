package br.com.tdm.whatsappsaldo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TelegramWebhookInitializer {

    private final TelegramWebhookReconciler reconciler;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeWebhook() {
        reconciler.reconcile();
    }
}
