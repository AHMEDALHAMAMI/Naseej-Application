package com.waleed.naseej.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The feasibility headline is worked out by the server from its three parts, so the breakdown always adds up. */
class IdeaScoreTest {

    private static int headline(int shortage, int competition, int demand) {
        return new IdeaCheckService.ScreeningResult(true, null, shortage, competition, demand, "summary").score();
    }

    @Test
    void theHeadlineIsTheRoundedAverageOfTheParts() {
        assertEquals(78, headline(85, 80, 70));
        assertEquals(67, headline(100, 50, 50));
        assertEquals(0, headline(0, 0, 0));
        assertEquals(100, headline(100, 100, 100));
    }
}
