package br.com.tdm.whatsappsaldo.dto;

import jakarta.validation.constraints.NotBlank;

public record TelegramSetWebhookRequest(
        @NotBlank(message = "URL do webhook e obrigatoria.")
        String url
) {
}
