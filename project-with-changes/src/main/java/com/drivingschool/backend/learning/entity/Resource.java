package com.drivingschool.backend.learning.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.learning.enums.ResourceType;
import com.drivingschool.backend.storage.StoredFile;
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

    // Exactly one of fileUrl (an external link) or storagePath (an uploaded file) is
    // set - enforced by ck_learning_resources_link_or_upload (V17).
    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "storage_path", length = 500)
    private String storagePath;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ResourceType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private VideoLesson lesson;

    /** An external link resource. */
    @Builder
    public Resource(String title, String fileUrl, ResourceType type, VideoLesson lesson) {
        this.title = title;
        this.fileUrl = fileUrl;
        this.type = type;
        this.lesson = lesson;
    }

    /** A resource whose file was uploaded through StorageService. */
    public static Resource uploaded(String title, ResourceType type, VideoLesson lesson, StoredFile file) {
        Resource resource = new Resource();
        resource.title = title;
        resource.type = type;
        resource.lesson = lesson;
        resource.attach(file);
        return resource;
    }

    public boolean isUploaded() {
        return storagePath != null;
    }

    public void rename(String newTitle) {
        this.title = newTitle;
    }

    /**
     * Swaps in a newly stored file; returns the previous storage path so the caller can
     * delete that object once the swap has succeeded.
     */
    public String replaceFile(StoredFile file) {
        String previous = this.storagePath;
        attach(file);
        return previous;
    }

    private void attach(StoredFile file) {
        this.fileUrl = null;
        this.storagePath = file.getStoragePath();
        this.fileName = file.getFileName();
        this.fileSize = file.getFileSize();
        this.contentType = file.getContentType();
    }
}
