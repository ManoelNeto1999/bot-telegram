package br.com.tdm.whatsappsaldo.service;

import br.com.tdm.whatsappsaldo.dto.TelegramIncomingMessage;
import br.com.tdm.whatsappsaldo.enums.TelegramMessageMode;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TelegramIncomingMessageMapper {

    public Optional<TelegramIncomingMessage> map(JsonNode updatePayload) {
        if (updatePayload == null || updatePayload.isNull()) {
            return Optional.empty();
        }

        long updateId = updatePayload.path("update_id").asLong(-1L);
        JsonNode businessMessage = updatePayload.path("business_message");
        if (!businessMessage.isMissingNode() && !businessMessage.isNull()) {
            return mapBusiness(updateId, businessMessage);
        }

        JsonNode message = updatePayload.path("message");
        if (!message.isMissingNode() && !message.isNull()) {
            return mapNormal(updateId, message);
        }
        return Optional.empty();
    }

    private Optional<TelegramIncomingMessage> mapNormal(long updateId, JsonNode message) {
        String chatId = requiredText(message.path("chat").path("id"));
        String text = requiredText(message.path("text"));
        if (chatId == null || text == null) {
            return Optional.empty();
        }

        return Optional.of(new TelegramIncomingMessage(
                updateId,
                chatId,
                text.trim(),
                message.path("message_id").asLong(-1L),
                null,
                TelegramMessageMode.NORMAL,
                optionalText(message.path("from").path("id")),
                optionalText(message.path("chat").path("type"))
        ));
    }

    private Optional<TelegramIncomingMessage> mapBusiness(long updateId, JsonNode message) {
        String connectionId = requiredText(message.path("business_connection_id"));
        String chatId = requiredText(message.path("chat").path("id"));
        String chatType = requiredText(message.path("chat").path("type"));
        String senderId = requiredText(message.path("from").path("id"));
        String text = requiredText(message.path("text"));
        long messageId = message.path("message_id").asLong(-1L);

        if (!"private".equals(chatType)) {
            log.info(
                    "Business message ignorada. reason=unsupported_chat_type updateId={} connectionId={} messageId={}",
                    updateId,
                    safe(connectionId),
                    messageId
            );
            return Optional.empty();
        }
        if (message.path("outgoing").asBoolean(false)
                || present(message.get("sender_business_bot"))
                || message.path("from").path("is_bot").asBoolean(false)) {
            log.info(
                    "Business message ignorada. reason=outgoing_business_message updateId={} "
                            + "connectionId={} messageId={}",
                    updateId,
                    safe(connectionId),
                    messageId
            );
            return Optional.empty();
        }
        if (connectionId == null || chatId == null || senderId == null || text == null || messageId < 0L) {
            return Optional.empty();
        }

        return Optional.of(new TelegramIncomingMessage(
                updateId,
                chatId,
                text.trim(),
                messageId,
                connectionId,
                TelegramMessageMode.BUSINESS,
                senderId,
                chatType
        ));
    }

    private static boolean present(JsonNode node) {
        return node != null && !node.isNull() && !node.isMissingNode();
    }

    private static String requiredText(JsonNode node) {
        String value = optionalText(node);
        return value == null || value.isBlank() ? null : value;
    }

    private static String optionalText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.asText("");
    }

    private static String safe(String value) {
        return value == null ? "<missing>" : value;
    }
}
