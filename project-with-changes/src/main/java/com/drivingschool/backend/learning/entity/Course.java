package com.drivingschool.backend.learning.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.learning.enums.CourseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "courses", indexes = {
        @Index(name = "idx_courses_instructor_id", columnList = "instructor_id"),
        @Index(name = "idx_courses_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CourseStatus status;

    @Column(name = "published_at")
    private java.time.LocalDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private InstructorProfile instructor;

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY)
    @OrderBy("lessonOrder ASC")
    private List<VideoLesson> lessons = new ArrayList<>();

    @Builder
    public Course(String title, String description, CourseStatus status,
                  InstructorProfile instructor) {
        this.title = title;
        this.description = description;
        this.status = status;
        this.instructor = instructor;
    }

    public void publish() {
        this.status = CourseStatus.PUBLISHED;
        this.publishedAt = java.time.LocalDateTime.now();
    }

    public void unpublish() {
        this.status = CourseStatus.DRAFT;
        this.publishedAt = null;
    }

    public void archive() {
        this.status = CourseStatus.ARCHIVED;
    }

    public void updateDetails(String title, String description) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
    }
}
