package com.golf.tournament.controller;

import com.golf.tournament.dto.user.ChangePasswordRequest;
import com.golf.tournament.dto.user.CreateUserRequest;
import com.golf.tournament.dto.user.UpdateUserRequest;
import com.golf.tournament.dto.user.UserDTO;
import com.golf.tournament.model.Role;
import com.golf.tournament.security.CurrentUserProvider;
import com.golf.tournament.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('TOTAL', 'CLUB_USERS')")
public class UserController {

    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/roles")
    public ResponseEntity<List<String>> getAvailableRoles() {
        // Un admin de club (ADMIN_CLUB) sólo puede asignar roles de club (USER, ADMIN_CLUB),
        // nunca el rol ADMIN (superadmin). El superadmin puede asignar cualquier rol.
        List<String> roles = Arrays.stream(Role.values())
                .filter(role -> currentUserProvider.isSuperAdmin() || role != Role.ADMIN)
                .map(Role::name)
                .toList();
        return ResponseEntity.ok(roles);
    }

    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDTO user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> changePassword(
            @PathVariable Long id,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
