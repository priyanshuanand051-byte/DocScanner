package com.galgotias.docscanner.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring's @Async processing.
 * Used for background document processing (OCR + chunking + embedding)
 * so uploads return immediately while processing continues in a thread pool.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
