package com.drivingschool.backend.messaging.repository;

import com.drivingschool.backend.messaging.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // Newest first, for a chat view that loads the latest page and scrolls back.
    @Query(value = "SELECT m FROM Message m WHERE m.conversation.id = :conversationId ORDER BY m.createdAt DESC, m.id DESC",
            countQuery = "SELECT COUNT(m) FROM Message m WHERE m.conversation.id = :conversationId")
    Page<Message> findByConversationId(@Param("conversationId") Long conversationId, Pageable pageable);

    /** Unread = sent by the other participant and not yet read, one row per conversation: [conversationId, count]. */
    @Query("SELECT m.conversation.id, COUNT(m) FROM Message m "
            + "WHERE m.conversation.id IN :conversationIds AND m.sender.id <> :userId AND m.readAt IS NULL "
            + "GROUP BY m.conversation.id")
    List<Object[]> countUnreadByConversation(@Param("conversationIds") Collection<Long> conversationIds,
                                             @Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Message m SET m.readAt = :readAt "
            + "WHERE m.conversation.id = :conversationId AND m.sender.id <> :userId AND m.readAt IS NULL")
    int markReadForRecipient(@Param("conversationId") Long conversationId,
                             @Param("userId") Long userId,
                             @Param("readAt") LocalDateTime readAt);
}
