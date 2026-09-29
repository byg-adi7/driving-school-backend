package com.drivingschool.backend.messaging.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_messages_conversation_created", columnList = "conversation_id, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    // Set when the OTHER participant reads it; a sender's own messages are never "unread" to them.
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Builder
    public Message(Conversation conversation, User sender, String body) {
        this.conversation = conversation;
        this.sender = sender;
        this.body = body;
    }
}
