# Input Validation Guide for Spring Boot REST API

## 📚 Overview

This comprehensive guide covers input validation for a Spring Boot REST API

---

## Why Input Validation Matters

### The Foundation of Application Security

**Input validation is the FIRST and most fundamental layer of security** - it's your application's front door. While specialized protections like parameterized queries (SQL injection), Content Security Policy (XSS), CSRF tokens, and output encoding are essential, they are **secondary defenses** that should never be your only line of protection. Think of security as a castle: input validation is the outer wall, while parameterized queries and XSS protections are the inner walls and guards.

**Why input validation must come first:**

1. **Universal Protection** - Input validation protects against ALL types of injection attacks in one place, not just SQL or XSS. It stops CSV injection, command injection, LDAP injection, XML injection, and even attack vectors that haven't been discovered yet.

2. **Defense in Depth** - Even if a developer forgets to use a parameterized query or misses an output encoding somewhere, validated input limits the damage. You have multiple layers of protection instead of relying on perfect implementation everywhere.

3. **Data Integrity** - Beyond security, validation ensures your database contains clean, consistent data. This prevents application crashes, business logic errors, and data corruption that can occur even without malicious intent.

4. **Performance** - Rejecting invalid input early (at the API boundary) is far more efficient than letting it travel through your application layers, potentially causing database errors, cache invalidation, or complex error handling.

5. **Clear Contract** - Input validation defines what your API accepts. This makes your API predictable, easier to test, and helps legitimate users understand what's expected.

**The Reality Check:** Parameterized queries only protect against SQL injection, and only if used correctly everywhere. XSS protection only prevents script execution in browsers. CSRF tokens only prevent cross-site request forgery. But **a single validation layer using an allow-list approach protects against injection attacks across your entire application surface** - in CSV exports, database queries, API responses, log files, and even attack types that don't exist yet.

**Example of why validation is foundational:**
```java
// ❌ DANGEROUS: No input validation, relying only on parameterized queries
String userInput = request.getParameter("name"); // Could be "=cmd|'/c calc'"
// Even with parameterized query, this is saved as-is in database
String sql = "INSERT INTO recipes (name) VALUES (?)";
preparedStatement.setString(1, userInput); // Safe from SQL injection, but...
// Later, when exported to CSV, the formula executes!

// ✅ SECURE: Validate input first, THEN use parameterized queries
String userInput = request.getParameter("name");
String validatedInput = inputValidator.validateAndSanitize(userInput, "name"); // Rejects "=cmd|..."
String sql = "INSERT INTO recipes (name) VALUES (?)";
preparedStatement.setString(1, validatedInput); // Safe everywhere: DB, CSV, logs, API responses
```

### Defense-in-Depth Strategy

We implement validation at **multiple layers**:

```
Client Request
    ↓
[1] Bean Validation (@Valid, @Pattern, @Size)
    ↓
[2] Custom Validation Utility (Allow-list)
    ↓
[3] Controller Validation (Business rules)
    ↓
[4] Service Layer Validation (Additional checks)
    ↓
[5] Database Constraints (Last line of defense)
    ↓
Database
```

**Why multiple layers?**
- If one layer fails, others catch the issue
- Different layers handle different concerns
- Defense against bypass attempts

---

## Validation Layers Overview

| Layer | Purpose | When It Runs | Example |
|-------|---------|--------------|---------|
| **Bean Validation** | Format/structure validation | Before method execution (@Valid) | @Pattern, @Size, @NotNull |
| **Custom Utility** | Security validation (allow-list) | Explicit calls in code | CsvInjectionPrevention |
| **Controller** | Request-specific validation | In controller methods | File type, size checks |
| **Service** | Business logic validation | Before database operations | Duplicate checks, constraints |
| **Database** | Final enforcement | On INSERT/UPDATE | NOT NULL, UNIQUE, CHECK |

---

## Setting Up Dependencies

Add to your `build.gradle`:

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
}
```

---

## Layer 1: Bean Validation (JSON APIs)

Bean Validation (JSR-380) provides declarative validation using annotations.

### Step 1: Create DTO with Validation Annotations

Create `src/main/java/com/example/api/dto/RecipeRequest.java`:

```java
package com.example.api.dto;

