package com.bajar.saman.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Converts a human-readable name (e.g. "Mobile Phones & Accessories") into a
 * URL-safe slug (e.g. "mobile-phones-accessories"). Used by both CategoryService
 * and ProductService — centralized here (DRY) so slug FORMAT rules stay consistent
 * everywhere a slug is generated, same reasoning as EmailNormalizer.
 */
public final class SlugGenerator {

    // Matches any run of characters that are NOT a-z, 0-9, or hyphen — everything
    // matched by this gets stripped/replaced. Pre-compiled once as a static final
    // field (not rebuilt on every call) since regex compilation has real cost and
    // this method may run frequently (every category/product create).
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private SlugGenerator() {
    }

    public static String generate(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Cannot generate a slug from empty input");
        }

        // Normalizer.normalize(..., NFD) + stripping combining marks converts
        // accented characters (é, ñ, ü) to their plain ASCII equivalent (e, n, u)
        // rather than just deleting them outright — "Café" becomes "cafe", not
        // "caf". Without this step, non-English product/category names would lose
        // meaningful characters instead of gracefully simplifying them.
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        String lowercase = normalized.toLowerCase(Locale.ROOT);

        // Replace any run of non-alphanumeric characters (spaces, &, /, punctuation)
        // with a single hyphen — "Mobile Phones & Accessories" -> after this step:
        // "mobile-phones-accessories" (the "&" and surrounding spaces collapse into
        // ONE hyphen, not three separate ones, thanks to the "+" in the regex above).
        String slug = NON_ALPHANUMERIC.matcher(lowercase).replaceAll("-");

        // Strip any leading/trailing hyphen that could result from the input itself
        // starting/ending with punctuation (e.g. "!Sale Items!" would otherwise
        // produce "-sale-items-").
        slug = slug.replaceAll("^-+|-+$", "");

        return slug;
    }
}