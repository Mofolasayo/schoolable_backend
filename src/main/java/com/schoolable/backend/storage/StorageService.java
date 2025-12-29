package com.schoolable.backend.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for handling file uploads using Cloudinary.
 * Cloudinary provides:
 * - Image optimization and transformation
 * - CDN delivery
 * - Secure uploads
 */
@Service
public class StorageService {

    private final Cloudinary cloudinary;

    public StorageService(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret) {
        
        if (cloudName.isEmpty() || apiKey.isEmpty() || apiSecret.isEmpty()) {
            System.out.println("⚠️ Cloudinary not configured. File uploads will be disabled.");
            this.cloudinary = null;
        } else {
            this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
            ));
            System.out.println("✅ Cloudinary configured for cloud: " + cloudName);
        }
    }

    /**
     * Check if storage service is available
     */
    public boolean isAvailable() {
        return cloudinary != null;
    }

    /**
     * Upload a file (generic)
     * @param file The file to upload
     * @param folder The folder/category (e.g., "attendance", "tasks", "avatars")
     * @return Map containing url, publicId, format, size
     */
    public Map<String, Object> uploadFile(MultipartFile file, String folder) throws IOException {
        if (cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured");
        }

        String uniqueId = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        Map<String, Object> options = ObjectUtils.asMap(
            "folder", "schoolable/" + folder,
            "public_id", uniqueId,
            "resource_type", "auto" // Automatically detect image/video/raw
        );

        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), options);

        Map<String, Object> response = new HashMap<>();
        response.put("url", uploadResult.get("secure_url"));
        response.put("publicId", uploadResult.get("public_id"));
        response.put("format", uploadResult.get("format"));
        response.put("size", uploadResult.get("bytes"));
        response.put("width", uploadResult.get("width"));
        response.put("height", uploadResult.get("height"));
        response.put("resourceType", uploadResult.get("resource_type"));
        response.put("originalFilename", originalFilename);

        return response;
    }

    /**
     * Upload an image with optimization for check-in photos
     * - Resizes to max 1200px
     * - Optimizes quality
     * - Converts to webp for smaller size
     */
    public Map<String, Object> uploadCheckInPhoto(MultipartFile file, String userId) throws IOException {
        if (cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured");
        }

        String uniqueId = "checkin_" + userId + "_" + System.currentTimeMillis();

        Map<String, Object> options = ObjectUtils.asMap(
            "folder", "schoolable/attendance",
            "public_id", uniqueId,
            "resource_type", "image",
            "transformation", ObjectUtils.asMap(
                "width", 1200,
                "height", 1200,
                "crop", "limit",
                "quality", "auto:good",
                "fetch_format", "webp"
            )
        );

        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), options);

        Map<String, Object> response = new HashMap<>();
        response.put("url", uploadResult.get("secure_url"));
        response.put("publicId", uploadResult.get("public_id"));
        response.put("format", uploadResult.get("format"));
        response.put("size", uploadResult.get("bytes"));

        return response;
    }

    /**
     * Upload a task attachment
     */
    public Map<String, Object> uploadTaskAttachment(MultipartFile file, Long taskId) throws IOException {
        if (cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured");
        }

        String uniqueId = "task_" + taskId + "_" + UUID.randomUUID().toString().substring(0, 8);
        String originalFilename = file.getOriginalFilename();

        Map<String, Object> options = ObjectUtils.asMap(
            "folder", "schoolable/tasks/" + taskId,
            "public_id", uniqueId,
            "resource_type", "auto"
        );

        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), options);

        Map<String, Object> response = new HashMap<>();
        response.put("url", uploadResult.get("secure_url"));
        response.put("publicId", uploadResult.get("public_id"));
        response.put("format", uploadResult.get("format"));
        response.put("size", uploadResult.get("bytes"));
        response.put("originalFilename", originalFilename);

        return response;
    }

    /**
     * Upload an avatar image
     */
    public Map<String, Object> uploadAvatar(MultipartFile file, String userId) throws IOException {
        if (cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured");
        }

        Map<String, Object> options = ObjectUtils.asMap(
            "folder", "schoolable/avatars",
            "public_id", "avatar_" + userId,
            "resource_type", "image",
            "overwrite", true,
            "transformation", ObjectUtils.asMap(
                "width", 300,
                "height", 300,
                "crop", "fill",
                "gravity", "face",
                "quality", "auto:good"
            )
        );

        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), options);

        Map<String, Object> response = new HashMap<>();
        response.put("url", uploadResult.get("secure_url"));
        response.put("publicId", uploadResult.get("public_id"));

        return response;
    }

    /**
     * Delete a file by its public ID
     */
    public boolean deleteFile(String publicId) {
        if (cloudinary == null) {
            return false;
        }

        try {
            Map result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            return "ok".equals(result.get("result"));
        } catch (IOException e) {
            System.err.println("Failed to delete file: " + publicId + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * Upload from base64 string (useful for mobile app sending base64 images)
     */
    public Map<String, Object> uploadBase64(String base64Data, String folder, String filename) throws IOException {
        if (cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured");
        }

        // Handle data URL format: data:image/png;base64,xxxx
        String dataToUpload = base64Data;
        if (!base64Data.startsWith("data:")) {
            dataToUpload = "data:image/jpeg;base64," + base64Data;
        }

        Map<String, Object> options = ObjectUtils.asMap(
            "folder", "schoolable/" + folder,
            "public_id", filename != null ? filename : UUID.randomUUID().toString(),
            "resource_type", "auto"
        );

        Map uploadResult = cloudinary.uploader().upload(dataToUpload, options);

        Map<String, Object> response = new HashMap<>();
        response.put("url", uploadResult.get("secure_url"));
        response.put("publicId", uploadResult.get("public_id"));
        response.put("format", uploadResult.get("format"));
        response.put("size", uploadResult.get("bytes"));

        return response;
    }

    /**
     * Upload a chat/message attachment
     * Supports images and files, optimizes images
     */
    public Map<String, Object> uploadChatAttachment(MultipartFile file, String channelId, String userId) throws IOException {
        if (cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured");
        }

        String uniqueId = "chat_" + channelId + "_" + System.currentTimeMillis();
        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        
        Map<String, Object> options;
        
        // Apply transformations for images, store raw for other files
        if (contentType != null && contentType.startsWith("image/")) {
            options = ObjectUtils.asMap(
                "folder", "schoolable/chat/" + channelId,
                "public_id", uniqueId,
                "resource_type", "image",
                "transformation", ObjectUtils.asMap(
                    "width", 1920,
                    "height", 1080,
                    "crop", "limit",
                    "quality", "auto:good"
                )
            );
        } else {
            options = ObjectUtils.asMap(
                "folder", "schoolable/chat/" + channelId,
                "public_id", uniqueId,
                "resource_type", "auto"
            );
        }

        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), options);

        Map<String, Object> response = new HashMap<>();
        response.put("url", uploadResult.get("secure_url"));
        response.put("publicId", uploadResult.get("public_id"));
        response.put("format", uploadResult.get("format"));
        response.put("size", uploadResult.get("bytes"));
        response.put("width", uploadResult.get("width"));
        response.put("height", uploadResult.get("height"));
        response.put("resourceType", uploadResult.get("resource_type"));
        response.put("originalFilename", originalFilename);
        response.put("isImage", contentType != null && contentType.startsWith("image/"));

        return response;
    }

    /**
     * Upload an announcement image
     * Optimizes for display in feed
     */
    public Map<String, Object> uploadAnnouncementImage(MultipartFile file, String announcementId) throws IOException {
        if (cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured");
        }

        String uniqueId = "announcement_" + announcementId + "_" + System.currentTimeMillis();

        Map<String, Object> options = ObjectUtils.asMap(
            "folder", "schoolable/announcements",
            "public_id", uniqueId,
            "resource_type", "image",
            "transformation", ObjectUtils.asMap(
                "width", 1200,
                "height", 630,
                "crop", "limit",
                "quality", "auto:good",
                "fetch_format", "auto"
            )
        );

        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), options);

        Map<String, Object> response = new HashMap<>();
        response.put("url", uploadResult.get("secure_url"));
        response.put("publicId", uploadResult.get("public_id"));
        response.put("format", uploadResult.get("format"));
        response.put("size", uploadResult.get("bytes"));
        response.put("width", uploadResult.get("width"));
        response.put("height", uploadResult.get("height"));

        return response;
    }
}
