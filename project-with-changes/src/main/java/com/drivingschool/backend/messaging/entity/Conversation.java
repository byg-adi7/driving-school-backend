package com.drivingschool.backend.messaging.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.student.entity.StudentProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * The one private thread between a student and an instructor of the same school -
 * reused for every message between that pair, whichever of them starts it.
 */
@Entity
@Table(name = "conversations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_conversations_student_instructor", columnNames = {"student_id", "instructor_id"})
}, indexes = {
        @Index(name = "idx_conversations_instructor", columnList = "instructor_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation extends BaseEntity {

    public static final int PREVIEW_LENGTH = 200;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private InstructorProfile instructor;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "last_message_preview", length = 200)
    private String lastMessagePreview;

    @Builder
    public Conversation(School school, StudentProfile student, InstructorProfile instructor) {
        this.school = school;
        this.student = student;
        this.instructor = instructor;
    }

    public void recordMessage(LocalDateTime sentAt, String body) {
        this.lastMessageAt = sentAt;
        this.lastMessagePreview = body.length() <= PREVIEW_LENGTH ? body : body.substring(0, PREVIEW_LENGTH);
    }

    public boolean hasParticipant(Long userId) {
        return student.getUser().getId().equals(userId) || instructor.getUser().getId().equals(userId);
    }
}
