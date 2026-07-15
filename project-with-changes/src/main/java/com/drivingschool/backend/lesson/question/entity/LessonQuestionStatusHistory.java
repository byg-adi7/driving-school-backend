package com.drivingschool.backend.lesson.question.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.lesson.question.enums.QuestionStatus;
import com.drivingschool.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "lesson_question_status_history",
        indexes = {
                @Index(name = "idx_status_history_question", columnList = "question_submission_id"),
                @Index(name = "idx_status_history_changed_by", columnList = "changed_by_id"),
                @Index(name = "idx_status_history_created_at", columnList = "created_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonQuestionStatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_submission_id", nullable = false)
    private LessonQuestionSubmission questionSubmission;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private QuestionStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private QuestionStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_id", nullable = false)
    private User changedBy;

    // createdAt is inherited from BaseEntity auditing

    @Column(name = "change_reason", length = 500)
    private String changeReason;
}
