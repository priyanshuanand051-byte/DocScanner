package com.galgotias.docscanner.repository;

import com.galgotias.docscanner.model.DocumentEntity;
import com.galgotias.docscanner.model.DocumentEntity.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    List<DocumentEntity> findAllByOrderByUploadedAtDesc();

    List<DocumentEntity> findByStatus(ProcessingStatus status);

    @Query("SELECT d FROM DocumentEntity d WHERE d.status = 'READY' ORDER BY d.uploadedAt DESC")
    List<DocumentEntity> findAllReady();

    @Query("SELECT d FROM DocumentEntity d WHERE LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(d.extractedText) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<DocumentEntity> fullTextSearch(String query);
}
