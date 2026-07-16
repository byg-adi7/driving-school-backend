package com.drivingschool.backend.lesson.note.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;

@Entity
@Table(
        name = "lesson_notes",
        indexes = {
                @Index(name = "idx_lesson_notes_instructor", columnList = "instructor_id"),
                @Index(name = "idx_lesson_notes_student", columnList = "student_id"),
                @Index(name = "idx_lesson_notes_live_session", columnList = "live_session_id"),
                @Index(name = "idx_lesson_notes_created_at", columnList = "created_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonNote extends BaseEntity {

    @Column(name = "live_session_id", nullable = false)
    private Long liveSessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id", nullable = false)
    private InstructorProfile instructor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(name = "lesson_summary", length = 1000)
    private String lessonSummary;

    @Column(name = "strengths", length = 1500)
    private String strengths;

    @Column(name = "weaknesses", length = 1500)
    private String weaknesses;

    @Column(name = "recommendations", length = 1500)
    private String recommendations;

    // createdAt and updatedAt are inherited from BaseEntity to centralize auditing.
    // (Previously redeclared here with Hibernate's @CreationTimestamp/@UpdateTimestamp,
    // which shadowed BaseEntity's Spring Data JPA auditing fields of the same name and
    // meant created_at never actually got populated - see PRODUCTION_READINESS.md.)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private User updatedBy;

    /**
     * One-to-Many relationship with file attachments
     * Lecture can have multiple PDF attachments
     * Cascade delete: if lesson note is deleted, all attachments are deleted
     * Lazy loading: attachments are loaded only when accessed
     */
    // @ToString.Exclude: LessonNoteAttachment's own @Data-generated toString() includes
    // its back-reference to this LessonNote, so including this collection here would
    // recurse infinitely (LessonNote -> attachments -> LessonNoteAttachment -> lessonNote
    // -> LessonNote -> ...) and crash with a StackOverflowError the moment either side's
    // toString() is ever called with both directions loaded - see PRODUCTION_READINESS.md.
    @ToString.Exclude
    @OneToMany(
            mappedBy = "lessonNote",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<LessonNoteAttachment> attachments = new ArrayList<>();

    /**
     * Add an attachment to this lesson note
     *
     * @param attachment The attachment to add
     */
    public void addAttachment(LessonNoteAttachment attachment) {
        if (this.attachments == null) {
            this.attachments = new ArrayList<>();
        }
        attachment.setLessonNote(this);
        this.attachments.add(attachment);
    }

    /**
     * Remove an attachment from this lesson note
     *
     * @param attachment The attachment to remove
     */
    public void removeAttachment(LessonNoteAttachment attachment) {
        if (this.attachments != null) {
            this.attachments.remove(attachment);
        }
    }

    /**
     * Get count of active attachments
     *
     * @return Count of active attachments
     */
    public long getActiveAttachmentCount() {
        if (this.attachments == null) {
            return 0;
        }
        return this.attachments.stream()
                .filter(LessonNoteAttachment::getIsActive)
                .count();
    }
}
