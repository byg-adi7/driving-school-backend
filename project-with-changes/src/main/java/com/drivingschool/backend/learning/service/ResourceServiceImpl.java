package com.drivingschool.backend.learning.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.learning.dto.CreateResourceRequest;
import com.drivingschool.backend.learning.dto.ResourceResponse;
import com.drivingschool.backend.learning.dto.UpdateResourceRequest;
import com.drivingschool.backend.learning.entity.Resource;
import com.drivingschool.backend.learning.entity.VideoLesson;
import com.drivingschool.backend.learning.enums.ResourceType;
import com.drivingschool.backend.learning.mapper.LearningMapper;
import com.drivingschool.backend.learning.repository.ResourceRepository;
import com.drivingschool.backend.learning.repository.VideoLessonRepository;
import com.drivingschool.backend.learning.validator.LearningValidator;
import com.drivingschool.backend.storage.StorageService;
import com.drivingschool.backend.storage.StoredFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Slf4j
@Service
public class ResourceServiceImpl implements ResourceService {

    private static final String STORAGE_FOLDER = "course-resources";
    private static final int MAX_TITLE_LENGTH = 200;

    private final ResourceRepository resourceRepository;
    private final VideoLessonRepository videoLessonRepository;
    private final LearningMapper mapper;
    private final LearningValidator validator;
    private final StorageService storageService;

    public ResourceServiceImpl(ResourceRepository resourceRepository,
                                VideoLessonRepository videoLessonRepository,
                                LearningMapper mapper,
                                LearningValidator validator,
                                StorageService storageService) {
        this.resourceRepository = resourceRepository;
        this.videoLessonRepository = videoLessonRepository;
        this.mapper = mapper;
        this.validator = validator;
        this.storageService = storageService;
    }

    @Override
    @Transactional
    public ResourceResponse create(CreateResourceRequest request, Long userId, String role) {
        VideoLesson lesson = findLesson(request.getLessonId());
        validator.validateLessonOwnership(lesson, userId, role);

        Resource resource = Resource.builder()
                .title(request.getTitle())
                .fileUrl(request.getFileUrl())
                .type(request.getType())
                .lesson(lesson)
                .build();

        return mapper.toResponse(resourceRepository.save(resource));
    }

    @Override
    @Transactional
    public void delete(Long resourceId, Long userId, String role) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", "id", resourceId));
        validator.validateLessonOwnership(resource.getLesson(), userId, role);

        resourceRepository.delete(resource);
        if (resource.isUploaded()) {
            deleteStoredFileAfterCommit(resource.getStoragePath());
        }
    }

    @Override
    @Transactional
    public ResourceResponse upload(Long lessonId, String title, MultipartFile file, Long userId, String role) {
        VideoLesson lesson = findLesson(lessonId);
        validator.validateLessonOwnership(lesson, userId, role);
        if (title == null || title.isBlank() || title.length() > MAX_TITLE_LENGTH) {
            throw new BadRequestException("Title is required and must not exceed " + MAX_TITLE_LENGTH + " characters");
        }

        StoredFile stored = store(file, lessonId);
        Resource resource = Resource.uploaded(title, ResourceType.PDF, lesson, stored);
        return mapper.toResponse(resourceRepository.save(resource));
    }

    @Override
    @Transactional
    public ResourceResponse rename(Long resourceId, UpdateResourceRequest request, Long userId, String role) {
        Resource resource = findResource(resourceId);
        validator.validateLessonOwnership(resource.getLesson(), userId, role);
        resource.rename(request.getTitle());
        return mapper.toResponse(resource);
    }

    @Override
    @Transactional
    public ResourceResponse replaceFile(Long resourceId, MultipartFile file, Long userId, String role) {
        Resource resource = findResource(resourceId);
        validator.validateLessonOwnership(resource.getLesson(), userId, role);
        if (!resource.isUploaded()) {
            throw new BadRequestException("This resource is an external link - delete it and upload a file instead");
        }

        // New file first, old one removed only after the swap commits: a failed upload
        // or a rolled-back transaction never leaves the resource pointing at nothing.
        StoredFile stored = store(file, resource.getLesson().getId());
        String previousPath = resource.replaceFile(stored);
        deleteStoredFileAfterCommit(previousPath);
        return mapper.toResponse(resource);
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadableFile download(Long resourceId, Long userId, String role) {
        Resource resource = findResource(resourceId);
        validator.validateLessonReadAccess(resource.getLesson(), userId, role);
        if (!resource.isUploaded()) {
            throw new BadRequestException("This resource is an external link - open its fileUrl instead");
        }
        try {
            return new DownloadableFile(storageService.load(resource.getStoragePath()), resource.getFileName());
        } catch (IOException e) {
            log.error("Failed to load stored file for resource {}", resourceId, e);
            throw new IllegalStateException("File could not be loaded", e);
        }
    }

    private StoredFile store(MultipartFile file, Long lessonId) {
        try {
            return storageService.store(file, STORAGE_FOLDER, String.valueOf(lessonId));
        } catch (IllegalArgumentException e) {
            // FileValidator: size, type, spoofed or active PDF content
            throw new BadRequestException(e.getMessage());
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("Failed to store resource file for lesson {}", lessonId, e);
            throw new IllegalStateException("File upload failed", e);
        }
    }

    // Removing the stored object only once the database change has committed means a
    // rolled-back delete/replace never leaves a row pointing at a file that's gone.
    private void deleteStoredFileAfterCommit(String storagePath) {
        Runnable delete = () -> {
            try {
                storageService.delete(storagePath);
            } catch (IOException e) {
                log.warn("Failed to delete stored resource file {}", storagePath, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete.run();
                }
            });
            return;
        }
        delete.run();
    }

    private Resource findResource(Long resourceId) {
        return resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", "id", resourceId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResourceResponse> getByLesson(Long lessonId, Long userId, String role) {
        VideoLesson lesson = findLesson(lessonId);
        validator.validateLessonReadAccess(lesson, userId, role);

        return resourceRepository.findByLessonIdOrderByIdAsc(lessonId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private VideoLesson findLesson(Long lessonId) {
        return videoLessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("VideoLesson", "id", lessonId));
    }
}
