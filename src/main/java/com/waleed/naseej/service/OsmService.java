package com.waleed.naseej.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenStreetMap lookups through Nominatim (free, no key):
 * - which district a point is in (sign-up, proposing an idea anywhere on the map)
 * - places with one OSM tag inside a box (competitors when the Overpass servers don't answer)
 * - searching a city or district by name (the investor's map search)
 * Nominatim allows about one request per second, so calls wait for their turn.
 */
@Slf4j
@Service
public class OsmService {

    private static final String BASE_URL = "https://nominatim.openstreetmap.org";
    private static final String USER_AGENT = "Naseej/1.0 (neighborhood demand platform)";
    private static final long MIN_GAP_MS = 1100;

    private final RestClient restClient;
    private long lastCallAt = 0;

    public OsmService() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(6));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }

    /** A district (neighborhood) in Arabic and its city in English (the key the website groups by), e.g. "الياسمين", "Riyadh". */
    public record District(String name, String city, String countryCode, double latitude, double longitude) {
    }

    /** One place found on the map. */
    public record Place(String id, String name, double latitude, double longitude) {
    }

    /** A search result for the map search box. */
    public record SearchResult(String name, String detail, double latitude, double longitude) {
    }

    /**
     * The district a point belongs to: its name in Arabic (like the rest of the neighborhoods) and its city as the
     * English key (Riyadh, Jeddah…). Empty when OSM has no district there or doesn't answer.
     */
    public Optional<District> reverse(double latitude, double longitude) {
        String url = String.format(Locale.ROOT,
                "%s/reverse?format=jsonv2&zoom=14&addressdetails=1&accept-language=ar&lat=%.6f&lon=%.6f",
                BASE_URL, latitude, longitude);
        NominatimPlace place = get(url, NominatimPlace.class);
        if (place == null || place.address() == null) {
            return Optional.empty();
        }
        Map<String, String> a = place.address();
        String name = cleanDistrict(first(a.get("suburb"), a.get("neighbourhood"), a.get("quarter"),
                a.get("city_district"), a.get("residential")));
        String city = cleanCity(first(a.get("city"), a.get("town"), a.get("village"), a.get("county"), a.get("state")));
        if (name == null || city == null) {
            return Optional.empty();
        }
        double lat = parse(place.lat(), latitude);
        double lng = parse(place.lon(), longitude);
        return Optional.of(new District(name, city, a.get("country_code"), lat, lng));
    }

    /** Places tagged {@code key=value} inside the box, at most 40 (Nominatim's limit). */
    public List<Place> searchTag(String key, String value, double south, double west, double north, double east) {
        String url = String.format(Locale.ROOT,
                "%s/search?format=jsonv2&limit=40&bounded=1&viewbox=%.6f,%.6f,%.6f,%.6f&q=%s",
                BASE_URL, west, north, east, south,
                URLEncoder.encode("[" + key + "=" + value + "]", StandardCharsets.UTF_8));
        NominatimPlace[] found = get(url, NominatimPlace[].class);
        if (found == null) {
            return null;
        }
        List<Place> places = new ArrayList<>();
        for (NominatimPlace p : found) {
            if (p.lat() == null || p.lon() == null) {
                continue;
            }
            String name = first(p.name(), p.displayName() != null ? p.displayName().split(",")[0] : null, "Unnamed place");
            places.add(new Place("osm:" + p.osmType() + "/" + p.osmId(), name, parse(p.lat(), 0), parse(p.lon(), 0)));
        }
        return places;
    }

    /** Cities and districts in Saudi Arabia matching the text, best first. */
    public List<SearchResult> search(String query) {
        String url = String.format(Locale.ROOT,
                "%s/search?format=jsonv2&limit=6&countrycodes=sa&addressdetails=1&accept-language=%s&q=%s",
                BASE_URL, containsArabic(query) ? "ar" : "en",
                URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
        NominatimPlace[] found = get(url, NominatimPlace[].class);
        if (found == null) {
            return List.of();
        }
        List<SearchResult> results = new ArrayList<>();
        for (NominatimPlace p : found) {
            if (p.lat() == null || p.lon() == null || p.displayName() == null) {
                continue;
            }
            String[] parts = p.displayName().split(",\\s*");
            String detail = parts.length > 1 ? String.join(", ", Arrays.copyOfRange(parts, 1, Math.min(parts.length, 3))) : "";
            results.add(new SearchResult(first(p.name(), parts[0]), detail, parse(p.lat(), 0), parse(p.lon(), 0)));
        }
        return results;
    }

    // ---------- Helpers ----------

    // Null when Nominatim can't be reached or answers with an error
    private <T> T get(String url, Class<T> type) {
        waitForTurn();
        try {
            return restClient.get().uri(URI.create(url)).retrieve().body(type);
        } catch (RestClientException e) {
            log.warn("Nominatim didn't answer: {}", e.getMostSpecificCause().getMessage());
            return null;
        }
    }

    private synchronized void waitForTurn() {
        long wait = lastCallAt + MIN_GAP_MS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallAt = System.currentTimeMillis();
    }

    // "Al Yasmin District" -> "Al Yasmin"
    private static String cleanDistrict(String name) {
        if (name == null) {
            return null;
        }
        String cleaned = name.replaceAll("(?i)\\s+(district|dist\\.?|neighborhood|neighbourhood)$", "")
                // "حي النرجس" → "النرجس"
                .replaceAll("^حي\\s+", "")
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    // Arabic city names from OSM → the English key the website uses for cities (it shows them in Arabic again)
    private static final Map<String, String> CITY_KEYS = Map.ofEntries(
            Map.entry("الرياض", "Riyadh"), Map.entry("جدة", "Jeddah"), Map.entry("الدمام", "Dammam"),
            Map.entry("الخبر", "Khobar"), Map.entry("الظهران", "Dhahran"), Map.entry("مكة المكرمة", "Makkah"),
            Map.entry("مكة", "Makkah"), Map.entry("المدينة المنورة", "Madinah"), Map.entry("المدينة", "Madinah"),
            Map.entry("الطائف", "Taif"), Map.entry("تبوك", "Tabuk"), Map.entry("أبها", "Abha"),
            Map.entry("خميس مشيط", "Khamis Mushait"), Map.entry("بريدة", "Buraidah"), Map.entry("عنيزة", "Unaizah"),
            Map.entry("حائل", "Hail"), Map.entry("جازان", "Jazan"), Map.entry("جيزان", "Jazan"),
            Map.entry("نجران", "Najran"), Map.entry("الأحساء", "Al Ahsa"), Map.entry("الهفوف", "Hofuf"),
            Map.entry("الجبيل", "Jubail"), Map.entry("ينبع", "Yanbu"), Map.entry("الخرج", "Kharj"),
            Map.entry("القطيف", "Qatif"), Map.entry("عرعر", "Arar"), Map.entry("سكاكا", "Sakaka"),
            Map.entry("الباحة", "Baha"), Map.entry("الدرعية", "Diriyah"));

    // "Riyadh governorate" / "Riyadh Region" / "Municipality of Jeddah" -> "Riyadh" / "Jeddah"
    private static String cleanCity(String city) {
        if (city == null) {
            return null;
        }
        String cleaned = city.replaceAll("(?i)\\s+(governorate|region|province|municipality)$", "")
                .replaceAll("(?i)^(municipality|governorate) of\\s+", "")
                // "منطقة الرياض" / "محافظة جدة" / "أمانة الدمام" → the city
                .replaceAll("^(منطقة|محافظة|أمانة|مدينة)\\s+", "")
                .trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return CITY_KEYS.getOrDefault(cleaned, cleaned);
    }

    private static boolean containsArabic(String text) {
        return text.codePoints().anyMatch(c -> c >= 0x0600 && c <= 0x06FF);
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static double parse(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NominatimPlace(String lat, String lon, String name,
                          @JsonProperty("display_name") String displayName,
                          @JsonProperty("osm_type") String osmType,
                          @JsonProperty("osm_id") Long osmId,
                          Map<String, String> address) {
    }
}
