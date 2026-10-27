package learn.gjx.tuku1.utils;

import java.util.Locale;

/** RGB metadata only; never executes expressions or fetches an image during a search. */
public final class PictureColor {
    private PictureColor() { }

    public static String normalize(String value) {
        if (value == null) return null;
        String text = value.trim();
        if (text.matches("#[0-9a-fA-F]{6}")) return text.toLowerCase(Locale.ROOT);
        // COS can omit leading zeroes in its numeric 0x representation.
        if (text.matches("0[xX][0-9a-fA-F]{1,6}")) {
            return String.format(Locale.ROOT, "#%06x", Integer.parseInt(text.substring(2), 16));
        }
        return null;
    }

    public static int rgb(String normalized) { return Integer.parseInt(normalized.substring(1), 16); }

    public static int distanceSquared(int left, int right) {
        int r = ((left >> 16) & 255) - ((right >> 16) & 255);
        int g = ((left >> 8) & 255) - ((right >> 8) & 255);
        int b = (left & 255) - (right & 255);
        return r * r + g * g + b * b;
    }
}
