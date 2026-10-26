package com.waleed.naseej.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How many votes an idea needs before it counts as proven demand (its membershipThreshold).
 * A bigger project risks more money, so it needs more neighbors behind it:
 *
 *   target = 20 + one vote for every 50,000 SAR of the estimated capital (the middle of its range),
 *            rounded to the nearest 5, between 20 and 100.
 *
 * e.g. a barber (120k–220k) → 25, a café (380k–620k) → 30, a supermarket (1M–1.8M) → 50,
 * a trampoline park (1.8M–3M) → 70. Without a capital estimate the target is FALLBACK.
 * The target is fixed when the idea is published, so editing the business model later doesn't move it.
 */
public final class VoteTarget {

    public static final int BASE = 20;
    public static final int SAR_PER_VOTE = 50_000;
    public static final int MIN = 20;
    public static final int MAX = 100;
    public static final int STEP = 5;
    public static final int FALLBACK = 40;

    private VoteTarget() {
    }

    /** The target for an estimated capital range in SAR; either end may be missing. */
    public static int forCapital(BigDecimal capitalMin, BigDecimal capitalMax) {
        BigDecimal middle;
        if (capitalMin != null && capitalMax != null) {
            middle = capitalMin.add(capitalMax).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        } else if (capitalMin != null || capitalMax != null) {
            middle = capitalMin != null ? capitalMin : capitalMax;
        } else {
            return FALLBACK;
        }
        if (middle.signum() <= 0) {
            return FALLBACK;
        }
        double raw = BASE + middle.doubleValue() / SAR_PER_VOTE;
        int rounded = (int) (Math.round(raw / STEP) * STEP);
        return Math.max(MIN, Math.min(MAX, rounded));
    }
}
