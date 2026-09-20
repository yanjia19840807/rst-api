package com.cmacgm.gbs.rst.api.governance.application;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Splits comma-separated labels, then keeps first-seen literals.
 */
public final class CommaTokens {

    private CommaTokens() {
    }

    /**
     * Splits each value on commas, trims, and keeps first-seen tokens.
     * {@code CHINA} + {@code HONG KONG SAR, CHINA} becomes {@code CHINA}, {@code HONG KONG SAR}.
     *
     * @param values raw labels
     * @return distinct tokens in first-seen order
     */
    public static List<String> distinct(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            for (String part : value.split(",")) {
                String token = part.trim();
                if (!token.isEmpty()) {
                    tokens.add(token);
                }
            }
        }
        return List.copyOf(tokens);
    }

    /**
     * Distinct tokens, sorted, for dropdown options.
     *
     * @param values raw labels
     * @return sorted distinct tokens
     */
    public static List<String> distinctSorted(List<String> values) {
        return distinct(values).stream().sorted().toList();
    }

    /**
     * Join distinct tokens with {@code ", "}.
     *
     * @param values raw labels
     * @return display text, or empty when there are no tokens
     */
    public static String joined(List<String> values) {
        return String.join(", ", distinct(values));
    }

    /**
     * Whether {@code raw} contains {@code selected} after comma-splitting.
     *
     * @param raw stored label, may contain commas
     * @param selected dropdown token
     * @return true when the token is present
     */
    public static boolean contains(String raw, String selected) {
        if (selected == null || selected.isBlank()) {
            return true;
        }
        return distinct(List.of(raw == null ? "" : raw)).contains(selected);
    }
}
