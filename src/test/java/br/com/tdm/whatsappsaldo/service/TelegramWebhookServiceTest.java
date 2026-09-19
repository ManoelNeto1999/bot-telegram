package br.com.tdm.whatsappsaldo.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import br.com.tdm.whatsappsaldo.dto.MensagemSimuladaRequest;
import br.com.tdm.whatsappsaldo.dto.MensagemSimuladaResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TelegramWebhookServiceTest {

    @Test
    void deveProcessarNegocioUmaVezEPropagarUpdateIdAoEnvio() throws Exception {
        SaldoBotService saldoBotService = mock(SaldoBotService.class);
        TelegramSendMessageService sendMessageService = mock(TelegramSendMessageService.class);
        TelegramWebhookService webhookService = new TelegramWebhookService(
                saldoBotService,
                sendMessageService
        );
        JsonNode update = new ObjectMapper().readTree("""
                {
                  "update_id": 482716510,
                  "message": {
                    "chat": {"id": 12345},
                    "text": "Usei credito 30"
                  }
                }
                """);
        when(saldoBotService.processarMensagem(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new MensagemSimuladaResponse(
                        "12345",
                        "Usei credito 30",
                        "resposta pronta",
                        BigDecimal.ZERO
                ));

        webhookService.processarUpdate(update);

        ArgumentCaptor<MensagemSimuladaRequest> requestCaptor =
                ArgumentCaptor.forClass(MensagemSimuladaRequest.class);
        verify(saldoBotService, times(1)).processarMensagem(requestCaptor.capture());
        verify(sendMessageService, times(1))
                .enviarMensagem(482716510L, "12345", "resposta pronta");
    }

    @Test
    void deveProcessarNegocioUmaVezMesmoQuandoEnvioFazRetry() throws Exception {
        SaldoBotService saldoBotService = mock(SaldoBotService.class);
        TelegramProperties properties = new TelegramProperties();
        properties.getBot().setToken("123456:token-secreto");
        properties.getBot().setApiUrl("https://api.telegram.test");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBot().getApiUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TelegramSendMessageService sendMessageService = new TelegramSendMessageService(
                properties,
                builder.build(),
                millis -> {
                }
        );
        TelegramWebhookService webhookService = new TelegramWebhookService(
                saldoBotService,
                sendMessageService
        );
        JsonNode update = new ObjectMapper().readTree("""
                {
                  "update_id": 482716511,
                  "message": {
                    "chat": {"id": 12345},
                    "text": "Usei credito 30"
                  }
                }
                """);
        when(saldoBotService.processarMensagem(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new MensagemSimuladaResponse(
                        "12345",
                        "Usei credito 30",
                        "resposta pronta",
                        BigDecimal.ZERO
                ));
        String sendMessageUrl =
                "https://api.telegram.test/bot123456%3Atoken-secreto/sendMessage";
        server.expect(requestTo(sendMessageUrl))
                .andRespond(withException(new IOException("Connection reset")));
        server.expect(requestTo(sendMessageUrl))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        webhookService.processarUpdate(update);

        verify(saldoBotService, times(1))
                .processarMensagem(org.mockito.ArgumentMatchers.any());
        server.verify();
    }
}
