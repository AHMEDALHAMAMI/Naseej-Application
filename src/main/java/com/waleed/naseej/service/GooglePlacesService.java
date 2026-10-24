package com.waleed.naseej.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.waleed.naseej.exception.ExternalServiceException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Finds businesses of one type inside a circle around a point, nearest first.
 * Uses Google Places API (New) when GOOGLE_PLACES_API_KEY is set; otherwise OpenStreetMap
 * (Overpass API), which is free, needs no key, and is the same map data the website shows.
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

    /**
     * Public Overpass servers for OpenStreetMap data. The main one is often busy,
     * so the search tries the next server when one times out or refuses.
     */
    private static final List<String> OVERPASS_URLS = List.of(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter");

    /**
     * The same business types as OpenStreetMap tags ("key=value", or "key~a|b" for several values).
     * A type matches a place if any of its tags matches.
     */
    private static final Map<String, List<String>> OSM_TAGS = Map.ofEntries(
            Map.entry("cafe", List.of("amenity=cafe")),
            Map.entry("coffee_shop", List.of("amenity=cafe", "shop=coffee")),
            Map.entry("restaurant", List.of("amenity=restaurant")),
            Map.entry("fast_food_restaurant", List.of("amenity=fast_food")),
            Map.entry("breakfast_restaurant", List.of("amenity=restaurant")),
            Map.entry("pizza_restaurant", List.of("amenity=restaurant", "amenity=fast_food")),
            Map.entry("sandwich_shop", List.of("amenity=fast_food")),
            Map.entry("bakery", List.of("shop=bakery")),
            Map.entry("ice_cream_shop", List.of("amenity=ice_cream")),
            Map.entry("juice_shop", List.of("shop=beverages", "amenity=cafe")),
            Map.entry("dessert_shop", List.of("shop~confectionery|pastry")),
            Map.entry("supermarket", List.of("shop=supermarket")),
            Map.entry("grocery_store", List.of("shop~greengrocer|convenience|supermarket")),
            Map.entry("convenience_store", List.of("shop=convenience")),
            Map.entry("butcher_shop", List.of("shop=butcher")),
            Map.entry("pharmacy", List.of("amenity=pharmacy", "shop=chemist")),
            Map.entry("gym", List.of("leisure=fitness_centre")),
            Map.entry("fitness_center", List.of("leisure=fitness_centre")),
            Map.entry("yoga_studio", List.of("leisure=fitness_centre")),
            Map.entry("sports_club", List.of("leisure=sports_centre")),
            Map.entry("sports_coaching", List.of("leisure=sports_centre")),
            Map.entry("swimming_pool", List.of("leisure=swimming_pool")),
            Map.entry("barber_shop", List.of("shop=hairdresser")),
            Map.entry("hair_salon", List.of("shop=hairdresser")),
            Map.entry("beauty_salon", List.of("shop=beauty")),
            Map.entry("nail_salon", List.of("shop=beauty")),
            Map.entry("spa", List.of("leisure=spa", "shop=massage")),
            Map.entry("massage", List.of("shop=massage")),
            Map.entry("makeup_artist", List.of("shop~beauty|cosmetics")),
            Map.entry("laundry", List.of("shop~laundry|dry_cleaning")),
            Map.entry("tailor", List.of("shop=tailor")),
            Map.entry("car_wash", List.of("amenity=car_wash")),
            Map.entry("car_repair", List.of("shop~car_repair|tyres")),
            Map.entry("gas_station", List.of("amenity=fuel")),
            Map.entry("dentist", List.of("amenity=dentist")),
            Map.entry("doctor", List.of("amenity~doctors|clinic")),
            Map.entry("medical_lab", List.of("healthcare=laboratory")),
            Map.entry("physiotherapist", List.of("healthcare=physiotherapist")),
            Map.entry("veterinary_care", List.of("amenity=veterinary")),
            Map.entry("child_care_agency", List.of("amenity=childcare")),
            Map.entry("preschool", List.of("amenity=kindergarten")),
            Map.entry("school", List.of("amenity=school")),
            Map.entry("library", List.of("amenity=library")),
            Map.entry("book_store", List.of("shop=books")),
            Map.entry("florist", List.of("shop=florist")),
            Map.entry("pet_store", List.of("shop=pet")),
            Map.entry("hardware_store", List.of("shop~hardware|doityourself")),
            Map.entry("clothing_store", List.of("shop=clothes")),
            Map.entry("shoe_store", List.of("shop=shoes")),
            Map.entry("jewelry_store", List.of("shop=jewelry")),
            Map.entry("furniture_store", List.of("shop=furniture")),
            Map.entry("gift_shop", List.of("shop=gift")),
            Map.entry("electronics_store", List.of("shop=electronics")),
            Map.entry("cell_phone_store", List.of("shop=mobile_phone")),
            Map.entry("sporting_goods_store", List.of("shop=sports")));

    private final RestClient restClient;
    private final RestClient overpassClient;
    private final String apiKey;

    public GooglePlacesService(@Value("${google.places.api.key:}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();

        // Shorter wait per Overpass server, so trying the backups still answers in reasonable time
        SimpleClientHttpRequestFactory overpassFactory = new SimpleClientHttpRequestFactory();
        overpassFactory.setConnectTimeout(Duration.ofSeconds(5));
        overpassFactory.setReadTimeout(Duration.ofSeconds(20));
        this.overpassClient = RestClient.builder()
                .requestFactory(overpassFactory)
                .build();
        this.apiKey = apiKey;
    }

    /**
     * Places of {@code placeType} within {@code radiusKm} of the point, nearest first.
     * Each result carries its distance from the point in km.
     */
    public List<FoundPlace> searchNearby(double latitude, double longitude, double radiusKm, String placeType) {
        if (apiKey == null || apiKey.isBlank()) {
            return searchOpenStreetMap(latitude, longitude, radiusKm, placeType);
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

    /** Same search on OpenStreetMap data through the Overpass API (no key). */
    private List<FoundPlace> searchOpenStreetMap(double latitude, double longitude, double radiusKm, String placeType) {
        List<String> tags = OSM_TAGS.getOrDefault(placeType, List.of());
        if (tags.isEmpty()) {
            return List.of();
        }
        // A box around the point is much faster for Overpass than "around" (a circle);
        // places in the corners of the box are dropped below by their real distance
        double latDelta = radiusKm / 111.32;
        double lngDelta = radiusKm / (111.32 * Math.max(0.2, Math.cos(Math.toRadians(latitude))));
        String bbox = String.format(Locale.ROOT, "[bbox:%.6f,%.6f,%.6f,%.6f]",
                latitude - latDelta, longitude - lngDelta, latitude + latDelta, longitude + lngDelta);
        StringBuilder query = new StringBuilder("[out:json][timeout:15]").append(bbox).append(";(");
        for (String tag : tags) {
            // Shops are points (node) or buildings (way); skipping relations keeps the search fast
            query.append("node").append(toOverpassFilter(tag)).append(';');
            query.append("way").append(toOverpassFilter(tag)).append(';');
        }
        query.append(");out center 200;");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("data", query.toString());

        OverpassResponse response = null;
        boolean answered = false;
        boolean busy = false;
        for (String url : OVERPASS_URLS) {
            try {
                response = overpassClient.post()
                        .uri(url)
                        .header("User-Agent", "Naseej/1.0 (neighborhood demand platform)")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(form)
                        .retrieve()
                        .body(OverpassResponse.class);
                answered = true;
                break;
            } catch (RestClientResponseException e) {
                busy = true;
                log.warn("Overpass server {} returned {}, trying the next one", url, e.getStatusCode());
            } catch (RestClientException e) {
                log.warn("Overpass server {} didn't answer ({}), trying the next one", url, e.getMostSpecificCause().getMessage());
            }
        }
        if (!answered) {
            log.error("No OpenStreetMap (Overpass) server answered");
            throw new ExternalServiceException(busy
                    ? "The map data service is busy, please try again in a minute"
                    : "Could not reach the map data service, please try again later");
        }
        if (response == null || response.elements() == null) {
            return List.of();
        }

        List<FoundPlace> places = new ArrayList<>();
        for (OsmElement element : response.elements()) {
            Double lat = element.lat() != null ? element.lat() : (element.center() != null ? element.center().lat() : null);
            Double lon = element.lon() != null ? element.lon() : (element.center() != null ? element.center().lon() : null);
            if (lat == null || lon == null) {
                continue;
            }
            Map<String, String> t = element.tags() != null ? element.tags() : Map.of();
            String name = firstNonBlank(t.get("name"), t.get("name:ar"), t.get("name:en"), t.get("brand"), "Unnamed place");
            places.add(new FoundPlace(
                    "osm:" + element.type() + "/" + element.id(),
                    name,
                    placeType,
                    distanceKm(latitude, longitude, lat, lon)));
        }
        return places.stream()
                .filter(place -> place.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(FoundPlace::distanceKm))
                .limit(MAX_RESULTS)
                .toList();
    }

    // "amenity=cafe" -> [amenity=cafe]   "shop~a|b" -> [shop~"^(a|b)$"]
    private static String toOverpassFilter(String tag) {
        if (tag.contains("~")) {
            String[] parts = tag.split("~", 2);
            return "[" + parts[0] + "~\"^(" + parts[1] + ")$\"]";
        }
        return "[" + tag + "]";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OverpassResponse(List<OsmElement> elements) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsmElement(String type, Long id, Double lat, Double lon, OsmCenter center, Map<String, String> tags) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsmCenter(Double lat, Double lon) {
    }
}
