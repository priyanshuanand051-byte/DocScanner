package com.galgotias.docscanner.service;

import com.galgotias.docscanner.config.OpenAiConfig;
import com.galgotias.docscanner.config.StorageConfig;
import com.galgotias.docscanner.dto.RagQueryRequest;
import com.galgotias.docscanner.dto.RagQueryResponse;
import com.galgotias.docscanner.dto.SummaryResponse;
import com.galgotias.docscanner.model.ChatMessageEntity;
import com.galgotias.docscanner.model.DocumentChunkEntity;
import com.galgotias.docscanner.model.DocumentEntity;
import com.galgotias.docscanner.repository.ChatMessageRepository;
import com.galgotias.docscanner.repository.DocumentChunkRepository;
import com.galgotias.docscanner.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) Pipeline Orchestrator.
 *
 * Full Pipeline:
 *  1. Embed the user query
 *  2. Retrieve top-K most similar document chunks (Vector Search)
 *  3. Assemble context string from retrieved chunks
 *  4. Generate answer via GPT (or local extractive engine)
 *  5. Persist conversation history
 *  6. Return response with cited sources
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final OpenAiLlmService llmService;
    private final OpenAiConfig openAiConfig;
    private final StorageConfig storageConfig;

    // =========================================================================
    // RAG Query & Answer
    // =========================================================================

    /**
     * Processes a RAG query: retrieves relevant chunks and generates an answer.
     *
     * @param request the RAG query request DTO
     * @return        the answer with source citations
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public RagQueryResponse query(RagQueryRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("RAG query: '{}'", request.getQuestion());

        // 1. Load candidate chunks
        List<DocumentChunkEntity> candidates = loadCandidateChunks(request.getDocumentId());

        // 2. Vector search
        int topK = request.getTopK() != null ? request.getTopK() : storageConfig.getVectorTopK();
        List<VectorStoreService.ScoredChunk> topChunks = vectorStoreService.findSimilarChunks(
                request.getQuestion(), candidates, topK);

        // Strict relevance check: if no chunks meet the relevance threshold, reject immediately
        if (topChunks.isEmpty()) {
            String docInfo = "";
            if (request.getDocumentId() != null) {
                docInfo = documentRepository.findById(request.getDocumentId())
                        .map(d -> " in '" + d.getOriginalFilename() + "'")
                        .orElse(" in this PDF");
            } else {
                docInfo = " in this PDF";
            }

            String notFoundMessage = "The content related to your question is not available" + docInfo + ". " +
                    "Please ask a question related to the topics covered in this document.";

            long totalTime = System.currentTimeMillis() - startTime;
            return RagQueryResponse.builder()
                    .question(request.getQuestion())
                    .answer(notFoundMessage)
                    .contextChunks(List.of())
                    .aiPowered(openAiConfig.isApiEnabled())
                    .modelUsed(openAiConfig.isApiEnabled() ? openAiConfig.getModel() : "local-extractive")
                    .processingTimeMs(totalTime)
                    .build();
        }

        // 3. Build context string
        String context = vectorStoreService.buildContext(topChunks);

        // 4. Generate answer
        String answer;
        boolean aiPowered;
        String modelUsed;

        if (openAiConfig.isApiEnabled()) {
            try {
                answer = generateGptAnswer(request.getQuestion(), context);
                aiPowered = true;
                modelUsed = llmService.getActiveModel();
            } catch (Exception e) {
                log.warn("GPT failed, using local extractive engine: {}", e.getMessage());
                answer = generateLocalAnswer(request.getQuestion(), topChunks);
                aiPowered = false;
                modelUsed = "local-extractive";
            }
        } else {
            answer = generateLocalAnswer(request.getQuestion(), topChunks);
            aiPowered = false;
            modelUsed = "local-extractive";
        }

        // 5. Build response using safe getters
        List<RagQueryResponse.ContextChunk> contextChunks = topChunks.stream()
                .map(sc -> RagQueryResponse.ContextChunk.builder()
                        .chunkId(sc.chunk().getId())
                        .documentId(sc.chunk().getSafeDocumentId())
                        .documentName(sc.chunk().getSafeDocumentFilename())
                        .chunkIndex(sc.chunk().getChunkIndex())
                        .text(sc.chunk().getText())
                        .similarityScore(Math.round(sc.score() * 1000.0) / 1000.0)
                        .build())
                .collect(Collectors.toList());

        long totalTime = System.currentTimeMillis() - startTime;

        // 6. Persist conversation history
        persistConversation(request, answer, topChunks, aiPowered);

        return RagQueryResponse.builder()
                .question(request.getQuestion())
                .answer(answer)
                .contextChunks(contextChunks)
                .aiPowered(aiPowered)
                .modelUsed(modelUsed)
                .processingTimeMs(totalTime)
                .build();
    }

    // =========================================================================
    // Summarization
    // =========================================================================

    /**
     * Generates an AI summary for a specific document.
     *
     * @param documentId the document to summarize
     * @return           structured summary response
     */
    public SummaryResponse summarize(Long documentId) {
        long startTime = System.currentTimeMillis();

        DocumentEntity doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));

        String text = doc.getExtractedText();
        if (text == null || text.isBlank()) {
            return SummaryResponse.builder()
                    .documentId(documentId)
                    .documentName(doc.getOriginalFilename())
                    .executiveSummary("No text content available for summarization.")
                    .keyHighlights(List.of())
                    .actionItems(List.of())
                    .aiPowered(false)
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        String truncatedText = text.length() > 12000 ? text.substring(0, 12000) + "..." : text;
        int wordCount = text.split("\\s+").length;

        if (openAiConfig.isApiEnabled()) {
            try {
                return generateGptSummary(doc, truncatedText, wordCount, startTime);
            } catch (Exception e) {
                log.warn("GPT summarization failed, using local: {}", e.getMessage());
            }
        }

        return generateLocalSummary(doc, text, wordCount, startTime);
    }

    // =========================================================================
    // Private Helper Methods
    // =========================================================================

    private List<DocumentChunkEntity> loadCandidateChunks(Long documentId) {
        if (documentId != null) {
            return chunkRepository.findEmbeddedChunksByDocumentId(documentId);
        }
        return chunkRepository.findAllEmbeddedChunks();
    }

    private String generateGptAnswer(String question, String context) {
        String systemPrompt = """
                You are DocScanner AI, an intelligent document analysis assistant developed at \
                Galgotias College of Engineering & Technology (AIML Department).
                
                CRITICAL RELEVANCE RULES:
                1. Answer user questions based ONLY on the retrieved document context below.
                2. If the user's question asks about information that is NOT mentioned, discussed, or covered in the context, you MUST state:
                   "The content related to your question is not available in this PDF. Please ask a question related to the topics covered in this document."
                3. Do NOT make assumptions, guess, or use external knowledge not contained in the context.
                4. Be concise, direct, and helpful.
                """;

        String userMessage = String.format("""
                RETRIEVED CONTEXT:
                %s
                
                USER QUESTION: %s
                """, context, question);

        return llmService.complete(systemPrompt, userMessage);
    }

    /**
     * Local extractive answer: returns the most relevant chunk's text as the answer.
     * Simple but effective for demonstrating the RAG pipeline without an API key.
     */
    private String generateLocalAnswer(String question, List<VectorStoreService.ScoredChunk> topChunks) {
        if (topChunks.isEmpty()) {
            return "The content related to your question is not available in this PDF. Please ask a question related to the topics covered in this document.";
        }

        StringBuilder answer = new StringBuilder();
        VectorStoreService.ScoredChunk bestMatch = topChunks.get(0);
        String primaryDoc = bestMatch.chunk().getSafeDocumentFilename();

        answer.append("### Answer from **").append(primaryDoc).append("**\n\n");

        // Synthesize best relevant passage
        String primaryExcerpt = extractRelevantSentences(question, bestMatch.chunk().getText());
        answer.append(primaryExcerpt).append("\n\n");

        // If there are additional supporting context chunks
        if (topChunks.size() > 1) {
            answer.append("#### Supporting Context & Highlights:\n");
            for (int i = 1; i < Math.min(topChunks.size(), 3); i++) {
                VectorStoreService.ScoredChunk sc = topChunks.get(i);
                String excerpt = extractRelevantSentences(question, sc.chunk().getText());
                if (!excerpt.isBlank() && !excerpt.equals(primaryExcerpt)) {
                    answer.append("• **[").append(sc.chunk().getSafeDocumentFilename())
                            .append("]**: ").append(excerpt).append("\n\n");
                }
            }
        }

        if (!openAiConfig.isApiEnabled()) {
            answer.append("\n---\n*ℹ️ Answered using local semantic RAG engine. Configure `OPENAI_API_KEY` for GPT generative synthesis.*");
        }

        return answer.toString();
    }

    /**
     * Extracts relevant content from a chunk: splits on sentences or lines,
     * scores by keyword frequency, and falls back to clean chunk text if general query.
     */
    private String extractRelevantSentences(String query, String chunkText) {
        if (chunkText == null || chunkText.isBlank()) return "";

        String[] queryWords = query.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
        // Split on sentences or line breaks
        String[] parts = chunkText.split("(?<=[.!?])\\s+|\\n+");

        if (parts.length <= 2) return chunkText.strip();

        Map<String, Integer> scoredParts = new LinkedHashMap<>();
        for (String p : parts) {
            String trimmed = p.strip();
            if (trimmed.length() < 8) continue;
            int score = 0;
            String lower = trimmed.toLowerCase();
            for (String w : queryWords) {
                if (w.length() >= 3 && lower.contains(w)) {
                    score += 2;
                }
            }
            scoredParts.put(trimmed, score);
        }

        List<String> topParts = new ArrayList<>();
        Set<String> added = new HashSet<>();

        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].strip();
            if (scoredParts.getOrDefault(p, 0) > 0 && added.add(p)) {
                topParts.add(p);
                // If it's a heading (e.g. ending in ':' or short title), include following content lines
                if ((p.endsWith(":") || p.length() < 40) && i + 1 < parts.length) {
                    for (int j = 1; j <= 5 && (i + j) < parts.length; j++) {
                        String next = parts[i + j].strip();
                        if (!next.isBlank()) {
                            if (added.add(next)) {
                                topParts.add(next);
                            }
                            if (next.endsWith(":")) break; // hit another heading
                        }
                    }
                }
                if (topParts.size() >= 6) break;
            }
        }

        if (!topParts.isEmpty()) {
            return String.join("\n", topParts);
        }

        // If no specific word hit, return the clean chunk directly (first ~350 chars)
        String stripped = chunkText.strip();
        return stripped.length() > 350 ? stripped.substring(0, 350) + "..." : stripped;
    }

    private SummaryResponse generateGptSummary(DocumentEntity doc, String text, int wordCount, long startTime) {
        String systemPrompt = """
                You are DocScanner AI, a document analysis assistant. 
                Analyze the provided document and respond with a JSON structure containing:
                1. "executiveSummary": A 2-3 sentence summary of the document's main purpose and content.
                2. "keyHighlights": An array of 5-7 key points or facts from the document.
                3. "actionItems": An array of actionable items or next steps mentioned (empty array if none).
                
                Respond in plain JSON only, no markdown code blocks.
                """;

        String userMessage = "Document: " + doc.getOriginalFilename() + "\n\nContent:\n" + text;

        try {
            String gptResponse = llmService.complete(systemPrompt, userMessage);
            // Attempt to parse JSON response
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<?, ?> parsed = mapper.readValue(gptResponse.trim(), Map.class);

            return SummaryResponse.builder()
                    .documentId(doc.getId())
                    .documentName(doc.getOriginalFilename())
                    .executiveSummary((String) parsed.get("executiveSummary"))
                    .keyHighlights((List<String>) parsed.get("keyHighlights"))
                    .actionItems((List<String>) parsed.get("actionItems"))
                    .wordCount(wordCount)
                    .aiPowered(true)
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse GPT summary JSON, using text response: {}", e.getMessage());
            return generateLocalSummary(doc, text, wordCount, startTime);
        }
    }

    /**
     * Local extractive summarization: picks first sentences + finds high-frequency terms.
     */
    private SummaryResponse generateLocalSummary(DocumentEntity doc, String text, int wordCount, long startTime) {
        // Extract first ~500 chars as executive summary
        String[] sentences = text.split("[.!?]");
        StringBuilder execSummary = new StringBuilder();
        for (String s : sentences) {
            if (execSummary.length() > 400) break;
            if (!s.trim().isEmpty()) {
                execSummary.append(s.trim()).append(". ");
            }
        }

        // Extract key highlights from paragraphs
        String[] paragraphs = text.split("\n\n");
        List<String> highlights = new ArrayList<>();
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.length() > 30 && highlights.size() < 6) {
                highlights.add(trimmed.length() > 150 ? trimmed.substring(0, 150) + "..." : trimmed);
            }
        }

        return SummaryResponse.builder()
                .documentId(doc.getId())
                .documentName(doc.getOriginalFilename())
                .executiveSummary(execSummary.toString().trim())
                .keyHighlights(highlights.isEmpty() ? List.of("Document processed. Text content extracted.") : highlights)
                .actionItems(List.of())
                .wordCount(wordCount)
                .aiPowered(false)
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build();
    }

    private void persistConversation(RagQueryRequest request, String answer,
                                     List<VectorStoreService.ScoredChunk> topChunks, boolean aiPowered) {
        try {
            String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();
            String chunkIds = topChunks.stream()
                    .map(sc -> String.valueOf(sc.chunk().getId()))
                    .collect(Collectors.joining(","));
            String scores = topChunks.stream()
                    .map(sc -> String.format("%.3f", sc.score()))
                    .collect(Collectors.joining(","));

            DocumentEntity doc = request.getDocumentId() != null
                    ? documentRepository.findById(request.getDocumentId()).orElse(null)
                    : null;

            chatMessageRepository.save(ChatMessageEntity.builder()
                    .sessionId(sessionId)
                    .document(doc)
                    .question(request.getQuestion())
                    .answer(answer)
                    .contextChunkIds("[" + chunkIds + "]")
                    .similarityScores("[" + scores + "]")
                    .aiPowered(aiPowered)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to persist conversation: {}", e.getMessage());
        }
    }
}
