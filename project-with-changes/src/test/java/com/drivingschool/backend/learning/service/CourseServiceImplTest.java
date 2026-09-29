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
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

    private final AdminSchoolScope adminSchoolScope = mock(AdminSchoolScope.class);

    @Mock private CourseRepository courseRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;
    private final LearningMapper mapper = new LearningMapper();
    private final LearningValidator validator = new LearningValidator(adminSchoolScope);

    private CourseServiceImpl courseService;

    @BeforeEach
    void setUp() {
        courseService = new CourseServiceImpl(courseRepository, instructorProfileRepository, mapper, validator);
    }

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private InstructorProfile instructorProfile(Long profileId, User user) {
        InstructorProfile instructor = InstructorProfile.builder()
                .user(user).active(true).school(School.builder().active(true).build()).build();
        ReflectionTestUtils.setField(instructor, "id", profileId);
        return instructor;
    }

    private Course courseFor(InstructorProfile instructor, CourseStatus status) {
        Course course = Course.builder().title("Road Safety 101").status(status).instructor(instructor).build();
        ReflectionTestUtils.setField(course, "id", 5L);
        return course;
    }

    // --- create ---

    @Test
    void create_asInstructor_usesOwnInstructorProfileRegardlessOfRequestBody() {
        InstructorProfile instructor = instructorProfile(20L, userWithId(1L));
        CreateCourseRequest request = CreateCourseRequest.builder()
                .title("Road Safety 101").instructorId(999L).build();

        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseResponse response = courseService.create(request, 1L, "INSTRUCTOR");

        assertThat(response.getInstructorId()).isEqualTo(20L);
        assertThat(response.getStatus()).isEqualTo(CourseStatus.DRAFT);
        verify(instructorProfileRepository, never()).findById(any());
    }

    @Test
    void create_asAdmin_honorsRequestBodyInstructorId() {
        InstructorProfile targetInstructor = instructorProfile(30L, userWithId(3L));
        CreateCourseRequest request = CreateCourseRequest.builder()
                .title("Road Safety 101").instructorId(30L).build();

        when(instructorProfileRepository.findById(30L)).thenReturn(Optional.of(targetInstructor));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseResponse response = courseService.create(request, 999L, "ADMIN");

        assertThat(response.getInstructorId()).isEqualTo(30L);
        verify(instructorProfileRepository, never()).findByUserId(any());
    }

    @Test
    void create_asAdminWithoutInstructorId_throwsBadRequestException() {
        CreateCourseRequest request = CreateCourseRequest.builder().title("Road Safety 101").build();

        assertThatThrownBy(() -> courseService.create(request, 999L, "ADMIN"))
                .isInstanceOf(BadRequestException.class);

        verify(courseRepository, never()).save(any());
    }

    @Test
    void create_asInstructorWithNoProfile_throwsResourceNotFoundException() {
        CreateCourseRequest request = CreateCourseRequest.builder().title("Road Safety 101").build();
        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.create(request, 1L, "INSTRUCTOR"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- update / publish / unpublish / archive: ownership ---

    @Test
    void update_asUnrelatedInstructor_isDenied() {
        Course course = courseFor(instructorProfile(20L, userWithId(1L)), CourseStatus.DRAFT);
        UpdateCourseRequest request = new UpdateCourseRequest();
        request.setTitle("New Title");

        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.update(5L, request, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);

        verify(courseRepository, never()).save(any());
    }

    @Test
    void update_asOwningInstructor_updatesFields() {
        Course course = courseFor(instructorProfile(20L, userWithId(1L)), CourseStatus.DRAFT);
        UpdateCourseRequest request = new UpdateCourseRequest();
        request.setTitle("New Title");

        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseResponse response = courseService.update(5L, request, 1L, "INSTRUCTOR");

        assertThat(response.getTitle()).isEqualTo("New Title");
    }

    @Test
    void publish_asUnrelatedInstructor_isDenied() {
        Course course = courseFor(instructorProfile(20L, userWithId(1L)), CourseStatus.DRAFT);
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.publish(5L, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);

        verify(courseRepository, never()).save(any());
    }

    @Test
    void publish_asOwningInstructor_setsPublishedStatus() {
        Course course = courseFor(instructorProfile(20L, userWithId(1L)), CourseStatus.DRAFT);
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseResponse response = courseService.publish(5L, 1L, "INSTRUCTOR");

        assertThat(response.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
    }

    @Test
    void archive_asAdminOfCoursesSchool_isAllowedRegardlessOfOwnership() {
        Course course = courseFor(instructorProfile(20L, userWithId(1L)), CourseStatus.PUBLISHED);
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
        when(adminSchoolScope.canAccess(any())).thenReturn(true);

        CourseResponse response = courseService.archive(5L, 999L, "ADMIN");

        assertThat(response.getStatus()).isEqualTo(CourseStatus.ARCHIVED);
    }

    @Test
    void archive_asAdminOfAnotherSchool_isRejected() {
        Course course = courseFor(instructorProfile(20L, userWithId(1L)), CourseStatus.PUBLISHED);
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(adminSchoolScope.canAccess(any())).thenReturn(false);

        assertThatThrownBy(() -> courseService.archive(5L, 999L, "ADMIN"))
                .isInstanceOf(BadRequestException.class);
        verify(courseRepository, never()).save(any());
    }

    // --- getById: read access ---

    @Test
    void getById_draftCourse_deniedToUnrelatedInstructor() {
        Course course = courseFor(instructorProfile(20L, userWithId(1L)), CourseStatus.DRAFT);
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.getById(5L, 999L, "STUDENT"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getById_publishedCourse_allowedForAnyStudent() {
        Course course = courseFor(instructorProfile(20L, userWithId(1L)), CourseStatus.PUBLISHED);
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        assertThatCode(() -> courseService.getById(5L, 999L, "STUDENT")).doesNotThrowAnyException();
    }

    @Test
    void getById_draftCourse_allowedForOwningInstructor() {
        Course course = courseFor(instructorProfile(20L, userWithId(1L)), CourseStatus.DRAFT);
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        assertThatCode(() -> courseService.getById(5L, 1L, "INSTRUCTOR")).doesNotThrowAnyException();
    }

    // --- getPublished / getMine ---

    @Test
    void getPublished_returnsOnlyPublishedCourses() {
        Course course = courseFor(instructorProfile(20L, userWithId(1L)), CourseStatus.PUBLISHED);
        when(courseRepository.findByStatus(CourseStatus.PUBLISHED)).thenReturn(List.of(course));

        List<CourseResponse> result = courseService.getPublished();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(CourseStatus.PUBLISHED);
    }

    @Test
    void getMine_returnsCallersCoursesRegardlessOfStatus() {
        InstructorProfile instructor = instructorProfile(20L, userWithId(1L));
        Course draft = courseFor(instructor, CourseStatus.DRAFT);
        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));
        when(courseRepository.findByInstructor_Id(20L)).thenReturn(List.of(draft));

        List<CourseResponse> result = courseService.getMine(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void create_asAdminTargetingAnotherSchoolsInstructor_isDenied() {
        CreateCourseRequest request = CreateCourseRequest.builder()
                .title("Road Safety 101").instructorId(30L).build();
        when(instructorProfileRepository.findById(30L)).thenReturn(Optional.of(instructorProfile(30L, userWithId(3L))));
        doThrow(new BadRequestException("no access")).when(adminSchoolScope).requireAccess(any());

        assertThatThrownBy(() -> courseService.create(request, 999L, "ADMIN"))
                .isInstanceOf(BadRequestException.class);
        verify(courseRepository, never()).save(any());
    }

    @Test
    void getById_draftCourse_deniedToAdminOfAnotherSchool() {
        Course course = courseFor(instructorProfile(20L, userWithId(1L)), CourseStatus.DRAFT);
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        doThrow(new BadRequestException("no access")).when(adminSchoolScope).requireAccess(any());

        assertThatThrownBy(() -> courseService.getById(5L, 999L, "ADMIN"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getById_publishedCourse_stillOpenToAdminOfAnotherSchool() {
        Course course = courseFor(instructorProfile(20L, userWithId(1L)), CourseStatus.PUBLISHED);
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        assertThatCode(() -> courseService.getById(5L, 999L, "ADMIN")).doesNotThrowAnyException();
        verify(adminSchoolScope, never()).requireAccess(any());
    }
}
