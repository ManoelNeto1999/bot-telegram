package br.com.tdm.whatsappsaldo.service;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Service
public class TelegramSendMessageService {

    private static final int MAX_TENTATIVAS = 3;
    private static final long[] DELAYS_MS = {500L, 1500L};

    private final TelegramProperties telegramProperties;
    private final RestClient restClient;
    private final RetrySleeper retrySleeper;

    @Autowired
    public TelegramSendMessageService(
            TelegramProperties telegramProperties,
            RestClient.Builder restClientBuilder
    ) {
        this(
                telegramProperties,
                restClientBuilder.baseUrl(telegramProperties.getApiUrl()).build(),
                Thread::sleep
        );
    }

    TelegramSendMessageService(
            TelegramProperties telegramProperties,
            RestClient restClient,
            RetrySleeper retrySleeper
    ) {
        this.telegramProperties = telegramProperties;
        this.restClient = restClient;
        this.retrySleeper = retrySleeper;
    }

    public void enviarMensagem(long updateId, String chatId, String mensagem) {
        if (!telegramProperties.hasTokenConfigured()) {
            log.info("Telegram nao configurado. Envio de resposta ignorado. updateId={}", updateId);
            return;
        }

        Map<String, Object> payload = Map.of(
                "chat_id", chatId,
                "text", mensagem
        );

        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            long inicio = System.nanoTime();
            try {
                JsonNode resposta = restClient.post()
                        .uri("/bot{token}/sendMessage", telegramProperties.getBotToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(JsonNode.class);

                long elapsedMs = elapsedMs(inicio);
                if (respostaTelegramValida(resposta)) {
                    log.info(
                            "Resposta enviada com sucesso pelo Telegram. updateId={} attempt={}/{} elapsedMs={}",
                            updateId,
                            tentativa,
                            MAX_TENTATIVAS,
                            elapsedMs
                    );
                } else {
                    log.warn(
                            "Resposta invalida do Telegram; envio nao confirmado. "
                                    + "updateId={} attempt={}/{} elapsedMs={}",
                            updateId,
                            tentativa,
                            MAX_TENTATIVAS,
                            elapsedMs
                    );
                }
                return;
            } catch (ResourceAccessException ex) {
                boolean novaTentativa = tentativa < MAX_TENTATIVAS;
                registrarFalha(updateId, tentativa, inicio, ex, novaTentativa);
                if (!novaTentativa || !aguardarProximaTentativa(updateId, tentativa)) {
                    return;
                }
            } catch (RestClientResponseException ex) {
                boolean novaTentativa = ex.getStatusCode().is5xxServerError()
                        && tentativa < MAX_TENTATIVAS;
                registrarFalha(updateId, tentativa, inicio, ex, novaTentativa);
                if (!novaTentativa || !aguardarProximaTentativa(updateId, tentativa)) {
                    return;
                }
            } catch (Exception ex) {
                registrarFalha(updateId, tentativa, inicio, ex, false);
                return;
            }
        }
    }

    static String sanitizarParaLog(String valor, String botToken) {
        if (valor == null) {
            return "<sem mensagem>";
        }

        String sanitizado = valor;
        if (botToken != null && !botToken.isBlank()) {
            sanitizado = sanitizado.replace(botToken, "<REDACTED>");
            String encodedToken = URLEncoder.encode(botToken, StandardCharsets.UTF_8);
            sanitizado = sanitizado
                    .replace(encodedToken, "<REDACTED>")
                    .replace(encodedToken.toLowerCase(Locale.ROOT), "<REDACTED>");
        }
        return sanitizado.replace('\n', ' ').replace('\r', ' ');
    }

    private boolean aguardarProximaTentativa(long updateId, int tentativa) {
        long delayMs = DELAYS_MS[tentativa - 1];
        try {
            retrySleeper.sleep(delayMs);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn(
                    "Retry de envio Telegram interrompido. updateId={} attempt={}/{}",
                    updateId,
                    tentativa,
                    MAX_TENTATIVAS
            );
            return false;
        }
    }

    private void registrarFalha(
            long updateId,
            int tentativa,
            long inicio,
            Exception exception,
            boolean novaTentativa
    ) {
        String formato;
        Object[] argumentos;

        if (exception instanceof RestClientResponseException httpException) {
            formato = "Falha HTTP ao enviar resposta Telegram. "
                    + "updateId={} attempt={}/{} elapsedMs={} exceptionType={} "
                    + "statusCode={} retry={}";
            argumentos = new Object[]{
                updateId,
                tentativa,
                MAX_TENTATIVAS,
                elapsedMs(inicio),
                exception.getClass().getSimpleName(),
                httpException.getStatusCode().value(),
                novaTentativa
            };
        } else {
            Throwable rootCause = rootCause(exception);
            if (exception instanceof ResourceAccessException) {
                formato = "Falha de transporte ao enviar resposta Telegram. "
                        + "updateId={} attempt={}/{} elapsedMs={} exceptionType={} "
                        + "rootCauseType={} rootCauseMessage={} retry={}";
                argumentos = new Object[]{
                    updateId,
                    tentativa,
                    MAX_TENTATIVAS,
                    elapsedMs(inicio),
                    exception.getClass().getSimpleName(),
                    rootCause.getClass().getSimpleName(),
                    sanitizarParaLog(rootCause.getMessage(), telegramProperties.getBotToken()),
                    novaTentativa
                };
            } else {
                formato = "Falha inesperada ao enviar resposta Telegram. "
                        + "updateId={} attempt={}/{} elapsedMs={} exceptionType={} "
                        + "rootCauseType={} retry={}";
                argumentos = new Object[]{
                    updateId,
                    tentativa,
                    MAX_TENTATIVAS,
                    elapsedMs(inicio),
                    exception.getClass().getSimpleName(),
                    rootCause.getClass().getSimpleName(),
                    novaTentativa
                };
            }
        }

        if (novaTentativa) {
            log.warn(formato, argumentos);
        } else {
            log.error(formato, argumentos);
        }
    }

    private static boolean respostaTelegramValida(JsonNode resposta) {
        JsonNode ok = resposta == null ? null : resposta.get("ok");
        return ok != null && ok.isBoolean() && ok.booleanValue();
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    private static long elapsedMs(long inicio) {
        return (System.nanoTime() - inicio) / 1_000_000L;
    }

    @FunctionalInterface
    interface RetrySleeper {

        void sleep(long millis) throws InterruptedException;
    }
}
