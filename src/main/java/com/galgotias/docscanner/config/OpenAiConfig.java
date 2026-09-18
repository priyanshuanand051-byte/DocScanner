package com.galgotias.docscanner.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * OpenAI Configuration
 * Reads all properties prefixed with 'docscanner.openai' from application.properties.
 * The application runs in LOCAL mode when api-key is blank (no OpenAI required).
 */
@Configuration
@ConfigurationProperties(prefix = "docscanner.openai")
@Data
public class OpenAiConfig {

    /** OpenAI API key - leave empty to use local fallback mode */
    private String apiKey = "";

    /** Chat completion model */
    private String model = "gpt-4o-mini";

    /** Embedding model */
    private String embeddingModel = "text-embedding-3-small";

    /** OpenAI base URL */
    private String baseUrl = "https://api.openai.com/v1";

    /** Max tokens for GPT responses */
    private int maxTokens = 1500;

    /** Temperature (0.0 = deterministic, 1.0 = creative) */
    private double temperature = 0.3;

    /**
     * Returns true if OpenAI API integration is active.
     * When false, the application uses local cosine similarity + extractive QA.
     */
    public boolean isApiEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Dynamically updates the API key and model at runtime without server restart.
     */
    public synchronized void updateConfig(String newKey, String newModel, String newBaseUrl) {
        if (newKey != null) {
            this.apiKey = newKey.trim();
        }
        if (newModel != null && !newModel.isBlank()) {
            this.model = newModel.trim();
        }
        if (newBaseUrl != null && !newBaseUrl.isBlank()) {
            this.baseUrl = newBaseUrl.trim();
        }
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
