package com.galgotias.docscanner.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO for the RAG query answer with context citations.
 */
@Data
@Builder
public class RagQueryResponse {

    /** The user's original question */
    private String question;

    /** The generated answer */
    private String answer;

    /** Source document chunks cited for this answer */
    private List<ContextChunk> contextChunks;

    /** Whether OpenAI was used or local engine */
    private boolean aiPowered;

    /** Model used: "gpt-4o-mini", "local-extractive", etc. */
    private String modelUsed;

    /** Total query time in milliseconds */
    private long processingTimeMs;

    @Data
    @Builder
    public static class ContextChunk {
        private Long chunkId;
        private Long documentId;
        private String documentName;
        private int chunkIndex;
        private String text;
        private double similarityScore;
    }
}
