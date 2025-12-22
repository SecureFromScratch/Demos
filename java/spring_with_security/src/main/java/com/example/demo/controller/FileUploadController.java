package com.example.demo.controller;

import com.example.demo.config.FileUploadConfig;
import com.example.demo.service.FileValidationService;
import com.example.demo.service.FileValidationService.ValidationResult;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

@RestController
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

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String handleFileUpload(
            @Parameter(
                description = "File to upload",
                required = true,
                schema = @Schema(type = "string", format = "binary")
            )
            @RequestPart(value = "file", required = true) MultipartFile file,
            RedirectAttributes redirectAttributes) {
        
        // Validate single file
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "Please select a file to upload");
            redirectAttributes.addFlashAttribute("alertClass", "alert-warning");
            return "redirect:/upload";
        }

        try {
            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadConfig.getUploadDir());
            
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String fileName = file.getOriginalFilename();
            
            // Validate file size - REJECT oversized files to prevent DoS
            if (file.getSize() > uploadConfig.getMaxBytesPerFile()) {
                redirectAttributes.addFlashAttribute("message", 
                    "File rejected: exceeds " + (uploadConfig.getMaxBytesPerFile() / (1024 * 1024)) + "MB limit");
                redirectAttributes.addFlashAttribute("alertClass", "alert-danger");
                return "redirect:/upload";
            }

            // Validate file (extension, magic bytes, path traversal)
            ValidationResult validationResult = validationService.validateFile(file);
            if (!validationResult.isValid()) {
                redirectAttributes.addFlashAttribute("message", 
                    "File rejected: " + validationResult.getMessage());
                redirectAttributes.addFlashAttribute("alertClass", "alert-danger");
                return "redirect:/upload";
            }

            // Use sanitized filename
            String sanitizedFileName = validationResult.getSanitizedFilename();
            Path filePath = uploadPath.resolve(sanitizedFileName);

            // Additional check: ensure resolved path is still within upload directory
            if (!filePath.normalize().startsWith(uploadPath.normalize())) {
                redirectAttributes.addFlashAttribute("message", 
                    "File rejected: path traversal detected");
                redirectAttributes.addFlashAttribute("alertClass", "alert-danger");
                return "redirect:/upload";
            }

            // Save the file
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            redirectAttributes.addFlashAttribute("message", 
                "File uploaded successfully: " + sanitizedFileName);
            redirectAttributes.addFlashAttribute("alertClass", "alert-success");

        } catch (IOException e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("message", 
                "Failed to upload files: " + e.getMessage());
            redirectAttributes.addFlashAttribute("alertClass", "alert-danger");
        }

        return "redirect:/upload";
    }
}