import com.example.api.models.Recipe.RecipeStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeRequest {
    
    // Required field with length constraint
    @NotBlank(message = "Recipe name is required")
    @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    @Pattern(
        regexp = "^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s.,!?;:()'\"&%$#*/\\[\\]{}\\-_]*$",
        message = "Recipe name must start with a letter or number and contain only allowed characters"
    )
    private String name;
    
    // Optional field with length constraint
    @Size(max = 5000, message = "Description must be less than 5000 characters")
    @Pattern(
        regexp = "^$|^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s.,!?;:()'\"&%$#*/\\[\\]{}\\-_]*$",
        message = "Description must start with a letter or number and contain only allowed characters"
    )
    private String description;
    
    // Prevent mass assignment - status cannot be set via JSON
    @JsonIgnore
    private RecipeStatus status;
    
    // URL validation
    @Pattern(
        regexp = "^$|^https?://.*",
        message = "Image URL must be a valid HTTP or HTTPS URL"
    )
    private String imageUrl;
}
```

### Step 2: Use @Valid in Controller

```java
package com.example.api.controllers;

import com.example.api.dto.RecipeRequest;
import com.example.api.dto.RecipeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    /**
     * Create recipe from JSON request.
     * Bean Validation automatically validates the request body.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecipeResponse> createRecipe(
            @Valid @RequestBody RecipeRequest request) {
        // If validation fails, Spring automatically returns 400 Bad Request
        // If validation passes, this code executes
        RecipeResponse response = recipeService.createRecipe(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecipeResponse> updateRecipe(
            @PathVariable Long id,
            @Valid @RequestBody RecipeRequest request) {
        RecipeResponse response = recipeService.updateRecipe(id, request);
        return ResponseEntity.ok(response);
    }
}
```

### Step 3: Common Bean Validation Annotations

| Annotation | Purpose | Example |
|------------|---------|---------|
| `@NotNull` | Field cannot be null | `@NotNull String name` |
| `@NotBlank` | String cannot be null, empty, or whitespace | `@NotBlank String name` |
| `@NotEmpty` | Collection/String cannot be empty | `@NotEmpty List<String> items` |
| `@Size(min, max)` | String/Collection size constraint | `@Size(min=1, max=100)` |
| `@Min(value)` | Number minimum value | `@Min(0) Integer quantity` |
| `@Max(value)` | Number maximum value | `@Max(1000) Integer quantity` |
| `@Email` | Valid email format | `@Email String email` |
| `@Pattern(regexp)` | Matches regex pattern | `@Pattern(regexp="^[A-Z].*")` |
| `@Positive` | Number must be positive | `@Positive Integer id` |
| `@PositiveOrZero` | Number must be positive or zero | `@PositiveOrZero Integer count` |
| `@Past` | Date must be in the past | `@Past LocalDate birthDate` |
| `@Future` | Date must be in the future | `@Future LocalDate expiryDate` |

### Bean Validation Flow

```
1. Client sends JSON: {"name": "=malicious", "description": "test"}
                           ↓
2. Spring deserializes JSON to RecipeRequest object
                           ↓
3. @Valid triggers Bean Validation
                           ↓
4. @Pattern validation fails on name
                           ↓
5. MethodArgumentNotValidException thrown
                           ↓
6. Spring returns 400 Bad Request with validation errors
```

---

## Layer 2: Custom Validation Utility

Bean Validation handles format, but we need additional security validation using an **allow-list approach**.

### Step 1: Create Input Validation Utility

Create `src/main/java/com/example/api/validation/InputValidator.java`:

```java
package com.example.api.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Input validation utility using ALLOW-LIST approach.
 * 
 * This is MORE SECURE than block-listing dangerous characters because:
 * 1. Protects against unknown attack vectors
 * 2. No need to track new dangerous characters
 * 3. Clear definition of acceptable input
 * 
 * Use this for security-critical validation (CSV injection, XSS, etc.)
 */
@Slf4j
@Component
public class InputValidator {
    
    // ALLOW-LIST: Only these characters are permitted
    // Includes: letters (any language), numbers, spaces, common punctuation
    private static final String ALLOWED_CHARS_PATTERN = 
        "^[\\p{L}\\p{N}\\s.,!?;:()'\"&%$#*/\\[\\]{}\\-_]+$";
    
    // Must start with letter or number (prevents formula injection)
    private static final String SAFE_START_PATTERN = "^[\\p{L}\\p{N}].*";
    
    /**
     * Validates that input contains only allowed characters.
     * 
     * @param input The string to validate
     * @return true if valid (contains only allowed characters)
     */
    public boolean isValid(String input) {
        if (input == null || input.isEmpty()) {
            return true;  // null/empty is considered valid
        }
        
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        
        return trimmed.matches(ALLOWED_CHARS_PATTERN);
    }
    
    /**
     * Validates that input starts with a safe character.
     * Prevents CSV injection and formula attacks.
     * 
     * @param input The string to validate
     * @return true if starts with letter or number
     */
    public boolean startsWithSafeChar(String input) {
        if (input == null || input.isEmpty()) {
            return true;
        }
        
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        
        return trimmed.matches(SAFE_START_PATTERN);
    }
    
