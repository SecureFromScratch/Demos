package com.example.demo.service;

import org.owasp.untrust.boxedpath.BoxedPath;
import org.owasp.untrust.boxedpath.PathSandbox;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

@Service
public class FileValidationService {

    // Allowed extensions
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "jpg", "png", "gif");

    // Magic bytes for common file types
    private static final Map<String, byte[]> MAGIC_BYTES = new HashMap<>();

    static {
        MAGIC_BYTES.put("pdf", new byte[] { 0x25, 0x50, 0x44, 0x46 }); // %PDF
        MAGIC_BYTES.put("jpg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF });
        MAGIC_BYTES.put("png", new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 });
        MAGIC_BYTES.put("gif", new byte[] { 0x47, 0x49, 0x46, 0x38 }); // GIF8
    }

    public ValidationResult validateFile(MultipartFile file, Path uploadPath) {
        String fileName = file.getOriginalFilename();

        if (fileName == null || fileName.isEmpty()) {
            return ValidationResult.error("Filename is empty");
        }

        // 1. Check for path traversal
        // if (containsPathTraversal(fileName)) {
        // return ValidationResult.error("Invalid filename: path traversal detected");
        // }
        BoxedPath filePath = PathSandbox.boxroot(uploadPath).resolve(fileName);

        // 2. Check extension
        String extension = getFileExtension(filePath).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return ValidationResult.error("File type not allowed: " + extension);
        }

        // 3. Verify magic bytes
        try {
            if (!verifyMagicBytes(file, extension)) {
                return ValidationResult.error("File content doesn't match extension");
            }
        } catch (IOException e) {
            return ValidationResult.error("Failed to read file content");
        }

        return ValidationResult.success(filePath, extension);
    }

    private String getFileExtension(Path filepath) {
        String filename = filepath.getFileName().toString();
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1);
        }
        return "";
    }

    private boolean verifyMagicBytes(MultipartFile file, String extension) throws IOException {
        if (file == null || file.isEmpty()) {
            return false;
        }
        if (extension == null) {
            return false;
        }

        String ext = extension.trim().toLowerCase(Locale.ROOT);

        // Allow only extensions that we have magic bytes for
        byte[] expected = MAGIC_BYTES.get(ext);
        if (expected == null) {
            return false;
        }

        byte[] actual = new byte[expected.length];
        try (InputStream is = new BufferedInputStream(file.getInputStream())) {
            int offset = 0;
            while (offset < actual.length) {
                int n = is.read(actual, offset, actual.length - offset);
                if (n == -1) {
                    return false;
                }
                offset += n;
            }
        }

        return Arrays.equals(actual, expected);
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final BoxedPath filepath;
        private final String extension;

        private ValidationResult(boolean valid, String message, BoxedPath filepath, String extension) {
            this.valid = valid;
            this.message = message;
            this.filepath = filepath;
            this.extension = extension;

        }

        public static ValidationResult success(BoxedPath filepath, String extension) {
            return new ValidationResult(true, "Valid", filepath, extension);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message, null, null);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public BoxedPath getFilepath() {
            return filepath;
        }

        public String getExtension() {
            return extension;
        }
    }
}
