package com.drivingschool.backend.lesson.question.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.lesson.question.enums.QuestionStatus;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "lesson_question_submissions",
        indexes = {
                @Index(name = "idx_question_student", columnList = "student_id"),
                @Index(name = "idx_question_instructor", columnList = "instructor_id"),
                @Index(name = "idx_question_status", columnList = "status"),
                @Index(name = "idx_question_created_at", columnList = "created_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonQuestionSubmission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id")
    private InstructorProfile instructor;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "question_body", nullable = false, length = 3000)
    private String questionBody;

    @Column(name = "response", length = 3000)
    private String response;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QuestionStatus status;

    // createdAt and updatedAt are inherited from BaseEntity to centralize auditing

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by_id")
    private User respondedBy;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
}
