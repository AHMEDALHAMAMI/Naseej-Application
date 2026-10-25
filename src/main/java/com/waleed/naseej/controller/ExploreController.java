package com.waleed.naseej.controller;

import com.waleed.naseej.dto.out.CityStatResponse;
import com.waleed.naseej.dto.out.NeighborhoodStatResponse;
import com.waleed.naseej.dto.out.OpportunityResponse;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.service.ExploreService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Investor map: cities → neighborhoods → opportunities. */
@RestController
@RequestMapping("/explore")
@RequiredArgsConstructor
public class ExploreController {

    private final ExploreService exploreService;

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
}