    /**
     * Validates and sanitizes input.
     * Throws exception if validation fails.
     * 
     * @param input The string to validate
     * @param fieldName Name of the field (for error messages)
     * @return The validated and trimmed input
     * @throws IllegalArgumentException if validation fails
     */
    public String validateAndSanitize(String input, String fieldName) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        
        // Check if starts with safe character
        if (!startsWithSafeChar(trimmed)) {
            char firstChar = trimmed.charAt(0);
            log.warn("Validation failed for {}: starts with unsafe character '{}'", 
                fieldName, firstChar);
            throw new IllegalArgumentException(
                String.format("%s must start with a letter or number. Found: '%c'", 
                    fieldName, firstChar)
            );
        }
        
        // Check if contains only allowed characters
        if (!isValid(trimmed)) {
            log.warn("Validation failed for {}: contains disallowed characters", fieldName);
            throw new IllegalArgumentException(
                String.format("%s contains invalid characters. Allowed: %s", 
                    fieldName, getAllowedCharsDescription())
            );
        }
        
        return trimmed;
    }
    
    /**
     * Sanitizes input by removing disallowed characters.
     * Use this if you want to clean input rather than reject it.
     * 
     * WARNING: Sanitizing can lead to unexpected behavior.
     * Rejecting invalid input (validateAndSanitize) is usually better.
     * 
     * @param input The string to sanitize
     * @return String with only allowed characters
     */
    public String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        
        // Remove disallowed characters
        String sanitized = trimmed.replaceAll(
            "[^\\p{L}\\p{N}\\s.,!?;:()'\"&%$#*/\\[\\]{}\\-_]", 
            ""
        );
        
        if (!sanitized.equals(trimmed)) {
            log.info("Input sanitized - removed {} characters", 
                trimmed.length() - sanitized.length());
        }
        
        return sanitized;
    }
    
    /**
     * Gets human-readable description of allowed characters.
     * 
     * @return Description for user-facing error messages
     */
    public String getAllowedCharsDescription() {
        return "letters, numbers, spaces, and punctuation (.,!?;:()'\"&%$#*/[]{}-_)";
    }
    
    /**
     * Validates a URL format.
     * 
     * @param url The URL to validate
     * @return true if valid URL format
     */
    public boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) {
            return true;
        }
        
        return url.matches("^https?://.*");
    }
    
    /**
     * Validates an email format (basic check).
     * 
     * @param email The email to validate
     * @return true if valid email format
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return true;
        }
        
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }
}
```

### Step 2: Use InputValidator in Code

```java
@Service
@RequiredArgsConstructor
public class RecipeService {
    
    private final RecipeRepository recipeRepository;
    private final InputValidator inputValidator;
    
    public RecipeResponse createRecipe(RecipeRequest request) {
        // Additional validation beyond Bean Validation
        String safeName = inputValidator.validateAndSanitize(
            request.getName(), 
            "Recipe name"
        );
        
        String safeDescription = null;
        if (request.getDescription() != null && !request.getDescription().isEmpty()) {
            safeDescription = inputValidator.validateAndSanitize(
                request.getDescription(),
                "Description"
            );
        }
        
        Recipe recipe = Recipe.builder()
                .name(safeName)
                .description(safeDescription)
                .status(RecipeStatus.NEW)
                .imageUrl(request.getImageUrl())
                .build();
        
        Recipe saved = recipeRepository.save(recipe);
        return mapToResponse(saved);
    }
}
```

---

## Layer 3: Controller-Level Validation

Controller validation handles request-specific concerns like file uploads, headers, and query parameters.

### File Upload Validation

```java
@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;
    private final InputValidator inputValidator;
    
    // Configuration (could be in application.properties)
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RecipeResponse> createRecipeWithFile(
            @RequestPart("name") String name,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        
        // Validate name
        name = inputValidator.validateAndSanitize(name, "Recipe name");
        
        // Validate description
        if (description != null && !description.isEmpty()) {
            description = inputValidator.validateAndSanitize(description, "Description");
        }
        
        // Validate file if provided
        if (file != null && !file.isEmpty()) {
            validateImageFile(file);
        }
        
        RecipeRequest request = RecipeRequest.builder()
                .name(name)
                .description(description)
                .status(RecipeStatus.NEW)
                .build();
        
        if (file != null && !file.isEmpty()) {
            String imageUrl = recipeService.uploadImage(file);
            request.setImageUrl(imageUrl);
        }
        
        RecipeResponse response = recipeService.createRecipe(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    /**
     * Validates image file upload.
     * Checks: file not empty, correct MIME type, size limit, file extension.
     */
    private void validateImageFile(MultipartFile file) {
        // Check file is not empty
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        
        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                String.format("File size exceeds maximum allowed size of %d MB", 
                    MAX_FILE_SIZE / (1024 * 1024))
            );
        }
        
        // Check MIME type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                "Invalid file type. Allowed types: " + ALLOWED_IMAGE_TYPES
            );
        }
        
        // Check file extension
        String filename = file.getOriginalFilename();
        if (filename == null || !hasValidImageExtension(filename)) {
            throw new IllegalArgumentException(
                "Invalid file extension. Allowed: .jpg, .jpeg, .png, .gif, .webp"
            );
        }
    }
    
    private boolean hasValidImageExtension(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || 
               lower.endsWith(".png") || lower.endsWith(".gif") || 
               lower.endsWith(".webp");
    }
}
```

### CSV File Validation

```java
/**
 * Validates CSV file upload.
 */
