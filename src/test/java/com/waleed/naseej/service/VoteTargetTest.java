package com.waleed.naseej.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The vote target from the estimated capital: 20 + 1 vote per 50,000 SAR of the middle, rounded to 5, 20–100. */
class VoteTargetTest {

    private static BigDecimal k(int thousands) {
        return BigDecimal.valueOf(thousands * 1000L);
    }

    @Test
    void biggerProjectsNeedMoreVotes() {
        assertEquals(25, VoteTarget.forCapital(k(120), k(220)));    // barber, middle 170k
        assertEquals(30, VoteTarget.forCapital(k(380), k(620)));    // café, middle 500k
        assertEquals(50, VoteTarget.forCapital(k(1000), k(1800)));  // supermarket, middle 1.4M
        assertEquals(70, VoteTarget.forCapital(k(1800), k(3000)));  // trampoline park, middle 2.4M
    }

    @Test
    void theTargetStaysBetweenTheLimits() {
        assertEquals(VoteTarget.MIN, VoteTarget.forCapital(k(10), k(40)));
        assertEquals(VoteTarget.MAX, VoteTarget.forCapital(k(8000), k(12000)));
    }

    @Test
    void oneEndOfTheRangeIsEnough() {
        assertEquals(30, VoteTarget.forCapital(k(500), null));
        assertEquals(30, VoteTarget.forCapital(null, k(500)));
    }

    @Test
    void noCapitalEstimateUsesTheFallback() {
        assertEquals(VoteTarget.FALLBACK, VoteTarget.forCapital(null, null));
        assertEquals(VoteTarget.FALLBACK, VoteTarget.forCapital(BigDecimal.ZERO, BigDecimal.ZERO));
    }
}
