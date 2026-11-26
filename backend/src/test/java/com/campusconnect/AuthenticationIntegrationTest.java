package com.campusconnect;

import com.campusconnect.auth.PasswordResetTokenRepository;
import com.campusconnect.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired PasswordResetTokenRepository resetTokens;
    @MockBean JavaMailSender mail;

    @BeforeEach
    void clean() {
        resetTokens.deleteAll();
        users.deleteAll();
        reset(mail);
    }

    private void register(String email) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                .contentType("application/json")
                .content("{\"fullName\":\"Jamie Student\",\"email\":\"" + email + "\",\"password\":\"StrongPass123!\",\"confirmPassword\":\"StrongPass123!\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                .param("email", email).param("password", password))
            .andExpect(status().isOk())
            .andReturn();
        return (MockHttpSession)result.getRequest().getSession(false);
    }

    private String latestResetToken() {
        ArgumentCaptor<SimpleMailMessage> messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, atLeastOnce()).send(messages.capture());
        List<SimpleMailMessage> sent = messages.getAllValues();
        String body = sent.get(sent.size() - 1).getText();
        return body.substring(body.indexOf("token=") + 6).split("\\s")[0];
    }

    @Test
    void registrationPersistsHashedPasswordAndRejectsDuplicates() throws Exception {
        register("student@example.edu");
        AppUser u = users.findByEmail("student@example.edu").orElseThrow();
        assertNotEquals("StrongPass123!", u.getPasswordHash());
        assertTrue(passwords.matches("StrongPass123!", u.getPasswordHash()));
        assertEquals(Role.MEMBER, u.getRole());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content("{\"fullName\":\"Jamie Student\",\"email\":\"student@example.edu\",\"password\":\"StrongPass123!\",\"confirmPassword\":\"StrongPass123!\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingInputAndInvalidLogin() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content("{\"fullName\":\"\",\"email\":\"bad\",\"password\":\"123\",\"confirmPassword\":\"123\"}"))
            .andExpect(status().isBadRequest());
        register("member@example.edu");
        mvc.perform(post("/api/auth/login").with(csrf()).param("email", "member@example.edu").param("password", "wrong"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWeakAndMismatchedPasswordsAndNormalizesLoginEmail() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content("{\"fullName\":\"Jamie Student\",\"email\":\"student@example.edu\",\"password\":\"alllowercase123!\",\"confirmPassword\":\"alllowercase123!\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content("{\"fullName\":\"Jamie Student\",\"email\":\"student@example.edu\",\"password\":\"StrongPass123!\",\"confirmPassword\":\"DifferentPass123!\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content("{\"fullName\":\"Jamie Student\",\"email\":\"privileged@example.edu\",\"password\":\"StrongPass123!\",\"confirmPassword\":\"StrongPass123!\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isBadRequest());
        register("student@example.edu");
        mvc.perform(post("/api/auth/login").with(csrf())
                .param("email", "STUDENT@EXAMPLE.EDU").param("password", "StrongPass123!"))
            .andExpect(status().isOk());
    }

    @Test
    void unauthorizedRoleAndLogoutAreEnforcedByServer() throws Exception {
        register("member@example.edu");
        mvc.perform(get("/api/dashboard/member")).andExpect(status().isUnauthorized());
        MockHttpSession session = login("member@example.edu", "StrongPass123!");
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("member@example.edu"));
        mvc.perform(get("/api/dashboard/member").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/admin").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void administratorCanPromoteButMemberCannot() throws Exception {
        register("member@example.edu");
        MockHttpSession memberSession = login("member@example.edu", "StrongPass123!");
        AppUser admin = users.save(new AppUser("Campus Admin", "admin@example.edu", passwords.encode("AdminPassword123!"), Role.ADMIN));
        MockHttpSession adminSession = login("admin@example.edu", "AdminPassword123!");
        Long memberId = users.findByEmail("member@example.edu").orElseThrow().getId();
        mvc.perform(patch("/api/admin/users/{id}/role", memberId).with(csrf()).session(adminSession)
                .contentType("application/json").content("{\"role\":\"ORGANIZER\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ORGANIZER"));
        assertEquals(Role.ORGANIZER, users.findByEmail("member@example.edu").orElseThrow().getRole());
        // The database filter updates an already-established session before authorization.
        mvc.perform(get("/api/dashboard/organizer").session(memberSession)).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/admin").session(memberSession)).andExpect(status().isForbidden());
        assertEquals(Role.ADMIN, users.findByEmail("admin@example.edu").orElseThrow().getRole());
    }

    @Test
    void deactivatedAccountLosesItsExistingSession() throws Exception {
        register("member@example.edu");
        users.save(new AppUser("Campus Admin", "admin@example.edu", passwords.encode("AdminPassword123!"), Role.ADMIN));
        MockHttpSession memberSession = login("member@example.edu", "StrongPass123!");
        MockHttpSession adminSession = login("admin@example.edu", "AdminPassword123!");
        Long memberId = users.findByEmail("member@example.edu").orElseThrow().getId();
        mvc.perform(patch("/api/admin/users/{id}/status", memberId).with(csrf()).session(adminSession)
                .contentType("application/json").content("{\"active\":false}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/member").session(memberSession)).andExpect(status().isUnauthorized());
    }

    @Test
    void profileIsOwnedByAuthenticatedUserAndPersists() throws Exception {
        register("profile@example.edu");
        MockHttpSession session = login("profile@example.edu", "StrongPass123!");
        mvc.perform(put("/api/auth/profile").session(session).with(csrf())
                .contentType("application/json").content("{\"fullName\":\"Updated Student\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Updated Student"));
        mvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Updated Student"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
        assertEquals("Updated Student", users.findByEmail("profile@example.edu").orElseThrow().getFullName());
    }

    @Test
    void changePasswordInvalidatesSessionAndRejectsOldPassword() throws Exception {
        register("change@example.edu");
        MockHttpSession session = login("change@example.edu", "StrongPass123!");
        MockHttpSession otherSession = login("change@example.edu", "StrongPass123!");
        mvc.perform(put("/api/auth/change-password").session(session).with(csrf())
                .contentType("application/json")
                .content("{\"currentPassword\":\"StrongPass123!\",\"newPassword\":\"ChangedPass123!\",\"confirmNewPassword\":\"ChangedPass123!\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(otherSession)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).param("email", "change@example.edu").param("password", "StrongPass123!"))
            .andExpect(status().isUnauthorized());
        login("change@example.edu", "ChangedPass123!");
        assertTrue(passwords.matches("ChangedPass123!", users.findByEmail("change@example.edu").orElseThrow().getPasswordHash()));
    }

    @Test
    void resetLinkIsSingleUseAndReplacesThePassword() throws Exception {
        register("reset@example.edu");
        MockHttpSession existingSession = login("reset@example.edu", "StrongPass123!");
        mvc.perform(post("/api/auth/forgot-password").with(csrf()).contentType("application/json")
                .content("{\"email\":\"reset@example.edu\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.message").value(containsString("If an account exists")));
        String rawToken = latestResetToken();
        assertNotEquals(rawToken, resetTokens.findAll().get(0).getTokenHash());
        mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType("application/json")
                .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"ResetPass123!\",\"confirmNewPassword\":\"ResetPass123!\"}"))
            .andExpect(status().isOk());
        assertEquals(1L, users.findByEmail("reset@example.edu").orElseThrow().getCredentialVersion());
        mvc.perform(post("/api/auth/login").with(csrf()).param("email", "reset@example.edu").param("password", "StrongPass123!"))
            .andExpect(status().isUnauthorized());
        login("reset@example.edu", "ResetPass123!");
        mvc.perform(get("/api/auth/me").session(existingSession)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType("application/json")
                .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"AnotherPass123!\",\"confirmNewPassword\":\"AnotherPass123!\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unknownAndExcessiveForgotPasswordRequestsAreProtected() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/forgot-password").with(csrf()).contentType("application/json")
                    .content("{\"email\":\"nobody@example.edu\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value(containsString("If an account exists")));
        }
        mvc.perform(post("/api/auth/forgot-password").with(csrf()).contentType("application/json")
                .content("{\"email\":\"nobody@example.edu\"}"))
            .andExpect(status().isTooManyRequests());
        verifyNoInteractions(mail);
    }

    @Test
    void resetLinksExpireAndNewRequestsInvalidateOlderLinks() throws Exception {
        register("replace@example.edu");
        mvc.perform(post("/api/auth/forgot-password").with(csrf()).contentType("application/json")
                .content("{\"email\":\"replace@example.edu\"}"))
            .andExpect(status().isOk());
        String firstToken = latestResetToken();
        mvc.perform(post("/api/auth/forgot-password").with(csrf()).contentType("application/json")
                .content("{\"email\":\"replace@example.edu\"}"))
            .andExpect(status().isOk());
        String secondToken = latestResetToken();
        mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType("application/json")
                .content("{\"token\":\"" + firstToken + "\",\"newPassword\":\"ReplacementPass123!\",\"confirmNewPassword\":\"ReplacementPass123!\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType("application/json")
                .content("{\"token\":\"" + secondToken + "\",\"newPassword\":\"ReplacementPass123!\",\"confirmNewPassword\":\"ReplacementPass123!\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void expiredAndExcessiveResetAttemptsAreRejected() throws Exception {
        register("expired@example.edu");
        mvc.perform(post("/api/auth/forgot-password").with(csrf()).contentType("application/json")
                .content("{\"email\":\"expired@example.edu\"}"))
            .andExpect(status().isOk());
        String expiredToken = latestResetToken();
        var savedToken = resetTokens.findAll().get(0);
        savedToken.setExpiresAt(java.time.Instant.now().minusSeconds(1));
        resetTokens.save(savedToken);
        mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType("application/json")
                .content("{\"token\":\"" + expiredToken + "\",\"newPassword\":\"ExpiredPass123!\",\"confirmNewPassword\":\"ExpiredPass123!\"}"))
            .andExpect(status().isBadRequest());
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType("application/json")
                    .content("{\"token\":\"rate-limit-token\",\"newPassword\":\"RateLimitPass123!\",\"confirmNewPassword\":\"RateLimitPass123!\"}"))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType("application/json")
                .content("{\"token\":\"rate-limit-token\",\"newPassword\":\"RateLimitPass123!\",\"confirmNewPassword\":\"RateLimitPass123!\"}"))
            .andExpect(status().isTooManyRequests());
    }
}
