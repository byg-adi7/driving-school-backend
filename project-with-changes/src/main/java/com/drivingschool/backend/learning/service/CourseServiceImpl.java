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

    @Override
    @Transactional
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
    public CourseResponse update(Long courseId, UpdateCourseRequest request, Long userId, String role) {
        Course course = findCourse(courseId);
        validator.validateCourseOwnership(course, userId, role);

        course.updateDetails(request.getTitle(), request.getDescription());
        return mapper.toResponse(courseRepository.save(course));
    }

    @Override
    @Transactional
    public CourseResponse publish(Long courseId, Long userId, String role) {
        Course course = findCourse(courseId);
        validator.validateCourseOwnership(course, userId, role);

        course.publish();
        return mapper.toResponse(courseRepository.save(course));
    }

    @Override
    @Transactional
    public CourseResponse unpublish(Long courseId, Long userId, String role) {
        Course course = findCourse(courseId);
        validator.validateCourseOwnership(course, userId, role);

        course.unpublish();
        return mapper.toResponse(courseRepository.save(course));
    }

    @Override
    @Transactional
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

    @Override
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
            return instructorProfileRepository.findById(requestedInstructorId)
                    .orElseThrow(() -> new ResourceNotFoundException("InstructorProfile", "id", requestedInstructorId));
        }

        return instructorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor profile not found for user ID: " + userId));
    }
}
