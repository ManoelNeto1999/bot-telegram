package br.com.tdm.whatsappsaldo.service;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramWebhookManagementService {

    private static final String TOKEN_NAO_CONFIGURADO = "telegram.bot-token n\u00E3o est\u00E1 configurado.";

    private final TelegramProperties telegramProperties;
    private final RestClient.Builder restClientBuilder;

    public JsonNode registrarWebhook(String url) {
        validarTokenConfigurado();

        try {
            JsonNode resposta = restClient().post()
                    .uri("/bot{token}/setWebhook", telegramProperties.getBotToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("url", url))
                    .retrieve()
                    .body(JsonNode.class);

            log.info("Webhook registrado com sucesso para a URL {}.", url);
            return resposta;
        } catch (RestClientResponseException ex) {
            log.error(
                    "Erro ao registrar webhook. Status: {}. Body: {}",
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString()
            );
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Erro ao registrar webhook no Telegram."
            );
        } catch (Exception ex) {
            log.error(
                    "Erro inesperado ao registrar webhook. Tipo: {}",
                    ex.getClass().getSimpleName()
            );
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Erro ao registrar webhook no Telegram."
            );
        }
    }

    public JsonNode consultarWebhookInfo() {
        validarTokenConfigurado();

        try {
            return restClient().get()
                    .uri("/bot{token}/getWebhookInfo", telegramProperties.getBotToken())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            log.error(
                    "Erro ao consultar webhook-info. Status: {}. Body: {}",
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString()
            );
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Erro ao consultar webhook-info no Telegram."
            );
        } catch (Exception ex) {
            log.error(
                    "Erro inesperado ao consultar webhook-info. Tipo: {}",
                    ex.getClass().getSimpleName()
            );
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Erro ao consultar webhook-info no Telegram."
            );
        }
    }

    private void validarTokenConfigurado() {
        if (!telegramProperties.hasTokenConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, TOKEN_NAO_CONFIGURADO);
        }
    }

    private RestClient restClient() {
        return restClientBuilder
                .baseUrl(telegramProperties.getApiUrl())
                .build();
    }
}
