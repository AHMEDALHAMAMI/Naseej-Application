package com.waleed.naseej.service;

/**
 * Saudi mobile numbers. People type them as 05XXXXXXXX (05 then 8 digits); the database keeps
 * 9665XXXXXXXX so WhatsApp alerts can use it as is. The older 9665XXXXXXXX form is still accepted.
 */
public final class PhoneNumbers {

    /** What the request DTOs accept: 05XXXXXXXX (the format shown to users) or 9665XXXXXXXX. */
    public static final String REGEX = "^(05\\d{8}|9665\\d{8})$";

    /** Same as REGEX, also allowing an empty string (used where an empty value means "remove it"). */
    public static final String REGEX_OR_EMPTY = "^$|^(05\\d{8}|9665\\d{8})$";

    public static final String MESSAGE = "Phone must be in the format 05XXXXXXXX";

    private PhoneNumbers() {
    }

    /** 05XXXXXXXX → 9665XXXXXXXX. Null or blank → null. Anything else is returned trimmed. */
    public static String normalize(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String value = phone.trim();
        return value.matches("^05\\d{8}$") ? "966" + value.substring(1) : value;
    }
}
