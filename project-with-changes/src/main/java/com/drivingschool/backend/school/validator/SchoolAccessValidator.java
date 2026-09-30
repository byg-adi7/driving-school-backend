package com.drivingschool.backend.school.validator;

import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class SchoolAccessValidator {

    private final SchoolRepository schoolRepository;

    public SchoolAccessValidator(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }

    public void validateViewAccess(School school, User caller) {
        if (caller.isBootstrapAdmin()) {
            return;
        }
        // Queried on School's owning FK side rather than User.ownedSchool - see
        // SchoolRepository.findByOwningAdminId for why the mappedBy side isn't used.
        School owned = schoolRepository.findByOwningAdminId(caller.getId()).orElse(null);
        if (owned == null || !owned.getId().equals(school.getId())) {
            throw new ForbiddenException("You do not have access to this school");
        }
    }
}
