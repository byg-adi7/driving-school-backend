package com.drivingschool.backend.learning.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.learning.dto.CreateResourceRequest;
import com.drivingschool.backend.learning.dto.ResourceResponse;
import com.drivingschool.backend.learning.entity.Course;
import com.drivingschool.backend.learning.entity.Resource;
import com.drivingschool.backend.learning.entity.VideoLesson;
import com.drivingschool.backend.learning.enums.ResourceType;
import com.drivingschool.backend.learning.mapper.LearningMapper;
import com.drivingschool.backend.learning.repository.ResourceRepository;
import com.drivingschool.backend.learning.repository.VideoLessonRepository;
import com.drivingschool.backend.learning.validator.LearningValidator;
import com.drivingschool.backend.school.entity.School;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceServiceImplTest {

    @Mock private ResourceRepository resourceRepository;
    @Mock private VideoLessonRepository videoLessonRepository;
    private final LearningMapper mapper = new LearningMapper();
    private final LearningValidator validator = new LearningValidator();

    private ResourceServiceImpl resourceService;

    @BeforeEach
    void setUp() {
        resourceService = new ResourceServiceImpl(resourceRepository, videoLessonRepository, mapper, validator);
    }

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private VideoLesson lessonFor(User instructorUser, boolean published) {
        InstructorProfile instructor = InstructorProfile.builder()
                .user(instructorUser).active(true).school(School.builder().active(true).build()).build();
        Course course = Course.builder().title("Road Safety 101").instructor(instructor).build();
        VideoLesson lesson = VideoLesson.builder()
                .title("Lesson 1").videoUrl("u").lessonOrder(1).published(published).course(course).build();
        ReflectionTestUtils.setField(lesson, "id", 10L);
        return lesson;
    }

    private Resource resourceFor(VideoLesson lesson) {
        Resource resource = Resource.builder()
                .title("Handbook").fileUrl("https://example.com/f.pdf").type(ResourceType.PDF).lesson(lesson).build();
        ReflectionTestUtils.setField(resource, "id", 30L);
        return resource;
    }

    // --- create ---

    @Test
    void create_asOwningInstructor_isAllowed() {
        VideoLesson lesson = lessonFor(userWithId(1L), false);
        CreateResourceRequest request = CreateResourceRequest.builder()
                .lessonId(10L).title("Handbook").fileUrl("https://example.com/f.pdf").type(ResourceType.PDF).build();

        when(videoLessonRepository.findById(10L)).thenReturn(Optional.of(lesson));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(inv -> inv.getArgument(0));

        ResourceResponse response = resourceService.create(request, 1L, "INSTRUCTOR");

        assertThat(response.getLessonId()).isEqualTo(10L);
        assertThat(response.getType()).isEqualTo(ResourceType.PDF);
    }

    @Test
    void create_asUnrelatedInstructor_isDenied() {
        VideoLesson lesson = lessonFor(userWithId(1L), false);
        CreateResourceRequest request = CreateResourceRequest.builder()
                .lessonId(10L).title("Handbook").fileUrl("https://example.com/f.pdf").type(ResourceType.PDF).build();

        when(videoLessonRepository.findById(10L)).thenReturn(Optional.of(lesson));

        assertThatThrownBy(() -> resourceService.create(request, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);

        verify(resourceRepository, never()).save(any());
    }

    @Test
    void create_lessonNotFound_throwsResourceNotFoundException() {
        CreateResourceRequest request = CreateResourceRequest.builder()
                .lessonId(10L).title("Handbook").fileUrl("https://example.com/f.pdf").type(ResourceType.PDF).build();
        when(videoLessonRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceService.create(request, 1L, "INSTRUCTOR"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- delete ---

    @Test
    void delete_asUnrelatedInstructor_isDenied() {
        VideoLesson lesson = lessonFor(userWithId(1L), false);
        Resource resource = resourceFor(lesson);
        when(resourceRepository.findById(30L)).thenReturn(Optional.of(resource));

        assertThatThrownBy(() -> resourceService.delete(30L, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);

        verify(resourceRepository, never()).delete(any());
    }

    @Test
    void delete_asOwningInstructor_deletesResource() {
        VideoLesson lesson = lessonFor(userWithId(1L), false);
        Resource resource = resourceFor(lesson);
        when(resourceRepository.findById(30L)).thenReturn(Optional.of(resource));

        resourceService.delete(30L, 1L, "INSTRUCTOR");

        verify(resourceRepository).delete(resource);
    }

    // --- getByLesson: read access ---

    @Test
    void getByLesson_unpublishedLesson_deniedToUnrelatedStudent() {
        VideoLesson lesson = lessonFor(userWithId(1L), false);
        when(videoLessonRepository.findById(10L)).thenReturn(Optional.of(lesson));

        assertThatThrownBy(() -> resourceService.getByLesson(10L, 999L, "STUDENT"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getByLesson_publishedLesson_allowedForAnyStudentAndReturnsResources() {
        VideoLesson lesson = lessonFor(userWithId(1L), true);
        Resource resource = resourceFor(lesson);

        when(videoLessonRepository.findById(10L)).thenReturn(Optional.of(lesson));
        when(resourceRepository.findByLessonIdOrderByIdAsc(10L)).thenReturn(List.of(resource));

        List<ResourceResponse> result = resourceService.getByLesson(10L, 999L, "STUDENT");

        assertThat(result).hasSize(1);
        assertThatCode(() -> resourceService.getByLesson(10L, 999L, "STUDENT")).doesNotThrowAnyException();
    }
}
