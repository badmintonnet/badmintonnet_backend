package com.tlcn.sportsnet_backend.controller;

import com.tlcn.sportsnet_backend.dto.ApiResponse;
import com.tlcn.sportsnet_backend.dto.facility.FacilityCreateRequest;
import com.tlcn.sportsnet_backend.service.FacilityService;
import com.tlcn.sportsnet_backend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/facilities")
public class FacilityController {
    private final FacilityService facilityService;
    private final FileStorageService fileStorageService;

    @GetMapping
    public ResponseEntity<?> getAllFacilities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(facilityService.getAllFacility(page, size));
    }

    @GetMapping("/all/filter")
    public ResponseEntity<?> getAllFacilitiesFilter() {
        return ResponseEntity.ok(facilityService.getAllFacilitiesFilter());
    }

    @PostMapping
    public ResponseEntity<?> addFacility(@RequestBody FacilityCreateRequest request) {
        return ResponseEntity.ok(facilityService.createFacility(request));
    }

//    @PutMapping
//    public ResponseEntity<?> updateFacility(@RequestBody FacilityCreateRequest request) {
//        return ResponseEntity.ok(facilityService.updateFacility(request));
//    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFacility(@PathVariable String id) {
        facilityService.deleteFacility(id);
        return ResponseEntity.ok("Xóa thành công Facility");
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String facilityId) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        String uploadedFile = fileStorageService.storeFile(file, "/facility");

        if (facilityId != null && !facilityId.isBlank()) {
            return ResponseEntity.ok(facilityService.updateFacilityImage(facilityId, uploadedFile));
        }

        return ResponseEntity.ok()
                .body(ApiResponse.success(Map.of("fileName", uploadedFile)));
    }

    @PostMapping("/{id}/upload")
    public ResponseEntity<?> uploadFacilityImage(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        String uploadedFile = fileStorageService.storeFile(file, "/facility");
        return ResponseEntity.ok(facilityService.updateFacilityImage(id, uploadedFile));
    }
}
