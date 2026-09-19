package br.com.tdm.whatsappsaldo.service;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import br.com.tdm.whatsappsaldo.dto.MensagemSimuladaRequest;
import br.com.tdm.whatsappsaldo.dto.MensagemSimuladaResponse;
import br.com.tdm.whatsappsaldo.dto.TelegramBusinessConnectionState;
import br.com.tdm.whatsappsaldo.dto.TelegramIncomingMessage;
import br.com.tdm.whatsappsaldo.enums.TelegramMessageMode;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramWebhookService {

    private final SaldoBotService saldoBotService;
    private final TelegramSendMessageService telegramSendMessageService;
    private final TelegramIncomingMessageMapper incomingMessageMapper;
    private final TelegramBusinessConnectionService businessConnectionService;
    private final TelegramProperties telegramProperties;

    public void processarUpdate(JsonNode updatePayload) {
        if (updatePayload == null || updatePayload.isNull()) {
            log.info("Update vazio recebido no webhook do Telegram. Ignorando.");
            return;
        }

        long updateId = updatePayload.path("update_id").asLong(-1L);
        log.info("Update recebido do Telegram. updateId={}", updateId);

        JsonNode businessConnection = updatePayload.path("business_connection");
        if (!businessConnection.isMissingNode() && !businessConnection.isNull()) {
            businessConnectionService.atualizar(updateId, businessConnection);
            return;
        }
        if (hasNode(updatePayload, "edited_business_message")) {
            JsonNode message = updatePayload.path("edited_business_message");
            log.info(
                    "Edited business message ignorada. updateId={} connectionId={} messageId={}",
                    updateId,
                    safeText(message.path("business_connection_id")),
                    message.path("message_id").asLong(-1L)
            );
            return;
        }
        if (hasNode(updatePayload, "deleted_business_messages")) {
            JsonNode deleted = updatePayload.path("deleted_business_messages");
            log.info(
                    "Deleted business messages ignoradas; nenhuma reconciliacao executada. "
                            + "updateId={} connectionId={}",
                    updateId,
                    safeText(deleted.path("business_connection_id"))
            );
            return;
        }

        Optional<TelegramIncomingMessage> mapped = incomingMessageMapper.map(updatePayload);
        if (mapped.isEmpty()) {
            log.info("Update {} ignorado: nao possui mensagem de texto valida.", updateId);
            return;
        }

        TelegramIncomingMessage incoming = mapped.get();
        if (incoming.mode() == TelegramMessageMode.BUSINESS) {
            log.info(
                    "Business message recebida. updateId={} connectionId={} chatId={} messageId={} mode={}",
                    incoming.updateId(),
                    incoming.businessConnectionId(),
                    incoming.chatId(),
                    incoming.messageId(),
                    incoming.mode()
            );
            if (!businessMessagePermitida(incoming)) {
                return;
            }
        }

        // POC de uma unica conta autorizada: chatId continua sendo a identidade financeira.
        // ISTO NAO E SEGURO PARA MULTIPLAS EMPRESAS e businessConnectionId nao e tenant permanente.
        MensagemSimuladaRequest request = new MensagemSimuladaRequest(incoming.chatId(), incoming.text());
        MensagemSimuladaResponse response = saldoBotService.processarMensagem(request);

        if (incoming.mode() == TelegramMessageMode.BUSINESS) {
            telegramSendMessageService.enviarMensagem(
                    incoming.updateId(),
                    incoming.businessConnectionId(),
                    incoming.chatId(),
                    response.resposta()
            );
        } else {
            telegramSendMessageService.enviarMensagem(
                    incoming.updateId(),
                    incoming.chatId(),
                    response.resposta()
            );
        }
    }

    private boolean businessMessagePermitida(TelegramIncomingMessage incoming) {
        TelegramProperties.Business business = telegramProperties.getBusiness();
        if (!business.isEnabled()) {
            logDenied("business_disabled", incoming);
            return false;
        }
        if (!"private".equals(incoming.chatType())) {
            logDenied("unsupported_chat_type", incoming);
            return false;
        }

        Optional<TelegramBusinessConnectionState> resolved =
                businessConnectionService.resolver(incoming.businessConnectionId());
        if (resolved.isEmpty()) {
            logDenied("connection_not_found", incoming);
            return false;
        }

        TelegramBusinessConnectionState connection = resolved.get();
        if (!connection.enabled()) {
            logDenied("connection_disabled", incoming);
            return false;
        }
        if (!connection.canReply()) {
            logDenied("reply_not_allowed", incoming);
            return false;
        }
        if (!business.hasAllowedOwnerUserId()
                || !business.getAllowedOwnerUserId().trim().equals(connection.ownerUserId())) {
            logDenied("owner_not_allowed", incoming);
            return false;
        }
        if (connection.ownerUserId().equals(incoming.senderId())) {
            logDenied("outgoing_business_message", incoming);
            return false;
        }
        return true;
    }

    private static boolean hasNode(JsonNode root, String fieldName) {
        JsonNode node = root.path(fieldName);
        return !node.isMissingNode() && !node.isNull();
    }

    private static String safeText(JsonNode node) {
        String value = node.asText("");
        return value.isBlank() ? "<missing>" : value;
    }

    private static void logDenied(String reason, TelegramIncomingMessage incoming) {
        log.info(
                "Business message ignorada. reason={} updateId={} connectionId={} chatId={} messageId={}",
                reason,
                incoming.updateId(),
                incoming.businessConnectionId(),
                incoming.chatId(),
                incoming.messageId()
        );
    }
}
