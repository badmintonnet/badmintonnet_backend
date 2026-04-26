package com.tlcn.sportsnet_backend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class NominatimService {

    @Value("${app.geocoding.nominatim.base-url:https://nominatim.openstreetmap.org}")
    private String baseUrl;

    @Value("${app.geocoding.nominatim.user-agent:SportsNetBackend/1.0}")
    private String userAgent;

    @Value("${app.geocoding.nominatim.email:}")
    private String contactEmail;

    @Value("${app.geocoding.nominatim.referer:}")
    private String referer;

    @Value("${app.geocoding.nominatim.accept-language:vi,en}")
    private String acceptLanguage;

    @Value("${app.geocoding.nominatim.country-codes:}")
    private String countryCodes;

    private final RestTemplate restTemplate = new RestTemplate();

    public Optional<LocationCoordinates> geocodeAddress(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }

        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/search")
                .queryParam("q", address)
                .queryParam("format", "jsonv2")
                .queryParam("limit", 1)
                .queryParamIfPresent("email", optionalValue(contactEmail))
                .queryParamIfPresent("countrycodes", optionalValue(countryCodes))
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();

        NominatimSearchResponse[] responses = executeGet(uri, NominatimSearchResponse[].class);

        if (responses == null || responses.length == 0) {
            return Optional.empty();
        }

        try {
            Double latitude = Double.valueOf(responses[0].getLatitude());
            Double longitude = Double.valueOf(responses[0].getLongitude());
            return Optional.of(new LocationCoordinates(latitude, longitude));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public Optional<String> reverseGeocode(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return Optional.empty();
        }

        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/reverse")
                .queryParam("lat", latitude)
                .queryParam("lon", longitude)
                .queryParam("format", "jsonv2")
                .queryParamIfPresent("email", optionalValue(contactEmail))
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();

        NominatimReverseResponse response = executeGet(uri, NominatimReverseResponse.class);
        if (response == null || response.getDisplayName() == null || response.getDisplayName().isBlank()) {
            return Optional.empty();
        }

        return Optional.of(response.getDisplayName());
    }

    private <T> T executeGet(URI uri, Class<T> responseType) {
        log.info("Executing GET request to Nominatim API: {}", uri);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.USER_AGENT, userAgent);
        if (StringUtils.hasText(acceptLanguage)) {
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        if (StringUtils.hasText(referer)) {
            headers.set(HttpHeaders.REFERER, referer);
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<T> response = restTemplate.exchange(uri, HttpMethod.GET, entity, responseType);
            log.info("Received response from Nominatim API: status={}, body={}", response.getStatusCode(), response.getBody());
            return response.getBody();
        } catch (HttpClientErrorException.Forbidden ex) {
            log.warn("Nominatim rejected request with 403. Configure valid app.geocoding.nominatim.user-agent/email/referer per policy: {}", ex.getMessage());
            return null;
        } catch (Exception ex) {
            log.warn("Nominatim request failed: {}", ex.getMessage());
            return null;
        }
    }

    private Optional<String> optionalValue(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    @Data
    public static class LocationCoordinates {
        private final Double latitude;
        private final Double longitude;
    }

    @Data
    public static class NominatimSearchResponse {
        @JsonProperty("lat")
        private String latitude;

        @JsonProperty("lon")
        private String longitude;
    }

    @Data
    public static class NominatimReverseResponse {
        @JsonProperty("display_name")
        private String displayName;
    }
}




