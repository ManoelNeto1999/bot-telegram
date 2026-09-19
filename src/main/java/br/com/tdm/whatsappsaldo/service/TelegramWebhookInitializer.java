package br.com.tdm.whatsappsaldo.service;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
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
            JsonNode result = webhookInfo.path("result");
            String currentUrl = result.path("url").asText("").trim();

            if (desiredUrl.equals(currentUrl) && allowedUpdatesCorretos(result.path("allowed_updates"))) {
                log.info("Webhook do Telegram ja esta configurado com URL e allowed_updates esperados.");
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

    static boolean allowedUpdatesCorretos(JsonNode allowedUpdates) {
        if (allowedUpdates == null || !allowedUpdates.isArray()
                || allowedUpdates.size() != TelegramWebhookManagementService.EXPECTED_ALLOWED_UPDATES.size()) {
            return false;
        }

        Set<String> current = new HashSet<>();
        allowedUpdates.forEach(item -> current.add(item.asText("")));
        return current.equals(new HashSet<>(TelegramWebhookManagementService.EXPECTED_ALLOWED_UPDATES));
    }
}
