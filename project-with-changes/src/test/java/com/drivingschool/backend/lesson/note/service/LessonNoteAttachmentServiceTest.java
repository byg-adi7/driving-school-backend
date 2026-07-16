package com.drivingschool.backend.lesson.note.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.lesson.note.entity.LessonNote;
import com.drivingschool.backend.lesson.note.entity.LessonNoteAttachment;
import com.drivingschool.backend.lesson.note.repository.LessonNoteAttachmentRepository;
import com.drivingschool.backend.lesson.note.repository.LessonNoteRepository;
import com.drivingschool.backend.lesson.note.validator.LessonNoteValidator;
import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.security.UserPrincipal;
import com.drivingschool.backend.storage.StorageService;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonNoteAttachmentServiceTest {

    @Mock private LessonNoteAttachmentRepository attachmentRepository;
    @Mock private LessonNoteRepository lessonNoteRepository;
    @Mock private UserRepository userRepository;
    @Mock private StorageService storageService;

    private LessonNoteAttachmentService attachmentService;

    @BeforeEach
    void setUp() {
        attachmentService = new LessonNoteAttachmentService(attachmentRepository, lessonNoteRepository,
                userRepository, storageService, new LessonNoteValidator());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private User userWithId(RoleName role, Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        user.addRole(Role.builder().name(role).build());
        return user;
    }

    private void authenticateAs(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private LessonNote noteAuthoredBy(User instructorUser, User studentUser) {
        InstructorProfile instructor = InstructorProfile.builder().user(instructorUser).active(true).school(School.builder().active(true).build()).build();
        StudentProfile student = StudentProfile.builder().user(studentUser).school(School.builder().active(true).build()).build();
        LessonNote note = LessonNote.builder().instructor(instructor).student(student).build();
        ReflectionTestUtils.setField(note, "id", 100L);
        return note;
    }

    private LessonNoteAttachment attachmentOf(LessonNote note, User uploader) {
        LessonNoteAttachment attachment = LessonNoteAttachment.builder()
                .lessonNote(note)
                .fileName("report.pdf")
                .fileType("application/pdf")
                .filePath("lesson-notes/2026/01/01/x.pdf")
                .uploadedBy(uploader)
                .isActive(true)
                .build();
        ReflectionTestUtils.setField(attachment, "id", 200L);
        return attachment;
    }

    // --- downloadAttachment: verifies the InstructorProfile-vs-User ID bug fix ---

    @Test
    void downloadAttachment_asAuthoringInstructor_isAllowed() throws Exception {
        User instructorUser = userWithId(RoleName.INSTRUCTOR, 1L);
        User studentUser = userWithId(RoleName.STUDENT, 2L);
        LessonNote note = noteAuthoredBy(instructorUser, studentUser);
        LessonNoteAttachment attachment = attachmentOf(note, instructorUser);

        authenticateAs(instructorUser);
        when(attachmentRepository.findByIdWithLessonNote(200L)).thenReturn(Optional.of(attachment));

        assertThatCode(() -> attachmentService.downloadAttachment(100L, 200L)).doesNotThrowAnyException();
    }

    @Test
    void downloadAttachment_asTheNoteStudent_isAllowed() throws Exception {
        User instructorUser = userWithId(RoleName.INSTRUCTOR, 1L);
        User studentUser = userWithId(RoleName.STUDENT, 2L);
        LessonNote note = noteAuthoredBy(instructorUser, studentUser);
        LessonNoteAttachment attachment = attachmentOf(note, instructorUser);

        authenticateAs(studentUser);
        when(attachmentRepository.findByIdWithLessonNote(200L)).thenReturn(Optional.of(attachment));

        assertThatCode(() -> attachmentService.downloadAttachment(100L, 200L)).doesNotThrowAnyException();
    }

    @Test
    void downloadAttachment_asUnrelatedInstructor_isDenied() {
        User instructorUser = userWithId(RoleName.INSTRUCTOR, 1L);
        User studentUser = userWithId(RoleName.STUDENT, 2L);
        User unrelatedInstructor = userWithId(RoleName.INSTRUCTOR, 999L);
        LessonNote note = noteAuthoredBy(instructorUser, studentUser);
        LessonNoteAttachment attachment = attachmentOf(note, instructorUser);

        authenticateAs(unrelatedInstructor);
        when(attachmentRepository.findByIdWithLessonNote(200L)).thenReturn(Optional.of(attachment));

        assertThatThrownBy(() -> attachmentService.downloadAttachment(100L, 200L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void downloadAttachment_asUnrelatedStudent_isDenied() {
        User instructorUser = userWithId(RoleName.INSTRUCTOR, 1L);
        User studentUser = userWithId(RoleName.STUDENT, 2L);
        User unrelatedStudent = userWithId(RoleName.STUDENT, 888L);
        LessonNote note = noteAuthoredBy(instructorUser, studentUser);
        LessonNoteAttachment attachment = attachmentOf(note, instructorUser);

        authenticateAs(unrelatedStudent);
        when(attachmentRepository.findByIdWithLessonNote(200L)).thenReturn(Optional.of(attachment));

        assertThatThrownBy(() -> attachmentService.downloadAttachment(100L, 200L))
                .isInstanceOf(BadRequestException.class);
    }

    // --- uploadAttachment: verifies the InstructorProfile-vs-User ID bug fix ---

    @Test
    void uploadAttachment_asUnrelatedInstructor_isDenied() {
        User instructorUser = userWithId(RoleName.INSTRUCTOR, 1L);
        User studentUser = userWithId(RoleName.STUDENT, 2L);
        User unrelatedInstructor = userWithId(RoleName.INSTRUCTOR, 999L);
        LessonNote note = noteAuthoredBy(instructorUser, studentUser);

        authenticateAs(unrelatedInstructor);
        when(lessonNoteRepository.findById(100L)).thenReturn(Optional.of(note));
        when(userRepository.findById(999L)).thenReturn(Optional.of(unrelatedInstructor));

        assertThatThrownBy(() -> attachmentService.uploadAttachment(100L, null, null))
                .isInstanceOf(BadRequestException.class);
    }

    // --- getAttachments / getAttachmentsPaginated: previously had zero access control ---

    @Test
    void getAttachments_asUnrelatedStudent_isDenied() {
        User instructorUser = userWithId(RoleName.INSTRUCTOR, 1L);
        User studentUser = userWithId(RoleName.STUDENT, 2L);
        User unrelatedStudent = userWithId(RoleName.STUDENT, 888L);
        LessonNote note = noteAuthoredBy(instructorUser, studentUser);

        authenticateAs(unrelatedStudent);
        when(lessonNoteRepository.findById(100L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> attachmentService.getAttachments(100L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getAttachments_asTheNoteStudent_isAllowed() {
        User instructorUser = userWithId(RoleName.INSTRUCTOR, 1L);
        User studentUser = userWithId(RoleName.STUDENT, 2L);
        LessonNote note = noteAuthoredBy(instructorUser, studentUser);

        authenticateAs(studentUser);
        when(lessonNoteRepository.findById(100L)).thenReturn(Optional.of(note));
        when(attachmentRepository.findActiveByLessonNoteId(100L)).thenReturn(java.util.List.of());

        assertThatCode(() -> attachmentService.getAttachments(100L)).doesNotThrowAnyException();
    }
}
