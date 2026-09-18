package com.galgotias.docscanner.dto;

import com.galgotias.docscanner.model.DocumentEntity.ProcessingStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO representing a document for API responses.
 */
@Data
@Builder
public class DocumentResponse {
    private Long id;
    private String originalFilename;
    private String mimeType;
    private Long fileSize;
    private ProcessingStatus status;
    private int pageCount;
    private int chunkCount;
    private int extractedTextLength;
    private String summary;
    private String errorMessage;
    private LocalDateTime uploadedAt;
    private LocalDateTime processedAt;

    /** Short preview of the extracted text (first 300 chars) */
    private String textPreview;
}
