package br.com.tdm.whatsappsaldo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import br.com.tdm.whatsappsaldo.dto.MensagemSimuladaRequest;
import br.com.tdm.whatsappsaldo.dto.MensagemSimuladaResponse;
import br.com.tdm.whatsappsaldo.dto.TelegramBusinessConnectionState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TelegramWebhookServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SaldoBotService saldoBotService;
    private TelegramSendMessageService sendMessageService;
    private TelegramBusinessConnectionService connectionService;
    private TelegramProperties properties;
    private TelegramWebhookService webhookService;

    @BeforeEach
    void setUp() {
        saldoBotService = mock(SaldoBotService.class);
        sendMessageService = mock(TelegramSendMessageService.class);
        connectionService = mock(TelegramBusinessConnectionService.class);
        properties = new TelegramProperties();
        webhookService = service(sendMessageService);
        when(saldoBotService.processarMensagem(any())).thenReturn(response());
    }

    @Test
    void deveManterFluxoNormalEProcessarNegocioUmaVez() throws Exception {
        JsonNode update = objectMapper.readTree("""
                {
                  "update_id": 482716510,
                  "message": {
                    "message_id": 80,
                    "from": {"id": 12345},
                    "chat": {"id": 12345, "type": "private"},
                    "text": "Usei credito 30"
                  }
                }
                """);

        webhookService.processarUpdate(update);

        ArgumentCaptor<MensagemSimuladaRequest> requestCaptor =
                ArgumentCaptor.forClass(MensagemSimuladaRequest.class);
        verify(saldoBotService, times(1)).processarMensagem(requestCaptor.capture());
        verify(sendMessageService).enviarMensagem(482716510L, "12345", "resposta pronta");
        verify(sendMessageService, never()).enviarMensagem(
                482716510L, null, "12345", "resposta pronta"
        );
    }

    @Test
    void retryNormalRepeteSomenteEnvioSemBusinessConnectionId() throws Exception {
        properties.getBot().setToken("123456:token-secreto");
        properties.getBot().setApiUrl("https://api.telegram.test");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBot().getApiUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TelegramSendMessageService realSendService = new TelegramSendMessageService(
                properties,
                builder.build(),
                millis -> {
                }
        );
        webhookService = service(realSendService);
        String url = "https://api.telegram.test/bot123456%3Atoken-secreto/sendMessage";
        String payload = "{\"chat_id\":\"12345\",\"text\":\"resposta pronta\"}";
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(payload, true))
                .andRespond(withException(new IOException("Connection reset")));
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(payload, true))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
        JsonNode update = objectMapper.readTree("""
                {"update_id": 482716510, "message": {
                  "chat": {"id": 12345}, "text": "Usei credito 30"
                }}
                """);

        webhookService.processarUpdate(update);

        verify(saldoBotService, times(1)).processarMensagem(any());
        server.verify();
    }

    @Test
    void deveProcessarBusinessAutorizadoUmaVezEEnviarComConnectionId() throws Exception {
        enableBusiness("9001");
        when(connectionService.resolver("connection-1"))
                .thenReturn(Optional.of(connection(true, true, "9001")));

        webhookService.processarUpdate(businessUpdate());

        verify(saldoBotService, times(1)).processarMensagem(any());
        verify(sendMessageService).enviarMensagem(
                482716511L, "connection-1", "12345", "resposta pronta"
        );
    }

    @Test
    void featureFlagDesabilitadaBloqueiaBusinessAntesDeResolverConexao() throws Exception {
        webhookService.processarUpdate(businessUpdate());

        verify(connectionService, never()).resolver(any());
        verify(saldoBotService, never()).processarMensagem(any());
        verify(sendMessageService, never()).enviarMensagem(any(Long.class), any(), any(), any());
    }

    @Test
    void conexaoAusenteBloqueiaBusiness() throws Exception {
        enableBusiness("9001");
        when(connectionService.resolver("connection-1")).thenReturn(Optional.empty());

        webhookService.processarUpdate(businessUpdate());

        verify(saldoBotService, never()).processarMensagem(any());
    }

    @Test
    void conexaoDesabilitadaBloqueiaBusiness() throws Exception {
        assertBlocked(connection(false, true, "9001"), "9001");
    }

    @Test
    void canReplyFalseBloqueiaBusiness() throws Exception {
        assertBlocked(connection(true, false, "9001"), "9001");
    }

    @Test
    void ownerDiferenteDaAllowlistBloqueiaBusiness() throws Exception {
        assertBlocked(connection(true, true, "outro-owner"), "9001");
    }

    @Test
    void allowlistAusenteBloqueiaBusiness() throws Exception {
        properties.getBusiness().setEnabled(true);
        when(connectionService.resolver("connection-1"))
                .thenReturn(Optional.of(connection(true, true, "9001")));

        webhookService.processarUpdate(businessUpdate());

        verify(saldoBotService, never()).processarMensagem(any());
    }

    @Test
    void mensagemEnviadaPeloOwnerEhTratadaComoSaidaEBloqueada() throws Exception {
        enableBusiness("9001");
        when(connectionService.resolver("connection-1"))
                .thenReturn(Optional.of(connection(true, true, "9001")));
        JsonNode outgoing = objectMapper.readTree("""
                {
                  "update_id": 482716511,
                  "business_message": {
                    "message_id": 81,
                    "business_connection_id": "connection-1",
                    "from": {"id": 9001, "is_bot": false},
                    "chat": {"id": 12345, "type": "private"},
                    "text": "mensagem do proprietario"
                  }
                }
                """);

        webhookService.processarUpdate(outgoing);

        verify(saldoBotService, never()).processarMensagem(any());
    }

    @Test
    void businessConnectionAtualizaCacheENaoExecutaNegocio() throws Exception {
        JsonNode update = objectMapper.readTree("""
                {"update_id": 10, "business_connection": {"id": "connection-1"}}
                """);

        webhookService.processarUpdate(update);

        verify(connectionService).atualizar(10L, update.path("business_connection"));
        verify(saldoBotService, never()).processarMensagem(any());
    }

    @Test
    void editedEDeletedBusinessNuncaExecutamNegocio() throws Exception {
        webhookService.processarUpdate(objectMapper.readTree("""
                {"update_id": 11, "edited_business_message": {
                  "message_id": 81, "business_connection_id": "connection-1", "text": "alterado"
                }}
                """));
        webhookService.processarUpdate(objectMapper.readTree("""
                {"update_id": 12, "deleted_business_messages": {
                  "business_connection_id": "connection-1", "message_ids": [81]
                }}
                """));

        verify(saldoBotService, never()).processarMensagem(any());
    }

    @Test
    void retryBusinessRepeteSomenteEnvioComMesmoPayload() throws Exception {
        properties.getBot().setToken("123456:token-secreto");
        properties.getBot().setApiUrl("https://api.telegram.test");
        enableBusiness("9001");
        when(connectionService.resolver("connection-1"))
                .thenReturn(Optional.of(connection(true, true, "9001")));
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBot().getApiUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TelegramSendMessageService realSendService = new TelegramSendMessageService(
                properties,
                builder.build(),
                millis -> {
                }
        );
        webhookService = service(realSendService);
        String url = "https://api.telegram.test/bot123456%3Atoken-secreto/sendMessage";
        String payload = """
                {"chat_id":"12345","text":"resposta pronta","business_connection_id":"connection-1"}
                """;
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(payload))
                .andRespond(withException(new IOException("Connection reset")));
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(payload))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        webhookService.processarUpdate(businessUpdate());

        verify(saldoBotService, times(1)).processarMensagem(any());
        server.verify();
    }

    @Test
    void cacheMissRecuperaBusinessConnectionEProssegue() throws Exception {
        properties.getBot().setToken("123456:token-secreto");
        properties.getBot().setApiUrl("https://api.telegram.test");
        enableBusiness("9001");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBot().getApiUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TelegramBusinessConnectionService realConnectionService =
                new TelegramBusinessConnectionService(properties, builder.build());
        webhookService = new TelegramWebhookService(
                saldoBotService,
                sendMessageService,
                new TelegramIncomingMessageMapper(),
                realConnectionService,
                properties
        );
        server.expect(requestTo(
                        "https://api.telegram.test/bot123456%3Atoken-secreto/getBusinessConnection"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"business_connection_id\":\"connection-1\"}", true))
                .andRespond(withSuccess("""
                        {"ok":true,"result":{
                          "id":"connection-1",
                          "user":{"id":9001},
                          "user_chat_id":777,
                          "rights":{"can_reply":true},
                          "is_enabled":true
                        }}
                        """, MediaType.APPLICATION_JSON));

        webhookService.processarUpdate(businessUpdate());

        verify(saldoBotService, times(1)).processarMensagem(any());
        verify(sendMessageService).enviarMensagem(
                482716511L, "connection-1", "12345", "resposta pronta"
        );
        assertThat(realConnectionService.getCached("connection-1")).isPresent();
        server.verify();
    }

    private TelegramWebhookService service(TelegramSendMessageService telegramSendMessageService) {
        return new TelegramWebhookService(
                saldoBotService,
                telegramSendMessageService,
                new TelegramIncomingMessageMapper(),
                connectionService,
                properties
        );
    }

    private void assertBlocked(TelegramBusinessConnectionState state, String allowedOwner) throws Exception {
        enableBusiness(allowedOwner);
        when(connectionService.resolver("connection-1")).thenReturn(Optional.of(state));

        webhookService.processarUpdate(businessUpdate());

        verify(saldoBotService, never()).processarMensagem(any());
    }

    private void enableBusiness(String ownerUserId) {
        properties.getBusiness().setEnabled(true);
        properties.getBusiness().setAllowedOwnerUserId(ownerUserId);
    }

    private JsonNode businessUpdate() throws Exception {
        return objectMapper.readTree("""
                {
                  "update_id": 482716511,
                  "business_message": {
                    "message_id": 81,
                    "business_connection_id": "connection-1",
                    "from": {"id": 12345, "is_bot": false},
                    "chat": {"id": 12345, "type": "private"},
                    "text": "Usei credito 30"
                  }
                }
                """);
    }

    private static TelegramBusinessConnectionState connection(
            boolean enabled,
            boolean canReply,
            String ownerUserId
    ) {
        return new TelegramBusinessConnectionState(
                "connection-1", ownerUserId, "777", enabled, canReply
        );
    }

    private static MensagemSimuladaResponse response() {
        return new MensagemSimuladaResponse(
                "12345", "Usei credito 30", "resposta pronta", BigDecimal.ZERO
        );
    }
}
