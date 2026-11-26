package com.campusconnect.config;

import com.campusconnect.user.AppUser;
import com.campusconnect.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Re-check account state/role every request so deactivation and demotion cannot
 * leave a stale authenticated session with privileges from an old login.
 */
public class DatabaseAccountFilter extends OncePerRequestFilter {
    public static final String CREDENTIAL_VERSION_ATTRIBUTE = "CAMPUSCONNECT_CREDENTIAL_VERSION";
    private final UserRepository users;
    public DatabaseAccountFilter(UserRepository users) { this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken)) {
            Optional<AppUser> existing = users.findByEmail(a.getName());
            if (existing.isEmpty() || !existing.get().isActive()) {
                if (request.getSession(false) != null) request.getSession(false).invalidate();
                SecurityContextHolder.clearContext();
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"This account is no longer active.\"}");
                return;
            }
            HttpSession session = request.getSession(false);
            Object storedVersion = session == null ? null : session.getAttribute(CREDENTIAL_VERSION_ATTRIBUTE);
            long sessionVersion = storedVersion instanceof Number version ? version.longValue() : 0L;
            if (sessionVersion != existing.get().getCredentialVersion()) {
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Please sign in again.\"}");
                return;
            }
            String authority = "ROLE_" + existing.get().getRole().name();
            boolean matchesRole = a.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals(authority))
                    && a.getAuthorities().size() == 1;
            if (!matchesRole) {
                UsernamePasswordAuthenticationToken updated = new UsernamePasswordAuthenticationToken(a.getPrincipal(), null,
                    List.of(new SimpleGrantedAuthority(authority)));
                updated.setDetails(a.getDetails());
                SecurityContextHolder.getContext().setAuthentication(updated);
            }
        }
        chain.doFilter(request, response);
    }
}
