package com.campusconnect.dashboard;

import com.campusconnect.auth.AccountService;
import com.campusconnect.user.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final AccountService accounts;
    public DashboardController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping("/member")
    @PreAuthorize("hasRole('MEMBER')")
    public Map<String, Object> member(Authentication auth) {
        AppUser u = accounts.requireUser(auth.getName());
        return Map.of("heading", "Member dashboard", "account", UserResponse.from(u),
            "permissions", List.of("Manage own account", "Future: browse events", "Future: report lost or found items"));
    }

    @GetMapping("/organizer")
    @PreAuthorize("hasRole('ORGANIZER')")
    public Map<String, Object> organizer(Authentication auth) {
        AppUser u = accounts.requireUser(auth.getName());
        return Map.of("heading", "Organizer dashboard", "account", UserResponse.from(u),
            "permissions", List.of("Manage own account", "Future: manage campus events", "Future: view event registrations"));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> admin(Authentication auth) {
        AppUser u = accounts.requireUser(auth.getName());
        return Map.of("heading", "Administrator dashboard", "account", UserResponse.from(u),
            "permissions", List.of("Manage own account", "View users", "Assign roles", "Activate or deactivate accounts"));
    }
}
