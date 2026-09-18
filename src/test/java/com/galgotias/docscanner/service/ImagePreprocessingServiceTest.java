package com.galgotias.docscanner.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ImagePreprocessingService.
 * Tests all four preprocessing pipeline stages.
 */
class ImagePreprocessingServiceTest {

    private ImagePreprocessingService service;

    @BeforeEach
    void setUp() {
        service = new ImagePreprocessingService();
    }

    private BufferedImage createTestImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Alternating black-and-white pattern to test thresholding
                int color = (x + y) % 2 == 0 ? 0xFFFFFF : 0x000000;
                img.setRGB(x, y, color);
            }
        }
        return img;
    }

    @Test
    void toGrayscale_shouldReturnGrayscaleImage() {
        BufferedImage original = createTestImage(100, 100);
        BufferedImage result = service.toGrayscale(original);
        assertThat(result).isNotNull();
        assertThat(result.getWidth()).isEqualTo(100);
        assertThat(result.getHeight()).isEqualTo(100);
    }

    @Test
    void applyGaussianBlur_shouldNotChangeDimensions() {
        BufferedImage gray = service.toGrayscale(createTestImage(80, 80));
        BufferedImage blurred = service.applyGaussianBlur(gray);
        assertThat(blurred).isNotNull();
        assertThat(blurred.getWidth()).isEqualTo(80);
        assertThat(blurred.getHeight()).isEqualTo(80);
    }

    @Test
    void otsuThreshold_shouldProduceBinaryLikeImage() {
        BufferedImage gray = service.toGrayscale(createTestImage(60, 60));
        BufferedImage thresholded = service.otsuThreshold(gray);
        assertThat(thresholded).isNotNull();
        // Binary image should only contain black (0) or white (0xFFFFFF) pixels
        for (int y = 0; y < thresholded.getHeight(); y++) {
            for (int x = 0; x < thresholded.getWidth(); x++) {
                int rgb = thresholded.getRGB(x, y) & 0xFFFFFF;
                assertThat(rgb).isIn(0x000000, 0xFFFFFF);
            }
        }
    }

    @Test
    void enhanceContrast_shouldReturnNonNullImage() {
        BufferedImage gray = service.toGrayscale(createTestImage(50, 50));
        BufferedImage enhanced = service.enhanceContrast(gray);
        assertThat(enhanced).isNotNull();
        assertThat(enhanced.getWidth()).isEqualTo(50);
    }

    @Test
    void toBytes_shouldProduceNonEmptyByteArray() throws Exception {
        BufferedImage img = createTestImage(40, 40);
        byte[] bytes = service.toBytes(img, "png");
        assertThat(bytes).isNotEmpty();
        assertThat(bytes.length).isGreaterThan(100);
    }
}
