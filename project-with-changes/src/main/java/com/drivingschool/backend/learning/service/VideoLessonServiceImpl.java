package com.drivingschool.backend.learning.service;

import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.learning.dto.CreateVideoLessonRequest;
import com.drivingschool.backend.learning.dto.UpdateVideoLessonRequest;
import com.drivingschool.backend.learning.dto.VideoLessonResponse;
import com.drivingschool.backend.learning.entity.Course;
import com.drivingschool.backend.learning.entity.VideoLesson;
import com.drivingschool.backend.learning.mapper.LearningMapper;
import com.drivingschool.backend.learning.repository.CourseRepository;
import com.drivingschool.backend.learning.repository.VideoLessonRepository;
import com.drivingschool.backend.learning.validator.LearningValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VideoLessonServiceImpl implements VideoLessonService {

    private final VideoLessonRepository videoLessonRepository;
    private final CourseRepository courseRepository;
    private final LearningMapper mapper;
    private final LearningValidator validator;

    public VideoLessonServiceImpl(VideoLessonRepository videoLessonRepository,
                                   CourseRepository courseRepository,
                                   LearningMapper mapper,
                                   LearningValidator validator) {
        this.videoLessonRepository = videoLessonRepository;
        this.courseRepository = courseRepository;
        this.mapper = mapper;
        this.validator = validator;
    }

    @Override
    @Transactional
    public VideoLessonResponse create(CreateVideoLessonRequest request, Long userId, String role) {
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", request.getCourseId()));
        validator.validateCourseOwnership(course, userId, role);

        VideoLesson lesson = VideoLesson.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .videoUrl(request.getVideoUrl())
                .lessonOrder(request.getLessonOrder())
                .durationSeconds(request.getDurationSeconds())
                .published(false)
                .course(course)
                .build();

        VideoLesson saved = videoLessonRepository.save(lesson);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public VideoLessonResponse update(Long lessonId, UpdateVideoLessonRequest request, Long userId, String role) {
        VideoLesson lesson = findLesson(lessonId);
        validator.validateLessonOwnership(lesson, userId, role);

        lesson.updateDetails(request.getTitle(), request.getDescription(), request.getVideoUrl(),
                request.getLessonOrder(), request.getDurationSeconds());
        return mapper.toResponse(videoLessonRepository.save(lesson));
    }

    @Override
    @Transactional
    public VideoLessonResponse publish(Long lessonId, Long userId, String role) {
        VideoLesson lesson = findLesson(lessonId);
        validator.validateLessonOwnership(lesson, userId, role);

        lesson.setPublished(true);
        return mapper.toResponse(videoLessonRepository.save(lesson));
    }

    @Override
    @Transactional
    public VideoLessonResponse unpublish(Long lessonId, Long userId, String role) {
        VideoLesson lesson = findLesson(lessonId);
        validator.validateLessonOwnership(lesson, userId, role);

        lesson.setPublished(false);
        return mapper.toResponse(videoLessonRepository.save(lesson));
    }

    @Override
    @Transactional(readOnly = true)
    public VideoLessonResponse getById(Long lessonId, Long userId, String role) {
        VideoLesson lesson = findLesson(lessonId);
        validator.validateLessonReadAccess(lesson, userId, role);
        return mapper.toResponse(lesson);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoLessonResponse> getByCourse(Long courseId, Long userId, String role) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
        validator.validateCourseSchoolAccess(course);
        boolean canManage = validator.isCourseOwnerOrAdmin(course, userId, role);

        return videoLessonRepository.findByCourseIdOrderByLessonOrderAsc(courseId).stream()
                .filter(lesson -> canManage || lesson.isPublished())
                .map(mapper::toResponse)
                .toList();
    }

    private VideoLesson findLesson(Long lessonId) {
        return videoLessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("VideoLesson", "id", lessonId));
    }
}
