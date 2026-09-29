package com.drivingschool.backend.auth.service;

import com.drivingschool.backend.auth.dto.ConfirmVerificationRequest;
import com.drivingschool.backend.auth.dto.SendVerificationCodeRequest;
import com.drivingschool.backend.auth.dto.VerificationChallengeResponse;
import com.drivingschool.backend.auth.enums.VerificationChannel;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ServiceUnavailableException;
import com.drivingschool.backend.common.exception.TooManyRequestsException;
import com.drivingschool.backend.config.ResendConfig;
import com.drivingschool.backend.config.TwilioConfig;
import com.drivingschool.backend.email.ResendEmailClient;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.sms.TwilioVerifyClient;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One-time-code verification of a new account, over email or WhatsApp. Login hands an
 * unverified user a challenge instead of tokens; the challenge id (opaque, random, kept
 * in Redis) stands in for "this caller already proved the password" through the send and
 * confirm steps, so neither needs the password again.
 *
 * Email codes are generated, hashed and checked here and sent through Resend. WhatsApp
 * codes are generated, sent and checked by Twilio Verify. The attempt limit and resend
 * cooldown are enforced here for both.
 */
@Slf4j
@Service
public class AccountVerificationService {

    static final int CODE_LENGTH = 6;
    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final Duration CHALLENGE_TTL = Duration.ofMinutes(15);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final int MAX_ATTEMPTS = 5;

    private static final String CHALLENGE_KEY = "verify:challenge:";
    private static final String CODE_KEY = "verify:code:";
    private static final String COOLDOWN_KEY = "verify:cooldown:";

    // Twilio Verify needs E.164: a plus, then up to 15 digits with no leading zero.
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    private final StringRedisTemplate redis;
    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final ResendEmailClient resendEmailClient;
    private final ResendConfig resendConfig;
    private final TwilioVerifyClient twilioVerifyClient;
    private final TwilioConfig twilioConfig;
    private final String fromAddress;
    private final boolean logCodesWhenEmailUnconfigured;
    private final SecureRandom random = new SecureRandom();

    public AccountVerificationService(StringRedisTemplate redis,
                                      UserRepository userRepository,
                                      StudentProfileRepository studentProfileRepository,
                                      InstructorProfileRepository instructorProfileRepository,
                                      ResendEmailClient resendEmailClient,
                                      ResendConfig resendConfig,
                                      TwilioVerifyClient twilioVerifyClient,
                                      TwilioConfig twilioConfig,
                                      @Value("${app.mail.from}") String fromAddress,
                                      @Value("${app.verification.log-codes-when-email-unconfigured:false}") boolean logCodesWhenEmailUnconfigured) {
        this.redis = redis;
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.resendEmailClient = resendEmailClient;
        this.resendConfig = resendConfig;
        this.twilioVerifyClient = twilioVerifyClient;
        this.twilioConfig = twilioConfig;
        this.fromAddress = fromAddress;
        this.logCodesWhenEmailUnconfigured = logCodesWhenEmailUnconfigured;
    }

    /** Called by login once the password checks out but the account isn't verified. */
    public VerificationChallengeResponse startChallenge(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String challengeId = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(CHALLENGE_KEY + challengeId, user.getId().toString(), CHALLENGE_TTL);

        String phone = whatsAppNumber(user);
        List<VerificationChannel> channels = new ArrayList<>(List.of(VerificationChannel.EMAIL));
        if (phone != null) {
            channels.add(VerificationChannel.WHATSAPP);
        }
        return VerificationChallengeResponse.builder()
                .challengeId(challengeId)
                .expiresInSeconds(CHALLENGE_TTL.toSeconds())
                .channels(channels)
                .maskedEmail(maskEmail(user.getEmail()))
                .maskedPhone(phone != null ? maskPhone(phone) : null)
                .build();
    }

    /** Sends a fresh code over the chosen channel, replacing any earlier one. */
    public void sendCode(SendVerificationCodeRequest request) {
        User user = userForChallenge(request.getChallengeId());
        Long userId = user.getId();

        Boolean first = redis.opsForValue().setIfAbsent(COOLDOWN_KEY + userId, "1", RESEND_COOLDOWN);
        if (!Boolean.TRUE.equals(first)) {
            Long ttl = redis.getExpire(COOLDOWN_KEY + userId);
            throw new TooManyRequestsException("Please wait before requesting another code",
                    ttl != null && ttl > 0 ? ttl : RESEND_COOLDOWN.toSeconds());
        }

        try {
            if (request.getChannel() == VerificationChannel.WHATSAPP) {
                sendWhatsAppCode(user);
            } else {
                sendEmailCode(user);
            }
        } catch (RuntimeException ex) {
            // Nothing was delivered - don't make the user wait out the cooldown to retry.
            redis.delete(COOLDOWN_KEY + userId);
            throw ex;
        }
        // Keep the challenge alive long enough to use the code just sent.
        redis.expire(CHALLENGE_KEY + request.getChallengeId(), CHALLENGE_TTL);
    }

