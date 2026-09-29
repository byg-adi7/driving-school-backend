package com.drivingschool.backend.user.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.enums.SchoolDeletionRequestStatus;
import com.drivingschool.backend.school.repository.SchoolDeletionRequestRepository;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.school.service.SchoolAdminCascadeDeletionService;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final SchoolDeletionRequestRepository schoolDeletionRequestRepository;
    private final SchoolAdminCascadeDeletionService cascadeDeletionService;
    private final AdminSchoolScope adminSchoolScope;

    public UserServiceImpl(UserRepository userRepository,
                           SchoolRepository schoolRepository,
                           SchoolDeletionRequestRepository schoolDeletionRequestRepository,
                           SchoolAdminCascadeDeletionService cascadeDeletionService,
                           AdminSchoolScope adminSchoolScope) {
        this.userRepository = userRepository;
        this.schoolRepository = schoolRepository;
        this.schoolDeletionRequestRepository = schoolDeletionRequestRepository;
        this.cascadeDeletionService = cascadeDeletionService;
        this.adminSchoolScope = adminSchoolScope;
    }

    @Override
    @Transactional
    public void softDelete(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (user.isDeleted()) {
            throw new BadRequestException("Account is already deleted");
        }

        user.softDelete();
        userRepository.save(user);
        log.info("Soft-deleted user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void deleteUserAccount(Long targetUserId, Long callerId) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", targetUserId));

        boolean targetIsAdmin = target.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMIN);
        if (!targetIsAdmin) {
            // A regular admin may only remove accounts belonging to their own school.
            adminSchoolScope.requireAccessToUser(targetUserId);
            softDelete(targetUserId);
            return;
        }

        if (target.isBootstrapAdmin()) {
            throw new BadRequestException("The bootstrap admin account cannot be deleted");
        }

        User caller = userRepository.findById(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", callerId));
        if (!caller.isBootstrapAdmin()) {
            throw new BadRequestException("Only the bootstrap admin can delete another admin's account");
        }

        // Queried on School's owning FK side rather than User.ownedSchool - see
        // SchoolRepository.findByOwningAdminId for why the mappedBy side isn't used.
        School owned = schoolRepository.findByOwningAdminId(targetUserId)
                .orElseThrow(() -> new IllegalStateException(
                        "Admin " + target.getEmail() + " has no owned school - data integrity violation"));

        // saveAndFlush, not save: the cascade below deletes the school/admin via a bulk
        // delete that executes immediately, including the DB's ON DELETE SET NULL on
        // this row's school_id - a pending (unflushed) UPDATE here would only flush
        // later and try to re-write the stale pre-cascade school_id, failing the FK
        // constraint against a school that by then no longer exists.
        schoolDeletionRequestRepository.findBySchoolIdAndStatus(owned.getId(), SchoolDeletionRequestStatus.PENDING)
                .ifPresent(pending -> {
                    pending.approve(caller, "Auto-resolved: bootstrap admin deleted the admin account directly");
                    schoolDeletionRequestRepository.saveAndFlush(pending);
                });

        cascadeDeletionService.execute(owned.getId());
        log.info("Bootstrap admin {} deleted admin account {} and owned school {}",
                caller.getEmail(), target.getEmail(), owned.getId());
    }
}
