package com.galgotias.docscanner.controller;

import com.galgotias.docscanner.config.OpenAiConfig;
import com.galgotias.docscanner.dto.RagQueryRequest;
import com.galgotias.docscanner.dto.RagQueryResponse;
import com.galgotias.docscanner.dto.SummaryResponse;
import com.galgotias.docscanner.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for RAG operations (query answering & summarization).
 * Base path: /api/rag
 */
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Slf4j
public class RagController {

    private final RagService ragService;
    private final OpenAiConfig openAiConfig;  // injected via constructor (RequiredArgsConstructor)

    /**
     * POST /api/rag/query
     * Processes a RAG question against uploaded documents.
     */
    @PostMapping("/query")
    public ResponseEntity<RagQueryResponse> query(@RequestBody RagQueryRequest request) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        log.info("RAG query: '{}'", request.getQuestion());
        return ResponseEntity.ok(ragService.query(request));
    }

    /**
     * POST /api/rag/summarize/{documentId}
     * Generates a structured AI summary of a document.
     */
    @PostMapping("/summarize/{documentId}")
    public ResponseEntity<SummaryResponse> summarize(@PathVariable Long documentId) {
        try {
            return ResponseEntity.ok(ragService.summarize(documentId));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Summarize error for doc {}: {}", documentId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/rag/status
     * Returns whether OpenAI API mode is active or local mode.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "aiPowered", openAiConfig.isApiEnabled(),
                "model", openAiConfig.isApiEnabled() ? openAiConfig.getModel() : "local-extractive",
                "embeddingMode", openAiConfig.isApiEnabled()
                        ? openAiConfig.getEmbeddingModel() : "local-ngram-tfidf",
                "hasApiKey", openAiConfig.isApiEnabled(),
                "baseUrl", openAiConfig.getBaseUrl(),
                "message", openAiConfig.isApiEnabled()
                        ? "Running with OpenAI API (" + openAiConfig.getModel() + ")"
                        : "Running in local mode. Configure OPENAI_API_KEY for GPT generative synthesis."
        ));
    }

    /**
     * POST /api/rag/config
     * Dynamically updates the API key, model, or base URL at runtime.
     */
    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, String> body) {
        String apiKey = body.get("apiKey");
        String model = body.getOrDefault("model", openAiConfig.getModel());
        String baseUrl = body.getOrDefault("baseUrl", openAiConfig.getBaseUrl());

        openAiConfig.updateConfig(apiKey, model, baseUrl);
        log.info("AI Configuration updated. Active model: {}, Enabled: {}", model, openAiConfig.isApiEnabled());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "aiPowered", openAiConfig.isApiEnabled(),
                "model", openAiConfig.getModel(),
                "message", openAiConfig.isApiEnabled()
                        ? "OpenAI API key configured successfully! GPT generative mode is now active."
                        : "Switched to local semantic RAG mode."
        ));
    }
}
