package com.galgotias.docscanner.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgotias.docscanner.config.OpenAiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Embedding Service.
 *
 * Generates vector embeddings for text chunks used in cosine similarity search.
 *
 * Modes:
 *  - OpenAI Mode: Calls text-embedding-3-small API (1536-dim vectors)
 *  - Local Mode:  Deterministic character n-gram + TF-IDF inspired vectorizer
 *                 (works offline, no API key required)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingService {

    private final OpenAiConfig openAiConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Local embedding dimension (smaller for performance)
    private static final int LOCAL_DIM = 384;
    private static final int NGRAM_SIZE = 3;

    /**
     * Generates an embedding vector for the given text.
     *
     * @param text input text to embed
     * @return     float array representing the embedding vector
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[LOCAL_DIM];
        }

        if (openAiConfig.isApiEnabled()) {
            try {
                return openAiEmbed(text);
            } catch (Exception e) {
                log.warn("OpenAI embedding failed, falling back to local: {}", e.getMessage());
            }
        }

        return localEmbed(text);
    }

    /**
     * Serializes a float[] embedding to JSON string for DB storage.
     */
    public String serializeEmbedding(float[] embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * Deserializes an embedding from its JSON DB representation.
     */
    public float[] deserializeEmbedding(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return new float[0];
        }
        try {
            List<Double> list = objectMapper.readValue(json, new TypeReference<>() {});
            float[] arr = new float[list.size()];
            for (int i = 0; i < list.size(); i++) arr[i] = list.get(i).floatValue();
            return arr;
        } catch (Exception e) {
            log.warn("Failed to deserialize embedding: {}", e.getMessage());
            return new float[0];
        }
    }

    // =========================================================================
    // OpenAI Embedding
    // =========================================================================

    @SuppressWarnings("unchecked")
    private float[] openAiEmbed(String text) {
        String url = openAiConfig.getBaseUrl() + "/embeddings";

        Map<String, Object> body = Map.of(
                "model", openAiConfig.getEmbeddingModel(),
                "input", truncateText(text, 8000)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiConfig.getApiKey());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
            if (data != null && !data.isEmpty()) {
                List<Double> vector = (List<Double>) data.get(0).get("embedding");
                float[] result = new float[vector.size()];
                for (int i = 0; i < vector.size(); i++) result[i] = vector.get(i).floatValue();
                return result;
            }
        }
        throw new RuntimeException("Empty embedding response from OpenAI");
    }

    // =========================================================================
    // Local TF-IDF inspired N-gram Embedding (offline fallback)
    // =========================================================================

    /**
     * Generates a deterministic LOCAL_DIM-dimensional vector using character n-gram hashing.
     * Produces consistent, comparable vectors across calls for the same text corpus.
     *
     * Algorithm:
     *  1. Extract character n-grams of size NGRAM_SIZE
     *  2. Hash each n-gram to a dimension index in [0, LOCAL_DIM)
     *  3. Accumulate term frequencies
     *  4. Apply log(1 + tf) weighting
     *  5. L2-normalize the vector
     */
    public float[] localEmbed(String text) {
        float[] vector = new float[LOCAL_DIM];
        String normalized = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ");

        // Character n-gram hashing
        for (int i = 0; i <= normalized.length() - NGRAM_SIZE; i++) {
            String ngram = normalized.substring(i, i + NGRAM_SIZE);
            int dim = Math.abs(ngram.hashCode()) % LOCAL_DIM;
            vector[dim] += 1.0f;
        }

        // Word-level unigrams for semantic coverage
        String[] words = normalized.split("\\s+");
        for (String word : words) {
            if (word.length() >= 2) {
                int dim = Math.abs(word.hashCode()) % LOCAL_DIM;
                vector[dim] += 2.0f; // weight words higher than char ngrams
            }
        }

        // Log-TF weighting
        for (int i = 0; i < LOCAL_DIM; i++) {
            vector[i] = (float) Math.log(1 + vector[i]);
        }

        // L2 normalize
        return l2Normalize(vector);
    }

    /**
     * L2 normalizes a vector in-place (returns the same array for chaining).
     */
    public float[] l2Normalize(float[] v) {
        double norm = 0;
        for (float x : v) norm += (double) x * x;
        norm = Math.sqrt(norm);
        if (norm > 1e-9) {
            for (int i = 0; i < v.length; i++) v[i] /= (float) norm;
        }
        return v;
    }

    private String truncateText(String text, int maxChars) {
        return text.length() > maxChars ? text.substring(0, maxChars) : text;
    }
}
