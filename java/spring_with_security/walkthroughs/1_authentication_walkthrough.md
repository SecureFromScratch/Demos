# Complete Tutorial: Secure User Registration with Spring Boot & Thymeleaf

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Project Setup](#project-setup)
4. [Understanding the Architecture](#understanding-the-architecture)
5. [Step-by-Step Implementation](#step-by-step-implementation)
6. [How It Works](#how-it-works)
7. [Testing Your Application](#testing-your-application)
8. [Security Best Practices](#security-best-practices)
9. [Troubleshooting](#troubleshooting)

---

## Overview

This tutorial shows you how to build a secure user registration system that:
- ✅ **Never stores plain-text passwords** (uses Argon/BCrypt hashing)
- ✅ **Has no hard-coded credentials** in the source code
- ✅ **Solves the "first user problem"** elegantly
- ✅ **Supports role-based access** (Admin and User roles)
- ✅ **Uses JPA/Hibernate** for database operations
- ✅ **Follows Spring Security best practices**

### The "First User Problem"
**Challenge**: How can the first user register when registration typically requires authentication?

**Our Solution**: A special `/setup` endpoint that only works when the database is empty, allowing the first user to create an admin account.

---

## Prerequisites

- Java 17 or higher
- Spring Boot 3.x
- Basic understanding of Spring MVC
- Basic understanding of Spring Security
- Gradle (or Maven)

---

## Project Setup

### 1. Add Dependencies to `build.gradle`

```gradle
dependencies {
    // Core Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    
    // Database (JPA + H2)
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'com.h2database:h2'
    
    
    // Testing
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
}
```

### 2. Configure Database in `application.properties`

```properties
# H2 In-Memory Database (perfect for demos)
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# JPA/Hibernate Configuration
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# H2 Console (optional - for debugging)
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
```

**What this does:**
- `ddl-auto=update`: Automatically creates/updates database tables from your entities
- `show-sql=true`: Logs SQL queries (helpful for debugging)
- H2 Console: Access database at `http://localhost:8080/h2-console`

---

## Understanding the Architecture

### Application Layers

```
┌─────────────────────────────────────────┐
│         Presentation Layer              │
│  (Controllers + Thymeleaf Templates)    │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│          Service Layer                  │
│  (Business Logic + Password Hashing)    │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│       Data Access Layer                 │
│      (JPA Repository)                   │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│          Database (H2)                  │
│         users table                     │
└─────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility |
|-----------|---------------|
| **WebSecurityConfig** | Configures Spring Security, defines which URLs require authentication |
| **User (Entity)** | Represents a user in the database |
| **UserRepository** | Handles database operations (save, find, count) |
| **UserService** | Contains business logic for user registration |
| **CustomUserDetailsService** | Loads user data for Spring Security authentication |
| **RegistrationController** | Handles HTTP requests for registration/setup |

---

## Step-by-Step Implementation

### Step 1: Create the User Entity

**File:** `src/main/java/com/example/demo/model/User.java`

```java
package com.example.demo.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(nullable = false)
    private String password;  // Stored as BCrypt hash
    
    @Column(nullable = false)
    private String roles;  // "USER" or "ADMIN,USER"
    
    @Column(nullable = false)
    private boolean enabled = true;

    // Constructors, getters, setters...
}
```

**Key Points:**
- `@Entity`: Marks this as a JPA entity (database table)
- `@Id` + `@GeneratedValue`: Auto-increment primary key
- `@Column(unique = true)`: Ensures usernames are unique
- `password`: Will store BCrypt hash (NOT plain text)
- `roles`: Comma-separated string of roles

**Why not use a separate roles table?**
For simple applications, storing roles as a string is sufficient. For complex role management, use a many-to-many relationship.

---

### Step 2: Create the Repository

**File:** `src/main/java/com/example/demo/repository/UserRepository.java`

```java
package com.example.demo.repository;

import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    long count();
}
```

**Key Points:**
- `JpaRepository<User, Long>`: Provides CRUD operations automatically
- `findByUsername()`: Spring Data JPA creates this query automatically
- `count()`: Used to check if database is empty (first user detection)

**Spring Data Magic:**
You only declare the method signature. Spring Data JPA generates the implementation based on the method name!

---

### Step 3: Create the User Service

**File:** `src/main/java/com/example/demo/service/UserService.java`

```java
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

    public boolean isFirstUser() {
        return userRepository.count() == 0;
    }

    public User registerUser(String username, String rawPassword, boolean isAdmin) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        // CRITICAL: Hash the password before storing
        String hashedPassword = passwordEncoder.encode(rawPassword);
        String roles = isAdmin ? "ADMIN,USER" : "USER";
        
        User user = new User(username, hashedPassword, roles);
        return userRepository.save(user);
    }

    public User registerFirstAdmin(String username, String rawPassword) {
        if (!isFirstUser()) {
            throw new IllegalStateException("First user already exists");
        }
        return registerUser(username, rawPassword, true);
    }
}
```

**Key Points:**
- `passwordEncoder.encode()`: Converts plain text → BCrypt hash
- `isFirstUser()`: Checks if database is empty
- `registerFirstAdmin()`: Only works when `count() == 0`
- Validation: Checks for duplicate usernames

**BCrypt Hashing:**
```
Input:  "myPassword123"
Output: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
         └─┬─┘ └┬┘ └─────────┬──────────┘└──────────┬───────────────┘
       algorithm cost      salt              hash
```

---

### Step 4: Create Custom UserDetailsService

**File:** `src/main/java/com/example/demo/service/CustomUserDetailsService.java`

```java
package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) 
            throws UsernameNotFoundException {
        
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(user.getPassword())  // Already hashed
            .roles(user.getRoles().split(","))  // "ADMIN,USER" → ["ADMIN", "USER"]
            .disabled(!user.isEnabled())
            .build();
    }
}
```

**Key Points:**
- Spring Security calls this during login
- Converts our `User` entity → Spring Security `UserDetails`
- Password is already hashed in database
- Splits roles string: `"ADMIN,USER"` → `["ADMIN", "USER"]`

**Authentication Flow:**
1. User submits login form
2. Spring Security calls `loadUserByUsername()`
3. We fetch user from database
4. Spring Security compares hashed passwords
5. If match → user authenticated

---

### Step 5: Create the Registration Controller

**File:** `src/main/java/com/example/demo/controller/RegistrationController.java`

```java
package com.example.demo.controller;

import com.example.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RegistrationController {

    @Autowired
    private UserService userService;

    // First-time setup
    @GetMapping("/setup")
    public String showSetupPage(Model model) {
        if (!userService.isFirstUser()) {
            return "redirect:/login";  // Already have users
        }
        model.addAttribute("isSetup", true);
        return "register";
    }

    @PostMapping("/setup")
    public String setupFirstAdmin(@RequestParam String username, 
                                   @RequestParam String password,
                                   Model model) {
        try {
            userService.registerFirstAdmin(username, password);
            return "redirect:/login?setup=success";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("isSetup", true);
            return "register";
        }
    }

    // Regular registration
    @GetMapping("/register")
    public String showRegistrationPage(Model model) {
        if (userService.isFirstUser()) {
            return "redirect:/setup";  // Redirect to setup if first user
        }
        model.addAttribute("isSetup", false);
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@RequestParam String username, 
                                @RequestParam String password,
                                Model model) {
        try {
            userService.registerUser(username, password, false);
            return "redirect:/login?registered=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("isSetup", false);
            return "register";
        }
    }
}
```

**Key Points:**
- `/setup`: Only accessible when database is empty
- `/register`: Redirects to `/setup` if first user
- `@RequestParam`: Extracts form data
- Error handling: Catches exceptions and shows error messages

**Request Flow:**
```
First User:
/register → redirect → /setup → create admin → /login

Subsequent Users:
/register → create user → /login
```

---
### Step 6: Configure MVC

``` java
package com.example.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addViewController("/home").setViewName("home");
		registry.addViewController("/").setViewName("home");
		registry.addViewController("/hello").setViewName("hello");
		registry.addViewController("/login").setViewName("login");
	}

}

