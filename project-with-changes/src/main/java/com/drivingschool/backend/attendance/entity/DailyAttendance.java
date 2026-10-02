package com.drivingschool.backend.attendance.entity;

import com.drivingschool.backend.attendance.enums.AttendanceSource;
import com.drivingschool.backend.attendance.enums.DailyAttendanceStatus;
import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One person's attendance for one school-local day (see V20). */
@Entity
@Table(name = "daily_attendance", uniqueConstraints = {
        @UniqueConstraint(name = "uk_daily_attendance_user_date", columnNames = {"user_id", "attendance_date"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyAttendance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleName role;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DailyAttendanceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceSource source;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "accuracy_meters")
    private Double accuracyMeters;

    @Column(name = "distance_meters")
    private Double distanceMeters;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_user_id")
    private User confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id")
    private User recordedBy;

    @Column(length = 500)
    private String reason;

    @Builder
    public DailyAttendance(School school, User user, RoleName role, LocalDate attendanceDate,
                           DailyAttendanceStatus status, AttendanceSource source, LocalDateTime checkedInAt,
                           Double latitude, Double longitude, Double accuracyMeters, Double distanceMeters,
                           User recordedBy, String reason) {
        this.school = school;
        this.user = user;
        this.role = role;
        this.attendanceDate = attendanceDate;
        this.status = status;
        this.source = source;
        this.checkedInAt = checkedInAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.distanceMeters = distanceMeters;
        this.recordedBy = recordedBy;
        this.reason = reason;
    }

    public void confirm(User by, LocalDateTime at) {
        this.status = DailyAttendanceStatus.PRESENT;
        this.confirmedBy = by;
        this.confirmedAt = at;
    }

    /** A staff entry or correction; keeps any check-in location for the audit trail. */
    public void recordManually(DailyAttendanceStatus status, String reason, User by) {
        this.status = status;
        this.reason = reason;
        this.recordedBy = by;
        this.source = AttendanceSource.MANUAL;
    }
}
