package com.example.api.services;

import com.example.api.dto.RecipeRequest;
import com.example.api.dto.RecipeResponse;
import com.example.api.models.Recipe;
import com.example.api.models.Recipe.RecipeStatus;
import com.example.api.data.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.owasp.untrust.boxedpath.BoxedPath;
import org.owasp.untrust.boxedpath.PathSandbox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {
    
    private final RecipeRepository recipeRepository;
    
    @Value("${app.upload.dir:uploads/images}")
    private String uploadDir;
    
    @Value("${app.upload.max-size:10485760}") // 10MB default
    private long maxFileSize;
    
    // Allowed extensions
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    
    // Magic bytes for image file types
    private static final Map<String, byte[]> MAGIC_BYTES = new HashMap<>();
    
    static {
        MAGIC_BYTES.put("jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        MAGIC_BYTES.put("jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        MAGIC_BYTES.put("png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        MAGIC_BYTES.put("gif", new byte[]{0x47, 0x49, 0x46, 0x38}); // GIF8
        MAGIC_BYTES.put("webp", new byte[]{0x52, 0x49, 0x46, 0x46}); // RIFF
    }
    
    @Transactional
    public RecipeResponse createRecipe(RecipeRequest request) {
        Recipe recipe = Recipe.builder()
                .name(request.getName())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : RecipeStatus.NEW)
                .imageUrl(request.getImageUrl())
                .build();
        
        Recipe savedRecipe = recipeRepository.save(recipe);
        return mapToResponse(savedRecipe);
    }
    
    public List<RecipeResponse> getAllRecipes() {
        return recipeRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    public RecipeResponse getRecipeById(Long id) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with id: " + id));
        return mapToResponse(recipe);
    }
    
    public List<RecipeResponse> getRecipesByStatus(RecipeStatus status) {
        return recipeRepository.findByStatus(status).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    public List<RecipeResponse> searchRecipesByName(String name) {
        return recipeRepository.findByNameContainingIgnoreCase(name).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public RecipeResponse updateRecipe(Long id, RecipeRequest request) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with id: " + id));
        
        recipe.setName(request.getName());
        recipe.setDescription(request.getDescription());
        
        if (request.getStatus() != null) {
            recipe.setStatus(request.getStatus());
        }
        
        if (request.getImageUrl() != null) {
            recipe.setImageUrl(request.getImageUrl());
        }
        
        Recipe updatedRecipe = recipeRepository.save(recipe);
        return mapToResponse(updatedRecipe);
    }
    
    @Transactional
    public RecipeResponse updateRecipeStatus(Long id, RecipeStatus status) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with id: " + id));
        
        recipe.setStatus(status);
        Recipe updatedRecipe = recipeRepository.save(recipe);
        return mapToResponse(updatedRecipe);
    }
    
    @Transactional
    public RecipeResponse updateRecipeImage(Long id, String imageUrl) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with id: " + id));
        
        if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
            deleteImageFile(recipe.getImageUrl());
        }
        
        recipe.setImageUrl(imageUrl);
        Recipe updatedRecipe = recipeRepository.save(recipe);
        return mapToResponse(updatedRecipe);
    }
    
    @Transactional
    public void deleteRecipe(Long id) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with id: " + id));
        
        if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
            deleteImageFile(recipe.getImageUrl());
        }
        
        recipeRepository.delete(recipe);
    }
    
    /**
     * SECURE FILE UPLOAD using OWASP BoxedPath
     * 
     * Security features:
     * 1. File size validation
     * 2. Path traversal protection via OWASP BoxedPath
     * 3. Magic bytes verification
     * 4. Extension whitelist
     * 5. Safe filename generation with UUID
     */
    public String uploadImage(MultipartFile file) {
        try {
            // 1. Validate file size
            if (file.getSize() > maxFileSize) {
                throw new FileStorageException("File size exceeds maximum allowed size of " + (maxFileSize / 1024 / 1024) + "MB");
            }
            
            // 2. Validate file is not empty
            if (file.isEmpty()) {
                throw new FileStorageException("Cannot upload empty file");
            }
            
            // 3. Validate original filename exists
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.trim().isEmpty()) {
                throw new FileStorageException("Invalid filename");
            }
            
            // 4. Extract and validate file extension
            String extension = getFileExtension(originalFilename).toLowerCase();
            if (extension.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension)) {
                throw new FileStorageException("Invalid file extension. Allowed: " + ALLOWED_EXTENSIONS);
            }
            
            // 5. Verify magic bytes - check actual file content matches extension
            if (!verifyMagicBytes(file, extension)) {
                throw new FileStorageException("File content doesn't match extension. Possible file spoofing attempt.");
            }
            
            // 6. Create upload directory
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);
            
            // 7. Generate safe filename using UUID to prevent injection attacks
            String safeFilename = UUID.randomUUID().toString() + "." + extension;
            
            // 8. Use OWASP BoxedPath to prevent path traversal attacks
            // BoxedPath ensures the resolved path stays within the upload directory            
            BoxedPath boxedPath = PathSandbox.boxroot(uploadPath).resolve(safeFilename);
            //String sanitizedName = boxedPath.toString();


            Path targetPath = Paths.get(boxedPath.toString());

            // 9. Write file securely
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            
            // 10. Return safe URL path
            return "/uploads/images/" + safeFilename;
            
        } catch (IOException e) {
            throw new FileStorageException("Failed to store file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extract file extension safely
     */
    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1);
        }
        return "";
    }
    
    /**
     * Verify file magic bytes match the expected extension
     * This prevents file type spoofing (e.g., renaming virus.exe to virus.jpg)
     */
    private boolean verifyMagicBytes(MultipartFile file, String extension) throws IOException {
        if (file == null || file.isEmpty() || extension == null) {
            return false;
        }
        
        String ext = extension.trim().toLowerCase(Locale.ROOT);
        
        // Get expected magic bytes for this extension
        byte[] expected = MAGIC_BYTES.get(ext);
        if (expected == null) {
            return false;
        }
        
        // Read actual magic bytes from file
        byte[] actual = new byte[expected.length];
        try (InputStream is = new BufferedInputStream(file.getInputStream())) {
            int offset = 0;
            while (offset < actual.length) {
                int bytesRead = is.read(actual, offset, actual.length - offset);
                if (bytesRead == -1) {
                    return false; // File is shorter than expected magic bytes
                }
                offset += bytesRead;
            }
        }
        
        // Compare expected vs actual magic bytes
        return Arrays.equals(actual, expected);
    }
    
    /**
     * Securely delete image file using OWASP BoxedPath
     */
    private void deleteImageFile(String imageUrl) {
        try {
            if (imageUrl == null || !imageUrl.startsWith("/uploads/images/")) {
                return;
            }
            
            String filename = imageUrl.substring("/uploads/images/".length());
            
            // Validate filename
            if (filename.isEmpty() || filename.contains("..")) {
                System.err.println("Invalid filename in delete operation: " + imageUrl);
                return;
            }
            
            // Use OWASP BoxedPath to prevent path traversal
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            BoxedPath boxedPath = PathSandbox.boxroot(uploadPath).resolve(filename);
            //Path filePath = boxedPath.toPath();
            Path filePath = Paths.get(boxedPath.toString());

            Files.deleteIfExists(filePath);
            
        } catch (IOException e) {
            // Log the error but don't fail the operation
            System.err.println("Failed to delete image file: " + imageUrl + " - " + e.getMessage());
        }
    }
    
    private RecipeResponse mapToResponse(Recipe recipe) {
        return RecipeResponse.builder()
                .id(recipe.getId())
                .name(recipe.getName())
                .description(recipe.getDescription())
                .status(recipe.getStatus())
                .imageUrl(recipe.getImageUrl())
                .createdAt(recipe.getCreatedAt())
                .updatedAt(recipe.getUpdatedAt())
                .build();
    }
}

// Custom Exceptions
class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

class FileStorageException extends RuntimeException {
    public FileStorageException(String message) {
        super(message);
    }
    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}