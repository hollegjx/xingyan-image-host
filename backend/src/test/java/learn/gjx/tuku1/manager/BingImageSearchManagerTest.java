package learn.gjx.tuku1.manager;

import learn.gjx.tuku1.exception.BusinessException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BingImageSearchManagerTest {

    private final BingImageSearchManager manager = new BingImageSearchManager();

    @Test
    void shouldPreferOriginalUrlAndFallbackToThumbnail() {
        Document document = Jsoup.parse("<div class='dgControl'>"
                + "<a data-m='{\"murl\":\"https://img.example.com/original.jpg?width=1200\"}'>"
                + "<img class='mimg' src='https://img.example.com/thumb.jpg?w=200'></a>"
                + "<img class='mimg' src='https://img.example.com/fallback.png?x=1'>"
                + "</div>");

        List<String> urls = manager.extractImageUrls(document);

        assertEquals("https://img.example.com/original.jpg", urls.get(0));
        assertEquals("https://img.example.com/thumb.jpg", urls.get(1));
        assertEquals("https://img.example.com/fallback.png", urls.get(2));
    }

    @Test
    void shouldRejectDocumentWithoutImages() {
        assertThrows(BusinessException.class, () -> manager.extractImageUrls(Jsoup.parse("<html></html>")));
    }
}
