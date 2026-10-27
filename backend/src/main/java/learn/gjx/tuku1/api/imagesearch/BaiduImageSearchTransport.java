package learn.gjx.tuku1.api.imagesearch;

import org.springframework.stereotype.Component;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Bounded, cookieless requests to a fixed provider. Never follows an upstream redirect. */
@Component
public class BaiduImageSearchTransport {
    static final int MAX_BYTES = 2 * 1024 * 1024;

    public String submit(String source, long deadline) throws IOException {
        URI uri = ImageSearchUrls.provider("https://graph.baidu.com/upload?uptime="
                + System.currentTimeMillis(), "/upload");
        String form = "image=" + URLEncoder.encode(source, "UTF-8")
                + "&tn=pc&from=pc&image_source=PC_UPLOAD_URL";
        return exchange(uri, form, deadline);
    }

    public String page(String value, long deadline) throws IOException {
        return exchange(ImageSearchUrls.provider(value, "/s"), null, deadline);
    }

    public String results(String value, long deadline) throws IOException {
        return exchange(ImageSearchUrls.provider(value, "/ajax/pcsimi"), null, deadline);
    }

    private String exchange(URI uri, String form, long deadline) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(timeout(deadline));
            connection.setReadTimeout(timeout(deadline));
            connection.setRequestProperty("User-Agent", "Znytk-Learning-ImageSearch/1.0");
            if (form != null) {
                byte[] bytes = form.getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            if (connection.getResponseCode() != 200) throw new IOException("Provider HTTP failure");
            if (connection.getContentLengthLong() > MAX_BYTES) throw new IOException("Provider response too large");
            try (InputStream input = connection.getInputStream()) {
                return readLimited(input, deadline);
            }
        } finally {
            connection.disconnect();
        }
    }

    static String readLimited(InputStream input, long deadline) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while (true) {
            timeout(deadline);
            count = input.read(chunk);
            if (count < 0) break;
            if (buffer.size() + count > MAX_BYTES) throw new IOException("Provider response too large");
            buffer.write(chunk, 0, count);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static int timeout(long deadline) throws IOException {
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remaining <= 0) throw new IOException("Provider deadline exceeded");
        return (int) Math.min(3000, remaining);
    }
}
