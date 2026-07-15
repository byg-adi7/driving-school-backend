package com.drivingschool.backend.lesson.note.service;

import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.lesson.note.dto.CreateLessonNoteRequest;
import com.drivingschool.backend.lesson.note.dto.LessonNoteResponse;
import com.drivingschool.backend.lesson.note.dto.UpdateLessonNoteRequest;
import com.drivingschool.backend.lesson.note.entity.LessonNote;
import com.drivingschool.backend.lesson.note.repository.LessonNoteRepository;
import com.drivingschool.backend.lesson.note.validator.LessonNoteValidator;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LessonNoteService {

    private final LessonNoteRepository lessonNoteRepository;
    private final UserRepository userRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LessonNoteValidator validator;

    @Transactional
    public LessonNoteResponse createLessonNote(CreateLessonNoteRequest request, Long instructorId) {
        validator.validateCreateRequest(request);

        var instructor = instructorProfileRepository.findByUserId(instructorId)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor profile not found for user ID: " + instructorId));

        var student = studentProfileRepository.findByUserId(request.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found for user ID: " + request.getStudentId()));

        LessonNote note = LessonNote.builder()
                .liveSessionId(request.getLiveSessionId())
                .instructor(instructor)
                .student(student)
                .lessonSummary(request.getLessonSummary())
                .strengths(request.getStrengths())
                .weaknesses(request.getWeaknesses())
                .recommendations(request.getRecommendations())
                .build();

        LessonNote savedNote = lessonNoteRepository.save(note);
        return mapToResponse(savedNote);
    }

    @Transactional
    public LessonNoteResponse updateLessonNote(Long noteId, UpdateLessonNoteRequest request, Long instructorId) {
        LessonNote note = lessonNoteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson note not found with ID: " + noteId));

        validator.validateOwnership(note, instructorId);

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

        User updatedBy = userRepository.findById(instructorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + instructorId));
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
        StudentProfile student = resolveStudentProfile(studentId);

        boolean hasTaughtStudent = "INSTRUCTOR".equals(role) && instructorProfileRepository.findByUserId(currentUserId)
                .map(instructor -> lessonNoteRepository.existsByStudent_IdAndInstructor_Id(student.getId(), instructor.getId()))
                .orElse(false);
        validator.validateStudentNotesAccess(student, currentUserId, role, hasTaughtStudent);

        Page<LessonNote> notes = lessonNoteRepository.findByStudentId(student.getId(), pageable);
        return notes.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<LessonNoteResponse> getInstructorNotes(Long instructorId, Pageable pageable, Long currentUserId, String role) {
        InstructorProfile instructor = resolveInstructorProfile(instructorId);
        validator.validateInstructorNotesAccess(instructor, currentUserId, role);

        Page<LessonNote> notes = lessonNoteRepository.findByInstructorId(instructor.getId(), pageable);
        return notes.map(this::mapToResponse);
    }

    // studentId may be a userId or a student profile id, matching how the endpoint was already documented/used.
    private StudentProfile resolveStudentProfile(Long studentId) {
        return studentProfileRepository.findByUserId(studentId)
                .or(() -> studentProfileRepository.findById(studentId))
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found: " + studentId));
    }

    // instructorId may be a userId or an instructor profile id, matching how the endpoint was already documented/used.
    private InstructorProfile resolveInstructorProfile(Long instructorId) {
        return instructorProfileRepository.findByUserId(instructorId)
                .or(() -> instructorProfileRepository.findById(instructorId))
                .orElseThrow(() -> new ResourceNotFoundException("Instructor profile not found: " + instructorId));
    }

    @Transactional(readOnly = true)
    public Page<LessonNoteResponse> getAllNotes(Pageable pageable) {
        Page<LessonNote> notes = lessonNoteRepository.findAllNotes(pageable);
        return notes.map(this::mapToResponse);
    }

    @Transactional
    public void deleteLessonNote(Long noteId, Long instructorId) {
        LessonNote note = lessonNoteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson note not found with ID: " + noteId));

        validator.validateOwnership(note, instructorId);
        lessonNoteRepository.delete(note);
    }

    private LessonNoteResponse mapToResponse(LessonNote note) {
        return LessonNoteResponse.builder()
                .id(note.getId())
                .liveSessionId(note.getLiveSessionId())
                .instructorId(note.getInstructor().getUser().getId())
                .instructorName(note.getInstructor().getUser().getDisplayName())
                .studentId(note.getStudent().getUser().getId())
                .studentName(note.getStudent().getUser().getDisplayName())
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
