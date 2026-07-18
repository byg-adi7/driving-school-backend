package com.drivingschool.backend.auth.service;

import com.drivingschool.backend.email.ResendEmailClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final ResendEmailClient resendEmailClient;
    private final String fromAddress;
    private final String resetUrlBase;

    public EmailService(ResendEmailClient resendEmailClient,
                        @Value("${app.mail.from}") String fromAddress,
                        @Value("${app.password-reset.reset-url}") String resetUrlBase) {
        this.resendEmailClient = resendEmailClient;
        this.fromAddress = fromAddress;
        this.resetUrlBase = resetUrlBase;
    }

    /**
     * Sends a password reset email. Failures are logged, not propagated -
     * the caller (forgot-password) always reports success regardless of
     * delivery outcome, to avoid leaking account existence or mail-server
     * state to the caller. Runs off the request thread for the same reason:
     * forgot-password should respond immediately regardless of how slow (or
     * unreachable) the mail server is.
     */
    @Async("notificationExecutor")
    public void sendPasswordResetEmail(String toEmail, String token, long validityMinutes) {
        try {
            String body = "We received a request to reset your password.\n\n"
                    + "Click the link below to choose a new one:\n"
                    + resetUrlBase + "?token=" + token + "\n\n"
                    + "This link expires in " + validityMinutes + " minutes. "
                    + "If you did not request this, you can safely ignore this email.";
            resendEmailClient.send(fromAddress, toEmail, "Reset your password", body);
            log.info("Password reset email sent to {}", toEmail);
        } catch (Exception e) {
            // Pass the exception itself, not just its message: SLF4J attaches
            // a Throwable to the log event only when it's the last argument,
            // which is what carries the full stack trace to log aggregators
            // and Sentry's logging integration (which builds an exception
            // event from that attached Throwable, not from the message text).
            log.error("Failed to send password reset email to {}", toEmail, e);
        }
    }
}
