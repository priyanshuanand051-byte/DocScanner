package com.galgotias.docscanner.controller;

import com.galgotias.docscanner.dto.DocumentResponse;
import com.galgotias.docscanner.dto.OcrResponse;
import com.galgotias.docscanner.model.DocumentEntity;
import com.galgotias.docscanner.service.DocumentService;
import com.galgotias.docscanner.service.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * REST Controller for document management.
 * Base path: /api/documents
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentStorageService storageService;

    /**
     * POST /api/documents/upload
     * Uploads a document; triggers async OCR + chunking + embedding pipeline.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No file provided or file is empty."));
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String mimeType = file.getContentType() != null ? file.getContentType() : "";

        if (!isSupportedFileType(filename, mimeType)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unsupported file type '" + mimeType + "'. " +
                             "Supported: PDF, PNG, JPG, JPEG, BMP, GIF, TIFF, TXT"));
        }

        try {
            DocumentEntity doc = documentService.uploadDocument(file);

            // Trigger background pipeline (non-blocking)
            documentService.processDocumentAsync(doc.getId());

            return ResponseEntity.ok(Map.of(
                    "documentId", doc.getId(),
                    "filename", doc.getOriginalFilename(),
                    "fileSize", doc.getFileSize(),
                    "mimeType", mimeType,
                    "status", doc.getStatus().name(),
                    "message", "File uploaded successfully. Processing started in background. " +
                               "Poll GET /api/documents/" + doc.getId() + " for status updates."
            ));

        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    /**
     * GET /api/documents
     * Lists all documents ordered newest first.
     */
    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getAllDocuments() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }

    /**
     * GET /api/documents/{id}
     * Returns document metadata and text preview.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(documentService.getDocumentById(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * GET /api/documents/{id}/ocr
     * Returns full extracted text for a document.
     */
    @GetMapping("/{id}/ocr")
    public ResponseEntity<OcrResponse> getOcrText(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(documentService.getOcrResult(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * POST /api/documents/{id}/reprocess
     * Re-runs the full OCR + chunking + embedding pipeline on an existing document.
     */
    @PostMapping("/{id}/reprocess")
    public ResponseEntity<Map<String, Object>> reprocessDocument(@PathVariable Long id) {
        try {
            documentService.getDocumentById(id); // verify existence
            documentService.processDocumentAsync(id);
            return ResponseEntity.ok(Map.of(
                    "documentId", id,
                    "message", "Reprocessing pipeline started. Poll status for updates."
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * GET /api/documents/{id}/download
     * Downloads the original uploaded file.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long id) {
        try {
            DocumentEntity doc = documentService.getEntityById(id);
            Path filePath = storageService.resolveFilePath(doc.getStoredFilename());

            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] fileBytes = Files.readAllBytes(filePath);
            String contentType = doc.getMimeType() != null
                    ? doc.getMimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + doc.getOriginalFilename() + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(fileBytes);

        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Download failed for doc {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * DELETE /api/documents/{id}
     * Deletes document from disk and database (including all chunks).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteDocument(@PathVariable Long id) {
        try {
            documentService.deleteDocument(id);
            return ResponseEntity.ok(Map.of("message", "Document " + id + " deleted successfully."));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private boolean isSupportedFileType(String filename, String mimeType) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf") || lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".bmp")
                || lower.endsWith(".tiff") || lower.endsWith(".tif") || lower.endsWith(".txt")) {
            return true;
        }
        return mimeType.startsWith("image/")
                || mimeType.equals("application/pdf")
                || mimeType.startsWith("text/");
    }
}
