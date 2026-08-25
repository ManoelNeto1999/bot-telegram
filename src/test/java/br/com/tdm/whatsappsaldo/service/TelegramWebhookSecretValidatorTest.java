package br.com.tdm.whatsappsaldo.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.tdm.whatsappsaldo.config.TelegramProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TelegramWebhookSecretValidatorTest {

    private TelegramProperties telegramProperties;
    private TelegramWebhookSecretValidator validator;

    @BeforeEach
    void setUp() {
        telegramProperties = new TelegramProperties();
        validator = new TelegramWebhookSecretValidator(telegramProperties);
    }

    @Test
    void deveAceitarQuandoSegredoNaoEstaConfiguradoENaoEhObrigatorio() {
        telegramProperties.getWebhook().setRequireSecret(false);

        assertThat(validator.isRequestAuthorized(null)).isTrue();
    }

    @Test
    void deveRejeitarQuandoSegredoEhObrigatorioMasNaoEstaConfigurado() {
        telegramProperties.getWebhook().setRequireSecret(true);

        assertThat(validator.isRequestAuthorized(null)).isFalse();
    }

    @Test
    void deveAceitarSomenteHeaderComSegredoCorreto() {
        telegramProperties.getWebhook().setSecret("segredo-teste");
        telegramProperties.getWebhook().setRequireSecret(true);

        assertThat(validator.isRequestAuthorized("segredo-teste")).isTrue();
        assertThat(validator.isRequestAuthorized("segredo-incorreto")).isFalse();
        assertThat(validator.isRequestAuthorized(null)).isFalse();
    }

    @Test
    void deveRejeitarSegredoConfiguradoForaDoFormatoOficial() {
        telegramProperties.getWebhook().setSecret("segredo com espaco");
        telegramProperties.getWebhook().setRequireSecret(true);

        assertThat(validator.isRequestAuthorized("segredo com espaco")).isFalse();
    }
}
