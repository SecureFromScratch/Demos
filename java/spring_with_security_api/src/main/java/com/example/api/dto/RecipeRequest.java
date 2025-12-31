package com.example.api.dto;

import com.example.api.models.Recipe.RecipeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Request DTO for creating/updating recipes
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeRequest {
    
    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;
    
    private String description;
    
    private RecipeStatus status;
    
    private String imageUrl;
}

