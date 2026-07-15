package com.drivingschool.backend.progress.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.progress.enums.AssessmentResult;
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
@Table(name = "driving_assessments", indexes = {
        @Index(name = "idx_driving_assessments_student_id", columnList = "student_id"),
        @Index(name = "idx_driving_assessments_instructor_id", columnList = "instructor_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrivingAssessment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private InstructorProfile instructor;

    @Column(name = "assessment_date", nullable = false)
    private LocalDateTime assessmentDate;

    @Column(nullable = false)
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssessmentResult result;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Builder
    public DrivingAssessment(StudentProfile student, InstructorProfile instructor,
                             LocalDateTime assessmentDate, Integer score,
                             AssessmentResult result, String feedback, Integer durationMinutes) {
        this.student = student;
        this.instructor = instructor;
        this.assessmentDate = assessmentDate;
        this.score = score;
        this.result = result;
        this.feedback = feedback;
        this.durationMinutes = durationMinutes;
    }
}
