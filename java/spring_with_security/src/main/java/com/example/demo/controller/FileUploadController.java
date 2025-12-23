package com.example.demo.controller;

import com.example.demo.config.FileUploadConfig;
import com.example.demo.service.FileValidationService;
import com.example.demo.service.FileValidationService.ValidationResult;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;

import org.owasp.untrust.boxedpath.BoxedPath;
import org.owasp.untrust.boxedpath.PathSandbox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class FileUploadController {

    @Autowired
    private FileUploadConfig uploadConfig;

    @Autowired
    private FileValidationService validationService;

    @GetMapping("/upload")
    public String showUploadForm(Model model) {
        model.addAttribute("maxSizeMB", uploadConfig.getMaxBytesPerFile() / (1024 * 1024));
        return "upload";
    }

    @ResponseBody
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> handleFileUpload(
            @Parameter(description = "File to upload", required = true, schema = @Schema(type = "string", format = "binary")) @RequestPart(value = "file", required = true) MultipartFile file,
            RedirectAttributes redirectAttributes) {

        // Validate single file
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "Please select a file to upload");
            redirectAttributes.addFlashAttribute("alertClass", "alert-warning");
            return ResponseEntity.badRequest().body(Map.of("error", "File Rejected"));
        }

        try {
            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadConfig.getUploadDir());

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String fileName = file.getOriginalFilename();

            // https://owasp.org/www-project-untrust/
            // Classic Path assumes full system access,
            // whereas BoxedPath confines operations to a sandbox.
            // Any attempt to escape the sandbox with BoxedPath results in a
            // SecurityException.

            BoxedPath filePath = PathSandbox.boxroot(uploadPath).resolve(fileName);

            // Validate file size - REJECT oversized files to prevent DoS
            if (file.getSize() > uploadConfig.getMaxBytesPerFile()) {
                redirectAttributes.addFlashAttribute("message",
                        "File rejected: exceeds " + (uploadConfig.getMaxBytesPerFile() / (1024 * 1024)) + "MB limit");
                redirectAttributes.addFlashAttribute("alertClass", "alert-danger");
                return ResponseEntity.badRequest().body(Map.of("error", "File Rejected"));
            }

            // Validate file (extension, magic bytes, path traversal)
            ValidationResult validationResult = validationService.validateFile(file, uploadPath);
            if (!validationResult.isValid()) {
                redirectAttributes.addFlashAttribute("message",
                        "File rejected: " + validationResult.getMessage());
                redirectAttributes.addFlashAttribute("alertClass", "alert-danger");
                return ResponseEntity.badRequest().body(Map.of("error", "File Rejected"));

            }

            // Save the file with a unique file name
            String uniqueFileName = UUID.randomUUID().toString() + "." + validationResult.getExtension();
            Path uniquePath = uploadPath.resolve(uniqueFileName).normalize();

            Files.copy(file.getInputStream(), uniquePath, StandardCopyOption.REPLACE_EXISTING);

            String publicUrl = "/media/" + uniqueFileName;
            return ResponseEntity.created(URI.create(publicUrl)).body(Map.of("location", publicUrl));
            /*
             * redirectAttributes.addFlashAttribute("message",
             * "File uploaded successfully: " + fileName);
             * redirectAttributes.addFlashAttribute("alertClass", "alert-success");
             */
        } catch (IOException e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("message",
                    "Failed to upload files: " + e.getMessage());
            redirectAttributes.addFlashAttribute("alertClass", "alert-danger");
        }

        return ResponseEntity.badRequest().body(Map.of("error", "File Rejected"));
    }

    @GetMapping("/media/{name:.+}")
    public ResponseEntity<Resource> get(@PathVariable String name) throws IOException {
        Path uploadDir = Paths.get(uploadConfig.getUploadDir()).toAbsolutePath().normalize();

        String fileName = StringUtils.cleanPath(name);
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        // Validate within sandbox (throws if escape attempt)
        PathSandbox.boxroot(uploadDir).resolve(fileName);

        // Actual filesystem path used for IO
        Path filePath = uploadDir.resolve(fileName).normalize();
        if (!filePath.startsWith(uploadDir) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri()); // now it's file://...
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        String ext = StringUtils.getFilenameExtension(fileName);
        if (ext != null) {
            switch (ext.toLowerCase()) {
                case "png" -> mediaType = MediaType.IMAGE_PNG;
                case "jpg", "jpeg" -> mediaType = MediaType.IMAGE_JPEG;
                case "gif" -> mediaType = MediaType.IMAGE_GIF;
                case "webp" -> mediaType = MediaType.parseMediaType("image/webp");
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Content-Type-Options", "nosniff");
        headers.setCacheControl(CacheControl.noStore());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(mediaType)
                .body(resource);
    }

}