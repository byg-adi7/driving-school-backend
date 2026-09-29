package com.drivingschool.backend.learning.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.learning.dto.CourseResponse;
import com.drivingschool.backend.learning.dto.CreateCourseRequest;
import com.drivingschool.backend.learning.dto.UpdateCourseRequest;
import com.drivingschool.backend.learning.entity.Course;
import com.drivingschool.backend.learning.enums.CourseStatus;
import com.drivingschool.backend.learning.mapper.LearningMapper;
import com.drivingschool.backend.learning.repository.CourseRepository;
import com.drivingschool.backend.learning.validator.LearningValidator;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final LearningMapper mapper;
    private final LearningValidator validator;

    public CourseServiceImpl(CourseRepository courseRepository,
                              InstructorProfileRepository instructorProfileRepository,
                              LearningMapper mapper,
                              LearningValidator validator) {
        this.courseRepository = courseRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.mapper = mapper;
        this.validator = validator;
    }

    // Only getPublished() is cached (see its own comment below) - create() can't
    // affect that list since new courses are always DRAFT, but it's evicted
    // anyway for defensiveness at near-zero cost since course creation is rare.
    @Override
    @Transactional
    @CacheEvict(value = "courses", key = "'published'")
    public CourseResponse create(CreateCourseRequest request, Long userId, String role) {
        InstructorProfile instructor = resolveInstructor(request.getInstructorId(), userId, role);

        Course course = Course.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(CourseStatus.DRAFT)
                .instructor(instructor)
                .build();

        Course saved = courseRepository.save(course);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "courses", key = "'published'")
    public CourseResponse update(Long courseId, UpdateCourseRequest request, Long userId, String role) {
        Course course = findCourse(courseId);
        validator.validateCourseOwnership(course, userId, role);

        course.updateDetails(request.getTitle(), request.getDescription());
        return mapper.toResponse(courseRepository.save(course));
    }

    @Override
    @Transactional
    @CacheEvict(value = "courses", key = "'published'")
    public CourseResponse publish(Long courseId, Long userId, String role) {
        Course course = findCourse(courseId);
        validator.validateCourseOwnership(course, userId, role);

        course.publish();
        return mapper.toResponse(courseRepository.save(course));
    }

    @Override
    @Transactional
    @CacheEvict(value = "courses", key = "'published'")
    public CourseResponse unpublish(Long courseId, Long userId, String role) {
        Course course = findCourse(courseId);
        validator.validateCourseOwnership(course, userId, role);

        course.unpublish();
        return mapper.toResponse(courseRepository.save(course));
    }

    @Override
    @Transactional
    @CacheEvict(value = "courses", key = "'published'")
    public CourseResponse archive(Long courseId, Long userId, String role) {
        Course course = findCourse(courseId);
        validator.validateCourseOwnership(course, userId, role);

        course.archive();
        return mapper.toResponse(courseRepository.save(course));
    }

    @Override
    @Transactional(readOnly = true)
    public CourseResponse getById(Long courseId, Long userId, String role) {
        Course course = findCourse(courseId);
        validator.validateCourseReadAccess(course, userId, role);
        return mapper.toResponse(course);
    }

    // Safe to cache uniformly across every caller - always filters to PUBLISHED
    // only, with no role/ownership-based variation in what's returned (unlike
    // getById, which is intentionally left uncached since drafts are
    // owner/admin-only and caching by courseId alone would risk serving a
    // cached draft response to a caller who shouldn't see it).
    @Override
    @Cacheable(value = "courses", key = "'published'")
    @Transactional(readOnly = true)
    public List<CourseResponse> getPublished() {
        return courseRepository.findByStatus(CourseStatus.PUBLISHED).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseResponse> getMine(Long userId) {
        InstructorProfile instructor = instructorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor profile not found for user ID: " + userId));

        return courseRepository.findByInstructor_Id(instructor.getId()).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private Course findCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
    }

    // Caller is always ADMIN or INSTRUCTOR (enforced by the controller's
    // @PreAuthorize) - an instructor always creates under their own identity;
    // only ADMIN may target another instructor via the request body.
    private InstructorProfile resolveInstructor(Long requestedInstructorId, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            if (requestedInstructorId == null) {
                throw new BadRequestException("Instructor ID is required when creating a course as ADMIN");
            }
            InstructorProfile instructor = instructorProfileRepository.findById(requestedInstructorId)
                    .orElseThrow(() -> new ResourceNotFoundException("InstructorProfile", "id", requestedInstructorId));
            validator.validateAdminSchoolAccess(instructor);
            return instructor;
        }

        return instructorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor profile not found for user ID: " + userId));
    }
}
