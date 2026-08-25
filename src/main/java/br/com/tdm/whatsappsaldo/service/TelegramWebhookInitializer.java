package br.com.tdm.whatsappsaldo.service;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramWebhookInitializer {

    private final TelegramProperties telegramProperties;
    private final TelegramWebhookManagementService telegramWebhookManagementService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeWebhook() {
        TelegramProperties.Webhook webhook = telegramProperties.getWebhook();

        if (!webhook.isAutoRegister()) {
            log.info("Registro automatico do webhook do Telegram esta desabilitado.");
            return;
        }
        if (!telegramProperties.hasTokenConfigured()) {
            log.error("Webhook do Telegram nao registrado: token do bot nao configurado.");
            return;
        }
        if (!webhook.hasUrlConfigured()) {
            log.error("Webhook do Telegram nao registrado: URL publica nao configurada.");
            return;
        }
        if (webhook.isRequireSecret() && !webhook.hasValidSecret()) {
            log.error("Webhook do Telegram nao registrado: secret token obrigatorio ausente ou invalido.");
            return;
        }

        String desiredUrl = webhook.getUrl().trim();
        if (!desiredUrl.startsWith("https://")) {
            log.error("Webhook do Telegram nao registrado: URL publica deve usar HTTPS.");
            return;
        }

        try {
            JsonNode webhookInfo = telegramWebhookManagementService.consultarWebhookInfo();
            String currentUrl = webhookInfo.path("result").path("url").asText("").trim();

            if (desiredUrl.equals(currentUrl)) {
                log.info("Webhook do Telegram ja esta configurado com a URL esperada.");
                return;
            }

            JsonNode registrationResponse = telegramWebhookManagementService.registrarWebhook(desiredUrl);
            if (registrationResponse != null && registrationResponse.path("ok").asBoolean(false)) {
                log.info("Webhook do Telegram registrado automaticamente com sucesso.");
            } else {
                log.error("Telegram nao confirmou o registro automatico do webhook.");
            }
        } catch (Exception ex) {
            log.error(
                    "Falha no registro automatico do webhook do Telegram. A aplicacao continuara em execucao. Tipo: {}",
                    ex.getClass().getSimpleName()
            );
        }
    }
}
