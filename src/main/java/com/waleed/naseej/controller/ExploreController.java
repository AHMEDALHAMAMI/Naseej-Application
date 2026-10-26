package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.AskOpportunitiesRequest;
import com.waleed.naseej.dto.out.AskOpportunitiesResponse;
import com.waleed.naseej.dto.in.AiSearchRequest;
import com.waleed.naseej.dto.out.AiSearchResponse;
import com.waleed.naseej.dto.out.CityStatResponse;
import com.waleed.naseej.dto.out.NeighborhoodStatResponse;
import com.waleed.naseej.dto.out.OpportunityResponse;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.service.AskService;
import com.waleed.naseej.service.ExploreService;
import com.waleed.naseej.service.OpportunitySearchService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Investor map: cities → neighborhoods → opportunities, plus search and rising requests. */
@RestController
@RequestMapping("/explore")
@RequiredArgsConstructor
public class ExploreController {

    private final ExploreService exploreService;
    private final AskService askService;

    // "Ask the AI": a question in the investor's own words becomes search filters
    @PostMapping("/ask")
    public AskOpportunitiesResponse ask(@AuthenticationPrincipal User user,
                                        @Valid @RequestBody AskOpportunitiesRequest request) {
        return askService.ask(user, request);
    }
    private final OpportunitySearchService opportunitySearchService;

    @GetMapping("/cities")
    public List<CityStatResponse> cities() {
        return exploreService.cities();
    }

    @GetMapping("/neighborhoods/{city}")
    public List<NeighborhoodStatResponse> neighborhoods(@PathVariable String city) {
        return exploreService.neighborhoods(city);
    }

    @GetMapping("/opportunities/{neighborhoodId}")
    public List<OpportunityResponse> opportunities(@AuthenticationPrincipal User user,
                                                   @PathVariable Integer neighborhoodId) {
        return exploreService.opportunities(user, neighborhoodId);
    }

    // e.g. /explore/search?q=gym&city=Riyadh&maxCapital=500000
    @GetMapping("/search")
    public List<OpportunityResponse> search(@AuthenticationPrincipal User user,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) String city,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) BigDecimal maxCapital,
                                            @RequestParam(required = false) Integer limit) {
        return exploreService.search(user, q, city, category, maxCapital, limit);
    }

    // e.g. /explore/rising?days=7&limit=10
    @GetMapping("/rising")
    public List<OpportunityResponse> rising(@AuthenticationPrincipal User user,
                                            @RequestParam(required = false) Integer days,
                                            @RequestParam(required = false) Integer limit) {
        return exploreService.rising(user, days, limit);
    }

    // ---------- Matches by budget and area, and the AI search (from the resident-side redesign) ----------

    @GetMapping("/top")
    public List<OpportunityResponse> top(@AuthenticationPrincipal User user,
                                         @RequestParam(defaultValue = "6") int limit) {
        return exploreService.top(user, limit);
    }

    @GetMapping("/match")
    public List<OpportunityResponse> match(@AuthenticationPrincipal User user,
                                           @RequestParam(required = false) String city,
                                           @RequestParam(required = false) Integer neighborhoodId,
                                           @RequestParam(required = false) BigDecimal budgetMin,
                                           @RequestParam(required = false) BigDecimal budgetMax,
                                           @RequestParam(defaultValue = "12") int limit) {
        return exploreService.match(user, city, neighborhoodId, budgetMin, budgetMax, limit);
    }

    @PostMapping("/ai-search")
    public AiSearchResponse aiSearch(@AuthenticationPrincipal User user, @Valid @RequestBody AiSearchRequest request) {
        return opportunitySearchService.search(user, request);
    }
}
