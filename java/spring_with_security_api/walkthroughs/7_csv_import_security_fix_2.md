# Security Fix Guide - CSV Injection


If you followed the previous tutorials, your CSV import feature has 
**CSV Injection** vulnerability - Malicious formulas can execute when CSV is opened in Excel


## Vulnerability Summary

**What:** Malicious formulas execute when CSV is opened in spreadsheet applications.

**Example Attack:**
```csv
name,description,imageUrl
=cmd|'/c calc'!A1,Description,image.jpg
```
☝️ Opens calculator (or worse: steals data, installs malware)

**Impact:** Remote code execution, data theft, malware

---

## Complete Code Fix

### Step 1: Create CSV Injection Prevention Utility

Create `src/main/java/com/example/api/security/CsvInjectionPrevention.java`:

```java
package com.example.api.security;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Prevents CSV Injection (Formula Injection) attacks using ALLOW-LIST approach.
 * 
 * CSV injection occurs when user input starting with special characters
 * (=, +, -, @, tab, CR) is included in CSV files. When opened in Excel,
 * these can execute as formulas, potentially leading to:
 * - Remote code execution
 * - Data exfiltration
 * - Malware installation
 * 
 * This class uses an ALLOW-LIST approach: only explicitly allowed characters
 * are permitted. This is more secure than blocklisting dangerous characters
 * because it protects against unknown attack vectors.
 */
@Slf4j
@Component
public class CsvInjectionPrevention {
    
    // ALLOW-LIST: Characters explicitly allowed in recipe names and descriptions
    // Includes: letters (any language), numbers, common punctuation, spaces
    private static final String ALLOWED_CHARS_PATTERN = 
        "^[\\p{L}\\p{N}\\s.,!?;:()'\"&%$#*/\\[\\]{}\\-_]+$";
    
    // Pattern to detect if string starts with safe characters
    private static final String SAFE_START_PATTERN = "^[\\p{L}\\p{N}].*";
    
    /**
     * Validates input using ALLOW-LIST approach.
     * Only allows alphanumeric characters, spaces, and common safe punctuation.
     * 
     * SECURITY: This is the RECOMMENDED approach as it's more secure than
     * blocklisting. It protects against unknown attack vectors.
     * 
     * @param input The string to validate
     * @return true if input contains only allowed characters
     */
    public boolean isValid(String input) {
        if (input == null || input.isEmpty()) {
            return true;  // Empty is valid
        }
        
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        
        // Check if contains only allowed characters
        return trimmed.matches(ALLOWED_CHARS_PATTERN);
    }
    
    /**
     * Validates that input starts with a safe character (letter or number).
     * This is a minimal check that still uses allow-list principle.
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
     * Sanitizes input by removing all non-allowed characters.
     * Uses ALLOW-LIST approach for maximum security.
     * 
     * @param input The string to sanitize
     * @return String containing only allowed characters
     */
    public String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        
        // Remove any characters not in the allow-list
        String sanitized = trimmed.replaceAll("[^\\p{L}\\p{N}\\s.,!?;:()'\"&%$#*/\\[\\]{}\\-_]", "");
        
        if (!sanitized.equals(trimmed)) {
            log.warn("Input sanitized - removed non-allowed characters. Original length: {}, New length: {}", 
                trimmed.length(), sanitized.length());
        }
        
        return sanitized;
    }
    
    /**
     * Validates and sanitizes input with detailed error messages.
     * Throws exception if input is invalid, making it easy to reject bad data.
     * 
     * @param input The string to validate
     * @param fieldName Name of the field (for error messages)
     * @throws IllegalArgumentException if input contains disallowed characters
     * @return The validated input (trimmed)
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
            log.error("Invalid {} - starts with unsafe character: {}", fieldName, firstChar);
            throw new IllegalArgumentException(
                String.format("%s must start with a letter or number. Found: '%c'", 
                    fieldName, firstChar)
            );
        }
        
        // Check if contains only allowed characters
        if (!isValid(trimmed)) {
            log.error("Invalid {} - contains disallowed characters", fieldName);
            throw new IllegalArgumentException(
                String.format("%s contains invalid characters. Only letters, numbers, " +
                    "spaces, and common punctuation (.,!?;:()'\"-_) are allowed.", fieldName)
            );
        }
        
        return trimmed;
    }
    
    /**
     * Gets description of allowed characters for user-facing messages.
     * 
     * @return Human-readable description of allowed characters
     */
    public String getAllowedCharsDescription() {
        return "letters, numbers, spaces, and punctuation (.,!?;:()'\"&%$#*/[]{}-_)";
    }
}
```

