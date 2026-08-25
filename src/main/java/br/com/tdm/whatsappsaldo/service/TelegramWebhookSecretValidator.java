package br.com.tdm.whatsappsaldo.service;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TelegramWebhookSecretValidator {

    public static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramProperties telegramProperties;

    public boolean isRequestAuthorized(String receivedSecret) {
        TelegramProperties.Webhook webhook = telegramProperties.getWebhook();

        if (!webhook.hasSecretConfigured()) {
            return !webhook.isRequireSecret();
        }
        if (!webhook.hasValidSecret()) {
            return false;
        }
        if (receivedSecret == null || receivedSecret.isBlank()) {
            return false;
        }

        byte[] expected = webhook.getSecret().getBytes(StandardCharsets.UTF_8);
        byte[] received = receivedSecret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, received);
    }
}
