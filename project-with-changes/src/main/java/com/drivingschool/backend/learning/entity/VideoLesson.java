package com.drivingschool.backend.learning.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "video_lessons", indexes = {
        @Index(name = "idx_video_lessons_course_id", columnList = "course_id"),
        @Index(name = "idx_video_lessons_order", columnList = "course_id, lesson_order")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoLesson extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "video_url", nullable = false, length = 500)
    private String videoUrl;

    @Column(name = "lesson_order", nullable = false)
    private Integer lessonOrder;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(nullable = false)
    private boolean published;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @OneToMany(mappedBy = "lesson", fetch = FetchType.LAZY)
    private List<Resource> resources = new ArrayList<>();

    @Builder
    public VideoLesson(String title, String description, String videoUrl, Integer lessonOrder,
                       Integer durationSeconds, boolean published, Course course) {
        this.title = title;
        this.description = description;
        this.videoUrl = videoUrl;
        this.lessonOrder = lessonOrder;
        this.durationSeconds = durationSeconds;
        this.published = published;
        this.course = course;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public void updateDetails(String title, String description, String videoUrl,
                               Integer lessonOrder, Integer durationSeconds) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (videoUrl != null) {
            this.videoUrl = videoUrl;
        }
        if (lessonOrder != null) {
            this.lessonOrder = lessonOrder;
        }
        if (durationSeconds != null) {
            this.durationSeconds = durationSeconds;
        }
    }
}