### Step 2: Update RecipeController

Update `src/main/java/com/example/api/controllers/RecipeController.java`:

```java
package com.example.api.controllers;

import com.example.api.dto.RecipeRequest;
import com.example.api.dto.RecipeResponse;
import com.example.api.models.Recipe.RecipeStatus;
import com.example.api.security.CsvInjectionPrevention;
import com.example.api.services.RecipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;
    private final CsvInjectionPrevention csvInjectionPrevention;

    // ... other existing methods ...

    /**
     * Import recipes from CSV file.
     * 
     * SECURITY NOTES:
     * - All recipes created with status=NEW (prevents mass assignment)
     * - Input sanitized for CSV injection (prevents formula execution)
     * - Status must be changed through separate authorized endpoint
     * 
     * CSV Format: name,description,imageUrl
     * 
     * @param file CSV file to import
     * @return Import results with success/error counts
     */
    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @io.swagger.v3.oas.annotations.Operation(
        summary = "Import recipes from CSV file",
        description = "Imports recipes from CSV. Format: name,description,imageUrl. " +
                      "All recipes will have status NEW for security. " +
                      "Status changes must go through proper approval workflow."
    )
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
                // Skip header line
                String header = reader.readLine();
                lineNumber++;
                
                if (header == null) {
                    throw new IllegalArgumentException("CSV file is empty");
                }
                
                // Validate header format
                if (!header.trim().equals("name,description,imageUrl")) {
                    log.warn("Unexpected CSV header format: {}", header);
                }
                
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
                        log.error("CSV import error on line {}: {}", lineNumber, e.getMessage());
                    }
                }
            }
            
            log.info("CSV import completed: {} imported, {} errors", 
                importedRecipes.size(), errors.size());
            
            Map<String, Object> result = Map.of(
                "message", "CSV import completed",
                "imported", importedRecipes.size(),
                "errors", errors.size(),
                "errorDetails", errors,
                "recipes", importedRecipes
            );
            
            return ResponseEntity.ok(result);
            
        } catch (IOException e) {
            log.error("Failed to read CSV file", e);
            throw new RuntimeException("Failed to read CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a single CSV line into a RecipeRequest.
     * 
     * SECURITY PROTECTIONS:
     * 1. Always sets status=NEW (prevents mass assignment)
     * 2. Validates input using ALLOW-LIST (prevents CSV injection and unknown attacks)
     * 
     * @param line CSV line to parse
     * @return RecipeRequest object
     */
    private RecipeRequest parseCsvLine(String line) {
        String[] parts = line.split(",", -1);
        
        if (parts.length < 1) {
            throw new IllegalArgumentException("Invalid CSV format: missing name");
        }
        
        String name = parts[0].trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Recipe name cannot be empty");
        }
        
        // SECURITY FIX 1: Prevent CSV Injection using ALLOW-LIST
        // Validate that name contains only safe characters
        // This protects against ALL injection vectors, not just known ones
        name = csvInjectionPrevention.validateAndSanitize(name, "Recipe name");
        
        String description = parts.length > 1 ? parts[1].trim() : "";
        
        // SECURITY FIX 1: Prevent CSV Injection using ALLOW-LIST
        // Validate description contains only safe characters
        if (!description.isEmpty()) {
            description = csvInjectionPrevention.validateAndSanitize(description, "Description");
        }
        
        // Note: imageUrl is at index 2 (no status column for security)
        String imageUrl = parts.length > 2 ? parts[2].trim() : null;
        
        RecipeRequest.RecipeRequestBuilder builder = RecipeRequest.builder()
                .name(name)
                .description(description.isEmpty() ? null : description)
                // SECURITY FIX 2: Prevent Mass Assignment
                // Always set status to NEW - users cannot approve own recipes
                .status(RecipeStatus.NEW);
        
        if (imageUrl != null && !imageUrl.isEmpty()) {
            try {
                builder.imageUrl(recipeService.uploadImageFromUrl(imageUrl));
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to upload image from URL: " + e.getMessage());
            }
        }
        
        return builder.build();
    }

    private void validateCsvFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty");
        }
        
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        
        if ((contentType == null || !contentType.equals("text/csv")) 
            && (filename == null || !filename.endsWith(".csv"))) {
            throw new IllegalArgumentException("File must be a CSV file");
        }
        
        // Add size limit (5MB)
        long maxSize = 5 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("CSV file too large. Maximum size: 5MB");
        }
    }
    
    // ... other existing methods ...
}
```

