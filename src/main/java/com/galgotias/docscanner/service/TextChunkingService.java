package com.galgotias.docscanner.service;

import com.galgotias.docscanner.config.StorageConfig;
import com.galgotias.docscanner.model.DocumentChunkEntity;
import com.galgotias.docscanner.model.DocumentEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Text Chunking Service.
 *
 * Implements a sliding-window chunking strategy with configurable overlap
 * to ensure context is preserved across chunk boundaries.
 *
 * Strategy:
 *  - Split at sentence boundaries (". ", "! ", "? ", "\n") where possible
 *  - Fall back to character-level splitting if no natural boundary found
 *  - Maintain configurable overlap between adjacent chunks
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TextChunkingService {

    private final StorageConfig storageConfig;

    // Sentence-level split patterns (highest priority first)
    private static final String[] SPLIT_PATTERNS = {"\n\n", ".\n", ". ", "! ", "? ", "\n", "; "};

    /**
     * Splits document text into overlapping chunks and creates DocumentChunkEntity objects.
     *
     * @param document the parent document entity
     * @param text     the full extracted text to chunk
     * @return         ordered list of chunk entities (not yet persisted)
     */
    public List<DocumentChunkEntity> chunkText(DocumentEntity document, String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        int chunkSize = storageConfig.getChunkSize();
        int overlap = storageConfig.getChunkOverlap();

        List<DocumentChunkEntity> chunks = new ArrayList<>();
        List<TextWindow> windows = buildWindows(text, chunkSize, overlap);

        for (int i = 0; i < windows.size(); i++) {
            TextWindow w = windows.get(i);
            DocumentChunkEntity chunk = DocumentChunkEntity.builder()
                    .document(document)
                    .documentId(document != null ? document.getId() : null)
                    .documentFilename(document != null ? document.getOriginalFilename() : "Document")
                    .chunkIndex(i)
                    .startOffset(w.start())
                    .endOffset(w.end())
                    .text(w.text())
                    .tokenCount(estimateTokenCount(w.text()))
                    .build();
            chunks.add(chunk);
        }

        log.info("Chunked document {} into {} chunks (size={}, overlap={})",
                document.getId(), chunks.size(), chunkSize, overlap);
        return chunks;
    }

    /**
     * Builds sliding text windows with natural boundary detection.
     */
    private List<TextWindow> buildWindows(String text, int size, int overlap) {
        List<TextWindow> windows = new ArrayList<>();
        int pos = 0;
        int textLen = text.length();

        while (pos < textLen) {
            int end = Math.min(pos + size, textLen);

            // Try to extend to a natural boundary if we're not at the end
            if (end < textLen) {
                int boundary = findNaturalBoundary(text, end, size / 2);
                if (boundary > pos) {
                    end = boundary;
                }
            }

            String chunkText = text.substring(pos, end).strip();
            if (!chunkText.isBlank()) {
                windows.add(new TextWindow(pos, end, chunkText));
            }

            // Advance by (size - overlap) to create overlapping windows
            int advance = Math.max(1, size - overlap);
            pos += advance;
        }

        return windows;
    }

    /**
     * Searches backwards from 'end' position for the most natural split boundary.
     * Returns the position just after the boundary, or 'end' if none found.
     */
    private int findNaturalBoundary(String text, int end, int searchRange) {
        int searchStart = Math.max(0, end - searchRange);

        for (String pattern : SPLIT_PATTERNS) {
            int idx = text.lastIndexOf(pattern, end);
            if (idx >= searchStart && idx > 0) {
                return idx + pattern.length();
            }
        }
        return end;
    }

    /**
     * Estimates token count: approximately 4 characters per token (GPT-style tokenization).
     */
    public int estimateTokenCount(String text) {
        if (text == null || text.isBlank()) return 0;
        return Math.max(1, text.length() / 4);
    }

    /**
     * Simple record holding a text window with character offsets.
     */
    private record TextWindow(int start, int end, String text) {}
}
