package com.campusconnect.auth;

import jakarta.validation.constraints.*;

public final class AuthDtos {
    private AuthDtos() {}

    private static final String STRONG_PASSWORD =
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,72}$";
    private static final String STRONG_PASSWORD_MESSAGE =
        "Password must be 10-72 characters and include uppercase, lowercase, number, and symbol.";

    public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 10, max = 72) @Pattern(regexp = STRONG_PASSWORD, message = STRONG_PASSWORD_MESSAGE) String password,
        @NotBlank String confirmPassword) {
        @Override public String toString() {
            return "RegisterRequest[fullName=" + fullName + ", email=" + email + ", password=[redacted], confirmPassword=[redacted]]";
        }
    }

    public record ProfileRequest(@NotBlank @Size(min = 2, max = 120) String fullName) {}

    public record PasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 10, max = 72) @Pattern(regexp = STRONG_PASSWORD, message = STRONG_PASSWORD_MESSAGE) String newPassword,
        @NotBlank String confirmNewPassword) {
        @Override public String toString() {
            return "PasswordRequest[currentPassword=[redacted], newPassword=[redacted], confirmNewPassword=[redacted]]";
        }
    }

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record ResetPasswordRequest(
        @NotBlank @Size(max = 512) String token,
        @NotBlank @Size(min = 10, max = 72) @Pattern(regexp = STRONG_PASSWORD, message = STRONG_PASSWORD_MESSAGE) String newPassword,
        @NotBlank String confirmNewPassword) {
        @Override public String toString() {
            return "ResetPasswordRequest[token=[redacted], newPassword=[redacted], confirmNewPassword=[redacted]]";
        }
    }
}
