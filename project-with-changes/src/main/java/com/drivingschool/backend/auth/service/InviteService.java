package com.drivingschool.backend.auth.service;

import com.drivingschool.backend.auth.dto.InviteDetailsResponse;
import com.drivingschool.backend.auth.dto.InviteResponse;
import com.drivingschool.backend.auth.entity.AccountInvite;
import com.drivingschool.backend.auth.repository.AccountInviteRepository;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.common.exception.TooManyRequestsException;
import com.drivingschool.backend.config.ResendConfig;
import com.drivingschool.backend.email.ResendEmailClient;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.security.RateLimiter;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.enums.AccountStatus;
import com.drivingschool.backend.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Accounts created by an admin or instructor without a password: the new user gets a
 * single-use link (72 hours) and chooses their own password, so nobody else ever knows
 * it. Only a SHA-256 hash of each token is stored.
 */
@Slf4j
@Service
public class InviteService {

    static final Duration VALIDITY = Duration.ofHours(72);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    // Shown to users exactly as written - the frontend relies on them.
    static final String EXPIRED = "This invite link has expired. Ask your school to send a new one.";
    static final String USED = "This invite link has already been used. Sign in instead.";
    static final String INVALID = "This invite link isn't valid.";

    /** The person an invite is for, as the email and the accept page describe them. */
    record Invitee(String firstName, String schoolName, RoleName role) {
    }

    private final AccountInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final SchoolRepository schoolRepository;
    private final CurrentUserService currentUserService;
    private final AdminSchoolScope adminSchoolScope;
    private final RateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final ResendEmailClient emailClient;
    private final ResendConfig resendConfig;
    private final String fromAddress;
    private final String acceptUrl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public InviteService(AccountInviteRepository inviteRepository,
                         UserRepository userRepository,
                         StudentProfileRepository studentProfileRepository,
                         InstructorProfileRepository instructorProfileRepository,
                         SchoolRepository schoolRepository,
                         CurrentUserService currentUserService,
                         AdminSchoolScope adminSchoolScope,
                         RateLimiter rateLimiter,
                         PasswordEncoder passwordEncoder,
                         ResendEmailClient emailClient,
                         ResendConfig resendConfig,
                         @Value("${app.mail.from}") String fromAddress,
                         @Value("${app.invite.accept-url:}") String acceptUrl,
                         @Value("${app.password-reset.reset-url}") String resetUrl,
                         Clock clock) {
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.schoolRepository = schoolRepository;
        this.currentUserService = currentUserService;
        this.adminSchoolScope = adminSchoolScope;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.emailClient = emailClient;
        this.resendConfig = resendConfig;
        this.fromAddress = fromAddress;
        this.acceptUrl = acceptUrl != null && !acceptUrl.isBlank() ? acceptUrl : siblingOf(resetUrl, "accept-invite");
        this.clock = clock;
    }

    /** A password nobody knows, for an account that hasn't been set up yet. */
    public String unusablePassword() {
        // 32 random bytes (43 chars): unguessable, and within BCrypt's 72-byte limit.
        return passwordEncoder.encode(newToken());
    }

    /**
     * Creates (or replaces) the user's invite and emails it. Never throws on a mail
     * failure - the account still exists and the result says FAILED, with the link for
     * the creator to share another way.
     */
    @Transactional
    public InviteResponse invite(User user, User invitedBy) {
        inviteRepository.deleteAllForUser(user.getId());
        String token = newToken();
        LocalDateTime expiresAt = LocalDateTime.now(clock).plus(VALIDITY);
        inviteRepository.save(AccountInvite.builder()
                .user(user).tokenHash(hash(token)).expiresAt(expiresAt).invitedBy(invitedBy).build());
        user.markInvited(expiresAt);
        userRepository.save(user);

        String url = acceptUrl + "?token=" + token;
        boolean sent = send(user, invitedBy, url);
        return InviteResponse.builder().status(sent ? "SENT" : "FAILED").expiresAt(expiresAt).url(url).build();
    }

    /** POST /users/{userId}/invite - same access rules as setting someone's photo. */
    @Transactional
    public InviteResponse resend(Long userId) {
        User target = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        requireCanManage(target);
        if (target.getAccountStatus() != AccountStatus.INVITED) {
            throw new BadRequestException("This person has already set up their account.");
        }
        requireCooldown(target.getId());
        User by = userRepository.findById(currentUserService.requireUserId()).orElse(null);
        return invite(target, by);
    }

