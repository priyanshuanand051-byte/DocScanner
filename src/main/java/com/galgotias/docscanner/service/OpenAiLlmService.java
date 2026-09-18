package com.galgotias.docscanner.service;

import com.galgotias.docscanner.config.OpenAiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * OpenAI LLM Service — handles GPT chat completion requests.
 *
 * Used by the RAG pipeline to generate context-grounded answers.
 * Falls back to local extractive engine when API key is not configured.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OpenAiLlmService {

    private final OpenAiConfig openAiConfig;
    private final RestTemplate restTemplate;

    /**
     * Sends a chat completion request to OpenAI.
     *
     * @param systemPrompt  the system-level instructions
     * @param userMessage   the user's message/question
     * @return              the model's text response
     */
    @SuppressWarnings("unchecked")
    public String complete(String systemPrompt, String userMessage) {
        if (!openAiConfig.isApiEnabled()) {
            throw new IllegalStateException("OpenAI API key not configured");
        }

        String url = openAiConfig.getBaseUrl() + "/chat/completions";

        Map<String, Object> body = Map.of(
                "model", openAiConfig.getModel(),
                "temperature", openAiConfig.getTemperature(),
                "max_tokens", openAiConfig.getMaxTokens(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiConfig.getApiKey());

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    if (message != null) {
                        return (String) message.get("content");
                    }
                }
            }
            throw new RuntimeException("Invalid OpenAI response structure");

        } catch (Exception e) {
            log.error("OpenAI API call failed: {}", e.getMessage());
            throw new RuntimeException("OpenAI call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns which model is currently being used.
     */
    public String getActiveModel() {
        return openAiConfig.isApiEnabled() ? openAiConfig.getModel() : "local-extractive";
    }
}
