package com.drivingschool.backend.live.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.live.enums.SessionStatus;
import com.drivingschool.backend.school.entity.School;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "live_sessions", indexes = {
        @Index(name = "idx_live_sessions_instructor_id", columnList = "instructor_id"),
        @Index(name = "idx_live_sessions_scheduled_at", columnList = "scheduled_at"),
        @Index(name = "idx_live_sessions_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveSession extends BaseEntity {

    /** A live class is at most a day long - keeps "still running" lookups bounded. */
    public static final int MAX_DURATION_MINUTES = 24 * 60;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "meeting_url", length = 500)
    private String meetingUrl;

    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SessionStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private InstructorProfile instructor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @OneToMany(mappedBy = "session", fetch = FetchType.LAZY)
    private List<Attendance> attendances = new ArrayList<>();

    @Builder
    public LiveSession(String title, String description, LocalDateTime scheduledAt,
                       Integer durationMinutes, String meetingUrl, Integer maxParticipants,
                       SessionStatus status, InstructorProfile instructor, School school) {
        this.title = title;
        this.description = description;
        this.scheduledAt = scheduledAt;
        this.durationMinutes = durationMinutes;
        this.meetingUrl = meetingUrl;
        this.maxParticipants = maxParticipants;
        this.status = status;
        this.instructor = instructor;
        this.school = school;
    }

    public void updateStatus(SessionStatus status) {
        this.status = status;
    }

    public LocalDateTime getEndsAt() {
        return scheduledAt.plusMinutes(durationMinutes);
    }

    public boolean hasEnded(LocalDateTime now) {
        return !now.isBefore(getEndsAt());
    }
}
