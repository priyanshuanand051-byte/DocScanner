package com.galgotias.docscanner.repository;

import com.galgotias.docscanner.model.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<ChatMessageEntity> findByDocumentIdOrderByCreatedAtDesc(Long documentId);

    void deleteBySessionId(String sessionId);
}
