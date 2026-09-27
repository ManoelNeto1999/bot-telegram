package br.com.tdm.whatsappsaldo.service;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramWebhookReconciler {

    private final TelegramProperties telegramProperties;
    private final TelegramWebhookManagementService managementService;
    private final ReentrantLock lock = new ReentrantLock();

    public void reconcile() {
        if (!lock.tryLock()) {
            log.debug("Reconciliacao do webhook do Telegram ja esta em andamento.");
            return;
        }
        try {
            reconcileLocked();
        } finally {
            lock.unlock();
        }
    }

    private void reconcileLocked() {
        TelegramProperties.Webhook webhook = telegramProperties.getWebhook();
        if (!webhook.isAutoRegister()) {
            log.debug("Registro automatico do webhook do Telegram esta desabilitado.");
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

        JsonNode webhookInfo;
        try {
            webhookInfo = managementService.consultarWebhookInfo();
        } catch (Exception ex) {
            log.error("Falha ao consultar webhook do Telegram. Tipo: {}", ex.getClass().getSimpleName());
            return;
        }
        if (webhookInfo == null || !webhookInfo.path("ok").asBoolean(false)) {
            log.error("Telegram nao confirmou a consulta do webhook.");
            return;
        }

        JsonNode result = webhookInfo.path("result");
        String currentUrl = result.path("url").asText("");
        String reason;
        if (currentUrl.isBlank()) {
            reason = "missing_url";
        } else if (!desiredUrl.equals(currentUrl)) {
            reason = "url_mismatch";
        } else if (!allowedUpdatesCorretos(result.path("allowed_updates"))) {
            reason = "allowed_updates_mismatch";
        } else {
            log.debug("Webhook do Telegram ja esta configurado corretamente.");
            return;
        }

        log.warn("Webhook do Telegram inconsistente: {}. Tentando restaurar.", reason);
        try {
            JsonNode response = managementService.registrarWebhook(desiredUrl);
            if (response != null && response.path("ok").asBoolean(false)) {
                log.info("Webhook do Telegram restaurado com sucesso.");
            } else {
                log.error("Telegram nao confirmou o registro automatico do webhook.");
            }
        } catch (Exception ex) {
            log.error("Falha ao registrar webhook do Telegram. Tipo: {}", ex.getClass().getSimpleName());
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
