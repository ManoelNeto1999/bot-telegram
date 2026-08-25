package br.com.tdm.whatsappsaldo.controller;

import br.com.tdm.whatsappsaldo.service.TelegramWebhookSecretValidator;
import br.com.tdm.whatsappsaldo.service.TelegramWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final TelegramWebhookService telegramWebhookService;
    private final TelegramWebhookSecretValidator telegramWebhookSecretValidator;

    @PostMapping("/webhook")
    public ResponseEntity<Void> receberWebhook(
            @RequestHeader(value = TelegramWebhookSecretValidator.SECRET_HEADER, required = false) String secretHeader,
            @RequestBody JsonNode payload
    ) {
        if (!telegramWebhookSecretValidator.isRequestAuthorized(secretHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        telegramWebhookService.processarUpdate(payload);
        return ResponseEntity.ok().build();
    }
}
