package com.campusconnect;

import com.campusconnect.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;

    @BeforeEach
    void clean() { users.deleteAll(); }

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
}
