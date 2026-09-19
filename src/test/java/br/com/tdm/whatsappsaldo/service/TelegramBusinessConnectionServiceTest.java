package br.com.tdm.whatsappsaldo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import br.com.tdm.whatsappsaldo.dto.TelegramBusinessConnectionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TelegramBusinessConnectionServiceTest {

    private static final String API_URL = "https://api.telegram.test";
    private static final String TOKEN = "123456:token-secreto";
    private static final String GET_CONNECTION_URL =
            API_URL + "/bot123456%3Atoken-secreto/getBusinessConnection";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private TelegramBusinessConnectionService service;

    @BeforeEach
    void setUp() {
        TelegramProperties properties = new TelegramProperties();
        properties.getBot().setToken(TOKEN);
        properties.getBot().setApiUrl(API_URL);
        RestClient.Builder builder = RestClient.builder().baseUrl(API_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        service = new TelegramBusinessConnectionService(properties, builder.build());
    }

    @Test
    void deveAtualizarCacheComConnectionHabilitadaECanReply() throws Exception {
        Optional<TelegramBusinessConnectionState> state = service.atualizar(
                10L,
                objectMapper.readTree(connectionJson(true, "\"rights\": {\"can_reply\": true},"))
        );

        assertThat(state).contains(new TelegramBusinessConnectionState(
                "connection-1", "9001", "777", true, true
        ));
        assertThat(service.getCached("connection-1")).isEqualTo(state);
    }

    @Test
    void rightsAusenteResultaEmCanReplyFalse() throws Exception {
        Optional<TelegramBusinessConnectionState> state = service.atualizar(
                11L,
                objectMapper.readTree(connectionJson(true, ""))
        );

        assertThat(state).isPresent();
        assertThat(state.orElseThrow().canReply()).isFalse();
    }

    @Test
    void canReplyFalseEhPreservadoNoCache() throws Exception {
        Optional<TelegramBusinessConnectionState> state = service.atualizar(
                12L,
                objectMapper.readTree(connectionJson(true, "\"rights\": {\"can_reply\": false},"))
        );

        assertThat(state).isPresent();
        assertThat(state.orElseThrow().canReply()).isFalse();
    }

    @Test
    void connectionDesabilitadaEhPreservadaNoCache() throws Exception {
        Optional<TelegramBusinessConnectionState> state = service.atualizar(
                13L,
                objectMapper.readTree(connectionJson(false, "\"rights\": {\"can_reply\": true},"))
        );

        assertThat(state).isPresent();
        assertThat(state.orElseThrow().enabled()).isFalse();
    }

    @Test
    void cacheMissChamaGetBusinessConnectionEArmazenaRespostaValida() {
        server.expect(requestTo(GET_CONNECTION_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"business_connection_id\":\"connection-1\"}"))
                .andRespond(withSuccess("""
                        {"ok":true,"result":{
                          "id":"connection-1",
                          "user":{"id":9001},
                          "user_chat_id":777,
                          "rights":{"can_reply":true},
                          "is_enabled":true
                        }}
                        """, MediaType.APPLICATION_JSON));

        Optional<TelegramBusinessConnectionState> first = service.resolver("connection-1");
        Optional<TelegramBusinessConnectionState> second = service.resolver("connection-1");

        assertThat(first).contains(new TelegramBusinessConnectionState(
                "connection-1", "9001", "777", true, true
        ));
        assertThat(second).isEqualTo(first);
        server.verify();
    }

    @Test
    void falhaNoGetBusinessConnectionAplicaFailClosed() {
        server.expect(requestTo(GET_CONNECTION_URL)).andRespond(withServerError());

        assertThat(service.resolver("connection-1")).isEmpty();

        server.verify();
        assertThat(service.getCached("connection-1")).isEmpty();
    }

    @Test
    void respostaComConnectionIdDiferenteEhRejeitada() {
        server.expect(requestTo(GET_CONNECTION_URL))
                .andRespond(withSuccess("""
                        {"ok":true,"result":{
                          "id":"outra-connection",
                          "user":{"id":9001},
                          "user_chat_id":777,
                          "rights":{"can_reply":true},
                          "is_enabled":true
                        }}
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.resolver("connection-1")).isEmpty();

        server.verify();
    }

    private static String connectionJson(boolean enabled, String rightsField) {
        return """
                {
                  "id": "connection-1",
                  "user": {"id": 9001},
                  "user_chat_id": 777,
                  %s
                  "is_enabled": %s
                }
                """.formatted(rightsField, enabled);
    }
}
