package br.com.tdm.whatsappsaldo.service;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import br.com.tdm.whatsappsaldo.dto.TelegramBusinessConnectionState;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class TelegramBusinessConnectionService {

    private final TelegramProperties telegramProperties;
    private final RestClient restClient;
    private final ConcurrentHashMap<String, TelegramBusinessConnectionState> connections =
            new ConcurrentHashMap<>();

    @Autowired
    public TelegramBusinessConnectionService(
            TelegramProperties telegramProperties,
            RestClient.Builder restClientBuilder
    ) {
        this(
                telegramProperties,
                restClientBuilder.baseUrl(telegramProperties.getApiUrl()).build()
        );
    }

    TelegramBusinessConnectionService(
            TelegramProperties telegramProperties,
            RestClient restClient
    ) {
        this.telegramProperties = telegramProperties;
        this.restClient = restClient;
    }

    public Optional<TelegramBusinessConnectionState> atualizar(long updateId, JsonNode connectionNode) {
        Optional<TelegramBusinessConnectionState> state = mapConnection(connectionNode);
        state.ifPresent(connection -> {
            connections.put(connection.connectionId(), connection);
            log.info(
                    "Business connection recebida. updateId={} connectionId={} ownerUserId={} "
                            + "enabled={} canReply={}",
                    updateId,
                    connection.connectionId(),
                    connection.ownerUserId(),
                    connection.enabled(),
                    connection.canReply()
            );
        });
        if (state.isEmpty()) {
            log.warn("Business connection invalida ignorada. updateId={}", updateId);
        }
        return state;
    }

    public Optional<TelegramBusinessConnectionState> resolver(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            return Optional.empty();
        }

        TelegramBusinessConnectionState cached = connections.get(connectionId);
        if (cached != null) {
            return Optional.of(cached);
        }
        return buscarNoTelegram(connectionId);
    }

    Optional<TelegramBusinessConnectionState> getCached(String connectionId) {
        return Optional.ofNullable(connections.get(connectionId));
    }

    private Optional<TelegramBusinessConnectionState> buscarNoTelegram(String connectionId) {
        if (!telegramProperties.hasTokenConfigured()) {
            log.warn("Business connection nao recuperada. reason=connection_not_found connectionId={}", connectionId);
            return Optional.empty();
        }

        try {
            JsonNode response = restClient.post()
                    .uri("/bot{token}/getBusinessConnection", telegramProperties.getBotToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("business_connection_id", connectionId))
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.path("ok").asBoolean(false)) {
                log.warn("Business connection nao recuperada. reason=connection_not_found connectionId={}", connectionId);
                return Optional.empty();
            }

            Optional<TelegramBusinessConnectionState> state = mapConnection(response.path("result"));
            if (state.isEmpty() || !connectionId.equals(state.get().connectionId())) {
                log.warn("Business connection invalida recebida. reason=connection_not_found connectionId={}", connectionId);
                return Optional.empty();
            }
            connections.put(connectionId, state.get());
            return state;
        } catch (Exception ex) {
            log.warn(
                    "Falha ao recuperar business connection. reason=connection_not_found "
                            + "connectionId={} exceptionType={}",
                    connectionId,
                    ex.getClass().getSimpleName()
            );
            return Optional.empty();
        }
    }

    private static Optional<TelegramBusinessConnectionState> mapConnection(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Optional.empty();
        }

        String connectionId = requiredText(node.path("id"));
        String ownerUserId = requiredText(node.path("user").path("id"));
        String userChatId = requiredText(node.path("user_chat_id"));
        JsonNode enabledNode = node.get("is_enabled");
        if (connectionId == null || ownerUserId == null || userChatId == null
                || enabledNode == null || !enabledNode.isBoolean()) {
            return Optional.empty();
        }

        boolean canReply = node.path("rights").path("can_reply").asBoolean(false);
        return Optional.of(new TelegramBusinessConnectionState(
                connectionId,
                ownerUserId,
                userChatId,
                enabledNode.booleanValue(),
                canReply
        ));
    }

    private static String requiredText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText("");
        return value.isBlank() ? null : value;
    }
}
