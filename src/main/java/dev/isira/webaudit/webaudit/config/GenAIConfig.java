package dev.isira.webaudit.webaudit.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class GenAIConfig {
    @Bean
    public ChatClient chatClient(ChatModel chatModel) throws IOException {
        final var resource = getClass().getClassLoader().getResource("system-prompt.md");
        if (resource == null) {
            throw new RuntimeException("Could not find default prompt resource");
        }

        try (final var stream = resource.openStream()) {
            final var systemPrompt = new String(stream.readAllBytes());
            return ChatClient
                    .builder(chatModel)
                    .defaultSystem(systemPrompt)
                    .build();
        }
    }
}
