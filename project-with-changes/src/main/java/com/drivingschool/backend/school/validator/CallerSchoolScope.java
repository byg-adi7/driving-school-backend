package com.drivingschool.backend.school.validator;

import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Confines ANY caller - not just an ADMIN - to their own school: a regular admin
 * to the school they own (via AdminSchoolScope), a student or instructor to their
 * profile's school. Only the bootstrap admin is unrestricted.
 *
 * For the places where a role's existing rule was "any staff member may act" with
 * no ownership relationship to scope against (e.g. any instructor may advance any
 * student's license workflow), which silently also meant "in any school".
 */
@Component
public class CallerSchoolScope {

    private final CurrentUserService currentUserService;
    private final AdminSchoolScope adminSchoolScope;
    private final StudentProfileRepository studentProfileRepository;
    private final InstructorProfileRepository instructorProfileRepository;

    public CallerSchoolScope(CurrentUserService currentUserService,
                             AdminSchoolScope adminSchoolScope,
                             StudentProfileRepository studentProfileRepository,
                             InstructorProfileRepository instructorProfileRepository) {
        this.currentUserService = currentUserService;
        this.adminSchoolScope = adminSchoolScope;
        this.studentProfileRepository = studentProfileRepository;
        this.instructorProfileRepository = instructorProfileRepository;
    }

    /**
     * @return the school the caller is confined to, or empty for the bootstrap admin.
     */
    public Optional<Long> callerSchoolId() {
        if (currentUserService.hasRole(RoleName.ADMIN)) {
            return adminSchoolScope.restrictedSchoolId();
        }
        Long userId = currentUserService.requireUserId();
        return Optional.of(studentProfileRepository.findByUserId(userId)
                .map(profile -> profile.getSchool().getId())
                .or(() -> instructorProfileRepository.findByUserId(userId)
                        .map(profile -> profile.getSchool().getId()))
                .orElseThrow(() -> new ForbiddenException("You do not belong to a school")));
    }

    public void requireSameSchool(Long schoolId) {
        callerSchoolId().ifPresent(own -> {
            if (!own.equals(schoolId)) {
                throw new ForbiddenException("You do not have access to this school's records");
            }
        });
    }

    /** Same as requireSameSchool, for a target identified by User.id. */
    public void requireSameSchoolAsUser(Long userId) {
        requireSameSchool(adminSchoolScope.schoolIdOfUser(userId));
    }
}
