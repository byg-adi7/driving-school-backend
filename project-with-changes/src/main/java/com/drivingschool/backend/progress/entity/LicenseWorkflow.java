package com.drivingschool.backend.progress.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.progress.enums.LicenseStage;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "license_workflows", uniqueConstraints = {
        @UniqueConstraint(name = "uk_license_workflows_student_id", columnNames = "student_id")
}, indexes = {
        @Index(name = "idx_license_workflows_student_id", columnList = "student_id"),
        @Index(name = "idx_license_workflows_current_stage", columnList = "current_stage")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LicenseWorkflow extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, unique = true)
    private StudentProfile student;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false, length = 50)
    private LicenseStage currentStage;

    @Column(name = "theory_progress_percent", nullable = false)
    private Integer theoryProgressPercent;

    @Column(name = "road_training_hours", nullable = false)
    private Integer roadTrainingHours;

    @Column(name = "stage_updated_at", nullable = false)
    private LocalDateTime stageUpdatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_instructor_id")
    private InstructorProfile approvedByInstructor;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Builder
    public LicenseWorkflow(StudentProfile student, LicenseStage currentStage,
                           Integer theoryProgressPercent, Integer roadTrainingHours,
                           LocalDateTime stageUpdatedAt, InstructorProfile approvedByInstructor,
                           String notes) {
        this.student = student;
        this.currentStage = currentStage;
        this.theoryProgressPercent = theoryProgressPercent;
        this.roadTrainingHours = roadTrainingHours;
        this.stageUpdatedAt = stageUpdatedAt;
        this.approvedByInstructor = approvedByInstructor;
        this.notes = notes;
    }

    public void advanceStage(LicenseStage newStage, InstructorProfile instructor, String notes) {
        this.currentStage = newStage;
        this.stageUpdatedAt = LocalDateTime.now();
        if (notes != null) {
            this.notes = notes;
        }
        if (isInstructorControlledStage(newStage)) {
            this.approvedByInstructor = instructor;
        }
    }

    public void updateTheoryProgress(int percent) {
        this.theoryProgressPercent = Math.min(100, Math.max(0, percent));
        if (this.theoryProgressPercent >= 100 && this.currentStage == LicenseStage.THEORY_LEARNING) {
            this.currentStage = LicenseStage.THEORY_COMPLETED;
            this.stageUpdatedAt = LocalDateTime.now();
        }
    }

    public void markQuizPassed() {
        if (this.currentStage.ordinal() <= LicenseStage.THEORY_COMPLETED.ordinal()) {
            this.currentStage = LicenseStage.QUIZ_PASSED;
            this.stageUpdatedAt = LocalDateTime.now();
        }
    }

    public static boolean isInstructorControlledStage(LicenseStage stage) {
        return stage == LicenseStage.ROAD_READY
                || stage == LicenseStage.DVLA_PROCESSING
                || stage == LicenseStage.LICENSE_APPROVED;
    }
}
