package learn.gjx.tuku1.api.imagesearch;

import learn.gjx.tuku1.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImageSearchApiFacadeTest {
    private BaiduImageSearchTransport transport;
    private ImageSearchApiFacade facade;
    private final String page = "https://graph.baidu.com/s?sign=test";
    private final String list = "https://graph.baidu.com/ajax/pcsimi?sign=test";
    private final String source = "https://public-123.cos.ap-test.myqcloud.com/public/1/source.png";

    @BeforeEach void setup() throws Exception {
        transport = mock(BaiduImageSearchTransport.class);
        facade = new ImageSearchApiFacade(transport);
        when(transport.submit(anyString(), anyLong())).thenReturn("{\"status\":0,\"data\":{\"url\":\"" + page + "\"}}");
        when(transport.page(eq(page), anyLong())).thenReturn("<script>const x={\"firstUrl\":\""
                + list.replace("/", "\\/") + "\"}</script>");
        when(transport.results(eq(list), anyLong())).thenReturn("{\"data\":{\"list\":["
                + "{\"thumbUrl\":\"https://images.example/a.jpg\",\"fromUrl\":\"https://example.com/a\"}]}}");
    }

    @Test void orchestratesThreeStepsAndUnescapesWithoutEvaluatingScripts() throws Exception {
        List<ImageSearchResult> result = facade.search(source);
        assertEquals(1, result.size());
        assertEquals("https://example.com/a", result.get(0).getFromUrl());
        org.mockito.InOrder calls = inOrder(transport);
        calls.verify(transport).submit(eq(source), anyLong());
        calls.verify(transport).page(eq(page), anyLong());
        calls.verify(transport).results(eq(list), anyLong());
        calls.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "<html>captcha</html>", "{\"status\":1002}",
            "{\"status\":0,\"data\":{\"url\":\"http://127.0.0.1/secret\"}}",
            "{\"status\":0,\"data\":{\"url\":\"https://evil.example/s\"}}"})
    void upstreamErrorsNeverTriggerNextRequestOrExposeBody(String body) throws Exception {
        when(transport.submit(anyString(), anyLong())).thenReturn(body);
        BusinessException failure = assertThrows(BusinessException.class, () -> facade.search(source));
        assertEquals(50300, failure.getCode());
        assertFalse(failure.getMessage().contains("secret"));
        verify(transport, never()).page(anyString(), anyLong());
    }

    @Test void missingOrHostileFirstUrlStopsBeforeListFetch() throws Exception {
        when(transport.page(anyString(), anyLong())).thenReturn("<script>{\"firstUrl\":\"https://evil.example/ajax/pcsimi\"}</script>");
        assertThrows(BusinessException.class, () -> facade.search(source));
        verify(transport, never()).results(anyString(), anyLong());
        assertThrows(IllegalArgumentException.class, () -> facade.parseFirstUrl("<p>No script</p>"));
    }

    @Test void distinguishesEmptyFromMalformedResults() throws Exception {
        assertTrue(facade.parseResults("{\"data\":{\"list\":[]}}").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> facade.parseResults("{\"data\":{}}"));
        assertThrows(IllegalArgumentException.class, () -> facade.parseResults("{\"status\":1,\"data\":{\"list\":[]}}"));
    }

    @Test void filtersUnsafeResultsDeduplicatesAndCapsAtThirty() throws Exception {
        StringBuilder body = new StringBuilder("{\"data\":{\"list\":[");
        body.append("{\"thumbUrl\":\"javascript:alert(1)\",\"fromUrl\":\"https://example.com\"},");
        body.append("{\"thumbUrl\":\"https://127.0.0.1/a\",\"fromUrl\":\"https://example.com\"},");
        for (int i = 0; i < 40; i++) {
            if (i > 0) body.append(',');
            body.append("{\"thumbUrl\":\"https://images.example/").append(i / 2)
                    .append(".png\",\"fromUrl\":\"https://example.com/a\"}");
        }
        body.append("]}}");
        assertEquals(20, facade.parseResults(body.toString()).size());
        String expanded = body.toString().replace("/a\"", "/b\"");
        assertEquals(20, facade.parseResults(expanded).size());
        StringBuilder large = new StringBuilder("{\"data\":{\"list\":[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) large.append(',');
            large.append("{\"thumbUrl\":\"https://images.example/").append(i)
                    .append(".png\",\"fromUrl\":\"https://example.com/a\"}");
        }
        assertEquals(30, facade.parseResults(large.append("]}}").toString()).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://graph.baidu.com.evil.example/s", "https://user:pass@graph.baidu.com/s",
            "http://graph.baidu.com/s", "https://graph.baidu.com:8123/s", "https://graph.baidu.com/s#x",
            "https://graph.baidu.com/../s", "https://graph.baidu.com/upload"})
    void onlyExactProviderOriginAndPathAccepted(String value) {
        assertThrows(IllegalArgumentException.class, () -> ImageSearchUrls.provider(value, "/s"));
    }

    @Test void boundedReaderRejectsOversizeAndExpiredDeadline() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        assertEquals("ok", BaiduImageSearchTransport.readLimited(new ByteArrayInputStream(new byte[]{111, 107}), deadline));
        assertThrows(IOException.class, () -> BaiduImageSearchTransport.readLimited(
                new ByteArrayInputStream(new byte[BaiduImageSearchTransport.MAX_BYTES + 1]), deadline));
        assertThrows(IOException.class, () -> BaiduImageSearchTransport.readLimited(
                new ByteArrayInputStream(new byte[0]), System.nanoTime() - 1));
    }

    @Test void networkFailureIsSafeAndNotRetried() throws Exception {
        when(transport.submit(anyString(), anyLong())).thenThrow(new IOException("secret upstream details"));
        BusinessException error = assertThrows(BusinessException.class, () -> facade.search(source));
        assertEquals(50300, error.getCode());
        assertFalse(error.getMessage().contains("secret"));
        verify(transport, times(1)).submit(anyString(), anyLong());
    }

    @Test void giantUnclosedScriptStringFailsWithoutRecursiveRegex() {
        String body = "<script>{\"firstUrl\":\"" + new String(new char[100000]).replace('\0', 'a') + "</script>";
        assertThrows(IllegalArgumentException.class, () -> facade.parseFirstUrl(body));
    }
}
