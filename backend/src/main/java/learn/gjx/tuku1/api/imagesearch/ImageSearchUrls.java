package learn.gjx.tuku1.api.imagesearch;

import java.net.URI;
import java.util.Locale;

/** No user-supplied fetch targets, redirects, signed links or non-HTTPS result links. */
public final class ImageSearchUrls {
    private ImageSearchUrls() { }

    public static URI provider(String value, String path) {
        URI uri = https(value);
        if (!"graph.baidu.com".equalsIgnoreCase(uri.getHost()) || !path.equals(uri.getRawPath())) {
            throw new IllegalArgumentException("Unexpected provider destination");
        }
        return uri;
    }

    public static String source(String value, String publicHost) {
        URI uri = https(value);
        if (!uri.getHost().equalsIgnoreCase(publicHost) || uri.getRawQuery() != null
                || !uri.getRawPath().matches("/public/[1-9][0-9]*/[a-zA-Z0-9_.-]+")
                || !uri.normalize().equals(uri)) {
            throw new IllegalArgumentException("Not an unsigned public COS object");
        }
        return uri.toASCIIString();
    }

    public static String result(String value) {
        try {
            URI uri = https(value);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (!host.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?") || !host.contains(".")
                    || host.matches("[0-9.]+") || host.endsWith(".localhost")
                    || host.endsWith(".local") || host.endsWith(".internal")) return null;
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static URI https(String value) {
        if (value == null || value.length() > 4096 || value.contains("\\")) {
            throw new IllegalArgumentException("Invalid URL");
        }
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("Invalid HTTPS URL");
        }
        return uri;
    }
}
