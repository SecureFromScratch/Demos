package com.example.demo.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class FileValidationService {

    // Allowed extensions
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "jpg", "jpeg", "png"
    );

    // Magic bytes for common file types
    private static final Map<String, byte[]> MAGIC_BYTES = new HashMap<>();
    
    static {
        MAGIC_BYTES.put("pdf", new byte[]{0x25, 0x50, 0x44, 0x46}); // %PDF
        MAGIC_BYTES.put("jpg", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF});
        MAGIC_BYTES.put("png", new byte[]{(byte)0x89, 0x50, 0x4E, 0x47});
        MAGIC_BYTES.put("gif", new byte[]{0x47, 0x49, 0x46, 0x38}); // GIF8        
    }

    public ValidationResult validateFile(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        
        if (fileName == null || fileName.isEmpty()) {
            return ValidationResult.error("Filename is empty");
        }

        // 1. Check for path traversal
        if (containsPathTraversal(fileName)) {
            return ValidationResult.error("Invalid filename: path traversal detected");
        }

        // 2. Sanitize filename
        String sanitizedName = sanitizeFilename(fileName);
        if (sanitizedName.isEmpty()) {
            return ValidationResult.error("Invalid filename after sanitization");
        }

        // 3. Check extension
        String extension = getFileExtension(sanitizedName).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return ValidationResult.error("File type not allowed: " + extension);
        }

        // 4. Verify magic bytes
        try {
            if (!verifyMagicBytes(file, extension)) {
                return ValidationResult.error("File content doesn't match extension");
            }
        } catch (IOException e) {
            return ValidationResult.error("Failed to read file content");
        }

        return ValidationResult.success(sanitizedName);
    }

    private boolean containsPathTraversal(String filename) {
        return filename.contains("..") || 
               filename.contains("/") || 
               filename.contains("\\") ||
               filename.contains("\0");
    }

    private String sanitizeFilename(String filename) {
        // Remove any path components
        filename = filename.replaceAll("[\\\\/]", "");
        
        // Remove null bytes
        filename = filename.replace("\0", "");
        
        // Remove control characters
        filename = filename.replaceAll("[\\p{Cntrl}]", "");
        
        // Keep only alphanumeric, dots, hyphens, underscores
        filename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        // Prevent multiple consecutive dots
        filename = filename.replaceAll("\\.{2,}", ".");
        
        // Ensure filename is not just dots or empty
        if (filename.matches("^\\.+$")) {
            return "";
        }
        
        return filename;
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1);
        }
        return "";
    }

    private boolean verifyMagicBytes(MultipartFile file, String extension) throws IOException {
        // Skip validation for text files (no reliable magic bytes)
        if ("txt".equals(extension)) {
            return true;
        }

        // Skip validation for old Office formats (complex headers)
        if ("doc".equals(extension) || "xls".equals(extension)) {
            return true;
        }

        byte[] expectedMagic = MAGIC_BYTES.get(extension);
        if (expectedMagic == null) {
            return true; // No magic bytes defined, allow
        }

        try (InputStream is = file.getInputStream()) {
            byte[] fileHeader = new byte[expectedMagic.length];
            int bytesRead = is.read(fileHeader);
            
            if (bytesRead < expectedMagic.length) {
                return false;
            }

            return Arrays.equals(fileHeader, expectedMagic);
        }
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final String sanitizedFilename;

        private ValidationResult(boolean valid, String message, String sanitizedFilename) {
            this.valid = valid;
            this.message = message;
            this.sanitizedFilename = sanitizedFilename;
        }

        public static ValidationResult success(String sanitizedFilename) {
            return new ValidationResult(true, "Valid", sanitizedFilename);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message, null);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public String getSanitizedFilename() {
            return sanitizedFilename;
        }
    }
}