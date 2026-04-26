package com.tlcn.sportsnet_backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/address")
public class AddressController {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${address.api.base-url:https://esgoo.net/api-tinhthanh-new}")
    private String addressApiBaseUrl;

    private String getAddressApiBaseUrl() {
        return addressApiBaseUrl.endsWith("/")
                ? addressApiBaseUrl.substring(0, addressApiBaseUrl.length() - 1)
                : addressApiBaseUrl;
    }

    @GetMapping("/provinces")
    public ResponseEntity<?> getProvinces() {
        String url = getAddressApiBaseUrl() + "/1/0.htm";
        Object response = restTemplate.getForObject(url, Object.class);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/wards/{provinceId}")
    public ResponseEntity<?> getWardsByProvinceId(@PathVariable String provinceId) {
        String url = getAddressApiBaseUrl() + "/2/" + provinceId + ".htm";
        Object response = restTemplate.getForObject(url, Object.class);
        return ResponseEntity.ok(response);
    }
}