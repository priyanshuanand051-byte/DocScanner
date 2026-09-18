package com.galgotias.docscanner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Spring Boot context load test.
 * Verifies all beans wire correctly on startup.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "docscanner.openai.api-key=",
    "docscanner.upload.dir=./test-uploads",
    "docscanner.tesseract.data-path=./tessdata",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DocScannerApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring application context loads without errors.
        // If this test passes, all @Component, @Service, @Repository beans are correctly configured.
    }
}
