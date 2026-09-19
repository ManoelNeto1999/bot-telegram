package br.com.tdm.whatsappsaldo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TelegramSendMessageServiceTest {

    private static final String API_URL = "https://api.telegram.test";
    private static final String TOKEN = "123456:token-secreto";
    private static final String SEND_MESSAGE_URL =
            API_URL + "/bot123456%3Atoken-secreto/sendMessage";
    private static final String PAYLOAD = "{\"chat_id\":\"chat-123\",\"text\":\"resposta pronta\"}";

    private TelegramProperties properties;
    private MockRestServiceServer server;
    private List<Long> delays;
    private RestClient restClient;
    private TelegramSendMessageService service;

    @BeforeEach
    void setUp() {
        properties = new TelegramProperties();
        properties.getBot().setToken(TOKEN);
        properties.getBot().setApiUrl(API_URL);

        RestClient.Builder builder = RestClient.builder().baseUrl(API_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        delays = new ArrayList<>();
        restClient = builder.build();
        service = new TelegramSendMessageService(properties, restClient, delays::add);
    }

    @Test
    void deveEnviarNaPrimeiraTentativa() {
        expectRequestComMesmoPayload(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        service.enviarMensagem(101L, "chat-123", "resposta pronta");

        server.verify();
        assertThat(delays).isEmpty();
    }

    @Test
    void deveEnviarBusinessConnectionIdSomenteNoPayloadBusiness() {
        String businessPayload = """
                {"chat_id":"chat-123","text":"resposta pronta","business_connection_id":"connection-1"}
                """;
        server.expect(requestTo(SEND_MESSAGE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(businessPayload, true))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        service.enviarMensagem(112L, "connection-1", "chat-123", "resposta pronta");

        server.verify();
        assertThat(delays).isEmpty();
    }

    @Test
    void deveReutilizarMesmoPayloadBusinessNoRetry() {
        String businessPayload = """
                {"chat_id":"chat-123","text":"resposta pronta","business_connection_id":"connection-1"}
                """;
        expectBusinessRequest(businessPayload, withException(new IOException("Connection reset")));
        expectBusinessRequest(
                businessPayload,
                withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON)
        );

        service.enviarMensagem(113L, "connection-1", "chat-123", "resposta pronta");

        server.verify();
        assertThat(delays).containsExactly(500L);
    }

    @Test
    void deveRepetirApenasOEnvioQuandoPrimeiraTentativaFalha() {
        expectRequestComMesmoPayload(withException(new IOException("Connection reset")));
        expectRequestComMesmoPayload(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        service.enviarMensagem(102L, "chat-123", "resposta pronta");

        server.verify();
        assertThat(delays).containsExactly(500L);
    }

    @Test
    void deveFazerTerceiraTentativaDepoisDeDuasFalhasTransitorias() {
        expectRequestComMesmoPayload(withException(new IOException("falha 1")));
        expectRequestComMesmoPayload(withException(new IOException("falha 2")));
        expectRequestComMesmoPayload(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        service.enviarMensagem(103L, "chat-123", "resposta pronta");

        server.verify();
        assertThat(delays).containsExactly(500L, 1500L);
    }

    @Test
    void devePararAposTresFalhasTransitorias() {
        expectRequestComMesmoPayload(withException(new IOException("falha 1")));
        expectRequestComMesmoPayload(withException(new IOException("falha 2")));
        expectRequestComMesmoPayload(withException(new IOException("falha 3")));

        service.enviarMensagem(104L, "chat-123", "resposta pronta");

        server.verify();
        assertThat(delays).containsExactly(500L, 1500L);
    }

    @Test
    void naoDeveRepetirHttp400() {
        ListAppender<ILoggingEvent> appender = iniciarCapturaDeLogs();
        String conteudoSensivel = "saldo e movimentacao financeira ficticia";
        expectRequestComMesmoPayload(withBadRequest().body(conteudoSensivel));

        service.enviarMensagem(105L, "chat-123", "resposta pronta");

        server.verify();
        assertThat(delays).isEmpty();
        assertThat(mensagens(appender, Level.ERROR))
                .anyMatch(message -> message.contains("Falha HTTP")
                        && message.contains("statusCode=400")
                        && message.contains("retry=false"))
                .noneMatch(message -> message.contains(conteudoSensivel));
        encerrarCapturaDeLogs(appender);
    }

    @Test
    void naoDeveRepetirHttp429NestaEtapa() {
        expectRequestComMesmoPayload(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        service.enviarMensagem(110L, "chat-123", "resposta pronta");

        server.verify();
        assertThat(delays).isEmpty();
    }

    @Test
    void deveRepetirHttp5xxPorSerFalhaTransitoria() {
        ListAppender<ILoggingEvent> appender = iniciarCapturaDeLogs();
        String conteudoSensivel = "payload financeiro ficticio no erro";
        expectRequestComMesmoPayload(withServerError().body(conteudoSensivel));
        expectRequestComMesmoPayload(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        service.enviarMensagem(106L, "chat-123", "resposta pronta");

        server.verify();
        assertThat(delays).containsExactly(500L);
        assertThat(mensagens(appender, Level.WARN))
                .anyMatch(message -> message.contains("Falha HTTP")
                        && message.contains("statusCode=500")
                        && message.contains("retry=true"))
                .noneMatch(message -> message.contains(conteudoSensivel));
        encerrarCapturaDeLogs(appender);
    }

    @Test
    void deveTratarRespostaComOkTrueComoSucessoReal() {
        ListAppender<ILoggingEvent> appender = iniciarCapturaDeLogs();
        expectRequestComMesmoPayload(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        service.enviarMensagem(107L, "chat-123", "resposta pronta");

        assertThat(mensagens(appender, Level.INFO))
                .anyMatch(message -> message.contains("Resposta enviada com sucesso")
                        && message.contains("updateId=107"));
        encerrarCapturaDeLogs(appender);
    }

    @Test
    void naoDeveTratarBodySemOkTrueComoSucesso() {
        ListAppender<ILoggingEvent> appender = iniciarCapturaDeLogs();
        expectRequestComMesmoPayload(withSuccess("{\"ok\":false}", MediaType.APPLICATION_JSON));

        service.enviarMensagem(108L, "chat-123", "resposta pronta");

        assertThat(mensagens(appender, Level.INFO))
                .noneMatch(message -> message.contains("Resposta enviada com sucesso"));
        assertThat(mensagens(appender, Level.WARN))
                .anyMatch(message -> message.contains("envio nao confirmado")
                        && message.contains("updateId=108"));
        assertThat(delays).isEmpty();
        encerrarCapturaDeLogs(appender);
    }

    @Test
    void deveRemoverTokenLiteralECodificadoDoLogDeFalha() {
        ListAppender<ILoggingEvent> appender = iniciarCapturaDeLogs();
        String mensagem = "falha em /bot" + TOKEN
                + "/sendMessage e /bot123456%3Atoken-secreto/sendMessage";
        expectRequestComMesmoPayload(withException(new IOException(mensagem)));
        expectRequestComMesmoPayload(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        service.enviarMensagem(111L, "chat-123", "resposta pronta");

        String logFalha = mensagens(appender, Level.WARN).stream()
                .filter(message -> message.contains("Falha de transporte ao enviar resposta Telegram"))
                .findFirst()
                .orElseThrow();
        assertThat(logFalha)
                .contains("updateId=111")
                .contains("attempt=1/3")
                .contains("elapsedMs=")
                .contains("exceptionType=ResourceAccessException")
                .contains("rootCauseType=IOException")
                .contains("rootCauseMessage=")
                .contains("<REDACTED>")
                .doesNotContain(TOKEN)
                .doesNotContain("123456%3Atoken-secreto");
        encerrarCapturaDeLogs(appender);
    }

    @Test
    void deveInterromperNovasTentativasQuandoEsperaForInterrompida() {
        expectRequestComMesmoPayload(withException(new IOException("Connection reset")));
        TelegramSendMessageService servicoInterrompido = new TelegramSendMessageService(
                properties,
                restClient,
                millis -> {
                    throw new InterruptedException("interrompido");
                }
        );

        try {
            servicoInterrompido.enviarMensagem(109L, "chat-123", "resposta pronta");

            server.verify();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private void expectRequestComMesmoPayload(
            org.springframework.test.web.client.ResponseCreator responseCreator
    ) {
        server.expect(requestTo(SEND_MESSAGE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(PAYLOAD, true))
                .andRespond(responseCreator);
    }

    private void expectBusinessRequest(
            String payload,
            org.springframework.test.web.client.ResponseCreator responseCreator
    ) {
        server.expect(requestTo(SEND_MESSAGE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(payload, true))
                .andRespond(responseCreator);
    }

    private static ListAppender<ILoggingEvent> iniciarCapturaDeLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(TelegramSendMessageService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void encerrarCapturaDeLogs(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(TelegramSendMessageService.class);
        logger.detachAppender(appender);
        appender.stop();
    }

    private static List<String> mensagens(ListAppender<ILoggingEvent> appender, Level level) {
        return appender.list.stream()
                .filter(event -> event.getLevel().equals(level))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
