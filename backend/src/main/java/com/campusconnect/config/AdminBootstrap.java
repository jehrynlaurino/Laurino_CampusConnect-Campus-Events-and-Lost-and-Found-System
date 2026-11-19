package com.campusconnect.config;

import com.campusconnect.user.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Locale;

@Configuration
public class AdminBootstrap {
    @Bean
    ApplicationRunner initialAdministrator(UserRepository users, PasswordEncoder passwords,
        @Value("${app.bootstrap-admin.email:}") String email,
        @Value("${app.bootstrap-admin.password:}") String password,
        @Value("${app.bootstrap-admin.name:Campus Administrator}") String name) {
        return args -> {
            if (email.isBlank() && password.isBlank()) return;
            if (email.isBlank() || password.length() < 10) {
                throw new IllegalStateException("Bootstrap admin requires a valid APP_ADMIN_EMAIL and APP_ADMIN_PASSWORD (10+ chars)");
            }
            String normalized = email.strip().toLowerCase(Locale.ROOT);
            // Never promote an existing account automatically: admin creation is first-run only.
            if (users.existsByEmail(normalized)) return;
            users.save(new AppUser(name.strip(), normalized, passwords.encode(password), Role.ADMIN));
            System.out.println("First-run administrator account created. Credentials are not logged.");
        };
    }
}
