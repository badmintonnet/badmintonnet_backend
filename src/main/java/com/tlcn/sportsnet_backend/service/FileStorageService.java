package com.tlcn.sportsnet_backend.service;

import com.tlcn.sportsnet_backend.enums.MediaTypeEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-key}")
    private String supabaseServiceKey;

    @Value("${supabase.bucket}")
    private String bucket;

    @Value("${supabase.base-public-url}")
    private String basePublicUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public String storeFile(MultipartFile file, String subDirectory) {
        try {
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());

            String fileExtension = "";
            int dotIndex = originalFilename.lastIndexOf(".");
            if (dotIndex >= 0) {
                fileExtension = originalFilename.substring(dotIndex);
            }

            String uniqueFilename = UUID.randomUUID() + fileExtension;

            String cleanSubDirectory = normalizePath(subDirectory);
            String objectPath = cleanSubDirectory.isEmpty()
                    ? uniqueFilename
                    : cleanSubDirectory + "/" + uniqueFilename;

            String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + encodePath(objectPath);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(supabaseServiceKey);
            headers.set("apikey", supabaseServiceKey);
            headers.set("x-upsert", "true");
            headers.setContentType(resolveContentType(file));

            HttpEntity<byte[]> requestEntity = new HttpEntity<>(file.getBytes(), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    uploadUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Upload failed: " + response.getBody());
            }

            // Chỉ trả về tên file để lưu DB
            return uniqueFilename;

        } catch (IOException ex) {
            throw new RuntimeException("Could not store file: " + ex.getMessage(), ex);
        }
    }

    public String getFileUrl(String filename, String subDirectory) {
        if (filename == null || filename.isBlank()) {
            return null;
        }

        String cleanSubDirectory = normalizePath(subDirectory);

        return cleanSubDirectory.isEmpty()
                ? basePublicUrl + "/" + bucket + "/" + filename
                : basePublicUrl + "/" + bucket + "/" + cleanSubDirectory + "/" + filename;
    }

    public void deleteFile(String filename, String subDirectory) {
        try {
            if (filename == null || filename.isBlank()) {
                return;
            }

            String cleanSubDirectory = normalizePath(subDirectory);
            String objectPath = cleanSubDirectory.isEmpty()
                    ? filename
                    : cleanSubDirectory + "/" + filename;

            String deleteUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + encodePath(objectPath);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(supabaseServiceKey);
            headers.set("apikey", supabaseServiceKey);

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    deleteUrl,
                    HttpMethod.DELETE,
                    requestEntity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()
                    && response.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw new RuntimeException("Could not delete file: " + response.getBody());
            }

        } catch (Exception ex) {
            throw new RuntimeException("Could not delete file: " + ex.getMessage(), ex);
        }
    }

    public MediaTypeEnum detectMediaType(String filename) {
        String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return List.of("mp4", "mov", "avi", "mkv").contains(ext)
                ? MediaTypeEnum.VIDEO
                : MediaTypeEnum.IMAGE;
    }

    private String normalizePath(String path) {
        if (path == null) return "";
        return path.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String encodePath(String path) {
        return URLEncoder.encode(path, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2F", "/");
    }

    private MediaType resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}