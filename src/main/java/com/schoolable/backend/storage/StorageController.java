package com.schoolable.backend.storage;

import com.schoolable.backend.config.FeatureFlags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/storage")
@Tag(name = "Storage", description = "File upload and management")
public class StorageController {

    private final StorageService storageService;
    private final FeatureFlags featureFlags;

    public StorageController(StorageService storageService, FeatureFlags featureFlags) {
        this.storageService = storageService;
        this.featureFlags = featureFlags;
    }

    @Operation(summary = "Check if storage is available")
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of(
            "available", storageService.isAvailable(),
            "provider", "cloudinary"
        ));
    }

    @Operation(summary = "Upload a generic file")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "general") String folder,
            Authentication auth) {
        
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        if (!storageService.isAvailable()) {
            return ResponseEntity.status(503).body(Map.of("error", "Storage service not configured"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        // Validate file size (max 10MB)
        if (file.getSize() > 10 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "File too large. Maximum size is 10MB"));
        }

        try {
            Map<String, Object> result = storageService.uploadFile(file, folder);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @Operation(summary = "Upload a check-in photo")
    @PostMapping(value = "/attendance/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadCheckInPhoto(
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        if (!storageService.isAvailable()) {
            return ResponseEntity.status(503).body(Map.of("error", "Storage service not configured"));
        }

        String userId = auth.getPrincipal().toString();

        // Validate it's an image
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only image files are allowed"));
        }

        // Validate size (max 5MB for photos)
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "Photo too large. Maximum size is 5MB"));
        }

        try {
            Map<String, Object> result = storageService.uploadCheckInPhoto(file, userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @Operation(summary = "Upload a task attachment")
    @PostMapping(value = "/tasks/{taskId}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadTaskAttachment(
            @PathVariable Long taskId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        if (!storageService.isAvailable()) {
            return ResponseEntity.status(503).body(Map.of("error", "Storage service not configured"));
        }

        // Validate file size (max 10MB)
        if (file.getSize() > 10 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "File too large. Maximum size is 10MB"));
        }

        try {
            Map<String, Object> result = storageService.uploadTaskAttachment(file, taskId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @Operation(summary = "Upload a profile avatar")
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        if (!storageService.isAvailable()) {
            return ResponseEntity.status(503).body(Map.of("error", "Storage service not configured"));
        }

        String userId = auth.getPrincipal().toString();

        // Validate it's an image
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only image files are allowed"));
        }

        // Validate size (max 2MB for avatars)
        if (file.getSize() > 2 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "Avatar too large. Maximum size is 2MB"));
        }

        try {
            Map<String, Object> result = storageService.uploadAvatar(file, userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @Operation(summary = "Upload from base64 (for mobile apps)")
    @PostMapping("/upload/base64")
    public ResponseEntity<?> uploadBase64(
            @RequestBody Base64UploadRequest req,
            Authentication auth) {
        
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        if (!storageService.isAvailable()) {
            return ResponseEntity.status(503).body(Map.of("error", "Storage service not configured"));
        }

        if (req.data() == null || req.data().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No data provided"));
        }

        try {
            Map<String, Object> result = storageService.uploadBase64(
                req.data(), 
                req.folder() != null ? req.folder() : "general",
                req.filename()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @Operation(summary = "Delete a file by public ID")
    @DeleteMapping("/delete/{publicId}")
    public ResponseEntity<?> deleteFile(@PathVariable String publicId, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        if (!storageService.isAvailable()) {
            return ResponseEntity.status(503).body(Map.of("error", "Storage service not configured"));
        }

        boolean deleted = storageService.deleteFile(publicId);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    @Operation(summary = "Upload a chat/message attachment")
    @PostMapping(value = "/chat/{channelId}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadChatAttachment(
            @PathVariable String channelId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {

        if (!featureFlags.isMessagingEnabled()) {
            return ResponseEntity.status(403).body(Map.of("error", "Messaging is disabled"));
        }
        
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        if (!storageService.isAvailable()) {
            return ResponseEntity.status(503).body(Map.of("error", "Storage service not configured"));
        }

        String userId = auth.getPrincipal().toString();

        // Validate file size (max 10MB)
        if (file.getSize() > 10 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "File too large. Maximum size is 10MB"));
        }

        try {
            Map<String, Object> result = storageService.uploadChatAttachment(file, channelId, userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @Operation(summary = "Upload an announcement image")
    @PostMapping(value = "/announcements/{announcementId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAnnouncementImage(
            @PathVariable String announcementId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        if (!storageService.isAvailable()) {
            return ResponseEntity.status(503).body(Map.of("error", "Storage service not configured"));
        }

        // Validate it's an image
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only image files are allowed"));
        }

        // Validate size (max 5MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "Image too large. Maximum size is 5MB"));
        }

        try {
            Map<String, Object> result = storageService.uploadAnnouncementImage(file, announcementId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    // Request DTOs
    public record Base64UploadRequest(
        String data,
        String folder,
        String filename
    ) {}
}
