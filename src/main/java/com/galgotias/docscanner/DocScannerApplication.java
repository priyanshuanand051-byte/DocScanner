package com.galgotias.docscanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import lombok.extern.slf4j.Slf4j;

/**
 * DocScanner - AI-Powered Document Scanning and RAG Management System
 *
 * Developed for: Department of Artificial Intelligence & Machine Learning
 * Institution:   Galgotias College of Engineering & Technology
 *
 * Architecture:
 *   Image Upload → Preprocessing (Grayscale/Denoise/Threshold) → OCR (PDFBox/Tess4J)
 *   → Text Chunking → Embeddings (OpenAI / TF-IDF Fallback)
 *   → Vector Store (Cosine Similarity) → RAG Pipeline (GPT / Local)
 *   → Interactive UI (Dashboard / Chat / Search / Viewer)
 */
@SpringBootApplication
@Slf4j
public class DocScannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocScannerApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("=============================================================");
        log.info("  DocScanner AI - Document Scanning & RAG Management System  ");
        log.info("  Galgotias College of Engineering & Technology - AIML Dept  ");
        log.info("=============================================================");
        log.info("  UI:       http://localhost:8080");
        log.info("  API:      http://localhost:8080/api");
        log.info("  H2 DB:    http://localhost:8080/h2-console");
        log.info("  Actuator: http://localhost:8080/actuator/health");
        log.info("=============================================================");
    }
}
