package com.example.api.controllers;

import com.example.api.dto.RecipeRequest;
import com.example.api.dto.RecipeResponse;
import com.example.api.models.Recipe.RecipeStatus;
import com.example.api.security.TrustedImageFetcher;
import com.example.api.services.RecipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    // Create recipe - handles both JSON and multipart
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecipeResponse> createRecipe(@Valid @RequestBody RecipeRequest request) throws IOException {
        RecipeRequest processed_request = buildRecipeRequest(request);
        RecipeResponse response = recipeService.createRecipe(processed_request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @io.swagger.v3.oas.annotations.Operation(summary = "Create recipe with image file upload")
    public ResponseEntity<RecipeResponse> createRecipeWithFile(
            @RequestPart("name") String name,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart(value = "status", required = false) String status,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        RecipeRequest request = buildRecipeRequest(name, description, status, file);
        RecipeResponse response = recipeService.createRecipe(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // Get operations
    @GetMapping
    public ResponseEntity<List<RecipeResponse>> getAllRecipes() {
        return ResponseEntity.ok(recipeService.getAllRecipes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecipeResponse> getRecipeById(@PathVariable Long id) {
        return ResponseEntity.ok(recipeService.getRecipeById(id));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<RecipeResponse>> getRecipesByStatus(@PathVariable RecipeStatus status) {
        return ResponseEntity.ok(recipeService.getRecipesByStatus(status));
    }

    @GetMapping("/search")
    public ResponseEntity<List<RecipeResponse>> searchRecipes(@RequestParam String name) {
        return ResponseEntity.ok(recipeService.searchRecipesByName(name));
    }

    // Update recipe - handles both JSON and multipart
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecipeResponse> updateRecipe(
            @PathVariable Long id,
            @Valid @RequestBody RecipeRequest request) {
        return ResponseEntity.ok(recipeService.updateRecipe(id, request));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @io.swagger.v3.oas.annotations.Operation(summary = "Update recipe with image file upload")
    public ResponseEntity<RecipeResponse> updateRecipeWithFile(
            @PathVariable Long id,
            @RequestPart("name") String name,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart(value = "status", required = false) String status,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        RecipeRequest request = buildRecipeRequest(name, description, status, file);
        return ResponseEntity.ok(recipeService.updateRecipe(id, request));
    }

    // Partial updates
    @PatchMapping("/{id}/status")
    public ResponseEntity<RecipeResponse> updateRecipeStatus(
            @PathVariable Long id,
            @RequestParam RecipeStatus status) {
        return ResponseEntity.ok(recipeService.updateRecipeStatus(id, status));
    }

    @PatchMapping("/{id}/image")
    public ResponseEntity<RecipeResponse> updateRecipeImage(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        validateImageFile(file);
        String imageUrl = recipeService.uploadImage(file);
        return ResponseEntity.ok(recipeService.updateRecipeImage(id, imageUrl));
    }

    // Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteRecipe(@PathVariable Long id) {
        recipeService.deleteRecipe(id);
        return ResponseEntity.ok(Map.of("message", "Recipe deleted successfully"));
    }

    // Standalone image upload
    @PostMapping("/upload-image")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestPart("file") MultipartFile file) {
        validateImageFile(file);
        String imageUrl = recipeService.uploadImage(file);
        return ResponseEntity.ok(Map.of(
                "imageUrl", imageUrl,
                "message", "Image uploaded successfully"));
    }

    private RecipeRequest buildRecipeRequest(RecipeRequest request) throws IOException {

        RecipeRequest.RecipeRequestBuilder builder = RecipeRequest.builder()
                .name(request.getName())
                .description(request.getDescription())
                .status(request.getStatus());

        String url = request.getImageUrl();
        if (url != null && !url.isBlank()) {
            builder.imageUrl(recipeService.uploadImageFromUrl(url));
        }

        return builder.build();
    }

    // Helper methods
    private RecipeRequest buildRecipeRequest(String name, String description, String status, MultipartFile file) {
        RecipeStatus recipeStatus = parseStatus(status);

        RecipeRequest.RecipeRequestBuilder builder = RecipeRequest.builder()
                .name(name)
                .description(description)
                .status(recipeStatus);

        if (file != null && !file.isEmpty()) {
            validateImageFile(file);
            String imageUrl = recipeService.uploadImage(file);
            builder.imageUrl(imageUrl);
        }

        return builder.build();
    }

    private RecipeStatus parseStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return RecipeStatus.NEW;
        }
        try {
            return RecipeStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return RecipeStatus.NEW;
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("File must be an image");
        }
    }
}