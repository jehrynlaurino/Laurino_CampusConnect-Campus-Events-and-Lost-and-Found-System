package com.campusconnect.auth;

import com.campusconnect.user.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService service;
    public AuthController(AccountService service) { this.service = service; }

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
    public Map<String, String> password(Authentication auth, @Valid @RequestBody AuthDtos.PasswordRequest req) {
        service.changePassword(auth.getName(), req);
        return Map.of("message", "Password changed successfully.");
    }
}
