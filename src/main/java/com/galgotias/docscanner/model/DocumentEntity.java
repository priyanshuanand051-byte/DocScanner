package com.galgotias.docscanner.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JPA Entity representing an uploaded and processed document.
 */
@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Original filename uploaded by user */
    @Column(nullable = false)
    private String originalFilename;

    /** Stored filename (UUID-based) */
    @Column(nullable = false)
    private String storedFilename;

    /** MIME type: image/jpeg, image/png, application/pdf, text/plain */
    @Column
    private String mimeType;

    /** File size in bytes */
    @Column
    private Long fileSize;

    /** Processing status: UPLOADED, PREPROCESSING, OCR, CHUNKING, EMBEDDING, READY, ERROR */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ProcessingStatus status = ProcessingStatus.UPLOADED;

    /** Raw text extracted by OCR or PDFBox */
    @Column(columnDefinition = "CLOB")
    private String extractedText;

    /** AI-generated summary */
    @Column(columnDefinition = "CLOB")
    private String summary;

    /** Number of pages (PDF) or 1 for images */
    @Builder.Default
    private int pageCount = 1;

    /** Number of text chunks created */
    @Builder.Default
    private int chunkCount = 0;

    /** Processing error message, if status is ERROR */
    @Column(columnDefinition = "CLOB")
    private String errorMessage;

    /** Timestamp when document was uploaded */
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime uploadedAt = LocalDateTime.now();

    /** Timestamp when processing completed */
    private LocalDateTime processedAt;

    /** Chunks derived from this document */
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DocumentChunkEntity> chunks;

    public enum ProcessingStatus {
        UPLOADED, PREPROCESSING, OCR, CHUNKING, EMBEDDING, READY, ERROR
    }
}
