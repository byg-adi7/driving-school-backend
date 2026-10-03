package com.drivingschool.backend.auth.service;

import com.drivingschool.backend.auth.dto.AdminRegisterRequest;
import com.drivingschool.backend.auth.dto.AuthResponse;
import com.drivingschool.backend.auth.dto.InviteDetailsResponse;
import com.drivingschool.backend.auth.dto.AcceptInviteRequest;
import com.drivingschool.backend.auth.dto.ConfirmVerificationRequest;
import com.drivingschool.backend.auth.dto.CurrentUserResponse;
import com.drivingschool.backend.auth.dto.ForgotPasswordRequest;
import com.drivingschool.backend.auth.dto.LoginRequest;
import com.drivingschool.backend.auth.dto.RefreshTokenRequest;
import com.drivingschool.backend.auth.dto.RegisterRequest;
import com.drivingschool.backend.auth.dto.ResetPasswordRequest;
import com.drivingschool.backend.auth.dto.SendVerificationCodeRequest;
import com.drivingschool.backend.auth.entity.PasswordResetToken;
import com.drivingschool.backend.auth.mapper.AuthMapper;
import com.drivingschool.backend.auth.mapper.CurrentUserMapper;
import com.drivingschool.backend.auth.repository.PasswordResetTokenRepository;
import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.common.exception.TooManyRequestsException;
import com.drivingschool.backend.security.RateLimiter;
import com.drivingschool.backend.security.RefreshTokenRevocationService;
import com.drivingschool.backend.common.exception.AuthenticationException;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.notification.dto.SendNotificationRequest;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.service.NotificationService;
import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.role.repository.RoleRepository;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.school.service.SchoolDeletionRequestService;
import com.drivingschool.backend.security.UserPrincipal;
import com.drivingschool.backend.security.jwt.JwtTokenProvider;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.enums.StudentStatus;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.enums.AccountStatus;
import com.drivingschool.backend.user.repository.UserRepository;
import com.drivingschool.backend.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SchoolRepository schoolRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthMapper authMapper;
    private final CurrentUserMapper currentUserMapper;
    private final CurrentUserService currentUserService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final UserService userService;
    private final RefreshTokenRevocationService refreshTokenRevocationService;
    private final SchoolDeletionRequestService schoolDeletionRequestService;
    private final NotificationService notificationService;
    private final AdminSchoolScope adminSchoolScope;
    private final AccountVerificationService accountVerificationService;
    private final RateLimiter rateLimiter;
    private final InviteService inviteService;
    static final int LOGIN_ATTEMPTS_PER_ACCOUNT = 10;
    static final java.time.Duration LOGIN_WINDOW = java.time.Duration.ofMinutes(15);
    private final long passwordResetTokenExpirationMs;
    private final boolean verificationRequired;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           UserRepository userRepository,
                           RoleRepository roleRepository,
                           SchoolRepository schoolRepository,
                           StudentProfileRepository studentProfileRepository,
                           InstructorProfileRepository instructorProfileRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           AuthMapper authMapper,
                           CurrentUserMapper currentUserMapper,
                           CurrentUserService currentUserService,
                           PasswordResetTokenRepository passwordResetTokenRepository,
                           EmailService emailService,
                           UserService userService,
                           RefreshTokenRevocationService refreshTokenRevocationService,
                           SchoolDeletionRequestService schoolDeletionRequestService,
                           NotificationService notificationService,
                           AdminSchoolScope adminSchoolScope,
                           AccountVerificationService accountVerificationService,
                           RateLimiter rateLimiter,
                           InviteService inviteService,
                           @Value("${app.password-reset.token-expiration-ms}") long passwordResetTokenExpirationMs,
                           @Value("${app.verification.required:true}") boolean verificationRequired) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.schoolRepository = schoolRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authMapper = authMapper;
        this.currentUserMapper = currentUserMapper;
        this.currentUserService = currentUserService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
        this.userService = userService;
        this.refreshTokenRevocationService = refreshTokenRevocationService;
        this.schoolDeletionRequestService = schoolDeletionRequestService;
        this.notificationService = notificationService;
        this.adminSchoolScope = adminSchoolScope;
        this.accountVerificationService = accountVerificationService;
        this.rateLimiter = rateLimiter;
        this.inviteService = inviteService;
        this.passwordResetTokenExpirationMs = passwordResetTokenExpirationMs;
        this.verificationRequired = verificationRequired;
        if (!verificationRequired) {
            log.warn("Account verification is OFF (VERIFICATION_REQUIRED=false): unverified accounts log in "
                    + "without a one-time code. Turn it back on once email delivery works for real users.");
        }
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Per account, not per address: a class shares one Wi-Fi address, but guessing one
        // person's password is still capped.
        RateLimiter.RateLimitResult attempt = rateLimiter.tryConsume(
                "ratelimit:login:" + request.getEmail().trim().toLowerCase(), LOGIN_ATTEMPTS_PER_ACCOUNT, LOGIN_WINDOW);
        if (!attempt.allowed()) {
            throw new TooManyRequestsException("Too many login attempts for this account - try again in a few minutes",
                    attempt.retryAfterSeconds());
        }
        userRepository.findByEmail(request.getEmail())
                .filter(u -> u.getAccountStatus() == AccountStatus.INVITED && !u.isDeleted())
                .ifPresent(u -> {
                    throw new ForbiddenException(
                            "Your account isn't set up yet. Open the invite link in your email to choose a password.");
                });
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));

        // Right password, unverified account: no tokens until a one-time code is confirmed -
        // unless verification is switched off (e.g. before a verified email domain exists).
        if (verificationRequired && !user.isAccountVerified()) {
            log.info("Login for unverified account {} - verification required", user.getEmail());
            return AuthResponse.builder()
                    .verificationRequired(true)
                    .verification(accountVerificationService.startChallenge(user))
                    .build();
        }

        return completeLogin(user, principal, request.isRememberMe());
    }

    @Override
    public void sendVerificationCode(SendVerificationCodeRequest request) {
        accountVerificationService.sendCode(request);
    }

    @Override
    @Transactional
    public AuthResponse confirmVerification(ConfirmVerificationRequest request) {
        User user = accountVerificationService.confirm(request);
        return completeLogin(user, new UserPrincipal(user), request.isRememberMe());
    }

    @Override
    @Transactional(readOnly = true)
    public InviteDetailsResponse getInvite(String token) {
        return inviteService.details(token);
    }

    // The link proved the email, so no one-time code - straight to a normal login response.
    @Override
    @Transactional
    public AuthResponse acceptInvite(AcceptInviteRequest request) {
        User user = inviteService.accept(request.getToken(), request.getPassword());
        log.info("Invite accepted: {}", user.getEmail());
        return completeLogin(user, new UserPrincipal(user), request.isRememberMe());
    }

    private AuthResponse completeLogin(User user, UserPrincipal principal, boolean rememberMe) {
        user.recordLogin();
        userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(principal, JwtTokenProvider.Session.start(rememberMe));

        log.info("User logged in: {}", user.getEmail());
        return authMapper.toAuthResponse(user, accessToken, refreshToken);
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        validateCallerCanCreate(request);

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        School school = schoolRepository.findById(request.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException("School", "id", request.getSchoolId()));

        if (!school.isActive()) {
            throw new BadRequestException("School is not active");
        }

        Role role = roleRepository.findByName(request.getRole())
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", request.getRole()));

        validateRoleSpecificFields(request);

        boolean invite = request.getPassword() == null || request.getPassword().isBlank();
        User user = User.builder()
                .email(request.getEmail())
                .password(invite ? inviteService.unusablePassword() : passwordEncoder.encode(request.getPassword()))
                .enabled(true)
                .emailVerified(false)
                .build();
        user.addRole(role);

        User savedUser = userRepository.save(user);

        if (request.getRole() == RoleName.STUDENT) {
            createStudentProfile(request, school, savedUser);
        } else if (request.getRole() == RoleName.INSTRUCTOR) {
            createInstructorProfile(request, school, savedUser);
        }

        sendWelcomeNotification(savedUser);

        log.info("User registered: {} with role {}{}", savedUser.getEmail(), request.getRole(), invite ? " (invited)" : "");
        AuthResponse response = authMapper.toRegisteredUserResponse(savedUser);
        if (!invite) {
            return response;
        }
        User creator = userRepository.findById(currentUserService.requireUserId()).orElse(null);
        return AuthResponse.builder().user(response.getUser())
                .invite(inviteService.invite(savedUser, creator)).build();
    }

    @Override
    @Transactional
    public AuthResponse registerByAdmin(AdminRegisterRequest request) {
        if (request.getRole() == RoleName.ADMIN) {
            throw new BadRequestException(
                    "Admin accounts can no longer be created via this endpoint - use POST /api/v1/schools, " +
                            "which creates a school and its owning admin together");
        }

        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(request.getEmail())
                .password(request.getPassword())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .dateOfBirth(request.getDateOfBirth())
                .schoolId(request.getSchoolId())
                .role(request.getRole())
                .specialization(request.getSpecialization())
                .licenseNumber(request.getLicenseNumber())
                .yearsExperience(request.getYearsExperience())
                .build();

        return register(registerRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser() {
        Long userId = currentUserService.requireUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        StudentProfile student = studentProfileRepository.findByUserId(userId).orElse(null);
        InstructorProfile instructor = instructorProfileRepository.findByUserId(userId).orElse(null);
        School ownedSchool = schoolRepository.findByOwningAdminId(userId).orElse(null);

        return currentUserMapper.toResponse(user, student, instructor, ownedSchool);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new AuthenticationException("Invalid refresh token");
        }

        if (refreshTokenRevocationService.isRevoked(jwtTokenProvider.getJtiFromToken(refreshToken))) {
            throw new AuthenticationException("Refresh token has been revoked");
        }

        String email = jwtTokenProvider.getEmailFromToken(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationException("User not found"));

        // Login refuses disabled accounts, but refresh never re-checked - so a
        // soft-deleted or admin-disabled user holding a refresh token could keep
        // minting new access tokens indefinitely.
        if (!user.isEnabled() || user.isDeleted()) {
            throw new AuthenticationException("Account is disabled");
        }
        long issuedAtSeconds = jwtTokenProvider.getIssuedAtFromToken(refreshToken).getTime() / 1000;
        if (refreshTokenRevocationService.isRevokedForUser(user.getId(), issuedAtSeconds)) {
            throw new AuthenticationException("Refresh token has been revoked");
        }

        UserPrincipal principal = new UserPrincipal(user);
        String newAccessToken = jwtTokenProvider.generateAccessToken(principal);
        // Same session, new token: the session's start and "remember me" choice carry over,
        // so refreshing extends it by the idle timeout but never past its maximum length.
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(principal, jwtTokenProvider.getSessionFromToken(refreshToken));

        return authMapper.toAuthResponse(user, newAccessToken, newRefreshToken);
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        // Always behaves the same regardless of whether the email is
        // registered, so the response can never be used to enumerate
        // accounts.
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            if (user.getAccountStatus() == AccountStatus.INVITED) {
                // Never set a password yet: a fresh invite, not a reset (same reply either way).
                inviteService.reinviteQuietly(user);
                return;
            }
            String token = UUID.randomUUID().toString();
            LocalDateTime expiresAt = LocalDateTime.now().plusNanos(passwordResetTokenExpirationMs * 1_000_000L);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .token(InviteService.hash(token))
                    .expiresAt(expiresAt)
                    .build();
            passwordResetTokenRepository.save(resetToken);

            emailService.sendPasswordResetEmail(user.getEmail(), token, passwordResetTokenExpirationMs / 60_000);
            log.info("Password reset requested for user: {}", user.getEmail());
        });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(InviteService.hash(request.getToken()))
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        if (resetToken.isUsed() || resetToken.isExpired()) {
            throw new BadRequestException("Invalid or expired reset token");
        }

        User user = resetToken.getUser();
        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.markUsed();
        passwordResetTokenRepository.save(resetToken);

        // A reset is often a response to a compromised account: every session that
        // existed before it must stop being refreshable. (Already-issued access tokens
        // still run out their short lifetime - 15 minutes by default.)
        refreshTokenRevocationService.revokeAllForUser(user.getId(), jwtTokenProvider.getRefreshTokenExpirationMs());

        log.info("Password reset completed for user: {}", user.getEmail());
        sendPasswordChangedNotification(user);
    }

    @Override
    @Transactional
    public void deleteCurrentAccount() {
        Long userId = currentUserService.requireUserId();

        if (currentUserService.isBootstrapAdmin()) {
            throw new BadRequestException("The bootstrap admin account cannot be deleted");
        }
        if (currentUserService.hasRole(RoleName.ADMIN)) {
            schoolDeletionRequestService.requestOwnSchoolDeletion(userId);
            return;
        }
        userService.softDelete(userId);
    }

    @Override
    public void logout(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new AuthenticationException("Invalid refresh token");
        }

        String jti = jwtTokenProvider.getJtiFromToken(refreshToken);
        long remainingMs = jwtTokenProvider.getExpirationFromToken(refreshToken).getTime() - System.currentTimeMillis();
        refreshTokenRevocationService.revoke(jti, remainingMs);

        log.info("Refresh token revoked for user: {}", jwtTokenProvider.getEmailFromToken(refreshToken));
    }

    // Called from register(), which is only reachable via AuthController#register
    // (@PreAuthorize hasAnyRole ADMIN/INSTRUCTOR) or indirectly via registerByAdmin
    // (@PreAuthorize hasRole ADMIN) - so the caller is always one of those two roles.
    // An instructor may only create student accounts, and only within their own school;
    // a regular admin may create either, but also only within their own school.
    private void validateCallerCanCreate(RegisterRequest request) {
        if (currentUserService.hasRole(RoleName.ADMIN)) {
            adminSchoolScope.requireAccess(request.getSchoolId());
            return;
        }

        if (request.getRole() != RoleName.STUDENT) {
            throw new ForbiddenException("Instructors can only create student accounts");
        }

        Long callerId = currentUserService.requireUserId();
        InstructorProfile callerProfile = instructorProfileRepository.findByUserId(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor profile not found for user ID: " + callerId));

        if (!callerProfile.getSchool().getId().equals(request.getSchoolId())) {
            throw new ForbiddenException("You can only create students for your own school");
        }
    }

    private void validateRoleSpecificFields(RegisterRequest request) {
        if (request.getRole() == RoleName.INSTRUCTOR) {
            if (request.getLicenseNumber() == null || request.getLicenseNumber().isBlank()) {
                throw new BadRequestException("License number is required for instructor registration");
            }
        }
        if (request.getRole() == RoleName.ADMIN) {
            throw new BadRequestException("Admin registration is not allowed via public endpoint");
        }
    }

    private void createStudentProfile(RegisterRequest request, School school, User user) {
        StudentProfile profile = StudentProfile.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .dateOfBirth(request.getDateOfBirth())
                .enrollmentDate(LocalDate.now())
                .status(StudentStatus.ACTIVE)
                .school(school)
                .user(user)
                .build();
        studentProfileRepository.save(profile);
    }

    private void createInstructorProfile(RegisterRequest request, School school, User user) {
        InstructorProfile profile = InstructorProfile.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .specialization(request.getSpecialization())
                .licenseNumber(request.getLicenseNumber())
                .yearsExperience(request.getYearsExperience())
                .active(true)
                .school(school)
                .user(user)
                .build();
        instructorProfileRepository.save(profile);
    }

    private void sendWelcomeNotification(User user) {
        notificationService.sendAfterCommit(SendNotificationRequest.builder()
                .userId(user.getId())
                .subject("Welcome to Aidly!")
                .body("Your account is ready. We're glad to have you on board - explore around and let us know if you need anything.")
                .channel(NotificationChannel.IN_APP)
                .build());
    }

    // EMAIL, not IN_APP: this is a security signal the user should see even if
    // the reset means they can no longer log in to check in-app notifications
    // (e.g. an attacker changed the password), and it should reach them
    // wherever they actually are, not just inside the app.
    private void sendPasswordChangedNotification(User user) {
        notificationService.sendAfterCommit(SendNotificationRequest.builder()
                .userId(user.getId())
                .subject("Your Aidly password was changed")
                .body("Your password was just changed. If this wasn't you, please contact support immediately.")
                .channel(NotificationChannel.EMAIL)
                .build());
    }

}
