package com.drivingschool.backend.auth.service;

import com.drivingschool.backend.auth.dto.AdminRegisterRequest;
import com.drivingschool.backend.auth.dto.AuthResponse;
import com.drivingschool.backend.auth.dto.ConfirmVerificationRequest;
import com.drivingschool.backend.auth.dto.ForgotPasswordRequest;
import com.drivingschool.backend.auth.dto.LoginRequest;
import com.drivingschool.backend.auth.dto.RefreshTokenRequest;
import com.drivingschool.backend.auth.dto.RegisterRequest;
import com.drivingschool.backend.auth.dto.ResetPasswordRequest;
import com.drivingschool.backend.auth.dto.VerificationChallengeResponse;
import com.drivingschool.backend.auth.entity.PasswordResetToken;
import com.drivingschool.backend.auth.mapper.AuthMapper;
import com.drivingschool.backend.auth.mapper.CurrentUserMapper;
import com.drivingschool.backend.auth.repository.PasswordResetTokenRepository;
import com.drivingschool.backend.common.exception.AuthenticationException;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.notification.service.NotificationService;
import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.role.repository.RoleRepository;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.school.service.SchoolDeletionRequestService;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.security.RefreshTokenRevocationService;
import com.drivingschool.backend.security.UserPrincipal;
import com.drivingschool.backend.security.jwt.JwtTokenProvider;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import com.drivingschool.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private SchoolRepository schoolRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuthMapper authMapper;
    @Mock private CurrentUserMapper currentUserMapper;
    @Mock private CurrentUserService currentUserService;
    @Mock private Authentication authentication;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailService emailService;
    @Mock private UserService userService;
    @Mock private RefreshTokenRevocationService refreshTokenRevocationService;
    @Mock private SchoolDeletionRequestService schoolDeletionRequestService;
    @Mock private NotificationService notificationService;
    @Mock private AdminSchoolScope adminSchoolScope;
    @Mock private AccountVerificationService accountVerificationService;
    @Mock private com.drivingschool.backend.security.RateLimiter rateLimiter;

    private AuthServiceImpl authService;

    private User existingUser(RoleName roleName) {
        User user = User.builder()
                .email("user@example.com")
                .password("encoded-password")
                .enabled(true)
                .emailVerified(true)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        user.addRole(Role.builder().name(roleName).build());
        return user;
    }

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(rateLimiter.tryConsume(any(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(new com.drivingschool.backend.security.RateLimiter.RateLimitResult(true, 9, 0));
        authService = new AuthServiceImpl(authenticationManager, userRepository, roleRepository,
                schoolRepository, studentProfileRepository, instructorProfileRepository,
                passwordEncoder, jwtTokenProvider, authMapper, currentUserMapper, currentUserService,
                passwordResetTokenRepository, emailService, userService, refreshTokenRevocationService,
                schoolDeletionRequestService, notificationService, adminSchoolScope, accountVerificationService, rateLimiter, 3_600_000L, true);
    }

    // --- login ---

    @Test
    void login_withValidCredentials_returnsAuthResponseAndRecordsLogin() {
        User user = existingUser(RoleName.STUDENT);
        UserPrincipal principal = new UserPrincipal(user);
        LoginRequest request = LoginRequest.builder().email("user@example.com").password("password123").build();
        AuthResponse expectedResponse = AuthResponse.builder().accessToken("access").refreshToken("refresh").build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(principal)).thenReturn("access");
        when(jwtTokenProvider.generateRefreshToken(org.mockito.ArgumentMatchers.eq(principal), any())).thenReturn("refresh");
        when(authMapper.toAuthResponse(user, "access", "refresh")).thenReturn(expectedResponse);

        AuthResponse response = authService.login(request);

        assertThat(response).isEqualTo(expectedResponse);
        verify(userRepository).save(user);
    }

    @Test
    void login_whenAuthenticatedUserMissingFromDatabase_throwsResourceNotFoundException() {
        User user = existingUser(RoleName.STUDENT);
        UserPrincipal principal = new UserPrincipal(user);
        LoginRequest request = LoginRequest.builder().email("user@example.com").password("password123").build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- register ---

    private RegisterRequest.RegisterRequestBuilder validStudentRequest() {
        return RegisterRequest.builder()
                .email("new@example.com")
                .password("password123")
                .firstName("Jane")
                .lastName("Doe")
                .schoolId(1L)
                .role(RoleName.STUDENT);
    }

    @Test
    void register_whenEmailAlreadyRegistered_throwsBadRequestException() {
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(true);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(validStudentRequest().build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already registered");

        verify(schoolRepository, never()).findById(any());
    }

    @Test
    void register_whenSchoolNotFound_throwsResourceNotFoundException() {
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(true);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(schoolRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(validStudentRequest().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void register_whenSchoolInactive_throwsBadRequestException() {
        School inactiveSchool = School.builder().active(false).build();
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(true);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(inactiveSchool));

        assertThatThrownBy(() -> authService.register(validStudentRequest().build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void register_whenRoleNotFound_throwsResourceNotFoundException() {
        School activeSchool = School.builder().active(true).build();
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(true);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(activeSchool));
        when(roleRepository.findByName(RoleName.STUDENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(validStudentRequest().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void register_withAdminRole_isRejectedRegardlessOfOtherData() {
        School activeSchool = School.builder().active(true).build();
        Role adminRole = Role.builder().name(RoleName.ADMIN).build();
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(true);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(activeSchool));
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));
        RegisterRequest adminRequest = validStudentRequest().role(RoleName.ADMIN).build();

        assertThatThrownBy(() -> authService.register(adminRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Admin registration is not allowed");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_instructorWithoutLicenseNumber_throwsBadRequestException() {
        School activeSchool = School.builder().active(true).build();
        Role instructorRole = Role.builder().name(RoleName.INSTRUCTOR).build();
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(true);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(activeSchool));
        when(roleRepository.findByName(RoleName.INSTRUCTOR)).thenReturn(Optional.of(instructorRole));
        RegisterRequest instructorRequest = validStudentRequest().role(RoleName.INSTRUCTOR).licenseNumber(null).build();

        assertThatThrownBy(() -> authService.register(instructorRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("License number is required");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_validStudent_createsUserAndProfileWithoutIssuingTokens() {
        School activeSchool = School.builder().active(true).build();
        Role studentRole = Role.builder().name(RoleName.STUDENT).build();
        User savedUser = existingUser(RoleName.STUDENT);
        AuthResponse expectedResponse = AuthResponse.builder().build();

        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(true);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(activeSchool));
        when(roleRepository.findByName(RoleName.STUDENT)).thenReturn(Optional.of(studentRole));
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(authMapper.toRegisteredUserResponse(savedUser)).thenReturn(expectedResponse);

        AuthResponse response = authService.register(validStudentRequest().build());

        assertThat(response).isEqualTo(expectedResponse);
        verify(studentProfileRepository).save(any());
        verify(instructorProfileRepository, never()).save(any());
        verify(jwtTokenProvider, never()).generateAccessToken(any());
        verify(jwtTokenProvider, never()).generateRefreshToken(any(), any());
    }

    // --- refreshToken ---

    @Test
    void refreshToken_withInvalidToken_throwsAuthenticationException() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("bad-token").build();
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AuthenticationException.class);

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void refreshToken_withAccessTokenInsteadOfRefreshToken_throwsAuthenticationException() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("access-token").build();
        when(jwtTokenProvider.validateToken("access-token")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("access-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void refreshToken_whenUserNoLongerExists_throwsAuthenticationException() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("refresh-token").build();
        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getJtiFromToken("refresh-token")).thenReturn("jti-1");
        when(refreshTokenRevocationService.isRevoked("jti-1")).thenReturn(false);
        when(jwtTokenProvider.getEmailFromToken("refresh-token")).thenReturn("ghost@example.com");
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void refreshToken_withRevokedToken_throwsAuthenticationException() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("refresh-token").build();
        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getJtiFromToken("refresh-token")).thenReturn("jti-1");
        when(refreshTokenRevocationService.isRevoked("jti-1")).thenReturn(true);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("revoked");

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void refreshToken_withValidRefreshToken_issuesNewTokenPair() {
        User user = existingUser(RoleName.STUDENT);
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("refresh-token").build();
        AuthResponse expectedResponse = AuthResponse.builder().accessToken("new-access").refreshToken("new-refresh").build();

        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getJtiFromToken("refresh-token")).thenReturn("jti-1");
        when(refreshTokenRevocationService.isRevoked("jti-1")).thenReturn(false);
        when(jwtTokenProvider.getEmailFromToken("refresh-token")).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.getIssuedAtFromToken("refresh-token")).thenReturn(new java.util.Date());
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("new-access");
        JwtTokenProvider.Session session = new JwtTokenProvider.Session("s1", 1_790_000_000L, false);
        when(jwtTokenProvider.getSessionFromToken("refresh-token")).thenReturn(session);
        when(jwtTokenProvider.generateRefreshToken(any(UserPrincipal.class), eq(session))).thenReturn("new-refresh");
        when(authMapper.toAuthResponse(eq(user), eq("new-access"), eq("new-refresh"))).thenReturn(expectedResponse);

        AuthResponse response = authService.refreshToken(request);

        // The new refresh token continues the same session (same start, same "remember me").
        assertThat(response).isEqualTo(expectedResponse);
        verify(jwtTokenProvider, times(1)).generateAccessToken(any(UserPrincipal.class));
    }

    // --- logout ---

    @Test
    void logout_withInvalidToken_throwsAuthenticationException() {
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.logout("bad-token"))
                .isInstanceOf(AuthenticationException.class);

        verify(refreshTokenRevocationService, never()).revoke(anyString(), anyLong());
    }

    @Test
    void logout_withAccessTokenInsteadOfRefreshToken_throwsAuthenticationException() {
        when(jwtTokenProvider.validateToken("access-token")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("access-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.logout("access-token"))
                .isInstanceOf(AuthenticationException.class);

        verify(refreshTokenRevocationService, never()).revoke(anyString(), anyLong());
    }

    @Test
    void logout_withValidRefreshToken_revokesIt() {
        Date expiry = new Date(System.currentTimeMillis() + 60_000L);
        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getJtiFromToken("refresh-token")).thenReturn("jti-1");
        when(jwtTokenProvider.getExpirationFromToken("refresh-token")).thenReturn(expiry);
        when(jwtTokenProvider.getEmailFromToken("refresh-token")).thenReturn("user@example.com");

        authService.logout("refresh-token");

        verify(refreshTokenRevocationService, times(1)).revoke(eq("jti-1"), anyLong());
    }

    // --- forgotPassword ---

    @Test
    void forgotPassword_withExistingEmail_createsTokenAndSendsEmail() {
        User user = existingUser(RoleName.STUDENT);
        ForgotPasswordRequest request = ForgotPasswordRequest.builder().email("user@example.com").build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        authService.forgotPassword(request);

        verify(passwordResetTokenRepository, times(1)).save(any(PasswordResetToken.class));
        verify(emailService, times(1)).sendPasswordResetEmail(eq("user@example.com"), anyString(), anyLong());
    }

    @Test
    void forgotPassword_withUnknownEmail_doesNothingAndDoesNotThrow() {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder().email("ghost@example.com").build();
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword(request);

        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyLong());
    }

    // --- resetPassword ---

    @Test
    void resetPassword_withValidToken_updatesPasswordAndMarksTokenUsed() {
        User user = existingUser(RoleName.STUDENT);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token("valid-token")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("valid-token")
                .newPassword("newPassword123")
                .build();

        when(passwordResetTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("newPassword123")).thenReturn("encoded-new-password");

        authService.resetPassword(request);

        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        assertThat(resetToken.isUsed()).isTrue();
        verify(userRepository, times(1)).save(user);
        verify(passwordResetTokenRepository, times(1)).save(resetToken);
    }

    @Test
    void resetPassword_withUnknownToken_throwsBadRequestException() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("missing-token")
                .newPassword("newPassword123")
                .build();
        when(passwordResetTokenRepository.findByToken("missing-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void resetPassword_withExpiredToken_throwsBadRequestException() {
        User user = existingUser(RoleName.STUDENT);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token("expired-token")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("expired-token")
                .newPassword("newPassword123")
                .build();
        when(passwordResetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resetPassword_withAlreadyUsedToken_throwsBadRequestException() {
        User user = existingUser(RoleName.STUDENT);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token("used-token")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        resetToken.markUsed();
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("used-token")
                .newPassword("newPassword123")
                .build();
        when(passwordResetTokenRepository.findByToken("used-token")).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    // --- registerByAdmin ---

    @Test
    void registerByAdmin_withAdminRole_throwsBadRequestException() {
        AdminRegisterRequest request = AdminRegisterRequest.builder()
                .email("new-admin@example.com").password("password123")
                .firstName("Jane").lastName("Doe").schoolId(1L).role(RoleName.ADMIN).build();

        assertThatThrownBy(() -> authService.registerByAdmin(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("POST /api/v1/schools");

        verify(userRepository, never()).save(any());
    }

    // --- deleteCurrentAccount ---

    @Test
    void deleteCurrentAccount_asStudentOrInstructor_delegatesToUserServiceSoftDelete() {
        when(currentUserService.requireUserId()).thenReturn(1L);
        when(currentUserService.isBootstrapAdmin()).thenReturn(false);
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(false);

        authService.deleteCurrentAccount();

        verify(userService, times(1)).softDelete(1L);
        verify(schoolDeletionRequestService, never()).requestOwnSchoolDeletion(any());
    }

    @Test
    void deleteCurrentAccount_asNonBootstrapAdmin_delegatesToSchoolDeletionRequestFlow() {
        when(currentUserService.requireUserId()).thenReturn(1L);
        when(currentUserService.isBootstrapAdmin()).thenReturn(false);
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(true);

        authService.deleteCurrentAccount();

        verify(schoolDeletionRequestService, times(1)).requestOwnSchoolDeletion(1L);
        verify(userService, never()).softDelete(any());
    }

    @Test
    void deleteCurrentAccount_asBootstrapAdmin_throwsBadRequestException() {
        when(currentUserService.requireUserId()).thenReturn(1L);
        when(currentUserService.isBootstrapAdmin()).thenReturn(true);

        assertThatThrownBy(() -> authService.deleteCurrentAccount())
                .isInstanceOf(BadRequestException.class);

        verify(userService, never()).softDelete(any());
        verify(schoolDeletionRequestService, never()).requestOwnSchoolDeletion(any());
    }

    @Test
    void register_asAdminIntoAnotherSchool_isRejectedBeforeAnyAccountIsCreated() {
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(true);
        doThrow(new BadRequestException("no access")).when(adminSchoolScope).requireAccess(1L);

        assertThatThrownBy(() -> authService.register(validStudentRequest().build()))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
        verify(studentProfileRepository, never()).save(any());
    }

    private RefreshTokenRequest stubValidRefreshTokenFor(User user) {
        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getJtiFromToken("refresh-token")).thenReturn("jti-1");
        when(refreshTokenRevocationService.isRevoked("jti-1")).thenReturn(false);
        when(jwtTokenProvider.getEmailFromToken("refresh-token")).thenReturn(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        return RefreshTokenRequest.builder().refreshToken("refresh-token").build();
    }

    @Test
    void refreshToken_forSoftDeletedUser_throwsAuthenticationException() {
        User user = existingUser(RoleName.STUDENT);
        user.softDelete();
        RefreshTokenRequest request = stubValidRefreshTokenFor(user);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("disabled");
        verify(jwtTokenProvider, never()).generateAccessToken(any());
    }

    @Test
    void refreshToken_forDisabledUser_throwsAuthenticationException() {
        User user = existingUser(RoleName.INSTRUCTOR);
        user.setEnabled(false);
        RefreshTokenRequest request = stubValidRefreshTokenFor(user);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AuthenticationException.class);
        verify(jwtTokenProvider, never()).generateAccessToken(any());
    }

    @Test
    void refreshToken_issuedBeforeAPasswordReset_throwsAuthenticationException() {
        User user = existingUser(RoleName.STUDENT);
        RefreshTokenRequest request = stubValidRefreshTokenFor(user);
        when(jwtTokenProvider.getIssuedAtFromToken("refresh-token")).thenReturn(new java.util.Date(5_000L));
        when(refreshTokenRevocationService.isRevokedForUser(1L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("revoked");
        verify(jwtTokenProvider, never()).generateAccessToken(any());
    }

    @Test
    void resetPassword_revokesEveryExistingRefreshTokenForTheUser() {
        User user = existingUser(RoleName.STUDENT);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token("valid-token")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(passwordResetTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("newPassword123")).thenReturn("encoded-new-password");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);

        authService.resetPassword(ResetPasswordRequest.builder().token("valid-token").newPassword("newPassword123").build());

        verify(refreshTokenRevocationService).revokeAllForUser(1L, 604_800_000L);
    }

    // --- account verification ---

    @Test
    void login_forAnUnverifiedAccount_returnsAChallengeInsteadOfTokens() {
        User user = User.builder().email("new@example.com").password("encoded").enabled(true).emailVerified(false).build();
        ReflectionTestUtils.setField(user, "id", 1L);
        user.addRole(Role.builder().name(RoleName.STUDENT).build());
        VerificationChallengeResponse challenge = VerificationChallengeResponse.builder().challengeId("c1").build();
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(new UserPrincipal(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(accountVerificationService.startChallenge(user)).thenReturn(challenge);

        AuthResponse response = authService.login(LoginRequest.builder().email("new@example.com").password("password123").build());

        assertThat(response.getVerificationRequired()).isTrue();
        assertThat(response.getVerification()).isSameAs(challenge);
        assertThat(response.getAccessToken()).isNull();
        assertThat(user.getLastLoginAt()).isNull();
        verify(jwtTokenProvider, never()).generateAccessToken(any());
    }

    @Test
    void confirmVerification_logsTheNowVerifiedUserIn() {
        User user = existingUser(RoleName.STUDENT);
        ConfirmVerificationRequest request = ConfirmVerificationRequest.builder().challengeId("c1").code("123456").build();
        AuthResponse tokens = AuthResponse.builder().accessToken("access").build();
        when(accountVerificationService.confirm(request)).thenReturn(user);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access");
        when(jwtTokenProvider.generateRefreshToken(any(), any())).thenReturn("refresh");
        when(authMapper.toAuthResponse(user, "access", "refresh")).thenReturn(tokens);

        assertThat(authService.confirmVerification(request)).isSameAs(tokens);
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void login_forAnUnverifiedAccount_withVerificationSwitchedOff_returnsTokens() {
        AuthServiceImpl withoutVerification = new AuthServiceImpl(authenticationManager, userRepository, roleRepository,
                schoolRepository, studentProfileRepository, instructorProfileRepository,
                passwordEncoder, jwtTokenProvider, authMapper, currentUserMapper, currentUserService,
                passwordResetTokenRepository, emailService, userService, refreshTokenRevocationService,
                schoolDeletionRequestService, notificationService, adminSchoolScope, accountVerificationService,
                rateLimiter, 3_600_000L, false);
        User user = User.builder().email("new@example.com").password("encoded").enabled(true).emailVerified(false).build();
        ReflectionTestUtils.setField(user, "id", 1L);
        user.addRole(Role.builder().name(RoleName.STUDENT).build());
        UserPrincipal principal = new UserPrincipal(user);
        AuthResponse tokens = AuthResponse.builder().accessToken("access").build();
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(principal)).thenReturn("access");
        when(jwtTokenProvider.generateRefreshToken(org.mockito.ArgumentMatchers.eq(principal), any())).thenReturn("refresh");
        when(authMapper.toAuthResponse(user, "access", "refresh")).thenReturn(tokens);

        AuthResponse response = withoutVerification.login(LoginRequest.builder().email("new@example.com").password("password123").build());

        assertThat(response).isSameAs(tokens);
        assertThat(user.isAccountVerified()).isFalse(); // still has to verify once the switch is back on
        verify(accountVerificationService, never()).startChallenge(any());
    }

    @Test
    void login_withRememberMe_startsARememberMeSession() {
        User user = existingUser(RoleName.STUDENT);
        UserPrincipal principal = new UserPrincipal(user);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        authService.login(LoginRequest.builder().email("user@example.com").password("password123").rememberMe(true).build());

        org.mockito.ArgumentCaptor<JwtTokenProvider.Session> session = org.mockito.ArgumentCaptor.forClass(JwtTokenProvider.Session.class);
        verify(jwtTokenProvider).generateRefreshToken(eq(principal), session.capture());
        assertThat(session.getValue().rememberMe()).isTrue();
    }

    @Test
    void login_tooManyAttemptsForOneAccount_isRefusedBeforeCheckingThePassword() {
        when(rateLimiter.tryConsume(eq("ratelimit:login:user@example.com"), eq(10), any()))
                .thenReturn(new com.drivingschool.backend.security.RateLimiter.RateLimitResult(false, 0, 420));

        assertThatThrownBy(() -> authService.login(LoginRequest.builder().email(" User@Example.com ").password("password123").build()))
                .isInstanceOf(com.drivingschool.backend.common.exception.TooManyRequestsException.class);
        verify(authenticationManager, never()).authenticate(any());
    }
}
