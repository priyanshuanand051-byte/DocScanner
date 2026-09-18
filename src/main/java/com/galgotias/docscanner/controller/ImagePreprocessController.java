package com.galgotias.docscanner.controller;

import com.galgotias.docscanner.model.DocumentEntity;
import com.galgotias.docscanner.repository.DocumentRepository;
import com.galgotias.docscanner.service.ImagePreprocessingService;
import com.galgotias.docscanner.service.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;

/**
 * REST Controller for image preprocessing operations.
 *
 * Base path: /api/preprocess
 */
@RestController
@RequestMapping("/api/preprocess")
@RequiredArgsConstructor
@Slf4j
public class ImagePreprocessController {

    private final ImagePreprocessingService preprocessingService;
    private final DocumentStorageService storageService;
    private final DocumentRepository documentRepository;

    /**
     * GET /api/preprocess/{documentId}/preview
     * Returns the preprocessing result as a PNG image for browser rendering.
     * Stage: full pipeline (grayscale + blur + threshold + contrast)
     */
    @GetMapping(value = "/{documentId}/preview", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> preview(@PathVariable Long documentId) {
        return processStage(documentId, "full");
    }

    /**
     * GET /api/preprocess/{documentId}/{stage}
     * Returns the image after a specific preprocessing stage.
     *
     * Stages:
     *  - grayscale   : color → grayscale
     *  - denoise     : grayscale → gaussian blur
     *  - threshold   : blur → otsu binarization
     *  - contrast    : full pipeline with contrast enhancement
     *  - full        : entire pipeline
     */
    @GetMapping(value = "/{documentId}/{stage}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> processStage(@PathVariable Long documentId, @PathVariable String stage) {
        try {
            DocumentEntity doc = documentRepository.findById(documentId)
                    .orElse(null);

            if (doc == null) return ResponseEntity.notFound().build();

            String mime = doc.getMimeType();
            if (mime == null || !mime.startsWith("image/")) {
                return ResponseEntity.badRequest().build();
            }

            Path filePath = storageService.resolveFilePath(doc.getStoredFilename());

            javax.imageio.ImageIO.setUseCache(false);
            BufferedImage original = javax.imageio.ImageIO.read(filePath.toFile());
            if (original == null) return ResponseEntity.unprocessableEntity().build();

            BufferedImage result = switch (stage.toLowerCase()) {
                case "grayscale" -> preprocessingService.toGrayscale(original);
                case "denoise"   -> preprocessingService.applyGaussianBlur(preprocessingService.toGrayscale(original));
                case "threshold" -> preprocessingService.otsuThreshold(preprocessingService.applyGaussianBlur(
                                        preprocessingService.toGrayscale(original)));
                case "contrast"  -> preprocessingService.enhanceContrast(preprocessingService.toGrayscale(original));
                default          -> {
                    Path outPath = filePath.getParent().resolve("temp_preprocessed_" + documentId + ".png");
                    yield preprocessingService.preprocess(filePath, outPath);
                }
            };

            byte[] imageBytes = preprocessingService.toBytes(result, "png");
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(imageBytes);

        } catch (Exception e) {
            log.error("Preprocessing failed for document {}: {}", documentId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/preprocess/{documentId}/info
     * Returns preprocessing pipeline metadata for a document.
     */
    @GetMapping("/{documentId}/info")
    public ResponseEntity<Map<String, Object>> info(@PathVariable Long documentId) {
        DocumentEntity doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(Map.of(
                "documentId", documentId,
                "filename", doc.getOriginalFilename(),
                "mimeType", doc.getMimeType() != null ? doc.getMimeType() : "unknown",
                "isImage", doc.getMimeType() != null && doc.getMimeType().startsWith("image/"),
                "stages", new String[]{"grayscale", "denoise", "threshold", "contrast", "full"},
                "pipeline", "Grayscale → Gaussian Blur (3x3) → Otsu Threshold → Contrast Enhancement"
        ));
    }
}
