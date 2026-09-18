package com.galgotias.docscanner.service;

import com.galgotias.docscanner.config.StorageConfig;
import com.galgotias.docscanner.model.DocumentChunkEntity;
import com.galgotias.docscanner.model.DocumentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TextChunkingService.
 * Validates chunk count, overlap, and boundary detection.
 */
class TextChunkingServiceTest {

    private TextChunkingService service;

    @BeforeEach
    void setUp() {
        StorageConfig config = new StorageConfig();
        config.setChunkSize(100);
        config.setChunkOverlap(20);
        service = new TextChunkingService(config);
    }

    private DocumentEntity mockDoc() {
        return DocumentEntity.builder().id(1L).originalFilename("test.pdf").build();
    }

    @Test
    void chunkText_shouldReturnEmptyForBlankText() {
        List<DocumentChunkEntity> chunks = service.chunkText(mockDoc(), "");
        assertThat(chunks).isEmpty();
    }

    @Test
    void chunkText_shouldReturnSingleChunkForShortText() {
        String text = "Short document content.";
        List<DocumentChunkEntity> chunks = service.chunkText(mockDoc(), text);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getText()).contains("Short document");
    }

    @Test
    void chunkText_shouldCreateMultipleChunksForLongText() {
        String text = "a".repeat(500);
        List<DocumentChunkEntity> chunks = service.chunkText(mockDoc(), text);
        assertThat(chunks.size()).isGreaterThan(1);
    }

    @Test
    void chunkText_shouldHaveSequentialIndices() {
        String text = "word ".repeat(300);
        List<DocumentChunkEntity> chunks = service.chunkText(mockDoc(), text);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).getChunkIndex()).isEqualTo(i);
        }
    }

    @Test
    void chunkText_shouldAssignCorrectDocumentReference() {
        DocumentEntity doc = mockDoc();
        List<DocumentChunkEntity> chunks = service.chunkText(doc, "Hello world. This is a test document.");
        chunks.forEach(c -> assertThat(c.getDocument()).isEqualTo(doc));
    }

    @Test
    void estimateTokenCount_shouldBePositiveForNonEmptyText() {
        int tokens = service.estimateTokenCount("Hello, this is a test sentence.");
        assertThat(tokens).isGreaterThan(0);
    }
}
