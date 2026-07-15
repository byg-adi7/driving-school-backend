package com.drivingschool.backend.user.service;

public interface UserService {

    /**
     * Hides the account and blocks login, without touching any row that
     * references it - see {@link com.drivingschool.backend.user.entity.User#softDelete()}.
     */
    void softDelete(Long userId);
}
