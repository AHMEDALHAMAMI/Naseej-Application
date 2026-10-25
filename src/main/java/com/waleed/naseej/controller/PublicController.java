package com.waleed.naseej.controller;

import com.waleed.naseej.dto.out.NeighborhoodResponse;
import com.waleed.naseej.dto.out.PublicIdeaPinResponse;
import com.waleed.naseej.dto.out.PublicStatsResponse;
import com.waleed.naseej.service.PublicService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** No login needed: landing page numbers and map, and the sign-up neighborhood lookup. */
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {

    private final PublicService publicService;

    // Map tiles for the website. With a MapTiler key the branded style is used; without one, OpenStreetMap tiles.
    // A tile key is always visible in the browser, so restrict it to your domain in the MapTiler dashboard.
    @Value("${app.map.maptiler-key:}")
    private String mapTilerKey;

    @Value("${app.map.style:streets-v2}")
    private String mapStyle;

    @GetMapping("/stats")
    public PublicStatsResponse stats() {
        return publicService.stats();
    }

    @GetMapping("/ideas-nearby")
    public List<PublicIdeaPinResponse> ideasNearby(@RequestParam double lat,
                                                   @RequestParam double lng,
                                                   @RequestParam(required = false) Double radiusKm) {
        return publicService.nearby(lat, lng, radiusKm);
    }

    @GetMapping("/nearest-neighborhood")
    public NeighborhoodResponse nearestNeighborhood(@RequestParam double lat, @RequestParam double lng) {
        return publicService.nearestNeighborhood(lat, lng);
    }

    @GetMapping("/map-config")
    public Map<String, Object> mapConfig() {
        if (mapTilerKey != null && !mapTilerKey.isBlank()) {
            return Map.of(
                    "tileUrl", "https://api.maptiler.com/maps/" + mapStyle + "/256/{z}/{x}/{y}.png?key=" + mapTilerKey.trim(),
                    "attribution", "&copy; <a href=\"https://www.maptiler.com/copyright/\">MapTiler</a> &copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors",
                    "maxZoom", 20);
        }
        return Map.of(
                "tileUrl", "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                "attribution", "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors",
                "maxZoom", 19);
    }
}
