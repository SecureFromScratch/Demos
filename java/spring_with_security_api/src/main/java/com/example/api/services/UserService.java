package com.example.api.services;

import com.example.api.data.UserRepository;
import com.example.api.models.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService implements IUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public boolean isFirstUser() {
        return userRepository.count() == 0;
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.trim().isEmpty() || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }

        if (!password.chars().anyMatch(Character::isUpperCase)) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        }

        if (!password.chars().anyMatch(Character::isLowerCase)) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        }

        if (!password.chars().anyMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Password must contain at least one number");
        }

        String specials = "!@#$%^&*()_+-=[]{};':\"\\|,.<>/?";
        if (!password.chars().anyMatch(ch -> specials.indexOf(ch) >= 0)) {
            throw new IllegalArgumentException("Password must contain at least one special character");
        }
    }

    @Override
    @Transactional
    public AppUser registerUser(String userName, String rawPassword, boolean isAdmin) {
        if (userName == null || userName.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        userName = userName.trim();

        if (userName.length() < 3) {
            throw new IllegalArgumentException("Username must be at least 3 characters long");
        }

        // Alternative: using findByUserName instead of existsByUserName
        if (userRepository.findByUserName(userName).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        validatePasswordStrength(rawPassword);

        AppUser user = new AppUser();
        user.setUserName(userName);
        user.setRoles(isAdmin ? "ADMIN,USER" : "USER");
        user.setEnabled(true);
        user.setPassword(passwordEncoder.encode(rawPassword));

        return userRepository.save(user);
    }

    @Override
    @Transactional
    public AppUser registerFirstAdmin(String userName, String rawPassword) {
        if (!isFirstUser()) {
            throw new IllegalStateException("First user already exists");
        }
        return registerUser(userName, rawPassword, true);
    }

    @Override
    public Optional<AppUser> findByUserName(String userName) {
        return userRepository.findByUserName(userName);
    }

    @Override
    public boolean verifyPassword(AppUser user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }
}