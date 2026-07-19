package com.drivingschool.backend.user.service;

public interface UserService {

    /**
     * Hides the account and blocks login, without touching any row that
     * references it - see {@link com.drivingschool.backend.user.entity.User#softDelete()}.
     * Used directly for STUDENT/INSTRUCTOR targets; ADMIN targets must go
     * through {@link #deleteUserAccount(Long, Long)} instead.
     */
    void softDelete(Long userId);

    /**
     * Role-aware entry point for {@code DELETE /api/v1/users/{id}}. Delegates to
     * {@link #softDelete(Long)} unchanged for non-admin targets; for an admin
     * target, rejects the bootstrap admin outright, requires the caller to be
     * the bootstrap admin, and cascade-deletes the target's owned school (and
     * the target's own account) together via SchoolAdminCascadeDeletionService.
     */
    void deleteUserAccount(Long targetUserId, Long callerId);
}
