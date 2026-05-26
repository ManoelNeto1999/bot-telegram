package br.com.tdm.whatsappsaldo.controller;

import br.com.tdm.whatsappsaldo.dto.TelegramSetWebhookRequest;
import br.com.tdm.whatsappsaldo.service.TelegramWebhookManagementService;
import br.com.tdm.whatsappsaldo.service.TelegramWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final TelegramWebhookService telegramWebhookService;
    private final TelegramWebhookManagementService telegramWebhookManagementService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> receberWebhook(@RequestBody JsonNode payload) {
        telegramWebhookService.processarUpdate(payload);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/set-webhook")
    public ResponseEntity<Map<String, Object>> setWebhook(@Valid @RequestBody TelegramSetWebhookRequest request) {
        JsonNode respostaTelegram = telegramWebhookManagementService.registrarWebhook(request.url().trim());
        Map<String, Object> resposta = Map.of(
                "message", "Webhook registrado com sucesso.",
                "telegramResponse", respostaTelegram
        );
        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/webhook-info")
    public ResponseEntity<Map<String, Object>> webhookInfo() {
        JsonNode respostaTelegram = telegramWebhookManagementService.consultarWebhookInfo();
        Map<String, Object> resposta = Map.of(
                "message", "Webhook info obtido com sucesso.",
                "telegramResponse", respostaTelegram
        );
        return ResponseEntity.ok(resposta);
    }
}
