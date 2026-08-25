package br.com.tdm.whatsappsaldo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.tdm.whatsappsaldo.service.TelegramWebhookSecretValidator;
import br.com.tdm.whatsappsaldo.service.TelegramWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TelegramWebhookControllerTest {

    private TelegramWebhookService webhookService;
    private TelegramWebhookSecretValidator secretValidator;
    private TelegramWebhookController controller;
    private JsonNode payload;

    @BeforeEach
    void setUp() throws Exception {
        webhookService = mock(TelegramWebhookService.class);
        secretValidator = mock(TelegramWebhookSecretValidator.class);
        controller = new TelegramWebhookController(webhookService, secretValidator);
        payload = new ObjectMapper().readTree("{\"update_id\":1}");
    }

    @Test
    void deveRejeitarWebhookSemSecretValido() {
        when(secretValidator.isRequestAuthorized(null)).thenReturn(false);

        ResponseEntity<Void> response = controller.receberWebhook(null, payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(webhookService, never()).processarUpdate(payload);
    }

    @Test
    void deveProcessarWebhookComSecretValido() {
        when(secretValidator.isRequestAuthorized("segredo-valido")).thenReturn(true);

        ResponseEntity<Void> response = controller.receberWebhook("segredo-valido", payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(webhookService).processarUpdate(payload);
    }
}
