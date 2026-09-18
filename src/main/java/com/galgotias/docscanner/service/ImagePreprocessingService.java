package com.galgotias.docscanner.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.*;
import java.nio.file.Path;

/**
 * Image Preprocessing Service for OCR Enhancement.
 *
 * Pipeline stages (as specified in the DocScanner academic project):
 *  1. Grayscale Conversion   - Luminance-based weighting (0.299R + 0.587G + 0.114B)
 *  2. Noise Reduction        - 3x3 Gaussian blur kernel
 *  3. Adaptive Thresholding  - Otsu's histogram-based binarization
 *  4. Contrast Enhancement   - Linear histogram stretching
 *
 * All operations run entirely in Java AWT/ImageIO — no external native library needed.
 */
@Service
@Slf4j
public class ImagePreprocessingService {

    // Gaussian 3x3 kernel for noise reduction
    private static final float[] GAUSSIAN_3X3 = {
        1/16f, 2/16f, 1/16f,
        2/16f, 4/16f, 2/16f,
        1/16f, 2/16f, 1/16f
    };

    /**
     * Full preprocessing pipeline: grayscale → denoise → threshold → contrast.
     *
     * @param inputPath  path to the source image
     * @param outputPath path where the preprocessed image is saved
     * @return           the preprocessed BufferedImage
     * @throws IOException on IO failure
     */
    public BufferedImage preprocess(Path inputPath, Path outputPath) throws IOException {
        log.info("Preprocessing image: {}", inputPath.getFileName());

        BufferedImage original = ImageIO.read(inputPath.toFile());
        if (original == null) {
            throw new IOException("Cannot read image at: " + inputPath);
        }

        BufferedImage result = original;
        result = toGrayscale(result);
        result = applyGaussianBlur(result);
        result = otsuThreshold(result);
        result = enhanceContrast(result);

        String formatName = getOutputFormat(outputPath.toString());
        ImageIO.write(result, formatName, outputPath.toFile());
        log.info("Preprocessing complete → {}", outputPath.getFileName());
        return result;
    }

    /**
     * Stage 1: Grayscale conversion using luminance weighting.
     * Y = 0.299*R + 0.587*G + 0.114*B
     */
    public BufferedImage toGrayscale(BufferedImage input) {
        int w = input.getWidth();
        int h = input.getHeight();
        BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2 = gray.createGraphics();
        g2.drawImage(input, 0, 0, null);
        g2.dispose();
        return gray;
    }

    /**
     * Stage 2: Gaussian blur (3x3 kernel) for noise reduction.
     */
    public BufferedImage applyGaussianBlur(BufferedImage input) {
        Kernel kernel = new Kernel(3, 3, GAUSSIAN_3X3);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        return op.filter(input, null);
    }

    /**
     * Stage 3: Otsu's binarization — automatically determines the optimal
     * threshold by minimizing intra-class variance across the intensity histogram.
     */
    public BufferedImage otsuThreshold(BufferedImage input) {
        int w = input.getWidth();
        int h = input.getHeight();
        int total = w * h;

        // Build intensity histogram
        int[] histogram = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = input.getRGB(x, y) & 0xFF;
                histogram[pixel]++;
            }
        }

        // Otsu: find threshold maximizing between-class variance
        double sum = 0;
        for (int i = 0; i < 256; i++) sum += i * histogram[i];

        double sumB = 0;
        int wB = 0;
        double maxVariance = 0;
        int threshold = 128;

        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) continue;
            int wF = total - wB;
            if (wF == 0) break;

            sumB += t * histogram[t];
            double mB = sumB / wB;
            double mF = (sum - sumB) / wF;
            double variance = (double) wB * wF * (mB - mF) * (mB - mF);

            if (variance > maxVariance) {
                maxVariance = variance;
                threshold = t;
            }
        }

        log.debug("Otsu threshold computed: {}", threshold);

        // Apply threshold to produce binary image
        BufferedImage binary = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int gray = input.getRGB(x, y) & 0xFF;
                binary.setRGB(x, y, gray > threshold ? 0xFFFFFF : 0x000000);
            }
        }
        return binary;
    }

    /**
     * Stage 4: Linear contrast enhancement (histogram stretching).
     * Maps [minVal, maxVal] → [0, 255].
     */
    public BufferedImage enhanceContrast(BufferedImage input) {
        // Convert to grayscale first for stretch computation
        int w = input.getWidth();
        int h = input.getHeight();

        BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2 = gray.createGraphics();
        g2.drawImage(input, 0, 0, null);
        g2.dispose();

        // Find min/max intensity
        int min = 255, max = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = gray.getRGB(x, y) & 0xFF;
                if (px < min) min = px;
                if (px > max) max = px;
            }
        }

        if (max == min) return gray; // flat image, nothing to stretch

        // Stretch
        BufferedImage enhanced = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = gray.getRGB(x, y) & 0xFF;
                int stretched = (int) ((px - min) * 255.0 / (max - min));
                int newRgb = (stretched << 16) | (stretched << 8) | stretched;
                enhanced.setRGB(x, y, newRgb);
            }
        }
        return enhanced;
    }

    /**
     * Converts a BufferedImage to a byte array as PNG.
     */
    public byte[] toBytes(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    private String getOutputFormat(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "jpg";
        if (lower.endsWith(".bmp")) return "bmp";
        return "png";
    }
}
