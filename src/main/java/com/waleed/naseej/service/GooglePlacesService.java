package com.waleed.naseej.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.waleed.naseej.exception.ExternalServiceException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Small client for Google Places API (New) — Nearby Search.
 * Finds businesses of one type inside a circle around a point, nearest first.
 */
@Slf4j
@Service
public class GooglePlacesService {

    private static final String SEARCH_NEARBY_URL = "https://places.googleapis.com/v1/places:searchNearby";
    private static final String FIELD_MASK = "places.id,places.displayName,places.primaryType,places.location";

    /** Google returns at most 20 places per search, so 20 means "20 or more". */
    public static final int MAX_RESULTS = 20;

    /**
     * Google place types (Table A) we let the AI choose from when it classifies an idea.
     * Every value here is accepted by Nearby Search as a filter.
     */
    public static final List<String> SUPPORTED_TYPES = List.of(
            "cafe", "coffee_shop", "restaurant", "fast_food_restaurant", "breakfast_restaurant",
            "pizza_restaurant", "sandwich_shop", "bakery", "ice_cream_shop", "juice_shop", "dessert_shop",
            "supermarket", "grocery_store", "convenience_store", "butcher_shop", "pharmacy",
            "gym", "fitness_center", "yoga_studio", "sports_club", "sports_coaching", "swimming_pool",
            "barber_shop", "hair_salon", "beauty_salon", "nail_salon", "spa", "massage", "makeup_artist",
            "laundry", "tailor", "car_wash", "car_repair", "gas_station",
            "dentist", "doctor", "medical_lab", "physiotherapist", "veterinary_care",
            "child_care_agency", "preschool", "school", "library", "book_store", "florist", "pet_store",
            "hardware_store", "clothing_store", "shoe_store", "jewelry_store", "furniture_store", "gift_shop",
            "electronics_store", "cell_phone_store", "sporting_goods_store");

    private final RestClient restClient;
    private final String apiKey;

    public GooglePlacesService(@Value("${google.places.api.key:}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
    }

    /**
     * Places of {@code placeType} within {@code radiusKm} of the point, nearest first.
     * Each result carries its distance from the point in km.
     */
    public List<FoundPlace> searchNearby(double latitude, double longitude, double radiusKm, String placeType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ExternalServiceException(
                    "Google Places is not configured. Add GOOGLE_PLACES_API_KEY to the .env file");
        }

        Map<String, Object> body = Map.of(
                "includedTypes", List.of(placeType),
                "maxResultCount", MAX_RESULTS,
                "rankPreference", "DISTANCE",
                "languageCode", "ar",
                "locationRestriction", Map.of("circle", Map.of(
                        "center", Map.of("latitude", latitude, "longitude", longitude),
                        "radius", radiusKm * 1000)));

        PlacesResponse response;
        try {
            response = restClient.post()
                    .uri(SEARCH_NEARBY_URL)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(PlacesResponse.class);
        } catch (RestClientResponseException e) {
            log.error("Google Places returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ExternalServiceException("Google Places returned an error, please try again later");
        } catch (RestClientException e) {
            log.error("Could not reach Google Places", e);
            throw new ExternalServiceException("Could not reach Google Places, please try again later");
        }

        // No results come back as an empty object, without a "places" list
        if (response == null || response.places() == null) {
            return List.of();
        }

        return response.places().stream()
                .filter(place -> place.id() != null && place.location() != null
                        && place.location().latitude() != null && place.location().longitude() != null)
                .map(place -> new FoundPlace(
                        place.id(),
                        place.displayName() != null && place.displayName().text() != null
                                ? place.displayName().text() : "Unnamed place",
                        place.primaryType(),
                        distanceKm(latitude, longitude, place.location().latitude(), place.location().longitude())))
                .sorted(Comparator.comparingDouble(FoundPlace::distanceKm))
                .toList();
    }

    /** Straight-line distance between two points in km (haversine), rounded to 2 decimals. */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double km = earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(km * 100) / 100.0;
    }

    /** One competitor found around the idea. */
    public record FoundPlace(String googlePlaceId, String name, String primaryType, double distanceKm) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PlacesResponse(List<Place> places) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Place(String id, DisplayName displayName, String primaryType, Location location) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DisplayName(String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Location(Double latitude, Double longitude) {
    }
}
