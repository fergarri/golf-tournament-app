package com.golf.tournament.controller;

import com.golf.tournament.dto.auth.ChangeOwnPasswordRequest;
import com.golf.tournament.dto.auth.LoginRequest;
import com.golf.tournament.dto.auth.LoginResponse;
import com.golf.tournament.dto.auth.ProfileResponse;
import com.golf.tournament.dto.auth.UpdateOwnProfileRequest;
import com.golf.tournament.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getProfile() {
        return ResponseEntity.ok(authService.getProfile());
    }

    @PutMapping("/me")
    public ResponseEntity<ProfileResponse> updateProfile(@RequestBody UpdateOwnProfileRequest request) {
        return ResponseEntity.ok(authService.updateProfile(request));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changeOwnPassword(@RequestBody ChangeOwnPasswordRequest request) {
        authService.changeOwnPassword(request);
        return ResponseEntity.ok().build();
    }
}
