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

    private String botToken = "";
    private String apiUrl = "https://api.telegram.org";

    public boolean hasTokenConfigured() {
        return botToken != null && !botToken.isBlank();
    }
}
