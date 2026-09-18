package com.galgotias.docscanner.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Storage and processing configuration.
 * Property prefix: "docscanner"
 *
 * Binds from application.properties:
 *   docscanner.upload-dir      → uploadDir
 *   docscanner.chunk-size      → chunkSize
 *   docscanner.chunk-overlap   → chunkOverlap
 *   docscanner.vector-top-k    → vectorTopK
 *   docscanner.vector-similarity-threshold → vectorSimilarityThreshold
 *   docscanner.tesseract.*     → tesseract.* (nested)
 */
@Configuration
@ConfigurationProperties(prefix = "docscanner")
@Data
public class StorageConfig {

    /** Directory where uploaded documents are stored on disk */
    private String uploadDir = "./uploads";

    /** Target character size per text chunk */
    private int chunkSize = 500;

    /** Character overlap between adjacent chunks to preserve context */
    private int chunkOverlap = 100;

    /** Number of top-K chunks to retrieve during RAG context assembly */
    private int vectorTopK = 5;

    /** Minimum cosine similarity score to include a chunk in search results */
    private double vectorSimilarityThreshold = 0.20;

    /** Tesseract OCR sub-configuration */
    private Tesseract tesseract = new Tesseract();

    @Data
    public static class Tesseract {
        /** Path to tessdata directory (folder containing *.traineddata files) */
        private String dataPath = "./tessdata";
        /** OCR language code */
        private String language = "eng";
    }
}
