package com.example.api.dto;

import com.example.api.models.Recipe.RecipeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

/**
 * This class is only used for Swagger/OpenAPI documentation.
 * It helps Swagger UI render the file upload form correctly.
 * It's NOT used in the actual controller logic.
 */
@Schema(description = "Recipe creation/update with file upload")
public class RecipeWithFileRequest {
    
    @Schema(description = "Recipe name", example = "Chocolate Cake", required = true)
    private String name;
    
    @Schema(description = "Recipe description", example = "Delicious chocolate cake recipe")
    private String description;
    
    @Schema(description = "Recipe status", example = "NEW")
    private RecipeStatus status;
    
    @Schema(description = "Image file to upload", type = "string", format = "binary")
    private MultipartFile file;
}