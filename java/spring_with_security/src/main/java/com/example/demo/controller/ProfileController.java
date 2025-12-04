package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public String showProfile(Model model, Authentication authentication) {
        // Get current logged-in user's username
        String username = authentication.getName();

        // Fetch the user from database
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        model.addAttribute("user", user);
        return "profile";
    }

    // just for Authentication demo
    @GetMapping("/debug")
    public String debugAuth(Authentication authentication, Model model) {
        System.out.println("=== AUTHENTICATION DEBUG ===");

        // 1. Principal (who you are)
        Object principal = authentication.getPrincipal();
        System.out.println("Principal type: " + principal.getClass().getName());
        System.out.println("Principal: " + principal);

        // Convert principal to String for Thymeleaf
        String principalString;
        String principalType = principal.getClass().getSimpleName();

        if (principal instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) principal;
            principalString = "Username: " + userDetails.getUsername() +
                    ", Enabled: " + userDetails.isEnabled() +
                    ", Account Non-Locked: " + userDetails.isAccountNonLocked();
        } else {
            principalString = principal.toString();
        }

        // 2. Credentials (password - usually null after authentication)
        Object credentials = authentication.getCredentials();
        String credentialsString = (credentials != null) ? "[PROTECTED]" : "null";
        System.out.println("Credentials: " + credentialsString);

        // 3. Authorities (roles/permissions) - Convert to String
        String authoritiesString = authentication.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .collect(Collectors.joining(", "));
        System.out.println("Authorities: " + authoritiesString);

        // 4. Details (extra info like IP, session ID)
        Object details = authentication.getDetails();
        String detailsString = (details != null) ? details.toString() : "null";
        System.out.println("Details: " + detailsString);

        // 5. Is authenticated?
        boolean isAuthenticated = authentication.isAuthenticated();
        System.out.println("Is Authenticated: " + isAuthenticated);

        // 6. Name (shortcut for username)
        String name = authentication.getName();
        System.out.println("Name: " + name);

        // Add STRING versions to model (Thymeleaf-friendly)
        model.addAttribute("principalType", principalType);
        model.addAttribute("principal", principalString);
        model.addAttribute("authorities", authoritiesString);
        model.addAttribute("details", detailsString);
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("name", name);

        return "debug";
    }

}