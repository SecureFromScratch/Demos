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

## Layer 3: Controller-Level & Service-Level Validation

Controller validation handles request-specific concerns like file uploads, headers, and query parameters. Here the validation is according to the use cases, file upload, CSV, forms etc.

---

## Global Exception Handling

Create a global exception handler to return consistent error responses.

Create `src/main/java/com/example/api/exception/GlobalExceptionHandler.java`:

``` java
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles Bean Validation errors from @Valid @RequestBody.
     * This catches validation errors on DTOs/request objects.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        
        Map<String, String> errors = new HashMap<>();
        
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        log.warn("Validation failed: {}", errors);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Input validation error. Please check your request.")
                .fieldErrors(errors)
                .build();
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    /**
     * Handles validation errors from @Validated on method parameters.
     * This catches validation errors on @PathVariable, @RequestParam, etc.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex) {
        
        Map<String, String> errors = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                    violation -> violation.getPropertyPath().toString(),
                    ConstraintViolation::getMessage
                ));
        
        log.warn("Constraint violation: {}", errors);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Constraint Violation")
                .message("Input validation error on request parameters.")
                .fieldErrors(errors)
                .build();
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    /**
     * Handles type mismatch errors (e.g., passing string where number expected).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        
        String error = String.format("Parameter '%s' should be of type %s", 
                ex.getName(), 
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        
        log.warn("Type mismatch: {}", error);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Type Mismatch")
                .message(error)
                .build();
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    /**
     * Handles missing request parameters.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex) {
        
        String error = String.format("Required parameter '%s' is missing", ex.getParameterName());
        
        log.warn("Missing parameter: {}", error);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Missing Parameter")
                .message(error)
                .build();
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    /**
     * Handles malformed JSON or unreadable request body.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex) {
        
        log.warn("Malformed JSON request: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Malformed JSON")
                .message("Request body is malformed or contains invalid JSON.")
                .build();
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    /**
     * Handles HTTP method not supported (e.g., POST when only GET is allowed).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        
        String error = String.format("Method %s is not supported for this endpoint. Supported methods: %s",
                ex.getMethod(),
                ex.getSupportedHttpMethods());
        
        log.warn("Method not supported: {}", error);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                .error("Method Not Allowed")
                .message(error)
                .build();
        
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(errorResponse);
    }

    /**
     * Handles unsupported media type (e.g., sending XML when only JSON is accepted).
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex) {
        
        String error = String.format("Media type %s is not supported. Supported types: %s",
                ex.getContentType(),
                ex.getSupportedMediaTypes());
        
        log.warn("Media type not supported: {}", error);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value())
                .error("Unsupported Media Type")
                .message(error)
                .build();
        
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(errorResponse);
    }

    /**
     * Handles 404 Not Found errors.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoHandlerFoundException ex) {
        
        String error = String.format("Endpoint %s %s not found",
                ex.getHttpMethod(),
                ex.getRequestURL());
        
        log.warn("Endpoint not found: {}", error);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(error)
                .build();
        
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }

    /**
     * Handles custom business logic exceptions.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex) {
        
        log.warn("Resource not found: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Resource Not Found")
                .message(ex.getMessage())
                .build();
        
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }

    /**
     * Handles custom business logic exceptions for conflicts.
     */
    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleResourceAlreadyExists(
            ResourceAlreadyExistsException ex) {
        
        log.warn("Resource already exists: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Resource Already Exists")
                .message(ex.getMessage())
                .build();
        
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }

    /**
     * Handles illegal argument exceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex) {
        
        log.warn("Illegal argument: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Invalid Argument")
                .message(ex.getMessage())
                .build();
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    /**
     * Catches all other unexpected exceptions.
     * Important: Never expose internal error details to clients in production.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {
        
        log.error("Unexpected error occurred", ex);
        
        // Don't expose internal error details in production
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please try again later.")
                .build();
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }
}

// ============================================
// ERROR RESPONSE DTO
// ============================================

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private Map<String, String> fieldErrors;
    private String path;
}

// ============================================
// CUSTOM EXCEPTIONS
// ============================================

class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

class ResourceAlreadyExistsException extends RuntimeException {
    public ResourceAlreadyExistsException(String message) {
        super(message);
    }
}
```