package com.galgotias.docscanner.service;

import com.galgotias.docscanner.config.StorageConfig;
import com.galgotias.docscanner.model.DocumentChunkEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Vector Store Service — In-memory cosine similarity and hybrid search engine.
 *
 * Provides retrieval functionality for the RAG pipeline:
 *  - Computes cosine similarity between query embedding and chunk vectors
 *  - Filters out common question stop words to extract meaningful content keywords
 *  - Penalizes queries with zero content-keyword overlap to prevent irrelevant answers
 *  - Strictly enforces similarity threshold to reject out-of-scope questions
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VectorStoreService {

    private final EmbeddingService embeddingService;
    private final StorageConfig storageConfig;

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "is", "are", "was", "were", "this", "that", "these", "those",
            "what", "when", "where", "which", "who", "whom", "whose", "why", "how",
            "can", "could", "will", "would", "shall", "should", "may", "might", "must",
            "have", "has", "had", "having", "does", "did", "doing", "done",
            "make", "made", "give", "given", "tell", "told", "show", "shown", "find",
            "about", "with", "from", "for", "into", "through", "during", "before",
            "after", "above", "below", "some", "any", "each", "every", "more", "most",
            "other", "such", "only", "same", "than", "too", "very", "just", "also",
            "like", "well", "much", "them", "they", "their", "there", "then"
    );

    public List<ScoredChunk> findSimilarChunks(String queryText, List<DocumentChunkEntity> allChunks) {
        return findSimilarChunks(queryText, allChunks, storageConfig.getVectorTopK());
    }

    public List<ScoredChunk> findSimilarChunks(String queryText, List<DocumentChunkEntity> allChunks, int topK) {
        if (allChunks == null || allChunks.isEmpty() || queryText == null || queryText.isBlank()) {
            log.warn("No chunks or empty query for similarity search");
            return List.of();
        }

        float[] queryVector = embeddingService.embed(queryText);
        double threshold = storageConfig.getVectorSimilarityThreshold();
        boolean hasContentWords = hasSignificantContentWords(queryText);

        List<ScoredChunk> allScored = allChunks.stream()
                .filter(chunk -> chunk.getEmbeddingJson() != null && !chunk.getEmbeddingJson().isBlank())
                .map(chunk -> {
                    float[] chunkVector = embeddingService.deserializeEmbedding(chunk.getEmbeddingJson());
                    double vectorScore = cosineSimilarity(queryVector, chunkVector);
                    double keywordScore = computeKeywordScore(queryText, chunk.getText());

                    // Relevance guard: If the query has specific content words and NONE exist in this chunk,
                    // suppress the score so irrelevant questions don't trigger false context matches
                    double combinedScore;
                    if (hasContentWords && keywordScore == 0.0) {
                        combinedScore = vectorScore * 0.35; // heavily dampen out-of-scope queries
                    } else {
                        combinedScore = 0.60 * vectorScore + 0.40 * keywordScore;
                    }
                    return new ScoredChunk(chunk, combinedScore);
                })
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .collect(Collectors.toList());

        // Strictly return only chunks meeting the similarity threshold
        return allScored.stream()
                .filter(sc -> sc.score() >= threshold)
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * Checks if the query contains at least one non-stopword content term.
     */
    private boolean hasSignificantContentWords(String query) {
        String[] words = query.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
        for (String w : words) {
            if (w.length() >= 3 && !STOP_WORDS.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keyword overlap scoring between query content terms and chunk text.
     */
    private double computeKeywordScore(String query, String text) {
        if (text == null || text.isBlank()) return 0.0;
        String[] rawWords = query.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
        String textLower = text.toLowerCase();

        int matched = 0;
        int totalContentWords = 0;

        for (String w : rawWords) {
            if (w.length() >= 3 && !STOP_WORDS.contains(w)) {
                totalContentWords++;
                if (textLower.contains(w)) {
                    matched++;
                }
            }
        }

        // Fallback if all query words were stop words
        if (totalContentWords == 0) {
            for (String w : rawWords) {
                if (w.length() >= 3) {
                    totalContentWords++;
                    if (textLower.contains(w)) matched++;
                }
            }
        }

        if (totalContentWords == 0) return 0.0;
        return (double) matched / totalContentWords;
    }

    /**
     * Computes cosine similarity between two embedding vectors.
     * cos(θ) = (A · B) / (||A|| × ||B||)
     */
    public double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) return 0.0;

        int minLen = Math.min(a.length, b.length);
        double dot = 0, normA = 0, normB = 0;

        for (int i = 0; i < minLen; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        if (denominator < 1e-9) return 0.0;

        return Math.max(0.0, dot / denominator);
    }

    /**
     * Assembles a RAG context string from the top-K retrieved chunks.
     */
    public String buildContext(List<ScoredChunk> scoredChunks) {
        if (scoredChunks.isEmpty()) {
            return "No relevant document context found.";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < scoredChunks.size(); i++) {
            ScoredChunk sc = scoredChunks.get(i);
            String docName = sc.chunk().getSafeDocumentFilename();
            sb.append(String.format("[Source %d - Document: '%s' | Chunk #%d | Relevance: %.2f]:\n",
                    i + 1,
                    docName,
                    sc.chunk().getChunkIndex(),
                    sc.score()));
            sb.append(sc.chunk().getText().strip());
            sb.append("\n\n");
        }
        return sb.toString();
    }

    public record ScoredChunk(DocumentChunkEntity chunk, double score) {}
}
