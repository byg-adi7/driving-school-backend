package com.drivingschool.backend.quiz.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.quiz.enums.QuestionType;
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

@Entity
@Table(name = "quiz_questions", indexes = {
        @Index(name = "idx_quiz_questions_quiz_id", columnList = "quiz_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizQuestion extends BaseEntity {

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 30)
    private QuestionType questionType;

    @Column(columnDefinition = "TEXT")
    private String options;

    @Column(name = "correct_answer", nullable = false, length = 500)
    private String correctAnswer;

    @Column(nullable = false)
    private Integer points;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Builder
    public QuizQuestion(String questionText, QuestionType questionType, String options,
                        String correctAnswer, Integer points, Integer questionOrder, Quiz quiz) {
        this.questionText = questionText;
        this.questionType = questionType;
        this.options = options;
        this.correctAnswer = correctAnswer;
        this.points = points;
        this.questionOrder = questionOrder;
        this.quiz = quiz;
    }
}
