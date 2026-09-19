package br.com.tdm.whatsappsaldo.dto;

import br.com.tdm.whatsappsaldo.enums.TelegramMessageMode;

public record TelegramIncomingMessage(
        long updateId,
        String chatId,
        String text,
        long messageId,
        String businessConnectionId,
        TelegramMessageMode mode,
        String senderId,
        String chatType
) {
}
