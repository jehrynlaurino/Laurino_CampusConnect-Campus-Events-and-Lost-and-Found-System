package com.campusconnect.auth;

import com.campusconnect.common.ApiException;
import com.campusconnect.user.AppUser;
import com.campusconnect.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

@Service
public class PasswordResetService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordResetEmailService email;
    private final PasswordResetRateLimiter limiter;
    private final PasswordEncoder passwords;
    private final SessionInvalidationService sessions;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
            PasswordResetEmailService email, PasswordResetRateLimiter limiter, PasswordEncoder passwords,
            SessionInvalidationService sessions) {
        this.users = users;
        this.tokens = tokens;
        this.email = email;
        this.limiter = limiter;
        this.passwords = passwords;
        this.sessions = sessions;
    }

    @Transactional
    public void requestReset(String submittedEmail) {
        String emailAddress = submittedEmail.strip().toLowerCase(Locale.ROOT);
        limiter.checkForgot(emailAddress);
        AppUser user = users.findByEmail(emailAddress).filter(AppUser::isActive).orElse(null);
        if (user == null) return; // The controller always returns the same generic response.

        byte[] bytes = new byte[32]; // 256 bits of entropy.
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        tokens.invalidateActiveByUserId(user.getId(), now);
        tokens.save(new PasswordResetToken(user, hash(rawToken), now.plus(TOKEN_LIFETIME)));
        email.send(user, rawToken);
    }

    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password confirmation does not match.");
        }
        String tokenHash = hash(request.token());
        limiter.checkReset(tokenHash);
        PasswordResetToken token = tokens.findByTokenHash(tokenHash).orElseThrow(this::invalidToken);
        AppUser user = token.getUser();
        Instant now = Instant.now();
        if (tokens.consumeIfActive(token.getId(), now, now) != 1) throw invalidToken();

        user.setPasswordHash(passwords.encode(request.newPassword()));
        user.bumpCredentialVersion();
        users.save(user);
        tokens.invalidateActiveByUserId(user.getId(), now);
        sessions.invalidateUserSessions(user.getEmail());
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "This password reset link is invalid or has expired.");
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
