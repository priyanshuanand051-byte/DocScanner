package com.galgotias.docscanner.controller;

import com.galgotias.docscanner.dto.DocumentResponse;
import com.galgotias.docscanner.repository.DocumentRepository;
import com.galgotias.docscanner.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for semantic and keyword search across documents.
 *
 * Base path: /api/search
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final com.galgotias.docscanner.repository.DocumentChunkRepository chunkRepository;
    private final com.galgotias.docscanner.service.VectorStoreService vectorStoreService;

    /**
     * GET /api/search?q=query
     * Full-text keyword search across document filenames and extracted text.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> search(@RequestParam("q") String query) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Search query cannot be empty"));
        }

        List<DocumentResponse> results = documentRepository.fullTextSearch(query.trim()).stream()
                .map(doc -> {
                    String text = doc.getExtractedText();
                    String preview = text != null && text.length() > 300
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
                            .uploadedAt(doc.getUploadedAt())
                            .textPreview(preview)
                            .build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "query", query,
                "resultCount", results.size(),
                "results", results
        ));
    }

    /**
     * GET /api/search/semantic?q=query&documentId=1&topK=5
     * Semantic vector search - finds the most semantically similar chunks.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @GetMapping("/semantic")
    public ResponseEntity<Map<String, Object>> semanticSearch(
            @RequestParam("q") String query,
            @RequestParam(value = "documentId", required = false) Long documentId,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {

        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query cannot be empty"));
        }

        List<com.galgotias.docscanner.model.DocumentChunkEntity> candidates = documentId != null
                ? chunkRepository.findEmbeddedChunksByDocumentId(documentId)
                : chunkRepository.findAllEmbeddedChunks();

        List<com.galgotias.docscanner.service.VectorStoreService.ScoredChunk> results =
                vectorStoreService.findSimilarChunks(query, candidates, topK);

        List<Map<String, Object>> hits = results.stream()
                .map(sc -> Map.<String, Object>of(
                        "chunkId", sc.chunk().getId(),
                        "documentId", sc.chunk().getSafeDocumentId() != null ? sc.chunk().getSafeDocumentId() : 0L,
                        "documentName", sc.chunk().getSafeDocumentFilename(),
                        "chunkIndex", sc.chunk().getChunkIndex(),
                        "text", sc.chunk().getText(),
                        "similarityScore", Math.round(sc.score() * 1000.0) / 1000.0
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "query", query,
                "searchType", "semantic-cosine-similarity",
                "candidateCount", candidates.size(),
                "resultCount", hits.size(),
                "results", hits
        ));
    }
}
