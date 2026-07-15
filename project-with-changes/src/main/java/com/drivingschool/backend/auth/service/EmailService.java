package com.drivingschool.backend.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String resetUrlBase;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from}") String fromAddress,
                        @Value("${app.password-reset.reset-url}") String resetUrlBase) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.resetUrlBase = resetUrlBase;
    }

    /**
     * Sends a password reset email. Failures are logged, not propagated -
     * the caller (forgot-password) always reports success regardless of
     * delivery outcome, to avoid leaking account existence or mail-server
     * state to the caller.
     */
    public void sendPasswordResetEmail(String toEmail, String token, long validityMinutes) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("Reset your password");
            message.setText(
                    "We received a request to reset your password.\n\n"
                            + "Click the link below to choose a new one:\n"
                            + resetUrlBase + "?token=" + token + "\n\n"
                            + "This link expires in " + validityMinutes + " minutes. "
                            + "If you did not request this, you can safely ignore this email.");
            mailSender.send(message);
            log.info("Password reset email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }
}
