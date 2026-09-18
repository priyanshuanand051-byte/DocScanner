package com.galgotias.docscanner.service;

import com.galgotias.docscanner.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Multi-Engine OCR Service.
 *
 * Priority Order:
 *  1. PDFBox    — For digital (non-scanned) PDF files. Fast, lossless, no library.
 *  2. Tess4J   — For scanned images and scanned-PDF rasterizations.
 *  3. Fallback  — When Tesseract native libs are not available on the host OS.
 *                 Produces a graceful "text not extractable" notice instead of crashing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OcrService {

    private final StorageConfig storageConfig;
    private final ImagePreprocessingService imagePreprocessingService;
    private final DocumentStorageService documentStorageService;

    /** Cached result of whether native Tesseract libs are available */
    private Boolean tesseractAvailable = null;

    /**
     * Extracts text from a document file. Selects OCR engine automatically based on file type.
     *
     * @param storedFilename  the UUID-stored filename in the uploads directory
     * @param mimeType        MIME type of the file
     * @return                extracted text string
     */
    public OcrResult extractText(String storedFilename, String mimeType) {
        Path filePath = documentStorageService.resolveFilePath(storedFilename);
        long startTime = System.currentTimeMillis();

        String fname = storedFilename != null ? storedFilename.toLowerCase() : "";
        String mime = mimeType != null ? mimeType.toLowerCase() : "";

        boolean isPdf = fname.endsWith(".pdf") || mime.contains("pdf");
        boolean isText = fname.endsWith(".txt") || fname.endsWith(".md") || fname.endsWith(".csv")
                || fname.endsWith(".json") || mime.startsWith("text/");

        if (isPdf) {
            return extractFromPdf(filePath, startTime);
        } else if (isText) {
            return extractFromTextFile(filePath, startTime);
        } else {
            // Image (PNG, JPG, TIFF, BMP) - preprocess then OCR
            return extractFromImage(filePath, startTime);
        }
    }

    // =========================================================================
    // PDF Extraction via Apache PDFBox
    // =========================================================================

    private OcrResult extractFromPdf(Path pdfPath, long startTime) {
        log.info("Extracting text from PDF: {}", pdfPath.getFileName());
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            int pageCount = doc.getNumberOfPages();

            if (text != null && !text.trim().isEmpty()) {
                // Successful digital PDF extraction
                log.info("PDFBox extracted {} chars from {} pages", text.trim().length(), pageCount);
                return OcrResult.builder()
                        .text(text.trim())
                        .engine("PDFBox")
                        .pageCount(pageCount)
                        .processingTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            } else {
                // Scanned PDF (0 digital text) - attempt OCR
                log.info("PDF has no embedded text stream. Checking for image OCR capability...");
                if (!isTesseractAvailable()) {
                    return OcrResult.builder()
                            .text(String.format("""
                                    [Scanned PDF - OCR Notice]
                                    Document has %d scanned page(s) with no embedded digital text layer.
                                    To enable image OCR for scanned PDFs, configure Tesseract OCR in application.properties.
                                    For digital PDFs with selectable text, DocScanner extracts all content automatically.
                                    """, pageCount))
                            .engine("PDFBox (Scanned)")
                            .pageCount(pageCount)
                            .processingTimeMs(System.currentTimeMillis() - startTime)
                            .build();
                }
                return extractFromScannedPdf(doc, pdfPath, startTime);
            }
        } catch (Exception e) {
            log.error("PDFBox extraction failed: {}", e.getMessage(), e);
            return fallbackResult(startTime, "PDF extraction error: " + e.getMessage());
        }
    }

    private OcrResult extractFromScannedPdf(PDDocument doc, Path pdfPath, long startTime) {
        try {
            org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(doc);
            StringBuilder sb = new StringBuilder();
            int pageCount = Math.min(doc.getNumberOfPages(), 10);

            for (int i = 0; i < pageCount; i++) {
                BufferedImage pageImage = renderer.renderImageWithDPI(i, 300);
                String pageText = ocrBufferedImage(pageImage);
                if (pageText != null && !pageText.isBlank()) {
                    sb.append("--- Page ").append(i + 1).append(" ---\n").append(pageText).append("\n\n");
                }
            }

            String resultText = sb.toString().trim();
            if (resultText.isBlank()) {
                resultText = "[Scanned PDF: No readable text detected by OCR engine]";
            }

            return OcrResult.builder()
                    .text(resultText)
                    .engine("PDFBox+Tess4J")
                    .pageCount(pageCount)
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        } catch (Exception e) {
            log.warn("Scanned PDF OCR failed, using fallback: {}", e.getMessage());
            return fallbackResult(startTime, "Scanned PDF OCR failed: " + e.getMessage());
        }
    }

    // =========================================================================
    // Image Extraction via Tess4J
    // =========================================================================

    private OcrResult extractFromImage(Path imagePath, long startTime) {
        log.info("Extracting text from image: {}", imagePath.getFileName());

        try {
            // Load and preprocess
            BufferedImage originalImage = ImageIO.read(imagePath.toFile());
            if (originalImage == null) {
                return fallbackResult(startTime, "Cannot read image file");
            }

            // Apply preprocessing pipeline
            Path preprocessedPath = imagePath.getParent().resolve("preprocessed_" + imagePath.getFileName());
            BufferedImage processedImage = imagePreprocessingService.preprocess(imagePath, preprocessedPath);

            String text = ocrBufferedImage(processedImage);

            // Clean up preprocessed temp file
            try { Files.deleteIfExists(preprocessedPath); } catch (Exception ignored) {}

            return OcrResult.builder()
                    .text(text)
                    .engine(isTesseractAvailable() ? "Tess4J" : "Fallback")
                    .pageCount(1)
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("Image OCR failed: {}", e.getMessage());
            return fallbackResult(startTime, "Image OCR failed: " + e.getMessage());
        }
    }

    /**
     * Runs Tesseract OCR on a BufferedImage.
     * Falls back gracefully if Tesseract native libraries are missing.
     */
    private String ocrBufferedImage(BufferedImage image) {
        if (!isTesseractAvailable()) {
            return generateFallbackText(image);
        }

        try {
            Tesseract tesseract = new Tesseract();
            String dataPath = storageConfig.getTesseract().getDataPath();
            String lang = storageConfig.getTesseract().getLanguage();

            File tessDataDir = new File(dataPath);
            if (tessDataDir.exists()) {
                tesseract.setDatapath(tessDataDir.getAbsolutePath());
            }
            tesseract.setLanguage(lang);
            tesseract.setPageSegMode(1); // Auto page segmentation with OSD
            tesseract.setOcrEngineMode(1); // LSTM

            return tesseract.doOCR(image);
        } catch (TesseractException | UnsatisfiedLinkError e) {
            log.warn("Tesseract OCR failed: {}. Using fallback.", e.getMessage());
            tesseractAvailable = false;
            return generateFallbackText(image);
        }
    }

    /**
     * Graceful fallback when Tesseract is not installed.
     * Generates a descriptive placeholder instead of crashing the application.
     */
    private String generateFallbackText(BufferedImage image) {
        int width = image != null ? image.getWidth() : 0;
        int height = image != null ? image.getHeight() : 0;
        return String.format("""
                [OCR Fallback Mode]
                
                Tesseract OCR native libraries are not installed on this system.
                
                Image dimensions: %d x %d pixels
                
                To enable full OCR functionality:
                1. Install Tesseract OCR: https://github.com/UB-Mannheim/tesseract/wiki
                2. Set the installation path in application.properties:
                   docscanner.tesseract.data-path=C:/Program Files/Tesseract-OCR/tessdata
                3. Restart the application.
                
                Alternatively, provide an OpenAI API key in application.properties 
                (docscanner.openai.api-key=sk-...) to use GPT-4 Vision as the OCR backend.
                """, width, height);
    }

    // =========================================================================
    // Plain Text File Extraction
    // =========================================================================

    private OcrResult extractFromTextFile(Path textPath, long startTime) {
        try {
            String content = Files.readString(textPath);
            return OcrResult.builder()
                    .text(content)
                    .engine("DirectRead")
                    .pageCount(1)
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        } catch (IOException e) {
            return fallbackResult(startTime, "Text read failed: " + e.getMessage());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private boolean isTesseractAvailable() {
        if (tesseractAvailable == null) {
            try {
                Tesseract t = new Tesseract();
                t.setLanguage("eng");
                tesseractAvailable = true;
                log.info("Tesseract OCR is available");
            } catch (UnsatisfiedLinkError | Exception e) {
                tesseractAvailable = false;
                log.warn("Tesseract OCR not available (native libs missing). Using fallback OCR.");
            }
        }
        return tesseractAvailable;
    }

    private OcrResult fallbackResult(long startTime, String reason) {
        return OcrResult.builder()
                .text("[Text extraction failed: " + reason + "]")
                .engine("Fallback")
                .pageCount(0)
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build();
    }

    /**
     * Inner result record for OCR output.
     */
    @lombok.Builder
    @lombok.Data
    public static class OcrResult {
        private String text;
        private String engine;
        private int pageCount;
        private long processingTimeMs;
    }
}
