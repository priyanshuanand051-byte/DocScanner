package com.galgotias.docscanner.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO for OCR extraction result.
 */
@Data
@Builder
public class OcrResponse {
    private Long documentId;
    private String extractedText;
    private int characterCount;
    private int wordCount;
    private int lineCount;
    private String ocrEngine; // "PDFBox", "Tess4J", "Fallback"
    private long processingTimeMs;
}
