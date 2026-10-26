package com.waleed.naseej.service;

import org.springframework.context.i18n.LocaleContextHolder;

/**
 * The language AI text is written in, and the tone rule every prompt adds.
 * The website sends Accept-Language (ar / en) on every request, and Spring keeps it in LocaleContextHolder.
 */
public final class AiLanguage {

    public static final String ARABIC = "ar";
    public static final String ENGLISH = "en";

    private static final String ARABIC_RULE = """
            Write every text field in Arabic, the way people talk in Saudi Arabia but clear to every Arab reader:
            اكتب بلهجة سعودية بيضاء يفهمها الكل. جمل قصيرة (١٥ كلمة أو أقل). لا تستخدم مصطلحات اقتصادية أو تقنية؛ \
            إذا لزم اشرحها بكلمات بسيطة. خاطب القارئ مباشرة.
            Keep business names, place names and numbers as they are.""";

    private static final String ENGLISH_RULE = """
            Write every text field in plain English that anyone understands. Short sentences of 15 words or fewer. \
            No business or technical jargon; if a term is needed, explain it in simple words. Speak to the reader directly.
            Keep business names, place names and numbers as they are.""";

    private AiLanguage() {
    }

    /** The language of the person making this request ("ar" or "en"). */
    public static String current() {
        return ARABIC.equals(LocaleContextHolder.getLocale().getLanguage()) ? ARABIC : ENGLISH;
    }

    /** The language and tone rule to add at the end of a system prompt. */
    public static String instruction(String lang) {
        return ARABIC.equals(lang) ? ARABIC_RULE : ENGLISH_RULE;
    }

    /** "ar" when a text has more Arabic letters than Latin ones, otherwise "en" (names in the other script don't flip it). */
    public static String detect(String text) {
        if (text == null) {
            return ENGLISH;
        }
        long arabic = text.codePoints().filter(c -> c >= 0x0600 && c <= 0x06FF).count();
        long latin = text.codePoints().filter(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')).count();
        return arabic > latin ? ARABIC : ENGLISH;
    }
}
