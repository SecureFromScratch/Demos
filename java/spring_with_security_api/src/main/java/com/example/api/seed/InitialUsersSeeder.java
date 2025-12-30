package com.example.api.seed;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.api.data.UserRepository;
import com.example.api.models.AppUser;

import org.springframework.security.crypto.password.PasswordEncoder;

@Component
public class InitialUsersSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.enabled:true}")
    private boolean enabled;

    @Value("${app.seed.default-password}")
    private String defaultPassword;

    public InitialUsersSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        // Run only if exactly 1 user exists (the admin created via /setup)
        long userCount = userRepository.count();
        if (userCount != 1) {
            return;
        }

        // Optional: enforce that the single user is actually "admin"
        AppUser onlyUser = userRepository.findAll().get(0);
        if (onlyUser.getUserName() == null || !onlyUser.getUserName().equalsIgnoreCase("admin")) {
            return;
        }

        seedIfMissing("QA1", "QA");
        seedIfMissing("QA2", "QA");
        seedIfMissing("DEV1", "DEV");
        seedIfMissing("DEV2", "DEV");
        seedIfMissing("PM1", "PM");
        seedIfMissing("PM2", "PM");
    }

    private void seedIfMissing(String username, String role) {
        if (userRepository.existsByUserNameIgnoreCase(username)) {
            return;
        }

        AppUser u = new AppUser();
        u.setUserName(username);
        u.setRoles(role);
        u.setEnabled(true);

        // Use the right setter for your schema:
        // If your entity uses passwordHash column:
        u.setPassword(passwordEncoder.encode(defaultPassword));
        // If your entity uses password column instead, replace with:
        // u.setPassword(passwordEncoder.encode(defaultPassword));

        userRepository.save(u);
    }
}
