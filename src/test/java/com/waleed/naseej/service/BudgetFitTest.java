package com.waleed.naseej.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** How an opportunity's estimated capital sits in the investor's budget range (ExploreService.budgetFit). */
class BudgetFitTest {

    private static BigDecimal k(int thousands) {
        return BigDecimal.valueOf(thousands * 1000L);
    }

    @Test
    void insideTheRangeIsAFullFit() {
        assertEquals("FULL", ExploreService.budgetFit(k(250), k(450), k(200), k(800)));
        assertEquals("FULL", ExploreService.budgetFit(k(200), k(800), k(200), k(800)));
    }

    @Test
    void overlappingRangesAreAPartialFit() {
        assertEquals("PARTIAL", ExploreService.budgetFit(k(150), k(450), k(200), k(800)));
        assertEquals("PARTIAL", ExploreService.budgetFit(k(600), k(1200), k(200), k(800)));
    }

    @Test
    void noOverlapIsLeftOut() {
        assertEquals("NONE", ExploreService.budgetFit(k(900), k(1500), k(200), k(800)));
        assertEquals("NONE", ExploreService.budgetFit(k(50), k(150), k(200), k(800)));
    }

    @Test
    void anOpenTopMeansAndAbove() {
        assertEquals("FULL", ExploreService.budgetFit(k(900), k(5000), k(200), null));
        assertEquals("PARTIAL", ExploreService.budgetFit(k(100), k(300), k(200), null));
    }

    @Test
    void noEstimateIsUnknownAndNoBudgetIsNoLabel() {
        assertEquals("UNKNOWN", ExploreService.budgetFit(null, null, k(200), k(800)));
        assertNull(ExploreService.budgetFit(k(250), k(450), null, null));
        // Only one end of the estimate is known: it is treated as a single figure
        assertEquals("FULL", ExploreService.budgetFit(k(300), null, k(200), k(800)));
    }
}
