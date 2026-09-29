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
import com.drivingschool.backend.school.validator.CallerSchoolScope;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final LearningMapper mapper;
    private final LearningValidator validator;
    private final CallerSchoolScope callerSchoolScope;
    private final PublishedCourseCatalog publishedCourseCatalog;

    public CourseServiceImpl(CourseRepository courseRepository,
                              InstructorProfileRepository instructorProfileRepository,
                              LearningMapper mapper,
                              LearningValidator validator,
                              CallerSchoolScope callerSchoolScope,
                              PublishedCourseCatalog publishedCourseCatalog) {
        this.courseRepository = courseRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.mapper = mapper;
        this.validator = validator;
        this.callerSchoolScope = callerSchoolScope;
        this.publishedCourseCatalog = publishedCourseCatalog;
    }

    // Only the published list is cached (PublishedCourseCatalog, one entry per
    // school) - create() can't affect it since new courses are always DRAFT, but
    // it's evicted anyway for defensiveness at near-zero cost since course creation
    // is rare. allEntries because a course change only knows its own school's key
    // plus the bootstrap admin's all-schools key, and schools are few.
    @Override
    @Transactional
    @CacheEvict(value = "courses", allEntries = true)
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
    @CacheEvict(value = "courses", allEntries = true)
    public CourseResponse update(Long courseId, UpdateCourseRequest request, Long userId, String role) {
        Course course = findCourse(courseId);
        validator.validateCourseOwnership(course, userId, role);

        course.updateDetails(request.getTitle(), request.getDescription());
        return mapper.toResponse(courseRepository.save(course));
    }

    @Override
    @Transactional
    @CacheEvict(value = "courses", allEntries = true)
    public CourseResponse publish(Long courseId, Long userId, String role) {
        Course course = findCourse(courseId);
        validator.validateCourseOwnership(course, userId, role);

        course.publish();
        return mapper.toResponse(courseRepository.save(course));
    }

    @Override
    @Transactional
    @CacheEvict(value = "courses", allEntries = true)
    public CourseResponse unpublish(Long courseId, Long userId, String role) {
        Course course = findCourse(courseId);
        validator.validateCourseOwnership(course, userId, role);

        course.unpublish();
        return mapper.toResponse(courseRepository.save(course));
    }

    @Override
    @Transactional
    @CacheEvict(value = "courses", allEntries = true)
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

    // Published courses are confined to the caller's own school (the bootstrap
    // admin sees every school's). The caller's school is resolved here, on every
    // call, and the cached lookup is keyed by it - so each cache entry is still
    // the same for every caller it can be served to. getById stays uncached:
    // drafts are owner/admin-only, and caching by courseId alone would risk
    // serving a cached draft to a caller who shouldn't see it.
    @Override
    public List<CourseResponse> getPublished() {
        return publishedCourseCatalog.forSchool(callerSchoolScope.callerSchoolId().orElse(null));
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
