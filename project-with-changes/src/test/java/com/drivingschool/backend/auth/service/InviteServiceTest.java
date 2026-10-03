package com.drivingschool.backend.auth.service;

import com.drivingschool.backend.auth.dto.InviteResponse;
import com.drivingschool.backend.auth.entity.AccountInvite;
import com.drivingschool.backend.auth.repository.AccountInviteRepository;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.common.exception.TooManyRequestsException;
import com.drivingschool.backend.config.ResendConfig;
import com.drivingschool.backend.email.ResendEmailClient;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.security.RateLimiter;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.enums.AccountStatus;
import com.drivingschool.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

    @Mock private AccountInviteRepository inviteRepository;
    @Mock private UserRepository userRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;
    @Mock private SchoolRepository schoolRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private AdminSchoolScope adminSchoolScope;
    @Mock private RateLimiter rateLimiter;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ResendEmailClient emailClient;

    private final ResendConfig resendConfig = new ResendConfig();
    private InviteService service;
    private User user;

    @BeforeEach
    void setUp() {
        resendConfig.setApiKey("re_test");
        service = new InviteService(inviteRepository, userRepository, studentProfileRepository,
                instructorProfileRepository, schoolRepository, currentUserService, adminSchoolScope, rateLimiter,
                passwordEncoder, emailClient, resendConfig, "Aidly <no-reply@aidly.test>", "",
                "https://aidly-frontend-mu.vercel.app/reset-password", Clock.fixed(NOW, ZoneOffset.UTC));
        user = User.builder().email("ransford@gmail.com").password("x").enabled(true).emailVerified(false).build();
        ReflectionTestUtils.setField(user, "id", 7L);
        School school = School.builder().name("KINGSCHOOL").address("Accra").active(true).build();
        ReflectionTestUtils.setField(school, "id", 5L);
        lenient().when(studentProfileRepository.findByUserId(7L)).thenReturn(Optional.of(
                StudentProfile.builder().firstName("Ransford").lastName("A").school(school).user(user).build()));
        lenient().when(rateLimiter.tryConsume(anyString(), anyInt(), any()))
                .thenReturn(new RateLimiter.RateLimitResult(true, 0, 0));
    }

    private String inviteAndCaptureToken() {
        InviteResponse response = service.invite(user, null);
        return response.getUrl().substring(response.getUrl().indexOf("token=") + 6);
    }

    private AccountInvite savedInvite() {
        ArgumentCaptor<AccountInvite> captor = ArgumentCaptor.forClass(AccountInvite.class);
        verify(inviteRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void inviting_storesOnlyAHash_expiresIn72Hours_marksTheUserInvited_andEmailsTheLink() {
        InviteResponse response = service.invite(user, null);

        String token = response.getUrl().substring(response.getUrl().indexOf("token=") + 6);
        assertThat(response.getUrl()).startsWith("https://aidly-frontend-mu.vercel.app/accept-invite?token=");
        assertThat(token).hasSizeGreaterThanOrEqualTo(43); // 32 bytes, URL-safe base64
        assertThat(response.getStatus()).isEqualTo("SENT");
        assertThat(response.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 10, 6, 10, 0));
        AccountInvite saved = savedInvite();
        assertThat(saved.getTokenHash()).isEqualTo(InviteService.hash(token)).isNotEqualTo(token);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.INVITED);
        assertThat(user.getInviteExpiresAt()).isEqualTo(response.getExpiresAt());
        verify(inviteRepository).deleteAllForUser(7L);
        verify(emailClient).send(anyString(), eq("ransford@gmail.com"), eq("You've been added to KINGSCHOOL on Aidly"),
                anyString(), anyString());
    }

    @Test
    void aMailFailure_stillCreatesTheInvite_andReportsFailed() {
        doThrow(new RuntimeException("403 testing domain")).when(emailClient)
                .send(anyString(), anyString(), anyString(), anyString(), anyString());

        InviteResponse response = service.invite(user, null);

        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getUrl()).isNotNull();
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.INVITED);
    }

    @Test
    void theInvitePage_greetsThePerson_withAMaskedEmail() {
        String token = inviteAndCaptureToken();
        AccountInvite invite = savedInvite();
        when(inviteRepository.findByTokenHash(InviteService.hash(token))).thenReturn(Optional.of(invite));

        var details = service.details(token);

        assertThat(details.getFirstName()).isEqualTo("Ransford");
        assertThat(details.getEmail()).isEqualTo("r***@gmail.com");
        assertThat(details.getSchoolName()).isEqualTo("KINGSCHOOL");
        assertThat(details.getRole()).isEqualTo("STUDENT");
    }

    @Test
    void accepting_setsThePassword_activates_verifiesTheEmail_andUsesUpTheToken() {
        String token = inviteAndCaptureToken();
        AccountInvite invite = savedInvite();
        when(inviteRepository.findByTokenHash(InviteService.hash(token))).thenReturn(Optional.of(invite));
        when(passwordEncoder.encode("MyOwnPass123")).thenReturn("encoded-own");
        when(userRepository.save(user)).thenReturn(user);

        service.accept(token, "MyOwnPass123");

        assertThat(user.getPassword()).isEqualTo("encoded-own");
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getInviteExpiresAt()).isNull();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(invite.isUsed()).isTrue();
        assertThatThrownBy(() -> service.accept(token, "Another123")).hasMessage(InviteService.USED);
    }

    @Test
    void anExpiredLink_andAnUnknownOne_getTheirFriendlyMessages() {
        AccountInvite expired = AccountInvite.builder().user(user).tokenHash(InviteService.hash("old"))
                .expiresAt(LocalDateTime.of(2026, 10, 1, 0, 0)).build();
        user.markInvited(expired.getExpiresAt());
        when(inviteRepository.findByTokenHash(InviteService.hash("old"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.details("old")).isInstanceOf(BadRequestException.class).hasMessage(InviteService.EXPIRED);
        assertThatThrownBy(() -> service.details("nope")).isInstanceOf(BadRequestException.class).hasMessage(InviteService.INVALID);
    }

    @Test
    void aDeletedPersonsInvite_isNoLongerValid() {
        String token = inviteAndCaptureToken();
        AccountInvite invite = savedInvite();
        when(inviteRepository.findByTokenHash(InviteService.hash(token))).thenReturn(Optional.of(invite));
        user.softDelete();

        assertThatThrownBy(() -> service.accept(token, "MyOwnPass123")).hasMessage(InviteService.INVALID);
    }

    @Test
    void resending_forSomeoneAlreadySetUp_isRejected() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(currentUserService.requireUserId()).thenReturn(9L);
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(true);

        assertThatThrownBy(() -> service.resend(7L)).isInstanceOf(BadRequestException.class)
                .hasMessage("This person has already set up their account.");
    }

    @Test
    void resendingWithinAMinute_is429() {
        user.markInvited(LocalDateTime.of(2026, 10, 6, 10, 0));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(currentUserService.requireUserId()).thenReturn(9L);
        when(currentUserService.hasRole(RoleName.ADMIN)).thenReturn(true);
        when(rateLimiter.tryConsume(eq("ratelimit:invite:7"), eq(1), any()))
                .thenReturn(new RateLimiter.RateLimitResult(false, 0, 42));

        assertThatThrownBy(() -> service.resend(7L)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void aStudent_cannotResendInvites() {
        user.markInvited(LocalDateTime.of(2026, 10, 6, 10, 0));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(currentUserService.requireUserId()).thenReturn(8L);

        assertThatThrownBy(() -> service.resend(7L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void theAcceptPage_sitsNextToTheResetPasswordPage() {
        assertThat(InviteService.siblingOf("https://aidly-frontend-mu.vercel.app/reset-password", "accept-invite"))
                .isEqualTo("https://aidly-frontend-mu.vercel.app/accept-invite");
        assertThat(InviteService.siblingOf("http://localhost:3000/reset-password", "accept-invite"))
                .isEqualTo("http://localhost:3000/accept-invite");
    }
}
