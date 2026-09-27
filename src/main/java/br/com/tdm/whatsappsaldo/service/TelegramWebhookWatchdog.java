package br.com.tdm.whatsappsaldo.service;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class TelegramWebhookWatchdog {

    private final TelegramProperties telegramProperties;
    private final TelegramWebhookReconciler reconciler;

    @Scheduled(
            fixedDelayString = "${telegram.webhook.watchdog-interval-ms:60000}",
            initialDelayString = "${telegram.webhook.watchdog-interval-ms:60000}"
    )
    public void checkWebhook() {
        if (telegramProperties.getWebhook().isWatchdogEnabled()) {
            reconciler.reconcile();
        }
    }
}
