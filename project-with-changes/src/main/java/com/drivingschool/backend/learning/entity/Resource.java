package com.drivingschool.backend.learning.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.learning.enums.ResourceType;
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
@Table(name = "learning_resources", indexes = {
        @Index(name = "idx_learning_resources_lesson_id", columnList = "lesson_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Resource extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ResourceType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private VideoLesson lesson;

    @Builder
    public Resource(String title, String fileUrl, ResourceType type, VideoLesson lesson) {
        this.title = title;
        this.fileUrl = fileUrl;
        this.type = type;
        this.lesson = lesson;
    }
}
