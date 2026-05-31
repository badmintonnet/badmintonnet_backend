package com.tlcn.sportsnet_backend.service;

import com.tlcn.sportsnet_backend.dto.facility.FacilityCreateRequest;
import com.tlcn.sportsnet_backend.dto.facility.FacilityResponse;
import com.tlcn.sportsnet_backend.entity.Facility;
import com.tlcn.sportsnet_backend.error.InvalidDataException;
import com.tlcn.sportsnet_backend.payload.response.PagedResponse;
import com.tlcn.sportsnet_backend.repository.FacilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FacilityService {
    private final FacilityRepository facilityRepository;
    private final FileStorageService fileStorageService;

    public FacilityResponse createFacility(FacilityCreateRequest request) {
        Facility facility = Facility.builder()
                .name(request.getName())
                .address(request.getAddress())
                .district(request.getDistrict())
                .city(request.getCity())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .image(request.getImage())
                .build();
        facility = facilityRepository.save(facility);
        return toFacilityResponse(facility);
    }

    public PagedResponse<FacilityResponse> getAllFacility(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("name")));
        Page<Facility> facilities = facilityRepository.findAll(pageable);
        List<FacilityResponse> content = facilities.stream()
                .map(this::toFacilityResponse)
                .toList();
        return new PagedResponse<>(
                content,
                facilities.getNumber(),
                facilities.getSize(),
                facilities.getTotalElements(),
                facilities.getTotalPages(),
                facilities.isLast()
        );
    }

    private FacilityResponse toFacilityResponse(Facility facility) {
        return FacilityResponse.builder()
                .id(facility.getId())
                .name(facility.getName())
                .address(facility.getAddress())
                .district(facility.getDistrict())
                .city(facility.getCity())
                .location(facility.getLocation())
                .latitude(facility.getLatitude())
                .longitude(facility.getLongitude())
                .image(fileStorageService.getFileUrl(facility.getImage(), "/facility"))
                .build();
    }

    public void deleteFacility(String id) {
        Facility facility = facilityRepository.findById(id).orElseThrow(() -> new InvalidDataException("Facility not found"));
        fileStorageService.deleteFile(facility.getImage(), "/facility");
        facilityRepository.delete(facility);
    }

    @Transactional
    public FacilityResponse updateFacilityImage(String id, String image) {
        Facility facility = facilityRepository.findById(id)
                .orElseThrow(() -> new InvalidDataException("Facility not found"));
        String oldImage = facility.getImage();

        facility.setImage(image);
        Facility savedFacility = facilityRepository.save(facility);

        if (oldImage != null && !oldImage.isBlank() && !oldImage.equals(image)) {
            fileStorageService.deleteFile(oldImage, "/facility");
        }

        return toFacilityResponse(savedFacility);
    }

    public List<FacilityResponse> getAllFacilitiesFilter() {
        List<Facility> facilityList = facilityRepository.findAllByOrderByNameAsc();
        return facilityList.stream()
                .map(this::toFacilityResponse)
                .toList();
    }
}
