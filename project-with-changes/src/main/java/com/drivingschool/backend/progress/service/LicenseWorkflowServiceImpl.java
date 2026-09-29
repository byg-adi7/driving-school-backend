package com.drivingschool.backend.progress.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.progress.dto.AdvanceStageRequest;
import com.drivingschool.backend.progress.dto.LicenseWorkflowResponse;
import com.drivingschool.backend.progress.entity.LicenseWorkflow;
import com.drivingschool.backend.progress.enums.LicenseStage;
import com.drivingschool.backend.progress.mapper.LicenseWorkflowMapper;
import com.drivingschool.backend.progress.repository.LicenseWorkflowRepository;
import com.drivingschool.backend.progress.validator.LicenseWorkflowValidator;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class LicenseWorkflowServiceImpl implements LicenseWorkflowService {

    private final LicenseWorkflowRepository licenseWorkflowRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final LicenseWorkflowMapper licenseWorkflowMapper;
    private final LicenseWorkflowValidator validator;

    public LicenseWorkflowServiceImpl(LicenseWorkflowRepository licenseWorkflowRepository,
                                      StudentProfileRepository studentProfileRepository,
                                      InstructorProfileRepository instructorProfileRepository,
                                      LicenseWorkflowMapper licenseWorkflowMapper,
                                      LicenseWorkflowValidator validator) {
        this.licenseWorkflowRepository = licenseWorkflowRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.licenseWorkflowMapper = licenseWorkflowMapper;
        this.validator = validator;
    }

    @Override
    @Transactional(readOnly = true)
    public LicenseWorkflowResponse getByStudentId(Long studentId, Long userId, String role) {
        LicenseWorkflow workflow = findByStudentId(studentId);
        validator.validateStudentAccess(workflow, userId, role);
        return licenseWorkflowMapper.toResponse(workflow);
    }

    @Override
    @Transactional
    public LicenseWorkflowResponse initializeForStudent(Long studentId) {
        StudentProfile student = studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentProfile", "id", studentId));
        validator.validateSchoolAccess(student);

        if (licenseWorkflowRepository.existsByStudentId(studentId)) {
            throw new BadRequestException("License workflow already exists for student");
        }

        LicenseWorkflow workflow = LicenseWorkflow.builder()
                .student(student)
                .currentStage(LicenseStage.THEORY_LEARNING)
                .theoryProgressPercent(0)
                .roadTrainingHours(0)
                .stageUpdatedAt(LocalDateTime.now())
                .build();

        LicenseWorkflow saved = licenseWorkflowRepository.save(workflow);
        log.info("Initialized license workflow for student {}", studentId);
        return licenseWorkflowMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LicenseWorkflowResponse updateTheoryProgress(Long studentId, int progressPercent, Long userId, String role) {
        LicenseWorkflow workflow = findByStudentId(studentId);
        validator.validateStudentAccess(workflow, userId, role);
        workflow.updateTheoryProgress(progressPercent);
        return licenseWorkflowMapper.toResponse(licenseWorkflowRepository.save(workflow));
    }

    @Override
    @Transactional
    public LicenseWorkflowResponse markQuizPassed(Long studentId) {
        LicenseWorkflow workflow = findByStudentId(studentId);
        validator.validateSchoolAccess(workflow.getStudent());
        workflow.markQuizPassed();
        return licenseWorkflowMapper.toResponse(licenseWorkflowRepository.save(workflow));
    }

    @Override
    @Transactional
    public LicenseWorkflowResponse advanceStage(Long studentId, AdvanceStageRequest request,
                                                Long instructorUserId) {
        LicenseWorkflow workflow = findByStudentId(studentId);
        validator.validateSchoolAccess(workflow.getStudent());
        LicenseStage targetStage = request.getTargetStage();

        if (LicenseWorkflow.isInstructorControlledStage(targetStage)) {
            InstructorProfile instructor = instructorProfileRepository.findByUserId(instructorUserId)
                    .orElseThrow(() -> new BadRequestException("Only instructors can approve this stage"));
            validateInstructorStageTransition(workflow.getCurrentStage(), targetStage);
            workflow.advanceStage(targetStage, instructor, request.getNotes());
        } else {
            validateAutomatedStageTransition(workflow.getCurrentStage(), targetStage);
            workflow.advanceStage(targetStage, null, request.getNotes());
        }

        LicenseWorkflow saved = licenseWorkflowRepository.save(workflow);
        log.info("Student {} advanced to stage {}", studentId, targetStage);
        return licenseWorkflowMapper.toResponse(saved);
    }

    private LicenseWorkflow findByStudentId(Long studentId) {
        return licenseWorkflowRepository.findByStudentId(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("LicenseWorkflow", "studentId", studentId));
    }

    private void validateInstructorStageTransition(LicenseStage current, LicenseStage target) {
        if (!LicenseWorkflow.isInstructorControlledStage(target)) {
            throw new BadRequestException("Stage " + target + " requires automated progression");
        }
        if (target.ordinal() <= current.ordinal()) {
            throw new BadRequestException("Cannot regress to an earlier stage");
        }
    }

    private void validateAutomatedStageTransition(LicenseStage current, LicenseStage target) {
        if (LicenseWorkflow.isInstructorControlledStage(target)) {
            throw new BadRequestException("Stage " + target + " requires instructor approval");
        }
        if (target.ordinal() != current.ordinal() + 1) {
            throw new BadRequestException("Invalid stage transition from " + current + " to " + target);
        }
    }
}
