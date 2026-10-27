package learn.gjx.tuku1.api.aliyunai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import learn.gjx.tuku1.config.AiEditingProperties;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.*;

/** Qwen synchronous editing adapter. Exactly one output, no redirects or POST retries. */
@Component
public class AliyunOutpaintingClient {
    public static final String MODEL = "qwen-image-3.0";
    private final AiEditingProperties config;
    private final ObjectMapper json;
    public AliyunOutpaintingClient(AiEditingProperties config, ObjectMapper json) { this.config=config; this.json=json; }
    public Generation generate(String sourceUrl, double xScale, double yScale, int width, int height) throws IOException {
        if (!config.configured()) throw new IOException("AI workspace is not configured");
        if (width < 512 || height < 512 || width > 2048 || height > 2048 || (long) width * height > 1_048_576
                || !Double.isFinite(xScale) || !Double.isFinite(yScale) || xScale < 1 || yScale < 1
                || xScale > 3 || yScale > 3 || (xScale == 1 && yScale == 1))
            throw new IOException("Invalid generation dimensions");
        Map<String,Object> parameters = new LinkedHashMap<>();
        parameters.put("n", 1); parameters.put("size", width + "*" + height);
        parameters.put("prompt_extend", false); parameters.put("watermark", false);
        String prompt = String.format(Locale.ROOT,
                "请对输入图片进行向外扩图，而不是裁剪或简单拉伸。将画面视野水平扩展至原来的 %.2f 倍、垂直扩展至原来的 %.2f 倍。"
                + "原图主体保持在画面中央，尽量保持主体身份、外观、原有细节和光照。"
                + "在四周自然延续原图背景、纹理与透视，不添加文字、边框或重复主体。输出画布为 %d×%d 像素。",
                xScale, yScale, width, height);
        Map<String,Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", Arrays.asList(Collections.singletonMap("image", sourceUrl), Collections.singletonMap("text", prompt)));
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("model", MODEL);
        body.put("input", Collections.singletonMap("messages", Collections.singletonList(message)));
        body.put("parameters", parameters);
        JsonNode result = exchange("https://" + config.getWorkspaceId()
                + ".cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation", json.writeValueAsBytes(body));
        String requestId=safeIdentifier(result.path("request_id").asText(null));
        if (result.hasNonNull("code") && !result.path("code").asText().isEmpty())
            throw new ResponseException("BUSINESS_ERROR_"+safeIdentifier(result.path("code").asText()),requestId);
        JsonNode choices = result.path("output").path("choices");
        // Some model versions omit finish_reason. A single image is required;
        // a text-generation stop token is not evidence of image validity.
        if (!choices.isArray() || choices.size() != 1)
            throw new ResponseException("CHOICES_SHAPE",requestId);
        JsonNode content = choices.get(0).path("message").path("content");
        String image = null;
        for (JsonNode item : content) {
            if (item.hasNonNull("image")) {
                if (image != null || !item.get("image").isTextual()) throw new ResponseException("OUTPUT_COUNT",requestId);
                image = item.get("image").asText();
            }
        }
        if (image == null || image.length() > 2048) throw new ResponseException("RESULT_URL_SHAPE",requestId);
        return new Generation(image,requestId); // URL stays internal until exact HTTPS host validation.
    }
    protected JsonNode exchange(String url, byte[] body) throws IOException {
        HttpPost request = new HttpPost(url);
        request.setHeader("Authorization", "Bearer " + config.getApiKey());
        request.setHeader("Content-Type", "application/json");
        request.setEntity(new ByteArrayEntity(body));
        ScheduledExecutorService deadline = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ai-http-deadline"); thread.setDaemon(true); return thread;
        });
        deadline.schedule(request::abort, 185, TimeUnit.SECONDS);
        try (CloseableHttpClient client = HttpClients.custom().disableAutomaticRetries().disableRedirectHandling()
                .disableCookieManagement().disableAuthCaching()
                .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(5000).setConnectionRequestTimeout(3000)
                        .setSocketTimeout(180000).build()).build();
             CloseableHttpResponse response = client.execute(request)) {
            int status = response.getStatusLine().getStatusCode();
            if (status < 200 || status >= 300) {
                // Retain only bounded, safe diagnostic identifiers. Never retain the
                // raw provider message, which may echo a signed source URL or key.
                byte[] error = new byte[0];
                try (InputStream in = response.getEntity() == null ? null : response.getEntity().getContent()) {
                    if (in != null) error = readLimited(in, 8192);
                } catch (IOException ignored) { /* The HTTP status is still useful. */ }
                org.apache.http.Header header = response.getFirstHeader("x-request-id");
                throw failure(status, error, header == null ? null : header.getValue());
            }
            if (response.getEntity() == null) throw new IOException("Empty generation response");
            try (InputStream in = response.getEntity().getContent()) { return json.readTree(readLimited(in, 64 * 1024)); }
        } finally { request.abort(); deadline.shutdownNow(); }
    }
    ProviderHttpException failure(int status, byte[] body, String headerRequestId) {
        String code = null, requestId = headerRequestId;
        try {
            JsonNode value = json.readTree(body);
            if (value != null) {
                JsonNode details = value.has("error") ? value.path("error") : value;
                code = details.path("code").asText(null);
                requestId = value.path("request_id").asText(requestId);
            }
        } catch (IOException ignored) { /* Non-JSON errors must not hide the HTTP status. */ }
        return new ProviderHttpException(status, safeIdentifier(code), safeIdentifier(requestId));
    }
    private String safeIdentifier(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.-]{1,128}") || value.contains("sk-")
                || (config.getApiKey() != null && !config.getApiKey().isEmpty() && value.contains(config.getApiKey()))) return "unavailable";
        return value;
    }
    public static class ProviderHttpException extends IOException {
        public final int status;
        public final String code;
        public final String requestId;
        public ProviderHttpException(int status, String code, String requestId) {
            super("Provider HTTP " + status);
            this.status = status; this.code = code; this.requestId = requestId;
        }
        /** A 408/409/5xx is not proof that generation was never accepted. */
        public boolean definitivelyRejected() {
            return Arrays.asList(400, 401, 403, 404, 405, 413, 415, 422, 429).contains(status);
        }
    }
    public static class Generation {
        public final String url;
        public final String requestId;
        public Generation(String url,String requestId){this.url=url;this.requestId=requestId;}
    }
    public static class ResponseException extends IOException {
        public final String code;
        public final String requestId;
        ResponseException(String code,String requestId){super("Provider response shape error");this.code=code;this.requestId=requestId;}
    }
    public static byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        long deadline = System.nanoTime() + 15_000_000_000L;
        while ((n=in.read(buffer)) >= 0) {
            if (out.size()+n>max || System.nanoTime()>deadline) throw new IOException("Response limit exceeded");
            out.write(buffer,0,n);
        }
        return out.toByteArray();
    }
}
