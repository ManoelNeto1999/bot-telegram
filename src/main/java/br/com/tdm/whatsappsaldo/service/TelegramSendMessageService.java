package br.com.tdm.whatsappsaldo.service;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramSendMessageService {

    private final TelegramProperties telegramProperties;
    private final RestClient.Builder restClientBuilder;

    public void enviarMensagem(String chatId, String mensagem) {
        if (!telegramProperties.hasTokenConfigured()) {
            log.info("Telegram nao configurado. Envio de resposta ignorado.");
            return;
        }

        Map<String, Object> payload = Map.of(
                "chat_id", chatId,
                "text", mensagem
        );

        try {
            JsonNode resposta = restClient().post()
                    .uri("/bot{token}/sendMessage", telegramProperties.getBotToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);

            log.info("Resposta enviada com sucesso pelo Telegram.");
            log.debug("Telegram confirmou sendMessage: {}", resposta != null && resposta.path("ok").asBoolean(false));
        } catch (RestClientResponseException ex) {
            log.error(
                    "Erro ao enviar resposta pelo Telegram. Status: {}.",
                    ex.getStatusCode()
            );
        } catch (Exception ex) {
            log.error(
                    "Erro inesperado ao enviar resposta pelo Telegram. Tipo: {}",
                    ex.getClass().getSimpleName()
            );
        }
    }

    private RestClient restClient() {
        return restClientBuilder
                .baseUrl(telegramProperties.getApiUrl())
                .build();
    }
}
