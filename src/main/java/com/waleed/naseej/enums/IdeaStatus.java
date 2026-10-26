package com.waleed.naseej.enums;

public enum IdeaStatus {
    SCREENING,
    // Passed the AI check; only the author sees it until they press Publish
    DRAFT,
    PUBLISHED,
    COMPLETED,
    ADOPTED,
    OPENED,
    // The AI check found a similar idea nearby or no shortage, so the idea isn't published
    REJECTED,
    // An admin took the idea off the map (spam, abuse); Idea.hiddenFromStatus keeps the status to restore
    HIDDEN
}
