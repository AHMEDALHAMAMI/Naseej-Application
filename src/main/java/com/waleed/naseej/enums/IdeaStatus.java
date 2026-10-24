package com.waleed.naseej.enums;

public enum IdeaStatus {
    SCREENING,
    PUBLISHED,
    COMPLETED,
    ADOPTED,
    OPENED,
    // The AI check found a similar idea nearby or no shortage, so the idea isn't published
    REJECTED
}
