package com.campusconnect.config;

import com.campusconnect.user.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import java.util.Locale;
import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    public SessionRegistry sessionRegistry() { return new SessionRegistryImpl(); }

    @Bean
    public ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository repo) {
        return email -> repo.findByEmail(email.strip().toLowerCase(Locale.ROOT)).map(u -> User.builder()
            .username(u.getEmail())
            .password(u.getPasswordHash())
            .roles(u.getRole().name())
            .disabled(!u.isActive())
            .build()).orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users, ObjectMapper json,
            SessionRegistry sessionRegistry,
            @Value("${app.security.require-https:false}") boolean requireHttps)
            throws Exception {
        HttpSessionCsrfTokenRepository csrf = new HttpSessionCsrfTokenRepository();
        csrf.setHeaderName("X-CSRF-TOKEN");
        http
          .csrf(cs -> cs.csrfTokenRepository(csrf))
          .sessionManagement(session -> session
              .sessionFixation(fixation -> fixation.migrateSession())
              .maximumSessions(-1)
              .sessionRegistry(sessionRegistry)
              .expiredSessionStrategy(event -> {
                  HttpServletResponse response = event.getResponse();
                  response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                  response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                  json.writeValue(response.getWriter(), Map.of("error", "Please sign in again."));
              }))
          .addFilterBefore(new DatabaseAccountFilter(users), AuthorizationFilter.class)
          .authorizeHttpRequests(a -> a
              .requestMatchers("/api/auth/csrf", "/api/auth/register", "/api/auth/login", "/api/auth/forgot-password", "/api/auth/reset-password", "/api/health").permitAll()
              .requestMatchers("/error").permitAll()
              .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/events", "/api/reports",
                  "/api/events/{id:[0-9]+}", "/api/reports/{id:[0-9]+}", "/api/campus/map").permitAll()
              .anyRequest().authenticated())
          .httpBasic(h -> h.disable())
          .formLogin(f -> f
              .loginProcessingUrl("/api/auth/login")
              .usernameParameter("email")
              .passwordParameter("password")
              .successHandler((request, response, auth) -> {
                  AppUser u = users.findByEmail(auth.getName()).orElseThrow();
                  request.getSession(true).setAttribute(DatabaseAccountFilter.CREDENTIAL_VERSION_ATTRIBUTE,
                      u.getCredentialVersion());
                  response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                  json.writeValue(response.getWriter(), UserResponse.from(u));
              })
              .failureHandler((request, response, error) -> {
                  response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                  response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                  json.writeValue(response.getWriter(), Map.of("error", "Invalid email or password, or inactive account."));
              }))
          .logout(l -> l
              .logoutUrl("/api/auth/logout")
              .invalidateHttpSession(true)
              .clearAuthentication(true)
              .deleteCookies("JSESSIONID")
              .logoutSuccessHandler((request, response, auth) -> {
                  response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                  json.writeValue(response.getWriter(), Map.of("message", "You are logged out."));
              }))
          .exceptionHandling(e -> e
              .authenticationEntryPoint((request, response, ex) -> {
                  response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                  response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                  json.writeValue(response.getWriter(), Map.of("error", "Please sign in to continue."));
              })
              .accessDeniedHandler((request, response, ex) -> {
                  response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                  response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                  json.writeValue(response.getWriter(), Map.of("error", "You do not have permission to access this resource."));
              }));
        if (requireHttps) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }
        return http.build();
    }
}
