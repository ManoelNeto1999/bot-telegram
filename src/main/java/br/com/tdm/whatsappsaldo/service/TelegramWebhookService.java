package br.com.tdm.whatsappsaldo.service;

import br.com.tdm.whatsappsaldo.dto.MensagemSimuladaRequest;
import br.com.tdm.whatsappsaldo.dto.MensagemSimuladaResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramWebhookService {

    private final SaldoBotService saldoBotService;
    private final TelegramSendMessageService telegramSendMessageService;

    public void processarUpdate(JsonNode updatePayload) {
        if (updatePayload == null || updatePayload.isNull()) {
            log.info("Update vazio recebido no webhook do Telegram. Ignorando.");
            return;
        }

        long updateId = updatePayload.path("update_id").asLong(-1L);
        log.info("Update recebido do Telegram. updateId={}", updateId);

        JsonNode messageNode = updatePayload.path("message");
        if (messageNode.isMissingNode() || messageNode.isNull()) {
            log.info("Update {} ignorado: nao possui campo message.", updateId);
            return;
        }

        JsonNode chatIdNode = messageNode.path("chat").path("id");
        if (chatIdNode.isMissingNode() || chatIdNode.isNull()) {
            log.info("Update {} ignorado: chat.id ausente.", updateId);
            return;
        }

        String chatId = chatIdNode.asText("");
        JsonNode textNode = messageNode.path("text");
        if (textNode.isMissingNode() || textNode.isNull() || textNode.asText("").isBlank()) {
            log.info("Mensagem do update {} ignorada por nao ser texto.", updateId);
            return;
        }

        String textoMensagem = textNode.asText().trim();
        log.debug("Mensagem de texto valida recebida no update {}.", updateId);

        MensagemSimuladaRequest request = new MensagemSimuladaRequest(chatId, textoMensagem);
        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(request);

        telegramSendMessageService.enviarMensagem(chatId, resposta.resposta());
    }
}
