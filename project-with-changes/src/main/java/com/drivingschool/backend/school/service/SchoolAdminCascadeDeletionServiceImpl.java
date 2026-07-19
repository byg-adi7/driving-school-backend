package com.drivingschool.backend.school.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SchoolAdminCascadeDeletionServiceImpl implements SchoolAdminCascadeDeletionService {

    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InstructorProfileRepository instructorProfileRepository;

    public SchoolAdminCascadeDeletionServiceImpl(SchoolRepository schoolRepository,
                                                 UserRepository userRepository,
                                                 StudentProfileRepository studentProfileRepository,
                                                 InstructorProfileRepository instructorProfileRepository) {
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.instructorProfileRepository = instructorProfileRepository;
    }

    @Override
    @Transactional
    @CacheEvict(value = "schools", allEntries = true)
    public void execute(Long schoolId) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School", "id", schoolId));
        User owningAdmin = school.getOwningAdmin();

        if (owningAdmin.isBootstrapAdmin()) {
            // Structurally shouldn't happen (bootstrap never owns a school) -
            // kept as a defensive last line of defense.
            throw new BadRequestException("The bootstrap admin cannot be deleted");
        }

        // Hard-delete active (non-soft-deleted) students'/instructors' own User
        // rows first. Without this, cascading only via school_id would leave an
        // orphaned-but-enabled User row with no profile once the school is gone.
        // Already-soft-deleted accounts are left alone - already inert, and any
        // leftover profile rows are swept up by the school_id CASCADE regardless.
        var studentUserIds = studentProfileRepository.findBySchoolIdExcludingDeletedUsers(schoolId).stream()
                .map(sp -> sp.getUser().getId())
                .toList();
        var instructorUserIds = instructorProfileRepository.findBySchoolIdExcludingDeletedUsers(schoolId).stream()
                .map(ip -> ip.getUser().getId())
                .toList();

        if (!studentUserIds.isEmpty()) {
            userRepository.deleteAllByIdInBatch(studentUserIds);
        }
        if (!instructorUserIds.isEmpty()) {
            userRepository.deleteAllByIdInBatch(instructorUserIds);
        }

        // Deleting the owning admin's own User row cascades - via
        // schools.owning_admin_id ON DELETE CASCADE - straight through to the
        // school row, and from there through any remaining profiles/bookings/
        // gamification rows via the V15 cascade fixes. No other repository
        // needs to be touched explicitly for this part.
        Long adminUserId = owningAdmin.getId();
        userRepository.delete(owningAdmin);
        log.info("Cascade-deleted school id={} and its owning admin (userId={})", schoolId, adminUserId);
    }
}
