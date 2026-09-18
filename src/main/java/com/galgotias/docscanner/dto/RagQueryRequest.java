package com.galgotias.docscanner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for a RAG query request from the user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryRequest {

    /** The user's question */
    private String question;

    /** Optional: restrict search to a specific document ID. Null = search all documents */
    private Long documentId;

    /** Session ID for conversation history tracking */
    private String sessionId;

    /** Number of top chunks to retrieve (overrides server default if provided) */
    private Integer topK;
}
