package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.AskOpportunitiesRequest;
import com.waleed.naseej.dto.out.AskOpportunitiesResponse;
import com.waleed.naseej.dto.out.OpportunityResponse;
import com.waleed.naseej.entity.Neighborhood;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.repository.NeighborhoodRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * "Ask the AI" for investors: the question in their own words becomes search filters (city, neighborhood,
 * category, capital, ready to unlock), and the opportunities that match come back with a one-line summary
 * of how the question was understood. When nothing matches every filter, the narrowest ones are dropped
 * so the investor still sees close results.
 */
@Service
@RequiredArgsConstructor
public class AskService {

    private static final int MAX_RESULTS = 30;
    private static final int MAX_PLACES_IN_PROMPT = 400;

    private static final Map<String, Object> NULLABLE_STRING = Map.of("anyOf", List.of(Map.of("type", "string"), Map.of("type", "null")));
    private static final Map<String, Object> NULLABLE_NUMBER = Map.of("anyOf", List.of(Map.of("type", "number"), Map.of("type", "null")));

    private static final Map<String, Object> FILTER_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "city", NULLABLE_STRING,
                    "neighborhoodId", Map.of("anyOf", List.of(Map.of("type", "integer"), Map.of("type", "null"))),
                    "category", Map.of("anyOf", List.of(
                            Map.of("type", "string", "enum", InvestorPreferenceService.CATEGORIES), Map.of("type", "null"))),
                    "minCapital", NULLABLE_NUMBER,
                    "maxCapital", NULLABLE_NUMBER,
                    "readyOnly", Map.of("type", "boolean"),
                    "keywords", NULLABLE_STRING,
                    "summary", Map.of("type", "string")),
            "required", List.of("city", "neighborhoodId", "category", "minCapital", "maxCapital", "readyOnly",
                    "keywords", "summary"),
            "additionalProperties", false);

    private static final String FILTER_PROMPT = """
            You turn an investor's question into search filters for Naseej, a Saudi platform where residents ask
            for the shops and services their neighborhood is missing, and investors open them.

            Rules:
            - city: one of the listed cities, written exactly as listed, or null.
            - neighborhoodId: the id of a listed neighborhood the investor names, or null. Set city to its city.
            - category: the idea category that fits the business they want, or null when they don't name one.
            - minCapital / maxCapital: the capital they want to put in, in SAR as plain numbers
              ("600 ألف" = 600000, "مليون" = 1000000, "under 600K" = maxCapital 600000). null when not given.
            - readyOnly: true when they want opportunities that are ready to open now (the vote target is reached).
            - keywords: only words that the other filters don't already cover, or null. Usually null.
            - summary: one short, friendly sentence saying what you'll look for. Write it in the language of the
              question. In Arabic use casual Saudi dialect and gender-neutral wording (don't address the reader as
              male or female), e.g. "بدوّر على صيدليات في الرياض برأس مال أقل من 600 ألف وجاهزة للفتح".
              If they name a place that isn't listed, say Naseej doesn't have it yet.
            Never invent cities or neighborhoods.""";

    /** Claude's answer: the filters it read from the question. */
    record Filters(String city, Integer neighborhoodId, String category, BigDecimal minCapital,
                   BigDecimal maxCapital, Boolean readyOnly, String keywords, String summary) {
    }

    private final ClaudeService claudeService;
    private final ExploreService exploreService;
    private final NeighborhoodRepository neighborhoodRepository;

    public AskOpportunitiesResponse ask(User currentUser, AskOpportunitiesRequest request) {
        List<Neighborhood> places = neighborhoodRepository.findAll().stream()
                .sorted(Comparator.comparing(Neighborhood::getCity).thenComparing(Neighborhood::getName))
                .limit(MAX_PLACES_IN_PROMPT)
                .toList();

        Filters filters = claudeService.generateJson(FILTER_PROMPT, message(request.getQuestion(), places),
                FILTER_SCHEMA, Filters.class);

        // Keep only places the platform really has
        Optional<Neighborhood> hood = filters.neighborhoodId() == null ? Optional.empty()
                : places.stream().filter(n -> n.getId().equals(filters.neighborhoodId())).findFirst();
        String city = hood.map(Neighborhood::getCity).orElse(knownCity(filters.city(), places));
        BigDecimal max = positive(filters.maxCapital());
        BigDecimal min = positive(filters.minCapital());
        boolean readyOnly = Boolean.TRUE.equals(filters.readyOnly());
        String keywords = filters.keywords() == null || filters.keywords().isBlank() ? null : filters.keywords().trim();
        Integer hoodId = hood.map(Neighborhood::getId).orElse(null);

        boolean relaxed = false;
        List<OpportunityResponse> results = search(currentUser, keywords, city, filters.category(), min, max, readyOnly, hoodId);
        if (results.isEmpty() && keywords != null) {
            results = search(currentUser, null, city, filters.category(), min, max, readyOnly, hoodId);
            relaxed = true;
        }
        if (results.isEmpty() && hoodId != null) {
            results = search(currentUser, null, city, filters.category(), min, max, readyOnly, null);
            relaxed = true;
        }

        return AskOpportunitiesResponse.builder()
                .summary(filters.summary())
                .city(city)
                .neighborhoodId(hoodId)
                .neighborhoodName(hood.map(Neighborhood::getName).orElse(null))
                .category(filters.category())
                .minCapital(min)
                .maxCapital(max)
                .readyOnly(readyOnly)
                .keywords(keywords)
                .relaxed(relaxed && !results.isEmpty())
                .results(results)
                .build();
    }

    private List<OpportunityResponse> search(User user, String keywords, String city, String category,
                                             BigDecimal min, BigDecimal max, boolean readyOnly, Integer hoodId) {
        return exploreService.search(user, keywords, city, category, max, 100).stream()
                // Adopted by someone else: nothing left to do with it
                .filter(o -> Boolean.TRUE.equals(o.getUnlocked())
                        || !("ADOPTED".equals(o.getStatus()) || "OPENED".equals(o.getStatus())))
                .filter(o -> hoodId == null || hoodId.equals(o.getNeighborhoodId()))
                .filter(o -> !readyOnly || "COMPLETED".equals(o.getStatus()))
                .filter(o -> min == null || o.getEstimatedCapitalMax() == null || o.getEstimatedCapitalMax().compareTo(min) >= 0)
                .limit(MAX_RESULTS)
                .toList();
    }

    private static String message(String question, List<Neighborhood> places) {
        String cities = places.stream().map(Neighborhood::getCity).filter(Objects::nonNull).map(String::trim)
                .distinct().collect(Collectors.joining(", "));
        String hoods = places.stream()
                .map(n -> n.getId() + " | " + n.getName() + " | " + n.getCity())
                .collect(Collectors.joining("\n"));
        return "Cities: " + (cities.isEmpty() ? "(none yet)" : cities)
                + "\n\nNeighborhoods (id | name | city):\n" + (hoods.isEmpty() ? "(none yet)" : hoods)
                + "\n\nThe investor asked: " + question.trim();
    }

    private static String knownCity(String city, List<Neighborhood> places) {
        if (city == null || city.isBlank()) {
            return null;
        }
        return places.stream().map(Neighborhood::getCity).filter(Objects::nonNull).map(String::trim)
                .filter(c -> c.equalsIgnoreCase(city.trim()))
                .findFirst()
                .orElse(null);
    }

    private static BigDecimal positive(BigDecimal value) {
        return value == null || value.signum() <= 0 ? null : value;
    }
}
