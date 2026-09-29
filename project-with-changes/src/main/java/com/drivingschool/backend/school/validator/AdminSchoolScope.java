package com.drivingschool.backend.school.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Confines a regular (non-bootstrap) ADMIN to the one school they own.
 *
 * Before the school/admin ownership model, ADMIN meant "unrestricted" and every
 * module's access checks short-circuited on it. Ownership was only ever enforced
 * inside the school module itself, so a regular admin could still read and mutate
 * another school's students, instructors, vehicles, bookings, lesson notes, etc.
 * simply by passing that school's IDs. Every such ADMIN short-circuit now goes
 * through here instead.
 *
 * Deliberately a no-op for any caller who isn't an ADMIN: those roles keep their
 * existing per-module ownership rules, and some shared service methods (e.g.
 * LicenseWorkflowService.markQuizPassed via QuizService.submit) also run in a
 * STUDENT's request context, where there's no owned school to compare against.
 */
@Component
public class AdminSchoolScope {

    private final CurrentUserService currentUserService;
    private final SchoolRepository schoolRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InstructorProfileRepository instructorProfileRepository;

    public AdminSchoolScope(CurrentUserService currentUserService,
                            SchoolRepository schoolRepository,
                            StudentProfileRepository studentProfileRepository,
                            InstructorProfileRepository instructorProfileRepository) {
        this.currentUserService = currentUserService;
        this.schoolRepository = schoolRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.instructorProfileRepository = instructorProfileRepository;
    }

    /**
     * @return true unless the caller is a regular ADMIN and schoolId isn't the
     *         school they own (a null schoolId never matches a regular admin).
     */
    public boolean canAccess(Long schoolId) {
        return restrictedSchoolId()
                .map(owned -> owned.equals(schoolId))
                .orElse(true);
    }

    public void requireAccess(Long schoolId) {
        if (!canAccess(schoolId)) {
            throw new BadRequestException("You do not have access to this school's records");
        }
    }

    /**
     * Same as requireAccess, for a target identified by User.id - resolved to the
     * school of their student profile, instructor profile, or owned school.
     */
    public void requireAccessToUser(Long userId) {
        requireAccess(schoolIdOfUser(userId));
    }

    /**
     * @return the school a regular ADMIN caller is confined to, or empty when the
     *         caller is unrestricted by this class (bootstrap admin, or not an ADMIN
     *         at all). Listing endpoints use this to filter instead of rejecting.
     */
    public Optional<Long> restrictedSchoolId() {
        if (!currentUserService.hasRole(RoleName.ADMIN) || currentUserService.isBootstrapAdmin()) {
            return Optional.empty();
        }
        // Queried on School's owning FK side rather than User.ownedSchool - see
        // SchoolRepository.findByOwningAdminId for why the mappedBy side isn't used.
        // Every non-bootstrap admin is created together with their school (and
        // deleted with it), so a missing school here is a data-integrity problem,
        // not a case to fall back to unrestricted access for.
        Long callerId = currentUserService.requireUserId();
        return Optional.of(schoolRepository.findByOwningAdminId(callerId)
                .map(School::getId)
                .orElseThrow(() -> new BadRequestException("You do not own a school")));
    }

    private Long schoolIdOfUser(Long userId) {
        return studentProfileRepository.findByUserId(userId)
                .map(profile -> profile.getSchool().getId())
                .or(() -> instructorProfileRepository.findByUserId(userId)
                        .map(profile -> profile.getSchool().getId()))
                .or(() -> schoolRepository.findByOwningAdminId(userId).map(School::getId))
                .orElse(null);
    }
}
