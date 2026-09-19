package br.com.tdm.whatsappsaldo.service;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TelegramWebhookManagementServiceTest {

    @Test
    void setWebhookEnviaAllowedUpdatesExatamenteComoEsperado() {
        TelegramProperties properties = new TelegramProperties();
        properties.getBot().setToken("123456:token-secreto");
        properties.getBot().setApiUrl("https://api.telegram.test");
        properties.getWebhook().setSecret("secret-123");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TelegramWebhookManagementService service =
                new TelegramWebhookManagementService(properties, builder);
        server.expect(requestTo("https://api.telegram.test/bot123456%3Atoken-secreto/setWebhook"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "url":"https://bot.example.com/api/telegram/webhook",
                          "allowed_updates":[
                            "message",
                            "business_connection",
                            "business_message",
                            "edited_business_message",
                            "deleted_business_messages"
                          ],
                          "secret_token":"secret-123"
                        }
                        """, true))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        service.registrarWebhook("https://bot.example.com/api/telegram/webhook");

        server.verify();
    }
}
