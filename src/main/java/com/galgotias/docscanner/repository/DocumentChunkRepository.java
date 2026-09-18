package com.galgotias.docscanner.repository;

import com.galgotias.docscanner.model.DocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, Long> {

    List<DocumentChunkEntity> findByDocumentIdOrderByChunkIndex(Long documentId);

    @Query("SELECT c FROM DocumentChunkEntity c LEFT JOIN FETCH c.document WHERE c.document.id = :documentId AND c.embeddingJson IS NOT NULL")
    List<DocumentChunkEntity> findEmbeddedChunksByDocumentId(Long documentId);

    @Query("SELECT c FROM DocumentChunkEntity c LEFT JOIN FETCH c.document WHERE c.embeddingJson IS NOT NULL")
    List<DocumentChunkEntity> findAllEmbeddedChunks();

    long countByDocumentId(Long documentId);

    @Modifying
    @Transactional
    @Query("DELETE FROM DocumentChunkEntity c WHERE c.document.id = :documentId")
    void deleteAllByDocumentId(Long documentId);
}
