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
     * Check if this is the first user (database is empty)
     * @return true if no users exist in the database
     */
    public boolean isFirstUser() {
        return userRepository.count() == 0;
    }

    /**
     * Register a new user with a hashed password
     * @param username The username
     * @param rawPassword The plain text password (will be hashed)
     * @param isAdmin Whether the user should have admin privileges
     * @return The created User entity
     * @throws IllegalArgumentException if username already exists
     */
    public User registerUser(String username, String rawPassword, boolean isAdmin) {
        // Check if username already exists
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

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
     * @param username The admin username
     * @param rawPassword The plain text password (will be hashed)
     * @return The created admin User entity
     * @throws IllegalStateException if a user already exists
     */
    public User registerFirstAdmin(String username, String rawPassword) {
        if (!isFirstUser()) {
            throw new IllegalStateException("First user already exists. Use regular registration.");
        }
        return registerUser(username, rawPassword, true);
    }
}