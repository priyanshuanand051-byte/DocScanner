package com.galgotias.docscanner.service;

import com.galgotias.docscanner.dto.DocumentResponse;
import com.galgotias.docscanner.dto.OcrResponse;
import com.galgotias.docscanner.model.DocumentChunkEntity;
import com.galgotias.docscanner.model.DocumentEntity;
import com.galgotias.docscanner.model.DocumentEntity.ProcessingStatus;
import com.galgotias.docscanner.repository.DocumentChunkRepository;
import com.galgotias.docscanner.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Central Document Service — coordinates the full document processing pipeline.
 * Upload → Store → OCR → Chunk → Embed → Ready
 *
 * Key design:
 *  - Upload is synchronous and returns immediately.
 *  - Processing runs via @Async in a background thread.
 *  - Each processing stage commits individually (Propagation.REQUIRES_NEW)
 *    so the UI can poll for real-time status updates.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentStorageService storageService;
    private final OcrService ocrService;
    private final TextChunkingService chunkingService;
    private final EmbeddingService embeddingService;

    // =========================================================================
    // Upload & Process
    // =========================================================================

    /**
     * Stores uploaded file and creates a database record.
     * Returns immediately — processing is triggered asynchronously.
     */
    @Transactional
    public DocumentEntity uploadDocument(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "unnamed";
        String storedFilename = storageService.storeFile(file);

        DocumentEntity doc = documentRepository.save(DocumentEntity.builder()
                .originalFilename(originalFilename)
                .storedFilename(storedFilename)
                .mimeType(file.getContentType())
                .fileSize(file.getSize())
                .status(ProcessingStatus.UPLOADED)
                .build());

        log.info("Document uploaded: id={} file={}", doc.getId(), originalFilename);
        return doc;
    }

    /**
     * Launches async background processing (called from controller after upload).
     * @Async ensures the HTTP response is returned first, then processing runs in background.
     */
    @Async
    public void processDocumentAsync(Long documentId) {
        try {
            processDocument(documentId);
        } catch (Exception e) {
            log.error("Async processing failed for doc {}: {}", documentId, e.getMessage(), e);
            markError(documentId, e.getMessage());
        }
    }

    /**
     * Full processing pipeline. Each stage updates the DB status so the
     * frontend can poll and show real-time progress.
     *
     * Uses REQUIRES_NEW so each status update is immediately committed
     * (visible to polling requests even while the pipeline is still running).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processDocument(Long documentId) {
        DocumentEntity doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));

        log.info("Starting processing pipeline for document: {} [{}]",
                documentId, doc.getOriginalFilename());

        // --- Stage 1: OCR ---
        updateStatus(documentId, ProcessingStatus.OCR);
        OcrService.OcrResult ocr = ocrService.extractText(doc.getStoredFilename(), doc.getMimeType());

        doc = documentRepository.findById(documentId).orElseThrow();
        doc.setExtractedText(ocr.getText());
        doc.setPageCount(Math.max(ocr.getPageCount(), 1));
        documentRepository.save(doc);

        // --- Stage 2: Chunking ---
        updateStatus(documentId, ProcessingStatus.CHUNKING);
        // Delete any old chunks (for reprocess)
        chunkRepository.deleteAllByDocumentId(documentId);

        doc = documentRepository.findById(documentId).orElseThrow();
        List<DocumentChunkEntity> chunks = chunkingService.chunkText(doc, ocr.getText());

        // --- Stage 3: Embedding ---
        updateStatus(documentId, ProcessingStatus.EMBEDDING);
        for (DocumentChunkEntity chunk : chunks) {
            float[] vector = embeddingService.embed(chunk.getText());
            chunk.setEmbeddingJson(embeddingService.serializeEmbedding(vector));
        }
        chunkRepository.saveAll(chunks);

        // --- Stage 4: Done ---
        doc = documentRepository.findById(documentId).orElseThrow();
        doc.setChunkCount(chunks.size());
        doc.setProcessedAt(LocalDateTime.now());
        doc.setStatus(ProcessingStatus.READY);
        documentRepository.save(doc);

        log.info("Document {} processing complete: {} chars, {} chunks, engine={}",
                documentId,
                ocr.getText() != null ? ocr.getText().length() : 0,
                chunks.size(),
                ocr.getEngine());
    }

    // =========================================================================
    // Read Operations
    // =========================================================================

    public List<DocumentResponse> getAllDocuments() {
        return documentRepository.findAllByOrderByUploadedAtDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public DocumentResponse getDocumentById(Long id) {
        return documentRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + id));
    }

    public DocumentEntity getEntityById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + id));
    }

    public OcrResponse getOcrResult(Long documentId) {
        DocumentEntity doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));
        String text = doc.getExtractedText() != null ? doc.getExtractedText() : "";
        return OcrResponse.builder()
                .documentId(documentId)
                .extractedText(text)
                .characterCount(text.length())
                .wordCount(text.isBlank() ? 0 : text.split("\\s+").length)
                .lineCount(text.isBlank() ? 0 : text.split("\n").length)
                .ocrEngine("Stored")
                .processingTimeMs(0)
                .build();
    }

    // =========================================================================
    // Delete
    // =========================================================================

    @Transactional
    public void deleteDocument(Long id) {
        DocumentEntity doc = documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + id));
        storageService.deleteFile(doc.getStoredFilename());
        documentRepository.delete(doc);
        log.info("Document {} deleted.", id);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(Long documentId, ProcessingStatus status) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setStatus(status);
            documentRepository.save(doc);
            log.info("Document {} → {}", documentId, status);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markError(Long documentId, String errorMessage) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setStatus(ProcessingStatus.ERROR);
            doc.setErrorMessage(errorMessage != null
                    ? errorMessage.substring(0, Math.min(errorMessage.length(), 1000))
                    : "Unknown error");
            documentRepository.save(doc);
        });
    }

    public DocumentResponse toResponse(DocumentEntity doc) {
        String text = doc.getExtractedText();
        String preview = (text != null && text.length() > 300)
                ? text.substring(0, 300) + "..." : text;

        return DocumentResponse.builder()
                .id(doc.getId())
                .originalFilename(doc.getOriginalFilename())
                .mimeType(doc.getMimeType())
                .fileSize(doc.getFileSize())
                .status(doc.getStatus())
                .pageCount(doc.getPageCount())
                .chunkCount(doc.getChunkCount())
                .extractedTextLength(text != null ? text.length() : 0)
                .summary(doc.getSummary())
                .errorMessage(doc.getErrorMessage())
                .uploadedAt(doc.getUploadedAt())
                .processedAt(doc.getProcessedAt())
                .textPreview(preview)
                .build();
    }
}
