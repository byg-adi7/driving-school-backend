package com.drivingschool.backend.learning.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.learning.dto.CreateVideoLessonRequest;
import com.drivingschool.backend.learning.dto.UpdateVideoLessonRequest;
import com.drivingschool.backend.learning.dto.VideoLessonResponse;
import com.drivingschool.backend.learning.entity.Course;
import com.drivingschool.backend.learning.entity.VideoLesson;
import com.drivingschool.backend.learning.enums.CourseStatus;
import com.drivingschool.backend.learning.mapper.LearningMapper;
import com.drivingschool.backend.learning.repository.CourseRepository;
import com.drivingschool.backend.learning.repository.VideoLessonRepository;
import com.drivingschool.backend.learning.validator.LearningValidator;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.school.validator.CallerSchoolScope;
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
class VideoLessonServiceImplTest {

    private final AdminSchoolScope adminSchoolScope = mock(AdminSchoolScope.class);
    private final CallerSchoolScope callerSchoolScope = mock(CallerSchoolScope.class);

    @Mock private VideoLessonRepository videoLessonRepository;
    @Mock private CourseRepository courseRepository;
    private final LearningMapper mapper = new LearningMapper();
    private final LearningValidator validator = new LearningValidator(adminSchoolScope, callerSchoolScope);

    private VideoLessonServiceImpl videoLessonService;

    @BeforeEach
    void setUp() {
        videoLessonService = new VideoLessonServiceImpl(videoLessonRepository, courseRepository, mapper, validator);
    }

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Course courseFor(User instructorUser) {
        InstructorProfile instructor = InstructorProfile.builder()
                .user(instructorUser).active(true).school(School.builder().active(true).build()).build();
        Course course = Course.builder().title("Road Safety 101").instructor(instructor).build();
        ReflectionTestUtils.setField(course, "id", 5L);
        return course;
    }

    private VideoLesson lessonFor(User instructorUser, boolean published) {
        VideoLesson lesson = VideoLesson.builder()
                .title("Lesson 1").videoUrl("https://example.com/v1").lessonOrder(1)
                .published(published).course(courseFor(instructorUser)).build();
        ReflectionTestUtils.setField(lesson, "id", 10L);
        return lesson;
    }

    // --- create ---

    @Test
    void create_asOwningInstructor_isAllowed() {
        Course course = courseFor(userWithId(1L));
        CreateVideoLessonRequest request = CreateVideoLessonRequest.builder()
                .courseId(5L).title("Lesson 1").videoUrl("https://example.com/v1").lessonOrder(1).build();

        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(videoLessonRepository.save(any(VideoLesson.class))).thenAnswer(inv -> inv.getArgument(0));

        VideoLessonResponse response = videoLessonService.create(request, 1L, "INSTRUCTOR");

        assertThat(response.getCourseId()).isEqualTo(5L);
        assertThat(response.isPublished()).isFalse();
    }

    @Test
    void create_asUnrelatedInstructor_isDenied() {
        Course course = courseFor(userWithId(1L));
        CreateVideoLessonRequest request = CreateVideoLessonRequest.builder()
                .courseId(5L).title("Lesson 1").videoUrl("https://example.com/v1").lessonOrder(1).build();

        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> videoLessonService.create(request, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);

        verify(videoLessonRepository, never()).save(any());
    }

    @Test
    void create_courseNotFound_throwsResourceNotFoundException() {
        CreateVideoLessonRequest request = CreateVideoLessonRequest.builder()
                .courseId(5L).title("Lesson 1").videoUrl("https://example.com/v1").lessonOrder(1).build();
        when(courseRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> videoLessonService.create(request, 1L, "INSTRUCTOR"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- update / publish ---

    @Test
    void update_asUnrelatedInstructor_isDenied() {
        VideoLesson lesson = lessonFor(userWithId(1L), false);
        UpdateVideoLessonRequest request = new UpdateVideoLessonRequest();
        request.setTitle("New Title");

        when(videoLessonRepository.findById(10L)).thenReturn(Optional.of(lesson));

        assertThatThrownBy(() -> videoLessonService.update(10L, request, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);

        verify(videoLessonRepository, never()).save(any());
    }

    @Test
    void publish_asOwningInstructor_setsPublishedTrue() {
        VideoLesson lesson = lessonFor(userWithId(1L), false);
        when(videoLessonRepository.findById(10L)).thenReturn(Optional.of(lesson));
        when(videoLessonRepository.save(any(VideoLesson.class))).thenAnswer(inv -> inv.getArgument(0));

        VideoLessonResponse response = videoLessonService.publish(10L, 1L, "INSTRUCTOR");

        assertThat(response.isPublished()).isTrue();
    }

    // --- getById: read access ---

    @Test
    void getById_unpublishedLesson_deniedToUnrelatedStudent() {
        VideoLesson lesson = lessonFor(userWithId(1L), false);
        when(videoLessonRepository.findById(10L)).thenReturn(Optional.of(lesson));

        assertThatThrownBy(() -> videoLessonService.getById(10L, 999L, "STUDENT"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getById_publishedLesson_allowedForAnyStudent() {
        VideoLesson lesson = lessonFor(userWithId(1L), true);
        when(videoLessonRepository.findById(10L)).thenReturn(Optional.of(lesson));

        assertThatCode(() -> videoLessonService.getById(10L, 999L, "STUDENT")).doesNotThrowAnyException();
    }

    // --- getByCourse: filtering ---

    @Test
    void getByCourse_asUnrelatedStudent_returnsOnlyPublishedLessons() {
        Course course = courseFor(userWithId(1L));
        VideoLesson published = VideoLesson.builder()
                .title("Published").videoUrl("u").lessonOrder(1).published(true).course(course).build();
        VideoLesson draft = VideoLesson.builder()
                .title("Draft").videoUrl("u").lessonOrder(2).published(false).course(course).build();

        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(videoLessonRepository.findByCourseIdOrderByLessonOrderAsc(5L)).thenReturn(List.of(published, draft));

        List<VideoLessonResponse> result = videoLessonService.getByCourse(5L, 999L, "STUDENT");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Published");
    }

    @Test
    void getByCourse_asOwningInstructor_returnsAllLessons() {
        Course course = courseFor(userWithId(1L));
        VideoLesson published = VideoLesson.builder()
                .title("Published").videoUrl("u").lessonOrder(1).published(true).course(course).build();
        VideoLesson draft = VideoLesson.builder()
                .title("Draft").videoUrl("u").lessonOrder(2).published(false).course(course).build();

        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(videoLessonRepository.findByCourseIdOrderByLessonOrderAsc(5L)).thenReturn(List.of(published, draft));

        List<VideoLessonResponse> result = videoLessonService.getByCourse(5L, 1L, "INSTRUCTOR");

        assertThat(result).hasSize(2);
    }

    @Test
    void getByCourse_courseOfAnotherSchool_isDenied() {
        Course course = Course.builder().title("Road Safety 101").status(CourseStatus.PUBLISHED)
                .instructor(com.drivingschool.backend.instructor.entity.InstructorProfile.builder()
                        .school(com.drivingschool.backend.school.entity.School.builder().active(true).build()).build())
                .build();
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        doThrow(new BadRequestException("no access")).when(callerSchoolScope).requireSameSchool(any());

        assertThatThrownBy(() -> videoLessonService.getByCourse(5L, 999L, "STUDENT"))
                .isInstanceOf(BadRequestException.class);
        verify(videoLessonRepository, never()).findByCourseIdOrderByLessonOrderAsc(any());
    }
}