private void validateCsvFile(MultipartFile file) {
    // Check file is not empty
    if (file.isEmpty()) {
        throw new IllegalArgumentException("CSV file is empty");
    }
    
    // Check file size (5MB max)
    if (file.getSize() > MAX_FILE_SIZE) {
        throw new IllegalArgumentException("CSV file too large. Maximum size: 5MB");
    }
    
    // Check MIME type
    String contentType = file.getContentType();
    String filename = file.getOriginalFilename();
    
    if ((contentType == null || !contentType.equals("text/csv")) 
        && (filename == null || !filename.endsWith(".csv"))) {
        throw new IllegalArgumentException("File must be a CSV file");
    }
    
    // Optional: Check row count
    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(file.getInputStream()))) {
        long lineCount = reader.lines().count();
        if (lineCount > 1000) {
            throw new IllegalArgumentException(
                "CSV file too large. Maximum rows: 1000"
            );
        }
    } catch (IOException e) {
        throw new RuntimeException("Failed to validate CSV file", e);
    }
}
```

---

## Layer 4: Service-Level Validation

Service layer handles business logic validation.

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeService {
    
    private final RecipeRepository recipeRepository;
    private final InputValidator inputValidator;
    
    public RecipeResponse createRecipe(RecipeRequest request) {
        // Security validation
        String safeName = inputValidator.validateAndSanitize(
            request.getName(), 
            "Recipe name"
        );
        
        String safeDescription = null;
        if (request.getDescription() != null && !request.getDescription().isEmpty()) {
            safeDescription = inputValidator.validateAndSanitize(
                request.getDescription(),
                "Description"
            );
        }
        
        // Business validation
        validateBusinessRules(safeName, request);
        
        Recipe recipe = Recipe.builder()
                .name(safeName)
                .description(safeDescription)
                .status(RecipeStatus.NEW)
                .imageUrl(request.getImageUrl())
                .build();
        
        Recipe saved = recipeRepository.save(recipe);
        log.info("Created recipe: id={}, name={}", saved.getId(), saved.getName());
        
        return mapToResponse(saved);
    }
    
    /**
     * Business logic validation.
     */
    private void validateBusinessRules(String name, RecipeRequest request) {
        // Check for duplicate names
        if (recipeRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException(
                "Recipe with name '" + name + "' already exists"
            );
        }
        
        // Validate image URL if provided
        if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
            if (!inputValidator.isValidUrl(request.getImageUrl())) {
                throw new IllegalArgumentException("Invalid image URL format");
            }
        }
        
        // Additional business rules...
    }
}
```

---

## CSV Import Validation

CSV imports require special handling because they bypass Bean Validation.

