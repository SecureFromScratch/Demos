package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/images")
public class EditorImageController {

    private static final long k_maxBytes = 2L * 1024 * 1024; // 2 MB
    private static final Set<String> k_allowedContentTypes = Set.of(
        "image/png",
        "image/jpeg",
        "image/gif",
        "image/webp",
        "image/svg+xml"
    );

    private final Path uploadsDir;

    public EditorImageController(@Value("${app.upload-dir}") String uploadDir) throws IOException {
        this.uploadsDir = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadsDir);
    }

    @PostMapping(path = "/editor-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file uploaded"));
        }
        if (file.getSize() > k_maxBytes) {
            return ResponseEntity.badRequest().body(Map.of("error", "File too large"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !k_allowedContentTypes.contains(contentType)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported image type"));
        }

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename());
        if (!originalName.matches("(?i).+\\.(png|jpe?g|gif|webp|svg)$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bad file extension"));
        }

        String ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
        String storedName = UUID.randomUUID() + ext;

        Path dest = uploadsDir.resolve(storedName).normalize();
        if (!dest.startsWith(uploadsDir)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bad path"));
        }

        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

        String publicUrl = "/images/media/" + storedName;
        return ResponseEntity.created(URI.create(publicUrl)).body(Map.of("location", publicUrl));
    }

    @GetMapping("/media/{name}")
    public ResponseEntity<Resource> get(@PathVariable String name) throws IOException {
        String fileName = StringUtils.cleanPath(name);
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        Path filePath = uploadsDir.resolve(fileName).normalize();
        if (!filePath.startsWith(uploadsDir) || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri());
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Content-Type-Options", "nosniff");
        headers.setCacheControl(CacheControl.noStore());

        //if ("image/svg+xml".equalsIgnoreCase(contentType)) {
        //    headers.add("Content-Security-Policy", "default-src 'none'");
        //}

        return ResponseEntity.ok()
            .headers(headers)
            .contentType(MediaType.parseMediaType(contentType))
            .body(resource);
    }
}
