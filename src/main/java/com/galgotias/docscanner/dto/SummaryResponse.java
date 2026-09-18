package com.galgotias.docscanner.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO for document summarization response.
 */
@Data
@Builder
public class SummaryResponse {
    private Long documentId;
    private String documentName;
    private String executiveSummary;
    private List<String> keyHighlights;
    private List<String> actionItems;
    private int wordCount;
    private boolean aiPowered;
    private long processingTimeMs;
}
