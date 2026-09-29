package com.drivingschool.backend.lesson.note.service;

import com.drivingschool.backend.booking.entity.Booking;
import com.drivingschool.backend.booking.repository.BookingRepository;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.lesson.note.dto.CreateLessonNoteRequest;
import com.drivingschool.backend.lesson.note.dto.LessonNoteResponse;
import com.drivingschool.backend.lesson.note.dto.UpdateLessonNoteRequest;
import com.drivingschool.backend.lesson.note.entity.LessonNote;
import com.drivingschool.backend.lesson.note.repository.LessonNoteRepository;
import com.drivingschool.backend.lesson.note.validator.LessonNoteValidator;
import com.drivingschool.backend.notification.dto.SendNotificationRequest;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.service.NotificationService;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LessonNoteService {

    private final LessonNoteRepository lessonNoteRepository;
    private final UserRepository userRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final BookingRepository bookingRepository;
    private final LessonNoteValidator validator;
    private final NotificationService notificationService;

    @Transactional
    public LessonNoteResponse createLessonNote(CreateLessonNoteRequest request, Long callerId) {
        validator.validateCreateRequest(request);

        var instructor = instructorProfileRepository.findByUserId(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor profile not found for user ID: " + callerId));

        var student = studentProfileRepository.findById(request.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("StudentProfile", "id", request.getStudentId()));
        // Writing a note is also what grants an instructor read access to the student's
        // whole note history (see LessonNoteValidator.validateStudentNotesAccess), so this
        // must never be possible across schools.
        if (!student.getSchool().getId().equals(instructor.getSchool().getId())) {
            throw new BadRequestException("You can only write lesson notes for students in your own school");
        }

        Booking booking = null;
        if (request.getBookingId() != null) {
            booking = bookingRepository.findById(request.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", request.getBookingId()));
            if (!booking.getInstructor().getId().equals(instructor.getId())
                    || !booking.getStudent().getId().equals(student.getId())) {
                throw new BadRequestException("Booking does not belong to this instructor and student");
            }
        }

        LessonNote note = LessonNote.builder()
                .booking(booking)
                .instructor(instructor)
                .student(student)
                .lessonSummary(request.getLessonSummary())
                .strengths(request.getStrengths())
                .weaknesses(request.getWeaknesses())
                .recommendations(request.getRecommendations())
                .build();

        LessonNote savedNote = lessonNoteRepository.save(note);
        notifyStudentOfNote(savedNote);
        return mapToResponse(savedNote);
    }

    private void notifyStudentOfNote(LessonNote note) {
        try {
            SendNotificationRequest request = SendNotificationRequest.builder()
                    .userId(note.getStudent().getUser().getId())
                    .subject("New lesson note from your instructor")
                    .body("%s %s added a note about your lesson: %s"
                            .formatted(note.getInstructor().getFirstName(), note.getInstructor().getLastName(),
                                    note.getLessonSummary()))
                    .channel(NotificationChannel.IN_APP)
                    .build();
            notificationService.send(request);
        } catch (Exception ex) {
            log.warn("Failed to send lesson note notification: noteId={}", note.getId(), ex);
        }
    }

    @Transactional
    public LessonNoteResponse updateLessonNote(Long noteId, UpdateLessonNoteRequest request, Long callerId) {
        LessonNote note = lessonNoteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson note not found with ID: " + noteId));

        validator.validateOwnership(note, callerId);

        if (request.getLessonSummary() != null) {
            note.setLessonSummary(request.getLessonSummary());
        }
        if (request.getStrengths() != null) {
            note.setStrengths(request.getStrengths());
        }
        if (request.getWeaknesses() != null) {
            note.setWeaknesses(request.getWeaknesses());
        }
        if (request.getRecommendations() != null) {
            note.setRecommendations(request.getRecommendations());
        }

        User updatedBy = userRepository.findById(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + callerId));
        note.setUpdatedBy(updatedBy);

        LessonNote updatedNote = lessonNoteRepository.save(note);
        return mapToResponse(updatedNote);
    }

    @Transactional(readOnly = true)
    public LessonNoteResponse getLessonNote(Long noteId, Long userId, String role) {
        LessonNote note = lessonNoteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson note not found with ID: " + noteId));

        validator.validateReadAccess(note, userId, role);
        return mapToResponse(note);
    }

    @Transactional(readOnly = true)
    public Page<LessonNoteResponse> getStudentNotes(Long studentId, Pageable pageable, Long currentUserId, String role) {
        StudentProfile student = studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentProfile", "id", studentId));

        boolean hasTaughtStudent = "INSTRUCTOR".equals(role) && instructorProfileRepository.findByUserId(currentUserId)
                .map(instructor -> lessonNoteRepository.existsByStudent_IdAndInstructor_Id(student.getId(), instructor.getId()))
                .orElse(false);
        validator.validateStudentNotesAccess(student, currentUserId, role, hasTaughtStudent);

        Page<LessonNote> notes = lessonNoteRepository.findByStudentId(student.getId(), pageable);
        return notes.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<LessonNoteResponse> getInstructorNotes(Long instructorId, Pageable pageable, Long currentUserId, String role) {
        InstructorProfile instructor = instructorProfileRepository.findById(instructorId)
                .orElseThrow(() -> new ResourceNotFoundException("InstructorProfile", "id", instructorId));
        validator.validateInstructorNotesAccess(instructor, currentUserId, role);

        Page<LessonNote> notes = lessonNoteRepository.findByInstructorId(instructor.getId(), pageable);
        return notes.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<LessonNoteResponse> getAllNotes(Pageable pageable) {
        Page<LessonNote> notes = validator.adminSchoolFilter()
                .map(schoolId -> lessonNoteRepository.findAllNotesBySchoolId(schoolId, pageable))
                .orElseGet(() -> lessonNoteRepository.findAllNotes(pageable));
        return notes.map(this::mapToResponse);
    }

    @Transactional
    public void deleteLessonNote(Long noteId, Long callerId) {
        LessonNote note = lessonNoteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson note not found with ID: " + noteId));

        validator.validateOwnership(note, callerId);
        lessonNoteRepository.delete(note);
    }

    private LessonNoteResponse mapToResponse(LessonNote note) {
        return LessonNoteResponse.builder()
                .id(note.getId())
                .bookingId(note.getBooking() != null ? note.getBooking().getId() : null)
                .instructorId(note.getInstructor().getId())
                .instructorName(note.getInstructor().getFirstName() + " " + note.getInstructor().getLastName())
                .studentId(note.getStudent().getId())
                .studentName(note.getStudent().getFirstName() + " " + note.getStudent().getLastName())
                .lessonSummary(note.getLessonSummary())
                .strengths(note.getStrengths())
                .weaknesses(note.getWeaknesses())
                .recommendations(note.getRecommendations())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .updatedById(note.getUpdatedBy() != null ? note.getUpdatedBy().getId() : null)
                .updatedByName(note.getUpdatedBy() != null ? note.getUpdatedBy().getDisplayName() : null)
                .build();
    }
}
