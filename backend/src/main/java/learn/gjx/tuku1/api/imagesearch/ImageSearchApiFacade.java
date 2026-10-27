package learn.gjx.tuku1.api.imagesearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Orchestrates the tutorial's three requests; no JavaScript evaluation or automatic retries. */
@Component
public class ImageSearchApiFacade {
    private static final Pattern FIRST_URL = Pattern.compile("\"firstUrl\"\\s*:\\s*\"");
    private final BaiduImageSearchTransport transport;
    private final ObjectMapper json = new ObjectMapper();

    public ImageSearchApiFacade(BaiduImageSearchTransport transport) { this.transport = transport; }

    public List<ImageSearchResult> search(String sourceUrl) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(18);
        try {
            String pageUrl = parsePageUrl(transport.submit(sourceUrl, deadline));
            String firstUrl = parseFirstUrl(transport.page(pageUrl, deadline));
            return parseResults(transport.results(firstUrl, deadline));
        } catch (Exception error) {
            // Do not leak URLs, provider responses, signed parameters or user data into errors/logs.
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "第三方以图搜图暂不可用，请稍后重试；图片未被修改");
        }
    }

    String parsePageUrl(String body) throws Exception {
        JsonNode root = json.readTree(body);
        if (!root.path("status").isIntegralNumber() || root.path("status").asInt() != 0) {
            throw new IllegalArgumentException("Provider rejected request");
        }
        return ImageSearchUrls.provider(root.path("data").path("url").asText(), "/s").toString();
    }

    String parseFirstUrl(String html) throws Exception {
        for (Element script : Jsoup.parse(html).getElementsByTag("script")) {
            Matcher match = FIRST_URL.matcher(script.data());
            if (match.find()) {
                // Scan a bounded JSON string instead of applying a recursive regex to untrusted HTML.
                String text = script.data();
                int start = match.end() - 1;
                int limit = Math.min(text.length(), start + 8192);
                for (int i = start + 1; i < limit; i++) {
                    if (text.charAt(i) == '\\') { i++; }
                    else if (text.charAt(i) == '"') {
                        String value = json.readValue(text.substring(start, i + 1), String.class);
                        return ImageSearchUrls.provider(value, "/ajax/pcsimi").toString();
                    }
                }
                throw new IllegalArgumentException("Provider URL malformed or too long");
            }
        }
        throw new IllegalArgumentException("Provider page format changed");
    }

    List<ImageSearchResult> parseResults(String body) throws Exception {
        JsonNode root = json.readTree(body);
        if (root.has("status") && (!root.path("status").isIntegralNumber()
                || root.path("status").asInt() != 0)) throw new IllegalArgumentException("Provider rejected results");
        JsonNode rows = root.path("data").path("list");
        if (!rows.isArray()) throw new IllegalArgumentException("Provider list missing");
        List<ImageSearchResult> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode row : rows) {
            String thumb = ImageSearchUrls.result(row.path("thumbUrl").asText());
            String from = ImageSearchUrls.result(row.path("fromUrl").asText());
            if (thumb != null && from != null && seen.add(thumb + "\n" + from)) {
                result.add(new ImageSearchResult(thumb, from));
                if (result.size() == 30) break;
            }
        }
        if (rows.size() > 0 && result.isEmpty()) throw new IllegalArgumentException("No safe provider results");
        return result;
    }
}
