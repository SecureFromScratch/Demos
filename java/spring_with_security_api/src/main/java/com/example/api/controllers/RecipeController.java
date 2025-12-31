package com.example.api.controllers;


import com.example.api.dto.RecipeRequest;
import com.example.api.dto.RecipeResponse;
import com.example.api.models.Recipe.RecipeStatus;
import com.example.api.services.RecipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {
    
    private final RecipeService recipeService;
    
    // Create a new recipe
    @PostMapping
    public ResponseEntity<RecipeResponse> createRecipe(@Valid @RequestBody RecipeRequest request) {
        RecipeResponse response = recipeService.createRecipe(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    // Get all recipes
    @GetMapping
    public ResponseEntity<List<RecipeResponse>> getAllRecipes() {
        List<RecipeResponse> recipes = recipeService.getAllRecipes();
        return ResponseEntity.ok(recipes);
    }
    
    // Get recipe by ID
    @GetMapping("/{id}")
    public ResponseEntity<RecipeResponse> getRecipeById(@PathVariable Long id) {
        RecipeResponse response = recipeService.getRecipeById(id);
        return ResponseEntity.ok(response);
    }
    
    // Get recipes by status
    @GetMapping("/status/{status}")
    public ResponseEntity<List<RecipeResponse>> getRecipesByStatus(@PathVariable RecipeStatus status) {
        List<RecipeResponse> recipes = recipeService.getRecipesByStatus(status);
        return ResponseEntity.ok(recipes);
    }
    
    // Search recipes by name
    @GetMapping("/search")
    public ResponseEntity<List<RecipeResponse>> searchRecipes(@RequestParam String name) {
        List<RecipeResponse> recipes = recipeService.searchRecipesByName(name);
        return ResponseEntity.ok(recipes);
    }
    
    // Update recipe
    @PutMapping("/{id}")
    public ResponseEntity<RecipeResponse> updateRecipe(
            @PathVariable Long id,
            @Valid @RequestBody RecipeRequest request) {
        RecipeResponse response = recipeService.updateRecipe(id, request);
        return ResponseEntity.ok(response);
    }
    
    // Update recipe status
    @PatchMapping("/{id}/status")
    public ResponseEntity<RecipeResponse> updateRecipeStatus(
            @PathVariable Long id,
            @RequestParam RecipeStatus status) {
        RecipeResponse recipe = recipeService.getRecipeById(id);
        RecipeRequest request = new RecipeRequest();
        request.setName(recipe.getName());
        request.setDescription(recipe.getDescription());
        request.setStatus(status);
        request.setImageUrl(recipe.getImageUrl());
        
        RecipeResponse response = recipeService.updateRecipe(id, request);
        return ResponseEntity.ok(response);
    }
    
    // Delete recipe
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteRecipe(@PathVariable Long id) {
        recipeService.deleteRecipe(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Recipe deleted successfully");
        return ResponseEntity.ok(response);
    }
    
    // Upload image file
    @PostMapping("/upload-image")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }
        
        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "File must be an image"));
        }
        
        String imageUrl = recipeService.uploadImage(file);
        Map<String, String> response = new HashMap<>();
        response.put("imageUrl", imageUrl);
        response.put("message", "Image uploaded successfully");
        return ResponseEntity.ok(response);
    }
    
    // Create recipe with image upload
    @PostMapping("/with-image")
    public ResponseEntity<RecipeResponse> createRecipeWithImage(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "status", required = false) RecipeStatus status,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "imageUrl", required = false) String imageUrl) {
        
        RecipeRequest request = new RecipeRequest();
        request.setName(name);
        request.setDescription(description);
        request.setStatus(status != null ? status : RecipeStatus.NEW);
        
        // If file is provided, upload it
        if (file != null && !file.isEmpty()) {
            String uploadedImageUrl = recipeService.uploadImage(file);
            request.setImageUrl(uploadedImageUrl);
        } else if (imageUrl != null && !imageUrl.isEmpty()) {
            // Otherwise use the provided URL
            request.setImageUrl(imageUrl);
        }
        
        RecipeResponse response = recipeService.createRecipe(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}