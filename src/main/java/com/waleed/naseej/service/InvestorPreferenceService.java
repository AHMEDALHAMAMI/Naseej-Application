package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.UpdateProfileRequest;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.ExternalServiceException;
import com.waleed.naseej.repository.NeighborhoodRepository;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The investor's preferences from the onboarding questions and the account page: budget (a preset or a
 * custom range), fields (with a field in their own words that the AI matches to idea categories) and the
 * neighborhoods they want to invest in. Fields that are null in the request are left as they are;
 * an empty string clears them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvestorPreferenceService {

    // The idea categories the website offers (saved in English, shown translated)
    public static final List<String> CATEGORIES = List.of(
            "Grocery", "Bakery", "Cafe", "Restaurant", "Pharmacy", "Clinic", "Women's gym", "Gym", "Laundry",
            "Barber", "Salon", "Kids activities", "School supplies", "Car services", "Other");

    private static final String CUSTOM = "CUSTOM";
    private static final int MAX_AREAS = 30;

    private static final String FIELD_PROMPT = """
            You match the business field an investor typed (Arabic or English, any dialect) to the idea
            categories of Naseej, a Saudi platform where residents ask for local shops and services.
            Return every category that fits, most relevant first, at most three. Use "Other" only when
            nothing else fits.""";

    private static final Map<String, Object> FIELD_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "categories", Map.of("type", "array", "items", Map.of("type", "string", "enum", CATEGORIES))),
            "required", List.of("categories"),
            "additionalProperties", false);

    /** Claude's answer: the categories that fit the investor's own words. */
    record FieldMatch(List<String> categories) {
    }

    private final NeighborhoodRepository neighborhoodRepository;
    private final ClaudeService claudeService;

    public void apply(User user, UpdateProfileRequest request) {
        applyBudget(user, request);
        if (request.getInvestmentFields() != null) {
            user.setInvestmentFields(blankToNull(request.getInvestmentFields()));
        }
        if (request.getInvestmentFieldsOther() != null) {
            applyOtherField(user, request.getInvestmentFieldsOther());
        }
        if (request.getInvestmentAreas() != null) {
            user.setInvestmentAreas(knownAreas(request.getInvestmentAreas()));
        }
    }

    // ---------- Budget ----------

    private void applyBudget(User user, UpdateProfileRequest request) {
        String budget = request.getInvestmentBudget();
        boolean rangeSent = request.getBudgetMin() != null || request.getBudgetMax() != null;
        if (budget == null && !rangeSent) {
            return;
        }
        if (budget == null) {
            // Only a range was sent: it is a custom budget
            budget = CUSTOM;
        }
        if (budget.isBlank()) {
            user.setInvestmentBudget(null);
            user.setBudgetMin(null);
            user.setBudgetMax(null);
            return;
        }
        if (!CUSTOM.equals(budget)) {
            // A preset replaces any custom range
            user.setInvestmentBudget(budget);
            user.setBudgetMin(null);
            user.setBudgetMax(null);
            return;
        }
        BigDecimal min = request.getBudgetMin() != null ? BigDecimal.valueOf(request.getBudgetMin()) : null;
        BigDecimal max = request.getBudgetMax() != null ? BigDecimal.valueOf(request.getBudgetMax()) : null;
        if (min == null && max == null) {
            throw new BadRequestException("Enter at least one end of your budget range");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new BadRequestException("The lowest budget can't be more than the highest");
        }
        user.setInvestmentBudget(CUSTOM);
        user.setBudgetMin(min);
        user.setBudgetMax(max);
    }

    // ---------- A field in the investor's own words ----------

    private void applyOtherField(User user, String text) {
        String other = blankToNull(text);
        if (other == null) {
            user.setInvestmentFieldsOther(null);
            user.setInvestmentFieldsOtherCategories(null);
            return;
        }
        if (other.equals(user.getInvestmentFieldsOther()) && user.getInvestmentFieldsOtherCategories() != null) {
            return; // unchanged, keep the earlier match
        }
        user.setInvestmentFieldsOther(other);
        user.setInvestmentFieldsOtherCategories(matchCategories(other));
    }

    /**
     * Asks the AI which idea categories the investor's words fit, so their opportunities can be matched.
     * When the AI is off or fails, the words are still saved and matched as text on the website.
     */
    private String matchCategories(String other) {
        try {
            FieldMatch match = claudeService.generateJson(
                    FIELD_PROMPT, "The investor wrote: " + other, FIELD_SCHEMA, FieldMatch.class);
            if (match == null || match.categories() == null) {
                return null;
            }
            String categories = match.categories().stream()
                    .filter(Objects::nonNull)
                    .filter(CATEGORIES::contains)
                    .distinct()
                    .limit(3)
                    .collect(Collectors.joining(","));
            return categories.isEmpty() ? null : categories;
        } catch (ExternalServiceException | BadRequestException e) {
            log.warn("Could not match the investor's field \"{}\" to categories: {}", other, e.getMessage());
            return null;
        }
    }

    // ---------- Areas ----------

    // Keeps only neighborhoods that exist, in the order sent, without repeats
    private String knownAreas(String value) {
        String areas = blankToNull(value);
        if (areas == null) {
            return null;
        }
        Set<Integer> ids = Arrays.stream(areas.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && s.length() <= 9)
                .map(Integer::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String kept = ids.stream()
                .filter(neighborhoodRepository::existsById)
                .limit(MAX_AREAS)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return kept.isEmpty() ? null : kept;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
