package com.campusconnect.auth;

import com.campusconnect.user.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class PasswordResetEmailService {
    private final JavaMailSender mail;
    private final String frontendUrl;
    private final String from;

    public PasswordResetEmailService(JavaMailSender mail,
            @Value("${app.password-reset.frontend-url}") String frontendUrl,
            @Value("${app.password-reset.from}") String from) {
        this.mail = mail;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.from = from;
    }

    public void send(AppUser user, String rawToken) {
        String resetUrl = frontendUrl + "/reset-password?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("CampusConnect password reset");
        message.setText("CampusConnect\n\nHello " + user.getFullName() + ",\n\n"
            + "We received a request to reset your CampusConnect password. Use this link within 15 minutes:\n"
            + resetUrl + "\n\nIf you did not request a password reset, you can safely ignore this email. "
            + "Your password will remain unchanged.");
        mail.send(message);
    }
}
