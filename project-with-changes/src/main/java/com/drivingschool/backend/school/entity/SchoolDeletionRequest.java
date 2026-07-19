package com.drivingschool.backend.school.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.school.enums.SchoolDeletionRequestStatus;
import com.drivingschool.backend.user.entity.User;
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

/**
 * Permanent audit trail for school (+ owning admin) deletion requests - rows are
 * never deleted by application code, only transitioned PENDING -> APPROVED/REJECTED.
 * {@code school}/{@code requestedBy} are nullable (ON DELETE SET NULL) because an
 * APPROVED request's own row must survive the very deletion it authorized; the
 * snapshot columns keep the row meaningful once those FKs go null.
 */
@Entity
@Table(name = "school_deletion_requests", indexes = {
        @Index(name = "idx_school_deletion_requests_school_id", columnList = "school_id"),
        @Index(name = "idx_school_deletion_requests_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolDeletionRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    @Column(name = "school_name", nullable = false, length = 200)
    private String schoolName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SchoolDeletionRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private User requestedBy;

    @Column(name = "requested_by_email", nullable = false, length = 255)
    private String requestedByEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_notes", length = 1000)
    private String reviewNotes;

    @Builder
    public SchoolDeletionRequest(School school, String schoolName, SchoolDeletionRequestStatus status,
                                 User requestedBy, String requestedByEmail) {
        this.school = school;
        this.schoolName = schoolName;
        this.status = status;
        this.requestedBy = requestedBy;
        this.requestedByEmail = requestedByEmail;
    }

    public void approve(User reviewer, String notes) {
        this.status = SchoolDeletionRequestStatus.APPROVED;
        this.reviewedBy = reviewer;
        this.reviewedAt = LocalDateTime.now();
        this.reviewNotes = notes;
    }

    public void reject(User reviewer, String notes) {
        this.status = SchoolDeletionRequestStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = LocalDateTime.now();
        this.reviewNotes = notes;
    }
}
