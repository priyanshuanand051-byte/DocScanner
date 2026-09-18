package com.galgotias.docscanner.service;

import com.galgotias.docscanner.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;

/**
 * Handles secure storage of uploaded document files.
 * Files are stored with UUID-based names to prevent collisions and path traversal attacks.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentStorageService {

    private final StorageConfig storageConfig;
    private Path uploadRoot;

    @PostConstruct
    public void init() {
        uploadRoot = Paths.get(storageConfig.getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRoot);
            log.info("Document storage initialized at: {}", uploadRoot);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create upload directory: " + uploadRoot, e);
        }
    }

    /**
     * Saves uploaded file to disk with a UUID-based filename.
     *
     * @param file      the uploaded MultipartFile
     * @return          the stored filename (UUID + extension)
     * @throws IOException on IO failure
     */
    public String storeFile(MultipartFile file) throws IOException {
        String original = file.getOriginalFilename();
        String extension = FilenameUtils.getExtension(original != null ? original : "file");
        String storedName = UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension);

        Path destination = uploadRoot.resolve(storedName).normalize();

        // Security: prevent path traversal
        if (!destination.startsWith(uploadRoot)) {
            throw new SecurityException("Cannot store file outside upload directory: " + storedName);
        }

        try (InputStream is = file.getInputStream()) {
            Files.copy(is, destination, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("Stored file: {} -> {}", original, storedName);
        return storedName;
    }

    /**
     * Returns the full resolved Path for a stored filename.
     */
    public Path resolveFilePath(String storedFilename) {
        return uploadRoot.resolve(storedFilename).normalize();
    }

    /**
     * Deletes a stored file from disk.
     *
     * @param storedFilename the stored UUID-based filename
     */
    public void deleteFile(String storedFilename) {
        try {
            Path target = resolveFilePath(storedFilename);
            Files.deleteIfExists(target);
            log.info("Deleted file: {}", storedFilename);
        } catch (IOException e) {
            log.warn("Could not delete file: {} - {}", storedFilename, e.getMessage());
        }
    }

    /**
     * Checks if a file exists on disk.
     */
    public boolean exists(String storedFilename) {
        return Files.exists(resolveFilePath(storedFilename));
    }

    public Path getUploadRoot() {
        return uploadRoot;
    }
}
