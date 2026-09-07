package dev.kasapdev.jsondiff;

import java.util.Locale;

/**
 * The kind of operation a {@link PatchOperation} represents, per RFC 6902.
 *
 * <p>Only the three operations {@link JsonPatch} actually generates from a structural diff are
 * modeled: {@code add}, {@code remove}, and {@code replace}. RFC 6902 also defines {@code move},
 * {@code copy}, and {@code test}, but a value-by-value structural diff never needs them.
 */
public enum PatchOp {
    ADD,
    REMOVE,
    REPLACE;

    /**
     * Returns the RFC 6902 {@code "op"} member's canonical lowercase string form,
     * e.g. {@code "add"}.
     */
    public String rfcName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