    /** Forgot-password for an INVITED account: a fresh invite instead of a reset email. */
    @Transactional
    public void reinviteQuietly(User user) {
        try {
            requireCooldown(user.getId());
            invite(user, null);
        } catch (RuntimeException e) {
            log.info("Not re-sending invite for user {}: {}", user.getId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public InviteDetailsResponse details(String token) {
        AccountInvite invite = usableInvite(token);
        User user = invite.getUser();
        Invitee who = describe(user);
        return InviteDetailsResponse.builder()
                .firstName(who.firstName())
                .email(AccountVerificationService.maskEmail(user.getEmail()))
                .schoolName(who.schoolName())
                .role(who.role() != null ? who.role().name() : null)
                .expiresAt(invite.getExpiresAt())
                .build();
    }

    /** Sets the password, activates the account (email proven by the link) and uses up the token. */
    @Transactional
    public User accept(String token, String rawPassword) {
        AccountInvite invite = usableInvite(token);
        User user = invite.getUser();
        user.updatePassword(passwordEncoder.encode(rawPassword));
        user.activate();
        user.verifyEmail();
        invite.markUsed(LocalDateTime.now(clock));
        inviteRepository.save(invite);
        return userRepository.save(user);
    }

    // ------------------------------------------------------------------ helpers

    private AccountInvite usableInvite(String token) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException(INVALID);
        }
        AccountInvite invite = inviteRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new BadRequestException(INVALID));
        User user = invite.getUser();
        if (user.isDeleted() || !user.isEnabled()) {
            throw new BadRequestException(INVALID);
        }
        if (invite.isUsed() || user.getAccountStatus() == AccountStatus.ACTIVE) {
            throw new BadRequestException(USED);
        }
        if (invite.isExpired(LocalDateTime.now(clock))) {
            throw new BadRequestException(EXPIRED);
        }
        return invite;
    }

    private void requireCanManage(User target) {
        Long callerId = currentUserService.requireUserId();
        if (currentUserService.hasRole(RoleName.ADMIN)) {
            adminSchoolScope.requireAccessToUser(target.getId());
            return;
        }
        if (currentUserService.hasRole(RoleName.INSTRUCTOR)) {
            Long mySchool = instructorProfileRepository.findByUserId(callerId).map(p -> p.getSchool().getId()).orElse(null);
            boolean myStudent = studentProfileRepository.findByUserId(target.getId())
                    .map(p -> p.getSchool().getId().equals(mySchool)).orElse(false);
            if (myStudent) {
                return;
            }
        }
        throw new ForbiddenException("You can't send an invite to this person");
    }

    private void requireCooldown(Long userId) {
        RateLimiter.RateLimitResult result = rateLimiter.tryConsume("ratelimit:invite:" + userId, 1, RESEND_COOLDOWN);
        if (!result.allowed()) {
            throw new TooManyRequestsException("An invite was just sent - please wait a minute before sending another",
                    result.retryAfterSeconds());
        }
    }

    Invitee describe(User user) {
        return studentProfileRepository.findByUserId(user.getId())
                .map(p -> new Invitee(p.getFirstName(), p.getSchool().getName(), RoleName.STUDENT))
                .or(() -> instructorProfileRepository.findByUserId(user.getId())
                        .map(p -> new Invitee(p.getFirstName(), p.getSchool().getName(), RoleName.INSTRUCTOR)))
                .or(() -> schoolRepository.findByOwningAdminId(user.getId())
                        .map(s -> new Invitee(null, s.getName(), RoleName.ADMIN)))
                .orElse(new Invitee(null, null, null));
    }

    private boolean send(User user, User invitedBy, String url) {
        if (resendConfig.getApiKey() == null || resendConfig.getApiKey().isBlank()) {
            log.warn("Email delivery not configured - invite for {} not emailed", user.getEmail());
            return false;
        }
        Invitee who = describe(user);
        String school = who.schoolName() != null ? who.schoolName() : "Aidly";
        String greeting = who.firstName() != null ? "Hi " + who.firstName() + "," : "Hi,";
        String inviter = invitedBy != null ? invitedBy.getDisplayName() : "Your school";
        String role = who.role() != null ? who.role().name().toLowerCase() : "member";
        String article = role.startsWith("a") || role.startsWith("i") ? "an" : "a";
        String added = inviter + " added you as " + article + " " + role + ".";
        String text = greeting + "\n\n" + added + "\n\nSet your password to start using Aidly:\n" + url
                + "\n\nThis link expires in 3 days.\nIf you weren't expecting this, ignore this email.";
        String html = "<p>" + HtmlUtils.htmlEscape(greeting) + "</p>"
                + "<p>" + HtmlUtils.htmlEscape(added) + "</p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(url) + "\" style=\"display:inline-block;padding:12px 20px;"
                + "background:#1a56db;color:#ffffff;text-decoration:none;border-radius:6px;font-weight:bold\">"
                + "Set your password</a></p>"
                + "<p>This link expires in 3 days.</p>"
                + "<p style=\"color:#666\">If you weren't expecting this, ignore this email.</p>";
        try {
            emailClient.send(fromAddress, user.getEmail(), "You've been added to " + school + " on Aidly", text, html);
            return true;
        } catch (Exception e) {
            log.warn("Failed to email invite to {} ({})", user.getEmail(), e.getMessage());
            return false;
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** ".../reset-password" -> ".../accept-invite", so both links share one base-URL setting. */
    static String siblingOf(String url, String page) {
        int slash = url.lastIndexOf('/');
        return (slash > url.indexOf("//") + 1 ? url.substring(0, slash) : url) + "/" + page;
    }

    Optional<String> acceptUrl() {
        return Optional.of(acceptUrl);
    }
}