```


### Step 7: Configure Spring Security

**File:** `src/main/java/com/example/demo/config/WebSecurityConfig.java`

```java
package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests((requests) -> requests
                .requestMatchers("/", "/home", "/register", "/setup").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin((form) -> form
                .loginPage("/login")
                .permitAll()
            )
            .logout((logout) -> logout.permitAll());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Argon2 is the modern standard and winner of the Password Hashing Competition
        // It's more secure than BCrypt against GPU/ASIC attacks
        // OWASP recommends Argon2id as the first choice for password hashing
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
```

**Key Points:**
- `permitAll()`: No authentication required for these URLs
- `hasRole("ADMIN")`: Only admins can access `/admin/**`
- `authenticated()`: All other URLs require login
- `BCryptPasswordEncoder`: Strong password hashing

**Security Rules:**
```
/                  → Everyone
/register          → Everyone
/setup             → Everyone
/admin/**          → ADMIN only
Everything else    → Authenticated users
```

**Why Argon2 over BCrypt?**
- ✅ **Winner of Password Hashing Competition (2015)**
- ✅ **Resistant to GPU/ASIC attacks** (uses memory-hard algorithm)
- ✅ **Configurable memory, time, and parallelism costs**
- ✅ **Recommended by OWASP** as first choice
- ✅ **Newer and more secure** than BCrypt (2013 vs 1999)

**Hash comparison:**
```
BCrypt:  $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
Argon2:  $argon2id$v=19$m=65536,t=3,p=1$abc123...$hash_output_here...
          └──┬───┘ └──┬─┘ └────────┬──────────┘ └─┬─┘ └──────┬──────┘
          variant  version  parameters(m,t,p) salt    hash
```

### Password Hashing Algorithm Comparison

| Algorithm | Released | Security Level | Speed | Memory Usage | OWASP Rank |
|-----------|----------|----------------|-------|--------------|------------|
| **Argon2id** | 2015 | 🟢 Excellent | Configurable | High (64MB+) | #1 ⭐ |
| Argon2i | 2015 | 🟢 Excellent | Configurable | High (64MB+) | #1 ⭐ |
| Argon2d | 2015 | 🟡 Good* | Configurable | High (64MB+) | - |
| scrypt | 2009 | 🟢 Very Good | Slow | Medium | #2 |
| BCrypt | 1999 | 🟡 Good | Slow | Low | #3 |
| PBKDF2 | 2000 | 🟡 Acceptable | Fast | Low | #4 |
| MD5 | 1991 | 🔴 **BROKEN** | Very Fast | Minimal | ❌ Never use |
| SHA-1 | 1995 | 🔴 **WEAK** | Very Fast | Minimal | ❌ Never use |

*Argon2d vulnerable to side-channel attacks; use Argon2id instead

**Why Argon2id wins:**
1. **Memory-hard**: Requires significant RAM, making GPU/ASIC attacks expensive
2. **Time-hard**: Configurable computational cost
3. **Side-channel resistant**: Combines Argon2i (data-independent) and Argon2d (data-dependent)
4. **Proven**: Winner of Password Hashing Competition, peer-reviewed
5. **Future-proof**: Parameters can be adjusted as hardware improves

**Argon2 Parameters Explained:**
```java
Argon2PasswordEncoder(
    16,      // saltLength (bytes) - random salt for each password
    32,      // hashLength (bytes) - output hash size
    1,       // parallelism - number of threads
    65536,   // memory (KB) - 64 MB RAM required
    3        // iterations - time cost
)
```

**Security Trade-offs:**
- Higher memory → More expensive attacks, slower hashing
- Higher iterations → Slower hashing, better security
- Higher parallelism → Can utilize multiple cores

**Recommended settings:**
- **Default** (what we use): `m=64MB, t=3, p=1` - Good balance
- **High security**: `m=256MB, t=5, p=4` - Very secure, slower
- **Low-resource**: `m=16MB, t=2, p=1` - Minimum acceptable

---

---

### Step 8: Create the Registration Template

**File:** `src/main/resources/templates/register.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title th:text="${isSetup} ? 'First-Time Setup' : 'Register'">Register</title>
    <style>
        body { 
            font-family: Arial, sans-serif; 
            max-width: 400px; 
            margin: 50px auto; 
            padding: 20px; 
        }
        .form-group { margin-bottom: 15px; }
        label { display: block; margin-bottom: 5px; font-weight: bold; }
        input[type="text"], input[type="password"] { 
            width: 100%; 
            padding: 8px; 
            box-sizing: border-box; 
        }
        button { 
            background-color: #4CAF50; 
            color: white; 
            padding: 10px 20px; 
            border: none; 
            cursor: pointer; 
            width: 100%; 
        }
        button:hover { background-color: #45a049; }
        .error { color: red; margin-bottom: 15px; }
        .info { 
            background-color: #e7f3fe; 
            border-left: 4px solid #2196F3; 
            padding: 10px; 
            margin-bottom: 15px; 
        }
    </style>
</head>
<body>
    <h1 th:text="${isSetup} ? 'First-Time Setup' : 'Register'">Register</h1>
    
    <div th:if="${isSetup}" class="info">
        <strong>Welcome!</strong> Create the first admin account to get started.
    </div>
    
    <div th:if="${error}" class="error" th:text="${error}"></div>

    <form th:action="${isSetup} ? '/setup' : '/register'" method="post">
        <div class="form-group">
            <label for="username">Username:</label>
            <input type="text" id="username" name="username" required />
        </div>
        
        <div class="form-group">
            <label for="password">Password:</label>
            <input type="password" id="password" name="password" required minlength="6" />
        </div>
        
        <button type="submit" th:text="${isSetup} ? 'Create Admin Account' : 'Register'">
            Register
        </button>
    </form>
    
    <p th:unless="${isSetup}">
        Already have an account? <a href="/login">Login here</a>
    </p>
</body>
</html>
```

**Thymeleaf Features:**
- `th:if`: Conditional rendering
- `th:text`: Dynamic text content
- `th:action`: Dynamic form action
- `th:unless`: Opposite of `th:if`

---

## How It Works

### First User Registration Flow

```
1. User visits http://localhost:8080/register
   ↓
2. Controller checks: userService.isFirstUser()
   ↓ (returns true - database empty)
3. Redirect to /setup
   ↓
4. User fills form: username="admin", password="SecureAdmin2024!"
   ↓
5. POST to /setup
   ↓
6. UserService:
   - Checks isFirstUser() again (security)
   - Hashes password: "SecureAdmin2024!" → Argon2 hash
   - Creates User with roles="ADMIN,USER"
   - Saves to database
   ↓
7. Redirect to /login
   ↓
8. User logs in with credentials
```

### Subsequent User Registration Flow

```
1. User visits http://localhost:8080/register
   ↓
2. Controller checks: userService.isFirstUser()
   ↓ (returns false - admin exists)
3. Show /register form
   ↓
4. User fills form: username="john", password="JohnPass2024!"
   ↓
5. POST to /register
   ↓
6. UserService:
   - Checks if username exists
   - Hashes password
   - Creates User with roles="USER"
   - Saves to database
   ↓
7. Redirect to /login
   ↓
8. User logs in with credentials
```

### Login Authentication Flow

```
1. User submits login form
   ↓
2. Spring Security intercepts
   ↓
3. Calls CustomUserDetailsService.loadUserByUsername()
   ↓
4. Fetches user from database
   ↓
5. Spring Security compares passwords:
   - User input: "SecureAdmin2024!"
   - Database: "$argon2id$v=19$m=65536,t=3,p=1$..."
   - Argon2 verifies if they match
   ↓
6. If match:
   - Create authentication token
   - Store in SecurityContext
   - Grant roles/authorities
   ↓
7. Redirect to protected page
```

---

## Testing Your Application

### 1. Start the Application

```bash
./gradlew bootRun
```

### 2. First User Setup

**Step 1:** Open browser → `http://localhost:8080/register`
- Should automatically redirect to `/setup`

**Step 2:** Create admin account
- Username: `admin`
- Password: `SecureAdmin2024!`
- Click "Create Admin Account"

**Step 3:** Login
- Use the credentials you just created
- Should successfully log in

### 3. Register Regular User

**Step 1:** Logout (click logout link)

**Step 2:** Go to `http://localhost:8080/register`
- Now shows regular registration form
- NO redirect to `/setup`

**Step 3:** Create user account
- Username: `user1`
- Password: `UserPass2024!`
- Click "Register"

**Step 4:** Login with new credentials

### 4. Test Security

**Try accessing setup again:**
```
http://localhost:8080/setup
```
Should redirect to `/login` (first user already exists)

**Test admin-only endpoints:**
Create a test admin page:
```java
@GetMapping("/admin/dashboard")
public String adminDashboard() {
    return "admin_dashboard";
}
```

- Login as `admin` → Access granted
- Login as `user1` → Access denied (403 Forbidden)

### 5. Inspect Database (H2 Console)

**Step 1:** Go to `http://localhost:8080/h2-console`

**Step 2:** Use these settings:
```
JDBC URL: jdbc:h2:mem:testdb
Username: sa
Password: (leave empty)
```

**Step 3:** Run query:
```sql
SELECT * FROM users;
```

**You should see:**
```
ID | USERNAME | PASSWORD                                                    | ROLES      | ENABLED
1  | admin    | $argon2id$v=19$m=65536,t=3,p=1$abc123...$hash_output_here... | ADMIN,USER | true
2  | user1    | $argon2id$v=19$m=65536,t=3,p=1$xyz789...$another_hash...    | USER       | true
```

**Notice:** Passwords are hashed with Argon2! 🔒

---

## Security Best Practices

### ✅ What We Implemented

1. **Argon2 Password Hashing** (Industry Best Practice)
   - Winner of Password Hashing Competition (2015)
   - Memory-hard algorithm (resistant to GPU/ASIC attacks)
   - OWASP's first choice recommendation
   - Configurable security parameters
   - Superior to BCrypt, scrypt, and PBKDF2

2. **No Hard-Coded Credentials**
   - No passwords in source code
   - No passwords in version control
   - All credentials created by users

3. **Input Validation**
   - Username uniqueness check
   - Minimum password length
   - Required fields

4. **Role-Based Access Control**
   - Admin vs. User roles
   - Path-based restrictions
   - Method-level security available

### 🔒 Additional Recommendations

#### 1. Strong Password Requirements (RECOMMENDED)

Add password validation to `UserService.java`:

```java
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Validates password strength
     * Requirements:
     * - At least 8 characters
     * - Contains uppercase letter
     * - Contains lowercase letter
     * - Contains digit
     * - Contains special character
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
            throw new IllegalArgumentException("Password must contain at least one special character");
        }
    }

    public boolean isFirstUser() {
        return userRepository.count() == 0;
    }

    public User registerUser(String username, String rawPassword, boolean isAdmin) {
        // Validate username
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (username.length() < 3) {
            throw new IllegalArgumentException("Username must be at least 3 characters");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        // Validate password strength
        validatePasswordStrength(rawPassword);

        // Hash password and create user
        String hashedPassword = passwordEncoder.encode(rawPassword);
        String roles = isAdmin ? "ADMIN,USER" : "USER";
        
        User user = new User(username, hashedPassword, roles);
        return userRepository.save(user);
    }

    public User registerFirstAdmin(String username, String rawPassword) {
        if (!isFirstUser()) {
            throw new IllegalStateException("First user already exists");
        }
        return registerUser(username, rawPassword, true);
    }
}
```

**Good passwords:**
- ✅ `MySecureP@ss2024`
- ✅ `Admin!Strong123`
- ✅ `User#Pass2024!`

**Bad passwords:**
- ❌ `password` (no uppercase, no numbers, no special chars)
- ❌ `Password` (no numbers, no special chars)
- ❌ `Pass123` (too short, no special chars)
- ❌ `12345678` (no letters, no special chars)

#### 2. Update Registration Template for Better UX

Update `register.html` to show password requirements:

```html
<div class="form-group">
    <label for="password">Password:</label>
    <input type="password" id="password" name="password" required minlength="8" />
    <small style="color: #666; font-size: 0.9em;">
        Password must contain:
        <ul style="margin: 5px 0; padding-left: 20px;">
            <li>At least 8 characters</li>
            <li>One uppercase letter (A-Z)</li>
            <li>One lowercase letter (a-z)</li>
            <li>One number (0-9)</li>
            <li>One special character (!@#$%^&*)</li>
        </ul>
    </small>
</div>
```

#### 3. Account Lockout
#### 3. Account Lockout After Failed Login Attempts

Add to `User.java`:
```java
@Column(nullable = false)
private int failedAttempts = 0;

@Column
private LocalDateTime lockTime;

public int getFailedAttempts() { return failedAttempts; }
public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }

public LocalDateTime getLockTime() { return lockTime; }
public void setLockTime(LocalDateTime lockTime) { this.lockTime = lockTime; }
```

Create `LoginAttemptService.java`:
```java
@Service
public class LoginAttemptService {
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_TIME_DURATION = 15; // minutes

    @Autowired
    private UserRepository userRepository;

    public void loginSucceeded(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setFailedAttempts(0);
            user.setLockTime(null);
            user.setEnabled(true);
            userRepository.save(user);
        });
    }

    public void loginFailed(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setFailedAttempts(user.getFailedAttempts() + 1);
            
            if (user.getFailedAttempts() >= MAX_ATTEMPTS) {
                user.setEnabled(false);
                user.setLockTime(LocalDateTime.now());
            }
            
            userRepository.save(user);
        });
    }

    public boolean isAccountLocked(String username) {
        return userRepository.findByUsername(username)
            .map(user -> {
                if (!user.isEnabled() && user.getLockTime() != null) {
                    LocalDateTime unlockTime = user.getLockTime().plusMinutes(LOCK_TIME_DURATION);
                    
                    if (LocalDateTime.now().isAfter(unlockTime)) {
                        // Unlock account automatically after lock duration
                        user.setEnabled(true);
                        user.setFailedAttempts(0);
                        user.setLockTime(null);
                        userRepository.save(user);
                        return false;
                    }
                    return true;
                }
                return false;
            })
            .orElse(false);
    }
}
```

#### 4. Email Verification
#### 4. Email Verification (Optional)
```java
@Column(nullable = false)
private boolean emailVerified = false;

@Column
private String verificationToken;
```

#### 5. HTTPS in Production (MANDATORY)
#### 5. HTTPS in Production (MANDATORY)

**Never send passwords over plain HTTP in production!**

```properties
# application-prod.properties
server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=${SSL_KEYSTORE_PASSWORD}
server.ssl.key-store-type=PKCS12
```

Generate SSL certificate:
```bash
keytool -genkeypair -alias myapp -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore keystore.p12 -validity 3650
```

#### 6. CSRF Protection
```java
// Already enabled in Spring Security!
// Protects against Cross-Site Request Forgery
```

---

## Troubleshooting

### Problem: "Bean named 'passwordEncoder' could not be found"

**Solution:** Make sure `WebSecurityConfig` has:
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

---

### Problem: "Table 'users' doesn't exist"

**Cause:** JPA not creating tables

**Solution 1:** Check `application.properties`:
```properties
spring.jpa.hibernate.ddl-auto=update
```

**Solution 2:** Check `User` class has `@Entity` annotation

**Solution 3:** Check database URL is correct

---

### Problem: "/setup always redirects to /login"

**Cause:** First user already created

**Solution:** Clear database:
```sql
DELETE FROM users;
```

Or restart with fresh H2 in-memory database (automatic on app restart)

---

### Problem: "403 Forbidden" on /register

**Cause:** CSRF token missing or /register not in permitAll()

**Solution 1:** Check `WebSecurityConfig`:
```java
.requestMatchers("/", "/home", "/register", "/setup").permitAll()
```

**Solution 2:** Ensure form method is POST (CSRF token auto-added)

---

### Problem: Login fails with correct password

**Possible Causes:**

1. **Password not hashed before saving**
```java
// WRONG
user.setPassword(rawPassword);

// CORRECT
user.setPassword(passwordEncoder.encode(rawPassword));
```

2. **Using wrong PasswordEncoder**
```java
// Make sure you're using Argon2, not BCrypt
@Bean
public PasswordEncoder passwordEncoder() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
}
```

3. **User not enabled**
```java
user.setEnabled(true);  // Make sure this is set!
```

---

### Problem: Can't access admin endpoints

**Cause:** User doesn't have ADMIN role

**Solution:** Check roles in database:
```sql
SELECT username, roles FROM users;
```

Admin should have: `ADMIN,USER`
Regular user: `USER`

---

## Next Steps

### Enhancements You Can Add

1. **Email Verification**
   - Send verification email on registration
   - User clicks link to activate account

2. **Password Reset**
   - "Forgot Password" link
   - Email reset token
   - Change password flow

3. **Profile Management**
   - Edit user details
   - Change password
   - Upload avatar

4. **Admin Dashboard**
   - View all users
   - Enable/disable accounts
   - Assign roles

5. **Audit Logging**
   - Log registration attempts
   - Log login/logout
   - Track admin actions

6. **OAuth2 Integration**
   - Login with Google
   - Login with GitHub
   - Social authentication

7. **Two-Factor Authentication**
   - TOTP (Google Authenticator)
   - SMS verification
   - Email codes

### Moving to Production

1. **Switch to Production Database**
```properties
# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/myapp
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

2. **Use Environment Variables**
```bash
export DB_USERNAME=myuser
export DB_PASSWORD=secretpassword
```

3. **Enable HTTPS**
4. **Set up proper logging**
5. **Configure CORS if needed**
6. **Set up monitoring**

---

## Conclusion

You've built a complete, secure user registration system that:

✅ Hashes all passwords with BCrypt
✅ Has zero hard-coded credentials
✅ Solves the first user problem elegantly
✅ Implements role-based access control
✅ Follows Spring Security best practices
✅ Is ready for production enhancements

The key innovation is the `/setup` endpoint that only works when the database is empty, allowing a smooth first-user experience while maintaining security.

### Key Takeaways

1. **Never store plain-text passwords** - Always use BCrypt or similar
2. **Separate concerns** - Controller → Service → Repository
3. **Validate input** - Check usernames, password strength, etc.
4. **Use Spring Security properly** - Let it handle authentication
5. **Think about the user experience** - Make registration smooth

Happy coding! 🚀
