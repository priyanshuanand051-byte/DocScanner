package com.galgotias.docscanner.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A single message in a document-scoped RAG chat session.
 */
@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Session identifier (UUID string from client) */
    @Column(nullable = false)
    private String sessionId;

    /** Document being queried (null = query across all documents) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private DocumentEntity document;

    /** The user's question */
    @Column(columnDefinition = "CLOB", nullable = false)
    private String question;

    /** The AI-generated answer */
    @Column(columnDefinition = "CLOB")
    private String answer;

    /** JSON array of chunk IDs used as context for this answer */
    @Column(columnDefinition = "CLOB")
    private String contextChunkIds;

    /** Similarity scores of retrieved context chunks, JSON array */
    @Column(columnDefinition = "CLOB")
    private String similarityScores;

    /** Was this answered by OpenAI (true) or local engine (false) */
    @Builder.Default
    private boolean aiPowered = false;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
