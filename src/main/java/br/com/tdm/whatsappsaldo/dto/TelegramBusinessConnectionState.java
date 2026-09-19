package br.com.tdm.whatsappsaldo.dto;

public record TelegramBusinessConnectionState(
        String connectionId,
        String ownerUserId,
        String userChatId,
        boolean enabled,
        boolean canReply
) {
}
