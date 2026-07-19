package com.drivingschool.backend.school.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class SchoolAccessValidator {

    public void validateViewAccess(School school, User caller) {
        if (caller.isBootstrapAdmin()) {
            return;
        }
        School owned = caller.getOwnedSchool();
        if (owned == null || !owned.getId().equals(school.getId())) {
            throw new BadRequestException("You do not have access to this school");
        }
    }
}
