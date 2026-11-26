package com.campusconnect.auth;

import com.campusconnect.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PasswordResetRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int FORGOT_LIMIT = 3;
    private static final int RESET_LIMIT = 5;
    private final ConcurrentHashMap<String, ArrayDeque<Instant>> attempts = new ConcurrentHashMap<>();

    public void checkForgot(String normalizedEmail) { check("forgot:" + normalizedEmail, FORGOT_LIMIT); }
    public void checkReset(String tokenHash) { check("reset:" + tokenHash, RESET_LIMIT); }

    private void check(String key, int limit) {
        ArrayDeque<Instant> timestamps = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            Instant cutoff = Instant.now().minus(WINDOW);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) timestamps.removeFirst();
            if (timestamps.size() >= limit) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many password reset attempts. Please try again later.");
            }
            timestamps.addLast(Instant.now());
        }
    }
}
