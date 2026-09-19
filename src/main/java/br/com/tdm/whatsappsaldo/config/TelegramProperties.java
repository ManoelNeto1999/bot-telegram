package br.com.tdm.whatsappsaldo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {

    private Bot bot = new Bot();
    private Webhook webhook = new Webhook();
    private Business business = new Business();

    public String getBotToken() {
        return bot.getToken();
    }

    public String getApiUrl() {
        return bot.getApiUrl();
    }

    public boolean hasTokenConfigured() {
        return getBotToken() != null && !getBotToken().isBlank();
    }

    @Getter
    @Setter
    public static class Bot {

        private String token = "";
        private String apiUrl = "https://api.telegram.org";
    }

    @Getter
    @Setter
    public static class Webhook {

        private String url = "";
        private String secret = "";
        private boolean autoRegister;
        private boolean requireSecret;
        private boolean manualManagementEnabled = true;

        public boolean hasUrlConfigured() {
            return url != null && !url.isBlank();
        }

        public boolean hasSecretConfigured() {
            return secret != null && !secret.isBlank();
        }

        public boolean hasValidSecret() {
            return hasSecretConfigured() && secret.matches("[A-Za-z0-9_-]{1,256}");
        }
    }

    @Getter
    @Setter
    public static class Business {

        private boolean enabled;
        private String allowedOwnerUserId = "";

        public boolean hasAllowedOwnerUserId() {
            return allowedOwnerUserId != null && !allowedOwnerUserId.isBlank();
        }
    }
}
