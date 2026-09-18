package com.galgotias.docscanner.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a chunked text segment of a document with an associated embedding vector.
 * These chunks form the retrieval corpus for the RAG pipeline.
 */
@Entity
@Table(name = "document_chunks", indexes = {
    @Index(name = "idx_chunk_document", columnList = "document_id"),
    @Index(name = "idx_chunk_index", columnList = "chunk_index")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Parent document reference */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    /** Explicit document ID for zero-session proxy-free access */
    @Column(name = "doc_id")
    private Long documentId;

    /** Cached document filename to prevent LazyInitializationException outside JPA session */
    @Column(length = 500)
    private String documentFilename;

    /** Sequential index of this chunk within the document */
    @Column(nullable = false)
    private int chunkIndex;

    /** Character offset start within extracted text */
    private int startOffset;

    /** Character offset end within extracted text */
    private int endOffset;

    /** The chunk text content */
    @Column(columnDefinition = "CLOB", nullable = false)
    private String text;

    /** JSON-serialized float array of the embedding vector (e.g., OpenAI 1536-dim or local TF-IDF) */
    @Column(columnDefinition = "CLOB")
    private String embeddingJson;

    /** Estimated token count for this chunk */
    @Builder.Default
    private int tokenCount = 0;

    /** Safe accessor for document ID */
    public Long getSafeDocumentId() {
        if (documentId != null) return documentId;
        if (document != null) return document.getId();
        return null;
    }

    /** Safe accessor for document filename */
    public String getSafeDocumentFilename() {
        if (documentFilename != null && !documentFilename.isBlank()) return documentFilename;
        if (document != null) return document.getOriginalFilename();
        return "Document";
    }
}
