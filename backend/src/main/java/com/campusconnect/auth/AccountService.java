package com.campusconnect.auth;

import com.campusconnect.common.ApiException;
import com.campusconnect.user.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
public class AccountService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String allowedDomain;

    public AccountService(UserRepository users, PasswordEncoder passwords,
                          @Value("${app.allowed-email-domain:}") String allowedDomain) {
        this.users = users;
        this.passwords = passwords;
        this.allowedDomain = allowedDomain.strip().toLowerCase(Locale.ROOT).replaceFirst("^@", "");
    }

    @Transactional
    public UserResponse register(AuthDtos.RegisterRequest req) {
        if (!req.password().equals(req.confirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password confirmation does not match.");
        }
        String email = req.email().strip().toLowerCase(Locale.ROOT);
        if (!allowedDomain.isBlank() && !email.endsWith("@" + allowedDomain)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use your approved campus email address.");
        }
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "This email address is already registered.");
        }
        AppUser user = users.save(new AppUser(req.fullName().strip(), email,
            passwords.encode(req.password()), Role.MEMBER));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public AppUser requireUser(String email) {
        AppUser user = users.findByEmail(email).orElseThrow(() ->
            new ApiException(HttpStatus.UNAUTHORIZED, "Please log in."));
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account has been deactivated.");
        }
        return user;
    }

    @Transactional
    public UserResponse updateProfile(String email, AuthDtos.ProfileRequest req) {
        AppUser user = requireUser(email);
        user.setFullName(req.fullName().strip());
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public void changePassword(String email, AuthDtos.PasswordRequest req) {
        AppUser user = requireUser(email);
        if (!req.newPassword().equals(req.confirmNewPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password confirmation does not match.");
        }
        if (!passwords.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use a different new password.");
        }
        user.setPasswordHash(passwords.encode(req.newPassword()));
        users.save(user);
    }
}
