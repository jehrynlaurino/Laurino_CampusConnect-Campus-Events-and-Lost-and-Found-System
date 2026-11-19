package com.campusconnect.admin;

import com.campusconnect.common.ApiException;
import com.campusconnect.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserRepository users;
    public AdminController(UserRepository users) { this.users = users; }

    public record RoleRequest(@NotNull Role role) {}
    public record StatusRequest(@NotNull Boolean active) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    @PatchMapping("/{id}/role")
    @Transactional
    public UserResponse role(@PathVariable Long id, @Valid @RequestBody RoleRequest req, Authentication auth) {
        AppUser target = find(id);
        if (target.getEmail().equals(auth.getName()) && req.role() != Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot remove your own administrator role.");
        }
        target.setRole(req.role());
        return UserResponse.from(users.save(target));
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public UserResponse status(@PathVariable Long id, @Valid @RequestBody StatusRequest req, Authentication auth) {
        AppUser target = find(id);
        if (target.getEmail().equals(auth.getName()) && !req.active()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot deactivate your own account.");
        }
        target.setActive(req.active());
        return UserResponse.from(users.save(target));
    }

    private AppUser find(Long id) {
        return users.findById(id).orElseThrow(() ->
            new ApiException(HttpStatus.NOT_FOUND, "User not found."));
    }
}
