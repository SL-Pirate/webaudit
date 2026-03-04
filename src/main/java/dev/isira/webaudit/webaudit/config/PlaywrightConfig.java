package dev.isira.webaudit.webaudit.config;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;

@Configuration
public class PlaywrightConfig {
    private Playwright playwright;

    @Bean
    public Browser browser() {
        playwright = Playwright.create();
        return playwright.chromium().launch();
    }

    @PreDestroy
    public void closePlaywright() {
        if (playwright != null) {
            playwright.close();
        }
    }
}