```java
@PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<Map<String, Object>> importRecipesFromCsv(
        @RequestPart("file") MultipartFile file) {
    
    validateCsvFile(file);
    
    try {
        List<RecipeResponse> importedRecipes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int lineNumber = 0;
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream()))) {
            
            String line;
            // Read and validate header
            String header = reader.readLine();
            lineNumber++;
            
            if (header == null) {
                throw new IllegalArgumentException("CSV file is empty");
            }
            
            if (!header.trim().equals("name,description,imageUrl")) {
                log.warn("Unexpected CSV header: {}", header);
            }
            
            // Process each line
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    RecipeRequest request = parseCsvLine(line);
                    RecipeResponse response = recipeService.createRecipe(request);
                    importedRecipes.add(response);
                } catch (Exception e) {
                    String errorMsg = "Line " + lineNumber + ": " + e.getMessage();
                    errors.add(errorMsg);
                    log.error("CSV import error: {}", errorMsg);
                }
            }
        }
        
        return ResponseEntity.ok(Map.of(
            "message", "CSV import completed",
            "imported", importedRecipes.size(),
            "errors", errors.size(),
            "errorDetails", errors,
            "recipes", importedRecipes
        ));
        
    } catch (IOException e) {
        throw new RuntimeException("Failed to read CSV file", e);
    }
}

/**
 * Parses CSV line with validation.
 * This is where we apply InputValidator for CSV imports.
 */
private RecipeRequest parseCsvLine(String line) {
    String[] parts = line.split(",", -1);
    
    if (parts.length < 1) {
        throw new IllegalArgumentException("Invalid CSV format");
    }
    
    String name = parts[0].trim();
    if (name.isEmpty()) {
        throw new IllegalArgumentException("Recipe name cannot be empty");
    }
    
    // CRITICAL: Validate using allow-list approach
    name = inputValidator.validateAndSanitize(name, "Recipe name");
    
    String description = parts.length > 1 ? parts[1].trim() : "";
    if (!description.isEmpty()) {
        description = inputValidator.validateAndSanitize(description, "Description");
    }
    
    String imageUrl = parts.length > 2 ? parts[2].trim() : null;
    
    return RecipeRequest.builder()
            .name(name)
            .description(description.isEmpty() ? null : description)
            .status(RecipeStatus.NEW)  // Always NEW for security
            .imageUrl(imageUrl)
            .build();
}
```

---

## Multipart Form Validation

Validating multipart form data requires manual parameter validation.

```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<RecipeResponse> createRecipeWithFile(
        @RequestPart("name") String name,
        @RequestPart(value = "description", required = false) String description,
        @RequestPart(value = "file", required = false) MultipartFile file) {
    
    // Manual validation (Bean Validation doesn't work on @RequestPart String)
    
    // 1. Validate name
    if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Recipe name is required");
    }
    if (name.length() > 255) {
        throw new IllegalArgumentException("Recipe name must be less than 255 characters");
    }
    name = inputValidator.validateAndSanitize(name, "Recipe name");
    
    // 2. Validate description
    if (description != null && !description.isEmpty()) {
        if (description.length() > 5000) {
            throw new IllegalArgumentException("Description must be less than 5000 characters");
        }
        description = inputValidator.validateAndSanitize(description, "Description");
    }
    
    // 3. Validate file
    if (file != null && !file.isEmpty()) {
        validateImageFile(file);
    }
    
    // Build request and process
    RecipeRequest request = RecipeRequest.builder()
            .name(name)
            .description(description)
            .status(RecipeStatus.NEW)
            .build();
    
    if (file != null && !file.isEmpty()) {
        String imageUrl = recipeService.uploadImage(file);
        request.setImageUrl(imageUrl);
    }
    
    RecipeResponse response = recipeService.createRecipe(request);
    return new ResponseEntity<>(response, HttpStatus.CREATED);
}
```

---

## Query Parameter Validation

Query parameters can also be validated.

```java
@RestController
@RequestMapping("/api/recipes")
@Validated  // Enable validation on method parameters
@RequiredArgsConstructor
public class RecipeController {

    @GetMapping("/search")
    public ResponseEntity<List<RecipeResponse>> searchRecipes(
            @RequestParam 
            @NotBlank(message = "Search query cannot be empty")
            @Size(min = 1, max = 100, message = "Search query must be between 1 and 100 characters")
            String query) {
        
        // Additional validation
        String safeQuery = inputValidator.validateAndSanitize(query, "Search query");
        
        List<RecipeResponse> results = recipeService.searchRecipesByName(safeQuery);
        return ResponseEntity.ok(results);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<RecipeResponse> getRecipeById(
            @PathVariable 
            @Positive(message = "Recipe ID must be positive")
            Long id) {
        
        RecipeResponse recipe = recipeService.getRecipeById(id);
        return ResponseEntity.ok(recipe);
    }
    
    @PatchMapping("/{id}/status")
    public ResponseEntity<RecipeResponse> updateRecipeStatus(
            @PathVariable @Positive Long id,
            @RequestParam @NotNull RecipeStatus status) {
        
        RecipeResponse updated = recipeService.updateRecipeStatus(id, status);
        return ResponseEntity.ok(updated);
    }
}
```

**Important:** Add `@Validated` annotation to the controller class to enable validation on method parameters.

---

## Global Exception Handling

Create a global exception handler to return consistent error responses.

Create `src/main/java/com/example/api/exception/GlobalExceptionHandler.java`:

```java
package com.example.api.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles Bean Validation errors from @Valid @RequestBody.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        
        Map<String, String> errors = new