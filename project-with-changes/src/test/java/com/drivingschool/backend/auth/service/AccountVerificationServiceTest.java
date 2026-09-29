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
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.sms.TwilioVerifyClient;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountVerificationServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    @Mock private HashOperations<String, Object, Object> hashes;
    @Mock private UserRepository userRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;
    @Mock private ResendEmailClient resendEmailClient;
    @Mock private TwilioVerifyClient twilioVerifyClient;

    private final ResendConfig resendConfig = new ResendConfig();
    private final TwilioConfig twilioConfig = new TwilioConfig();

    // A tiny in-memory Redis: plain values and hashes, expiry ignored.
    private final Map<String, String> store = new HashMap<>();
    private final Map<String, Map<Object, Object>> hashStore = new HashMap<>();

    private AccountVerificationService service;
    private User user;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(values);
        lenient().when(redis.<Object, Object>opsForHash()).thenReturn(hashes);
        lenient().doAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(1)))
                .when(values).set(anyString(), anyString(), any(Duration.class));
        lenient().when(values.get(anyString())).thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
        lenient().when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(inv -> store.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null);
        lenient().when(redis.delete(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return store.remove(key) != null | hashStore.remove(key) != null;
        });
        lenient().when(redis.delete(any(Collection.class))).thenAnswer(inv -> {
            ((Collection<String>) inv.getArgument(0)).forEach(k -> { store.remove(k); hashStore.remove(k); });
            return 1L;
        });
        lenient().doAnswer(inv -> hashStore.computeIfAbsent(inv.getArgument(0), k -> new HashMap<>())
                        .put(inv.getArgument(1), inv.getArgument(2)))
                .when(hashes).put(anyString(), any(), any());
        lenient().doAnswer(inv -> { hashStore.computeIfAbsent(inv.getArgument(0), k -> new HashMap<>())
                        .putAll(inv.getArgument(1)); return null; })
                .when(hashes).putAll(anyString(), anyMap());
        lenient().when(hashes.entries(anyString()))
                .thenAnswer(inv -> new HashMap<>(hashStore.getOrDefault(inv.<String>getArgument(0), Map.of())));
        lenient().when(hashes.increment(anyString(), any(), anyLong())).thenAnswer(inv -> {
            Map<Object, Object> hash = hashStore.computeIfAbsent(inv.getArgument(0), k -> new HashMap<>());
            long next = Long.parseLong((String) hash.getOrDefault(inv.getArgument(1), "0")) + inv.<Long>getArgument(2);
            hash.put(inv.getArgument(1), Long.toString(next));
            return next;
        });

        resendConfig.setApiKey("re_test");
        service = new AccountVerificationService(redis, userRepository, studentProfileRepository,
                instructorProfileRepository, resendEmailClient, resendConfig, twilioVerifyClient, twilioConfig,
                "Aidly <no-reply@aidly.test>", false);

        user = User.builder().email("sam@example.com").password("x").enabled(true).emailVerified(false).build();
        ReflectionTestUtils.setField(user, "id", 7L);
        lenient().when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    }

    private void enableWhatsApp(String phone) {
        twilioConfig.setAccountSid("AC1");
        twilioConfig.setAuthToken("token");
        twilioConfig.setVerifyServiceSid("VA1");
        StudentProfile profile = StudentProfile.builder().firstName("Sam").lastName("S").phone(phone).build();
        when(studentProfileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
    }

    private String emailedCode() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(resendEmailClient).send(anyString(), eq("sam@example.com"), anyString(), body.capture());
        Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(body.getValue());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private ConfirmVerificationRequest confirm(String challengeId, String code) {
        return ConfirmVerificationRequest.builder().challengeId(challengeId).code(code).build();
    }

    private SendVerificationCodeRequest send(String challengeId, VerificationChannel channel) {
        return SendVerificationCodeRequest.builder().challengeId(challengeId).channel(channel).build();
    }

    @Test
    void challenge_offersEmailOnly_whenWhatsAppIsNotConfigured() {
        VerificationChallengeResponse challenge = service.startChallenge(user);

        assertThat(challenge.getChallengeId()).hasSizeGreaterThan(30);
        assertThat(challenge.getChannels()).containsExactly(VerificationChannel.EMAIL);
        assertThat(challenge.getMaskedEmail()).isEqualTo("s***@example.com");
        assertThat(challenge.getMaskedPhone()).isNull();
    }

    @Test
    void challenge_offersWhatsApp_forAnE164NumberOnTheProfile_masked() {
        enableWhatsApp("+233 24 123 4567");

        VerificationChallengeResponse challenge = service.startChallenge(user);

        assertThat(challenge.getChannels()).containsExactly(VerificationChannel.EMAIL, VerificationChannel.WHATSAPP);
        assertThat(challenge.getMaskedPhone()).isEqualTo("+********4567");
    }

    @Test
    void challenge_doesNotOfferWhatsApp_forANumberWithoutCountryCode() {
        enableWhatsApp("0241234567");

        assertThat(service.startChallenge(user).getChannels()).containsExactly(VerificationChannel.EMAIL);
    }

    @Test
    void emailCode_confirmed_verifiesTheEmail_andConsumesTheChallenge() {
        String challengeId = service.startChallenge(user).getChallengeId();
        service.sendCode(send(challengeId, VerificationChannel.EMAIL));

        User verified = service.confirm(confirm(challengeId, emailedCode()));

        assertThat(verified.isEmailVerified()).isTrue();
        assertThat(verified.isAccountVerified()).isTrue();
        assertThatThrownBy(() -> service.confirm(confirm(challengeId, "000000")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("log in again");
    }

    @Test
    void wrongCode_countsDown_andTheFifthMissKillsTheCode() {
        String challengeId = service.startChallenge(user).getChallengeId();
        service.sendCode(send(challengeId, VerificationChannel.EMAIL));
        String right = emailedCode();
        String wrong = right.equals("111111") ? "222222" : "111111";

        assertThatThrownBy(() -> service.confirm(confirm(challengeId, wrong))).hasMessage("Incorrect code - 4 attempts left");
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.confirm(confirm(challengeId, wrong))).isInstanceOf(BadRequestException.class);
        }
        assertThatThrownBy(() -> service.confirm(confirm(challengeId, wrong))).hasMessage("Incorrect code - request a new one");
        // Even the right code is useless now.
        assertThatThrownBy(() -> service.confirm(confirm(challengeId, right))).hasMessage("No active code - request a new one");
        assertThat(user.isAccountVerified()).isFalse();
    }

    @Test
    void resendWithinTheCooldown_isRejectedWith429() {
        String challengeId = service.startChallenge(user).getChallengeId();
        service.sendCode(send(challengeId, VerificationChannel.EMAIL));
        when(redis.getExpire(anyString())).thenReturn(42L);

        assertThatThrownBy(() -> service.sendCode(send(challengeId, VerificationChannel.EMAIL)))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(ex -> assertThat(((TooManyRequestsException) ex).getRetryAfterSeconds()).isEqualTo(42L));
    }

    @Test
    void failedEmailDelivery_is503_andDoesNotStartTheCooldown() {
        String challengeId = service.startChallenge(user).getChallengeId();
        doThrow(new RuntimeException("resend down")).when(resendEmailClient).send(anyString(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.sendCode(send(challengeId, VerificationChannel.EMAIL)))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(store).doesNotContainKey("verify:cooldown:7");
    }

    @Test
    void unconfiguredEmail_is503_unlessDevCodeLoggingIsOn() {
        resendConfig.setApiKey("");
        String challengeId = service.startChallenge(user).getChallengeId();

        assertThatThrownBy(() -> service.sendCode(send(challengeId, VerificationChannel.EMAIL)))
                .isInstanceOf(ServiceUnavailableException.class).hasMessage("Email delivery is not configured");

        AccountVerificationService devService = new AccountVerificationService(redis, userRepository,
                studentProfileRepository, instructorProfileRepository, resendEmailClient, resendConfig,
                twilioVerifyClient, twilioConfig, "from", true);
        devService.sendCode(send(challengeId, VerificationChannel.EMAIL));
        verify(resendEmailClient, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void whatsAppCode_isSentAndCheckedByTwilioVerify_andVerifiesThePhone() {
        enableWhatsApp("+233241234567");
        String challengeId = service.startChallenge(user).getChallengeId();
        service.sendCode(send(challengeId, VerificationChannel.WHATSAPP));
        verify(twilioVerifyClient).sendWhatsAppCode("+233241234567");
        when(twilioVerifyClient.checkCode("+233241234567", "123456")).thenReturn(true);

        User verified = service.confirm(confirm(challengeId, "123456"));

        assertThat(verified.isPhoneVerified()).isTrue();
        assertThat(verified.isEmailVerified()).isFalse();
        assertThat(verified.isAccountVerified()).isTrue();
    }

    @Test
    void whatsApp_isRefused_whenTheAccountHasNoUsableNumber() {
        String challengeId = service.startChallenge(user).getChallengeId();

        assertThatThrownBy(() -> service.sendCode(send(challengeId, VerificationChannel.WHATSAPP)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("use email");
        verify(twilioVerifyClient, never()).sendWhatsAppCode(anyString());
    }

    @Test
    void unknownOrExpiredChallenge_isRejected() {
        assertThatThrownBy(() -> service.sendCode(send("nope", VerificationChannel.EMAIL)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("log in again");
    }

    @Test
    void aDisabledAccount_cannotUseItsChallenge() {
        String challengeId = service.startChallenge(user).getChallengeId();
        user.softDelete();

        assertThatThrownBy(() -> service.sendCode(send(challengeId, VerificationChannel.EMAIL)))
                .isInstanceOf(BadRequestException.class);
    }
}
