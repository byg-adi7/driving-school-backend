package com.drivingschool.backend.learning.service;

import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.learning.dto.CreateResourceRequest;
import com.drivingschool.backend.learning.dto.ResourceResponse;
import com.drivingschool.backend.learning.entity.Resource;
import com.drivingschool.backend.learning.entity.VideoLesson;
import com.drivingschool.backend.learning.mapper.LearningMapper;
import com.drivingschool.backend.learning.repository.ResourceRepository;
import com.drivingschool.backend.learning.repository.VideoLessonRepository;
import com.drivingschool.backend.learning.validator.LearningValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResourceServiceImpl implements ResourceService {

    private final ResourceRepository resourceRepository;
    private final VideoLessonRepository videoLessonRepository;
    private final LearningMapper mapper;
    private final LearningValidator validator;

    public ResourceServiceImpl(ResourceRepository resourceRepository,
                                VideoLessonRepository videoLessonRepository,
                                LearningMapper mapper,
                                LearningValidator validator) {
        this.resourceRepository = resourceRepository;
        this.videoLessonRepository = videoLessonRepository;
        this.mapper = mapper;
        this.validator = validator;
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
