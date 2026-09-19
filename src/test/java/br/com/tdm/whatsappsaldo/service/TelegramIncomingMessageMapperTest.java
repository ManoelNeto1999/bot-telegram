package br.com.tdm.whatsappsaldo.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.tdm.whatsappsaldo.dto.TelegramIncomingMessage;
import br.com.tdm.whatsappsaldo.enums.TelegramMessageMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TelegramIncomingMessageMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TelegramIncomingMessageMapper mapper = new TelegramIncomingMessageMapper();

    @Test
    void deveMapearTodosOsCamposDaBusinessMessage() throws Exception {
        Optional<TelegramIncomingMessage> result = mapper.map(objectMapper.readTree("""
                {
                  "update_id": 555,
                  "business_message": {
                    "message_id": 42,
                    "business_connection_id": "connection-abc",
                    "from": {"id": 12345, "is_bot": false},
                    "chat": {"id": 12345, "type": "private"},
                    "text": "  saldo  "
                  }
                }
                """));

        assertThat(result).contains(new TelegramIncomingMessage(
                555L,
                "12345",
                "saldo",
                42L,
                "connection-abc",
                TelegramMessageMode.BUSINESS,
                "12345",
                "private"
        ));
    }

    @Test
    void deveMapearMensagemNormalSemExigirCamposBusiness() throws Exception {
        Optional<TelegramIncomingMessage> result = mapper.map(objectMapper.readTree("""
                {"update_id": 7, "message": {"chat": {"id": -100}, "text": " saldo "}}
                """));

        assertThat(result).contains(new TelegramIncomingMessage(
                7L, "-100", "saldo", -1L, null, TelegramMessageMode.NORMAL, null, null
        ));
    }

    @Test
    void deveIgnorarBusinessSemTexto() throws Exception {
        assertThat(mapper.map(objectMapper.readTree("""
                {"update_id": 1, "business_message": {
                  "message_id": 2,
                  "business_connection_id": "connection-abc",
                  "from": {"id": 123},
                  "chat": {"id": 123, "type": "private"}
                }}
                """))).isEmpty();
    }

    @Test
    void deveExigirChatPrivateNoBusiness() throws Exception {
        assertThat(mapper.map(objectMapper.readTree("""
                {"update_id": 1, "business_message": {
                  "message_id": 2,
                  "business_connection_id": "connection-abc",
                  "from": {"id": 123},
                  "chat": {"id": -100, "type": "group"},
                  "text": "saldo"
                }}
                """))).isEmpty();
    }

    @Test
    void deveIgnorarOutgoingExplicita() throws Exception {
        assertThat(mapper.map(businessWith("\"outgoing\": true"))).isEmpty();
    }

    @Test
    void deveIgnorarSenderBusinessBotParaEvitarLoop() throws Exception {
        assertThat(mapper.map(businessWith("\"sender_business_bot\": {\"id\": 999}"))).isEmpty();
    }

    @Test
    void deveIgnorarRemetenteBotParaEvitarLoop() throws Exception {
        assertThat(mapper.map(objectMapper.readTree("""
                {"update_id": 1, "business_message": {
                  "message_id": 2,
                  "business_connection_id": "connection-abc",
                  "from": {"id": 123, "is_bot": true},
                  "chat": {"id": 123, "type": "private"},
                  "text": "saldo"
                }}
                """))).isEmpty();
    }

    private com.fasterxml.jackson.databind.JsonNode businessWith(String extraField) throws Exception {
        return objectMapper.readTree("""
                {"update_id": 1, "business_message": {
                  "message_id": 2,
                  "business_connection_id": "connection-abc",
                  "from": {"id": 123, "is_bot": false},
                  "chat": {"id": 123, "type": "private"},
                  "text": "saldo",
                  %s
                }}
                """.formatted(extraField));
    }
}
