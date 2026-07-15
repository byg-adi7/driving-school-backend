package com.drivingschool.backend.quiz.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.quiz.enums.SubmissionStatus;
import com.drivingschool.backend.student.entity.StudentProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "quiz_submissions", indexes = {
        @Index(name = "idx_quiz_submissions_quiz_id", columnList = "quiz_id"),
        @Index(name = "idx_quiz_submissions_student_id", columnList = "student_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizSubmission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(nullable = false)
    private Integer attemptNumber;

    @Column(columnDefinition = "TEXT")
    private String answers;

    private Integer score;

    @Column(nullable = false)
    private boolean passed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubmissionStatus status;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Builder
    public QuizSubmission(Quiz quiz, StudentProfile student, Integer attemptNumber,
                          String answers, Integer score, boolean passed,
                          SubmissionStatus status, LocalDateTime submittedAt) {
        this.quiz = quiz;
        this.student = student;
        this.attemptNumber = attemptNumber;
        this.answers = answers;
        this.score = score;
        this.passed = passed;
        this.status = status;
        this.submittedAt = submittedAt;
    }

    public void submit(Integer score, boolean passed) {
        this.score = score;
        this.passed = passed;
        this.status = SubmissionStatus.GRADED;
        this.submittedAt = LocalDateTime.now();
    }
}
