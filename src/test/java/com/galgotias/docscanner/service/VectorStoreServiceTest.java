package com.galgotias.docscanner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgotias.docscanner.config.OpenAiConfig;
import com.galgotias.docscanner.config.StorageConfig;
import com.galgotias.docscanner.model.DocumentChunkEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for VectorStoreService cosine similarity math.
 */
class VectorStoreServiceTest {

    private VectorStoreService vectorStoreService;
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiKey(""); // local mode
        embeddingService = new EmbeddingService(openAiConfig, null, new ObjectMapper());

        StorageConfig storageConfig = new StorageConfig();
        storageConfig.setVectorTopK(5);
        storageConfig.setVectorSimilarityThreshold(0.1); // low threshold for testing

        vectorStoreService = new VectorStoreService(embeddingService, storageConfig);
    }

    @Test
    void cosineSimilarity_identicalVectors_shouldReturnOne() {
        float[] v = {0.5f, 0.5f, 0.5f, 0.5f};
        double sim = vectorStoreService.cosineSimilarity(v, v);
        assertThat(sim).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void cosineSimilarity_orthogonalVectors_shouldReturnZero() {
        float[] a = {1.0f, 0.0f};
        float[] b = {0.0f, 1.0f};
        double sim = vectorStoreService.cosineSimilarity(a, b);
        assertThat(sim).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void cosineSimilarity_nullInputs_shouldReturnZero() {
        assertThat(vectorStoreService.cosineSimilarity(null, new float[]{1f})).isEqualTo(0.0);
        assertThat(vectorStoreService.cosineSimilarity(new float[]{1f}, null)).isEqualTo(0.0);
    }

    @Test
    void findSimilarChunks_shouldReturnEmptyListForNullChunks() {
        List<VectorStoreService.ScoredChunk> result = vectorStoreService.findSimilarChunks("test", null);
        assertThat(result).isEmpty();
    }

    @Test
    void findSimilarChunks_shouldRankSimilarTextHigher() {
        // Create chunks with their local embeddings
        DocumentChunkEntity c1 = DocumentChunkEntity.builder()
                .id(1L).chunkIndex(0)
                .text("machine learning artificial intelligence neural networks")
                .build();
        DocumentChunkEntity c2 = DocumentChunkEntity.builder()
                .id(2L).chunkIndex(1)
                .text("cooking recipes pasta ingredients kitchen tomatoes")
                .build();

        // Embed the chunks
        float[] e1 = embeddingService.localEmbed(c1.getText());
        float[] e2 = embeddingService.localEmbed(c2.getText());
        c1.setEmbeddingJson(embeddingService.serializeEmbedding(e1));
        c2.setEmbeddingJson(embeddingService.serializeEmbedding(e2));

        String query = "deep learning and AI systems";
        List<VectorStoreService.ScoredChunk> results = vectorStoreService.findSimilarChunks(query, List.of(c1, c2));

        assertThat(results).isNotEmpty();
        // The AI chunk should rank higher than the cooking chunk
        if (results.size() >= 2) {
            assertThat(results.get(0).chunk().getId()).isEqualTo(1L);
        }
    }

    @Test
    void embeddingService_localEmbed_shouldReturnNormalizedVector() {
        float[] v = embeddingService.localEmbed("test document content");
        assertThat(v).isNotEmpty();
        // L2 norm should be approximately 1.0
        double norm = 0;
        for (float f : v) norm += (double) f * f;
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }
}
