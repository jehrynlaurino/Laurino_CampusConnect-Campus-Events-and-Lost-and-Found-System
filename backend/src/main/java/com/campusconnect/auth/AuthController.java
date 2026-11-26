package com.campusconnect.auth;

import com.campusconnect.user.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService service;
    private final PasswordResetService passwordResets;
    public AuthController(AccountService service, PasswordResetService passwordResets) {
        this.service = service;
        this.passwordResets = passwordResets;
    }

    // This token is bound to the server-side session and must be sent in X-CSRF-TOKEN.
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(req));
    }

    @GetMapping("/me")
    public UserResponse me(Authentication auth) {
        return UserResponse.from(service.requireUser(auth.getName()));
    }

    @PutMapping("/profile")
    public UserResponse profile(Authentication auth, @Valid @RequestBody AuthDtos.ProfileRequest req) {
        return service.updateProfile(auth.getName(), req);
    }

    @PutMapping("/change-password")
    public Map<String, String> password(Authentication auth, @Valid @RequestBody AuthDtos.PasswordRequest req,
                                        HttpServletRequest request) {
        service.changePassword(auth.getName(), req);
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        return Map.of("message", "Password changed successfully.");
    }

    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest req) {
        passwordResets.requestReset(req.email());
        return Map.of("message", "If an account exists for this email, a password reset link will be sent.");
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest req) {
        passwordResets.resetPassword(req);
        return Map.of("message", "Password reset successfully. Please sign in with your new password.");
    }
}
