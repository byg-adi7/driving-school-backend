package com.drivingschool.backend.learning.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ForbiddenException;
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
class ResourceServiceImplTest {

    private final AdminSchoolScope adminSchoolScope = mock(AdminSchoolScope.class);
    private final CallerSchoolScope callerSchoolScope = mock(CallerSchoolScope.class);

    @Mock private ResourceRepository resourceRepository;
    @Mock private VideoLessonRepository videoLessonRepository;
    @Mock private com.drivingschool.backend.storage.StorageService storageService;
    private final LearningMapper mapper = new LearningMapper();
    private final LearningValidator validator = new LearningValidator(adminSchoolScope, callerSchoolScope);

    private ResourceServiceImpl resourceService;

    @BeforeEach
    void setUp() {
        resourceService = new ResourceServiceImpl(resourceRepository, videoLessonRepository, mapper, validator, storageService);
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
                .isInstanceOf(ForbiddenException.class);

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
                .isInstanceOf(ForbiddenException.class);

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
                .isInstanceOf(ForbiddenException.class);
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

    // --- uploaded files ---

    private static final org.springframework.mock.web.MockMultipartFile PDF =
            new org.springframework.mock.web.MockMultipartFile("file", "handbook.pdf", "application/pdf", new byte[]{1, 2, 3});

    private com.drivingschool.backend.storage.StoredFile stored(String path) {
        return com.drivingschool.backend.storage.StoredFile.builder()
                .storagePath(path).fileName("handbook.pdf").fileSize(3L).contentType("application/pdf").build();
    }

    private Resource uploadedResourceFor(VideoLesson lesson) {
        Resource resource = Resource.uploaded("Handbook", ResourceType.PDF, lesson, stored("course-resources/10/old.pdf"));
        ReflectionTestUtils.setField(resource, "id", 31L);
        return resource;
    }

    @Test
    void upload_asOwningInstructor_storesThePdfAndReturnsADownloadUrl() throws Exception {
        VideoLesson lesson = lessonFor(userWithId(1L), true);
        when(videoLessonRepository.findById(10L)).thenReturn(Optional.of(lesson));
        when(storageService.store(PDF, "course-resources", "10")).thenReturn(stored("course-resources/10/new.pdf"));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 40L);
            return r;
        });

        ResourceResponse response = resourceService.upload(10L, "Highway Code", PDF, 1L, "INSTRUCTOR");

        assertThat(response.isUploaded()).isTrue();
        assertThat(response.getFileUrl()).isNull();
        assertThat(response.getFileName()).isEqualTo("handbook.pdf");
        assertThat(response.getDownloadUrl()).isEqualTo("/api/v1/resources/40/download");
        assertThat(response.getType()).isEqualTo(ResourceType.PDF);
    }

    @Test
    void upload_asUnrelatedInstructor_isDeniedBeforeAnythingIsStored() throws Exception {
        when(videoLessonRepository.findById(10L)).thenReturn(Optional.of(lessonFor(userWithId(1L), true)));

        assertThatThrownBy(() -> resourceService.upload(10L, "Highway Code", PDF, 999L, "INSTRUCTOR"))
                .isInstanceOf(ForbiddenException.class);
        verify(storageService, never()).store(any(), any(), any());
    }

    @Test
    void upload_fileRejectedByTheValidator_isABadRequest() throws Exception {
        when(videoLessonRepository.findById(10L)).thenReturn(Optional.of(lessonFor(userWithId(1L), true)));
        when(storageService.store(PDF, "course-resources", "10")).thenThrow(new IllegalArgumentException("Only PDF files are allowed"));

        assertThatThrownBy(() -> resourceService.upload(10L, "Highway Code", PDF, 1L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only PDF");
        verify(resourceRepository, never()).save(any());
    }

    @Test
    void upload_blankTitle_isRejected() {
        when(videoLessonRepository.findById(10L)).thenReturn(Optional.of(lessonFor(userWithId(1L), true)));

        assertThatThrownBy(() -> resourceService.upload(10L, " ", PDF, 1L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rename_asOwningInstructor_changesTheTitle() {
        Resource resource = uploadedResourceFor(lessonFor(userWithId(1L), true));
        when(resourceRepository.findById(31L)).thenReturn(Optional.of(resource));

        ResourceResponse response = resourceService.rename(31L,
                com.drivingschool.backend.learning.dto.UpdateResourceRequest.builder().title("Highway Code 2026").build(), 1L, "INSTRUCTOR");

        assertThat(response.getTitle()).isEqualTo("Highway Code 2026");
    }

    @Test
    void replaceFile_storesTheNewFileThenDeletesTheOldOne() throws Exception {
        Resource resource = uploadedResourceFor(lessonFor(userWithId(1L), true));
        when(resourceRepository.findById(31L)).thenReturn(Optional.of(resource));
        when(storageService.store(PDF, "course-resources", "10")).thenReturn(stored("course-resources/10/new.pdf"));

        resourceService.replaceFile(31L, PDF, 1L, "INSTRUCTOR");

        assertThat(resource.getStoragePath()).isEqualTo("course-resources/10/new.pdf");
        verify(storageService).delete("course-resources/10/old.pdf");
    }

    @Test
    void replaceFile_insideATransaction_deletesTheOldFileOnlyAfterCommit() throws Exception {
        Resource resource = uploadedResourceFor(lessonFor(userWithId(1L), true));
        when(resourceRepository.findById(31L)).thenReturn(Optional.of(resource));
        when(storageService.store(PDF, "course-resources", "10")).thenReturn(stored("course-resources/10/new.pdf"));

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            resourceService.replaceFile(31L, PDF, 1L, "INSTRUCTOR");
            verify(storageService, never()).delete(any());
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(storageService).delete("course-resources/10/old.pdf");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void replaceFile_onALinkResource_isRejected() {
        Resource link = resourceFor(lessonFor(userWithId(1L), true));
        when(resourceRepository.findById(30L)).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> resourceService.replaceFile(30L, PDF, 1L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("external link");
    }

    @Test
    void delete_uploadedResource_alsoDeletesTheStoredFile() throws Exception {
        Resource resource = uploadedResourceFor(lessonFor(userWithId(1L), true));
        when(resourceRepository.findById(31L)).thenReturn(Optional.of(resource));

        resourceService.delete(31L, 1L, "INSTRUCTOR");

        verify(resourceRepository).delete(resource);
        verify(storageService).delete("course-resources/10/old.pdf");
    }

    @Test
    void download_publishedLesson_openToAStudentOfTheSchool() throws Exception {
        Resource resource = uploadedResourceFor(lessonFor(userWithId(1L), true));
        org.springframework.core.io.Resource content = new org.springframework.core.io.ByteArrayResource(new byte[]{1});
        when(resourceRepository.findById(31L)).thenReturn(Optional.of(resource));
        when(storageService.load("course-resources/10/old.pdf")).thenReturn(content);

        ResourceService.DownloadableFile file = resourceService.download(31L, 5L, "STUDENT");

        assertThat(file.content()).isSameAs(content);
        assertThat(file.fileName()).isEqualTo("handbook.pdf");
    }

    @Test
    void download_unpublishedLesson_deniedToStudents() {
        Resource resource = uploadedResourceFor(lessonFor(userWithId(1L), false));
        when(resourceRepository.findById(31L)).thenReturn(Optional.of(resource));

        assertThatThrownBy(() -> resourceService.download(31L, 5L, "STUDENT"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void download_anotherSchoolsResource_isDenied() {
        Resource resource = uploadedResourceFor(lessonFor(userWithId(1L), true));
        when(resourceRepository.findById(31L)).thenReturn(Optional.of(resource));
        doThrow(new BadRequestException("no access")).when(callerSchoolScope).requireSameSchool(any());

        assertThatThrownBy(() -> resourceService.download(31L, 5L, "STUDENT"))
                .isInstanceOf(BadRequestException.class);
    }
}