    /**
     * Checks the code and, when right, marks the account verified and consumes the
     * challenge. The caller issues tokens and must save the returned user.
     */
    public User confirm(ConfirmVerificationRequest request) {
        User user = userForChallenge(request.getChallengeId());
        String codeKey = CODE_KEY + user.getId();
        Map<Object, Object> pending = redis.opsForHash().entries(codeKey);
        if (pending.isEmpty()) {
            throw new BadRequestException("No active code - request a new one");
        }

        long attempts = redis.opsForHash().increment(codeKey, "attempts", 1);
        if (attempts > MAX_ATTEMPTS) {
            redis.delete(codeKey);
            throw new BadRequestException("Too many incorrect attempts - request a new code");
        }

        VerificationChannel channel = VerificationChannel.valueOf((String) pending.get("channel"));
        boolean correct = channel == VerificationChannel.WHATSAPP
                ? checkWhatsAppCode((String) pending.get("to"), request.getCode())
                : MessageDigest.isEqual(
                        hash(user.getId(), request.getCode()).getBytes(StandardCharsets.UTF_8),
                        ((String) pending.get("hash")).getBytes(StandardCharsets.UTF_8));
        if (!correct) {
            long left = MAX_ATTEMPTS - attempts;
            if (left <= 0) {
                redis.delete(codeKey);
            }
            throw new BadRequestException(left > 0
                    ? "Incorrect code - " + left + " attempt" + (left == 1 ? "" : "s") + " left"
                    : "Incorrect code - request a new one");
        }

        if (channel == VerificationChannel.WHATSAPP) {
            user.verifyPhone();
        } else {
            user.verifyEmail();
        }
        redis.delete(List.of(codeKey, CHALLENGE_KEY + request.getChallengeId(), COOLDOWN_KEY + user.getId()));
        log.info("Account {} verified by {}", user.getEmail(), channel);
        return user;
    }

    private void sendEmailCode(User user) {
        String code = newCode();
        storePendingCode(user.getId(), VerificationChannel.EMAIL, Map.of("hash", hash(user.getId(), code)));

        if (resendConfig.getApiKey() == null || resendConfig.getApiKey().isBlank()) {
            if (logCodesWhenEmailUnconfigured) {
                log.info("Email delivery not configured - verification code for {} is {}", user.getEmail(), code);
                return;
            }
            throw new ServiceUnavailableException("Email delivery is not configured");
        }
        try {
            resendEmailClient.send(fromAddress, user.getEmail(), "Your Aidly verification code",
                    "Your Aidly verification code is " + code + ".\n\n"
                            + "It expires in " + CODE_TTL.toMinutes() + " minutes. "
                            + "If you didn't try to sign in to Aidly, you can ignore this email.");
        } catch (Exception ex) {
            throw new ServiceUnavailableException("Couldn't send the verification email - please try again", ex);
        }
    }

    private void sendWhatsAppCode(User user) {
        String phone = whatsAppNumber(user);
        if (phone == null) {
            throw new BadRequestException("WhatsApp verification isn't available for this account - use email");
        }
        try {
            twilioVerifyClient.sendWhatsAppCode(phone);
        } catch (Exception ex) {
            throw new ServiceUnavailableException("Couldn't send the WhatsApp code - try email instead", ex);
        }
        storePendingCode(user.getId(), VerificationChannel.WHATSAPP, Map.of("to", phone));
    }

    private boolean checkWhatsAppCode(String phone, String code) {
        try {
            return twilioVerifyClient.checkCode(phone, code);
        } catch (Exception ex) {
            throw new ServiceUnavailableException("Couldn't check the WhatsApp code - please try again", ex);
        }
    }

    // A new code replaces the previous one (and its attempt count) for every channel.
    private void storePendingCode(Long userId, VerificationChannel channel, Map<String, String> fields) {
        String codeKey = CODE_KEY + userId;
        redis.delete(codeKey);
        redis.opsForHash().put(codeKey, "channel", channel.name());
        redis.opsForHash().put(codeKey, "attempts", "0");
        redis.opsForHash().putAll(codeKey, fields);
        redis.expire(codeKey, CODE_TTL);
    }

    private User userForChallenge(String challengeId) {
        String userId = redis.opsForValue().get(CHALLENGE_KEY + challengeId);
        if (userId == null) {
            throw new BadRequestException("Verification session expired - please log in again");
        }
        User user = userRepository.findById(Long.valueOf(userId))
                .orElseThrow(() -> new BadRequestException("Verification session expired - please log in again"));
        if (!user.isEnabled() || user.isDeleted()) {
            throw new BadRequestException("Verification session expired - please log in again");
        }
        return user;
    }

    /** The profile's phone number, when it can receive a WhatsApp code; null otherwise. */
    private String whatsAppNumber(User user) {
        if (!twilioConfig.isVerifyConfigured()) {
            return null;
        }
        String phone = studentProfileRepository.findByUserId(user.getId()).map(StudentProfile::getPhone)
                .or(() -> instructorProfileRepository.findByUserId(user.getId()).map(InstructorProfile::getPhone))
                .orElse(null);
        if (phone == null) {
            return null;
        }
        String normalized = phone.replaceAll("[\\s\\-().]", "");
        return E164.matcher(normalized).matches() ? normalized : null;
    }

    private String newCode() {
        return String.format("%0" + CODE_LENGTH + "d", random.nextInt((int) Math.pow(10, CODE_LENGTH)));
    }

    private static String hash(Long userId, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((userId + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    static String maskPhone(String phone) {
        return "+" + "*".repeat(phone.length() - 5) + phone.substring(phone.length() - 4);
    }
}