### Step 3: Add Validation to RecipeRequest (Not for csv import, other scenarios)

Update `src/main/java/com/example/api/dto/RecipeRequest.java`:

```java
package com.example.api.dto;

import com.example.api.models.Recipe.RecipeStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecipeRequest {
    
    @NotBlank(message = "Recipe name is required")
    @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    @Pattern(
        regexp = "^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s.,!?;:()'\"&%$#*/\\[\\]{}\\-_]*$",
        message = "Recipe name must start with a letter or number and contain only allowed characters"
    )
    private String name;
    
    @Size(max = 5000, message = "Description must be less than 5000 characters")
    @Pattern(
        regexp = "^$|^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s.,!?;:()'\"&%$#*/\\[\\]{}\\-_]*$",
        message = "Description must start with a letter or number and contain only allowed characters"
    )
    private String description;
    
    // Don't allow status to be set directly via JSON
    // (it can still be set programmatically in code)
    @JsonIgnore
    private RecipeStatus status;
    
    private String imageUrl;
}
```

### Step 4: Update RecipeService (Defense in Depth)

Add sanitization at service layer too:

```java
package com.example.api.services;

import com.example.api.dto.RecipeRequest;
import com.example.api.dto.RecipeResponse;
import com.example.api.models.Recipe;
import com.example.api.models.Recipe.RecipeStatus;
import com.example.api.repositories.RecipeRepository;
import com.example.api.security.CsvInjectionPrevention;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeService {
    
    private final RecipeRepository recipeRepository;
    private final CsvInjectionPrevention csvInjectionPrevention;
    
    public RecipeResponse createRecipe(RecipeRequest request) {
        // Defense in depth: Validate using ALLOW-LIST at service layer too
        String safeName = csvInjectionPrevention.validateAndSanitize(
            request.getName(), 
            "Recipe name"
        );
        
        String safeDescription = null;
        if (request.getDescription() != null && !request.getDescription().isEmpty()) {
            safeDescription = csvInjectionPrevention.validateAndSanitize(
                request.getDescription(),
                "Description"
            );
        }
        
        Recipe recipe = Recipe.builder()
                .name(safeName)
                .description(safeDescription)
                .status(request.getStatus() != null ? request.getStatus() : RecipeStatus.NEW)
                .imageUrl(request.getImageUrl())
                .build();
        
        Recipe saved = recipeRepository.save(recipe);
        log.info("Created recipe: id={}, name={}", saved.getId(), saved.getName());
        
        return mapToResponse(saved);
    }
    
    // ... other methods ...
}
```

---

## Testing

### Test 1: CSV Injection Protection (Allow-List)

**Test CSV with various injection attempts:**
```csv
name,description,imageUrl
=1+1,Normal description,
+2+2,=cmd|'/c calc',
-3+3,@SUM(1+1),
Normal Recipe,Normal description,
Recipe123,Good description!,
<script>alert('xss')</script>,Malicious script,
```

**Expected Results:**
- Row 1: **REJECTED** - name starts with `=`
- Row 2: **REJECTED** - name starts with `+`
- Row 3: **REJECTED** - name starts with `-`
- Row 4: **ACCEPTED** - name = `Normal Recipe`, description = `Normal description`
- Row 5: **ACCEPTED** - name = `Recipe123`, description = `Good description!`
- Row 6: **REJECTED** - name contains `<>` which are not in allow-list

**Error messages should specify:**
```
Line 2: Recipe name must start with a letter or number. Found: '='
Line 3: Recipe name must start with a letter or number. Found: '+'
Line 4: Recipe name must start with a letter or number. Found: '-'
Line 7: Recipe name contains invalid characters. Only letters, numbers, spaces, and common punctuation are allowed.
```
---
