package com.drivingschool.backend.auth.service;

import com.drivingschool.backend.auth.dto.AuthResponse;
import com.drivingschool.backend.auth.dto.ForgotPasswordRequest;
import com.drivingschool.backend.auth.dto.LoginRequest;
import com.drivingschool.backend.auth.dto.RefreshTokenRequest;
import com.drivingschool.backend.auth.dto.RegisterRequest;
import com.drivingschool.backend.auth.dto.ResetPasswordRequest;
import com.drivingschool.backend.auth.entity.PasswordResetToken;
import com.drivingschool.backend.auth.mapper.AuthMapper;
import com.drivingschool.backend.auth.mapper.CurrentUserMapper;
import com.drivingschool.backend.auth.repository.PasswordResetTokenRepository;
import com.drivingschool.backend.common.exception.AuthenticationException;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.role.repository.RoleRepository;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.security.UserPrincipal;
import com.drivingschool.backend.security.jwt.JwtTokenProvider;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        authService = new AuthServiceImpl(authenticationManager, userRepository, roleRepository,
                schoolRepository, studentProfileRepository, instructorProfileRepository,
                passwordEncoder, jwtTokenProvider, authMapper, currentUserMapper, currentUserService,
                passwordResetTokenRepository, emailService, 3_600_000L);
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
        when(jwtTokenProvider.generateRefreshToken(principal)).thenReturn("refresh");
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
        when(userRepository.existsByEmail("new@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(validStudentRequest().build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already registered");

        verify(schoolRepository, never()).findById(any());
    }

    @Test
    void register_whenSchoolNotFound_throwsResourceNotFoundException() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(schoolRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(validStudentRequest().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void register_whenSchoolInactive_throwsBadRequestException() {
        School inactiveSchool = School.builder().active(false).build();
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(inactiveSchool));

        assertThatThrownBy(() -> authService.register(validStudentRequest().build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void register_whenRoleNotFound_throwsResourceNotFoundException() {
        School activeSchool = School.builder().active(true).build();
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
    void register_validStudent_createsUserAndProfileAndReturnsTokens() {
        School activeSchool = School.builder().active(true).build();
        Role studentRole = Role.builder().name(RoleName.STUDENT).build();
        User savedUser = existingUser(RoleName.STUDENT);
        AuthResponse expectedResponse = AuthResponse.builder().accessToken("access").refreshToken("refresh").build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(activeSchool));
        when(roleRepository.findByName(RoleName.STUDENT)).thenReturn(Optional.of(studentRole));
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access");
        when(jwtTokenProvider.generateRefreshToken(any(UserPrincipal.class))).thenReturn("refresh");
        when(authMapper.toAuthResponse(eq(savedUser), eq("access"), eq("refresh"))).thenReturn(expectedResponse);

        AuthResponse response = authService.register(validStudentRequest().build());

        assertThat(response).isEqualTo(expectedResponse);
        verify(studentProfileRepository).save(any());
        verify(instructorProfileRepository, never()).save(any());
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
        when(jwtTokenProvider.getEmailFromToken("refresh-token")).thenReturn("ghost@example.com");
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void refreshToken_withValidRefreshToken_issuesNewTokenPair() {
        User user = existingUser(RoleName.STUDENT);
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("refresh-token").build();
        AuthResponse expectedResponse = AuthResponse.builder().accessToken("new-access").refreshToken("new-refresh").build();

        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken("refresh-token")).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("new-access");
        when(jwtTokenProvider.generateRefreshToken(any(UserPrincipal.class))).thenReturn("new-refresh");
        when(authMapper.toAuthResponse(eq(user), eq("new-access"), eq("new-refresh"))).thenReturn(expectedResponse);

        AuthResponse response = authService.refreshToken(request);

        assertThat(response).isEqualTo(expectedResponse);
        verify(jwtTokenProvider, times(1)).generateAccessToken(any(UserPrincipal.class));
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
}
