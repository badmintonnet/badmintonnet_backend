package com.tlcn.sportsnet_backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/address")
public class AddressController {

    private static final String FALLBACK_ADDRESS_API_BASE_URL = "https://provinces.open-api.vn/api/v2";

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
        Object response = getFromPrimaryAddressApi(url);
        if (!isBlockedByBotProtection(response)) {
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.ok(getFallbackProvinces());
    }

    @GetMapping("/wards/{provinceId}")
    public ResponseEntity<?> getWardsByProvinceId(@PathVariable String provinceId) {
        String url = getAddressApiBaseUrl() + "/2/" + provinceId + ".htm";
        Object response = getFromPrimaryAddressApi(url);
        if (!isBlockedByBotProtection(response)) {
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.ok(getFallbackWardsByProvinceId(provinceId));
    }

    private Object getFromPrimaryAddressApi(String url) {
        try {
            return restTemplate.getForObject(url, Object.class);
        } catch (RestClientException ex) {
            return null;
        }
    }

    private boolean isBlockedByBotProtection(Object response) {
        if (response == null) {
            return true;
        }

        if (response instanceof String responseText) {
            String lowerCaseResponse = responseText.toLowerCase();
            return lowerCaseResponse.contains("imunify360")
                    || lowerCaseResponse.contains("bot-protection")
                    || lowerCaseResponse.contains("request is being verified");
        }

        if (response instanceof Map<?, ?> responseMap) {
            Object message = responseMap.get("message");
            return message != null && message.toString().toLowerCase().contains("imunify360");
        }

        return false;
    }

    private Map<String, Object> getFallbackProvinces() {
        String url = FALLBACK_ADDRESS_API_BASE_URL + "/?depth=1";
        List<?> provinces = restTemplate.getForObject(url, List.class);
        List<Map<String, Object>> data = Objects.requireNonNullElse(provinces, List.of())
                .stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::toAddressItem)
                .toList();

        return successResponse(data);
    }

    private Map<String, Object> getFallbackWardsByProvinceId(String provinceId) {
        String url = FALLBACK_ADDRESS_API_BASE_URL + "/p/" + provinceId + "?depth=2";
        Map<?, ?> province = restTemplate.getForObject(url, Map.class);
        Object wards = province == null ? List.of() : province.get("wards");

        List<Map<String, Object>> data = wards instanceof List<?> wardList
                ? wardList.stream()
                        .filter(Map.class::isInstance)
                        .map(Map.class::cast)
                        .map(this::toAddressItem)
                        .toList()
                : List.of();

        return successResponse(data);
    }

    private Map<String, Object> toAddressItem(Map<?, ?> source) {
        Map<String, Object> item = new LinkedHashMap<>();
        Object code = source.get("code");
        Object name = source.get("name");

        item.put("id", code == null ? null : code.toString());
        item.put("name", name);
        item.put("full_name", name);
        item.put("code", code);
        item.put("division_type", source.get("division_type"));
        item.put("codename", source.get("codename"));
        return item;
    }

    private Map<String, Object> successResponse(List<Map<String, Object>> data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", 0);
        response.put("error_text", "Success");
        response.put("data", data);
        return response;
    }
}
