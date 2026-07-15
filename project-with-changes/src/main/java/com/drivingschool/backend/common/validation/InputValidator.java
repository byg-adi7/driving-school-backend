package com.drivingschool.backend.common.validation;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Input validation and sanitization utility for production security
 */
@Component
public class InputValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$"
    );

    private static final Pattern ALPHA_NUMERIC_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9\\s\\-_.]*$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?[1-9]\\d{1,14}$"
    );

    private static final int MAX_STRING_LENGTH = 500;
    private static final int MAX_TEXT_LENGTH = 5000;

    /**
     * Validate email format
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.isEmpty() || email.length() > MAX_STRING_LENGTH) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Validate password strength
     * Requirements: min 8 chars, at least 1 uppercase, 1 lowercase, 1 digit, 1 special char
     */
    public boolean isValidPassword(String password) {
        if (password == null || password.isEmpty()) {
            return false;
        }
        return PASSWORD_PATTERN.matcher(password).matches();
    }

    /**
     * Validate phone number
     */
    public boolean isValidPhoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * Validate alphanumeric string (names, titles, etc.)
     */
    public boolean isValidAlphaNumeric(String input) {
        if (input == null || input.isEmpty() || input.length() > MAX_STRING_LENGTH) {
            return false;
        }
        return ALPHA_NUMERIC_PATTERN.matcher(input).matches();
    }

    /**
     * Sanitize string input - remove potentially harmful characters
     */
    public String sanitizeString(String input) {
        if (input == null) {
            return null;
        }
        return input.trim()
                .replaceAll("[<>\"'%;()&+]", "")
                .substring(0, Math.min(input.length(), MAX_STRING_LENGTH));
    }

    /**
     * Validate and sanitize text input
     */
    public String sanitizeText(String input) {
        if (input == null) {
            return null;
        }
        String sanitized = input.trim();
        if (sanitized.length() > MAX_TEXT_LENGTH) {
            sanitized = sanitized.substring(0, MAX_TEXT_LENGTH);
        }
        return sanitized;
    }

    /**
     * Check if string contains SQL injection patterns
     */
    public boolean containsSqlInjectionPatterns(String input) {
        if (input == null) {
            return false;
        }
        String lowerInput = input.toLowerCase();
        return lowerInput.contains("union")
                || lowerInput.contains("select")
                || lowerInput.contains("insert")
                || lowerInput.contains("update")
                || lowerInput.contains("delete")
                || lowerInput.contains("drop")
                || lowerInput.contains("exec")
                || lowerInput.contains(";");
    }

    /**
     * Validate URL format
     */
    public boolean isValidUrl(String url) {
        if (url == null || url.isEmpty() || url.length() > MAX_STRING_LENGTH) {
            return false;
        }
        try {
            new java.net.URL(url);
            return url.startsWith("https://") || url.startsWith("http://");
        } catch (java.net.MalformedURLException e) {
            return false;
        }
    }

    /**
     * Validate string length
     */
    public boolean isValidLength(String input, int minLength, int maxLength) {
        if (input == null) {
            return minLength == 0;
        }
        int length = input.length();
        return length >= minLength && length <= maxLength;
    }
}
