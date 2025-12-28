package com.example.api.controllers;

import com.example.api.config.SecurityConfig;
import com.example.api.models.AppUser;
import com.example.api.services.IUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final IUserService userService;
    private final SecurityConfig.JwtTokenProvider jwtTokenProvider;

    public AccountController(
            IUserService userService,
            SecurityConfig.JwtTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    // DTOs
    public record RegisterRequest(String userName, String password) {}
    public record LoginRequest(String userName, String password) {}
    public record MeResponse(String userName, List<String> roles) {}
    public record LoginResponse(String token, MeResponse user) {}

    @GetMapping("/is-first-user")
    public ResponseEntity<Boolean> isFirstUser() {
        return ResponseEntity.ok(userService.isFirstUser());
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setup(@RequestBody RegisterRequest request) {
        try {
            userService.registerFirstAdmin(request.userName(), request.password());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            userService.registerUser(request.userName(), request.password(), false);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        var userOpt = userService.findByUserName(request.userName());
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid credentials"));
        }

        AppUser user = userOpt.get();
        
        if (!user.isEnabled()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid credentials"));
        }

        if (!userService.verifyPassword(user, request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid credentials"));
        }

        List<String> roles = Arrays.stream(user.getRoles().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        String token = jwtTokenProvider.generateToken(user.getUserName(), roles);

        MeResponse me = new MeResponse(user.getUserName(), roles);
        return ResponseEntity.ok(new LoginResponse(token, me));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // JWT logout is handled client side by deleting token
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String userName = authentication.getName();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new MeResponse(userName, roles));
    }
}