package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Validates password strength with strict requirements
     * Requirements:
     * - At least 8 characters
     * - Contains at least one uppercase letter (A-Z)
     * - Contains at least one lowercase letter (a-z)
     * - Contains at least one digit (0-9)
     * - Contains at least one special character (!@#$%^&*()_+-=[]{}|;:,.<>?)
     * 
     * @param password The password to validate
     * @throws IllegalArgumentException if password doesn't meet requirements
     */
    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Password must contain at least one number");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            throw new IllegalArgumentException("Password must contain at least one special character (!@#$%^&* etc.)");
        }
    }

    /**
     * Validates username requirements
     * Requirements:
     * - Not null or empty
     * - At least 3 characters
     * - Only alphanumeric and underscore allowed
     * 
     * @param username The username to validate
     * @throws IllegalArgumentException if username doesn't meet requirements
     */
    private void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (username.length() < 3) {
            throw new IllegalArgumentException("Username must be at least 3 characters long");
        }
        if (username.length() > 50) {
            throw new IllegalArgumentException("Username cannot exceed 50 characters");
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Username can only contain letters, numbers, and underscores");
        }
    }

    /**
     * Check if this is the first user (database is empty)
     * @return true if no users exist in the database
     */
    public boolean isFirstUser() {
        return userRepository.count() == 0;
    }

    /**
     * Register a new user with strong password validation
     * 
     * @param username The username
     * @param rawPassword The plain text password (will be hashed)
     * @param isAdmin Whether the user should have admin privileges
     * @return The created User entity
     * @throws IllegalArgumentException if username already exists or validation fails
     */
    public User registerUser(String username, String rawPassword, boolean isAdmin) {
        // Validate username
        validateUsername(username);
        
        // Check if username already exists
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        // Validate password strength
        validatePasswordStrength(rawPassword);

        // Hash the password using BCrypt
        String hashedPassword = passwordEncoder.encode(rawPassword);
        
        // Set roles based on admin flag
        String roles = isAdmin ? "ADMIN,USER" : "USER";
        
        // Create and save the user
        User user = new User(username, hashedPassword, roles);
        return userRepository.save(user);
    }

    /**
     * Register the first admin user (only works if database is empty)
     * This ensures strong password requirements even for the initial setup
     * 
     * @param username The admin username
     * @param rawPassword The plain text password (will be hashed)
     * @return The created admin User entity
     * @throws IllegalStateException if a user already exists
     * @throws IllegalArgumentException if validation fails
     */
    public User registerFirstAdmin(String username, String rawPassword) {
        if (!isFirstUser()) {
            throw new IllegalStateException("First user already exists. Use regular registration.");
        }
        return registerUser(username, rawPassword, true);
    }
}