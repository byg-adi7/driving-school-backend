package com.drivingschool.backend.live.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.live.enums.AttendanceStatus;
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
@Table(name = "attendances", uniqueConstraints = {
        @UniqueConstraint(name = "uk_attendances_session_student", columnNames = {"session_id", "student_id"})
}, indexes = {
        @Index(name = "idx_attendances_session_id", columnList = "session_id"),
        @Index(name = "idx_attendances_student_id", columnList = "student_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private LiveSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AttendanceStatus status;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(length = 500)
    private String notes;

    @Builder
    public Attendance(LiveSession session, StudentProfile student, AttendanceStatus status,
                      LocalDateTime checkedInAt, String notes) {
        this.session = session;
        this.student = student;
        this.status = status;
        this.checkedInAt = checkedInAt;
        this.notes = notes;
    }

    public void markPresent() {
        this.status = AttendanceStatus.PRESENT;
        this.checkedInAt = LocalDateTime.now();
    }

    public void markAbsent() {
        this.status = AttendanceStatus.ABSENT;
    }
}
