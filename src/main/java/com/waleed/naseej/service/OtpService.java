package com.waleed.naseej.service;

import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.OtpPurpose;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Email OTP stored on the users table (otpHash, otpPurpose, otpExpiresAt, otpAttempts).
 * One active code per user. Reusable by other features, e.g. CONTRACT_SIGN.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final N8nService n8nService;

    @Value("${otp.expiration-minutes}")
    private long expirationMinutes;

    @Value("${otp.max-attempts}")
    private int maxAttempts;

    /** Generates a 6-digit code, saves only its BCrypt hash and emails the plain code through n8n. */
    public void sendOtp(User user, OtpPurpose purpose) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        user.setOtpHash(passwordEncoder.encode(code));
        user.setOtpPurpose(purpose);
        user.setOtpExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));
        user.setOtpAttempts(0);
        userRepository.save(user);

        n8nService.sendOtpEmail(user.getEmail(), user.getFullName(), code, purpose, expirationMinutes);
    }

    /** Throws BadRequestException when the code is wrong, expired or locked. Clears the code on success. */
    public void verifyOtp(User user, OtpPurpose purpose, String code) {
        if (user.getOtpHash() == null || user.getOtpPurpose() != purpose) {
            throw new BadRequestException("No active code for this action, request a new one");
        }
        if (user.getOtpExpiresAt().isBefore(LocalDateTime.now())) {
            clearOtp(user);
            throw new BadRequestException("The code has expired, request a new one");
        }
        if (user.getOtpAttempts() >= maxAttempts) {
            clearOtp(user);
            throw new BadRequestException("Too many wrong attempts, request a new code");
        }
        if (!passwordEncoder.matches(code, user.getOtpHash())) {
            user.setOtpAttempts(user.getOtpAttempts() + 1);
            userRepository.save(user);
            int left = maxAttempts - user.getOtpAttempts();
            throw new BadRequestException("Wrong code, " + left + " attempt(s) left");
        }
        clearOtp(user);
    }

    private void clearOtp(User user) {
        user.setOtpHash(null);
        user.setOtpPurpose(null);
        user.setOtpExpiresAt(null);
        user.setOtpAttempts(0);
        userRepository.save(user);
    }
}