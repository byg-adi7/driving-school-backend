package com.drivingschool.backend.school.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.role.repository.RoleRepository;
import com.drivingschool.backend.school.dto.CreateSchoolWithAdminRequest;
import com.drivingschool.backend.school.dto.SchoolResponse;
import com.drivingschool.backend.school.dto.SchoolWithAdminResponse;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.enums.SchoolDeletionRequestStatus;
import com.drivingschool.backend.school.mapper.SchoolMapper;
import com.drivingschool.backend.school.repository.SchoolDeletionRequestRepository;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.school.validator.SchoolAccessValidator;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.notification.dto.SendNotificationRequest;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.service.NotificationService;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class SchoolServiceImpl implements SchoolService {

    private final SchoolRepository schoolRepository;
    private final SchoolMapper schoolMapper;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final SchoolDeletionRequestRepository schoolDeletionRequestRepository;
    private final SchoolAdminCascadeDeletionService cascadeDeletionService;
    private final SchoolAccessValidator accessValidator;
    private final NotificationService notificationService;

    public SchoolServiceImpl(SchoolRepository schoolRepository,
                             SchoolMapper schoolMapper,
                             UserRepository userRepository,
                             RoleRepository roleRepository,
                             PasswordEncoder passwordEncoder,
                             CurrentUserService currentUserService,
                             SchoolDeletionRequestRepository schoolDeletionRequestRepository,
                             SchoolAdminCascadeDeletionService cascadeDeletionService,
                             SchoolAccessValidator accessValidator,
                             NotificationService notificationService) {
        this.schoolRepository = schoolRepository;
        this.schoolMapper = schoolMapper;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
        this.schoolDeletionRequestRepository = schoolDeletionRequestRepository;
        this.cascadeDeletionService = cascadeDeletionService;
        this.accessValidator = accessValidator;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    @CacheEvict(value = {"schools", "schools-active"}, allEntries = true)
    public SchoolWithAdminResponse createWithAdmin(CreateSchoolWithAdminRequest request) {
        if (!currentUserService.isBootstrapAdmin()) {
            throw new BadRequestException("Only the bootstrap admin can create a school");
        }
        if (userRepository.existsByEmail(request.getAdminEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", RoleName.ADMIN));

        User admin = User.builder()
                .email(request.getAdminEmail())
                .password(passwordEncoder.encode(request.getAdminPassword()))
                .enabled(true)
                // Verifies with a one-time code at first login, like every other created account.
                .emailVerified(false)
                .build();
        admin.addRole(adminRole);
        User savedAdmin = userRepository.save(admin);

        School school = School.builder()
                .name(request.getSchoolName())
                .address(request.getSchoolAddress())
                .phone(request.getSchoolPhone())
                .email(request.getSchoolEmail())
                .active(true)
                .owningAdmin(savedAdmin)
                .build();
        School savedSchool = schoolRepository.save(school);

        log.info("Bootstrap admin created school '{}' with owning admin {}", savedSchool.getName(), savedAdmin.getEmail());
        sendWelcomeNotification(savedAdmin);
        return SchoolWithAdminResponse.builder()
                .school(schoolMapper.toResponse(savedSchool))
                .adminUserId(savedAdmin.getId())
                .adminEmail(savedAdmin.getEmail())
                .build();
    }

    @Override
    @Cacheable(value = "schools", key = "#id + ':' + #callerId")
    @Transactional(readOnly = true)
    public SchoolResponse getById(Long id, Long callerId, String callerRole) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("School", "id", id));
        if ("ADMIN".equals(callerRole)) {
            User caller = userRepository.findById(callerId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", callerId));
            accessValidator.validateViewAccess(school, caller);
        }
        return schoolMapper.toResponse(school);
    }

    @Override
    @Cacheable(value = "schools-active", key = "#callerId")
    @Transactional(readOnly = true)
    public List<SchoolResponse> getAllActive(Long callerId, String callerRole) {
        if ("ADMIN".equals(callerRole)) {
            User caller = userRepository.findById(callerId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", callerId));
            if (!caller.isBootstrapAdmin()) {
                // Queried on School's owning FK side rather than User.ownedSchool - see
                // SchoolRepository.findByOwningAdminId for why the mappedBy side isn't used.
                return schoolRepository.findByOwningAdminId(callerId)
                        .map(owned -> List.of(schoolMapper.toResponse(owned)))
                        .orElse(List.of());
            }
        }
        return schoolRepository.findAll().stream()
                .filter(School::isActive)
                .map(schoolMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteDirectly(Long schoolId) {
        if (!currentUserService.isBootstrapAdmin()) {
            throw new BadRequestException("Only the bootstrap admin can delete a school");
        }
        if (!schoolRepository.existsById(schoolId)) {
            throw new ResourceNotFoundException("School", "id", schoolId);
        }

        // saveAndFlush, not save: the cascade below deletes the school/admin via a bulk
        // delete that executes immediately, including the DB's ON DELETE SET NULL on
        // this row's school_id - a pending (unflushed) UPDATE here would only flush
        // later and try to re-write the stale pre-cascade school_id, failing the FK
        // constraint against a school that by then no longer exists.
        schoolDeletionRequestRepository.findBySchoolIdAndStatus(schoolId, SchoolDeletionRequestStatus.PENDING)
                .ifPresent(pending -> {
                    pending.approve(userRepository.getReferenceById(currentUserService.requireUserId()),
                            "Auto-resolved: bootstrap admin deleted the school directly");
                    schoolDeletionRequestRepository.saveAndFlush(pending);
                });

        cascadeDeletionService.execute(schoolId);
    }

    private void sendWelcomeNotification(User user) {
        notificationService.sendAfterCommit(SendNotificationRequest.builder()
                .userId(user.getId())
                .subject("Welcome to Aidly!")
                .body("Your admin account is ready. We're glad to have you on board - explore around and let us know if you need anything.")
                .channel(NotificationChannel.IN_APP)
                .build());
    }
}
