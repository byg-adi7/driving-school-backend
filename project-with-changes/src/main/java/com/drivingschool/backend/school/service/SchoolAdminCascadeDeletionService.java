package com.drivingschool.backend.school.service;

public interface SchoolAdminCascadeDeletionService {

    /**
     * Permanently deletes a school, its owning admin's account, and every
     * student/instructor/booking/etc. tied to it. The only convergence point
     * for all school+admin deletion entry points (bootstrap direct delete,
     * bootstrap deleting an admin, or an approved deletion request).
     */
    void execute(Long schoolId);
}
