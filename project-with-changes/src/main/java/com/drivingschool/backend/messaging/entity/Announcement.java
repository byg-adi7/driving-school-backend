package com.drivingschool.backend.messaging.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.school.entity.School;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A one-way message from an instructor to every student of their school. Students
 * can't reply to it; they message the instructor directly instead.
 */
@Entity
@Table(name = "announcements", indexes = {
        @Index(name = "idx_announcements_school_created", columnList = "school_id, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private InstructorProfile instructor;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Builder
    public Announcement(School school, InstructorProfile instructor, String subject, String body) {
        this.school = school;
        this.instructor = instructor;
        this.subject = subject;
        this.body = body;
    }
}
